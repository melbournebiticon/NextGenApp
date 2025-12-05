package com.example.nextgen.student;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * QuizResultActivity - same behavior as the provided ResultActivity but uses the "quizTitle" extra.
 * - Displays student/profile info and score summary.
 * - Attempts to use profile image from intent first, then overrides with DB-stored profileImage if available.
 */
public class QuizResultActivity extends AppCompatActivity {

    private TextView tvCourseCode, tvSubjectName, tvTeacherName;
    private TextView tvStudentName, tvStudentId;
    private TextView tvScoreRaw, tvScorePercent, tvEquivalentGrade;
    private ImageView imgProfile;

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
    private void loadProfileFromFirebase(String studentId) {

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("Students");

        ref.orderByChild("studentId")
                .equalTo(studentId)
                .limitToFirst(1)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (snapshot.exists()) {

                        for (DataSnapshot studentSnap : snapshot.getChildren()) {

                            String dbImage = studentSnap.child("profileImage").getValue(String.class);

                            if (dbImage != null && !dbImage.trim().isEmpty()) {
                                setProfileImage(dbImage); // Override
                            }
                        }
                    }

                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error fetching profile: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
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

        // --- SET UI TEXT ---
        // If you want the course code area to show quizTitle instead, adjust accordingly.
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

        // Optionally show deductions in the percent or elsewhere (not part of original UI).
        // For compatibility we won't alter the existing textviews beyond what's shown above.

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
}