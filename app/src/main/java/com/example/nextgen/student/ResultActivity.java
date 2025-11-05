package com.example.nextgen.student;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast; // Added for error messages
import androidx.appcompat.app.AppCompatActivity;
import com.example.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DatabaseReference;

public class ResultActivity extends AppCompatActivity {

    private TextView tvCourseCode, tvSubjectName, tvTeacherName;
    private TextView tvStudentName, tvStudentId;
    private TextView tvScoreRaw, tvScorePercent, tvEquivalentGrade;
    private ImageView imgProfile;

    // Helper method to decode and set Base64 image
    private void setProfileImage(String base64Image) {
        if (base64Image != null && !base64Image.isEmpty()) {
            try {
                // Remove potential prefixes like "data:image/jpeg;base64," if present
                String pureBase64 = base64Image.replaceAll("data:image/.*?;base64,", "");

                byte[] decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                if (bitmap != null) {
                    imgProfile.setImageBitmap(bitmap);
                    return; // Successfully loaded
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Fall through to set default image on failure
            }
        }
        // Set default image if input is null/empty or decoding failed
        imgProfile.setImageResource(R.drawable.examinee_default);
    }

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
        final String studentId = getIntent().getStringExtra("studentId"); // Final to use in listener

        // FIX: Palitan ang "score" ng "totalScore" para maging consistent sa TakeExamActivity
        int finalScore = getIntent().getIntExtra("totalScore", 0);
        int maxScore = getIntent().getIntExtra("maxScore", 0);
        String profileImageFromIntent = getIntent().getStringExtra("profileImage"); // Use for fallback

        // Populate text fields
        tvCourseCode.setText(courseCode != null ? courseCode : "N/A");
        tvSubjectName.setText(subjectName != null ? subjectName : "N/A");
        tvTeacherName.setText(teacherName != null ? teacherName : "N/A");
        tvStudentName.setText(studentName != null ? studentName : "N/A");
        tvStudentId.setText(studentId != null ? studentId : "N/A");

        // Calculate and Display Score
        tvScoreRaw.setText(finalScore + "/" + maxScore);

        double percent = maxScore > 0 ? (finalScore * 100.0) / maxScore : 0;
        tvScorePercent.setText(String.format("%.2f%%", percent));

        // Determine Grade
        String grade = percent >= 75 ? "Passed" : "Failed";
        tvEquivalentGrade.setText(grade);

        // --- Profile Image Loading Logic ---

        // 1. Try to load profile image from the Intent data first (fastest)
        setProfileImage(profileImageFromIntent);

        // 2. Load the latest profile from Firebase (This overrides the Intent data if successful)
        if (studentId != null && !studentId.isEmpty()) {
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("Students")
                    .orderByChild("studentId").equalTo(studentId) // Search by studentId
                    .limitToFirst(1).getRef();

            ref.get().addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    // We need to iterate the result since orderByChild returns a list
                    for (DataSnapshot studentSnap : snapshot.getChildren()) {
                        String profileFromDb = studentSnap.child("profileImage").getValue(String.class);
                        if (profileFromDb != null && !profileFromDb.isEmpty()) {
                            setProfileImage(profileFromDb); // Set the DB profile image
                            return; // Stop after finding and setting the image
                        }
                    }
                }
                // If not found in DB or empty, the default image or Intent image remains set.
            }).addOnFailureListener(e -> {
                // Log and show error if DB fetch fails, but keep the current image (Intent/Default)
                Toast.makeText(this, "Error fetching latest profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        } else {
            imgProfile.setImageResource(R.drawable.examinee_default);
        }
    }
}

