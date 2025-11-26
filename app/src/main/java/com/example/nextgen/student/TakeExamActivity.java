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
import com.example.nextgen.offline.QuestionEntity; // <-- added
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

    private List<String> allMatchingAnswers = new ArrayList<>();
    private boolean isShowingRules = false;
    private boolean isRequestingMicPermission = false;

    // NEW: set to true when we successfully loaded offline data on startup
    private volatile boolean offlineLoaded = false;

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

        // --- NEW: Load cached exam metadata (title + duration) so we can show title and start timer while offline ---
        new Thread(() -> {
            try {
                com.example.nextgen.offline.AppDatabase db = com.example.nextgen.offline.AppDatabase.getInstance(TakeExamActivity.this);
                com.example.nextgen.offline.ExamEntity examEntity = db.examDao().getExamById(examId);
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
                com.example.nextgen.offline.AppDatabase db = com.example.nextgen.offline.AppDatabase.getInstance(this);
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
        String localStudentId = com.example.nextgen.SessionManager.getStudentId(this);

        if (localStudentId != null && !localStudentId.isEmpty()) {
            new Thread(() -> {
                com.example.nextgen.offline.AppDatabase db = com.example.nextgen.offline.AppDatabase.getInstance(TakeExamActivity.this);
                com.example.nextgen.offline.PendingSubmission pending =
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
            btnSubmit.setOnClickListener(v -> handleNextOrSubmit());
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

        if (switchCount >= MAX_SWITCHES) {
            Toast.makeText(this, "Cheating detected! Auto-submitting exam.", Toast.LENGTH_LONG).show();
            submitExamWithZeroScore();
        } else {
            Toast.makeText(this, "WARNING: Switching apps detected. " + (MAX_SWITCHES - switchCount) + " attempts left.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInMultiWindowMode()) {
            if (countDownTimer != null) countDownTimer.cancel();
            stopAudioMonitoring();
            Toast.makeText(this, "CHEATING DETECTED: Split-screen mode. Auto-submitting.", Toast.LENGTH_LONG).show();
            submitExamWithZeroScore();
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
                submitExamWithZeroScore();
            } else {
                Toast.makeText(this, "In-app Back Arrow detected. " + (MAX_SWITCHES - switchCount) + " attempts left.", Toast.LENGTH_LONG).show();
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
            com.example.nextgen.offline.AppDatabase db = com.example.nextgen.offline.AppDatabase.getInstance(this);

            // Query for cached questions by examId
            List<com.example.nextgen.offline.QuestionEntity> cachedQuestions = db.questionDao().getQuestionsByExamId(examId);

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
    private void mapEntitiesToQuestionsAndShow(List<com.example.nextgen.offline.QuestionEntity> list) {
        questionList.clear();
        allMatchingAnswers.clear();

        Log.d("OfflineDebug", "Mapping cached questions to Question objects. Total: " + list.size());

        for (com.example.nextgen.offline.QuestionEntity qe : list) {
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
        btnSubmit.setOnClickListener(v -> handleNextOrSubmit());
    }

    // And the Firebase fetch/cache helper:
    private void fetchQuestionsFromFirebaseAndCache(String examId) {
        DatabaseReference questionsRef = FirebaseDatabase.getInstance().getReference("Questions").child(examId);
        questionsRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                List<com.example.nextgen.offline.QuestionEntity> toCache = new ArrayList<>();
                for (com.google.firebase.database.DataSnapshot snap : snapshot.getChildren()) {
                    com.example.nextgen.offline.QuestionEntity qe = snap.getValue(com.example.nextgen.offline.QuestionEntity.class);
                    if (qe == null) qe = new com.example.nextgen.offline.QuestionEntity();

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
                    com.example.nextgen.offline.OfflineExamManager mgr = new com.example.nextgen.offline.OfflineExamManager(TakeExamActivity.this);
                    mgr.saveQuestions(examId, toCache);

                    // map & show on UI thread
                    runOnUiThread(() -> {
                        mapEntitiesToQuestionsAndShow(toCache);
                        // mark offlineLoaded and ensure audio/submit wiring
                        offlineLoaded = true;
                        checkAndRequestAudioPermission();
                        btnSubmit.setOnClickListener(v -> handleNextOrSubmit());
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
            goToNextType();
            return;
        }

        if (currentIndex < currentTypeQuestions.size()) {
            List<Question> singleQuestion = new ArrayList<>();
            Question currentQ = currentTypeQuestions.get(currentIndex);
            currentQ.setDisplayNumber(typeQuestionNumber);
            singleQuestion.add(currentQ);

            questionAdapter = new TakeExamAdapter(TakeExamActivity.this, singleQuestion, allMatchingAnswers);
            rvQuestions.setAdapter(questionAdapter);

            // NEW: look ahead for actual non-empty sections
            if (currentIndex == currentTypeQuestions.size() - 1 && !hasNextNonEmptySection()) {
                btnSubmit.setText("Submit Exam");
            } else if (currentIndex == currentTypeQuestions.size() - 1) {
                btnSubmit.setText("Next Section");
            } else {
                btnSubmit.setText("Next");
            }

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
        }
    }

    // Paste these two methods into TakeExamActivity, replacing the existing submitExam() and submitExamWithZeroScore() methods.

    private void submitExam() {
        stopAudioMonitoring();

        if (questionList.isEmpty()) {
            Toast.makeText(this, "No questions to submit", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false); // prevent double taps

        int totalQuestions = questionList.size();
        int correctAnswers = 0;

        for (Question q : questionList) {
            String studentAns = q.getStudentAnswer();
            if (studentAns != null && q.getCorrectAnswer() != null && studentAns.equalsIgnoreCase(q.getCorrectAnswer())) {
                correctAnswers++;
            }
        }

        int finalCalculatedScore = Math.max(correctAnswers - totalDeductions, 0);

        // Try local Student ID first (works offline)
        String localStudentId = com.example.nextgen.SessionManager.getStudentId(this);
        if (localStudentId != null && !localStudentId.isEmpty()) {
            com.example.nextgen.sync.SubmissionHelper.saveSubmissionLocallyAndEnqueue(
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
                                    // allow retry by re-enabling the button
                                    btnSubmit.setEnabled(true);
                                    return;
                                }

                                // Use the studentId read from Firebase
                                com.example.nextgen.sync.SubmissionHelper.saveSubmissionLocallyAndEnqueue(
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
                            btnSubmit.setEnabled(true);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        btnSubmit.setEnabled(true);
                        Toast.makeText(TakeExamActivity.this, "Error fetching student ID.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void submitExamWithZeroScore() {
        stopAudioMonitoring();

        int maxScore = questionList.size();
        btnSubmit.setEnabled(false);

        // Try local Student ID first (works offline)
        String localStudentId = com.example.nextgen.SessionManager.getStudentId(this);
        if (localStudentId != null && !localStudentId.isEmpty()) {
            com.example.nextgen.sync.SubmissionHelper.saveSubmissionLocallyAndEnqueue(
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
                                    btnSubmit.setEnabled(true);
                                    return;
                                }

                                // Use the studentId read from Firebase (not localStudentId)
                                com.example.nextgen.sync.SubmissionHelper.saveSubmissionLocallyAndEnqueue(
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
                            btnSubmit.setEnabled(true);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        btnSubmit.setEnabled(true);
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

    // Tandaan: Ang FINAL_HUMAN_THRESHOLD ay dapat mo ring i-update sa taas ng TakeExamActivity.java
// Halimbawa: private final float FINAL_HUMAN_THRESHOLD = 0.75f;
// Kung wala ka pang variable sa taas, gamitin muna natin ang hardcoded value.

    private void startAudioClassification() {
        // **BAGONG FINAL THRESHOLD: 0.75f**
        final float NEW_HIGH_CONFIDENCE_THRESHOLD = 0.75f;

        try {
            // Tanging ang detections na may confidence na 0.75f pataas ang papayagan.
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
                                // Tandaan: Dahil 0.75f na ang global threshold, lahat ng lalabas na result dito ay 0.75f na.
                                if (label.equalsIgnoreCase("human") || label.equalsIgnoreCase("speech")) {

                                    audioCheatingCount++;
                                    Log.w("AUDIO_TFLITE", "!!! CHEATING STRIKE " + audioCheatingCount + ": " + label + " detected! Conf: " + confidence);

                                    // ⚠️ Show warning before auto-submitting
                                    if (audioCheatingCount < MAX_AUDIO_STRIKES) {
                                        runOnUiThread(() -> Toast.makeText(TakeExamActivity.this,
                                                "WARNING: Human voice detected! (" + audioCheatingCount + "/" + MAX_AUDIO_STRIKES + ")",
                                                Toast.LENGTH_SHORT).show());
                                    }

                                    // Auto-submit kapag umabot sa limit
                                    if (audioCheatingCount >= MAX_AUDIO_STRIKES) {
                                        Log.e("AUDIO_TFLITE", "!!! MAJOR CHEATING: Audio strike limit reached! Auto-submitting!");
                                        runOnUiThread(() -> submitExamWithZeroScore());
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
            updateButtonText();
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
                updateButtonText();
            } else {
                // Skip empty section
                goToNextTypeOrSubmit();
            }
        } else {
            // All sections done -> ask for confirmation (replaces direct submitExam())
            btnSubmit.setText("Submit Exam");
            maybeConfirmSubmit();
        }
    }
    private void showQuestion(Question question) {
        question.setDisplayNumber(typeQuestionNumber);
        List<Question> singleQuestion = new ArrayList<>();
        singleQuestion.add(question);

        questionAdapter = new TakeExamAdapter(this, singleQuestion, allMatchingAnswers);
        rvQuestions.setAdapter(questionAdapter);
    }
    private void updateButtonText() {
        if (currentIndex < currentTypeQuestions.size() - 1) {
            btnSubmit.setText("Next");
        } else if (hasNextNonEmptySection()) {
            btnSubmit.setText("Next Section");
        } else {
            btnSubmit.setText("Submit Exam");
        }
    }

}

