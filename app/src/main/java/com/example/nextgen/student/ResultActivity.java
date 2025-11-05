package com.example.nextgen.student;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.nextgen.R;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DatabaseReference;

public class ResultActivity extends AppCompatActivity {

    private TextView tvCourseCode, tvSubjectName, tvTeacherName;
    private TextView tvStudentName, tvStudentId;
    private TextView tvScoreRaw, tvScorePercent, tvEquivalentGrade;
    private ImageView imgProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        // Initialize views
        imgProfile = findViewById(R.id.ivProfile);
        tvCourseCode = findViewById(R.id.tvCourseCode);
        tvSubjectName = findViewById(R.id.tvSubjectName);
        tvTeacherName = findViewById(R.id.tvTeacherName);
        tvStudentName = findViewById(R.id.tvStudentName);
        tvStudentId = findViewById(R.id.tvStudentId);
        tvScoreRaw = findViewById(R.id.tvScoreRaw);
        tvScorePercent = findViewById(R.id.tvScorePercent);
        tvEquivalentGrade = findViewById(R.id.tvEquivalentGrade);

        // Get data from Intent
        String courseCode = getIntent().getStringExtra("courseCode");
        String subjectName = getIntent().getStringExtra("subjectName");
        String teacherName = getIntent().getStringExtra("teacherName");
        String studentName = getIntent().getStringExtra("studentName");
        String studentId = getIntent().getStringExtra("studentId");
        // String profileImage = getIntent().getStringExtra("profileImage"); // Hindi ginagamit

        // --- KEY FIX DITO ---
        int finalScore = getIntent().getIntExtra("score", 0); // 👈 TAMA NA: Ito ang pinasa ng TakeExamActivity
        int maxScore = getIntent().getIntExtra("maxScore", 0);
        // int deductions = getIntent().getIntExtra("deductions", 0); // Optional, kung may TextView ka para dito
        // --- END KEY FIX ---

        // Populate text fields
        tvCourseCode.setText(courseCode);
        tvSubjectName.setText(subjectName);
        tvTeacherName.setText(teacherName);
        tvStudentName.setText(studentName);
        tvStudentId.setText(studentId);

        // --- DISPLAY FIX DITO ---
        tvScoreRaw.setText(finalScore + "/" + maxScore); // 👈 Ginamit na ang "finalScore"

        double percent = maxScore > 0 ? (finalScore * 100.0) / maxScore : 0; // 👈 Ginamit na ang "finalScore"
        tvScorePercent.setText(String.format("%.2f%%", percent));
        // --- END DISPLAY FIX ---

        String grade = percent >= 75 ? "Passed" : "Failed";
        tvEquivalentGrade.setText(grade);

        // 🔹 Default profile first (in case Firebase fails)
        imgProfile.setImageResource(R.drawable.examinee_default);

        // 🔹 Try to load latest profile from Firebase (instead of old Intent data)
        if (studentId != null && !studentId.isEmpty()) {
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("Students")
                    .child(studentId);

            ref.get().addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    String profileFromDb = snapshot.child("profileImage").getValue(String.class);
                    if (profileFromDb != null && !profileFromDb.isEmpty()) {
                        try {
                            byte[] decodedBytes = Base64.decode(profileFromDb, Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                            imgProfile.setImageBitmap(bitmap);
                        } catch (Exception e) {
                            e.printStackTrace();
                            imgProfile.setImageResource(R.drawable.examinee_default);
                        }
                    }
                }
            }).addOnFailureListener(e -> {
                e.printStackTrace();
                imgProfile.setImageResource(R.drawable.examinee_default);
            });
        }
    }
}