package com.finale.nextgen.student;

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

import com.finale.nextgen.R;
import com.finale.nextgen.teacher.Question;
import com.finale.nextgen.offline.QuestionEntity; // <-- added
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
import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TakeExamActivity extends AppCompatActivity {

    private TextView tvExamTitle;
    private RecyclerView rvQuestions;
    private TakeExamAdapter questionAdapter;
    private List<Question> questionList = new ArrayList<>();

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

    // --- AUDIO CHEATING VARIABLES (Enhanced for Classroom) ---
    private int audioCheatingCount = 0;
    private final int MAX_AUDIO_STRIKES = 5; // Strikes before escalation
    private final float HIGH_CONFIDENCE_THRESHOLD = 0.70f; // Raised for classroom

    // Audio Detection
    private MediaRecorder mediaRecorder = null;
    private Handler audioHandler = new Handler();
    private static final int AUDIO_DETECTION_INTERVAL = 500; // 0.5s
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // TFLite Audio
    private AudioClassifier classifier;
    private TensorAudio tensorAudio;
    private android.media.AudioRecord audioRecord;
    private volatile boolean isAudioMonitoringActive = false;

    private List<String> allMatchingAnswers = new ArrayList<>();
    private boolean isShowingRules = false;
    private boolean isRequestingMicPermission = false;

    // NEW: set to true when we successfully loaded offline data on startup
    private volatile boolean offlineLoaded = false;

    // Robust Calibration
    private float ambientNoiseRms = 0f;
    private final int CALIBRATION_FRAMES = 12; // More samples for median
    private final int CALIBRATION_SLEEP_MS = 300;

    // Dual Threshold Parameters (Classroom-Tuned)
    private final float AMBIENT_MULTIPLIER = 3.5f; // Relative threshold
    private final float ABS_DELTA_THRESHOLD = 0.03f; // Absolute increase required (30mV equivalent)
    private final float SNR_RATIO_THRESHOLD = 4.0f; // frameRms must be 4x ambient
    private final float FALLBACK_MIC_LOUDNESS_THRESHOLD = 0.005f;

    // Temporal Smoothing (Sliding Window)
    private final int SLIDING_WINDOW_SIZE = 8; // Last 8 ticks
    private final int REQUIRED_POSITIVES = 5; // Need 5/8 to advance
    private boolean[] detectionWindow = new boolean[SLIDING_WINDOW_SIZE];
    private int windowIndex = 0;

    // Accumulation & Decay
    private final int REQUIRED_DETECTION_MS = 4000; // 4 seconds sustained
    private int accumulatedDetectionMs = 0;
    private final int DECAY_ON_NO_DETECT_MS = 500; // Decay slower
    private long lastStrikeTimestamp = 0L;
    private final long STRIKE_RESET_MS = 30_000L; // 30 seconds
    private final long MIN_TIME_BETWEEN_STRIKES_MS = 8_000L; // Minimum 8 seconds between strikes

    // Crash Protection
    private int classifierRestartAttempts = 0;
    private final int MAX_CLASSIFIER_RESTARTS = 1; // Allow one restart

    // Teacher Override
    private boolean teacherSpeakMode = false;
    private long teacherSpeakModeEndTime = 0L;
    private final long TEACHER_SPEAK_PAUSE_MS = 45_000L; // 45 seconds
    private DatabaseReference teacherSpeakRef;

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
        tvTimer = findViewById(R.id.tvTimer);

        examId = getIntent().getStringExtra("examId");
        examTitle = getIntent().getStringExtra("examTitle");

        // --- NEW: Load cached exam metadata (title + duration) so we can show title and start timer while offline ---
        new Thread(() -> {
            try {
                com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(TakeExamActivity.this);
                com.finale.nextgen.offline.ExamEntity examEntity = db.examDao().getExamById(examId);
                if (examEntity != null) {
                    runOnUiThread(() -> {
                        // Use cached title if the Intent didn't provide one
                        if (examTitle == null || examTitle.isEmpty()) {
                            examTitle = examEntity.examTitle;
                        }
                        tvExamTitle.setText("Exam: " + (examTitle != null ? examTitle : ""));

                        // Start timer if duration available and timer not already started
                        if (durationMinutes == 0 && examEntity.durationMinutes != null && examEntity.durationMinutes > 0) {
                            durationMinutes = examEntity.durationMinutes;
                            startTimer();
                        }
                    });
                } else {
                    // If examTitle was provided by Intent, still show it
                    runOnUiThread(() -> {
                        if (examTitle != null && !examTitle.isEmpty()) tvExamTitle.setText("Exam: " + examTitle);
                    });
                }
            } catch (Exception e) {
                Log.e("OfflineDebug", "Error loading cached exam metadata: " + e.getMessage());
            }
        }).start();

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentStudentUid = currentUser.getUid();

        // --- NEW: Try to load cached questions immediately (background thread) ---
        new Thread(() -> {
            try {
                com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(this);
                List<QuestionEntity> cached = db.questionDao().getQuestionsByExamId(examId);
                Log.d("OfflineDebug", "onCreate() preload - examId=" + examId + ", cached size=" + cached.size());

                if (!cached.isEmpty()) {
                    // Map & show on UI thread
                    runOnUiThread(() -> {
                        mapEntitiesToQuestionsAndShow(cached);
                        offlineLoaded = true;
                        Log.d("OfflineDebug", "onCreate() used cached data and set offlineLoaded=true");
                    });
                }
            } catch (Exception e) {
                Log.e("OfflineDebug", "Error preloading cached questions: " + e.getMessage());
            }
        }).start();
        // --- END NEW ---

        checkIfExamIsAlreadyTaken();
    }

    private void checkIfExamIsAlreadyTaken() {
        // Try local studentId first (offline-safe)
        String localStudentId = com.finale.nextgen.SessionManager.getStudentId(this);

        if (localStudentId != null && !localStudentId.isEmpty()) {
            new Thread(() -> {
                com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(TakeExamActivity.this);
                com.finale.nextgen.offline.PendingSubmission pending =
                        db.pendingSubmissionDao().findPendingByExamAndStudent(examId, localStudentId);

                runOnUiThread(() -> {
                    if (pending != null) {
                        // There is a local pending submission -> treat as already taken
                        Toast.makeText(TakeExamActivity.this, "You have already submitted this exam (pending sync).", Toast.LENGTH_LONG).show();

                        // Option A: redirect to results using local computed score
                        redirectToResultActivity(pending.computedScore, pending.maxScore);

                        // Option B (alternative): finish() to simply close the activity
                        // finish();
                    } else {
                        // No local pending -> fallback to server check (existing behaviour)
                        checkIfTakenOnServer();
                    }
                });
            }).start();
        } else {
            // No local studentId available -> fallback to server check directly
            checkIfTakenOnServer();
        }
    }

    // Existing server-side check (extracted to keep code clear)
    private void checkIfTakenOnServer() {
        // NOTE: your existing code used currentStudentUid for Scores path; keep that for now
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
    private boolean isActivityAlive() {
        // isDestroyed() exists on API 17+; safe to call on modern projects
        return !isFinishing() && !isDestroyed();
    }

    private void showExamRulesAlert() {
        // Ensure the activity is alive before attempting to show a dialog
        if (!isActivityAlive()) return;

        runOnUiThread(() -> {
            if (!isActivityAlive()) return;

            try {
                new AlertDialog.Builder(TakeExamActivity.this)
                        .setTitle("IMPORTANT: Exam Rules & Anti-Cheating")
                        .setMessage("By pressing START, you agree to the following rules:\n\n" +
                                "1. DO NOT EXIT THE APP (Switching or minimizing will deduct points).\n" +
                                "2. DO NOT USE SPLIT-SCREEN or MULTI-WINDOW mode.\n" +
                                "3. The PHONE'S BACK BUTTON is DISABLED.\n" +
                                "4. The In-App Back Arrow will deduct " + DEDUCTION_PER_STRIKE + " point(s) upon press.\n" +
                                "5. Your microphone will be monitored for HUMAN voice (Speech/Whispering) only.\n\n" +
                                "Exceeding " + MAX_SWITCHES + " screen/navigation violations or " + MAX_AUDIO_STRIKES + " audio strikes will result in automatic submission with a score of zero (0)."
                        )
                        .setPositiveButton("START EXAM", (dialog, which) -> {
                            if (isActivityAlive()) startExamLoadingProcessContinued();
                        })
                        .setCancelable(false)
                        .show();
            } catch (WindowManager.BadTokenException e) {
                // Activity probably finished before the dialog could be shown -> ignore safely
                Log.w("TakeExamActivity", "Could not show rules dialog: activity not running", e);
            } catch (Exception e) {
                Log.e("TakeExamActivity", "Unexpected error showing rules dialog", e);
            }
        });
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

        // IMPORTANT: only call loadQuestions() if we haven't already loaded offline cache in onCreate()
        if (!offlineLoaded) {
            loadQuestions();
        } else {
            Log.d("OfflineDebug", "offlineLoaded already true - skipping loadQuestions()");
            // We still want audio permission request / submit listener set
            checkAndRequestAudioPermission();
        }

        // -----------------------------
        // ✅ Listen for exam reset
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        studentsRef.orderByChild("uid").equalTo(currentStudentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if(snapshot.exists()){
                            for(DataSnapshot ds : snapshot.getChildren()){
                                String studentId = ds.child("studentId").getValue(String.class);
                                listenForExamReset(studentId);
                                listenForTeacherSpeak(studentId);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
        // -----------------------------
    }

    private void checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            isRequestingMicPermission = true;
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
    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()) return;
        if (isShowingRules || isRequestingMicPermission) return;

        switchCount++;
        totalDeductions += DEDUCTION_PER_STRIKE;

        // Stop audio monitoring to preserve resources and ensure restart upon resume
        stopAudioMonitoring();

        // Always show Toast for feedback
        if (switchCount >= MAX_SWITCHES) {
            Toast.makeText(this, "Cheating detected! Auto-submitting exam.", Toast.LENGTH_LONG).show();
            // Try alert, but if app is backgrounded, submit immediately:
            try {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(TakeExamActivity.this)
                            .setTitle("Cheating Detected!")
                            .setMessage("You switched away from the exam too many times.\nYour exam will be auto-submitted with zero score.")
                            .setPositiveButton("OK", (dialog, which) -> {
                                dialog.dismiss();
                                submitExamWithZeroScore();
                            })
                            .setCancelable(false)
                            .show();
                });
            } catch (Exception e) {
                submitExamWithZeroScore();
            }
        } else {
            Toast.makeText(this, "WARNING: Switching apps detected. " + (MAX_SWITCHES - switchCount) + " attempts left.", Toast.LENGTH_LONG).show();
            try {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(TakeExamActivity.this)
                            .setTitle("Warning: App Switching Detected")
                            .setMessage("Switching apps or minimizing during the exam is NOT allowed.\nAttempts left: " +
                                    (MAX_SWITCHES - switchCount))
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .setCancelable(true)
                            .show();
                });
            } catch (Exception e) {
                // Ignore dialog failure, Toast is visible
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;

        // Restart audio monitoring so cheating detection resumes even after tab/app switch
        startAudioMonitoring();

        // If cheating threshold was reached while app was in BG, finish immediately:
        if (switchCount >= MAX_SWITCHES) {
            Toast.makeText(this, "Cheating detected! Auto-submitting exam.", Toast.LENGTH_LONG).show();
            runOnUiThread(() -> {
                try {
                    new AlertDialog.Builder(TakeExamActivity.this)
                            .setTitle("Cheating Detected!")
                            .setMessage("You switched away from the exam too many times. Your exam will be auto-submitted with zero score.")
                            .setPositiveButton("OK", (dialog, which) -> {
                                dialog.dismiss();
                                submitExamWithZeroScore();
                            })
                            .setCancelable(false)
                            .show();
                } catch (Exception e) {
                    submitExamWithZeroScore();
                }
            });
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInMultiWindowMode()) {
            if (countDownTimer != null) countDownTimer.cancel();
            stopAudioMonitoring();
            Toast.makeText(this, "CHEATING DETECTED: Split-screen mode. Auto-submitting.", Toast.LENGTH_LONG).show();
            runOnUiThread(() -> {
                try {
                    new AlertDialog.Builder(TakeExamActivity.this)
                            .setTitle("Cheating Detected!")
                            .setMessage("Split-screen/multi-window mode is not allowed during the exam. Your exam will now be auto-submitted with zero score.")
                            .setPositiveButton("OK", (dialog, which) -> {
                                dialog.dismiss();
                                submitExamWithZeroScore();
                            })
                            .setCancelable(false)
                            .show();
                } catch (Exception e) {
                    submitExamWithZeroScore();
                }
            });
            return;
        }

        if (switchCount > 0 && switchCount < MAX_SWITCHES) {
            Toast.makeText(this, "Welcome back. Switching apps is monitored.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (questionList.isEmpty() || countDownTimer == null) {
                Toast.makeText(this, "Exam finished. Use Submit button.", Toast.LENGTH_SHORT).show();
                return true;
            }

            switchCount++;
            totalDeductions += DEDUCTION_PER_STRIKE;

            if (switchCount >= MAX_SWITCHES) {
                Toast.makeText(this, "CHEATING DETECTED: Auto-submitting.", Toast.LENGTH_LONG).show();
                runOnUiThread(() -> {
                    try {
                        new AlertDialog.Builder(TakeExamActivity.this)
                                .setTitle("Cheating Detected!")
                                .setMessage("You navigated away from the exam too many times.\nYour exam will be auto-submitted with zero score.")
                                .setPositiveButton("OK", (dialog, which) -> {
                                    dialog.dismiss();
                                    submitExamWithZeroScore();
                                })
                                .setCancelable(false)
                                .show();
                    } catch (Exception e) {
                        submitExamWithZeroScore();
                    }
                });
            } else {
                Toast.makeText(this, "In-app Back Arrow detected. " + (MAX_SWITCHES - switchCount) + " attempts left.", Toast.LENGTH_LONG).show();
                try {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(TakeExamActivity.this)
                                .setTitle("Warning: In-App Back Arrow Detected")
                                .setMessage("Using the back arrow during an exam is NOT allowed.\nAttempts left: " +
                                        (MAX_SWITCHES - switchCount))
                                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                                .setCancelable(true)
                                .show();
                    });
                } catch (Exception e) {
                    // Ignore dialog failure
                }
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
        new Thread(() -> {
            // Get the local Room database instance
            com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(this);

            // Query for cached questions by examId
            List<com.finale.nextgen.offline.QuestionEntity> cachedQuestions = db.questionDao().getQuestionsByExamId(examId);

            // ✅ Add debug log here
            Log.d("OfflineDebug", "examId=" + examId + ", cached size=" + cachedQuestions.size());

            runOnUiThread(() -> {
                if (!cachedQuestions.isEmpty()) {
                    // Found cached questions: convert to Question objects for UI
                    mapEntitiesToQuestionsAndShow(cachedQuestions);
                    offlineLoaded = true; // mark it
                } else if (isNetworkAvailable()) {
                    // Not cached, online: fetch from Firebase and cache
                    fetchQuestionsFromFirebaseAndCache(examId);
                } else {
                    // Not cached and offline: show message
                    Toast.makeText(this,
                            "Questions not yet downloaded. Connect to WiFi or Data before taking this exam.",
                            Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        }).start();
    }

    private boolean hasNextNonEmptySection() {
        // Look for any questions that belong to a later section type
        for (int i = typeIndex + 1; i < questionTypeOrder.length; i++) {
            String futureType = questionTypeOrder[i];
            for (Question q : questionList) {
                if (q.getQuestionType() != null && q.getQuestionType().equalsIgnoreCase(futureType)) {
                    return true;
                }
            }
        }
        return false;
    }

    // 2) Helper: check whether all questions have been answered
    private boolean allQuestionsAnswered() {
        for (Question q : questionList) {
            String ans = q.getStudentAnswer();
            if (ans == null || ans.trim().isEmpty()) return false;
        }
        return true;
    }

    // 3) Confirmation dialog before final submission (handles unanswered case)
    private void maybeConfirmSubmit() {
        if (!isActivityAlive()) return;

        runOnUiThread(() -> {
            if (!isActivityAlive()) return;

            if (allQuestionsAnswered()) {
                try {
                    new AlertDialog.Builder(TakeExamActivity.this)
                            .setTitle("Confirm Submit")
                            .setMessage("Are you sure you want to submit? You won't be able to change your answers.")
                            .setPositiveButton("Submit", (dialog, which) -> submitExam())
                            .setNegativeButton("Cancel", null)
                            .setCancelable(true)
                            .show();
                } catch (WindowManager.BadTokenException e) {
                    Log.w("TakeExamActivity", "Confirm dialog not shown: activity not running", e);
                }
            } else {
                int unanswered = 0;
                for (Question q : questionList) {
                    String ans = q.getStudentAnswer();
                    if (ans == null || ans.trim().isEmpty()) unanswered++;
                }
                try {
                    new AlertDialog.Builder(TakeExamActivity.this)
                            .setTitle("Unanswered Questions")
                            .setMessage("You have " + unanswered + " unanswered question(s). Do you want to submit anyway?")
                            .setPositiveButton("Submit Anyway", (dialog, which) -> submitExam())
                            .setNegativeButton("Go Back", null)
                            .setCancelable(true)
                            .show();
                } catch (WindowManager.BadTokenException e) {
                    Log.w("TakeExamActivity", "Unanswered confirm dialog not shown: activity not running", e);
                }
            }
        });
    }


    // Put this helper method in your activity as well:
    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    // And the helper for mapping and showing:
    private void mapEntitiesToQuestionsAndShow(List<com.finale.nextgen.offline.QuestionEntity> list) {
        questionList.clear();
        allMatchingAnswers.clear();

        Log.d("OfflineDebug", "Mapping cached questions to Question objects. Total: " + list.size());

        for (com.finale.nextgen.offline.QuestionEntity qe : list) {
            // Log each question for debugging
            Log.d("OfflineDebug", "Question text: " + qe.questionText);
            Log.d("OfflineDebug", "Question type (raw): " + qe.questionType);

            Question q = new Question();
            q.setQuestionText(qe.questionText);
            q.setQuestionType(qe.questionType);
            q.setOptionA(qe.optionA);
            q.setOptionB(qe.optionB);
            q.setOptionC(qe.optionC);
            q.setOptionD(qe.optionD);
            q.setCorrectAnswer(qe.correctAnswer);
            q.setStudentAnswer(qe.studentAnswer);
            q.setDisplayNumber(qe.displayNumber);
            q.setMatchingOptions(qe.matchingOptions);

            questionList.add(q);

            // Collect all Matching Type answers
            if ("Matching Type".equalsIgnoreCase(q.getQuestionType())) {
                String answer = q.getCorrectAnswer();
                if (answer != null && !allMatchingAnswers.contains(answer)) {
                    allMatchingAnswers.add(answer);
                }
            }
        }

        // Debug log for Matching Type
        Log.d("OfflineDebug", "All Matching Type answers collected: " + allMatchingAnswers);

        // Setup first section/question
        typeIndex = 0;
        filterQuestionsByType(questionTypeOrder[typeIndex]);

        // Log filtered questions for this type
        Log.d("OfflineDebug", "Questions for first section (" + questionTypeOrder[typeIndex] + "): " + currentTypeQuestions.size());

        showNextQuestion();
        // At end of mapEntitiesToQuestionsAndShow(...) after showNextQuestion();
        offlineLoaded = true; // already set earlier in some places, but ensure it
        checkAndRequestAudioPermission();
    }

    // And the Firebase fetch/cache helper:
    private void fetchQuestionsFromFirebaseAndCache(String examId) {
        DatabaseReference questionsRef = FirebaseDatabase.getInstance().getReference("Questions").child(examId);
        questionsRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                List<com.finale.nextgen.offline.QuestionEntity> toCache = new ArrayList<>();
                for (com.google.firebase.database.DataSnapshot snap : snapshot.getChildren()) {
                    com.finale.nextgen.offline.QuestionEntity qe = snap.getValue(com.finale.nextgen.offline.QuestionEntity.class);
                    if (qe == null) qe = new com.finale.nextgen.offline.QuestionEntity();

                    // MUST set these so Room queries work later
                    qe.examId = examId;
                    qe.firebaseKey = snap.getKey();

                    // defensive field mapping (optional, keeps values correct)
                    if (snap.child("questionText").exists()) qe.questionText = snap.child("questionText").getValue(String.class);
                    if (snap.child("questionType").exists()) qe.questionType = snap.child("questionType").getValue(String.class);
                    if (snap.child("correctAnswer").exists()) qe.correctAnswer = snap.child("correctAnswer").getValue(String.class);
                    if (snap.child("optionA").exists()) qe.optionA = snap.child("optionA").getValue(String.class);
                    if (snap.child("optionB").exists()) qe.optionB = snap.child("optionB").getValue(String.class);
                    if (snap.child("optionC").exists()) qe.optionC = snap.child("optionC").getValue(String.class);
                    if (snap.child("optionD").exists()) qe.optionD = snap.child("optionD").getValue(String.class);
                    if (snap.child("displayNumber").exists()) {
                        Long dn = snap.child("displayNumber").getValue(Long.class);
                        if (dn != null) qe.displayNumber = dn.intValue();
                    }
                    if (snap.child("matchingOptions").exists()) {
                        List<String> mo = (List<String>) snap.child("matchingOptions").getValue();
                        qe.matchingOptions = mo;
                    }

                    toCache.add(qe);
                }

                // Save via your OfflineExamManager so old rows are cleared and new ones inserted
                new Thread(() -> {
                    com.finale.nextgen.offline.OfflineExamManager mgr = new com.finale.nextgen.offline.OfflineExamManager(TakeExamActivity.this);
                    mgr.saveQuestions(examId, toCache);

                    // map & show on UI thread
                    runOnUiThread(() -> {
                        mapEntitiesToQuestionsAndShow(toCache);
                        // mark offlineLoaded and ensure audio/submit wiring
                        offlineLoaded = true;
                        checkAndRequestAudioPermission();
                    });
                }).start();
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                Toast.makeText(TakeExamActivity.this, "Failed to load questions online: " + error.getMessage(), Toast.LENGTH_LONG).show();
                finish();
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
            goToNextTypeOrSubmit(); // Use OrSubmit to handle end-of-section/exam properly!
            return;
        }

        if (currentIndex < currentTypeQuestions.size()) {
            List<Question> singleQuestion = new ArrayList<>();
            Question currentQ = currentTypeQuestions.get(currentIndex);
            currentQ.setDisplayNumber(typeQuestionNumber);
            singleQuestion.add(currentQ);

            // Button label logic -- ensure this block is here!
            boolean hasNext = currentIndex < currentTypeQuestions.size() - 1;
            boolean hasNextSection = !hasNext && hasNextNonEmptySection();
            String buttonText;
            if (hasNext) buttonText = "Next";
            else if (hasNextSection) buttonText = "Next Section";
            else buttonText = "Submit";

            // Make sure totalQuestions is the total for the exam (ex: questionList.size() or fullQuestionList.size())
            int totalQuestions = questionList.size(); // or whatever holds the full count for the exam
            questionAdapter = new TakeExamAdapter(this, singleQuestion, allMatchingAnswers, buttonText, totalQuestions);
            questionAdapter.setOnActionListener((position, actionString) -> {
                if ("Next".equalsIgnoreCase(actionString)) {
                    moveToNext();
                } else if ("Next Section".equalsIgnoreCase(actionString)) {
                    goToNextTypeOrSubmit();
                } else if (actionString.startsWith("Submit")) {
                    maybeConfirmSubmit();
                }
            });
            rvQuestions.setAdapter(questionAdapter);

        } else {
            goToNextTypeOrSubmit();
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
        }
    }

    // Paste these two methods into TakeExamActivity, replacing the existing submitExam() and submitExamWithZeroScore() methods.

    private void submitExam() {
        stopAudioMonitoring();

        if (questionList.isEmpty()) {
            Toast.makeText(this, "No questions to submit", Toast.LENGTH_SHORT).show();
            return;
        }




        int totalQuestions = questionList.size();
        int correctAnswers = 0;
        for (Question q : questionList) {
            String studentAns = q.getStudentAnswer();
            String correctAns = q.getCorrectAnswer();
            if (studentAns != null && correctAns != null &&
                    studentAns.trim().equalsIgnoreCase(correctAns.trim())) {
                correctAnswers++;
            }
        }

        int finalCalculatedScore = Math.max(correctAnswers - totalDeductions, 0);

        // Try local Student ID first (works offline)
        String localStudentId = com.finale.nextgen.SessionManager.getStudentId(this);
        if (localStudentId != null && !localStudentId.isEmpty()) {
            com.finale.nextgen.sync.SubmissionHelper.saveSubmissionLocallyAndEnqueue(
                    getApplicationContext(),
                    examId,
                    localStudentId,
                    questionList,
                    finalCalculatedScore,
                    totalQuestions
            );

            Toast.makeText(TakeExamActivity.this, "Submission saved locally and will sync when online.", Toast.LENGTH_LONG).show();
            redirectToResultActivity(finalCalculatedScore, totalQuestions);
            return;
        }

        // Fallback: lookup studentId from Firebase (network required)
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        studentsRef.orderByChild("uid").equalTo(currentStudentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String studentId = ds.child("studentId").getValue(String.class);
                                if (studentId == null || studentId.isEmpty()) {
                                    Toast.makeText(TakeExamActivity.this, "Student ID missing.", Toast.LENGTH_SHORT).show();

                                    return;
                                }

                                // Use the studentId read from Firebase
                                com.finale.nextgen.sync.SubmissionHelper.saveSubmissionLocallyAndEnqueue(
                                        getApplicationContext(),
                                        examId,
                                        studentId,
                                        questionList,
                                        finalCalculatedScore,
                                        totalQuestions
                                );

                                Toast.makeText(TakeExamActivity.this, "Submission saved and will sync when online.", Toast.LENGTH_LONG).show();
                                redirectToResultActivity(finalCalculatedScore, totalQuestions);
                                break; // stop after first match
                            }
                        } else {
                            Toast.makeText(TakeExamActivity.this, "Student ID not found online.", Toast.LENGTH_SHORT).show();


                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(TakeExamActivity.this, "Error fetching student ID.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void submitExamWithZeroScore() {
        stopAudioMonitoring();

        int maxScore = questionList.size();



        // Try local Student ID first (works offline)
        String localStudentId = com.finale.nextgen.SessionManager.getStudentId(this);
        if (localStudentId != null && !localStudentId.isEmpty()) {
            com.finale.nextgen.sync.SubmissionHelper.saveSubmissionLocallyAndEnqueue(
                    getApplicationContext(),
                    examId,
                    localStudentId,
                    questionList,
                    0,
                    maxScore
            );
            Toast.makeText(TakeExamActivity.this, "Zero-score submission saved locally and will sync when online.", Toast.LENGTH_LONG).show();
            redirectToResultActivity(0, maxScore);
            return;
        }

        // Fallback: lookup studentId from Firebase (network required)
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        studentsRef.orderByChild("uid").equalTo(currentStudentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String studentId = ds.child("studentId").getValue(String.class);
                                if (studentId == null || studentId.isEmpty()) {
                                    Toast.makeText(TakeExamActivity.this, "Student ID missing.", Toast.LENGTH_SHORT).show();


                                    return;
                                }

                                // Use the studentId read from Firebase (not localStudentId)
                                com.finale.nextgen.sync.SubmissionHelper.saveSubmissionLocallyAndEnqueue(
                                        getApplicationContext(),
                                        examId,
                                        studentId,
                                        questionList,
                                        0,
                                        maxScore
                                );
                                Toast.makeText(TakeExamActivity.this, "Zero-score submission saved locally and will sync when online.", Toast.LENGTH_LONG).show();
                                redirectToResultActivity(0, maxScore);
                                break;
                            }
                        } else {
                            Toast.makeText(TakeExamActivity.this, "Student ID not found online.", Toast.LENGTH_SHORT).show();


                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                        Toast.makeText(TakeExamActivity.this, "Error fetching student ID.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveScoreToFirebase(String studentId, int score, int maxScore) {
        DatabaseReference scoreEntryRef = FirebaseDatabase.getInstance()
                .getReference("Scores")
                .child(studentId)
                .child(examId);

        scoreEntryRef.child("score").setValue(score);
        scoreEntryRef.child("maxScore").setValue(maxScore);
        scoreEntryRef.child("timestamp").setValue(System.currentTimeMillis());
        scoreEntryRef.child("deductions").setValue(totalDeductions);
    }

    private void redirectToResultActivity(int score, int maxScore) {
        // Offline fallback: if no network, launch ResultActivity with local data only
        // Offline behavior: don't show result screen — return to dashboard and inform user
        if (!isNetworkAvailable()) {
            // Stop any audio/timers if still running
            if (countDownTimer != null) countDownTimer.cancel();
            stopAudioMonitoring();

            // Inform the student and return to dashboard
            Toast.makeText(TakeExamActivity.this,
                    "Submission saved locally and will sync when online. You will be returned to the dashboard.",
                    Toast.LENGTH_LONG).show();

            Intent intent = new Intent(TakeExamActivity.this, StudentDashboardActivity.class);
// include flag and pending exam id so dashboard can refresh and highlight
            intent.putExtra("fromSubmitPending", true);
            intent.putExtra("pendingExamId", examId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return;
        }
        // NOTE: ALL Firebase lookups for Student/Exam/Subject info are moved here.

        // Step 1: Get Student Info
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        studentsRef.orderByChild("uid").equalTo(currentStudentUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot studentSnap) {
                if (!studentSnap.exists()) {
                    Toast.makeText(TakeExamActivity.this, "Student info not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                for (DataSnapshot studentData : studentSnap.getChildren()) {
                    final String studentId = studentData.child("studentId").getValue(String.class);
                    final String fullName = studentData.child("fullName").getValue(String.class);
                    final String profileImage = studentData.child("profileImage").getValue(String.class);

                    // Step 2: Get Exam Info
                    DatabaseReference examsRef = FirebaseDatabase.getInstance().getReference("Exams");
                    examsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            boolean found = false;
                            for (DataSnapshot teacherSnap : snapshot.getChildren()) {
                                if (teacherSnap.hasChild(examId)) {
                                    found = true;
                                    DataSnapshot examSnap = teacherSnap.child(examId);

                                    final String subjectName = examSnap.child("subjectName").getValue(String.class);
                                    final String teacherName = examSnap.child("teacherName").getValue(String.class);

                                    // Step 3: Get Subject Code
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

                                            // Step 4: Final Intent Launch
                                            Intent intent = new Intent(TakeExamActivity.this, ResultActivity.class);

                                            // Student Info
                                            intent.putExtra("studentName", fullName);
                                            intent.putExtra("studentId", studentId);
                                            intent.putExtra("profileImage", profileImage);

                                            // Course Info
                                            intent.putExtra("courseCode", subjectCode);
                                            intent.putExtra("subjectName", subjectName);
                                            intent.putExtra("teacherName", teacherName);

                                            // Score Info
                                            intent.putExtra("examTitle", examTitle);
                                            // Note: Changed from "score" to "totalScore" for clarity/consistency
                                            intent.putExtra("totalScore", score);
                                            intent.putExtra("maxScore", maxScore);
                                            intent.putExtra("deductions", totalDeductions);

                                            startActivity(intent);
                                            finish();
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            Toast.makeText(TakeExamActivity.this, "Error loading subject: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                    return;
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

// ... (unchanged imports and class code above)

    // ----------- AUDIO MONITORING WITH HUMAN VOICE DETECTION -------------
    private void startAudioMonitoring() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w("AUDIO_TFLITE", "Cannot start audio monitoring: no permission");
            return;
        }
        
        // Prevent multiple concurrent starts
        if (isAudioMonitoringActive) {
            Log.d("AUDIO_TFLITE", "Audio monitoring already active, skipping start request.");
            return;
        }
        
        isAudioMonitoringActive = true;
        startAudioClassification();
    }


    private void stopAudioMonitoring() {
        if (!isAudioMonitoringActive) {
            Log.d("AUDIO_TFLITE", "Audio monitoring already stopped, skipping stop request.");
            return;
        }
        
        Log.d("AUDIO_TFLITE", "Stopping audio monitoring...");
        isAudioMonitoringActive = false;
        
        // Stop scheduled detection callbacks
        audioHandler.removeCallbacksAndMessages(null);

        // Remove teacher speak listener
        if (teacherSpeakRef != null) {
            try {
                teacherSpeakRef.removeEventListener((ValueEventListener) null);
            } catch (Exception ignored) {}
            teacherSpeakRef = null;
        }
        teacherSpeakMode = false;

        // Stop & release AudioRecord if created by classifier
        try {
            if (audioRecord != null) {
                try { audioRecord.stop(); } catch (Exception ignored) {}
                try { audioRecord.release(); } catch (Exception ignored) {}
                audioRecord = null;
                Log.d("AUDIO_TFLITE", "AudioRecord stopped and released.");
            }
        } catch (Exception e) {
            Log.w("AUDIO_TFLITE", "Error stopping/releasing audioRecord: " + e.getMessage());
        }

        // Legacy MediaRecorder cleanup (if used)
        if (mediaRecorder != null) {
            try { mediaRecorder.stop(); } catch (Exception ignored) {}
            try { mediaRecorder.release(); } catch (Exception ignored) {}
            mediaRecorder = null;
        }

        // Close classifier
        if (classifier != null) {
            try { classifier.close(); } catch (Exception ignored) {}
            classifier = null;
            Log.d("AUDIO_TFLITE", "Classifier closed.");
        }

        // Reset detection state so next start is fresh
        accumulatedDetectionMs = 0;
        lastStrikeTimestamp = 0L;
        // Keep audioCheatingCount persistent across pause/resume so strikes accumulate
        Log.d("AUDIO_TFLITE", "Audio monitoring stopped and state reset.");
    }

    // FINAL MODIFIED AUDIO MONITORING (sensitive only to CLOSE/loud voices)
    // ... (other unchanged imports and code)

    private void startAudioClassification() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w("AUDIO_TFLITE", "No RECORD_AUDIO permission; cannot start audio classification.");
            isAudioMonitoringActive = false;
            return;
        }

        // Prevent multiple simultaneous starts
        if (classifier != null || audioRecord != null) {
            Log.d("AUDIO_TFLITE", "Audio classification already running, skipping start.");
            return;
        }

        try {
            Log.d("AUDIO_TFLITE", "Starting audio classification...");
            AudioClassifier.AudioClassifierOptions options =
                    AudioClassifier.AudioClassifierOptions.builder()
                            .setMaxResults(1)
                            .setScoreThreshold(HIGH_CONFIDENCE_THRESHOLD)
                            .build();

            classifier = AudioClassifier.createFromFileAndOptions(this, "model.tflite", options);
            tensorAudio = classifier.createInputTensorAudio();
            Log.d("AUDIO_TFLITE", "Classifier created successfully.");

            // Reset state
            accumulatedDetectionMs = 0;
            lastStrikeTimestamp = 0L;
            detectionWindow = new boolean[SLIDING_WINDOW_SIZE];
            windowIndex = 0;

            // Create audioRecord safely
            try {
                audioRecord = classifier.createAudioRecord();
                audioRecord.startRecording();
                Log.d("AUDIO_TFLITE", "AudioRecord created and started successfully.");
            } catch (Exception e) {
                Log.e("AUDIO_TFLITE", "Failed to create/start audioRecord: " + e.getMessage());
                try { classifier.close(); } catch (Exception ignored) {}
                classifier = null;
                audioRecord = null;
                isAudioMonitoringActive = false;
                Toast.makeText(this, "Audio monitoring unavailable on this device.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Background calibration + detection
            new Thread(() -> {
                performRobustCalibration();
                startDetectionLoop();
            }).start();

        } catch (IOException e) {
            Log.e("AUDIO_TFLITE", "Failed to create classifier: " + e.getMessage());
            isAudioMonitoringActive = false;
            Toast.makeText(this, "Failed to load audio model.", Toast.LENGTH_SHORT).show();
        }
    }

    private void performRobustCalibration() {
        try {
            float[] rmsReadings = new float[CALIBRATION_FRAMES];
            int collected = 0;

            for (int i = 0; i < CALIBRATION_FRAMES; i++) {
                if (audioRecord == null) break;
                tensorAudio.load(audioRecord);
                float[] data = tensorAudio.getTensorBuffer().getFloatArray();
                rmsReadings[collected++] = computeRMS(data);
                try { Thread.sleep(CALIBRATION_SLEEP_MS); } catch (InterruptedException ignored) {}
            }

            if (collected > 0) {
                // Use median (more robust than mean)
                java.util.Arrays.sort(rmsReadings, 0, collected);
                ambientNoiseRms = rmsReadings[collected / 2];
            }

            if (ambientNoiseRms < 1e-5f) {
                ambientNoiseRms = FALLBACK_MIC_LOUDNESS_THRESHOLD * 0.6f;
            }

            Log.d("AUDIO_TFLITE", "Robust calibration complete. Ambient (median): " + ambientNoiseRms);
        } catch (Exception e) {
            Log.w("AUDIO_TFLITE", "Calibration failed: " + e.getMessage());
            ambientNoiseRms = FALLBACK_MIC_LOUDNESS_THRESHOLD * 0.6f;
        }
    }

    private void startDetectionLoop() {
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // Check if classifier is still valid before running tick
                    if (classifier == null || audioRecord == null) {
                        Log.w("AUDIO_TFLITE", "Classifier or audioRecord null; stopping detection loop.");
                        return; // Stop scheduling more ticks
                    }
                    
                    runDetectionTick();
                    
                    // Only schedule next tick if classifier is still valid
                    if (classifier != null && audioRecord != null) {
                        audioHandler.postDelayed(this, AUDIO_DETECTION_INTERVAL);
                    }
                } catch (Throwable t) {
                    // Catch native crashes (SIGABRT, etc.)
                    Log.e("AUDIO_TFLITE", "FATAL ERROR in detection loop: " + t.getMessage(), t);
                    handleClassifierCrash();
                    return; // Stop this loop
                }
            }
        });
    }

    private void runDetectionTick() {
        if (classifier == null || audioRecord == null) {
            Log.w("AUDIO_TFLITE", "Classifier or audioRecord null in tick.");
            return;
        }

        // Check teacher speak mode
        long now = System.currentTimeMillis();
        if (teacherSpeakMode && now < teacherSpeakModeEndTime) {
            Log.d("AUDIO_TFLITE", "Detection paused (teacher speaking)");
            return; // Skip detection this tick
        } else if (teacherSpeakMode && now >= teacherSpeakModeEndTime) {
            teacherSpeakMode = false; // Auto-expire
        }

        // Load audio buffer
        tensorAudio.load(audioRecord);
        List<Classifications> results = classifier.classify(tensorAudio);

        float[] audioData = tensorAudio.getTensorBuffer().getFloatArray();
        float frameRms = computeRMS(audioData);

        // Adaptive threshold (relative)
        float relativeThreshold = Math.max(FALLBACK_MIC_LOUDNESS_THRESHOLD, ambientNoiseRms * AMBIENT_MULTIPLIER);

        // Dual checks
        float absoluteDelta = frameRms - ambientNoiseRms;
        float snrRatio = (ambientNoiseRms > 1e-5f) ? (frameRms / ambientNoiseRms) : 0f;

        boolean detectedSpeech = false;
        String detectedLabel = "";
        float detectedConfidence = 0f;

        if (!results.isEmpty()) {
            Classifications cls = results.get(0);
            if (!cls.getCategories().isEmpty()) {
                detectedLabel = cls.getCategories().get(0).getLabel();
                detectedConfidence = cls.getCategories().get(0).getScore();

                // Speech detection: label match + confidence + dual amplitude checks
                if ((detectedLabel.equalsIgnoreCase("human") || detectedLabel.equalsIgnoreCase("speech"))
                        && detectedConfidence >= HIGH_CONFIDENCE_THRESHOLD
                        && frameRms >= relativeThreshold
                        && absoluteDelta >= ABS_DELTA_THRESHOLD
                        && snrRatio >= SNR_RATIO_THRESHOLD) {
                    detectedSpeech = true;
                }
            }
        }

        // Sliding window smoothing
        detectionWindow[windowIndex] = detectedSpeech;
        windowIndex = (windowIndex + 1) % SLIDING_WINDOW_SIZE;

        int positiveCount = 0;
        for (boolean b : detectionWindow) if (b) positiveCount++;

        boolean windowTriggered = (positiveCount >= REQUIRED_POSITIVES);

        // Log comprehensive diagnostics
        Log.d("AUDIO_TFLITE", String.format(Locale.US,
                "Label=%s Conf=%.2f RMS=%.4f Ambient=%.4f Delta=%.4f SNR=%.2f RelThresh=%.4f Window=%d/%d AccumMs=%d Strikes=%d",
                detectedLabel, detectedConfidence, frameRms, ambientNoiseRms, absoluteDelta, snrRatio,
                relativeThreshold, positiveCount, REQUIRED_POSITIVES, accumulatedDetectionMs, audioCheatingCount));

        // Accumulate or decay
        if (windowTriggered) {
            accumulatedDetectionMs += AUDIO_DETECTION_INTERVAL;
        } else {
            accumulatedDetectionMs = Math.max(0, accumulatedDetectionMs - DECAY_ON_NO_DETECT_MS);
        }

        // Register strike when threshold reached AND minimum time gap has passed
        if (accumulatedDetectionMs >= REQUIRED_DETECTION_MS) {
            long timeSinceLastStrike = now - lastStrikeTimestamp;
            
            // Only register strike if enough time has passed since last one
            if (lastStrikeTimestamp == 0L || timeSinceLastStrike >= MIN_TIME_BETWEEN_STRIKES_MS) {
                audioCheatingCount++;
                lastStrikeTimestamp = now;
                accumulatedDetectionMs = 0; // Reset window

                Log.w("AUDIO_TFLITE", String.format(Locale.US,
                        "⚠️ AUDIO STRIKE #%d | Label=%s Conf=%.2f RMS=%.4f Ambient=%.4f Delta=%.4f SNR=%.2f",
                        audioCheatingCount, detectedLabel, detectedConfidence, frameRms, ambientNoiseRms, absoluteDelta, snrRatio));

                // Log to Firebase
                logAudioEventToFirebase(detectedLabel, detectedConfidence, frameRms, ambientNoiseRms, absoluteDelta, snrRatio);

                // Handle strike (warnings, deductions, escalation)
                handleRegisteredAudioStrike(detectedLabel, detectedConfidence, frameRms);
            } else {
                // Too soon after last strike - ignore but log
                Log.d("AUDIO_TFLITE", "Strike suppressed (only " + (timeSinceLastStrike/1000) + "s since last strike, need " + (MIN_TIME_BETWEEN_STRIKES_MS/1000) + "s)");
                accumulatedDetectionMs = 0; // Still reset accumulation
            }
        }

        // Reset strikes after timeout
        if (audioCheatingCount > 0 && (now - lastStrikeTimestamp) > STRIKE_RESET_MS) {
            Log.d("AUDIO_TFLITE", "Clearing audio strikes due to timeout (" + (STRIKE_RESET_MS/1000) + "s)");
            audioCheatingCount = 0;
        }
    }

    private void handleClassifierCrash() {
        runOnUiThread(() -> Toast.makeText(this, "Audio detector encountered an error.", Toast.LENGTH_SHORT).show());

        // Clean up safely
        Log.e("AUDIO_TFLITE", "Classifier crashed, cleaning up...");
        isAudioMonitoringActive = false; // Reset flag before stopping
        stopAudioMonitoring();

        // Attempt one restart if not exceeded
        if (classifierRestartAttempts < MAX_CLASSIFIER_RESTARTS) {
            classifierRestartAttempts++;
            Log.w("AUDIO_TFLITE", "Attempting classifier restart (" + classifierRestartAttempts + "/" + MAX_CLASSIFIER_RESTARTS + ")");
            audioHandler.postDelayed(() -> {
                if (!isFinishing()) {
                    Log.d("AUDIO_TFLITE", "Restarting audio monitoring after crash...");
                    startAudioMonitoring();
                }
            }, 2000); // Wait 2s before restart
        } else {
            Log.e("AUDIO_TFLITE", "Max classifier restarts reached. Audio monitoring disabled.");
        }
    }

    // Helper: RMS calculation (works well as amplitude metric)
    private float computeRMS(float[] buffer) {
        if (buffer == null || buffer.length == 0) return 0f;
        double sumSq = 0d;
        for (float v : buffer) {
            sumSq += v * v;
        }
        double meanSq = sumSq / buffer.length;
        return (float) Math.sqrt(meanSq);
    }

    private void logAudioEventToFirebase(String label, float confidence, float frameRms,
                                         float ambient, float delta, float snr) {
        String localStudentId = com.finale.nextgen.SessionManager.getStudentId(this);
        if (localStudentId == null || localStudentId.isEmpty()) return;

        DatabaseReference logRef = FirebaseDatabase.getInstance()
                .getReference("ExamAudioLogs")
                .child(examId)
                .child(localStudentId)
                .child("events")
                .push();

        java.util.HashMap<String, Object> eventData = new java.util.HashMap<>();
        eventData.put("timestamp", System.currentTimeMillis());
        eventData.put("label", label);
        eventData.put("confidence", confidence);
        eventData.put("frameRms", frameRms);
        eventData.put("ambientRms", ambient);
        eventData.put("absoluteDelta", delta);
        eventData.put("snrRatio", snr);
        eventData.put("strikeNumber", audioCheatingCount);
        eventData.put("deviceModel", android.os.Build.MODEL);
        eventData.put("androidVersion", android.os.Build.VERSION.SDK_INT);
        eventData.put("accumulatedMs", accumulatedDetectionMs);
        
        // Generate interpretation and category based on metrics
        String category = generateCategory(delta, frameRms, ambient, snr);
        String interpretation = generateInterpretation(delta, frameRms, ambient, snr, confidence, accumulatedDetectionMs);
        int suspicionLevel = calculateSuspicionLevel(delta, frameRms, ambient, snr, confidence);
        
        eventData.put("category", category);
        eventData.put("interpretation", interpretation);
        eventData.put("suspicionLevel", suspicionLevel);
        
        // Add contextual information about what likely happened
        String audioContext = generateAudioContext(category, suspicionLevel, confidence, snr);
        eventData.put("audioContext", audioContext);
        
        // Transcription placeholder (real transcription requires SpeechRecognizer - see guide)
        String transcription = generateTranscriptionPlaceholder(suspicionLevel, category);
        eventData.put("transcription", transcription);

        logRef.setValue(eventData).addOnFailureListener(e ->
                Log.w("AUDIO_TFLITE", "Failed to log audio event: " + e.getMessage()));
    }
    
    private String generateCategory(float delta, float frameRms, float ambient, float snr) {
        if (delta > 0.08f && frameRms > 0.15f) {
            return "Very Close Speech";
        } else if (delta > 0.05f && frameRms > 0.08f) {
            return "Nearby Conversation";
        } else if (delta > 0.03f && frameRms > 0.04f) {
            return "Moderate Distance Speech";
        } else if (frameRms > ambient * 3) {
            return "Distant Speech/Announcement";
        } else {
            return "Ambient Noise";
        }
    }
    
    private String generateInterpretation(float delta, float frameRms, float ambient, float snr, float confidence, int accumMs) {
        StringBuilder sb = new StringBuilder();
        
        // Proximity
        if (delta > 0.08f && frameRms > 0.15f) {
            sb.append("📍 Very close to device (within 1-2 feet). ");
        } else if (delta > 0.05f && frameRms > 0.08f) {
            sb.append("📍 Nearby speech detected (2-4 feet away). ");
        } else if (delta > 0.03f && frameRms > 0.04f) {
            sb.append("📍 Moderate distance speech (4-8 feet). ");
        } else if (frameRms > ambient * 3) {
            sb.append("📍 Distant speech or classroom announcement. ");
        }
        
        // Signal clarity
        if (snr > 20f) {
            sb.append("Very clear audio signal. ");
        } else if (snr > 10f) {
            sb.append("Clear audio above background noise. ");
        } else if (snr > 4f) {
            sb.append("Audio slightly above background. ");
        } else {
            sb.append("Audio barely above background (possible false positive). ");
        }
        
        // Duration
        if (accumMs >= 4000) {
            sb.append("Sustained for 4+ seconds. ");
        } else if (accumMs >= 2000) {
            sb.append("Sustained for 2-4 seconds. ");
        }
        
        // Confidence
        if (confidence > 0.9f) {
            sb.append("Model very confident it's human speech.");
        } else if (confidence > 0.75f) {
            sb.append("Model confident it's human speech.");
        } else {
            sb.append("Model moderately confident.");
        }
        
        return sb.toString();
    }
    
    private int calculateSuspicionLevel(float delta, float frameRms, float ambient, float snr, float confidence) {
        int level = 3; // Start at moderate
        
        // Very close speech is highly suspicious
        if (delta > 0.08f && frameRms > 0.15f) {
            level = 5;
        } else if (delta > 0.05f && frameRms > 0.08f) {
            level = 4;
        } else if (delta > 0.03f && frameRms > 0.04f) {
            level = 3;
        } else {
            level = 2; // Distant or ambient
        }
        
        // Adjust based on SNR
        if (snr < 6f) {
            level = Math.max(1, level - 1); // Reduce suspicion for low SNR
        }
        
        // Adjust based on confidence
        if (confidence < 0.75f) {
            level = Math.max(1, level - 1); // Reduce suspicion for low confidence
        }
        
        return level;
    }
    
    private String generateAudioContext(String category, int suspicionLevel, float confidence, float snr) {
        StringBuilder sb = new StringBuilder();
        
        if (suspicionLevel >= 4) {
            sb.append("⚠️ High suspicion event. ");
            sb.append("Audio characteristics consistent with nearby human conversation. ");
        } else if (suspicionLevel == 3) {
            sb.append("⚠️ Moderate suspicion. ");
            sb.append("Could be student speech or environmental noise. ");
        } else {
            sb.append("ℹ️ Low suspicion. ");
            sb.append("Likely environmental noise or distant speech. ");
        }
        
        if (category.contains("Very Close") || category.contains("Nearby")) {
            sb.append("Proximity analysis indicates source was close to device (within 4 feet). ");
        } else if (category.contains("Moderate")) {
            sb.append("Proximity analysis indicates moderate distance (4-8 feet). ");
        } else {
            sb.append("Proximity analysis indicates distant source (8+ feet). ");
        }
        
        if (confidence > 0.85f && snr > 15f) {
            sb.append("High confidence human speech with clear signal.");
        } else if (confidence > 0.75f) {
            sb.append("Confident detection of human speech.");
        } else {
            sb.append("Moderate confidence - could be other sounds.");
        }
        
        return sb.toString();
    }
    
    private String generateTranscriptionPlaceholder(int suspicionLevel, String category) {
        // Note: Real transcription requires Android SpeechRecognizer or Google Cloud STT
        // This provides contextual information instead
        
        if (suspicionLevel >= 4) {
            return "[Speech detected - transcription not available. Pattern suggests nearby conversation. Teacher should review context and interview student if needed.]";
        } else if (suspicionLevel == 3) {
            return "[Speech detected at moderate distance - transcription unreliable. Could be legitimate environmental noise or student communication.]";
        } else {
            return "[Distant audio detected - likely classroom announcement, teacher instruction, or environmental noise. Transcription not feasible at this distance.]";
        }
    }

    private void handleRegisteredAudioStrike(String label, float confidence, float frameRms) {
        runOnUiThread(() -> {
            if (!isActivityAlive()) return;

            // Progressive response based on strike count
            if (audioCheatingCount == 1 || audioCheatingCount == 2) {
                // First two strikes: soft warning only
                Toast.makeText(this,
                        "⚠️ Audio detection: possible voice detected (" + audioCheatingCount + "/" + MAX_AUDIO_STRIKES + ")",
                        Toast.LENGTH_LONG).show();

            } else if (audioCheatingCount == 3) {
                // Third strike: warning + small deduction
                totalDeductions += 1;
                Toast.makeText(this,
                        "⚠️ Multiple audio detections. -1 point deduction applied.",
                        Toast.LENGTH_LONG).show();

                try {
                    new AlertDialog.Builder(this)
                            .setTitle("Audio Detection Warning")
                            .setMessage("Sustained voice has been detected multiple times.\n\n" +
                                    "• 1 point has been deducted.\n" +
                                    "• Further detections may result in exam submission.\n" +
                                    "• All events are logged for teacher review.")
                            .setPositiveButton("Understood", null)
                            .setCancelable(false)
                            .show();
                } catch (Exception e) {
                    Log.w("AUDIO_TFLITE", "Could not show warning dialog", e);
                }

            } else if (audioCheatingCount >= MAX_AUDIO_STRIKES) {
                // Max strikes reached: check combined evidence
                if (switchCount >= 2) {
                    // Combined evidence: audio + navigation violations → auto-submit
                    Toast.makeText(this,
                            "CHEATING DETECTED: Multiple audio + navigation violations. Auto-submitting.",
                            Toast.LENGTH_LONG).show();

                    try {
                        new AlertDialog.Builder(this)
                                .setTitle("Exam Auto-Submission")
                                .setMessage("Multiple cheating indicators detected:\n" +
                                        "• " + audioCheatingCount + " audio strikes\n" +
                                        "• " + switchCount + " navigation violations\n\n" +
                                        "Your exam will be submitted with zero score.")
                                .setPositiveButton("OK", (dialog, which) -> submitExamWithZeroScore())
                                .setCancelable(false)
                                .show();
                    } catch (Exception e) {
                        submitExamWithZeroScore();
                    }
                } else {
                    // Audio-only strikes: deduct more points but don't auto-submit (let teacher review)
                    totalDeductions += 2;
                    Toast.makeText(this,
                            "⚠️ Excessive audio detections. -2 additional points. Teacher will review.",
                            Toast.LENGTH_LONG).show();

                    Log.w("AUDIO_TFLITE", "Max audio strikes reached but insufficient combined evidence. Logging for teacher review.");
                }
            }
        });
    }

    private void listenForTeacherSpeak(String studentId) {
        teacherSpeakRef = FirebaseDatabase.getInstance()
                .getReference("ExamStudents")
                .child(examId)
                .child(studentId)
                .child("teacherSpeak");

        teacherSpeakRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean flag = snapshot.getValue(Boolean.class);
                if (flag != null && flag) {
                    teacherSpeakMode = true;
                    teacherSpeakModeEndTime = System.currentTimeMillis() + TEACHER_SPEAK_PAUSE_MS;
                    Log.d("AUDIO_TFLITE", "Teacher speak mode activated for " + (TEACHER_SPEAK_PAUSE_MS/1000) + "s");
                    runOnUiThread(() -> Toast.makeText(TakeExamActivity.this,
                            "Audio detection paused (teacher speaking)", Toast.LENGTH_SHORT).show());

                    // Auto-reset flag after timeout
                    audioHandler.postDelayed(() -> {
                        teacherSpeakRef.setValue(false);
                        teacherSpeakMode = false;
                    }, TEACHER_SPEAK_PAUSE_MS);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w("AUDIO_TFLITE", "Teacher speak listener cancelled: " + error.getMessage());
            }
        });
    }


    // ... (other unchanged code below)
    // ... (rest of your unchanged TakeExamActivity code)
    private void listenForExamReset(String studentId){
        DatabaseReference resetRef = FirebaseDatabase.getInstance()
                .getReference("ExamStudents")
                .child(examId)
                .child(studentId)
                .child("reset");

        resetRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean resetFlag = snapshot.getValue(Boolean.class);
                if(resetFlag != null && resetFlag){
                    // ✅ Reset detected! Handle it here
                    handleExamReset();

                    // Optional: remove the reset flag after handling
                    resetRef.setValue(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void handleExamReset(){
        // Stop timer & audio monitoring
        if(countDownTimer != null) countDownTimer.cancel();
        stopAudioMonitoring();

        // Reset question list
        for(Question q : questionList){
            q.setStudentAnswer(null); // clear student answers
        }

        currentIndex = 0;
        typeIndex = 0;
        typeQuestionNumber = 1;

        // Reset UI
        filterQuestionsByType(questionTypeOrder[typeIndex]);
        showNextQuestion();

        // Restart timer
        startTimer();

        Toast.makeText(this, "Exam has been reset by your teacher.", Toast.LENGTH_LONG).show();
    }
    private void handleNextOrSubmit() {
        if (currentTypeQuestions.isEmpty()) {
            goToNextTypeOrSubmit();
            return;
        }

        Question current = currentTypeQuestions.get(currentIndex);
        String answer = (current == null) ? null : current.getStudentAnswer();

        if (answer == null || answer.trim().isEmpty()) {
            if (!isActivityAlive()) return;
            runOnUiThread(() -> {
                if (!isActivityAlive()) return;
                try {
                    new AlertDialog.Builder(TakeExamActivity.this)
                            .setTitle("Unanswered Question")
                            .setMessage("You haven't answered this question yet. Please answer before moving on.")
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .setCancelable(true)
                            .show();
                } catch (WindowManager.BadTokenException e) {
                    Log.w("TakeExamActivity", "Unanswered dialog not shown: activity not running", e);
                }
            });
        } else {
            moveToNext();
        }
    }

    private void moveToNext() {
        if (currentIndex < currentTypeQuestions.size() - 1) {
            currentIndex++;
            typeQuestionNumber++;
            showQuestion(currentTypeQuestions.get(currentIndex));
        } else {
            goToNextTypeOrSubmit();
        }
    }
    private void goToNextTypeOrSubmit() {
        typeIndex++;
        if (typeIndex < questionTypeOrder.length) {
            filterQuestionsByType(questionTypeOrder[typeIndex]);
            if (!currentTypeQuestions.isEmpty()) {
                showQuestion(currentTypeQuestions.get(currentIndex));
            } else {
                // Skip empty section
                goToNextTypeOrSubmit();
            }
        } else {

            maybeConfirmSubmit();
        }
    }
    private void showQuestion(Question question) {
        // Set question display number for formatting (e.g., 1., 2., ...)
        question.setDisplayNumber(typeQuestionNumber);

        // Prepare a single-question list (for one-question-at-a-time navigation)
        List<Question> singleQuestion = new ArrayList<>();
        singleQuestion.add(question);

        // --- Button label logic:
        // "Next" = if more questions left in current section
        // "Next Section" = if this is last question of current section, but more section types exist
        // "Submit" = last question of last section
        boolean hasNext = currentIndex < currentTypeQuestions.size() - 1;
        boolean hasNextSection = !hasNext && hasNextNonEmptySection();
        String buttonText;
        if (hasNext) buttonText = "Next";
        else if (hasNextSection) buttonText = "Next Section";
        else buttonText = "Submit";

        // Create adapter and pass button text
        questionAdapter = new TakeExamAdapter(this, singleQuestion, allMatchingAnswers, buttonText, questionList.size());

        // Set the navigation callback for item button clicks
        questionAdapter.setOnActionListener((position, actionString) -> {
            if ("Next".equalsIgnoreCase(actionString)) {
                moveToNext();
            } else if ("Next Section".equalsIgnoreCase(actionString)) {
                goToNextTypeOrSubmit();
            } else if (actionString.startsWith("Submit")) {
                maybeConfirmSubmit();
            }
        });

        // Set the adapter
        rvQuestions.setAdapter(questionAdapter);
    }
}