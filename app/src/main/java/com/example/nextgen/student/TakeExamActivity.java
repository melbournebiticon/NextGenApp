package com.example.nextgen.student;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
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


import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier.AudioClassifierOptions;
import org.tensorflow.lite.task.audio.classifier.Classifications;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
// ----------------------------------

public class TakeExamActivity extends AppCompatActivity {

    private static final String TAG = "TakeExamActivity";

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

    // 🛑 AUDIO DETECTION VARIABLES (OLD MediaRecorder variables are REMOVED)

    // 🏆 TFLITE INTEGRATION: NEW AUDIO DETECTION VARIABLES
    private AudioClassifier classifier = null;
    private AudioRecord audioRecord = null;
    private Handler audioHandler = new Handler(); // Gagamitin pa rin ang Handler para sa loop
    private Runnable audioMonitorRunnable;
    private static final int CLASSIFICATION_INTERVAL = 1000; // Check every 1 second (1000ms)

    private final String MODEL_FILE = "yamnet.tflite";
    private final float SPEECH_CONFIDENCE_THRESHOLD = 0.70f;
    private final float MUSIC_CONFIDENCE_THRESHOLD = 0.65f;
    private final float NOISE_CONFIDENCE_THRESHOLD = 0.70f;
    private int speechStreak = 0;
    private int musicStreak = 0;
    private int noiseStreak = 0;
    private static final int MAX_HIGH_VOLUME_TIME_COUNT = 3; // 3 seconds count (3 * 1s interval)
    // -----------------------------------------------------------

    // 🛑 NEW: Request code for audio permission
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // 🏆 TFLITE INTEGRATION: Constant for permission
    private static final int RECORD_AUDIO_PERMISSION_CODE = 200;


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

        Log.d(TAG, "ExamID received: " + examId + ", ExamTitle: " + examTitle);

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
                Log.e(TAG, "Database error during score check: " + error.getMessage());
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
                        "5. Your microphone will be monitored for human speech (voices) and excessive noise.\n\n" +
                        "Exceeding " + MAX_SWITCHES + " violations will result in automatic submission with a score of zero (0)."
                )
                .setPositiveButton("START EXAM", (dialog, which) -> {
                    // Pagkatapos pindutin ang START EXAM, saka tuluyang simulan ang paglo-loading
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

    // 🛑 NEW FUNCTION: I-check at hiningi ang Audio Permission (UPDATED)
    private void checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_CODE);
        } else {
            // 🏆 TFLITE INTEGRATION: Call the new start function
            startAudioClassification();
        }
    }

    // 🛑 UPDATED: I-handle ang resulta ng Permission Request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 🏆 TFLITE INTEGRATION: Call the new start function
                startAudioClassification();
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
        // 🏆 TFLITE INTEGRATION: Call the new stop function
        stopAudioClassification();
    }

    // 🛑 LIFECYCLE METHOD: Detect App Switch/Tab Change (WITH DEDUCTION/STRIKE)
    @Override
    protected void onPause() {
        super.onPause();

        // 🏆 TFLITE INTEGRATION: Stop monitoring on pause
        stopAudioClassification();

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

        // 🏆 TFLITE INTEGRATION: Restart monitoring on resume
        if (checkAudioPermission()) { // Added checkAudioPermission helper
            startAudioClassification();
        }

        if (isFinishing()) return;

        // 🛑 NEW ANTI-CHEATING: RE-CHECK SPLIT-SCREEN
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInMultiWindowMode()) {
            if (countDownTimer != null) countDownTimer.cancel();
            stopAudioClassification(); // TFLite stop

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
                            Log.d(TAG, "Duration loaded from Firebase: " + durationMinutes + " minutes. Starting Timer.");
                            startTimer();
                        } else {
                            Log.d(TAG, "durationMinutes not found in Firebase. Timer will not start.");
                        }
                        break;
                    }
                }
                if (!found) {
                    Log.e(TAG, "Exam details not found in Firebase under any teacher node.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching exam details from Firebase: " + error.getMessage());
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
        Log.d(TAG, "Loaded " + currentTypeQuestions.size() + " questions for type: " + type);
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
        // 🏆 TFLITE INTEGRATION: Call the new stop function
        stopAudioClassification();

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
        // 🏆 TFLITE INTEGRATION: Call the new stop function
        stopAudioClassification();

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


        Log.d(TAG, "Score saved to Firebase: " + score + "/" + maxScore + ", Deductions: " + totalDeductions);
        Toast.makeText(this, "Score successfully recorded!", Toast.LENGTH_SHORT).show();
    }

    // 🏆 NEW HELPER METHOD: Check audio permission
    private boolean checkAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    // ===============================================
    // 🏆 TFLITE AUDIO CLASSIFICATION LOGIC (ADVANCED)
    // ===============================================

    private void startAudioClassification() {
        if (classifier != null) {
            return;
        }

        try {
            AudioClassifierOptions options = AudioClassifierOptions.builder().setMaxResults(5).build();
            classifier = AudioClassifier.createFromFileAndOptions(this, MODEL_FILE, options);
            audioRecord = classifier.createAudioRecord();
            audioRecord.startRecording();
            speechStreak = 0;

            audioMonitorRunnable = new Runnable() {
                @Override
                public void run() {
                    if (classifier != null && audioRecord != null &&
                            audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {

                        TensorAudio tensorAudio = classifier.createInputTensorAudio();
                        tensorAudio.load(audioRecord);
                        List<Classifications> classifications = classifier.classify(tensorAudio);

                        boolean detectedSpeech = false;
                        for (Classifications c : classifications) {
                            for (Category cat : c.getCategories()) {
                                String label = cat.getLabel().toLowerCase();
                                float score = cat.getScore();
                                if ((label.contains("speech") || label.contains("talking") || label.contains("conversation")) && score >= SPEECH_CONFIDENCE_THRESHOLD) {
                                    detectedSpeech = true;
                                    break;
                                }
                            }
                            if (detectedSpeech) break;
                        }

                        if (detectedSpeech) {
                            speechStreak++;
                            if (speechStreak >= MAX_HIGH_VOLUME_TIME_COUNT) {
                                speechStreak = 0;
                                switchCount++;
                                totalDeductions += DEDUCTION_PER_STRIKE;

                                runOnUiThread(() -> Toast.makeText(TakeExamActivity.this, "⛔ WARNING: Sustained human speech detected. " + DEDUCTION_PER_STRIKE + " point deducted.", Toast.LENGTH_LONG).show());

                                if (switchCount >= MAX_SWITCHES) {
                                    runOnUiThread(() -> Toast.makeText(TakeExamActivity.this, "CHEATING DETECTED: Exceeded max strikes! Auto-submitting.", Toast.LENGTH_LONG).show());
                                    submitExamWithZeroScore();
                                    return;
                                }
                            }
                        } else {
                            speechStreak = 0;
                        }
                    }
                    audioHandler.postDelayed(this, CLASSIFICATION_INTERVAL);
                }
            };
            audioHandler.post(audioMonitorRunnable);
            runOnUiThread(() -> Toast.makeText(this, "Audio monitoring started.", Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            classifier = null;
            Toast.makeText(this, "Proctoring error: Model failed to load.", Toast.LENGTH_LONG).show();
        } catch (SecurityException se) {
            Toast.makeText(this, "Microphone permission required.", Toast.LENGTH_LONG).show();
        }
    }

    private void startClassificationLoop() {
        // Remove previous runnable if any
        if (audioHandler != null && audioMonitorRunnable != null) {
            audioHandler.removeCallbacks(audioMonitorRunnable);
        }

        audioMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (classifier != null && audioRecord != null &&
                        audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {

                    TensorAudio tensorAudio = classifier.createInputTensorAudio();
                    tensorAudio.load(audioRecord);
                    List<Classifications> classifications = classifier.classify(tensorAudio);


                    // Process advanced classification
                    processAudioClassification(classifications);
                }

                // Schedule next check
                audioHandler.postDelayed(this, CLASSIFICATION_INTERVAL);
            }
        };

        audioHandler.post(audioMonitorRunnable);
        Log.d(TAG, "TFLite Classification Loop started.");
    }

    /**
     * Advanced audio classification processing:
     * - Detects speech (human voice), music, and loud noise categories (applause/laughter/vehicle/etc.)
     * - Maintains per-category streak counters to only penalize sustained events (e.g., 3s)
     * - On sustained violation: increments strike (switchCount), applies deduction, issues warning, and may auto-submit.
     */
    private void processAudioClassification(List<Classifications> classifications) {
        boolean localSpeechDetected = false;
        boolean localMusicDetected = false;
        boolean localNoiseDetected = false;

        // Determine top categories across all returned Classifications
        for (Classifications classification : classifications) {
            for (Category category : classification.getCategories()) {
                String label = category.getLabel().toLowerCase(Locale.getDefault());
                float score = category.getScore();

                // Speech / Talking / Voice detection
                if ((label.contains("speech") || label.contains("talking") || label.contains("voice") || label.contains("conversation"))
                        && score >= SPEECH_CONFIDENCE_THRESHOLD) {
                    localSpeechDetected = true;
                    Log.i("AUDIO_ADV", "Speech: " + label + " (" + String.format(Locale.getDefault(), "%.2f", score * 100) + "%)");
                    break; // speech found in this classification block
                }

                // Music detection (music, singing)
                if ((label.contains("music") || label.contains("singing") || label.contains("singer"))
                        && score >= MUSIC_CONFIDENCE_THRESHOLD) {
                    localMusicDetected = true;
                    Log.i("AUDIO_ADV", "Music: " + label + " (" + String.format(Locale.getDefault(), "%.2f", score * 100) + "%)");
                    // don't break here; we want to give priority to speech detection earlier
                }

                // Loud noise / applause / laughter / vehicle / siren / engine / construction / dog barking etc.
                if ((label.contains("applause") || label.contains("laughter") || label.contains("engine") ||
                        label.contains("vehicle") || label.contains("siren") || label.contains("construction") ||
                        label.contains("bark") || label.contains("dog") || label.contains("noise") || label.contains("alarm"))
                        && score >= NOISE_CONFIDENCE_THRESHOLD) {
                    localNoiseDetected = true;
                    Log.i("AUDIO_ADV", "Noise: " + label + " (" + String.format(Locale.getDefault(), "%.2f", score * 100) + "%)");
                }
            }
            if (localSpeechDetected) break; // speech has highest priority
        }

        // Update streaks (sustained detection across consecutive intervals)
        if (localSpeechDetected) {
            speechStreak++;
            musicStreak = 0;
            noiseStreak = 0;
        } else if (localMusicDetected) {
            musicStreak++;
            speechStreak = 0;
            noiseStreak = 0;
        } else if (localNoiseDetected) {
            noiseStreak++;
            speechStreak = 0;
            musicStreak = 0;
        } else {
            // no relevant detection -> reset all
            speechStreak = 0;
            musicStreak = 0;
            noiseStreak = 0;
        }

        // Check sustained speech violation
        if (speechStreak >= MAX_HIGH_VOLUME_TIME_COUNT) {
            // sustained speech -> treat as cheating indicator
            speechStreak = 0; // reset after handling
            switchCount++;
            totalDeductions += DEDUCTION_PER_STRIKE;

            Toast.makeText(this, "⛔ WARNING: Sustained human speech detected. " + DEDUCTION_PER_STRIKE + " point deducted.", Toast.LENGTH_LONG).show();
            Log.w("AUDIO_ADV", "Sustained speech strike. Total Deductions: " + totalDeductions + ", Strikes: " + switchCount);

            if (switchCount >= MAX_SWITCHES) {
                Toast.makeText(this, "CHEATING DETECTED: Exceeded max allowed strikes (Audio/Switching)! Auto-submitting.", Toast.LENGTH_LONG).show();
                submitExamWithZeroScore();
                return;
            }
        }

        // Check sustained music violation (student might be playing music)
        if (musicStreak >= MAX_HIGH_VOLUME_TIME_COUNT) {
            musicStreak = 0;
            switchCount++;
            totalDeductions += DEDUCTION_PER_STRIKE;

            Toast.makeText(this, "⛔ WARNING: Sustained music detected near your device. " + DEDUCTION_PER_STRIKE + " point deducted.", Toast.LENGTH_LONG).show();
            Log.w("AUDIO_ADV", "Sustained music strike. Total Deductions: " + totalDeductions + ", Strikes: " + switchCount);

            if (switchCount >= MAX_SWITCHES) {
                Toast.makeText(this, "CHEATING DETECTED: Exceeded max allowed strikes (Audio/Switching)! Auto-submitting.", Toast.LENGTH_LONG).show();
                submitExamWithZeroScore();
                return;
            }
        }

        // Check sustained loud noise violation (applause, laughter, siren, engine)
        if (noiseStreak >= MAX_HIGH_VOLUME_TIME_COUNT) {
            noiseStreak = 0;
            switchCount++;
            totalDeductions += DEDUCTION_PER_STRIKE;

            Toast.makeText(this, "⛔ WARNING: Sustained loud noise detected around you. " + DEDUCTION_PER_STRIKE + " point deducted.", Toast.LENGTH_LONG).show();
            Log.w("AUDIO_ADV", "Sustained noise strike. Total Deductions: " + totalDeductions + ", Strikes: " + switchCount);

            if (switchCount >= MAX_SWITCHES) {
                Toast.makeText(this, "CHEATING DETECTED: Exceeded max allowed strikes (Audio/Switching)! Auto-submitting.", Toast.LENGTH_LONG).show();
                submitExamWithZeroScore();
                return;
            }
        }

        // otherwise continue monitoring
    }

    private void stopAudioClassification() {
        if (audioHandler != null && audioMonitorRunnable != null) {
            audioHandler.removeCallbacks(audioMonitorRunnable);
        }
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping AudioRecord: " + e.getMessage());
            } finally {
                try {
                    audioRecord.release();
                } catch (Exception ex) {
                    Log.e(TAG, "Error releasing AudioRecord: " + ex.getMessage());
                }
                audioRecord = null;
            }
        }
        if (classifier != null) {
            try {
                classifier.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing classifier: " + e.getMessage());
            }
            classifier = null;
        }
        Log.i(TAG, "TFLite Audio Classification stopped.");
    }
}
