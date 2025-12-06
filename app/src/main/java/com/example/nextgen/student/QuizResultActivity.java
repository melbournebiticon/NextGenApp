package com.example.nextgen.student;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * QuizResultActivity - same behavior as the provided ResultActivity but uses the "quizTitle" extra.
 * - Displays student/profile info and score summary.
 * - Attempts to use profile image from intent first, then overrides with DB-stored profileImage if available.
 * - When Back or Up is pressed it notifies QuizListActivity via local broadcast and brings the list to front
 *   to ensure the quiz is marked taken and student cannot retake it.
 */
public class QuizResultActivity extends AppCompatActivity {

    private TextView tvCourseCode, tvSubjectName, tvTeacherName;
    private TextView tvStudentName, tvStudentId;
    private TextView tvScoreRaw, tvScorePercent, tvEquivalentGrade;
    private ImageView imgProfile;

    // Quiz id passed from TakeQuizActivity (used to notify list)
    private String quizId;

    // --- Helper: Decode Base64 and set profile image ---
    private void setProfileImage(String base64Image) {
        try {
            if (base64Image == null || base64Image.trim().isEmpty()) {
                imgProfile.setImageResource(R.drawable.examinee_default);
                return;
            }

            // Remove prefix if present
            String pureBase64 = base64Image.replaceAll("data:image/.*?;base64,", "");

            byte[] decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            if (bitmap != null) {
                imgProfile.setImageBitmap(bitmap);
            } else {
                imgProfile.setImageResource(R.drawable.examinee_default);
            }

        } catch (Exception e) {
            e.printStackTrace();
            imgProfile.setImageResource(R.drawable.examinee_default);
        }
    }

    // --- Load from Firebase and override image if valid ---
    // Replace the existing loadProfileFromFirebase(...) in QuizResultActivity with this implementation

    private void loadProfileFromFirebase(final String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            // Nothing to do
            android.util.Log.w("QuizResultActivity", "loadProfileFromFirebase: studentId is null/empty");
            return;
        }

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students");

        android.util.Log.d("QuizResultActivity", "Attempting to load profile for studentId=" + studentId);

        // Try query by child 'studentId' first (some projects store studentId as a child field)
        ref.orderByChild("studentId")
                .equalTo(studentId)
                .limitToFirst(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        android.util.Log.w("QuizResultActivity", "Query by child 'studentId' failed: " + (ex != null ? ex.getMessage() : "unknown"));
                        // Fallback to direct child lookup
                        tryDirectChildLookup(ref, studentId, ex);
                        return;
                    }

                    DataSnapshot snapshot = task.getResult();
                    if (snapshot != null && snapshot.exists()) {
                        // Use the first matched node
                        for (DataSnapshot studentSnap : snapshot.getChildren()) {
                            try {
                                String dbImage = studentSnap.child("profileImage").getValue(String.class);
                                if (dbImage != null && !dbImage.trim().isEmpty()) {
                                    setProfileImage(dbImage);
                                    android.util.Log.d("QuizResultActivity", "Loaded profileImage from Students query for " + studentId);
                                } else {
                                    android.util.Log.d("QuizResultActivity", "No profileImage field in matched Students node for " + studentId);
                                }
                            } catch (Exception e) {
                                android.util.Log.w("QuizResultActivity", "Error reading profileImage from snapshot: " + e.getMessage(), e);
                            }
                            break;
                        }
                    } else {
                        // No match found — try direct child lookup in case Students/{studentId} is the key
                        android.util.Log.d("QuizResultActivity", "Query returned no results; trying direct child Students/" + studentId);
                        tryDirectChildLookup(ref, studentId, null);
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("QuizResultActivity", "Query by child 'studentId' failed with exception: " + e.getMessage(), e);
                    tryDirectChildLookup(ref, studentId, e);
                });
    }

    private void tryDirectChildLookup(DatabaseReference ref, String studentId, Exception prior) {
        ref.child(studentId).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        android.util.Log.e("QuizResultActivity", "Direct child lookup Students/" + studentId + " failed: " + (ex != null ? ex.getMessage() : "unknown"), ex);
                        // show a toast with useful info for debugging (optional)
                        String msg = "Error fetching profile: " + (ex != null ? ex.getMessage() : (prior != null ? prior.getMessage() : "unknown"));
                        Toast.makeText(QuizResultActivity.this, msg, Toast.LENGTH_LONG).show();
                        return;
                    }

                    DataSnapshot snap = task.getResult();
                    if (snap != null && snap.exists()) {
                        try {
                            String dbImage = snap.child("profileImage").getValue(String.class);
                            if (dbImage != null && !dbImage.trim().isEmpty()) {
                                setProfileImage(dbImage);
                                android.util.Log.d("QuizResultActivity", "Loaded profileImage from Students/" + studentId);
                            } else {
                                android.util.Log.d("QuizResultActivity", "No profileImage at Students/" + studentId);
                            }
                        } catch (Exception e) {
                            android.util.Log.w("QuizResultActivity", "Failed to read profileImage from direct child: " + e.getMessage(), e);
                        }
                    } else {
                        android.util.Log.d("QuizResultActivity", "No student node at Students/" + studentId);
                        // Not necessarily an error — student may not exist in DB — don't toast here unless you want to.
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("QuizResultActivity", "Direct child lookup failed: " + e.getMessage(), e);
                    Toast.makeText(QuizResultActivity.this, "Error fetching profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quizresult);

        imgProfile = findViewById(R.id.ivProfile);
        tvCourseCode = findViewById(R.id.tvCourseCode);
        tvSubjectName = findViewById(R.id.tvSubjectName);
        tvTeacherName = findViewById(R.id.tvTeacherName);
        tvStudentName = findViewById(R.id.tvStudentName);
        tvStudentId = findViewById(R.id.tvStudentId);
        tvScoreRaw = findViewById(R.id.tvScoreRaw);
        tvScorePercent = findViewById(R.id.tvScorePercent);
        tvEquivalentGrade = findViewById(R.id.tvEquivalentGrade);

        // --- Retrieve Intent Data ---
        String courseCode = getIntent().getStringExtra("courseCode");
        String subjectName = getIntent().getStringExtra("subjectName");
        String teacherName = getIntent().getStringExtra("teacherName");
        String studentName = getIntent().getStringExtra("studentName");
        final String studentId = getIntent().getStringExtra("studentId");

        int finalScore = getIntent().getIntExtra("totalScore", 0);
        int maxScore = getIntent().getIntExtra("maxScore", 0);

        String profileImageFromIntent = getIntent().getStringExtra("profileImage");

        // Optional: deductions (not displayed by default in original layout)
        int deductions = getIntent().getIntExtra("deductions", 0);

        // Quiz title (was examTitle in older flow)
        String quizTitle = getIntent().getStringExtra("quizTitle");

        // Read quizId extra (so we can notify list on back)
        quizId = getIntent().getStringExtra("quizId");

        // --- SET UI TEXT ---
        tvCourseCode.setText(courseCode != null ? courseCode : "N/A");
        tvSubjectName.setText(subjectName != null ? subjectName : (quizTitle != null ? quizTitle : "N/A"));
        tvTeacherName.setText(teacherName != null ? teacherName : "N/A");
        tvStudentName.setText(studentName != null ? studentName : "N/A");
        tvStudentId.setText(studentId != null ? studentId : "N/A");

        // --- Score Calculation ---
        tvScoreRaw.setText(finalScore + "/" + maxScore);

        double percent = maxScore > 0 ? (finalScore * 100.0) / maxScore : 0;
        tvScorePercent.setText(String.format("%.2f%%", percent));

        tvEquivalentGrade.setText(percent >= 75 ? "Passed" : "Failed");

        // ==============================
        //   PROFILE IMAGE FIX (FINAL)
        // ==============================

        // 1. Always apply DEFAULT image first
        imgProfile.setImageResource(R.drawable.examinee_default);

        // 2. Apply Intent image only if valid
        if (profileImageFromIntent != null && !profileImageFromIntent.trim().isEmpty()) {
            setProfileImage(profileImageFromIntent);
        }

        // 3. Firebase overrides if valid
        if (studentId != null && !studentId.isEmpty()) {
            loadProfileFromFirebase(studentId);
        }

        // Optional: show a toast with quiz title
        if (quizTitle != null && !quizTitle.isEmpty()) {
            Toast.makeText(this, "Quiz: " + quizTitle, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * When user presses Android Back button, notify the list (so it marks quiz taken) and return to list.
     */
    @Override
    public void onBackPressed() {
        notifyListQuizTakenAndFinish();
    }

    /**
     * Handle Up (toolbar) navigation same as Back
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            notifyListQuizTakenAndFinish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Sends local broadcast so QuizListActivity updates and brings the list to the front.
     * This helps ensure the student cannot retake the just-submitted quiz.
     */
    private void notifyListQuizTakenAndFinish() {
        try {
            if (quizId != null && !quizId.isEmpty()) {
                Intent b = new Intent("com.example.nextgen.QUIZ_SUBMITTED");
                b.putExtra("quizId", quizId);
                LocalBroadcastManager.getInstance(this).sendBroadcast(b);
            }
        } catch (Exception ignored) {}

        // Bring QuizListActivity to front (reuse existing instance if present)
        try {
            Intent i = new Intent(this, QuizListActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception ignored) {}

        finish();
    }
}