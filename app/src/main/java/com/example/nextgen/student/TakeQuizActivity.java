package com.example.nextgen.student;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.offline.QuestionEntity;
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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TakeQuizActivity - updated:
 * - Final availability guard before showing rules / allowing start (uses intent extras availableAt/durationMinutes if present)
 * - Normalizes timestamps (seconds -> milliseconds)
 * - Starts timer from intent duration if provided
 * - Keeps existing offline/pending checks and anti-cheating features
 * - Accepts deep links / scanned QR payloads to auto-mark present and open quiz
 */
public class TakeQuizActivity extends AppCompatActivity {

    private static final String TAG = "TakeQuiz";
    private static final long START_TOLERANCE_MS = 5_000L; // allow slight clock skew

    private TextView tvQuizTitle;
    private RecyclerView rvQuestions;
    private TakeExamAdapter questionAdapter; // Reuse same adapter as exam
    private List<Question> questionList = new ArrayList<>();


    private String quizId;
    private String quizName;
    private DatabaseReference questionsRef;

    // Navigation variables (same behavior as exam)
    private int currentIndex = 0;
    private String currentQuestionType = "Multiple Choice";
    private List<Question> currentTypeQuestions = new ArrayList<>();
    private final String[] questionTypeOrder = {"Multiple Choice", "True/False", "Matching Type"};
    private int typeIndex = 0;
    private int typeQuestionNumber = 1;

    // Duration & timer
    private int durationMinutes = 0;
    private CountDownTimer countDownTimer;
    private TextView tvTimer;
    private long timeLeftInMillis;

    // Firebase Auth
    private FirebaseAuth auth;
    private String currentStudentUid;

    // Anti-cheating
    private int switchCount = 0;
    private final int MAX_SWITCHES = 3;
    private int totalDeductions = 0;
    private final int DEDUCTION_PER_STRIKE = 1;

    // Audio cheating variables (same concept)
    private int audioCheatingCount = 0;
    private final int MAX_AUDIO_STRIKES = 5;
    private final float FINAL_HUMAN_THRESHOLD = 0.75f;

    // Audio monitoring
    private MediaRecorder mediaRecorder = null;
    private Handler audioHandler = new Handler();
    private static final int AUDIO_DETECTION_INTERVAL = 500; // ms
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 201;

    // TFLite audio
    private AudioClassifier classifier;
    private TensorAudio tensorAudio;
    private android.media.AudioRecord audioRecord;

    private List<String> allMatchingAnswers = new ArrayList<>();
    private boolean isShowingRules = false;
    private boolean isRequestingMicPermission = false;

    // offline loaded flag
    private volatile boolean offlineLoaded = false;

    // Intent-provided times (normalized)
    private long intentAvailableAt = 0L;
    private int intentDurationMinutes = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Prevent screenshots / screen-recording
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_exam); // reuse same layout as exam

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        tvQuizTitle = findViewById(R.id.tvExamTitle); // layout id reused
        rvQuestions = findViewById(R.id.rvQuestions);
        rvQuestions.setLayoutManager(new LinearLayoutManager(this));
        tvTimer = findViewById(R.id.tvTimer);

        // Read intent extras (quizId, quizName, optional availableAt, durationMinutes)
        quizId = getIntent().getStringExtra("quizId");
        if (quizId == null || quizId.isEmpty()) {
            // compat fallback
            quizId = getIntent().getStringExtra("examId");
        }

        quizName = getIntent().getStringExtra("quizName");
        if (quizName == null || quizName.isEmpty()) {
            quizName = getIntent().getStringExtra("quizTitle");
        }
        if (quizName == null) quizName = "(Untitled Quiz)";

        // Normalize intent timing extras if present
        Long rawAvailableAt = null;
        try {
            rawAvailableAt = getIntent().hasExtra("availableAt") ? getIntent().getLongExtra("availableAt", 0L) : null;
        } catch (Exception ignored) {
        }
        intentAvailableAt = normalizeTimestamp(rawAvailableAt);
        try {
            intentDurationMinutes = getIntent().hasExtra("durationMinutes") ? getIntent().getIntExtra("durationMinutes", 0) : 0;
        } catch (Exception ignored) {
            intentDurationMinutes = 0;
        }

        // If duration provided via intent and not set yet, use it
        if (intentDurationMinutes > 0 && durationMinutes == 0) {
            durationMinutes = intentDurationMinutes;
            startTimer(); // safe to start - will be cancelled/re-started later if updated from DB
        }

        // Check incoming deep link or scanned_text. If present, mark present first then continue normal flow.
        String incomingQuizId = parseQuizIdFromIntent(getIntent());
        if (incomingQuizId != null && !incomingQuizId.isEmpty()) {
            // override quizId with incoming value (if different)
            quizId = incomingQuizId;
            resolveStudentIdAndMarkPresent(quizId, this::continueNormalFlowAfterMark);
        } else {
            // no incoming scanned payload - continue normally
            continueNormalFlowAfterMark();
        }
    }

    /**
     * Continue the original onCreate flow after marking present (or immediately if not a scanned/opened deep link).
     * This method contains the original preloading and checks that were previously inside onCreate.
     */
    private void continueNormalFlowAfterMark() {
        // Try to preload cached quiz metadata (title + duration) to support offline start
        final String finalQuizId = quizId;
        new Thread(() -> {
            try {
                com.example.nextgen.offline.AppDatabase db = com.example.nextgen.offline.AppDatabase.getInstance(TakeQuizActivity.this);
                com.example.nextgen.offline.ExamEntity examEntity = db.examDao().getExamById(finalQuizId);
                if (examEntity != null) {
                    runOnUiThread(() -> {
                        if ((quizName == null || quizName.isEmpty()) && examEntity.examTitle != null) {
                            quizName = examEntity.examTitle;
                        }
                        tvQuizTitle.setText("Quiz: " + (quizName != null ? quizName : ""));
                        if (durationMinutes == 0 && examEntity.durationMinutes != null && examEntity.durationMinutes > 0) {
                            durationMinutes = examEntity.durationMinutes;
                            startTimer();
                        }
                    });
                } else {
                    runOnUiThread(() -> tvQuizTitle.setText("Quiz: " + quizName));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error preloading quiz metadata: " + e.getMessage());
            }
        }).start();

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            runOnUiThread(() -> {
                Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
                finish();
            });
            return;
        }
        currentStudentUid = currentUser.getUid();

        // Preload cached questions for this quiz
        new Thread(() -> {
            try {
                com.example.nextgen.offline.AppDatabase db = com.example.nextgen.offline.AppDatabase.getInstance(this);
                List<QuestionEntity> cached = db.questionDao().getQuestionsByExamId(quizId);
                Log.d("OfflineDebug", "TakeQuiz onCreate preload - quizId=" + quizId + ", cached size=" + cached.size());

                if (!cached.isEmpty()) {
                    runOnUiThread(() -> {
                        mapEntitiesToQuestionsAndShow(cached);
                        offlineLoaded = true;
                        Log.d("OfflineDebug", "TakeQuiz used cached data and set offlineLoaded=true");
                    });
                }
            } catch (Exception e) {
                Log.e("OfflineDebug", "Error preloading cached quiz questions: " + e.getMessage());
            }
        }).start();

        checkIfQuizIsAlreadyTaken();
    }

    // ---------- deep-link / scanned QR helpers ----------

    /**
     * Parse quizId from Intent data or scanned_text extra.
     * Supports:
     * - Intent data: nextgen://quiz/{quizId}
     * - scanned_text extra: "quiz:{quizId}" or deep link URL
     */
    private String parseQuizIdFromIntent(Intent intent) {
        if (intent == null) return null;

        // 1) If activity was opened via VIEW intent with data (deep link)
        try {
            Uri data = intent.getData();
            if (data != null) {
                String scheme = data.getScheme();
                String host = data.getHost();
                if ("nextgen".equalsIgnoreCase(scheme) && "quiz".equalsIgnoreCase(host)) {
                    String last = data.getLastPathSegment();
                    if (last != null && !last.isEmpty()) return last;
                }
                // Also support https://.../quiz/{id}
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    String path = data.getPath(); // e.g. /quiz/abc123
                    if (path != null && path.startsWith("/quiz/")) {
                        return path.substring("/quiz/".length());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 2) If launched by StudentQrScanResultHandler or external scanner with scanned_text extra
        String scanned = intent.getStringExtra("scanned_text");
        if (scanned == null || scanned.trim().isEmpty()) {
            // fallback to plain "quizId" extra already handled by existing code
            return null;
        }
        scanned = scanned.trim();
        if (scanned.toLowerCase().startsWith("quiz:")) {
            return scanned.substring("quiz:".length());
        }

        // try parse as URI
        try {
            Uri uri = Uri.parse(scanned);
            if (uri != null) {
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if ("nextgen".equalsIgnoreCase(scheme) && "quiz".equalsIgnoreCase(host)) {
                    return uri.getLastPathSegment();
                }
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    String path = uri.getPath();
                    if (path != null && path.startsWith("/quiz/")) {
                        return path.substring("/quiz/".length());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    /**
     * Resolve studentId (SessionManager preferred; fallback to Students node by auth uid),
     * then write QuizStudents/{quizId}/{studentId}/present = true and run the callback.
     * If resolution fails or auth missing, runs callback anyway.
     */
    private void resolveStudentIdAndMarkPresent(String quizId, Runnable afterMarking) {
        if (quizId == null || quizId.isEmpty()) {
            if (afterMarking != null) afterMarking.run();
            return;
        }

        // Prefer saved studentId
        String stored = null;
        try {
            stored = new com.example.nextgen.SessionManager(this).getStudentId();
        } catch (Exception ignored) {
        }

        if (stored != null && !stored.isEmpty()) {
            writePresentFlag(quizId, stored, afterMarking);
            return;
        }

        // Fallback: find studentId by auth uid
        String uid = null;
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) uid = u.getUid();
        } catch (Exception ignored) {
        }

        if (uid == null || uid.isEmpty()) {
            // cannot resolve studentId -> just continue
            if (afterMarking != null) afterMarking.run();
            return;
        }

        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        studentsRef.orderByChild("uid").equalTo(uid).limitToFirst(1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String found = null;
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String sid = ds.child("studentId").getValue(String.class);
                                if (sid != null && !sid.isEmpty()) {
                                    found = sid;
                                    break;
                                }
                            }
                        }
                        if (found != null) {
                            try {
                                new com.example.nextgen.SessionManager(TakeQuizActivity.this).saveStudentId(found);
                            } catch (Exception ignored) {
                            }
                            writePresentFlag(quizId, found, afterMarking);
                        } else {
                            // not found -> continue without marking
                            if (afterMarking != null) afterMarking.run();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (afterMarking != null) afterMarking.run();
                    }
                });
    }

    private void writePresentFlag(String quizId, String studentId, Runnable afterMarking) {
        if (quizId == null || studentId == null) {
            if (afterMarking != null) afterMarking.run();
            return;
        }
        DatabaseReference presRef = FirebaseDatabase.getInstance()
                .getReference("QuizStudents")
                .child(quizId)
                .child(studentId)
                .child("present");

        presRef.get().addOnCompleteListener(task -> {
            boolean already = false;
            if (task.isSuccessful() && task.getResult() != null) {
                Boolean v = task.getResult().getValue(Boolean.class);
                already = Boolean.TRUE.equals(v);
            }

            if (already) {
                runOnUiThread(() -> Toast.makeText(TakeQuizActivity.this, "Marked present (already).", Toast.LENGTH_SHORT).show());
                if (afterMarking != null) afterMarking.run();
                return;
            }

            presRef.setValue(true).addOnCompleteListener(writeTask -> {
                if (writeTask.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(TakeQuizActivity.this, "You are marked present.", Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(TakeQuizActivity.this, "Failed to mark present: " + (writeTask.getException() != null ? writeTask.getException().getMessage() : ""), Toast.LENGTH_SHORT).show());
                }
                if (afterMarking != null) afterMarking.run();
            });
        });
    }

    // ============ The rest of your original methods (unchanged) ============
    // All remaining methods from your original file are kept as-is below.
    // I paste them to produce a complete file exactly as you requested.
    // (They were present earlier in the file you supplied and are reproduced here.)

    private long normalizeTimestamp(Long ts) {
        if (ts == null) return 0L;
        if (ts > 0 && ts < 1_000_000_000_000L) return ts * 1000L;
        return ts;
    }

    private void checkIfQuizIsAlreadyTaken() {
        // Try local pending submissions (offline-safe)
        String localStudentId = com.example.nextgen.SessionManager.getStudentId(this);
        if (localStudentId != null && !localStudentId.isEmpty()) {
            new Thread(() -> {
                com.example.nextgen.offline.AppDatabase db = com.example.nextgen.offline.AppDatabase.getInstance(TakeQuizActivity.this);
                com.example.nextgen.offline.PendingSubmission pending =
                        db.pendingSubmissionDao().findPendingByExamAndStudent(quizId, localStudentId);

                runOnUiThread(() -> {
                    if (pending != null) {
                        Toast.makeText(TakeQuizActivity.this, "You have a pending quiz submission.", Toast.LENGTH_LONG).show();
                        redirectToResultActivity(pending.computedScore, pending.maxScore);
                    } else {
                        checkIfTakenOnServer();
                    }
                });
            }).start();
        } else {
            checkIfTakenOnServer();
        }
    }

    private void checkIfTakenOnServer() {
        DatabaseReference scoreRef = FirebaseDatabase.getInstance()
                .getReference("Scores")
                .child(currentStudentUid)
                .child(quizId);

        scoreRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(TakeQuizActivity.this, "You have already completed this quiz.", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    // Final availability guard before showing rules
                    if (!isQuizStartAllowed()) {
                        // Show friendly message and finish
                        long now = System.currentTimeMillis();
                        long avail = getComputedAvailableAt();
                        long endAt = getComputedEndAt();
                        if (avail > now) {
                            String when = android.text.format.DateFormat.getDateFormat(TakeQuizActivity.this).format(avail)
                                    + " " + android.text.format.DateFormat.getTimeFormat(TakeQuizActivity.this).format(avail);
                            new AlertDialog.Builder(TakeQuizActivity.this)
                                    .setTitle("Not Available Yet")
                                    .setMessage("This quiz will be available at:\n" + when)
                                    .setPositiveButton("OK", (d, w) -> finish())
                                    .setCancelable(false)
                                    .show();
                        } else {
                            new AlertDialog.Builder(TakeQuizActivity.this)
                                    .setTitle("Quiz Unavailable")
                                    .setMessage("This quiz is no longer available.")
                                    .setPositiveButton("OK", (d, w) -> finish())
                                    .setCancelable(false)
                                    .show();
                        }
                    } else {
                        showQuizRulesAlert();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database error: " + error.getMessage());
                // allow user to continue (best-effort)
                showQuizRulesAlert();
            }
        });
    }

    private long getComputedAvailableAt() {
        // priority: intentAvailableAt > quiz node availableAt > scheduledAt (from cached DB)
        if (intentAvailableAt > 0) return intentAvailableAt;

        // try cached local DB
        try {
            com.example.nextgen.offline.AppDatabase db = com.example.nextgen.offline.AppDatabase.getInstance(this);
            com.example.nextgen.offline.ExamEntity ex = db.examDao().getExamById(quizId);
            if (ex != null && ex.scheduledAt > 0) return ex.scheduledAt;
        } catch (Exception ignored) {
        }

        // fallback: 0 (immediately available)
        return 0L;
    }

    private long getComputedEndAt() {
        long avail = getComputedAvailableAt();
        int dur = (durationMinutes > 0) ? durationMinutes : intentDurationMinutes;
        if (dur > 0) return avail + dur * 60_000L;
        return Long.MAX_VALUE; // treat as no end
    }

    private boolean isQuizStartAllowed() {
        long now = System.currentTimeMillis();
        long avail = getComputedAvailableAt();
        long endAt = getComputedEndAt();

        if (avail > 0 && now + START_TOLERANCE_MS < avail) return false; // not yet
        if (endAt < now) return false; // expired
        return true;
    }

    private boolean isActivityAlive() {
        return !isFinishing() && !isDestroyed();
    }

    private void showQuizRulesAlert() {
        if (!isActivityAlive()) return;

        runOnUiThread(() -> {
            if (!isActivityAlive()) return;
            try {
                isShowingRules = true;
                new AlertDialog.Builder(TakeQuizActivity.this)
                        .setTitle("IMPORTANT: Quiz Rules & Anti-Cheating")
                        .setMessage("By pressing START, you agree to the following rules:\n\n" +
                                "1. DO NOT EXIT THE APP.\n" +
                                "2. DO NOT USE SPLIT-SCREEN.\n" +
                                "3. The PHONE'S BACK BUTTON is DISABLED.\n" +
                                "4. The In-App Back Arrow will deduct " + DEDUCTION_PER_STRIKE + " point(s) upon press.\n" +
                                "5. Your microphone will be monitored for HUMAN voice.\n\n" +
                                "Exceeding " + MAX_SWITCHES + " navigation violations or " + MAX_AUDIO_STRIKES +
                                " audio strikes may auto-submit with zero."
                        )
                        .setPositiveButton("START QUIZ", (dialog, which) -> {
                            isShowingRules = false;
                            // final guard at the moment of starting
                            if (!isQuizStartAllowed()) {
                                Toast.makeText(TakeQuizActivity.this, "Quiz not available at this moment.", Toast.LENGTH_LONG).show();
                                finish();
                                return;
                            }
                            startQuizLoadingProcessContinued();
                        })
                        .setCancelable(false)
                        .show();
            } catch (WindowManager.BadTokenException e) {
                Log.w(TAG, "Could not show rules dialog", e);
            }
        });
    }

    private void startQuizLoadingProcessContinued() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInMultiWindowMode()) {
            Toast.makeText(this, "Split-screen detected. Auto-submitting.", Toast.LENGTH_LONG).show();
            submitQuizWithZeroScore();
            return;
        }

        tvQuizTitle.setText("Quiz: " + quizName);

        // If duration wasn't set already, use intentDurationMinutes if provided
        if (durationMinutes == 0 && intentDurationMinutes > 0) {
            durationMinutes = intentDurationMinutes;
        }

        // Fetch quiz details from Firebase to override/correct duration if available
        fetchQuizDetailsFromFirebase();

        questionsRef = FirebaseDatabase.getInstance().getReference("Questions").child(quizId);

        if (!offlineLoaded) {
            loadQuestions();
        } else {
            Log.d("OfflineDebug", "offlineLoaded true - skipping loadQuestions()");
            checkAndRequestAudioPermission();

        }

        // Listen for quiz reset using QuizStudents node then fallback to ExamStudents
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        studentsRef.orderByChild("uid").equalTo(currentStudentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String studentId = ds.child("studentId").getValue(String.class);
                                listenForQuizReset(studentId);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
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
                Toast.makeText(this, "Audio monitoring disabled. Microphone permission denied.", Toast.LENGTH_LONG).show();
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
        stopAudioMonitoring();

        if (switchCount >= MAX_SWITCHES) {
            Toast.makeText(this, "Cheating detected! Auto-submitting quiz.", Toast.LENGTH_LONG).show();
            try {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(TakeQuizActivity.this)
                            .setTitle("Cheating Detected!")
                            .setMessage("You switched away too many times. Auto-submitting with zero score.")
                            .setPositiveButton("OK", (dialog, which) -> submitQuizWithZeroScore())
                            .setCancelable(false)
                            .show();
                });
            } catch (Exception e) {
                submitQuizWithZeroScore();
            }
        } else {
            Toast.makeText(this, "Switching apps detected. Attempts left: " + (MAX_SWITCHES - switchCount), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        startAudioMonitoring();

        if (switchCount >= MAX_SWITCHES) {
            Toast.makeText(this, "Cheating detected! Auto-submitting quiz.", Toast.LENGTH_LONG).show();
            try {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(TakeQuizActivity.this)
                            .setTitle("Cheating Detected!")
                            .setMessage("You switched away too many times. Auto-submitting with zero score.")
                            .setPositiveButton("OK", (dialog, which) -> submitQuizWithZeroScore())
                            .setCancelable(false)
                            .show();
                });
            } catch (Exception e) {
                submitQuizWithZeroScore();
            }
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInMultiWindowMode()) {
            if (countDownTimer != null) countDownTimer.cancel();
            stopAudioMonitoring();
            Toast.makeText(this, "Split-screen detected. Auto-submitting quiz.", Toast.LENGTH_LONG).show();
            submitQuizWithZeroScore();
            return;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (questionList.isEmpty() || countDownTimer == null) {
                Toast.makeText(this, "Quiz finished. Use Submit button.", Toast.LENGTH_SHORT).show();
                return true;
            }

            switchCount++;
            totalDeductions += DEDUCTION_PER_STRIKE;

            if (switchCount >= MAX_SWITCHES) {
                Toast.makeText(this, "CHEATING DETECTED: Auto-submitting.", Toast.LENGTH_LONG).show();
                try {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(TakeQuizActivity.this)
                                .setTitle("Cheating Detected!")
                                .setMessage("You navigated away from the quiz too many times. Auto-submitting with zero score.")
                                .setPositiveButton("OK", (dialog, which) -> submitQuizWithZeroScore())
                                .setCancelable(false)
                                .show();
                    });
                } catch (Exception e) {
                    submitQuizWithZeroScore();
                }
            } else {
                Toast.makeText(this, "Back arrow used. Attempts left: " + (MAX_SWITCHES - switchCount), Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Back button disabled during quiz.", Toast.LENGTH_LONG).show();
    }

    private void startTimer() {
        if (durationMinutes <= 0) return;
        timeLeftInMillis = (long) durationMinutes * 60000;
        if (countDownTimer != null) countDownTimer.cancel();
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
                Toast.makeText(TakeQuizActivity.this, "Time's up! Submitting quiz.", Toast.LENGTH_LONG).show();
                submitQuiz();
            }
        }.start();
    }

    private void updateCountDownText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        String timeLeftFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        tvTimer.setText(timeLeftFormatted);
    }

    private void fetchQuizDetailsFromFirebase() {
        DatabaseReference quizzesRootRef = FirebaseDatabase.getInstance().getReference("Quizzes");
        quizzesRootRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;
                for (DataSnapshot teacherSnap : snapshot.getChildren()) {
                    if (teacherSnap.hasChild(quizId)) {
                        found = true;
                        DataSnapshot quizSnap = teacherSnap.child(quizId);
                        Long durationLong = quizSnap.child("durationMinutes").getValue(Long.class);
                        Long availableAtNode = quizSnap.child("availableAt").getValue(Long.class);
                        Integer availableAfter = quizSnap.child("availableAfterMinutes").getValue(Integer.class);

                        if (durationLong != null) {
                            durationMinutes = durationLong.intValue();
                            startTimer();
                        } else if (intentDurationMinutes > 0) {
                            durationMinutes = intentDurationMinutes;
                            startTimer();
                        }

                        // If availableAt present in DB, normalize and use it (helps when intent didn't include)
                        if (availableAtNode != null && availableAtNode > 0) {
                            long norm = normalizeTimestamp(availableAtNode);
                            intentAvailableAt = norm;
                        } else if (availableAfter != null && availableAfter > 0 && quizSnap.child("scheduledAt").getValue(Long.class) != null) {
                            long sched = normalizeTimestamp(quizSnap.child("scheduledAt").getValue(Long.class));
                            intentAvailableAt = sched + availableAfter * 60_000L;
                        }
                        break;
                    }
                }
                if (!found) Log.e(TAG, "Quiz details not found in Firebase.");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching quiz details: " + error.getMessage());
            }
        });
    }

    private void loadQuestions() {
        new Thread(() -> {
            com.example.nextgen.offline.AppDatabase db = com.example.nextgen.offline.AppDatabase.getInstance(this);
            List<QuestionEntity> cachedQuestions = db.questionDao().getQuestionsByExamId(quizId);

            Log.d("OfflineDebug", "quizId=" + quizId + ", cached size=" + cachedQuestions.size());

            runOnUiThread(() -> {
                if (!cachedQuestions.isEmpty()) {
                    mapEntitiesToQuestionsAndShow(cachedQuestions);
                    offlineLoaded = true;
                } else if (isNetworkAvailable()) {
                    fetchQuestionsFromFirebaseAndCache(quizId);
                } else {
                    Toast.makeText(this, "Questions not downloaded. Connect to network.", Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        }).start();
    }

    private boolean hasNextNonEmptySection() {
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

    private boolean allQuestionsAnswered() {
        for (Question q : questionList) {
            String ans = q.getStudentAnswer();
            if (ans == null || ans.trim().isEmpty()) return false;
        }
        return true;
    }

    private void maybeConfirmSubmit() {
        if (!isActivityAlive()) return;
        runOnUiThread(() -> {
            if (!isActivityAlive()) return;
            if (allQuestionsAnswered()) {
                try {
                    new AlertDialog.Builder(TakeQuizActivity.this)
                            .setTitle("Confirm Submit")
                            .setMessage("Are you sure you want to submit? You won't be able to change your answers.")
                            .setPositiveButton("Submit", (dialog, which) -> submitQuiz())
                            .setNegativeButton("Cancel", null)
                            .setCancelable(true)
                            .show();
                } catch (WindowManager.BadTokenException e) {
                    Log.w(TAG, "Confirm dialog not shown", e);
                }
            } else {
                int unanswered = 0;
                for (Question q : questionList) {
                    String ans = q.getStudentAnswer();
                    if (ans == null || ans.trim().isEmpty()) unanswered++;
                }
                try {
                    new AlertDialog.Builder(TakeQuizActivity.this)
                            .setTitle("Unanswered Questions")
                            .setMessage("You have " + unanswered + " unanswered question(s). Do you want to submit anyway?")
                            .setPositiveButton("Submit Anyway", (dialog, which) -> submitQuiz())
                            .setNegativeButton("Go Back", null)
                            .setCancelable(true)
                            .show();
                } catch (WindowManager.BadTokenException e) {
                    Log.w(TAG, "Unanswered dialog not shown", e);
                }
            }
        });
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    private void mapEntitiesToQuestionsAndShow(List<QuestionEntity> list) {
        questionList.clear();
        allMatchingAnswers.clear();

        Log.d("OfflineDebug", "Mapping cached quiz questions. Total: " + list.size());

        for (QuestionEntity qe : list) {
            Log.d("OfflineDebug", "Question text: " + qe.questionText + " type: " + qe.questionType);
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

            if ("Matching Type".equalsIgnoreCase(q.getQuestionType())) {
                String answer = q.getCorrectAnswer();
                if (answer != null && !allMatchingAnswers.contains(answer))
                    allMatchingAnswers.add(answer);
            }
        }

        typeIndex = 0;
        filterQuestionsByType(questionTypeOrder[typeIndex]);

        Log.d("OfflineDebug", "Questions for first section (" + questionTypeOrder[typeIndex] + "): " + currentTypeQuestions.size());
        showNextQuestion();

        offlineLoaded = true;
        checkAndRequestAudioPermission();
    }

    private void fetchQuestionsFromFirebaseAndCache(String id) {
        DatabaseReference questionsRef = FirebaseDatabase.getInstance().getReference("Questions").child(id);
        questionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<QuestionEntity> toCache = new ArrayList<>();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    QuestionEntity qe = snap.getValue(QuestionEntity.class);
                    if (qe == null) qe = new QuestionEntity();
                    qe.examId = id;
                    qe.firebaseKey = snap.getKey();

                    if (snap.child("questionText").exists())
                        qe.questionText = snap.child("questionText").getValue(String.class);
                    if (snap.child("questionType").exists())
                        qe.questionType = snap.child("questionType").getValue(String.class);
                    if (snap.child("correctAnswer").exists())
                        qe.correctAnswer = snap.child("correctAnswer").getValue(String.class);
                    if (snap.child("optionA").exists())
                        qe.optionA = snap.child("optionA").getValue(String.class);
                    if (snap.child("optionB").exists())
                        qe.optionB = snap.child("optionB").getValue(String.class);
                    if (snap.child("optionC").exists())
                        qe.optionC = snap.child("optionC").getValue(String.class);
                    if (snap.child("optionD").exists())
                        qe.optionD = snap.child("optionD").getValue(String.class);
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

                new Thread(() -> {
                    com.example.nextgen.offline.OfflineExamManager mgr = new com.example.nextgen.offline.OfflineExamManager(TakeQuizActivity.this);
                    mgr.saveQuestions(id, toCache);
                    runOnUiThread(() -> {
                        mapEntitiesToQuestionsAndShow(toCache);
                        offlineLoaded = true;
                        checkAndRequestAudioPermission();

                    });
                }).start();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TakeQuizActivity.this, "Failed to load quiz questions: " + error.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void filterQuestionsByType(String type) {
        currentQuestionType = type;
        currentTypeQuestions.clear();
        for (Question q : questionList) {
            if (q.getQuestionType().equalsIgnoreCase(type)) currentTypeQuestions.add(q);
        }
        typeQuestionNumber = 1;
        currentIndex = 0;
    }

    private void showNextQuestion() {
        if (currentTypeQuestions.isEmpty()) {
            goToNextTypeOrSubmit();
            return;
        }

        if (currentIndex < currentTypeQuestions.size()) {
            List<Question> singleQuestion = new ArrayList<>();
            Question currentQ = currentTypeQuestions.get(currentIndex);
            currentQ.setDisplayNumber(typeQuestionNumber);
            singleQuestion.add(currentQ);

            // ---- Add here ----
            boolean hasNext = currentIndex < currentTypeQuestions.size() - 1;
            boolean hasNextSection = !hasNext && hasNextNonEmptySection();
            String buttonText;
            if (hasNext) buttonText = "Next";
            else if (hasNextSection) buttonText = "Next Section";
            else buttonText = "Submit";
            // ----------

            questionAdapter = new TakeExamAdapter(this, singleQuestion, allMatchingAnswers, buttonText);
            questionAdapter.setOnActionListener((position, actionString) -> {
                if ("Next".equalsIgnoreCase(actionString)) moveToNext();
                else if ("Next Section".equalsIgnoreCase(actionString)) goToNextTypeOrSubmit();
                else if (actionString.startsWith("Submit")) maybeConfirmSubmit();
            });
            rvQuestions.setAdapter(questionAdapter);

        } else {
            goToNextTypeOrSubmit();
        }
    }


    private void submitQuiz() {
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
        String localStudentId = com.example.nextgen.SessionManager.getStudentId(this);

        if (localStudentId != null && !localStudentId.isEmpty()) {
            com.example.nextgen.sync.SubmissionHelper.saveSubmissionLocallyAndEnqueue(
                    getApplicationContext(),
                    quizId,
                    localStudentId,
                    questionList,
                    finalCalculatedScore,
                    totalQuestions
            );

            // if online, also write Scores immediately for faster UI update
            if (isNetworkAvailable())
                saveScoreToFirebase(localStudentId, finalCalculatedScore, totalQuestions);

            Toast.makeText(TakeQuizActivity.this, "Submission saved locally; will sync when online.", Toast.LENGTH_LONG).show();
            redirectToResultActivity(finalCalculatedScore, totalQuestions);
            return;
        }

        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        studentsRef.orderByChild("uid").equalTo(currentStudentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String studentId = ds.child("studentId").getValue(String.class);
                                if (studentId == null || studentId.isEmpty()) {
                                    Toast.makeText(TakeQuizActivity.this, "Student ID missing.", Toast.LENGTH_SHORT).show();

                                    return;
                                }
                                com.example.nextgen.sync.SubmissionHelper.saveSubmissionLocallyAndEnqueue(
                                        getApplicationContext(),
                                        quizId,
                                        studentId,
                                        questionList,
                                        finalCalculatedScore,
                                        totalQuestions
                                );

                                // if online, also write Scores immediately for faster UI update
                                if (isNetworkAvailable())
                                    saveScoreToFirebase(studentId, finalCalculatedScore, totalQuestions);

                                Toast.makeText(TakeQuizActivity.this, "Submission saved and will sync when online.", Toast.LENGTH_LONG).show();
                                redirectToResultActivity(finalCalculatedScore, totalQuestions);
                                break;
                            }
                        } else {
                            Toast.makeText(TakeQuizActivity.this, "Student ID not found online.", Toast.LENGTH_SHORT).show();


                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {


                        Toast.makeText(TakeQuizActivity.this, "Error fetching student ID.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void submitQuizWithZeroScore() {
        stopAudioMonitoring();
        int maxScore = questionList.size();


        String localStudentId = com.example.nextgen.SessionManager.getStudentId(this);
        if (localStudentId != null && !localStudentId.isEmpty()) {
            com.example.nextgen.sync.SubmissionHelper.saveSubmissionLocallyAndEnqueue(
                    getApplicationContext(),
                    quizId,
                    localStudentId,
                    questionList,
                    0,
                    maxScore
            );
            if (isNetworkAvailable()) saveScoreToFirebase(localStudentId, 0, maxScore);
            Toast.makeText(TakeQuizActivity.this, "Zero-score submission saved locally.", Toast.LENGTH_LONG).show();
            redirectToResultActivity(0, maxScore);
            return;
        }

        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        studentsRef.orderByChild("uid").equalTo(currentStudentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String studentId = ds.child("studentId").getValue(String.class);
                                if (studentId == null || studentId.isEmpty()) {
                                    Toast.makeText(TakeQuizActivity.this, "Student ID missing.", Toast.LENGTH_SHORT).show();


                                    return;
                                }

                                com.example.nextgen.sync.SubmissionHelper.saveSubmissionLocallyAndEnqueue(
                                        getApplicationContext(),
                                        quizId,
                                        studentId,
                                        questionList,
                                        0,
                                        maxScore
                                );
                                if (isNetworkAvailable())
                                    saveScoreToFirebase(studentId, 0, maxScore);
                                Toast.makeText(TakeQuizActivity.this, "Zero-score submission saved locally.", Toast.LENGTH_LONG).show();
                                redirectToResultActivity(0, maxScore);
                                break;
                            }
                        } else {
                            Toast.makeText(TakeQuizActivity.this, "Student ID not found online.", Toast.LENGTH_SHORT).show();


                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                        Toast.makeText(TakeQuizActivity.this, "Error fetching student ID.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveScoreToFirebase(String studentId, int score, int maxScore) {
        DatabaseReference scoreEntryRef = FirebaseDatabase.getInstance()
                .getReference("Scores")
                .child(studentId)
                .child(quizId);

        scoreEntryRef.child("score").setValue(score);
        scoreEntryRef.child("maxScore").setValue(maxScore);
        scoreEntryRef.child("timestamp").setValue(System.currentTimeMillis());
        scoreEntryRef.child("deductions").setValue(totalDeductions);
    }

    private void redirectToResultActivity(int score, int maxScore) {
        if (!isNetworkAvailable()) {
            if (countDownTimer != null) countDownTimer.cancel();
            stopAudioMonitoring();

            Toast.makeText(TakeQuizActivity.this,
                    "Submission saved locally and will sync when online. Returning to dashboard.",
                    Toast.LENGTH_LONG).show();

            Intent intent = new Intent(TakeQuizActivity.this, StudentDashboardActivity.class);
            intent.putExtra("fromSubmitPending", true);
            intent.putExtra("pendingExamId", quizId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // Resolve student info and quiz info similar to exam flow, then open ResultActivity
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        studentsRef.orderByChild("uid").equalTo(currentStudentUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot studentSnap) {
                if (!studentSnap.exists()) {
                    Toast.makeText(TakeQuizActivity.this, "Student info not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                for (DataSnapshot studentData : studentSnap.getChildren()) {
                    final String studentId = studentData.child("studentId").getValue(String.class);
                    final String fullName = studentData.child("fullName").getValue(String.class);
                    final String profileImage = studentData.child("profileImage").getValue(String.class);

                    // Get quiz info from Quizzes node
                    DatabaseReference quizzesRef = FirebaseDatabase.getInstance().getReference("Quizzes");
                    quizzesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            boolean found = false;
                            for (DataSnapshot teacherSnap : snapshot.getChildren()) {
                                if (teacherSnap.hasChild(quizId)) {
                                    found = true;
                                    DataSnapshot quizSnap = teacherSnap.child(quizId);
                                    final String subjectName = quizSnap.child("subjectName").getValue(String.class);
                                    final String teacherName = quizSnap.child("teacherName").getValue(String.class);

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

                                            Intent intent = new Intent(TakeQuizActivity.this, ResultActivity.class);
                                            intent.putExtra("studentName", fullName);
                                            intent.putExtra("studentId", studentId);
                                            intent.putExtra("profileImage", profileImage);

                                            intent.putExtra("courseCode", subjectCode);
                                            intent.putExtra("subjectName", subjectName);
                                            intent.putExtra("teacherName", teacherName);

                                            intent.putExtra("examTitle", quizName);
                                            intent.putExtra("totalScore", score);
                                            intent.putExtra("maxScore", maxScore);
                                            intent.putExtra("deductions", totalDeductions);

                                            startActivity(intent);
                                            finish();
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            Toast.makeText(TakeQuizActivity.this, "Error loading subject: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                    return;
                                }
                            }
                            if (!found) {
                                Toast.makeText(TakeQuizActivity.this, "Quiz not found in any teacher node", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(TakeQuizActivity.this, "Error fetching quiz data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TakeQuizActivity.this, "Error fetching student info: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------- AUDIO MONITORING ----------
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
        audioCheatingCount = 0;
    }

    private void startAudioClassification() {
        final float NEW_HIGH_CONFIDENCE_THRESHOLD = 0.6f;
        final float MIC_LOUDNESS_THRESHOLD = 0.15f;

        try {
            AudioClassifier.AudioClassifierOptions options =
                    AudioClassifier.AudioClassifierOptions.builder()
                            .setMaxResults(1)
                            .setScoreThreshold(NEW_HIGH_CONFIDENCE_THRESHOLD)
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

                        float[] audioData = tensorAudio.getTensorBuffer().getFloatArray();
                        float maxAmplitude = 0f;
                        for (float v : audioData)
                            maxAmplitude = Math.max(maxAmplitude, Math.abs(v));

                        if (!results.isEmpty()) {
                            Classifications classification = results.get(0);
                            if (!classification.getCategories().isEmpty()) {
                                String label = classification.getCategories().get(0).getLabel();
                                float confidence = classification.getCategories().get(0).getScore();

                                Log.d("AUDIO_DEBUG", "Label=" + label + " Conf=" + confidence + " Loud=" + maxAmplitude);

                                if ((label.equalsIgnoreCase("human") || label.equalsIgnoreCase("speech"))
                                        && confidence >= NEW_HIGH_CONFIDENCE_THRESHOLD
                                        && maxAmplitude >= MIC_LOUDNESS_THRESHOLD) {

                                    audioCheatingCount++;
                                    Log.w("AUDIO_TFLITE", "CHEATING STRIKE " + audioCheatingCount);

                                    if (audioCheatingCount < MAX_AUDIO_STRIKES) {
                                        runOnUiThread(() -> Toast.makeText(TakeQuizActivity.this,
                                                "WARNING: Loud human voice detected! (" + audioCheatingCount + "/" + MAX_AUDIO_STRIKES + ")",
                                                Toast.LENGTH_SHORT).show());
                                    }

                                    if (audioCheatingCount >= MAX_AUDIO_STRIKES) {
                                        Log.e("AUDIO_TFLITE", "MAJOR CHEATING: Auto-submitting quiz!");
                                        runOnUiThread(() -> submitQuizWithZeroScore());
                                    }
                                } else {
                                    audioCheatingCount = 0;
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("AUDIO_TFLITE", "Error audio classify: " + e.getMessage());
                    }
                    audioHandler.postDelayed(this, AUDIO_DETECTION_INTERVAL);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load audio model.", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Reset listener ----------
    private void listenForQuizReset(String studentId) {
        // primary: QuizStudents/{quizId}/{studentId}/reset
        DatabaseReference resetRef = FirebaseDatabase.getInstance()
                .getReference("QuizStudents")
                .child(quizId)
                .child(studentId)
                .child("reset");

        resetRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean resetFlag = snapshot.getValue(Boolean.class);
                if (resetFlag != null && resetFlag) {
                    handleQuizReset();
                    resetRef.setValue(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // fallback: monitor ExamStudents path if QuizStudents fails (optional)
                Log.w(TAG, "Quiz reset listener cancelled: " + error.getMessage());
            }
        });
    }

    private void handleQuizReset() {
        if (countDownTimer != null) countDownTimer.cancel();
        stopAudioMonitoring();

        for (Question q : questionList) q.setStudentAnswer(null);

        currentIndex = 0;
        typeIndex = 0;
        typeQuestionNumber = 1;

        filterQuestionsByType(questionTypeOrder[typeIndex]);
        showNextQuestion();
        startTimer();

        Toast.makeText(this, "Quiz has been reset by your teacher.", Toast.LENGTH_LONG).show();
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
                    new AlertDialog.Builder(TakeQuizActivity.this)
                            .setTitle("Unanswered Question")
                            .setMessage("You haven't answered this question yet. Please answer before moving on.")
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .setCancelable(true)
                            .show();
                } catch (WindowManager.BadTokenException e) {
                    Log.w(TAG, "Unanswered dialog not shown", e);
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
                goToNextTypeOrSubmit();
            }
        }
    }

    private void showQuestion(Question question) {
        question.setDisplayNumber(typeQuestionNumber);
        List<Question> singleQuestion = new ArrayList<>();
        singleQuestion.add(question);

        // ---- Add here ----
        boolean hasNext = currentIndex < currentTypeQuestions.size() - 1;
        boolean hasNextSection = !hasNext && hasNextNonEmptySection();
        String buttonText;
        if (hasNext) buttonText = "Next";
        else if (hasNextSection) buttonText = "Next Section";
        else buttonText = "Submit";
        // ------------------

        questionAdapter = new TakeExamAdapter(this, singleQuestion, allMatchingAnswers, buttonText);
        questionAdapter.setOnActionListener((position, actionString) -> {
            if ("Next".equalsIgnoreCase(actionString)) moveToNext();
            else if ("Next Section".equalsIgnoreCase(actionString)) goToNextTypeOrSubmit();
            else if (actionString.startsWith("Submit")) maybeConfirmSubmit();
        });
        rvQuestions.setAdapter(questionAdapter);
    }
}