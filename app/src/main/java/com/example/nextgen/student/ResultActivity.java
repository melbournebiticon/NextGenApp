package com.example.nextgen.student;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DatabaseReference;

public class ResultActivity extends AppCompatActivity {

    private static final String TAG = "ResultActivity";

    private TextView tvCourseCode, tvSubjectName, tvTeacherName;
    private TextView tvStudentName, tvStudentId;
    private TextView tvScoreRaw, tvScorePercent, tvEquivalentGrade;
    private ImageView imgProfile;

    // --- Helper: Decode Base64 and set profile image ---
    private void setProfileImage(String base64Image) {
        try {
            if (imgProfile == null) return;
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
            Log.w(TAG, "setProfileImage failed: " + e.getMessage(), e);
            if (imgProfile != null) imgProfile.setImageResource(R.drawable.examinee_default);
        }
    }

    /**
     * Try to load student profile image by:
     * 1) Querying Students where studentId == provided id
     * 2) If no result, attempt direct child lookup Students/{studentId} (in case studentId is the node key)
     *
     * Logs errors and shows a toast on failure with the exception text (useful during debugging).
     */
    private void loadProfileFromFirebase(final String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            Log.w(TAG, "loadProfileFromFirebase called with null/empty studentId");
            return;
        }

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students");

        Log.d(TAG, "Attempting query Students where studentId=" + studentId);
        ref.orderByChild("studentId")
                .equalTo(studentId)
                .limitToFirst(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        Log.w(TAG, "Query by child 'studentId' failed: " + (ex != null ? ex.getMessage() : "unknown"));
                        // Try fallback direct child lookup
                        tryDirectChildLookup(ref, studentId, ex);
                        return;
                    }

                    DataSnapshot snapshot = task.getResult();
                    if (snapshot != null && snapshot.exists()) {
                        Log.d(TAG, "Query matched student node, reading profileImage");
                        for (DataSnapshot studentSnap : snapshot.getChildren()) {
                            try {
                                String dbImage = studentSnap.child("profileImage").getValue(String.class);
                                if (dbImage != null && !dbImage.trim().isEmpty()) {
                                    setProfileImage(dbImage); // Override
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to read profileImage from snapshot: " + e.getMessage(), e);
                            }
                            break;
                        }
                    } else {
                        Log.d(TAG, "Query returned no results for studentId. Trying direct child lookup Students/" + studentId);
                        tryDirectChildLookup(ref, studentId, null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Query by child 'studentId' failed with exception: " + e.getMessage(), e);
                    tryDirectChildLookup(ref, studentId, e);
                });
    }

    private void tryDirectChildLookup(DatabaseReference ref, String studentId, Exception prior) {
        // Try reading Students/{studentId} in case the DB keys are the student IDs
        ref.child(studentId).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        Log.e(TAG, "Direct child lookup Students/" + studentId + " failed: " + (ex != null ? ex.getMessage() : "unknown"), ex);
                        String msg = "Error fetching profile: " + (ex != null ? ex.getMessage() : (prior != null ? prior.getMessage() : "unknown"));
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        return;
                    }

                    DataSnapshot snap = task.getResult();
                    if (snap != null && snap.exists()) {
                        try {
                            String dbImage = snap.child("profileImage").getValue(String.class);
                            if (dbImage != null && !dbImage.trim().isEmpty()) {
                                setProfileImage(dbImage);
                            } else {
                                Log.d(TAG, "No profileImage field in Students/" + studentId);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to read profileImage from direct child: " + e.getMessage(), e);
                        }
                    } else {
                        Log.d(TAG, "No student node at Students/" + studentId);
                        // Not an error necessarily — there may simply be no image — so no toast here
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Direct child lookup failed: " + e.getMessage(), e);
                    Toast.makeText(this, "Error fetching profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

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

        // --- SET UI TEXT ---
        tvCourseCode.setText(courseCode != null ? courseCode : "N/A");
        tvSubjectName.setText(subjectName != null ? subjectName : "N/A");
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
        if (imgProfile != null) imgProfile.setImageResource(R.drawable.examinee_default);

        // 2. Apply Intent image only if valid
        if (profileImageFromIntent != null && !profileImageFromIntent.trim().isEmpty()) {
            setProfileImage(profileImageFromIntent);
        }

        // 3. Firebase overrides if valid
        if (studentId != null && !studentId.isEmpty()) {
            loadProfileFromFirebase(studentId);
        } else {
            Log.w(TAG, "StudentId intent extra null or empty; will not attempt Firebase profile lookup");
        }
    }
}