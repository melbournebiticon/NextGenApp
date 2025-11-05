package com.example.nextgen.student;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
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
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

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
    private int typeIndex = 0;
    private int typeQuestionNumber = 1;

    // Exam duration
    private int durationMinutes = 0;

    // Timer
    private CountDownTimer countDownTimer;
    private TextView tvTimer;
    private long timeLeftInMillis;

    // Firebase Auth
    private FirebaseAuth auth;
    private String currentStudentUid;

    // Anti-cheating (Screen/Navigation)
    private int switchCount = 0;
    private final int MAX_SWITCHES = 3;
    private int totalDeductions = 0;
    private final int DEDUCTION_PER_STRIKE = 1;

    // --- START: FINAL AUDIO CHEATING VARIABLES ---
    private int audioCheatingCount = 0;
    private final int MAX_AUDIO_STRIKES = 5; // 5 x 0.5s = 2.5 seconds of detected voice
    private final float FINAL_HUMAN_THRESHOLD = 0.75f; // Threshold for Speech/Human detection
    // --- END: FINAL AUDIO CHEATING VARIABLES ---

    // Audio Detection
    private MediaRecorder mediaRecorder = null;
    private Handler audioHandler = new Handler();
    private static final int AUDIO_DETECTION_INTERVAL = 500; // 0.5s
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // TFLite Audio
    private AudioClassifier classifier;
    private TensorAudio tensorAudio;

    private android.media.AudioRecord audioRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_exam);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        tvExamTitle = findViewById(R.id.tvExamTitle);
        rvQuestions = findViewById(R.id.rvQuestions);
        rvQuestions.setLayoutManager(new LinearLayoutManager(this));
        btnSubmit = findViewById(R.id.btnSubmitExam);
        tvTimer = findViewById(R.id.tvTimer);

        examId = getIntent().getStringExtra("examId");
        examTitle = getIntent().getStringExtra("examTitle");

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentStudentUid = currentUser.getUid();

        checkIfExamIsAlreadyTaken();
    }

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
                    showExamRulesAlert();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("TakeExam", "Database error: " + error.getMessage());
                showExamRulesAlert();
            }
        });
    }

    private void showExamRulesAlert() {
        new AlertDialog.Builder(this)
                .setTitle("IMPORTANT: Exam Rules & Anti-Cheating")
                .setMessage("By pressing START, you agree to the following rules:\n\n" +
                        "1. DO NOT EXIT THE APP (Switching or minimizing will deduct points).\n" +
                        "2. DO NOT USE SPLIT-SCREEN or MULTI-WINDOW mode.\n" +
                        "3. The PHONE'S BACK BUTTON is DISABLED.\n" +
                        "4. The In-App Back Arrow will deduct " + DEDUCTION_PER_STRIKE + " point(s) upon press.\n" +
                        "5. Your microphone will be monitored for HUMAN voice (Speech/Whispering) only.\n\n" +
                        "Exceeding " + MAX_SWITCHES + " screen/navigation violations or " + MAX_AUDIO_STRIKES + " audio strikes will result in automatic submission with a score of zero (0)."
                )
                .setPositiveButton("START EXAM", (dialog, which) -> startExamLoadingProcessContinued())
                .setCancelable(false)
                .show();
    }

    private void startExamLoadingProcessContinued() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInMultiWindowMode()) {
            Toast.makeText(this, "CHEATING DETECTED: Split-screen mode not allowed. Auto-submitting.", Toast.LENGTH_LONG).show();
            submitExamWithZeroScore();
            return;
        }

        tvExamTitle.setText("Exam: " + examTitle);
        fetchExamDetailsFromFirebase();
        questionsRef = FirebaseDatabase.getInstance().getReference("Questions").child(examId);
        loadQuestions();
        checkAndRequestAudioPermission();
        btnSubmit.setOnClickListener(v -> submitExam());
    }

    private void checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            startAudioMonitoring();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioMonitoring();
            } else {
                Toast.makeText(this, "Warning: Audio monitoring disabled. Microphone permission denied.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (countDownTimer != null) countDownTimer.cancel();
        stopAudioMonitoring();
    }

    /**
     * 🟡 1. App Switching Detection (onPause)
     * Nagbabago ang action kapag naabot ang MAX_SWITCHES.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing() || countDownTimer == null) return;

        switchCount++;
        totalDeductions += DEDUCTION_PER_STRIKE;

        if (switchCount >= MAX_SWITCHES) {
            // 💡 Palitan ang Toast/Auto-submit ng showCheatingAlert
            showCheatingAlert("You left the exam screen too many times. Cheating detected!");

            // Optional: Maaari mong panatilihin ang auto-submit dito kung yan ang policy
            // submitExamWithZeroScore(); // Kung gusto mo pa rin mag-auto-submit
        } else {
            // Ibinabalik ang Toast warning para malaman ng user na may deduction
            Toast.makeText(this, "WARNING: Switching apps detected. " + (MAX_SWITCHES - switchCount) + " attempts left. You have incurred a deduction.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 🟡 2. Split-Screen Detection (onResume)
     * Nagbabago ang action mula sa auto-submit patungo sa showCheatingAlert.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInMultiWindowMode()) {
            if (countDownTimer != null) countDownTimer.cancel();
            stopAudioMonitoring();

            // 💡 Palitan ang Toast/Auto-submit ng showCheatingAlert
            showCheatingAlert("Split screen or multi-window mode is not allowed. Cheating detected!");

            // Inalis ang submitExamWithZeroScore() at pinanatili ang return
            return;
        }

        if (switchCount > 0 && switchCount < MAX_SWITCHES) {
            Toast.makeText(this, "Welcome back. Switching apps is monitored.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 🟡 3. Back Button Press Detection
     * Nagiging simple alert na lang at inaalis ang counting logic.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (questionList.isEmpty() || countDownTimer == null) {
                Toast.makeText(this, "Exam finished. Use Submit button.", Toast.LENGTH_SHORT).show();
                return true;
            }


            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Back button disabled during exam.", Toast.LENGTH_LONG).show();
    }

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
                Toast.makeText(TakeExamActivity.this, "Time's up! Submitting exam.", Toast.LENGTH_LONG).show();
                submitExam();
            }
        }.start();
    }

    private void updateCountDownText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        String timeLeftFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        tvTimer.setText(timeLeftFormatted);
    }

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
                            startTimer();
                        }
                        break;
                    }
                }
                if (!found) Log.e("TakeExam", "Exam details not found in Firebase.");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("TakeExam", "Error fetching exam details: " + error.getMessage());
                Toast.makeText(TakeExamActivity.this, "Error fetching exam details.", Toast.LENGTH_SHORT).show();
            }
        });
    }

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

    private void submitExam() {
        if (countDownTimer != null) countDownTimer.cancel();
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

        int finalCalculatedScore = Math.max(correctAnswers - totalDeductions, 0);

        saveScoreToFirebase(finalCalculatedScore, totalQuestions);
        redirectToResultActivity(finalCalculatedScore, totalQuestions);
    }

    private void submitExamWithZeroScore() {
        if (countDownTimer != null) countDownTimer.cancel();
        stopAudioMonitoring();
        saveScoreToFirebase(0, questionList.size());
        redirectToResultActivity(0, questionList.size());
    }

    private void saveScoreToFirebase(int score, int maxScore) {
        DatabaseReference scoreEntryRef = FirebaseDatabase.getInstance()
                .getReference("Scores")
                .child(currentStudentUid)
                .child(examId);

        scoreEntryRef.child("score").setValue(score);
        scoreEntryRef.child("maxScore").setValue(maxScore);
        scoreEntryRef.child("timestamp").setValue(System.currentTimeMillis());
        scoreEntryRef.child("deductions").setValue(totalDeductions);
    }

    private void redirectToResultActivity(int score, int maxScore) {
        // Original redirect code unchanged (Firebase student info, exam info)
        Intent intent = new Intent(TakeExamActivity.this, ResultActivity.class);
        intent.putExtra("examTitle", examTitle);
        intent.putExtra("score", score);
        intent.putExtra("maxScore", maxScore);
        intent.putExtra("deductions", totalDeductions);
        startActivity(intent);
        finish();
    }

    // ----------- AUDIO MONITORING WITH HUMAN VOICE DETECTION -------------
    private void startAudioMonitoring() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        startAudioClassification();
    }

    private void stopAudioMonitoring() {
        audioHandler.removeCallbacksAndMessages(null);
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception ignored) {
            }
            mediaRecorder.release();
            mediaRecorder = null;
        }
        if (classifier != null) {
            classifier.close();
            classifier = null;
        }
        // I-reset ang audio counter kapag nag-submit o nag-stop ang monitoring
        audioCheatingCount = 0;
    }

    private void startAudioClassification() {
        final float NEW_HIGH_CONFIDENCE_THRESHOLD = 0.75f;

        try {
            AudioClassifier.AudioClassifierOptions options =
                    AudioClassifier.AudioClassifierOptions.builder()
                            .setMaxResults(1)
                            .setScoreThreshold(NEW_HIGH_CONFIDENCE_THRESHOLD) // Tumaas na threshold
                            .build();

            classifier = AudioClassifier.createFromFileAndOptions(this, "model.tflite", options);
            tensorAudio = classifier.createInputTensorAudio();
            audioRecord = classifier.createAudioRecord();
            audioRecord.startRecording();

            audioHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        tensorAudio.load(audioRecord);
                        List<Classifications> results = classifier.classify(tensorAudio);

                        if (!results.isEmpty()) {
                            Classifications classification = results.get(0);
                            if (!classification.getCategories().isEmpty()) {
                                String label = classification.getCategories().get(0).getLabel();
                                float confidence = classification.getCategories().get(0).getScore();

                                // ✅ FINAL CHEATING LOGIC: Human o Speech at Confidence >= 0.75f
                                if (label.equalsIgnoreCase("human") || label.equalsIgnoreCase("speech")) {

                                    audioCheatingCount++;
                                    Log.w("AUDIO_TFLITE", "!!! CHEATING STRIKE " + audioCheatingCount + ": " + label + " detected! Conf: " + confidence);

                                    // 🟡 4. Audio Detection Logic Changed
                                    if (audioCheatingCount >= MAX_AUDIO_STRIKES) {
                                        Log.w("AUDIO_TFLITE", "!!! MAJOR CHEATING: Audio strike limit reached! Showing alert.");
                                        // Kailangan gumamit ng runOnUiThread dahil nasa background thread tayo
                                        runOnUiThread(() -> {
                                            showCheatingAlert("We detected human speech during your exam. Please stay silent.");
                                            audioCheatingCount = 0; // Reset para hindi paulit-ulit
                                        });
                                    }

                                }
                                // ❌ IGNORED: Non-human sound, o Silence (i-reset ang counter)
                                else {
                                    audioCheatingCount = 0; // Reset strike count
                                    Log.d("AUDIO_TFLITE", "Ignored/Reset: Label: " + label + ", Conf: " + confidence);
                                }
                            }
                        }

                    } catch (Exception e) {
                        Log.e("AUDIO_TFLITE", "Error during audio classification: " + e.getMessage());
                    }

                    audioHandler.postDelayed(this, AUDIO_DETECTION_INTERVAL);
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load audio model.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 🟢 5. Alert Dialog Method
     * Ito ang magdi-display ng cheating alert message.
     */
    private void showCheatingAlert(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Cheating Detected")
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
