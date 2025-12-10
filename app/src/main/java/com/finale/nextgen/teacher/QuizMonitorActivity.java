package com.finale.nextgen.teacher;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.finale.nextgen.admin.StudentModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

/**
 * QuizMonitorActivity - teacher view for monitoring students for a quiz.
 *
 * Changes in this update:
 * - setStudentPresent(...) now updates both QuizStudents and ExamStudents and also writes
 *   an explicit "allowed" flag that student clients can check to permit/deny taking the quiz.
 *   When teacher marks a student absent -> present=false and allowed=false (student should not be able to start).
 *   When teacher marks present -> present=true and allowed=true.
 *
 * - Filtering: this version filters the Students snapshot by class/course/year/section using intent extras:
 *   quizSpecialization, quizSectionName, quizYearName, quizCourseName.
 *
 * Note: student apps must check the "allowed" flag (QuizStudents/{quizId}/{studentId}/allowed)
 * in addition to "present" if you want to ensure they're prevented from starting a quiz.
 * This update only ensures the teacher side writes the required fields.
 */
public class QuizMonitorActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private StudentQuizAdapter adapter;
    private String quizTitle;
    private String quizId;

    // Filtering criteria obtained from intent
    private String quizSpecialization;
    private String quizSectionName;
    private String quizYearName;
    private String quizCourseName;

    // Firebase refs and cached snapshots
    private DatabaseReference studentsRef;
    private DatabaseReference quizStudentsRef;
    private DataSnapshot studentsSnapshotCache;
    private ValueEventListener quizStudentsRealtimeListener;

    // Keep last-known presence map so we can detect changes (studentId -> present)
    private final Map<String, Boolean> lastPresenceMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_monitor);

        recyclerView = findViewById(R.id.recyclerStudents);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Read id/title from intent (support both quiz and exam keys for compatibility)
        quizId = getIntent().getStringExtra("quizId");
        if (quizId == null || quizId.isEmpty()) {
            quizId = getIntent().getStringExtra("examId");
        }

        quizTitle = getIntent().getStringExtra("quizName");
        if (quizTitle == null || quizTitle.isEmpty()) {
            quizTitle = getIntent().getStringExtra("quizTitle");
        }
        if (quizTitle == null || quizTitle.isEmpty()) {
            quizTitle = getIntent().getStringExtra("examTitle");
        }
        if (quizTitle == null) quizTitle = "(Untitled)";

        // Read filtering criteria from intent. These should match the keys the caller sends.
        quizSpecialization = getIntent().getStringExtra("quizSpecialization");
        quizSectionName = getIntent().getStringExtra("quizSectionName");
        quizYearName = getIntent().getStringExtra("quizYearName");
        quizCourseName = getIntent().getStringExtra("quizCourseName");

        // Normalize nulls to empty strings for easier comparison
        if (quizSpecialization == null) quizSpecialization = "";
        if (quizSectionName == null) quizSectionName = "";
        if (quizYearName == null) quizYearName = "";
        if (quizCourseName == null) quizCourseName = "";

        setTitle("Monitoring: " + quizTitle);
        android.util.Log.d("QuizMonitor", "quizId from intent: " + quizId);
        android.util.Log.d("QuizMonitor", "Filtering by spec='" + quizSpecialization + "' section='" + quizSectionName + "' year='" + quizYearName + "' course='" + quizCourseName + "'");

        // Show QR button (reuses same button id in layout)
        View btnShowQr = findViewById(R.id.btnShowQR);
        if (btnShowQr != null) {
            btnShowQr.setOnClickListener(v -> showQrDialog(quizId));
        }

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        quizStudentsRef = FirebaseDatabase.getInstance().getReference("QuizStudents").child(quizId);

        // Load and cache Students snapshot and then attach realtime QuizStudents listener
        cacheStudentsAndAttachListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachQuizStudentsListener();
    }

    private void cacheStudentsAndAttachListener() {
        // Cache Students snapshot once
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                studentsSnapshotCache = snapshot;
                // Attach realtime listener to QuizStudents so UI updates immediately on any presence/ongoing changes
                attachQuizStudentsRealtimeListener();
                // Also immediately populate list using current quizStudents snapshot (single read)
                quizStudentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot quizSnap) {
                        // initialize lastPresenceMap from current snapshot
                        initializeLastPresenceMap(quizSnap);
                        buildStudentList(quizSnap, studentsSnapshotCache);
                    }
                    @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                        // fallback: try ExamStudents node if QuizStudents is not present
                        attachExamStudentsFallbackListener();
                    }
                });
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                Toast.makeText(QuizMonitorActivity.this, "Failed to load students: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void attachQuizStudentsRealtimeListener() {
        // detach previous if any
        detachQuizStudentsListener();

        quizStudentsRealtimeListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot quizSnap) {
                // detect presence changes compared to lastPresenceMap
                detectAndNotifyPresenceChanges(quizSnap);
                // Rebuild list using cached students snapshot
                buildStudentList(quizSnap, studentsSnapshotCache);
                // update lastPresenceMap so next change detection works
                updateLastPresenceMap(quizSnap);
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                android.util.Log.w("QuizMonitor", "QuizStudents realtime listener cancelled: " + error.getMessage());
            }
        };
        quizStudentsRef.addValueEventListener(quizStudentsRealtimeListener);
        android.util.Log.d("QuizMonitor", "Attached realtime listener to QuizStudents/" + quizId);
    }

    private void detachQuizStudentsListener() {
        try {
            if (quizStudentsRealtimeListener != null && quizStudentsRef != null) {
                quizStudentsRef.removeEventListener(quizStudentsRealtimeListener);
            }
        } catch (Exception ignored) {}
        quizStudentsRealtimeListener = null;
    }

    private void attachExamStudentsFallbackListener() {
        DatabaseReference fallbackRef = FirebaseDatabase.getInstance().getReference("ExamStudents").child(quizId);
        // detach any existing listeners
        detachQuizStudentsListener();
        quizStudentsRealtimeListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot examSnap) {
                detectAndNotifyPresenceChanges(examSnap);
                buildStudentList(examSnap, studentsSnapshotCache);
                updateLastPresenceMap(examSnap);
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                android.util.Log.w("QuizMonitor", "ExamStudents realtime listener cancelled: " + error.getMessage());
            }
        };
        fallbackRef.addValueEventListener(quizStudentsRealtimeListener);
        android.util.Log.d("QuizMonitor", "Attached realtime listener to ExamStudents/" + quizId);
    }

    /**
     * Build the list of StudentQuizStatus items by correlating Students snapshot with QuizStudents/ExamStudents snapshot.
     * Uses setters to create StudentQuizStatus instances so it works regardless of available constructors.
     *
     * This version filters Students by the class/course/year/section passed in the intent. Comparison is case-insensitive
     * and will include students only when all non-empty filter criteria match.
     *
     * IMPORTANT: if a StudentModel.studentId is missing, we use the DataSnapshot key locally but do NOT write it back to the DB.
     */
    private void buildStudentList(@NonNull DataSnapshot quizSnap, @Nullable DataSnapshot studentsSnap) {
        if (studentsSnap == null) {
            // no students available, show empty list
            runOnUiThread(() -> {
                adapter = new StudentQuizAdapter(new ArrayList<>(), quizId, actionListener);
                recyclerView.setAdapter(adapter);
            });
            return;
        }

        List<StudentQuizStatus> students = new ArrayList<>();
        for (DataSnapshot ds : studentsSnap.getChildren()) {
            StudentModel student = ds.getValue(StudentModel.class);
            if (student == null) continue;

            // Determine studentId locally (do NOT write back to DB if missing)
            String studentId = student.getStudentId();
            if (studentId == null || studentId.trim().isEmpty()) {
                studentId = ds.getKey();
            }

            // Normalize student properties for comparison
            String studentSpec = student.getSpecializationName() != null ? student.getSpecializationName().trim() : "";
            String studentSection = student.getSectionName() != null ? student.getSectionName().trim() : "";
            String studentYear = student.getYearName() != null ? student.getYearName().trim() : "";
            String studentCourse = student.getCourseName() != null ? student.getCourseName().trim() : "";

            // Apply filtering: if a filter is provided (non-empty), it must match the student's value (case-insensitive)
            boolean matches = true;
            if (!quizSpecialization.isEmpty() && !studentSpec.equalsIgnoreCase(quizSpecialization)) matches = false;
            if (!quizSectionName.isEmpty() && !studentSection.equalsIgnoreCase(quizSectionName)) matches = false;
            if (!quizYearName.isEmpty() && !studentYear.equalsIgnoreCase(quizYearName)) matches = false;
            if (!quizCourseName.isEmpty() && !studentCourse.equalsIgnoreCase(quizCourseName)) matches = false;

            if (!matches) continue;

            boolean present = false;
            boolean ongoing = false;
            int questionsAnswered = 0;

            if (quizSnap != null && quizSnap.hasChild(studentId)) {
                DataSnapshot studentQuizNode = quizSnap.child(studentId);
                present = Boolean.TRUE.equals(studentQuizNode.child("present").getValue(Boolean.class));
                ongoing = Boolean.TRUE.equals(studentQuizNode.child("ongoing").getValue(Boolean.class));
                Integer answered = studentQuizNode.child("questionsAnswered").getValue(Integer.class);
                if (answered != null) questionsAnswered = answered;
            }

            // Build via setters to avoid constructor mismatch
            StudentQuizStatus status = new StudentQuizStatus();
            status.setStudentId(studentId);
            status.setFullName(student.getFullName());
            status.setPresent(present);
            status.setOngoing(ongoing);
            status.setQuestionsAnswered(questionsAnswered);
            status.setCourse(studentCourse);
            status.setSpecialization(studentSpec);
            status.setYear(studentYear);
            status.setSection(studentSection);

            students.add(status);
        }

        runOnUiThread(() -> {
            adapter = new StudentQuizAdapter(students, quizId, actionListener);
            recyclerView.setAdapter(adapter);
        });
    }

    // ---------- Teacher actions (called by adapter via the provided listener) ----------
    private final StudentQuizAdapter.ActionListener actionListener = new StudentQuizAdapter.ActionListener() {
        @Override
        public void onTogglePresent(String studentId, boolean newValue) {
            setStudentPresent(studentId, newValue);
        }

        @Override
        public void onToggleOngoing(String studentId, boolean newValue) {
            setStudentOngoing(studentId, newValue);
        }

        @Override
        public void onTogglePresentClicked(String studentId, boolean newStatus) {
            setStudentPresent(studentId, newStatus);
        }

        @Override
        public void onResetStudentQuiz(String studentId) {
            showResetConfirmationAlert(studentId);
        }
    };

    /**
     * Mark/unmark a student's attendance for this quiz.
     * Writes to QuizStudents/{quizId}/{studentId}/present = true/false
     *
     * New behavior:
     * - Also writes the same present flag under ExamStudents path for compatibility.
     * - Also writes an explicit "allowed" boolean: when present==false -> allowed=false (student cannot start).
     *   When present==true -> allowed=true.
     */
    private void setStudentPresent(@NonNull String studentId, boolean present) {
        if (quizId == null || quizId.trim().isEmpty() || studentId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("present", present);
        updates.put("allowed", present); // allowed==present: if absent -> not allowed

        // QuizStudents path
        DatabaseReference quizRef = FirebaseDatabase.getInstance()
                .getReference("QuizStudents")
                .child(quizId)
                .child(studentId);

        // ExamStudents path (fallback)
        DatabaseReference examRef = FirebaseDatabase.getInstance()
                .getReference("ExamStudents")
                .child(quizId)
                .child(studentId);

        // Update both nodes
        quizRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(QuizMonitorActivity.this, (present ? "Student marked PRESENT: " : "Student marked ABSENT: ") + studentId, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(QuizMonitorActivity.this, "Failed to update presence for " + studentId, Toast.LENGTH_SHORT).show();
            }
        });

        examRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                android.util.Log.w("QuizMonitor", "Failed to update ExamStudents for " + studentId + ": " + task.getException());
            }
        });
    }

    /**
     * Mark/unmark ongoing state for a student.
     * Writes to QuizStudents/{quizId}/{studentId}/ongoing = true/false
     */
    private void setStudentOngoing(@NonNull String studentId, boolean ongoing) {
        if (quizId == null || quizId.trim().isEmpty() || studentId == null) return;
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("QuizStudents").child(quizId).child(studentId).child("ongoing");
        ref.setValue(ongoing).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(QuizMonitorActivity.this, (ongoing ? "Student marked ONGOING: " : "Student stopped ONGOING: ") + studentId, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(QuizMonitorActivity.this, "Failed to update ongoing for " + studentId, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Shows confirmation dialog before initiating the quiz reset.
     */
    private void showResetConfirmationAlert(String studentId) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Quiz Reset")
                .setMessage("Are you sure you want to reset the quiz data for Student ID: " + studentId + "? This will erase the score and answered questions count. This action cannot be undone.")
                .setPositiveButton("Reset", (dialog, which) -> performReset(studentId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Executes the actual Firebase reset operation.
     * Clears Scores and resets the status in QuizStudents node.
     */
    private void performReset(String studentId) {
        if (studentId == null || studentId.trim().isEmpty() || quizId == null || quizId.trim().isEmpty()) {
            Toast.makeText(this, "Invalid student or quiz id for reset.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1) References to all known score/answer nodes we want to clear
        DatabaseReference quizScoresRef = FirebaseDatabase.getInstance()
                .getReference("QuizScores")
                .child(studentId)
                .child(quizId);

        DatabaseReference legacyScoresRef = FirebaseDatabase.getInstance()
                .getReference("Scores")
                .child(studentId)
                .child(quizId);

        DatabaseReference usersAnswersRef = FirebaseDatabase.getInstance()
                .getReference("UsersAnswers")
                .child(studentId)
                .child(quizId);

        // Also clear any per-quiz-per-student score stored under QuizStudents/{quizId}/{studentId}/score
        DatabaseReference quizStudentScoreRef = FirebaseDatabase.getInstance()
                .getReference("QuizStudents")
                .child(quizId)
                .child(studentId)
                .child("score");

        // And clear submitted/finished flags under QuizStudents (we'll set them false via update later)
        DatabaseReference quizStudentNodeRef = FirebaseDatabase.getInstance()
                .getReference("QuizStudents")
                .child(quizId)
                .child(studentId);

        // Prepare status resets for the QuizStudents node (set to Absent/not allowed/not ongoing)
        Map<String, Object> updates = new HashMap<>();
        updates.put("present", false);
        updates.put("ongoing", false);
        updates.put("questionsAnswered", 0);
        updates.put("allowed", false);
        updates.put("submitted", false);
        updates.put("finished", false);

        // RemoveValue tasks
        Task<Void> t1 = quizScoresRef.removeValue();
        Task<Void> t2 = legacyScoresRef.removeValue();
        Task<Void> t3 = usersAnswersRef.removeValue();
        Task<Void> t4 = quizStudentScoreRef.removeValue();

        // Wait for all removals to complete
        Tasks.whenAll(t1, t2, t3, t4).addOnCompleteListener(allTask -> {
            if (allTask.isSuccessful()) {
                // Now update the QuizStudents node to clear flags and ensure student cannot start
                quizStudentNodeRef.updateChildren(updates).addOnCompleteListener(statusTask -> {
                    if (statusTask.isSuccessful()) {
                        Toast.makeText(QuizMonitorActivity.this, "Quiz reset and scores cleared for student: " + studentId, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(QuizMonitorActivity.this, "Scores cleared but failed to clear status for " + studentId, Toast.LENGTH_LONG).show();
                        android.util.Log.e("Reset", "Failed to clear status: " + statusTask.getException());
                    }
                });
            } else {
                // At least one removal failed — attempt best-effort status update and report error
                quizStudentNodeRef.updateChildren(updates).addOnCompleteListener(statusTask -> {
                    Toast.makeText(QuizMonitorActivity.this, "Reset encountered errors; some score records may remain. Status flags updated.", Toast.LENGTH_LONG).show();
                });
                android.util.Log.e("Reset", "One or more deletions failed when resetting student " + studentId + " for quiz " + quizId);
            }
        }).addOnFailureListener(e -> {
            // Tasks.whenAll failed to schedule / immediate failure
            android.util.Log.e("Reset", "Failed to remove score nodes: " + e);
            // Try best-effort status update
            quizStudentNodeRef.updateChildren(updates);
            Toast.makeText(QuizMonitorActivity.this, "Reset failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    // ---------- QR helper (updated) ----------
    @SuppressLint("InflateParams")
    private void showQrDialog(String id) {
        if (id == null || id.trim().isEmpty()) {
            Toast.makeText(this, "No quiz id available for QR", Toast.LENGTH_SHORT).show();
            return;
        }

        // Use a deep link payload so external scanner apps can open the app if intent-filter exists.
        final String deepLink = "nextgen://quiz/" + id;
        final String plainPayload = "quiz:" + id; // fallback/plain text

        QRCodeWriter writer = new QRCodeWriter();
        try {
            int size = 300;
            // Encode the deepLink into the QR so scanners that support app links / custom schemes can open the app.
            BitMatrix bitMatrix = writer.encode(deepLink, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            View dialogView = getLayoutInflater().inflate(R.layout.dialog_quiz_qr, null);
            ImageView qrImageView = dialogView.findViewById(R.id.qrImageView);
            TextView tvQuizTitle = dialogView.findViewById(R.id.tvQuizTitle);
            Button btnShare = dialogView.findViewById(R.id.btnShareQr);
            Button btnClose = dialogView.findViewById(R.id.btnClose);

            // Show quiz title (if available) in the dialog
            if (quizTitle != null && !quizTitle.trim().isEmpty()) {
                tvQuizTitle.setText(quizTitle);
                tvQuizTitle.setVisibility(View.VISIBLE);
            } else {
                tvQuizTitle.setVisibility(View.GONE);
            }

            qrImageView.setImageBitmap(bitmap);

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create();

            // Share button: share the deep link and a human-friendly text (plainPayload included).
            btnShare.setOnClickListener(v -> {
                try {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    String shareTitle = "Quiz: " + (quizTitle != null ? quizTitle : id);
                    String shareText = shareTitle + "\n\nOpen in app: " + deepLink + "\n\nOr use code: " + plainPayload;
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, shareTitle);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                    startActivity(Intent.createChooser(shareIntent, "Share quiz"));
                } catch (Exception ex) {
                    Toast.makeText(this, "Failed to share QR: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

            btnClose.setOnClickListener(v -> dialog.dismiss());
            dialog.show();

        } catch (WriterException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate QR", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Presence change helpers ----------
    private void initializeLastPresenceMap(@Nullable DataSnapshot quizSnap) {
        lastPresenceMap.clear();
        if (quizSnap == null) return;
        for (DataSnapshot child : quizSnap.getChildren()) {
            String sid = child.getKey();
            boolean present = Boolean.TRUE.equals(child.child("present").getValue(Boolean.class));
            lastPresenceMap.put(sid, present);
        }
    }

    private void updateLastPresenceMap(@Nullable DataSnapshot quizSnap) {
        lastPresenceMap.clear();
        if (quizSnap == null) return;
        for (DataSnapshot child : quizSnap.getChildren()) {
            String sid = child.getKey();
            boolean present = Boolean.TRUE.equals(child.child("present").getValue(Boolean.class));
            lastPresenceMap.put(sid, present);
        }
    }

    private void detectAndNotifyPresenceChanges(@Nullable DataSnapshot quizSnap) {
        if (quizSnap == null) return;

        Set<String> newlyPresent = new HashSet<>();
        for (DataSnapshot child : quizSnap.getChildren()) {
            String sid = child.getKey();
            boolean present = Boolean.TRUE.equals(child.child("present").getValue(Boolean.class));
            Boolean previous = lastPresenceMap.get(sid);
            if (!Boolean.TRUE.equals(previous) && present) {
                newlyPresent.add(sid);
            }
        }

        if (!newlyPresent.isEmpty()) {
            // Build a human-friendly message and show snackbar/toast, then highlight students in the adapter if possible
            StringBuilder names = new StringBuilder();
            int shown = 0;
            for (String sid : newlyPresent) {
                String display = sid;
                if (studentsSnapshotCache != null && studentsSnapshotCache.hasChild(sid)) {
                    StudentModel s = studentsSnapshotCache.child(sid).getValue(StudentModel.class);
                    if (s != null && s.getFullName() != null && !s.getFullName().isEmpty()) {
                        display = s.getFullName();
                    }
                }
                if (shown > 0) names.append(", ");
                names.append(display);
                shown++;
            }

            final String msg = "Marked present: " + names.toString();

            runOnUiThread(() -> {
                View root = findViewById(android.R.id.content);
                if (root != null) {
                    Snackbar.make(root, msg, Snackbar.LENGTH_LONG)
                            .setAction("Show", v -> {
                                // scroll to first newly present student in the list (if adapter present)
                                String firstId = newlyPresent.iterator().next();
                                if (adapter != null) {
                                    int idx = adapter.getPositionForStudentId(firstId);
                                    if (idx >= 0) recyclerView.scrollToPosition(idx);
                                    adapter.setHighlightStudentId(firstId);
                                }
                            })
                            .show();
                } else {
                    Toast.makeText(QuizMonitorActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}

