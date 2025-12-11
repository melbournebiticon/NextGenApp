package com.finale.nextgen.student;




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




import com.finale.nextgen.R;
import com.finale.nextgen.admin.StudentModel;
import com.finale.nextgen.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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




import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.core.content.ContextCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;




/**
 * Patched QuizListActivity with offline caching integration (uses QuizCacheManager + Room).
 *
 * Integration points:
 * - Saves Firebase AvailableQuizzes snapshot to local Room cache after successful read.
 * - At startup if network unavailable, loads cached quizzes and shows them.
 * - Persists optimistic present when student scans a quiz.
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
    private static final int REQ_CODE_PICK_IMAGE = 0x1003;
    private static final int REQ_CODE_READ_STORAGE = 0x2001;




    private final Map<String, Boolean> lastQuizNodeExists = new HashMap<>();
    private final Map<String, Boolean> lastQuizAllowed = new HashMap<>();
    private final Map<String, Boolean> lastQuizPresent = new HashMap<>();
    private final Map<String, Boolean> lastExamAllowed = new HashMap<>();
    private final Map<String, Boolean> lastExamPresent = new HashMap<>();




    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);




        LocalBroadcastManager.getInstance(this).registerReceiver(
                quizSubmittedReceiver,
                new IntentFilter("com.finale.nextgen.QUIZ_SUBMITTED")
        );








        setContentView(R.layout.activity_quiz_list);
        sessionManager = new SessionManager(this);


        MaterialToolbar topBar = findViewById(R.id.topBar);
        setSupportActionBar(topBar);

        // Optional: set title in code to match XML
        // getSupportActionBar().setTitle("Take Quiz");

        // Enable the up button (shows back arrow)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Handle up (back arrow) button tap
        topBar.setNavigationOnClickListener(v -> {
            finish(); // Go back to previous activity
        });



        debugReadServerPresenceOnce( "-OfwYDC8ZXUPI6uNLcc6");




        rvQuizzes = findViewById(R.id.rvQuizzes);
        progress = findViewById(R.id.progressQuizzes);
        tvEmpty = findViewById(R.id.tvNoQuizzes);
        FloatingActionButton btnScanQr = findViewById(R.id.btnScanQr);





        rvQuizzes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new QuizListAdapter(quizList, this);
        rvQuizzes.setAdapter(adapter);




        // after: rvQuizzes.setLayoutManager(...) and rvQuizzes.setAdapter(adapter);
        dumpSpecificCachedQuiz("" +
                "-Oft2XaEk5EN_3l0WK2Y");




        LocalBroadcastManager.getInstance(this).registerReceiver(presenceReceiver,
                new IntentFilter("com.finale.nextgen.PRESENCE_UPDATED"));




        if (btnScanQr != null) {
            btnScanQr.setOnClickListener(v -> launchInAppScanner());
        }




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




        // offline fallback: if no network, try load cached quizzes
        // offline fallback: if no network, try load cached quizzes
        // Replace the existing offline fallback block in onCreate with this snippet:
        if (!isNetworkAvailable()) {
            // load cached quizzes off the main thread to avoid Room's "no DB on main thread" error
            new Thread(() -> {
                List<QuizModel> cached = com.finale.nextgen.offline.QuizCacheManager.loadCachedQuizzes(QuizListActivity.this);
                if (cached != null && !cached.isEmpty()) {
                    runOnUiThread(() -> {
                        adapter.updateData(cached);
                        progress.setVisibility(View.GONE);
                        tvEmpty.setVisibility(cached.isEmpty() ? View.VISIBLE : View.GONE);




                        // debug: show a toast/log so you know the cached branch ran
                        Log.d(TAG_DEBUG, "Offline: loaded " + cached.size() + " quizzes from local cache");
                        try { Toast.makeText(QuizListActivity.this, "Offline: loaded " + cached.size() + " cached quizzes", Toast.LENGTH_LONG).show(); } catch (Exception ignored) {}




                        attachScoresRealtimeListenerIfNeeded();
                    });
                    return;
                } else {
                    Log.d(TAG_DEBUG, "Offline: no cached quizzes found");
                }
                // If cached empty, continue to try the online listener (falls through)
                runOnUiThread(() -> {
                    // show spinner while online fetch will happen
                    progress.setVisibility(View.VISIBLE);
                    // fallback: start realtime listener (will run when network is available)
                    startRealtimeListener();
                    startChildNotifications();
                });
            }).start();




            // Important: return here so onCreate doesn't continue to call startRealtimeListener()
            // (we started it in the background branch above).
            return;
        }




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
            debugFetchAndLog();
            startRealtimeListener();
            startChildNotifications();
        }
    }




    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        } catch (Exception e) {
            return false;
        }
    }




    // inside QuizListActivity: update the BroadcastReceiver logic
    // inside QuizListActivity
    private final BroadcastReceiver presenceReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String quizId = intent.getStringExtra("quizId");
            String studentId = intent.getStringExtra("studentId");
            boolean present = intent.getBooleanExtra("present", true); // default true




            if (quizId == null || quizId.trim().isEmpty()) return;
            if (sessionManager == null) return;
            String myStudentId = sessionManager.getStudentId();




            if (studentId == null || studentId.equals(myStudentId)) {
                // update UI
                if (adapter != null) adapter.setStudentPresent(quizId, present);




                // Persist presence into local cache so offline shows same state
                new Thread(() -> {
                    try {
                        com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(context);
                        com.finale.nextgen.offline.QuizEntity qe = db.quizDao().getById(quizId);
                        if (qe == null) {
                            qe = new com.finale.nextgen.offline.QuizEntity();
                            qe.quizId = quizId;
                            // optionally set a minimal name so row exists
                            qe.quizName = intent.getStringExtra("quizName");
                            qe.cachedAt = System.currentTimeMillis();
                        }
                        qe.present = present;
                        qe.cachedAt = System.currentTimeMillis();
                        db.quizDao().insert(qe); // REPLACE semantics
                        Log.d("QuizListDebug", "Persisted presence for quizId=" + quizId + " present=" + present);
                    } catch (Exception e) {
                        Log.w("QuizListDebug", "Failed to persist presence: " + e.getMessage());
                    }
                }).start();
            }
        }
    };




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




                                    StudentModel saved = new StudentModel();
                                    saved.setStudentId(studentId);
                                    if (uid != null) saved.setUid(uid);
                                    if (course != null) saved.setCourseName(course);
                                    if (spec != null) saved.setSpecializationName(spec);
                                    if (year != null) saved.setYearName(year);
                                    if (section != null) saved.setSectionName(section);
                                    if (fullName != null) saved.setFullName(fullName);
                                    if (profileImage != null) saved.setProfileImage(profileImage);




                                    try { sessionManager.saveStudentModel(saved); } catch (Exception ignored) {}
                                    student = saved;
                                    break;
                                }
                            } else {
                                Log.w(TAG_DEBUG, "Student profile not found for studentId=" + studentId);
                            }




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
    // Add inside QuizListActivity
    private void dumpSpecificCachedQuiz(final String quizId) {
        new Thread(() -> {
            try {
                com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(this);
                com.finale.nextgen.offline.QuizEntity qe = db.quizDao().getById(quizId);
                if (qe == null) {
                    Log.d("QuizListDebug", "No cached row for quizId=" + quizId);
                } else {
                    Log.d("QuizListDebug", "Cached quiz: id=" + qe.quizId + " name=" + qe.quizName
                            + " present=" + qe.present + " active=" + qe.active
                            + " availableAt=" + qe.availableAt + " duration=" + qe.durationMinutes
                            + " course=" + qe.courseName + " section=" + qe.sectionName
                            + " cachedAt=" + qe.cachedAt);
                }
            } catch (Exception e) {
                Log.e("QuizListDebug", "dumpSpecificCachedQuiz failed: " + e.getMessage(), e);
            }
        }).start();
    }




    private void startChildNotifications() { /* no-op for now */ }




    @Override
    protected void onResume() {
        super.onResume();
        attachScoresRealtimeListenerIfNeeded();

        // Try to upload pending scores if you have that logic (optional)
        // attemptUploadPendingScores();

        // If online, fetch authoritative scores for any quizzes that were marked present/taken but show no score
        if (isNetworkAvailable()) {
            new Thread(() -> {
                List<String> idsToCheck = new ArrayList<>();
                synchronized (quizList) {
                    for (QuizModel qm : quizList) {
                        if (qm == null) continue;
                        boolean present = Boolean.TRUE.equals(qm.getPresent());
                        boolean taken = "TAKEN".equalsIgnoreCase(qm.getStatus());
                        boolean hasScore = false;
                        try { hasScore = qm.getScore() != null; } catch (Exception ignored) {}
                        if ((present || taken) && !hasScore) {
                            idsToCheck.add(qm.getQuizId());
                        }
                    }
                }
                for (String qid : idsToCheck) {
                    tryFetchScoreForQuiz(qid);
                }
            }).start();
        }
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(presenceReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(quizSubmittedReceiver);
    }




    private void stopChildNotifications() { /* no-op */ }




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
                        adapter.markOptimisticPresent(quizIdToUse);
                        adapter.setStudentPresent(quizIdToUse, true);




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




                        // persist optimistic present into cache
                        new Thread(() -> {
                            try {
                                com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(QuizListActivity.this);
                                com.finale.nextgen.offline.QuizEntity qe = db.quizDao().getById(quizIdToUse);
                                if (qe == null) {
                                    qe = new com.finale.nextgen.offline.QuizEntity();
                                    qe.quizId = quizIdToUse;
                                }
                                qe.present = true;
                                qe.cachedAt = System.currentTimeMillis();
                                db.quizDao().insert(qe);
                            } catch (Exception e) {
                                Log.w(TAG_DEBUG, "persist optimistic present failed: " + e.getMessage());
                            }
                        }).start();




                        autoOpenQuizId = quizIdToUse;
                        adapter.setHighlightQuizId(quizIdToUse);
                        int pos = adapter.getPositionForQuizId(quizIdToUse);
                        if (pos >= 0) rvQuizzes.scrollToPosition(pos);




                        View root = findViewById(android.R.id.content);
                        if (root != null) {
                            Snackbar.make(root, "Marked present locally. Tap 'Take Quiz' to start.", Snackbar.LENGTH_LONG)
                                    .setAction("Open", v -> {
                                        int idx = adapter.getPositionForQuizId(quizIdToUse);
                                        if (idx >= 0) {
                                            synchronized (quizList) {
                                                if (idx < quizList.size()) {
                                                    onQuizClick(quizList.get(idx));
                                                    return;
                                                }
                                            }
                                        }
                                        synchronized (quizList) {
                                            for (QuizModel qm : quizList) {
                                                if (qm != null && quizIdToUse.equalsIgnoreCase(qm.getQuizId())) {
                                                    onQuizClick(qm);
                                                    return;
                                                }
                                            }
                                        }
                                        Snackbar.make(findViewById(android.R.id.content), "Quiz not available yet. Refreshing list...", Snackbar.LENGTH_SHORT).show();
                                        startRealtimeListener();
                                    })
                                    .show();
                        }
                    }
                }
            }
            return;
        }




        // NEW: handle gallery image pick that may contain a QR code
        if (requestCode == REQ_CODE_PICK_IMAGE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    try {
                        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                        opts.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
                        java.io.InputStream is = getContentResolver().openInputStream(imageUri);
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is, null, opts);
                        if (is != null) try { is.close(); } catch (Exception ignored) {}




                        if (bmp != null) {
                            String decoded = decodeQRCodeFromBitmap(bmp); // add helper decodeQRCodeFromBitmap(Bitmap) to the activity
                            if (decoded != null && !decoded.trim().isEmpty()) {
                                String candidate = decoded.trim();
                                if (candidate.toLowerCase().startsWith("quiz:")) candidate = candidate.substring("quiz:".length()).trim();
                                String matched = findMatchingQuizId(candidate);
                                final String quizIdToUse = matched != null ? matched : candidate;




                                if (!quizIdToUse.isEmpty()) {
                                    adapter.markOptimisticPresent(quizIdToUse);
                                    adapter.setStudentPresent(quizIdToUse, true);




                                    // persist optimistic present
                                    new Thread(() -> {
                                        try {
                                            com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(QuizListActivity.this);
                                            com.finale.nextgen.offline.QuizEntity qe = db.quizDao().getById(quizIdToUse);
                                            if (qe == null) {
                                                qe = new com.finale.nextgen.offline.QuizEntity();
                                                qe.quizId = quizIdToUse;
                                            }
                                            qe.present = true;
                                            qe.cachedAt = System.currentTimeMillis();
                                            db.quizDao().insert(qe);
                                        } catch (Exception e) {
                                            Log.w(TAG_DEBUG, "persist optimistic present failed: " + e.getMessage());
                                        }
                                    }).start();




                                    autoOpenQuizId = quizIdToUse;
                                    adapter.setHighlightQuizId(quizIdToUse);
                                    int pos = adapter.getPositionForQuizId(quizIdToUse);
                                    if (pos >= 0) rvQuizzes.scrollToPosition(pos);




                                    View root = findViewById(android.R.id.content);
                                    if (root != null) Snackbar.make(root, "Marked present locally. Tap 'Take Quiz' to start.", Snackbar.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(this, "QR decoded but no matching quiz found: " + decoded, Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Toast.makeText(this, "No QR code detected in selected image.", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(this, "Failed to decode image.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG_DEBUG, "gallery pick decode failed: " + e.getMessage(), e);
                        Toast.makeText(this, "Failed to read image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
            return;
        }




        if (requestCode == REQ_CODE_ZXING_SCAN) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String scanContents = data.getStringExtra("SCAN_RESULT");
                if (scanContents != null && !scanContents.isEmpty()) {
                    String candidate = scanContents.trim();
                    String matched = findMatchingQuizId(candidate);
                    final String quizIdToUse = matched != null ? matched : candidate;




                    adapter.markOptimisticPresent(quizIdToUse);
                    adapter.setStudentPresent(quizIdToUse, true);




                    // persist optimistic present
                    new Thread(() -> {
                        try {
                            com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(QuizListActivity.this);
                            com.finale.nextgen.offline.QuizEntity qe = db.quizDao().getById(quizIdToUse);
                            if (qe == null) {
                                qe = new com.finale.nextgen.offline.QuizEntity();
                                qe.quizId = quizIdToUse;
                            }
                            qe.present = true;
                            qe.cachedAt = System.currentTimeMillis();
                            db.quizDao().insert(qe);
                        } catch (Exception e) {
                            Log.w(TAG_DEBUG, "persist optimistic present failed: " + e.getMessage());
                        }
                    }).start();




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




    // Add this method inside QuizListActivity (class body)
    private @Nullable String decodeQRCodeFromBitmap(android.graphics.Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);




            com.google.zxing.RGBLuminanceSource source =
                    new com.google.zxing.RGBLuminanceSource(width, height, pixels);
            com.google.zxing.common.HybridBinarizer binarizer =
                    new com.google.zxing.common.HybridBinarizer(source);
            com.google.zxing.BinaryBitmap binaryBitmap = new com.google.zxing.BinaryBitmap(binarizer);




            com.google.zxing.Reader reader = new com.google.zxing.MultiFormatReader();
            com.google.zxing.Result result = reader.decode(binaryBitmap);
            return result != null ? result.getText() : null;
        } catch (com.google.zxing.NotFoundException nfe) {
            // No QR code found in image
            return null;
        } catch (Exception e) {
            Log.w(TAG_DEBUG, "decodeQRCodeFromBitmap failed: " + e.getMessage(), e);
            return null;
        }
    }
    private void startRealtimeListener() {
        progress.setVisibility(View.VISIBLE);
        publicRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override // Replace the onDataChange body in startRealtimeListener() with this:
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // DEBUG: log the size returned from Firebase
                Log.d("QuizListActivity", "AvailableQuizzes snapshot size=" + (snapshot == null ? 0 : snapshot.getChildrenCount()));




                // save snapshot to local cache for offline fallback
                com.finale.nextgen.offline.QuizCacheManager.saveSnapshot(QuizListActivity.this, snapshot);




                // existing logic
                resolveStudentIdAndFetchScores(snapshot);
            }
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
        DatabaseReference quizScoresRef = FirebaseDatabase.getInstance().getReference("QuizScores").child(studentId);
        DatabaseReference legacyScoresRef = FirebaseDatabase.getInstance().getReference("Scores").child(studentId);




        Set<String> takenQuizIds = new HashSet<>();




        quizScoresRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                for (DataSnapshot snap : task.getResult().getChildren()) {
                    String qid = snap.getKey();
                    if (qid != null) takenQuizIds.add(qid);
                }
            }




            legacyScoresRef.get().addOnCompleteListener(task2 -> {
                if (task2.isSuccessful() && task2.getResult() != null) {
                    for (DataSnapshot snap : task2.getResult().getChildren()) {
                        String qid = snap.getKey();
                        if (qid != null) takenQuizIds.add(qid);
                    }
                }




                scoresStudentId = studentId;
                buildListFromPublicSnapshot(publicSnapshot, takenQuizIds, studentId);
                attachScoresRealtimeListenerIfNeeded();
            });
        });
    }




    // Replace the existing buildListFromPublicSnapshot(...) method with this version.
    private void buildListFromPublicSnapshot(@Nullable DataSnapshot snapshot, @NonNull Set<String> takenQuizIds, @Nullable String studentId) {
        List<QuizModel> newList = new ArrayList<>();
        quizIds.clear();
        adapter.setHighlightQuizId(null);




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
                    if (quizIds.contains(quizId)) continue;




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




                    String nCourse = normalize(!parsedCourse.isEmpty() ? parsedCourse : courseNameRaw);
                    String nSpec = normalize(parsedSpec);
                    String nYear = normalize(parsedYear);
                    String nSection = normalize(parsedSection);
                    String nCourseDisplay = normalize(child.child("courseDisplay").getValue(String.class));




                    boolean match = true;




                    if (!stuCourse.isEmpty()) {
                        boolean courseMatches = false;
                        if (!nCourse.isEmpty() && nCourse.equals(stuCourse)) courseMatches = true;
                        if (!courseMatches && !nCourseDisplay.isEmpty() && nCourseDisplay.equals(stuCourse)) courseMatches = true;
                        if (!courseMatches) match = false;
                    }




                    if (match && !stuSpec.isEmpty()) {
                        if (nSpec.isEmpty() || !nSpec.equals(stuSpec)) match = false;
                    }




                    if (match && !stuYear.isEmpty()) {
                        if (nYear.isEmpty() || !nYear.equals(stuYear)) match = false;
                    }




                    if (match && !stuSection.isEmpty()) {
                        if (nSection.isEmpty() || !nSection.equals(stuSection)) {
                            match = false;
                        }
                    }




                    if (!match) continue;




                    // Always include the quiz; show as TAKEN when score exists instead of removing it
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
                    try {
                        if (alreadyTaken) {
                            // Mark the model as taken; do NOT mark 'present' just because it's taken.
                            qm.setStatus("TAKEN");
                            qm.setAvailable(false);
                            // Mark present=true so adapter renders the TAKEN state (it keys off 'present' for taken)
                            qm.setPresent(true);
                            qm.setStudentPresent(true);
                            // If QuizModel exposes setTaken(boolean), set it (reflection fallback used for safety)
                            try {
                                java.lang.reflect.Method m = qm.getClass().getMethod("setTaken", boolean.class);
                                if (m != null) m.invoke(qm, true);
                            } catch (NoSuchMethodException ignored) {}
                        } else {
                            qm.setStatus("QUIZ");
                            qm.setAvailable(true);
                            qm.setPresent(false); // default; presence will be updated by realtime listeners
                        }
                    } catch (Exception ignored) {}




                    newList.add(qm);




                    attachPresenceListenerForQuiz(quizId);




                    quizIds.add(quizId);
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




            if (quizScoresRefForStudent != null && quizScoresChildListener != null && studentId.equals(scoresStudentId)) return;




            try {
                if (quizScoresRefForStudent != null && quizScoresChildListener != null) quizScoresRefForStudent.removeEventListener(quizScoresChildListener);
            } catch (Exception ignored) {}
            try {
                if (legacyScoresRefForStudent != null && legacyScoresChildListener != null) legacyScoresRefForStudent.removeEventListener(legacyScoresChildListener);
            } catch (Exception ignored) {}




            quizScoresRefForStudent = FirebaseDatabase.getInstance().getReference("QuizScores").child(studentId);
            quizScoresChildListener = new ChildEventListener() {
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
                @Override public void onCancelled(@NonNull DatabaseError error) { Log.w(TAG_DEBUG, "QuizScores listener cancelled: " + error.getMessage()); }
            };
            quizScoresRefForStudent.addChildEventListener(quizScoresChildListener);




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




        Log.d(TAG_DEBUG, "markQuizTakenLocally called for quizId=" + quizId);




        if (adapter != null) {
            adapter.setQuizTaken(quizId);
            return;
        }




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




    // Replace the existing onQuizClick(...) method with this:
    @Override
    public void onQuizClick(QuizModel quiz) {
        if (quiz == null || quiz.getQuizId() == null) return;




        boolean isPresent = false;
        try { isPresent = Boolean.TRUE.equals(quiz.getPresent()); } catch (Exception ignored) {}




        if (isPresent || "TAKEN".equalsIgnoreCase(quiz.getStatus())) {
            Toast.makeText(this, "You have already taken this quiz.", Toast.LENGTH_SHORT).show();
            return;
        }




        // Defer starting the quiz until we verify server-side permission (prefer QuizStudents)
        checkAllowedAndStart(quiz);
    }




    /**
     * Reads QuizStudents/{quizId}/{studentId} (authoritative if present).
     * If QuizStudents node doesn't exist, falls back to ExamStudents/{quizId}/{studentId}.
     * Starts TakeQuizActivity only when allowed.
     */
    // Replace the existing checkAllowedAndStart(...) method in QuizListActivity with this implementation.
    private void checkAllowedAndStart(@NonNull final QuizModel quiz) {
        final String quizId = quiz.getQuizId();
        final String studentId = (scoresStudentId != null && !scoresStudentId.isEmpty())
                ? scoresStudentId
                : sessionManager.getStudentId();

        if (studentId == null || studentId.trim().isEmpty()) {
            Log.d(TAG_DEBUG, "checkAllowedAndStart: no studentId available; blocking start for quiz=" + quizId);
            Toast.makeText(this, "Student identity not available; cannot start quiz.", Toast.LENGTH_SHORT).show();
            return;
        }

        // OFFLINE PATH: if there's no network, rely on local cache / optimistic present
        if (!isNetworkAvailable()) {
            Log.d(TAG_DEBUG, "Offline: checking local cache for optimistic presence for quiz=" + quizId);
            new Thread(() -> {
                try {
                    com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(QuizListActivity.this);
                    com.finale.nextgen.offline.QuizEntity qe = db.quizDao().getById(quizId);
                    boolean presentLocal = qe != null && Boolean.TRUE.equals(qe.present);

                    runOnUiThread(() -> {
                        if (presentLocal) {
                            Log.d(TAG_DEBUG, "Offline: found local present=true for quiz=" + quizId + ", starting TakeQuizActivity");
                            startTakeQuizActivityWithModel(quiz);
                        } else {
                            Log.d(TAG_DEBUG, "Offline: no local present record for quiz=" + quizId + ", cannot verify permission");
                            Toast.makeText(QuizListActivity.this,
                                    "Offline: unable to verify permission for this quiz. Please try again when online or ask your instructor.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    Log.w(TAG_DEBUG, "Offline permission check failed: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(QuizListActivity.this,
                            "Offline: unable to verify permission for this quiz. Please try again when online.",
                            Toast.LENGTH_LONG).show());
                }
            }).start();
            return;
        }

        // ONLINE PATH: original authoritative server checks (QuizStudents -> fallback ExamStudents)
        DatabaseReference quizRef = FirebaseDatabase.getInstance()
                .getReference("QuizStudents").child(quizId).child(studentId);

        quizRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean exists = snapshot.exists();
                Boolean allowed = snapshot.child("allowed").getValue(Boolean.class);
                Boolean present = snapshot.child("present").getValue(Boolean.class);

                Log.d(TAG_DEBUG, "serverCheck: QuizStudents read quiz=" + quizId + " student=" + studentId
                        + " exists=" + exists + " allowed=" + allowed + " present=" + present
                        + " value=" + (snapshot.getValue() == null ? "null" : snapshot.getValue().toString()));

                if (exists) {
                    boolean allow = Boolean.TRUE.equals(allowed) || Boolean.TRUE.equals(present);
                    if (allow) {
                        Log.d(TAG_DEBUG, "serverCheck: allowed by QuizStudents -> starting quiz " + quizId);
                        startTakeQuizActivityWithModel(quiz);
                    } else {
                        Log.d(TAG_DEBUG, "serverCheck: denied by QuizStudents -> blocking quiz " + quizId);
                        Toast.makeText(QuizListActivity.this, "You are not allowed to take this quiz.", Toast.LENGTH_SHORT).show();
                        // trigger a refresh so UI updates quickly
                        startRealtimeListener();
                    }
                    return;
                }

                // Fallback to ExamStudents only when QuizStudents is absent
                DatabaseReference examRef = FirebaseDatabase.getInstance()
                        .getReference("ExamStudents").child(quizId).child(studentId);

                examRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap2) {
                        Boolean allowed2 = snap2.child("allowed").getValue(Boolean.class);
                        Boolean present2 = snap2.child("present").getValue(Boolean.class);
                        Log.d(TAG_DEBUG, "serverCheck: ExamStudents read quiz=" + quizId + " student=" + studentId
                                + " exists=" + snap2.exists() + " allowed=" + allowed2 + " present=" + present2
                                + " value=" + (snap2.getValue() == null ? "null" : snap2.getValue().toString()));

                        boolean allow2 = Boolean.TRUE.equals(allowed2) || Boolean.TRUE.equals(present2);
                        if (allow2) {
                            Log.d(TAG_DEBUG, "serverCheck: allowed by ExamStudents fallback -> starting quiz " + quizId);
                            startTakeQuizActivityWithModel(quiz);
                        } else {
                            Log.d(TAG_DEBUG, "serverCheck: denied by ExamStudents fallback -> blocking quiz " + quizId);
                            Toast.makeText(QuizListActivity.this, "You are not allowed to take this quiz.", Toast.LENGTH_SHORT).show();
                            startRealtimeListener();
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        Log.w(TAG_DEBUG, "serverCheck: ExamStudents read cancelled: " + error.getMessage());
                        Toast.makeText(QuizListActivity.this, "Unable to verify permission: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG_DEBUG, "serverCheck: QuizStudents read cancelled: " + error.getMessage());
                Toast.makeText(QuizListActivity.this, "Unable to verify permission: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }




    /** Start TakeQuizActivity with the same intent-building logic you had in onQuizClick. */
    private void startTakeQuizActivityWithModel(@NonNull QuizModel quiz) {
        Intent intent = new Intent(this, TakeQuizActivity.class);
        intent.putExtra("quizId", quiz.getQuizId());
        intent.putExtra("quizName", quiz.getQuizName());
        intent.putExtra("availableAt", quiz.getAvailableAt() != null ? quiz.getAvailableAt() : 0L);
        intent.putExtra("durationMinutes", quiz.getDurationMinutes() != null ? quiz.getDurationMinutes() : 0);




        try {
            java.lang.reflect.Method m = quiz.getClass().getMethod("getSubjectId");
            Object sid = m.invoke(quiz);
            if (sid instanceof String && !((String) sid).trim().isEmpty()) {
                intent.putExtra("subjectId", (String) sid);
            }
        } catch (Exception ignored) {}




        if (quiz.getSubjectName() != null) intent.putExtra("subjectName", quiz.getSubjectName());
        if (quiz.getTeacherName() != null) intent.putExtra("teacherName", quiz.getTeacherName());




        String courseCodeOrName = null;
        try {
            java.lang.reflect.Method m = quiz.getClass().getMethod("getSubjectCode");
            Object sc = m.invoke(quiz);
            if (sc instanceof String) courseCodeOrName = (String) sc;
        } catch (Exception ignored) {}
        if (courseCodeOrName == null || courseCodeOrName.trim().isEmpty()) {
            courseCodeOrName = quiz.getCourseName();
        }
        if (courseCodeOrName != null) intent.putExtra("courseCode", courseCodeOrName);




        Log.d("QuizListActivity", "Launching TakeQuizActivity: quizId=" + quiz.getQuizId()
                + " subjectId=" + intent.getStringExtra("subjectId")
                + " subjectName=" + intent.getStringExtra("subjectName")
                + " courseCode=" + intent.getStringExtra("courseCode")
                + " teacher=" + intent.getStringExtra("teacherName"));




        startActivity(intent);
    }




    /** Optional debug helper: logs both QuizStudents and ExamStudents values for a given quizId (call from UI for quick checks). */
    private void debugReadServerPresenceOnce(final String quizId) {
        if (quizId == null || quizId.trim().isEmpty()) {
            Log.d(TAG_DEBUG, "debugReadServerPresenceOnce: quizId is empty");
            return;
        }




        // Ensure we have a SessionManager instance (fall back to creating one if necessary)
        SessionManager sm = this.sessionManager;
        if (sm == null) {
            try {
                sm = new SessionManager(this);
            } catch (Exception e) {
                Log.w(TAG_DEBUG, "debugReadServerPresenceOnce: failed to construct SessionManager: " + e.getMessage());
                sm = null;
            }
        }




        final String studentId = (scoresStudentId != null && !scoresStudentId.isEmpty())
                ? scoresStudentId
                : (sm != null ? sm.getStudentId() : null);




        if (studentId == null || studentId.isEmpty()) {
            Log.d(TAG_DEBUG, "debugReadServerPresenceOnce: no studentId available");
            return;
        }




        DatabaseReference qRef = FirebaseDatabase.getInstance().getReference("QuizStudents").child(quizId).child(studentId);
        qRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG_DEBUG, "QuizStudents read: quiz=" + quizId + " student=" + studentId + " exists=" + snapshot.exists()
                        + " present=" + snapshot.child("present").getValue(Boolean.class)
                        + " allowed=" + snapshot.child("allowed").getValue(Boolean.class)
                        + " value=" + (snapshot.getValue() == null ? "null" : snapshot.getValue().toString()));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG_DEBUG, "QuizStudents read cancelled: " + error.getMessage());
            }
        });




        DatabaseReference eRef = FirebaseDatabase.getInstance().getReference("ExamStudents").child(quizId).child(studentId);
        eRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG_DEBUG, "ExamStudents read: quiz=" + quizId + " student=" + studentId + " exists=" + snapshot.exists()
                        + " present=" + snapshot.child("present").getValue(Boolean.class)
                        + " allowed=" + snapshot.child("allowed").getValue(Boolean.class)
                        + " value=" + (snapshot.getValue() == null ? "null" : snapshot.getValue().toString()));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG_DEBUG, "ExamStudents read cancelled: " + error.getMessage());
            }
        });
    }




    // Replace the existing attachPresenceListenerForQuiz(...) method in QuizListActivity with this code.
    private void attachPresenceListenerForQuiz(@NonNull final String quizId) {
        if (quizId == null || quizId.trim().isEmpty()) return;
        final String key = quizId.trim();




        if (quizPresenceListeners.containsKey(key)) return;




        final String studentId = (scoresStudentId != null && !scoresStudentId.isEmpty())
                ? scoresStudentId
                : sessionManager.getStudentId();




        if (studentId == null || studentId.isEmpty()) {
            android.util.Log.d(TAG_DEBUG, "attachPresenceListenerForQuiz: no studentId yet, skipping for quiz=" + key);
            return;
        }




        DatabaseReference qRef = FirebaseDatabase.getInstance()
                .getReference("QuizStudents").child(key).child(studentId);




        ValueEventListener qListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean allowed = Boolean.TRUE.equals(snapshot.child("allowed").getValue(Boolean.class));
                boolean present = Boolean.TRUE.equals(snapshot.child("present").getValue(Boolean.class));
                boolean exists = snapshot.exists();




                // store latest quiz node state (synchronized)
                synchronized (QuizListActivity.this) {
                    lastQuizNodeExists.put(key, exists);
                    lastQuizAllowed.put(key, allowed);
                    lastQuizPresent.put(key, present);
                }




                android.util.Log.d(TAG_DEBUG, "QuizStudents presence change: quiz=" + key + " student=" + studentId + " allowed=" + allowed + " present=" + present + " exists=" + exists);




                // compute effective presence: QuizStudents authoritative when it exists
                boolean effective;
                synchronized (QuizListActivity.this) {
                    if (Boolean.TRUE.equals(lastQuizNodeExists.get(key))) {
                        effective = Boolean.TRUE.equals(lastQuizAllowed.get(key)) || Boolean.TRUE.equals(lastQuizPresent.get(key));
                    } else {
                        effective = Boolean.TRUE.equals(lastExamAllowed.get(key)) || Boolean.TRUE.equals(lastExamPresent.get(key));
                    }
                }




                // update UI
                runOnUiThread(() -> {
                    if (adapter != null) adapter.setStudentPresent(key, effective);
                });




                // persist effective presence into local cache
                final boolean toPersist = effective;
                new Thread(() -> {
                    try {
                        com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(QuizListActivity.this);
                        com.finale.nextgen.offline.QuizEntity qe = db.quizDao().getById(key);
                        if (qe == null) {
                            qe = new com.finale.nextgen.offline.QuizEntity();
                            qe.quizId = key;
                        }
                        qe.present = toPersist;
                        qe.cachedAt = System.currentTimeMillis();
                        db.quizDao().insert(qe);
                        android.util.Log.d(TAG_DEBUG, "Persisted QuizStudents presence for quiz=" + key + " present=" + toPersist);
                    } catch (Exception e) {
                        android.util.Log.w(TAG_DEBUG, "Failed to persist presence for quiz=" + key + ": " + e.getMessage());
                    }
                }).start();
            }




            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.w(TAG_DEBUG, "QuizStudents listener cancelled for quiz=" + key + ": " + error.getMessage());
            }
        };




        ValueEventListener eListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean allowed = Boolean.TRUE.equals(snapshot.child("allowed").getValue(Boolean.class));
                boolean present = Boolean.TRUE.equals(snapshot.child("present").getValue(Boolean.class));
                boolean exists = snapshot.exists();




                // store latest exam node state (synchronized)
                synchronized (QuizListActivity.this) {
                    lastExamAllowed.put(key, allowed);
                    lastExamPresent.put(key, present);
                    // note: do NOT set lastQuizNodeExists here
                }




                android.util.Log.d(TAG_DEBUG, "ExamStudents presence change: quiz=" + key + " student=" + studentId + " allowed=" + allowed + " present=" + present + " exists=" + exists);




                // compute effective presence: QuizStudents authoritative when it exists
                boolean effective;
                synchronized (QuizListActivity.this) {
                    if (Boolean.TRUE.equals(lastQuizNodeExists.get(key))) {
                        effective = Boolean.TRUE.equals(lastQuizAllowed.get(key)) || Boolean.TRUE.equals(lastQuizPresent.get(key));
                    } else {
                        effective = Boolean.TRUE.equals(lastExamAllowed.get(key)) || Boolean.TRUE.equals(lastExamPresent.get(key));
                    }
                }




                // update UI
                runOnUiThread(() -> {
                    if (adapter != null) adapter.setStudentPresent(key, effective);
                });




                // persist effective presence into local cache
                final boolean toPersist = effective;
                new Thread(() -> {
                    try {
                        com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(QuizListActivity.this);
                        com.finale.nextgen.offline.QuizEntity qe = db.quizDao().getById(key);
                        if (qe == null) {
                            qe = new com.finale.nextgen.offline.QuizEntity();
                            qe.quizId = key;
                        }
                        qe.present = toPersist;
                        qe.cachedAt = System.currentTimeMillis();
                        db.quizDao().insert(qe);
                        android.util.Log.d(TAG_DEBUG, "Persisted ExamStudents presence for quiz=" + key + " present=" + toPersist);
                    } catch (Exception e) {
                        android.util.Log.w(TAG_DEBUG, "Failed to persist presence for quiz=" + key + ": " + e.getMessage());
                    }
                }).start();
            }




            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.w(TAG_DEBUG, "ExamStudents listener cancelled for quiz=" + key + ": " + error.getMessage());
            }
        };

        // Attach listeners (previously missing) so student presence updates arrive in real time
        quizPresenceRefs.put(key, qRef);
        quizPresenceListeners.put(key, qListener);
        qRef.addValueEventListener(qListener);

        DatabaseReference eRef = FirebaseDatabase.getInstance()
                .getReference("ExamStudents").child(key).child(studentId);
        examPresenceRefs.put(key, eRef);
        examPresenceListeners.put(key, eListener);
        eRef.addValueEventListener(eListener);
    }




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




    private final BroadcastReceiver quizSubmittedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String quizId = intent.getStringExtra("quizId");
            if (quizId != null && adapter != null) {
                Log.d(TAG_DEBUG, "quizSubmittedReceiver: marking quiz taken: " + quizId);
                adapter.setQuizTaken(quizId);
                Log.d("QUIZ_DEBUG", "About to setQuizTaken for " + quizId + " from " + Thread.currentThread().getName());
            }
            // Try to fetch authoritative score (will no-op if offline)
            tryFetchScoreForQuiz(quizId);
        }
    };
    // Paste into QuizListActivity (class body)

    // Try to fetch score for a quiz if network available; if not, leave for onResume to retry.
    private void tryFetchScoreForQuiz(@NonNull final String quizId) {
        if (!isNetworkAvailable()) {
            Log.d(TAG_DEBUG, "Offline - will fetch score for " + quizId + " when back online");
            return;
        }

        // Use scoresStudentId if present (set when we fetched scores earlier), otherwise session student id
        String studentId = (scoresStudentId != null && !scoresStudentId.isEmpty()) ? scoresStudentId : null;
        if (studentId == null || studentId.isEmpty()) {
            try {
                studentId = sessionManager != null ? sessionManager.getStudentId() : null;
            } catch (Exception ignored) {}
        }

        if (studentId != null && !studentId.isEmpty()) {
            fetchScoreFromFirebase(studentId, quizId);
        } else {
            // fallback: try to resolve studentId by current auth uid, then fetch
            tryResolveStudentIdAndFetchScore(quizId);
        }
    }

    private void tryResolveStudentIdAndFetchScore(@NonNull final String quizId) {
        String uid = null;
        try { if (FirebaseAuth.getInstance().getCurrentUser() != null) uid = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}
        if (uid == null || uid.isEmpty()) {
            Log.w(TAG_DEBUG, "Unable to resolve uid to fetch score for quiz=" + quizId);
            return;
        }
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        studentsRef.orderByChild("uid").equalTo(uid).limitToFirst(1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String foundStudentId = null;
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String sid = ds.child("studentId").getValue(String.class);
                                if (sid != null && !sid.isEmpty()) { foundStudentId = sid; break; }
                            }
                        }
                        if (foundStudentId != null) {
                            // cache for future
                            try { sessionManager.saveStudentId(foundStudentId); } catch (Exception ignored) {}
                            // also set scoresStudentId so other flows use it
                            scoresStudentId = foundStudentId;
                            fetchScoreFromFirebase(foundStudentId, quizId);
                        } else {
                            Log.w(TAG_DEBUG, "Could not find studentId for uid, cannot fetch score for " + quizId);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        Log.w(TAG_DEBUG, "student lookup cancelled: " + error.getMessage());
                    }
                });
    }

    // Replace your existing fetchScoreFromFirebase(...) onDataChange handling with this corrected snippet.

    private void fetchScoreFromFirebase(@NonNull final String studentId, @NonNull final String quizId) {
        DatabaseReference scoreRef = FirebaseDatabase.getInstance()
                .getReference("QuizScores")
                .child(studentId)
                .child(quizId);

        scoreRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // parse score and maxScore safely
                    Integer scoreInt = null;
                    try {
                        Object sc = snapshot.child("score").getValue();
                        if (sc instanceof Long) scoreInt = ((Long) sc).intValue();
                        else if (sc instanceof Integer) scoreInt = (Integer) sc;
                        else if (sc instanceof Double) scoreInt = ((Double) sc).intValue();
                    } catch (Exception ignored) {}

                    Integer maxScore = null;
                    try {
                        Object ms = snapshot.child("maxScore").getValue();
                        if (ms instanceof Long) maxScore = ((Long) ms).intValue();
                        else if (ms instanceof Integer) maxScore = (Integer) ms;
                    } catch (Exception ignored) {}

                    final Double scoreToShow = (scoreInt != null) ? scoreInt.doubleValue() : null;
                    final Integer maxScoreFinal = maxScore; // make a final copy for the lambda

                    // update UI on main thread — lambda now uses final locals
                    runOnUiThread(() -> {
                        if (adapter != null) {
                            adapter.setQuizScore(quizId, scoreToShow, maxScoreFinal);
                            adapter.setQuizTaken(quizId);
                        }
                    });

                    Log.d(TAG_DEBUG, "Fetched server score for quiz=" + quizId + " score=" + scoreInt);
                } else {
                    Log.d(TAG_DEBUG, "No score found on server yet for quiz=" + quizId);
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG_DEBUG, "Failed to read score for " + quizId + ": " + error.getMessage());
            }
        });
    }
    private void launchGalleryPicker() {
        // Runtime permission: for Android 13+ use READ_MEDIA_IMAGES; for older, READ_EXTERNAL_STORAGE
        String perm;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            perm = android.Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            perm = android.Manifest.permission.READ_EXTERNAL_STORAGE;
        }




        if (ContextCompat.checkSelfPermission(this, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{perm}, REQ_CODE_READ_STORAGE);
            return;
        }




        try {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQ_CODE_PICK_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open gallery: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }




    // Handle permission result for gallery access
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_READ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launchGalleryPicker();
            } else {
                Toast.makeText(this, "Permission required to pick images.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

