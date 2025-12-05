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


// add these imports near the top of QuizListActivity.java (with the other imports)
import java.util.Map;
import java.util.HashMap;
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

    private final Map<String, ValueEventListener> quizPresenceListeners = new HashMap<>();
    private final Map<String, DatabaseReference> quizPresenceRefs = new HashMap<>();
    private final Map<String, ValueEventListener> examPresenceListeners = new HashMap<>();
    private final Map<String, DatabaseReference> examPresenceRefs = new HashMap<>();

    private DatabaseReference quizScoresRefForStudent;
    private DatabaseReference legacyScoresRefForStudent;
    private ChildEventListener quizScoresChildListener;
    private ChildEventListener legacyScoresChildListener;

    // Insert or replace the onCreate (and add helper) in your existing QuizListActivity.
// Only the relevant changed parts are shown — keep the rest of your file as-is.

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

        // Read Intent extras (if StudentDashboard passed them)
        try {
            Intent caller = getIntent();
            if (caller != null) {
                String intentStudentId = caller.getStringExtra("studentId");
                String intentCourse = caller.getStringExtra("courseName");
                String intentSpec = caller.getStringExtra("specializationName");
                String intentYear = caller.getStringExtra("yearName");
                String intentSection = caller.getStringExtra("sectionName");

                if (intentStudentId != null && !intentStudentId.isEmpty()) {
                    try { sessionManager.saveStudentId(intentStudentId); } catch (Exception ignored) {}
                    scoresStudentId = intentStudentId;
                }

                if (student == null) student = new StudentModel();
                if (intentCourse != null && !intentCourse.trim().isEmpty()) student.setCourseName(intentCourse);
                if (intentSpec != null && !intentSpec.trim().isEmpty()) student.setSpecializationName(intentSpec);
                if (intentYear != null && !intentYear.trim().isEmpty()) student.setYearName(intentYear);
                if (intentSection != null && !intentSection.trim().isEmpty()) student.setSectionName(intentSection);

                Log.d(TAG_DEBUG, "Intent-seeded student: course='" + student.getCourseName()
                        + "' spec='" + student.getSpecializationName()
                        + "' year='" + student.getYearName()
                        + "' section='" + student.getSectionName()
                        + "' scoresStudentId='" + scoresStudentId + "'");
            }
        } catch (Exception e) {
            Log.w(TAG_DEBUG, "Failed to read intent extras: " + e.getMessage());
        }

        autoOpenQuizId = getIntent().getStringExtra("autoOpenQuizId");
        publicRef = FirebaseDatabase.getInstance().getReference("AvailableQuizzes");

        // If we don't have course/section info yet but we do have a saved studentId,
        // fetch the student's profile from the DB to populate session and local student
        // BEFORE starting realtime listeners so filtering works immediately.
        String storedStudentId = null;
        try { storedStudentId = sessionManager.getStudentId(); } catch (Exception ignored) {}

        boolean hasStudentFields =
                (student != null &&
                        ( (student.getCourseName() != null && !student.getCourseName().trim().isEmpty()) ||
                                (student.getSpecializationName() != null && !student.getSpecializationName().trim().isEmpty()) ||
                                (student.getYearName() != null && !student.getYearName().trim().isEmpty()) ||
                                (student.getSectionName() != null && !student.getSectionName().trim().isEmpty())
                        ));

        if (!hasStudentFields && storedStudentId != null && !storedStudentId.isEmpty()) {
            fetchStudentProfileByStudentIdAndStart(storedStudentId);
        } else {
            // we either have student fields already or no studentId to look up - proceed
            debugFetchAndLog();
            startRealtimeListener();
            startChildNotifications();
        }
    }

    /**
     * Query "Students" for the given studentId, populate sessionManager and local `student`,
     * then start the listeners (debugFetch/startRealtime/startChildNotifications).
     */
    // Replace your fetchStudentProfileByStudentIdAndStart implementation with this version
// (or update the body where you read the student's DB node).

    private void fetchStudentProfileByStudentIdAndStart(@NonNull String studentId) {
        try {
            Log.d(TAG_DEBUG, "Looking up student profile for studentId=" + studentId);
            DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
            studentsRef.orderByChild("studentId").equalTo(studentId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot != null && snapshot.exists()) {
                                for (DataSnapshot ds : snapshot.getChildren()) {
                                    String course = ds.child("courseName").getValue(String.class);
                                    String spec = ds.child("specializationName").getValue(String.class);
                                    String year = ds.child("yearName").getValue(String.class);
                                    String section = ds.child("sectionName").getValue(String.class);
                                    String uid = ds.child("uid").getValue(String.class);
                                    String fullName = ds.child("fullName").getValue(String.class);
                                    String profileImage = ds.child("profileImage").getValue(String.class);

                                    // Build StudentModel and persist it into SessionManager
                                    StudentModel saved = new StudentModel();
                                    saved.setStudentId(studentId);
                                    if (uid != null) saved.setUid(uid);
                                    if (course != null) saved.setCourseName(course);
                                    if (spec != null) saved.setSpecializationName(spec);
                                    if (year != null) saved.setYearName(year);
                                    if (section != null) saved.setSectionName(section);
                                    if (fullName != null) saved.setFullName(fullName);
                                    if (profileImage != null) saved.setProfileImage(profileImage);

                                    try {
                                        sessionManager.saveStudentModel(saved);
                                    } catch (Exception ignored) {}

                                    // Update local `student` reference used by buildListFromPublicSnapshot
                                    student = saved;

                                    break; // use first matching node
                                }
                            } else {
                                Log.w(TAG_DEBUG, "Student profile not found for studentId=" + studentId);
                            }

                            // Start the listeners after populating student info
                            debugFetchAndLog();
                            startRealtimeListener();
                            startChildNotifications();
                        }

                        @Override public void onCancelled(@NonNull DatabaseError error) {
                            Log.w(TAG_DEBUG, "Student profile lookup cancelled: " + error.getMessage());
                            debugFetchAndLog();
                            startRealtimeListener();
                            startChildNotifications();
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG_DEBUG, "fetchStudentProfile failed: " + e.getMessage());
            debugFetchAndLog();
            startRealtimeListener();
            startChildNotifications();
        }
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
        detachAllPresenceListeners();
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

    // Replace your existing fetchScoresAndBuildList(...) with this:
    private void fetchScoresAndBuildList(@Nullable DataSnapshot publicSnapshot, @NonNull String studentId) {
        // References for both possible nodes
        DatabaseReference quizScoresRef = FirebaseDatabase.getInstance().getReference("QuizScores").child(studentId);
        DatabaseReference legacyScoresRef = FirebaseDatabase.getInstance().getReference("Scores").child(studentId);

        // First read QuizScores, then read legacy Scores and merge
        Set<String> takenQuizIds = new HashSet<>();

        quizScoresRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                for (DataSnapshot snap : task.getResult().getChildren()) {
                    String qid = snap.getKey();
                    if (qid != null) takenQuizIds.add(qid);
                }
            }

            // Now read legacy Scores and merge any keys
            legacyScoresRef.get().addOnCompleteListener(task2 -> {
                if (task2.isSuccessful() && task2.getResult() != null) {
                    for (DataSnapshot snap : task2.getResult().getChildren()) {
                        String qid = snap.getKey();
                        if (qid != null) takenQuizIds.add(qid);
                    }
                }

                // Now we have the union of quiz IDs in takenQuizIds
                scoresStudentId = studentId;
                buildListFromPublicSnapshot(publicSnapshot, takenQuizIds, studentId);
                attachScoresRealtimeListenerIfNeeded(); // ensure realtime updates are attached
            });
        });
    }

    // Replace the existing buildListFromPublicSnapshot(...) method in your QuizListActivity with this version.
    // Replace only the buildListFromPublicSnapshot(...) method in your QuizListActivity with this version.
    private void buildListFromPublicSnapshot(@Nullable DataSnapshot snapshot, @NonNull Set<String> takenQuizIds, @Nullable String studentId) {
        // Prepare output list
        List<QuizModel> newList = new ArrayList<>();
        quizIds.clear();
        adapter.setHighlightQuizId(null);

        // normalize student fields used for matching
        String stuCourse = normalize(student != null ? student.getCourseName() : null);
        String stuSpec = normalize(student != null ? student.getSpecializationName() : null);
        String stuYear = normalize(student != null ? student.getYearName() : null);
        String stuSection = normalize(student != null ? student.getSectionName() : null);

        Log.d(TAG_DEBUG, "Normalized student: course='" + stuCourse + "' spec='" + stuSpec + "' year='" + stuYear + "' section='" + stuSection + "'");

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
                    if (!active) continue;

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

                    // parse section field which may contain "Course - Spec - Year - Section"
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
                            // if the DB stored only the section name, treat it as parsedSection
                            parsedSection = sectionValue.trim();
                        }
                    }

                    // normalize parsed fields and course display fallback
                    String nCourse = normalize(!parsedCourse.isEmpty() ? parsedCourse : courseNameRaw);
                    String nSpec = normalize(parsedSpec);
                    String nYear = normalize(parsedYear);
                    String nSection = normalize(parsedSection);
                    String nCourseDisplay = normalize(child.child("courseDisplay").getValue(String.class));

                    // Strict matching rules:
                    // - Always require course to match when student has course (prevents showing other-course quizzes)
                    // - If student has specialization/year/section populated, require equality on those fields as well.
                    boolean match = true;

                    // course matching (use courseDisplay fallback)
                    if (!stuCourse.isEmpty()) {
                        boolean courseMatches = false;
                        if (!nCourse.isEmpty() && nCourse.equals(stuCourse)) courseMatches = true;
                        if (!courseMatches && !nCourseDisplay.isEmpty() && nCourseDisplay.equals(stuCourse)) courseMatches = true;
                        if (!courseMatches) match = false;
                    }

                    // specialization match if student provided it
                    if (match && !stuSpec.isEmpty()) {
                        if (nSpec.isEmpty() || !nSpec.equals(stuSpec)) match = false;
                    }

                    // year match if student provided it
                    if (match && !stuYear.isEmpty()) {
                        if (nYear.isEmpty() || !nYear.equals(stuYear)) match = false;
                    }

                    // SECTION: if student has section set, require section equality
                    if (match && !stuSection.isEmpty()) {
                        if (nSection.isEmpty() || !nSection.equals(stuSection)) {
                            match = false;
                        }
                    }

                    if (!match) continue;

                    // If not already taken (or debug flag), add to list
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

                        // compute availableAt as before
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

                        // <-- add model to UI list
                        newList.add(qm);

                        // <-- ATTACH PRESENCE LISTENER FOR THIS QUIZ (exact insertion point)
                        attachPresenceListenerForQuiz(quizId);

                        // bookkeeping
                        quizIds.add(quizId);
                    }
                } catch (Exception e) {
                    Log.w(TAG_DEBUG, "Error processing quiz node: " + e.getMessage());
                }
            }
        }

        // apply to UI on main thread
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

            // If listeners already attached for same student, do nothing
            if (quizScoresRefForStudent != null && quizScoresChildListener != null && studentId.equals(scoresStudentId)) return;

            // detach existing if any
            try {
                if (quizScoresRefForStudent != null && quizScoresChildListener != null) quizScoresRefForStudent.removeEventListener(quizScoresChildListener);
            } catch (Exception ignored) {}
            try {
                if (legacyScoresRefForStudent != null && legacyScoresChildListener != null) legacyScoresRefForStudent.removeEventListener(legacyScoresChildListener);
            } catch (Exception ignored) {}

            // Attach to QuizScores (preferred)
            quizScoresRefForStudent = FirebaseDatabase.getInstance().getReference("QuizScores").child(studentId);
            quizScoresChildListener = new ChildEventListener() {
                @Override public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    markQuizTakenLocally(snapshot.getKey());
                }
                @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    markQuizTakenLocally(snapshot.getKey());
                }
                @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                    // If score removed, refresh list fully
                    startRealtimeListener();
                }
                @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
                @Override public void onCancelled(@NonNull DatabaseError error) { Log.w(TAG_DEBUG, "QuizScores listener cancelled: " + error.getMessage()); }
            };
            quizScoresRefForStudent.addChildEventListener(quizScoresChildListener);

            // Also attach to legacy Scores for backward compatibility
            legacyScoresRefForStudent = FirebaseDatabase.getInstance().getReference("Scores").child(studentId);
            legacyScoresChildListener = new ChildEventListener() {
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
            legacyScoresRefForStudent.addChildEventListener(legacyScoresChildListener);

            scoresStudentId = studentId;
            Log.d(TAG_DEBUG, "Attached QuizScores and Scores realtime listeners for studentId=" + studentId);
        } catch (Exception e) {
            Log.w(TAG_DEBUG, "attachScoresRealtimeListener failed: " + e.getMessage());
        }
    }

    private void detachScoresRealtimeListener() {
        try {
            if (quizScoresRefForStudent != null && quizScoresChildListener != null) {
                quizScoresRefForStudent.removeEventListener(quizScoresChildListener);
            }
        } catch (Exception ignored) {}
        try {
            if (legacyScoresRefForStudent != null && legacyScoresChildListener != null) {
                legacyScoresRefForStudent.removeEventListener(legacyScoresChildListener);
            }
        } catch (Exception ignored) {}
        quizScoresRefForStudent = null;
        legacyScoresRefForStudent = null;
        quizScoresChildListener = null;
        legacyScoresChildListener = null;
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

    // Add this helper method inside the activity (best placed after startRealtimeListener / fetch methods):

    /**
     * Attach a realtime listener on QuizStudents/{quizId}/{studentId} and fallback ExamStudents/{quizId}/{studentId}.
     * When allowed or present becomes true, update adapter so the "Take Quiz" button shows immediately.
     */
    private void attachPresenceListenerForQuiz(@NonNull final String quizId) {
        if (quizId == null || quizId.trim().isEmpty()) return;
        final String key = quizId.trim();

        // already attached?
        if (quizPresenceListeners.containsKey(key)) return;

        // Determine studentId (scoresStudentId if explicit, otherwise stored studentId)
        final String studentId = (scoresStudentId != null && !scoresStudentId.isEmpty())
                ? scoresStudentId
                : sessionManager.getStudentId();

        if (studentId == null || studentId.isEmpty()) {
            // cannot attach until we know studentId
            android.util.Log.d(TAG_DEBUG, "attachPresenceListenerForQuiz: no studentId yet, skipping for quiz=" + key);
            return;
        }

        // QuizStudents ref + listener
        DatabaseReference qRef = FirebaseDatabase.getInstance()
                .getReference("QuizStudents").child(key).child(studentId);

        ValueEventListener qListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // if node missing, snapshot.exists() == false
                boolean allowed = Boolean.TRUE.equals(snapshot.child("allowed").getValue(Boolean.class));
                boolean present = Boolean.TRUE.equals(snapshot.child("present").getValue(Boolean.class));
                boolean allowStudent = allowed || present;
                android.util.Log.d(TAG_DEBUG, "QuizStudents presence change: quiz=" + key + " student=" + studentId + " allowed=" + allowed + " present=" + present);
                // Update adapter on UI thread
                runOnUiThread(() -> {
                    if (adapter != null) adapter.setStudentPresent(key, allowStudent);
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.w(TAG_DEBUG, "QuizStudents listener cancelled for quiz=" + key + ": " + error.getMessage());
            }
        };

        qRef.addValueEventListener(qListener);
        quizPresenceListeners.put(key, qListener);
        quizPresenceRefs.put(key, qRef);

        // Also attach fallback listener under ExamStudents so either write is picked up
        DatabaseReference eRef = FirebaseDatabase.getInstance()
                .getReference("ExamStudents").child(key).child(studentId);

        ValueEventListener eListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean allowed = Boolean.TRUE.equals(snapshot.child("allowed").getValue(Boolean.class));
                boolean present = Boolean.TRUE.equals(snapshot.child("present").getValue(Boolean.class));
                boolean allowStudent = allowed || present;
                android.util.Log.d(TAG_DEBUG, "ExamStudents presence change: quiz=" + key + " student=" + studentId + " allowed=" + allowed + " present=" + present);
                runOnUiThread(() -> {
                    if (adapter != null) adapter.setStudentPresent(key, allowStudent);
                });
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.w(TAG_DEBUG, "ExamStudents listener cancelled for quiz=" + key + ": " + error.getMessage());
            }
        };

        eRef.addValueEventListener(eListener);
        examPresenceListeners.put(key, eListener);
        examPresenceRefs.put(key, eRef);
    }

    // Add this helper to remove all presence listeners (call from onDestroy)
    private void detachAllPresenceListeners() {
        try {
            for (Map.Entry<String, DatabaseReference> en : quizPresenceRefs.entrySet()) {
                DatabaseReference ref = en.getValue();
                ValueEventListener l = quizPresenceListeners.get(en.getKey());
                if (ref != null && l != null) try { ref.removeEventListener(l); } catch (Exception ignored) {}
            }
            for (Map.Entry<String, DatabaseReference> en : examPresenceRefs.entrySet()) {
                DatabaseReference ref = en.getValue();
                ValueEventListener l = examPresenceListeners.get(en.getKey());
                if (ref != null && l != null) try { ref.removeEventListener(l); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        quizPresenceListeners.clear();
        quizPresenceRefs.clear();
        examPresenceListeners.clear();
        examPresenceRefs.clear();
    }
}