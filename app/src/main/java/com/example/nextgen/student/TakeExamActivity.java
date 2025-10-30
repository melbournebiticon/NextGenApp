package com.example.nextgen.student;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem; // 🛑 NEW IMPORT: Para sa onOptionsItemSelected
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog; // 🛑 NEW IMPORT: Para sa Exam Rules Alert
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.teacher.Question;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TakeExamActivity extends AppCompatActivity {

    private TextView tvExamTitle;
    private RecyclerView rvQuestions;
    private TakeExamAdapter questionAdapter;
    private List<Question> questionList = new ArrayList<>();
    private Button btnSubmit;

    private String examId;
    private String examTitle;
    private DatabaseReference questionsRef;

    // Navigation variables
    private int currentIndex = 0;
    private String currentQuestionType = "Multiple Choice";
    private List<Question> currentTypeQuestions = new ArrayList<>();

    private final String[] questionTypeOrder = {"Multiple Choice", "True/False", "Matching Type"};
    private int typeIndex = 0; // to track which question type we’re on

    // 🔹 Added type-based numbering tracker
    private int typeQuestionNumber = 1;

    // 🔹 Exam duration
    private int durationMinutes = 0;

    // 🏆 NEW TIMER VARIABLES
    private CountDownTimer countDownTimer;
    private TextView tvTimer;
    private long timeLeftInMillis;

    // 🏆 NEW: Firebase Auth and UID
    private FirebaseAuth auth;
    private String currentStudentUid;

    // 🛑 ANTI-CHEATING VARIABLES
    private int switchCount = 0;
    private final int MAX_SWITCHES = 3;

    // 🛑 NEW DEDUCTION VARIABLE
    private int totalDeductions = 0;
    private final int DEDUCTION_PER_STRIKE = 1; // 1 point deduction per violation

    // 🛑 NEW AUDIO DETECTION VARIABLES
    private MediaRecorder mediaRecorder = null;
    private Handler audioHandler = new Handler();
    private static final int AUDIO_DETECTION_INTERVAL = 500; // Check every 0.5 seconds
    private static final int VOLUME_THRESHOLD = 5000; // Needs testing and adjustment
    private int highVolumeCount = 0;
    private static final int MAX_HIGH_VOLUME_TIME_COUNT = 6; // 6 * 0.5s = 3 seconds

    // 🛑 NEW: Request code for audio permission
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // 🛑 ANTI-CHEATING: No Screenshot, Screen Recording, or Copy-Paste (System Level)
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_exam);

        // 🛑 NEW: I-enable ang In-App Back Arrow para gumana ang deduction logic (onOptionsItemSelected)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // I-show ang back arrow
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        tvExamTitle = findViewById(R.id.tvExamTitle);
        rvQuestions = findViewById(R.id.rvQuestions);
        rvQuestions.setLayoutManager(new LinearLayoutManager(this));
        btnSubmit = findViewById(R.id.btnSubmitExam);

        tvTimer = findViewById(R.id.tvTimer);

        examId = getIntent().getStringExtra("examId");
        examTitle = getIntent().getStringExtra("examTitle");

        Log.d("TakeExam", "ExamID received: " + examId + ", ExamTitle: " + examTitle);

        if (examId == null || examId.isEmpty()) {
            Toast.makeText(this, "Invalid exam ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentStudentUid = currentUser.getUid();

        // 🚀 CRITICAL CHECK: Dito magsisimula ang flow (check score -> show alert)
        checkIfExamIsAlreadyTaken();
    }

    // 🏆 UPDATED CRITICAL FUNCTION: Checks if score exists in Firebase
    private void checkIfExamIsAlreadyTaken() {
        DatabaseReference scoreRef = FirebaseDatabase.getInstance()
                .getReference("Scores")
                .child(currentStudentUid)
                .child(examId);

        scoreRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(TakeExamActivity.this, "You have already completed this exam.", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    // ✅ WALANG SCORE RECORD: Tuloy sa pagpapakita ng rules
                    showExamRulesAlert(); // 🛑 NEW ENTRY POINT
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("TakeExam", "Database error during score check: " + error.getMessage());
                Toast.makeText(TakeExamActivity.this, "Failed to verify exam status.", Toast.LENGTH_SHORT).show();
                // Safety first: Allow user to proceed if check fails
                showExamRulesAlert();
            }
        });
    }

    // 🛑 NEW FUNCTION: Alert para sa Exam Rules at Anti-Cheating Warnings
    private void showExamRulesAlert() {
        new AlertDialog.Builder(this)
                .setTitle("IMPORTANT: Exam Rules & Anti-Cheating")
                .setMessage("By pressing START, you agree to the following rules:\n\n" +
                        "1. DO NOT EXIT THE APP (Switching or minimizing will deduct points).\n" +
                        "2. DO NOT USE SPLIT-SCREEN or MULTI-WINDOW mode.\n" +
                        "3. The PHONE'S BACK BUTTON is DISABLED.\n" +
                        "4. The In-App Back Arrow will deduct " + DEDUCTION_PER_STRIKE + " point(s) upon press.\n" +
                        "5. Your microphone will be monitored for excessive noise or voices.\n\n" +
                        "Exceeding " + MAX_SWITCHES + " violations will result in automatic submission with a score of zero (0)."
                )
                .setPositiveButton("START EXAM", (dialog, which) -> {
                    // Pagkatapos pindutin ang START EXAM, saka tuluyang simulan ang paglo-load
                    startExamLoadingProcessContinued();
                })
                .setCancelable(false) // Bawal i-dismiss ng back button
                .show();
    }


    // 🏆 UPDATED HELPER: Ito na ang bagong entry point para lang magpakita ng alert
    private void startExamLoadingProcess() {
        // Dito na tatawagin ang showExamRulesAlert()
        showExamRulesAlert();
    }

    // 🏆 NEW CRITICAL FUNCTION: Ito ang original loading logic na tatawagin pagkatapos ng alert
    private void startExamLoadingProcessContinued() {

        // 🛑 NEW ANTI-CHEATING: SPLIT-SCREEN CHECK
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInMultiWindowMode()) {
            Toast.makeText(this, "CHEATING DETECTED: Split-screen mode is not allowed during the exam. Auto-submitting.", Toast.LENGTH_LONG).show();
            submitExamWithZeroScore();
            return; // Itigil ang paglo-loading
        }
        // ----------------------------------------

        tvExamTitle.setText("Exam: " + examTitle);

        fetchExamDetailsFromFirebase();

        questionsRef = FirebaseDatabase.getInstance().getReference("Questions").child(examId);
        loadQuestions();

        // 🛑 SIMULAN ANG AUDIO MONITORING MATAPOS ANG RULES
        checkAndRequestAudioPermission();

        btnSubmit.setOnClickListener(v -> submitExam());
    }

    // 🛑 NEW FUNCTION: I-check at hiningi ang Audio Permission
    private void checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            startAudioMonitoring();
        }
    }

    // 🛑 UPDATED: I-handle ang resulta ng Permission Request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioMonitoring();
            } else {
                Toast.makeText(this, "Warning: Audio monitoring is disabled. Microphone permission denied.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // 🏆 LIFECYCLE METHOD: Stop timer when activity is closed
    @Override
    protected void onStop() {
        super.onStop();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        stopAudioMonitoring();
    }

    // 🛑 LIFECYCLE METHOD: Detect App Switch/Tab Change (WITH DEDUCTION/STRIKE)
    @Override
    protected void onPause() {
        super.onPause();

        if (isFinishing()) { return; }

        switchCount++;
        totalDeductions += DEDUCTION_PER_STRIKE; // 🛑 DEDUCTION: 1 point per app switch
        Log.w("ANTI_CHEAT", "App switch detected. Strike Count: " + switchCount + ", Total Deduction: " + totalDeductions);

        if (switchCount >= MAX_SWITCHES) {
            Toast.makeText(this, "Cheating detected: Exceeded max allowed screen switches! Submitting exam automatically with final score.", Toast.LENGTH_LONG).show();
            submitExamWithZeroScore();

        } else {
            Toast.makeText(this, "WARNING: Switching apps detected. " + DEDUCTION_PER_STRIKE + " point deducted. " + (MAX_SWITCHES - switchCount) + " attempt(s) remaining before automatic submission.", Toast.LENGTH_LONG).show();
        }
    }

    // 🛑 LIFECYCLE METHOD: Re-check on resume
    @Override
    protected void onResume() {
        super.onResume();

        if (isFinishing()) return;

        // 🛑 NEW ANTI-CHEATING: RE-CHECK SPLIT-SCREEN
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInMultiWindowMode()) {
            if (countDownTimer != null) countDownTimer.cancel();
            stopAudioMonitoring();

            Toast.makeText(this, "CHEATING DETECTED: Split-screen mode activated during exam. Auto-submitting.", Toast.LENGTH_LONG).show();
            submitExamWithZeroScore();
            return;
        }

        if (switchCount > 0 && switchCount < MAX_SWITCHES) {
            Toast.makeText(this, "Welcome back. Be cautious, switching apps is being monitored.", Toast.LENGTH_SHORT).show();
        }
    }

    // 🛑 NEW FUNCTION: DEDUCTION LOGIC - In-App Back Arrow is pressed
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {

            // I-check muna kung active pa ang exam
            if (questionList.isEmpty() || countDownTimer == null) {
                Toast.makeText(this, "Exam finished. Please use the Submit button.", Toast.LENGTH_SHORT).show();
                return true;
            }

            switchCount++;
            totalDeductions += DEDUCTION_PER_STRIKE; // 🛑 DEDUCTION: 1 point for hitting back arrow

            if (switchCount >= MAX_SWITCHES) {
                Toast.makeText(this, "CHEATING DETECTED: Exceeded max allowed navigation switches! Auto-submitting.", Toast.LENGTH_LONG).show();
                submitExamWithZeroScore();

            } else {
                Toast.makeText(this,
                        "WARNING: In-app Back Arrow attempt detected. " + DEDUCTION_PER_STRIKE + " point deducted. " + (MAX_SWITCHES - switchCount) + " attempt(s) remaining.",
                        Toast.LENGTH_LONG).show();

                Log.w("ANTI_CHEAT", "In-app back strike! Deductions: " + totalDeductions);
            }

            return true; // Kinain namin ang click at hindi na ito ipinasa sa system
        }
        return super.onOptionsItemSelected(item);
    }

    // 🛑 UPDATED FUNCTION: Hardware Back Button: Disable lang, walang deduction.
    @Override
    public void onBackPressed() {
        Toast.makeText(this, "The Phone's Back button is disabled during the exam.", Toast.LENGTH_LONG).show();
        // Huwag tawagin ang super.onBackPressed()
    }

    // 🏆 NEW TIMER FUNCTION: Starts the countdown
    private void startTimer() {
        timeLeftInMillis = (long) durationMinutes * 60000;

        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateCountDownText();
            }

            @Override
            public void onFinish() {
                timeLeftInMillis = 0;
                updateCountDownText();
                Toast.makeText(TakeExamActivity.this, "Time's up! Submitting exam automatically.", Toast.LENGTH_LONG).show();
                submitExam();
            }
        }.start();
    }

    // 🏆 NEW TIMER FUNCTION: Updates the TextView
    private void updateCountDownText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;

        String timeLeftFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        tvTimer.setText(timeLeftFormatted);
    }

    // 🏆 UPDATED FUNCTION: Load durationMinutes directly from Firebase and start timer
    private void fetchExamDetailsFromFirebase() {
        DatabaseReference examsRootRef = FirebaseDatabase.getInstance().getReference("Exams");

        examsRootRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;
                for (DataSnapshot teacherSnap : snapshot.getChildren()) {
                    if (teacherSnap.hasChild(examId)) {
                        found = true;
                        DataSnapshot examSnap = teacherSnap.child(examId);

                        Long durationLong = examSnap.child("durationMinutes").getValue(Long.class);
                        if (durationLong != null) {
                            durationMinutes = durationLong.intValue();
                            Log.d("TakeExam", "Duration loaded from Firebase: " + durationMinutes + " minutes. Starting Timer.");
                            startTimer();
                        } else {
                            Log.d("TakeExam", "durationMinutes not found in Firebase. Timer will not start.");
                        }
                        break;
                    }
                }
                if (!found) {
                    Log.e("TakeExam", "Exam details not found in Firebase under any teacher node.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("TakeExam", "Error fetching exam details from Firebase: " + error.getMessage());
                Toast.makeText(TakeExamActivity.this, "Error fetching exam details.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- loadQuestions and Navigation functions (Unchanged) ---
    private void loadQuestions() {
        questionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                questionList.clear();
                for (DataSnapshot questionSnap : snapshot.getChildren()) {
                    Question q = questionSnap.getValue(Question.class);
                    if (q != null && q.getQuestionType() != null) {
                        questionList.add(q);
                    }
                }

                if (questionList.isEmpty()) {
                    Toast.makeText(TakeExamActivity.this, "No questions found for this exam", Toast.LENGTH_SHORT).show();
                    return;
                }

                typeIndex = 0;
                filterQuestionsByType(questionTypeOrder[typeIndex]);
                showNextQuestion();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TakeExamActivity.this, "Error loading questions: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterQuestionsByType(String type) {
        currentQuestionType = type;
        currentTypeQuestions.clear();

        for (Question q : questionList) {
            if (q.getQuestionType().equalsIgnoreCase(type)) {
                currentTypeQuestions.add(q);
            }
        }

        typeQuestionNumber = 1;
        currentIndex = 0;
        Log.d("TakeExam", "Loaded " + currentTypeQuestions.size() + " questions for type: " + type);
    }

    private void showNextQuestion() {
        if (currentTypeQuestions.isEmpty()) {
            goToNextType();
            return;
        }

        if (currentIndex < currentTypeQuestions.size()) {
            List<Question> singleQuestion = new ArrayList<>();
            Question currentQ = currentTypeQuestions.get(currentIndex);

            currentQ.setDisplayNumber(typeQuestionNumber);

            singleQuestion.add(currentQ);

            questionAdapter = new TakeExamAdapter(TakeExamActivity.this, singleQuestion);
            rvQuestions.setAdapter(questionAdapter);

            if (currentIndex == currentTypeQuestions.size() - 1 && typeIndex == questionTypeOrder.length - 1) {
                btnSubmit.setText("Submit Exam");
            } else if (currentIndex == currentTypeQuestions.size() - 1) {
                btnSubmit.setText("Next Section");
            } else {
                btnSubmit.setText("Next");
            }

            btnSubmit.setOnClickListener(v -> {
                currentIndex++;
                typeQuestionNumber++;

                if (currentIndex < currentTypeQuestions.size()) {
                    showNextQuestion();
                } else {
                    goToNextType();
                }
            });
        } else {
            goToNextType();
        }
    }

    private void goToNextType() {
        typeIndex++;
        if (typeIndex < questionTypeOrder.length) {
            filterQuestionsByType(questionTypeOrder[typeIndex]);
            if (currentTypeQuestions.isEmpty()) {
                goToNextType();
            } else {
                showNextQuestion();
            }
        } else {
            btnSubmit.setText("Submit Exam");
            btnSubmit.setOnClickListener(v -> submitExam());
        }
    }
    // --- End of loadQuestions and Navigation functions ---


    private void submitExam() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        stopAudioMonitoring();

        if (questionList.isEmpty()) {
            Toast.makeText(this, "No questions to submit", Toast.LENGTH_SHORT).show();
            return;
        }

        int totalQuestions = questionList.size();
        int correctAnswers = 0;

        for (Question q : questionList) {
            String studentAns = q.getStudentAnswer();
            if (studentAns != null && studentAns.equalsIgnoreCase(q.getCorrectAnswer())) {
                correctAnswers++;
            }
        }

        // 🛑 NEW CALCULATION: Apply deductions sa final score
        int finalCalculatedScore = correctAnswers - totalDeductions;
        // Tiyakin na hindi magiging negative ang score
        if (finalCalculatedScore < 0) {
            finalCalculatedScore = 0;
        }

        final int finalTotalQuestions = totalQuestions;
        final int finalCorrectAnswers = finalCalculatedScore;

        saveScoreToFirebase(finalCorrectAnswers, finalTotalQuestions);

        redirectToResultActivity(finalCorrectAnswers, finalTotalQuestions);
    }

    // 🛑 NEW HELPER METHOD: I-execute kapag may cheating
    private void submitExamWithZeroScore() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        stopAudioMonitoring();

        final int finalTotalQuestions = questionList.size();
        final int finalCorrectAnswers = 0; // Set to zero (auto-fail)

        saveScoreToFirebase(finalCorrectAnswers, finalTotalQuestions);

        redirectToResultActivity(finalCorrectAnswers, finalTotalQuestions);
    }

    // 🛑 NEW HELPER METHOD: Para i-handle ang nested logic ng ResultActivity redirection
    private void redirectToResultActivity(int score, int maxScore) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = currentUser.getUid();

        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        studentsRef.orderByChild("uid").equalTo(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot studentSnap) {
                if (!studentSnap.exists()) {
                    Toast.makeText(TakeExamActivity.this, "Student info not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                for (DataSnapshot studentData : studentSnap.getChildren()) {
                    String studentId = studentData.child("studentId").getValue(String.class);
                    String fullName = studentData.child("fullName").getValue(String.class);
                    String profileImage = studentData.child("profileImage").getValue(String.class);

                    DatabaseReference examsRef = FirebaseDatabase.getInstance().getReference("Exams");
                    examsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            boolean found = false;

                            for (DataSnapshot teacherSnap : snapshot.getChildren()) {
                                if (teacherSnap.hasChild(examId)) {
                                    found = true;
                                    DataSnapshot examSnap = teacherSnap.child(examId);

                                    String subjectName = examSnap.child("subjectName").getValue(String.class);
                                    String teacherName = examSnap.child("teacherName").getValue(String.class);

                                    DatabaseReference subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
                                    subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot subjectSnap) {
                                            String subjectCode = "";
                                            for (DataSnapshot s : subjectSnap.getChildren()) {
                                                String sName = s.child("name").getValue(String.class);
                                                if (sName != null && sName.equals(subjectName)) {
                                                    subjectCode = s.child("code").getValue(String.class);
                                                    break;
                                                }
                                            }

                                            Intent intent = new Intent(TakeExamActivity.this, ResultActivity.class);
                                            intent.putExtra("courseCode", subjectCode);
                                            intent.putExtra("subjectName", subjectName);
                                            intent.putExtra("teacherName", teacherName);
                                            intent.putExtra("studentName", fullName);
                                            intent.putExtra("studentId", studentId);
                                            intent.putExtra("profileImage", profileImage);
                                            intent.putExtra("totalScore", score);
                                            intent.putExtra("maxScore", maxScore);
                                            intent.putExtra("examDurationMinutes", durationMinutes);
                                            // 🛑 Pass the deduction count to ResultActivity for remarks
                                            intent.putExtra("totalDeductions", totalDeductions);

                                            startActivity(intent);
                                            finish();
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            Toast.makeText(TakeExamActivity.this, "Error loading subject: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                    break;
                                }
                            }

                            if (!found) {
                                Toast.makeText(TakeExamActivity.this, "Exam not found in any teacher node", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(TakeExamActivity.this, "Error fetching exam data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TakeExamActivity.this, "Error fetching student info: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 🏆 NEW CRITICAL FUNCTION: Saves the score to the Scores table
    private void saveScoreToFirebase(int score, int maxScore) {
        DatabaseReference scoreEntryRef = FirebaseDatabase.getInstance()
                .getReference("Scores")
                .child(currentStudentUid)
                .child(examId);

        scoreEntryRef.child("score").setValue(score);
        scoreEntryRef.child("maxScore").setValue(maxScore);
        scoreEntryRef.child("timestamp").setValue(System.currentTimeMillis());
        // 🛑 Save the deduction count for history/reporting
        scoreEntryRef.child("deductions").setValue(totalDeductions);


        Log.d("TakeExam", "Score saved to Firebase: " + score + "/" + maxScore + ", Deductions: " + totalDeductions);
        Toast.makeText(this, "Score successfully recorded!", Toast.LENGTH_SHORT).show();
    }

    // 🛑 NEW: Runnable para i-check ang volume (UNCHANGED)
    private Runnable audioRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaRecorder != null) {
                try {
                    int amplitude = mediaRecorder.getMaxAmplitude();

                    if (amplitude > VOLUME_THRESHOLD) {
                        highVolumeCount++;
                        Log.w("AUDIO_CHEAT", "High volume detected! Count: " + highVolumeCount + ", Amplitude: " + amplitude);

                        if (highVolumeCount >= MAX_HIGH_VOLUME_TIME_COUNT) {
                            stopAudioMonitoring();
                            Toast.makeText(TakeExamActivity.this, "AUDIO CHEATING DETECTED: Unnecessary noise/voice detected. Submitting exam.", Toast.LENGTH_LONG).show();
                            submitExamWithZeroScore();
                            return;
                        }
                    } else {
                        if (highVolumeCount > 0) {
                            highVolumeCount = 0;
                        }
                    }

                } catch (IllegalStateException e) {
                    Log.e("AUDIO_CHECK", "MediaRecorder not ready: " + e.getMessage());
                }
            }
            audioHandler.postDelayed(this, AUDIO_DETECTION_INTERVAL);
        }
    };

    // 🛑 CRITICAL FIX: startAudioMonitoring
    private void startAudioMonitoring() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("AUDIO_CHECK", "Attempted to start monitoring without permission. Exiting.");
            return;
        }

        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            String tempFilePath = getCacheDir().getAbsolutePath() + "/temp_audio_monitor.3gp";
            mediaRecorder.setOutputFile(tempFilePath);

            try {
                mediaRecorder.prepare();
                mediaRecorder.start();
                Log.d("AUDIO_CHECK", "MediaRecorder started successfully.");

                highVolumeCount = 0;
                audioHandler.post(audioRunnable);
            } catch (IOException e) {
                Log.e("AUDIO_CHECK", "Failed to prepare MediaRecorder (IOException): " + e.getMessage());
                stopAudioMonitoring();
                submitExamWithZeroScore();
            } catch (IllegalStateException e) {
                Log.e("AUDIO_CHECK", "Failed to start MediaRecorder (IllegalStateException): " + e.getMessage());
                stopAudioMonitoring();
                submitExamWithZeroScore();
            } catch (RuntimeException e) {
                Log.e("AUDIO_CHECK", "MediaRecorder failed to start (Generic Runtime/ -1004): " + e.getMessage());
                stopAudioMonitoring();
                submitExamWithZeroScore();
            }
        }
    }

    // 🛑 CRITICAL FIX: stopAudioMonitoring
    private void stopAudioMonitoring() {
        audioHandler.removeCallbacks(audioRunnable);
        if (mediaRecorder != null) {
            try {
                try {
                    mediaRecorder.stop();
                } catch (IllegalStateException e) {
                    Log.w("AUDIO_CHECK", "MediaRecorder was not in a recording state to stop.");
                }

                mediaRecorder.release();
            } catch (Exception e) {
                Log.e("AUDIO_CHECK", "Error during MediaRecorder stop/release: " + e.getMessage());
            } finally {
                mediaRecorder = null;
                Log.d("AUDIO_CHECK", "MediaRecorder stopped and released.");
            }
        }
    }
}