package com.finale.nextgen.student;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.finale.nextgen.R;
import com.finale.nextgen.admin.StudentModel;
import com.finale.nextgen.sync.PresenceHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Improved ExamListActivity:
 * - Fully supports offline exam list and question caching/usage.
 * - Loads exams from local Room DB (offline_exams) if offline.
 * - Caches fetched exams after Firebase reads for future offline access.
 * - Caches questions for each exam for offline-taking (via OfflineExamManager).
 * - Handles attendance/presents locally for offline sync/usage.
 */
public class ExamListActivity extends AppCompatActivity {

    private static final String TAG = "ExamListActivity";
    private static final String TAG_DEBUG = "EXAM_OFFLINE_DEBUG";
    private static final long MAX_LOGIN_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(15);
    private final int REFRESH_INTERVAL = 3000; // 3 seconds

    // --- UI ---
    private LinearLayout emptyStateLayout, layoutOfflinePrep;
    private TextView tvAvailableExamsCount, tvOfflinePrep;
    private ProgressBar progressOfflinePrep;
    private RecyclerView rvExams;
    private ExamAdapter examAdapter;
    private FloatingActionButton fabQrExam;

    // --- Data/State ---
    private List<ExamModel> examList = Collections.synchronizedList(new ArrayList<>());
    private boolean isFetchingExams = false;
    private Handler handler = new Handler();
    private Runnable examRefreshRunnable;
    private String currentStudentUid;
    private StudentModel currentStudent;

    // --- Firebase ---
    private FirebaseAuth auth;
    private DatabaseReference studentsRef, scoresRef, examsRef;

    // Broadcast for presence: reload exams from local DB if local changes
    private final android.content.BroadcastReceiver presenceSavedReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            if (PresenceHelper.ACTION_PRESENCE_SAVED.equals(intent.getAction())) {
                runOnUiThread(() -> loadExamsFromLocalDb());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvAvailableExamsCount = findViewById(R.id.tvAvailableExamsCount);
        emptyStateLayout = findViewById(R.id.emptyState);
        rvExams = findViewById(R.id.rvExams);
        layoutOfflinePrep = findViewById(R.id.layoutOfflinePrep);
        tvOfflinePrep = findViewById(R.id.tvOfflinePrep);
        progressOfflinePrep = findViewById(R.id.progressOfflinePrep);
        fabQrExam = findViewById(R.id.fabAddExam);
        fabQrExam.setOnClickListener(v ->
                startActivity(new Intent(ExamListActivity.this, StudentQRScannerActivity.class))
        );

        examAdapter = new ExamAdapter(this, examList);
        rvExams.setLayoutManager(new LinearLayoutManager(this));
        rvExams.setAdapter(examAdapter);

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No logged-in user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentStudentUid = currentUser.getUid();
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        examsRef = FirebaseDatabase.getInstance().getReference("Exams");
        scoresRef = FirebaseDatabase.getInstance().getReference("Scores");

        // ---- OFFLINE EXAM LIST LOGIC ----
        if (!isNetworkAvailable()) {
            loadExamsFromLocalDbNoNetwork();
            return; // do not continue with Firebase code
        }
        // ---- END OFFLINE LOGIC ----

        studentsRef.orderByChild("uid").equalTo(currentStudentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                currentStudent = ds.getValue(StudentModel.class);
                                break;
                            }
                            if (currentStudent != null) {
                                populateAndStart();
                            } else {
                                Toast.makeText(ExamListActivity.this, "Student record not found", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(ExamListActivity.this, "Student record not found", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ExamListActivity.this, "Failed to fetch data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void populateAndStart() {
        loadExamsFromLocalDb();
        startPeriodicExamFetch(currentStudent);
        fetchRealtimeExamStatus(currentStudent);
    }

    private void startPeriodicExamFetch(StudentModel student) {
        if (examRefreshRunnable != null) handler.removeCallbacks(examRefreshRunnable);
        examRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isFetchingExams) fetchExamsForStudent(student);
                handler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
        handler.post(examRefreshRunnable);
    }

    /** Loads exams from local DB for online UI and for caching new questions if network is available. */
    private void loadExamsFromLocalDb() {
        new Thread(() -> {
            com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(this);
            List<com.finale.nextgen.offline.ExamEntity> cachedExams =
                    db.examDao().getAllExamsForStudent(currentStudentUid);
            runOnUiThread(() -> {
                examList.clear();
                for (com.finale.nextgen.offline.ExamEntity entity : cachedExams) {
                    examList.add(toExamModel(entity));
                }
                updateExamRecyclerView();
                updateAvailableExamsCount();
                if (isNetworkAvailable()) cacheAllExamQuestionsForOffline(examList);
            });
        }).start();
    }

    /** Loads exams from local DB when network is not available on startup. */
    private void loadExamsFromLocalDbNoNetwork() {
        new Thread(() -> {
            com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(this);
            List<com.finale.nextgen.offline.ExamEntity> cachedExams =
                    db.examDao().getAllExamsForStudent(currentStudentUid);
            runOnUiThread(() -> {
                examList.clear();
                for (com.finale.nextgen.offline.ExamEntity entity : cachedExams) {
                    examList.add(toExamModel(entity));
                }
                updateExamRecyclerView();
                updateAvailableExamsCount();
                progressOfflinePrep.setVisibility(View.GONE);
                Log.d(TAG_DEBUG, "Offline: loaded " + examList.size() + " exams from local cache");
                Toast.makeText(ExamListActivity.this, "Offline: loaded " + examList.size() + " cached exams", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    /** Checks for network connection. */
    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        } catch (Exception e) { return false; }
    }

    /** Converts Room ExamEntity to ExamModel used by UI. */
    private ExamModel toExamModel(com.finale.nextgen.offline.ExamEntity entity) {
        ExamModel model = new ExamModel();
        model.setExamId(entity.examId);
        model.setExamTitle(entity.examTitle);
        model.setCourseName(entity.courseName);
        model.setSpecializationName(entity.specializationName);
        model.setYearName(entity.yearName);
        model.setSectionName(entity.sectionName);
        model.setTeacherName(entity.teacherName);
        model.setScheduledAt(entity.scheduledAt);
        model.setDurationMinutes(entity.durationMinutes);
        model.setActive(entity.active);
        model.setStatus(entity.status);
        model.setAvailable(entity.isAvailable);
        model.setPresent(entity.present);
        return model;
    }

    // Debug helper: dump cached exam by examId
    private void dumpSpecificCachedExam(final String examId) {
        new Thread(() -> {
            try {
                com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(this);
                com.finale.nextgen.offline.ExamEntity qe = db.examDao().getExamById(examId);
                if (qe == null) {
                    Log.d(TAG_DEBUG, "No cached row for examId=" + examId);
                } else {
                    Log.d(TAG_DEBUG, "Cached exam: id=" + qe.examId + " title=" + qe.examTitle
                            + " present=" + qe.present + " active=" + qe.active
                            + " availableAt=" + qe.scheduledAt + " duration=" + qe.durationMinutes
                            + " course=" + qe.courseName + " section=" + qe.sectionName
                            + " cachedAt=" + qe.scheduledAt);
                }
            } catch (Exception e) {
                Log.e(TAG_DEBUG, "dumpSpecificCachedExam failed: " + e.getMessage(), e);
            }
        }).start();
    }

    private void updateAvailableExamsCount() {
        int availableTodayCount = 0;
        Calendar today = Calendar.getInstance();
        for (ExamModel exam : examList) {
            if (exam == null) continue;
            if (exam.isActive() && isToday(exam.getScheduledAt())) {
                availableTodayCount++;
            }
        }
        tvAvailableExamsCount.setText("Available exams today: " + availableTodayCount);
    }

    private boolean isToday(Long timestampMillis) {
        if (timestampMillis == null) return false;
        Calendar now = Calendar.getInstance();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMillis);
        return now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);
    }

    /** Fetches all eligible exams from Firebase and saves them to local DB for offline use. */
    private void fetchExamsForStudent(StudentModel student) {
        if (isFetchingExams) return;
        isFetchingExams = true;
        String studentCourseDisplay = student.getCourseName()
                + " - " + student.getSpecializationName()
                + " - " + student.getYearName()
                + " - " + student.getSectionName();

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        String studentId = student.getStudentId();

        examsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ExamModel> tempExamList = Collections.synchronizedList(new ArrayList<>());
                List<DataSnapshot> eligibleExams = new ArrayList<>();
                for (DataSnapshot teacherSnap : snapshot.getChildren()) {
                    for (DataSnapshot examSnap : teacherSnap.getChildren()) {
                        ExamModel exam = examSnap.getValue(ExamModel.class);
                        Long scheduledAtLong = examSnap.child("scheduledAt").getValue(Long.class);
                        Integer durationMinutesInt = examSnap.child("durationMinutes").getValue(Integer.class);

                        if (scheduledAtLong != null && scheduledAtLong < 1_000_000_000_000L) {
                            scheduledAtLong = scheduledAtLong * 1000L;
                        }

                        if (exam != null && scheduledAtLong != null && durationMinutesInt != null
                                && exam.getCourseDisplay().equals(studentCourseDisplay)
                                && exam.isActive()) {
                            eligibleExams.add(examSnap);
                        }
                    }
                }
                final int totalEligibleExams = eligibleExams.size();
                final int[] examsProcessed = {0};

                if (totalEligibleExams == 0) {
                    runOnUiThread(() -> {
                        synchronized (examList) {
                            examList.clear();
                            updateExamRecyclerView();
                            updateAvailableExamsCount();
                        }
                        isFetchingExams = false;
                    });
                    return;
                }

                for (DataSnapshot examSnap : eligibleExams) {
                    ExamModel exam = examSnap.getValue(ExamModel.class);
                    String examId = examSnap.getKey();
                    exam.setExamId(examId);
                    Long scheduled = examSnap.child("scheduledAt").getValue(Long.class);
                    if (scheduled != null && scheduled < 1_000_000_000_000L) scheduled = scheduled * 1000L;
                    exam.setScheduledAt(scheduled);
                    exam.setDurationMinutes(examSnap.child("durationMinutes").getValue(Integer.class));

                    scoresRef.child(studentId).child(examId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot scoreSnapshot) {
                                    long now = System.currentTimeMillis();
                                    synchronized (tempExamList) {
                                        if (scoreSnapshot.exists()) {
                                            exam.setStatus("TAKEN");
                                            exam.setAvailable(false);
                                        } else {
                                            long start = exam.getScheduledAt();
                                            long endLogin = start + MAX_LOGIN_WINDOW_MILLIS;
                                            if (now < start) {
                                                exam.setStatus("Scheduled: Starts at " + sdf.format(new Date(start)));
                                                exam.setAvailable(false);
                                            } else if (now <= endLogin) {
                                                exam.setStatus("AVAILABLE NOW (Login closes at " + sdf.format(new Date(endLogin)) + ")");
                                                exam.setAvailable(true);
                                            } else {
                                                exam.setStatus("EXPIRED: Login window closed at " + sdf.format(new Date(endLogin)));
                                                exam.setAvailable(false);
                                            }
                                        }
                                        DatabaseReference examStudentRef = FirebaseDatabase.getInstance()
                                                .getReference("ExamStudents")
                                                .child(examId)
                                                .child(studentId);

                                        examStudentRef.child("present").addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override
                                            public void onDataChange(@NonNull DataSnapshot presentSnap) {
                                                synchronized (tempExamList) {
                                                    Boolean present = presentSnap.getValue(Boolean.class);
                                                    exam.setPresent(present != null && present);
                                                    exam.setAvailable(exam.isAvailable() && exam.isPresent());
                                                    tempExamList.add(exam);
                                                    examsProcessed[0]++;
                                                    if (examsProcessed[0] == totalEligibleExams) {
                                                        new Thread(() -> {
                                                            com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(ExamListActivity.this);
                                                            String studentIdForDb = student.getStudentId();
                                                            for (ExamModel ex : tempExamList) {
                                                                if (ex.getExamId() == null) continue;
                                                                try {
                                                                    com.finale.nextgen.offline.PendingSubmission p = db.pendingSubmissionDao()
                                                                            .findPendingByExamAndStudent(ex.getExamId(), studentIdForDb);
                                                                    if (p != null) {
                                                                        ex.setAvailable(false);
                                                                        ex.setPresent(true);
                                                                        ex.setStatus("TAKEN (Pending sync)");
                                                                    }
                                                                } catch (Exception e) {
                                                                    Log.e(TAG, "Error checking pending submission for exam " + ex.getExamId() + ": " + e.getMessage());
                                                                }
                                                            }
                                                            runOnUiThread(() -> {
                                                                synchronized (examList) {
                                                                    examList.clear();
                                                                    examList.addAll(tempExamList);
                                                                    updateExamRecyclerView();
                                                                    updateAvailableExamsCount();
                                                                    // Bulk save for offline cache
                                                                    new Thread(() -> {
                                                                        com.finale.nextgen.offline.AppDatabase db2 = com.finale.nextgen.offline.AppDatabase.getInstance(ExamListActivity.this);
                                                                        List<com.finale.nextgen.offline.ExamEntity> entities = new ArrayList<>();
                                                                        for (ExamModel ex : tempExamList) {
                                                                            com.finale.nextgen.offline.ExamEntity entity = new com.finale.nextgen.offline.ExamEntity();
                                                                            entity.examId = ex.getExamId();
                                                                            entity.examTitle = ex.getExamTitle();
                                                                            entity.courseName = ex.getCourseName();
                                                                            entity.specializationName = ex.getSpecializationName();
                                                                            entity.yearName = ex.getYearName();
                                                                            entity.sectionName = ex.getSectionName();
                                                                            entity.teacherName = ex.getTeacherName();
                                                                            entity.scheduledAt = ex.getScheduledAt();
                                                                            entity.durationMinutes = ex.getDurationMinutes();
                                                                            entity.active = ex.isActive();
                                                                            entity.status = ex.getStatus();
                                                                            entity.isAvailable = ex.isAvailable();
                                                                            entity.present = ex.isPresent();
                                                                            entity.studentUid = currentStudentUid;
                                                                            entity.cachedAt = System.currentTimeMillis();
                                                                            entities.add(entity);
                                                                        }
                                                                        db2.examDao().insertExams(entities);
                                                                        Log.d(TAG_DEBUG, "Saved " + entities.size() + " exams to offline cache.");
                                                                    }).start();
                                                                }
                                                                isFetchingExams = false;
                                                            });
                                                        }).start();
                                                    }
                                                }
                                            }
                                            @Override
                                            public void onCancelled(@NonNull DatabaseError error) {
                                                synchronized (tempExamList) {
                                                    examsProcessed[0]++;
                                                    if (examsProcessed[0] == totalEligibleExams) {
                                                        runOnUiThread(() -> {
                                                            isFetchingExams = false;
                                                        });
                                                    }
                                                }
                                            }
                                        });
                                    }
                                }
                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    synchronized (tempExamList) {
                                        examsProcessed[0]++;
                                        if (examsProcessed[0] == totalEligibleExams) {
                                            runOnUiThread(() -> {
                                                isFetchingExams = false;
                                            });
                                        }
                                    }
                                }
                            });
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                runOnUiThread(() -> {
                    synchronized (examList) {
                        examList.clear();
                        updateExamRecyclerView();
                        updateAvailableExamsCount();
                    }
                    isFetchingExams = false;
                });
            }
        });
    }

    private void updateExamRecyclerView() {
        if (examList.isEmpty()) {
            rvExams.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            rvExams.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
            examAdapter.notifyDataSetChanged();
        }
    }

    /** Caches all exam questions for offline use. */
    private void cacheAllExamQuestionsForOffline(List<ExamModel> exams) {
        if (exams == null || exams.isEmpty()) return;
        runOnUiThread(() -> {
            layoutOfflinePrep.setVisibility(View.VISIBLE);
            tvOfflinePrep.setText("Preparing exams for offline use... 0/" + exams.size());
        });
        final int total = exams.size();
        final int[] done = {0};
        for (ExamModel exam : exams) {
            String examId = exam.getExamId();
            if (examId == null) {
                done[0]++;
                continue;
            }
            new Thread(() -> {
                com.finale.nextgen.offline.OfflineExamManager mgr = new com.finale.nextgen.offline.OfflineExamManager(ExamListActivity.this);
                if (mgr.hasCachedQuestions(examId)) {
                    runOnUiThread(() -> {
                        done[0]++;
                        tvOfflinePrep.setText("Preparing exams for offline use... " + done[0] + "/" + total);
                        if (done[0] == total) {
                            layoutOfflinePrep.setVisibility(View.GONE);
                            Toast.makeText(ExamListActivity.this, "All exams are ready for offline use!", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                DatabaseReference questionsRef = FirebaseDatabase.getInstance().getReference("Questions").child(examId);
                questionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<com.finale.nextgen.offline.QuestionEntity> questions = new ArrayList<>();
                        for (DataSnapshot snap : snapshot.getChildren()) {
                            com.finale.nextgen.offline.QuestionEntity q = snap.getValue(com.finale.nextgen.offline.QuestionEntity.class);
                            if (q == null) q = new com.finale.nextgen.offline.QuestionEntity();
                            q.examId = examId;
                            q.firebaseKey = snap.getKey();
                            if (snap.child("questionText").exists())
                                q.questionText = snap.child("questionText").getValue(String.class);
                            if (snap.child("questionType").exists())
                                q.questionType = snap.child("questionType").getValue(String.class);
                            if (snap.child("correctAnswer").exists())
                                q.correctAnswer = snap.child("correctAnswer").getValue(String.class);
                            if (snap.child("optionA").exists())
                                q.optionA = snap.child("optionA").getValue(String.class);
                            if (snap.child("optionB").exists())
                                q.optionB = snap.child("optionB").getValue(String.class);
                            if (snap.child("optionC").exists())
                                q.optionC = snap.child("optionC").getValue(String.class);
                            if (snap.child("optionD").exists())
                                q.optionD = snap.child("optionD").getValue(String.class);
                            if (snap.child("displayNumber").exists()) {
                                Long dn = snap.child("displayNumber").getValue(Long.class);
                                if (dn != null) q.displayNumber = dn.intValue();
                            }
                            if (snap.child("matchingOptions").exists()) {
                                List<String> mo = (List<String>) snap.child("matchingOptions").getValue();
                                q.matchingOptions = mo;
                            }
                            questions.add(q);
                        }
                        new Thread(() -> {
                            com.finale.nextgen.offline.OfflineExamManager mgr2 =
                                    new com.finale.nextgen.offline.OfflineExamManager(ExamListActivity.this);
                            mgr2.saveQuestions(examId, questions);
                            runOnUiThread(() -> {
                                done[0]++;
                                tvOfflinePrep.setText("Preparing exams for offline use... " + done[0] + "/" + total);
                                if (done[0] == total) {
                                    layoutOfflinePrep.setVisibility(View.GONE);
                                    Toast.makeText(ExamListActivity.this, "All exams are ready for offline use!", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }).start();
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        runOnUiThread(() -> {
                            done[0]++;
                            tvOfflinePrep.setText("Preparing exams for offline use... " + done[0] + "/" + total);
                            if (done[0] == total) {
                                layoutOfflinePrep.setVisibility(View.GONE);
                                Toast.makeText(ExamListActivity.this, "All exams are ready for offline use!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }).start();
        }
    }

    private void fetchRealtimeExamStatus(StudentModel student) {
        // (Optional: Add live updates if you want, else you can comment/remove this)
    }

    // (Optional) Add QR scanner logic: mark attendance/present as true and persist locally for offline use
    public void markPresentOffline(String examId) {
        new Thread(() -> {
            try {
                com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(ExamListActivity.this);
                com.finale.nextgen.offline.ExamEntity entity = db.examDao().getExamById(examId);
                if (entity == null) {
                    entity = new com.finale.nextgen.offline.ExamEntity();
                    entity.examId = examId;
                }
                entity.present = true;
                entity.cachedAt = System.currentTimeMillis();
                db.examDao().insertExam(entity); // REPLACE semantics
                Log.d(TAG_DEBUG, "Persisted present=true for examId=" + examId);
            } catch (Exception e) {
                Log.e(TAG_DEBUG, "Failed to persist present: " + e.getMessage());
            }
        }).start();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        try {
            registerReceiver(presenceSavedReceiver, new IntentFilter(PresenceHelper.ACTION_PRESENCE_SAVED));
        } catch (Exception ignored) { }
    }

    @Override
    protected void onPause() {
        try { unregisterReceiver(presenceSavedReceiver); } catch (Exception ignored) { }
        if (handler != null && examRefreshRunnable != null)
            handler.removeCallbacks(examRefreshRunnable);
        super.onPause();
    }
}