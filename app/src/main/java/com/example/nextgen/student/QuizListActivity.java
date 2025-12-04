package com.example.nextgen.student;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.admin.StudentModel;
import com.example.nextgen.SessionManager;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * QuizListActivity
 *
 * Simplified: removed presence-confirmation flow.
 * - After an in-app scan returns, the activity marks the quiz present immediately (optimistic)
 *   and does NOT attach any presence listener that waits for teacher confirmation.
 * - Presence listeners and duplicated listener logic removed to avoid "waiting for teacher" behavior.
 * - The adapter still supports optimistic placeholders so the Take Quiz button appears instantly.
 *
 * This makes "scan -> Take Quiz visible" immediate and permanent (until DB/scores update).
 */
public class QuizListActivity extends AppCompatActivity implements QuizListAdapter.OnQuizClickListener {

    private static final String TAG = "QuizListActivity";
    private static final String TAG_DEBUG = "QUIZ_DEBUG";
    private static final boolean SHOW_ALL_ACTIVE_FOR_DEBUG = false;

    private static final int REQ_CODE_ZXING_SCAN = 0x1001;
    private static final int REQ_CODE_IN_APP_SCAN = 0x1002;

    private RecyclerView rvQuizzes;
    private ProgressBar progress;
    private TextView tvEmpty;
    private Button btnScanQr;

    private QuizListAdapter adapter;
    private final List<QuizModel> quizList = new ArrayList<>();
    private final Set<String> quizIds = new HashSet<>();

    private StudentModel student;
    private SessionManager sessionManager;

    private DatabaseReference publicRef;
    private ChildEventListener publicChildListener;

    private DatabaseReference scoresRefForStudent;
    private ChildEventListener scoresChildListener;
    private String scoresStudentId = null;

    private String autoOpenQuizId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_list);

        rvQuizzes = findViewById(R.id.rvQuizzes);
        progress = findViewById(R.id.progressQuizzes);
        tvEmpty = findViewById(R.id.tvNoQuizzes);
        btnScanQr = findViewById(R.id.btnScanQr);

        rvQuizzes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new QuizListAdapter(quizList, this);
        rvQuizzes.setAdapter(adapter);

        if (btnScanQr != null) {
            btnScanQr.setOnClickListener(v -> launchInAppScanner());
        }

        sessionManager = new SessionManager(this);
        student = sessionManager.getStudentModel();
        if (student == null) {
            student = new StudentModel();
            student.setCourseName(sessionManager.getCourseName());
            student.setSpecializationName(sessionManager.getSpecializationName());
            student.setYearName(sessionManager.getYearName());
            student.setSectionName(sessionManager.getSectionName());
        }

        Log.d(TAG_DEBUG, "Session student: course='" + sessionManager.getCourseName()
                + "' spec='" + sessionManager.getSpecializationName()
                + "' year='" + sessionManager.getYearName()
                + "' section='" + sessionManager.getSectionName() + "'");

        autoOpenQuizId = getIntent().getStringExtra("autoOpenQuizId");
        publicRef = FirebaseDatabase.getInstance().getReference("AvailableQuizzes");

        debugFetchAndLog();
        startRealtimeListener();
        startChildNotifications();
    }

    private void startChildNotifications() {
    }

    @Override
    protected void onResume() {
        super.onResume();
        attachScoresRealtimeListenerIfNeeded();
    }

    @Override
    protected void onPause() {
        super.onPause();
        detachScoresRealtimeListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopChildNotifications();
        detachScoresRealtimeListener();
        // presence listener logic removed intentionally (no confirmation flow)
    }

    private void stopChildNotifications() {
    }

    // ---------- Scanner launch helpers ----------

    private void launchInAppScanner() {
        try {
            Intent intent = new Intent(this, StudentQuizScannerActivity.class);
            startActivityForResult(intent, REQ_CODE_IN_APP_SCAN);
        } catch (ActivityNotFoundException ex) {
            Log.w(TAG, "In-app quiz scanner not found, falling back to external scanner");
            launchExternalScannerFallback();
        }
    }

    private void launchExternalScannerFallback() {
        Intent intent = new Intent("com.google.zxing.client.android.SCAN");
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
        try {
            startActivityForResult(intent, REQ_CODE_ZXING_SCAN);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No scanner available. Please install a Barcode Scanner app or enable the in-app scanner.", Toast.LENGTH_LONG).show();
            try {
                Intent store = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.google.zxing.client.android"));
                startActivity(store);
            } catch (Exception ignored) {}
        }
    }

    private @Nullable String findMatchingQuizId(String scanned) {
        if (scanned == null) return null;
        String s = scanned.trim();
        if (s.toLowerCase().startsWith("quiz:")) s = s.substring(5).trim();
        if (s.toLowerCase().startsWith("exam:")) s = s.substring(5).trim();
        String lower = s.toLowerCase();

        synchronized (quizList) {
            for (QuizModel qm : quizList) {
                if (qm == null || qm.getQuizId() == null) continue;
                String stored = qm.getQuizId();
                if (stored.equalsIgnoreCase(s) || stored.equalsIgnoreCase(scanned) || stored.toLowerCase().equals(lower)) {
                    return stored;
                }
            }
        }
        return null;
    }

    /**
     * Simplified scanner result handling:
     * - Immediately mark optimistic present and show Take Quiz (no presence-confirmation listeners).
     * - Do not attach realtime presence listeners that wait for teacher confirmation.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CODE_IN_APP_SCAN) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String contents = data.getStringExtra("scanned_text");
                if (contents == null || contents.trim().isEmpty()) {
                    contents = data.getStringExtra("SCAN_RESULT");
                }
                String quizNameMeta = data.getStringExtra("quizName");

                if (contents != null && !contents.trim().isEmpty()) {
                    contents = contents.trim();
                    String candidate = contents.toLowerCase().startsWith("quiz:") ? contents.substring("quiz:".length()).trim() : contents;
                    String matchedQuizId = findMatchingQuizId(candidate);
                    final String quizIdToUse = matchedQuizId != null ? matchedQuizId : candidate;

                    if (!quizIdToUse.isEmpty()) {
                        // Immediately show Take Quiz — no confirmation wait
                        adapter.markOptimisticPresent(quizIdToUse);
                        adapter.setStudentPresent(quizIdToUse, true);

                        // Apply metadata if provided
                        if (quizNameMeta != null && !quizNameMeta.trim().isEmpty()) {
                            synchronized (quizList) {
                                int pos = adapter.getPositionForQuizId(quizIdToUse);
                                if (pos >= 0) {
                                    QuizModel qm = quizList.get(pos);
                                    if (qm != null) {
                                        qm.setQuizName(quizNameMeta);
                                        adapter.updateOrAddQuiz(qm);
                                    }
                                } else {
                                    QuizModel qm = new QuizModel();
                                    qm.setQuizId(quizIdToUse);
                                    qm.setQuizName(quizNameMeta);
                                    qm.setStatus("QUIZ");
                                    qm.setAvailable(true);
                                    adapter.updateOrAddQuiz(qm);
                                }
                            }
                        }

                        // Highlight and scroll into view
                        autoOpenQuizId = quizIdToUse;
                        adapter.setHighlightQuizId(quizIdToUse);
                        int pos = adapter.getPositionForQuizId(quizIdToUse);
                        if (pos >= 0) rvQuizzes.scrollToPosition(pos);

                        // show snackbar (optional)
                        View root = findViewById(android.R.id.content);
                        if (root != null) {
                            Snackbar.make(root, "Marked present locally. Tap 'Take Quiz' to start.", Snackbar.LENGTH_LONG)
                                    .setAction("Open", v -> {
                                        int idx = adapter.getPositionForQuizId(quizIdToUse);
                                        if (idx >= 0) onQuizClick(quizList.get(idx));
                                    })
                                    .show();
                        }
                    }
                }
            }
            return;
        }

        // External ZXing scanner fallback
        if (requestCode == REQ_CODE_ZXING_SCAN) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String scanContents = data.getStringExtra("SCAN_RESULT");
                if (scanContents != null && !scanContents.isEmpty()) {
                    String candidate = scanContents.trim();
                    String matched = findMatchingQuizId(candidate);
                    final String quizIdToUse = matched != null ? matched : candidate;

                    adapter.markOptimisticPresent(quizIdToUse);
                    adapter.setStudentPresent(quizIdToUse, true);

                    autoOpenQuizId = quizIdToUse;
                    adapter.setHighlightQuizId(quizIdToUse);
                    int pos = adapter.getPositionForQuizId(quizIdToUse);
                    if (pos >= 0) rvQuizzes.scrollToPosition(pos);

                    View root = findViewById(android.R.id.content);
                    if (root != null) Snackbar.make(root, "Marked present locally. Tap 'Take Quiz' to start.", Snackbar.LENGTH_LONG).show();
                }
            }
        }
    }

    // Presence-listener methods removed intentionally — no teacher-confirmation flow

    // ---------- Firebase list / scores / builder ----------
    // (rest of file left mostly unchanged; buildListFromPublicSnapshot no longer attaches presence listeners)

    private void startRealtimeListener() {
        progress.setVisibility(View.VISIBLE);
        publicRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) { resolveStudentIdAndFetchScores(snapshot); }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "AvailableQuizzes listener cancelled: " + error.getMessage());
                progress.setVisibility(View.GONE);
            }
        });
    }

    private void resolveStudentIdAndFetchScores(@Nullable DataSnapshot publicSnapshot) {
        String storedStudentId = null;
        try { storedStudentId = sessionManager.getStudentId(); } catch (Exception ignored) {}

        if (storedStudentId != null && !storedStudentId.isEmpty()) {
            fetchScoresAndBuildList(publicSnapshot, storedStudentId);
            return;
        }

        String uid = null;
        try { if (FirebaseAuth.getInstance().getCurrentUser() != null) uid = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}

        if (uid == null || uid.isEmpty()) {
            buildListFromPublicSnapshot(publicSnapshot, new HashSet<>(), null);
            return;
        }

        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        String finalUid = uid;
        studentsRef.orderByChild("uid").equalTo(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String foundStudentId = null;
                if (snapshot.exists()) {
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        String sid = ds.child("studentId").getValue(String.class);
                        if (sid != null && !sid.isEmpty()) { foundStudentId = sid; break; }
                    }
                }
                if (foundStudentId != null) {
                    try { sessionManager.saveStudentId(foundStudentId); } catch (Exception ignored) {}
                    fetchScoresAndBuildList(publicSnapshot, foundStudentId);
                } else {
                    buildListFromPublicSnapshot(publicSnapshot, new HashSet<>(), finalUid);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG_DEBUG, "Failed to lookup studentId by uid: " + error.getMessage());
                buildListFromPublicSnapshot(publicSnapshot, new HashSet<>(), null);
            }
        });
    }

    private void fetchScoresAndBuildList(@Nullable DataSnapshot publicSnapshot, @NonNull String studentId) {
        DatabaseReference scoresRef = FirebaseDatabase.getInstance().getReference("Scores").child(studentId);
        scoresRef.get().addOnCompleteListener(task -> {
            Set<String> takenQuizIds = new HashSet<>();
            if (task.isSuccessful() && task.getResult() != null) {
                for (DataSnapshot snap : task.getResult().getChildren()) {
                    String qid = snap.getKey();
                    if (qid != null) takenQuizIds.add(qid);
                }
            }
            scoresStudentId = studentId;
            buildListFromPublicSnapshot(publicSnapshot, takenQuizIds, studentId);
            attachScoresRealtimeListenerIfNeeded();
        });
    }

    private void buildListFromPublicSnapshot(@Nullable DataSnapshot snapshot, @NonNull Set<String> takenQuizIds, @Nullable String studentId) {
        // no presence listeners attached here anymore
        List<QuizModel> newList = new ArrayList<>();
        quizIds.clear();
        adapter.setHighlightQuizId(null);

        String stuCourse = normalize(student != null ? student.getCourseName() : null);
        String stuSpec = normalize(student != null ? student.getSpecializationName() : null);
        String stuYear = normalize(student != null ? student.getYearName() : null);
        String stuSection = normalize(student != null ? student.getSectionName() : null);
        String stuCombined = (stuCourse + " - " + stuSpec + " - " + stuYear + " - " + stuSection).trim();

        Log.d(TAG_DEBUG, "Normalized student: " + stuCombined);

        if (snapshot == null || !snapshot.exists()) {
            Log.d(TAG_DEBUG, "AvailableQuizzes snapshot empty");
        } else {
            for (DataSnapshot child : snapshot.getChildren()) {
                try {
                    String quizId = child.getKey();
                    if (quizId == null) continue;

                    if (quizIds.contains(quizId)) continue; // safety guard

                    Boolean activeObj = child.child("active").getValue(Boolean.class);
                    boolean active = activeObj != null && activeObj;

                    String quizName = child.child("quizName").getValue(String.class);
                    String teacherName = child.child("teacherName").getValue(String.class);
                    String subjectName = child.child("subjectName").getValue(String.class);
                    String sectionValue = child.child("section").getValue(String.class);
                    String courseNameRaw = child.child("courseName").getValue(String.class);

                    Long scheduledAtRaw = child.child("scheduledAt").getValue(Long.class);
                    Integer duration = child.child("durationMinutes").getValue(Integer.class);

                    long scheduledAt = scheduledAtRaw != null ? scheduledAtRaw : 0L;
                    if (scheduledAt > 0 && scheduledAt < 1_000_000_000_000L) {
                        scheduledAt = scheduledAt * 1000L;
                    }

                    Long availableAtFromDb = child.child("availableAt").getValue(Long.class);
                    Integer availableAfterMinutes = child.child("availableAfterMinutes").getValue(Integer.class);
                    if (availableAtFromDb != null && availableAtFromDb > 0 && availableAtFromDb < 1_000_000_000_000L) {
                        availableAtFromDb = availableAtFromDb * 1000L;
                    }

                    if (!active) continue;

                    // parse section
                    String parsedCourse = "";
                    String parsedSpec = "";
                    String parsedYear = "";
                    String parsedSection = "";

                    if (sectionValue != null && !sectionValue.trim().isEmpty()) {
                        if (sectionValue.contains(" - ")) {
                            String[] parts = sectionValue.split(" - ");
                            if (parts.length > 0) parsedCourse = parts[0].trim();
                            if (parts.length > 1) parsedSpec = parts[1].trim();
                            if (parts.length > 2) parsedYear = parts[2].trim();
                            if (parts.length > 3) parsedSection = parts[3].trim();
                        } else {
                            parsedSection = sectionValue.trim();
                        }
                    }

                    // normalize and matching (same as before)
                    String nCourse = normalize(!parsedCourse.isEmpty() ? parsedCourse : courseNameRaw);
                    String nSpec = normalize(parsedSpec);
                    String nYear = normalize(parsedYear);
                    String nSection = normalize(parsedSection);
                    String nCourseDisplay = normalize(child.child("courseDisplay").getValue(String.class));

                    String nStuCourse = normalize(stuCourse);

                    boolean match = false;
                    boolean studentInfoAvailable = !(nStuCourse.isEmpty());

                    if (studentInfoAvailable) {
                        match = (!nCourse.isEmpty() ? nCourse.contains(nStuCourse) : true);
                        if (!match && !nCourseDisplay.isEmpty()) match = nCourseDisplay.contains(nStuCourse);
                    } else {
                        match = true;
                    }

                    if (!match) continue;

                    if (!takenQuizIds.contains(quizId) || SHOW_ALL_ACTIVE_FOR_DEBUG) {
                        QuizModel qm = new QuizModel();
                        qm.setQuizId(quizId);
                        qm.setQuizName(quizName != null ? quizName : "Quiz");
                        qm.setTeacherName(teacherName != null ? teacherName : "");
                        qm.setSubjectName(subjectName != null ? subjectName : "");
                        qm.setSectionName(parsedSection != null ? parsedSection : "");
                        qm.setCourseName(!parsedCourse.isEmpty() ? parsedCourse : courseNameRaw);
                        qm.setScheduledAt(scheduledAt);
                        qm.setDurationMinutes(duration != null ? duration : 0);
                        qm.setActive(active);

                        long computedAvailableAt = 0L;
                        if (availableAtFromDb != null && availableAtFromDb > 0) {
                            computedAvailableAt = availableAtFromDb;
                        } else if (availableAfterMinutes != null && availableAfterMinutes > 0 && scheduledAt > 0) {
                            computedAvailableAt = scheduledAt + (availableAfterMinutes * 60_000L);
                        } else if (scheduledAt > 0) {
                            computedAvailableAt = scheduledAt;
                        }
                        qm.setAvailableAt(computedAvailableAt > 0 ? computedAvailableAt : 0L);

                        qm.setSpecializationName(parsedSpec != null ? parsedSpec : "");
                        qm.setYearName(parsedYear != null ? parsedYear : "");
                        qm.setSectionName(parsedSection != null ? parsedSection : "");

                        boolean alreadyTaken = takenQuizIds.contains(quizId);
                        try { qm.setPresent(alreadyTaken); } catch (Exception ignored) {}

                        qm.setStatus("QUIZ");
                        qm.setAvailable(true);

                        newList.add(qm);
                        quizIds.add(quizId);

                        // NO presence listener attachment here (removed)
                    }
                } catch (Exception e) {
                    Log.w(TAG_DEBUG, "Error processing quiz node: " + e.getMessage());
                }
            }
        }

        runOnUiThread(() -> {
            synchronized (quizList) {
                quizList.clear();
                quizList.addAll(newList);
            }
            adapter.updateData(quizList);

            progress.setVisibility(View.GONE);
            tvEmpty.setVisibility(quizList.isEmpty() ? View.VISIBLE : View.GONE);

            if (autoOpenQuizId != null && !autoOpenQuizId.isEmpty()) {
                int idx = adapter.getPositionForQuizId(autoOpenQuizId);
                if (idx >= 0) {
                    rvQuizzes.scrollToPosition(idx);
                    adapter.setHighlightQuizId(autoOpenQuizId);
                    View root = findViewById(android.R.id.content);
                    Snackbar.make(root, "New quiz available: " + quizList.get(idx).getQuizName(), Snackbar.LENGTH_LONG)
                            .setAction("Open", v -> onQuizClick(quizList.get(idx)))
                            .show();
                }
                autoOpenQuizId = null;
            }
        });
    }

    private void attachScoresRealtimeListenerIfNeeded() {
        try {
            String studentId = scoresStudentId != null && !scoresStudentId.isEmpty() ? scoresStudentId : sessionManager.getStudentId();
            if (studentId == null || studentId.isEmpty()) return;

            if (scoresRefForStudent != null && scoresChildListener != null && studentId.equals(scoresStudentId)) return;

            if (scoresRefForStudent != null && scoresChildListener != null) {
                try { scoresRefForStudent.removeEventListener(scoresChildListener); } catch (Exception ignored) {}
            }

            scoresRefForStudent = FirebaseDatabase.getInstance().getReference("Scores").child(studentId);
            scoresChildListener = new ChildEventListener() {
                @Override public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    markQuizTakenLocally(snapshot.getKey());
                }
                @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    markQuizTakenLocally(snapshot.getKey());
                }
                @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                    startRealtimeListener();
                }
                @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
                @Override public void onCancelled(@NonNull DatabaseError error) { Log.w(TAG_DEBUG, "Scores listener cancelled: " + error.getMessage()); }
            };
            scoresRefForStudent.addChildEventListener(scoresChildListener);
            scoresStudentId = studentId;
            Log.d(TAG_DEBUG, "Attached Scores realtime listener for studentId=" + studentId);
        } catch (Exception e) {
            Log.w(TAG_DEBUG, "attachScoresRealtimeListener failed: " + e.getMessage());
        }
    }

    private void detachScoresRealtimeListener() {
        try {
            if (scoresRefForStudent != null && scoresChildListener != null) {
                scoresRefForStudent.removeEventListener(scoresChildListener);
            }
        } catch (Exception ignored) {}
        scoresRefForStudent = null;
        scoresChildListener = null;
        scoresStudentId = null;
    }

    private void markQuizTakenLocally(String quizId) {
        if (quizId == null) return;
        int pos = findIndexById(quizId);
        if (pos >= 0) {
            synchronized (quizList) {
                QuizModel e = quizList.get(pos);
                if (e != null) {
                    e.setPresent(true);
                    e.setAvailable(false);
                    e.setStatus("TAKEN");
                }
            }
            runOnUiThread(() -> adapter.notifyItemChanged(pos));
        }
    }

    private int findIndexById(String id) {
        if (id == null) return -1;
        synchronized (quizList) {
            for (int i = 0; i < quizList.size(); i++) {
                String quizId = quizList.get(i).getQuizId();
                if (quizId != null && quizId.equals(id)) return i;
            }
        }
        return -1;
    }

    private void debugFetchAndLog() {
        publicRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.e(TAG_DEBUG, "debugFetch failed");
                return;
            }
            DataSnapshot snapshot = task.getResult();
            if (snapshot != null) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    Log.d(TAG_DEBUG,
                            "Quiz: " + child.getKey()
                                    + " | quizName: " + child.child("quizName").getValue()
                                    + " | subjectName: " + child.child("subjectName").getValue()
                                    + " | section: " + child.child("section").getValue()
                                    + " | teacherName: " + child.child("teacherName").getValue()
                                    + " | teacherId: " + child.child("teacherId").getValue()
                                    + " | availableAt: " + child.child("availableAt").getValue()
                                    + " | durationMinutes: " + child.child("durationMinutes").getValue()
                                    + " | active: " + child.child("active").getValue());
                }
            }
        });
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase();
    }

    @Override
    public void onQuizClick(QuizModel quiz) {
        if (quiz == null || quiz.getQuizId() == null) return;

        Intent intent = new Intent(this, TakeQuizActivity.class);
        intent.putExtra("quizId", quiz.getQuizId());
        intent.putExtra("quizName", quiz.getQuizName());
        intent.putExtra("availableAt", quiz.getAvailableAt() != null ? quiz.getAvailableAt() : 0L);
        intent.putExtra("durationMinutes", quiz.getDurationMinutes() != null ? quiz.getDurationMinutes() : 0);
        startActivity(intent);
    }
}