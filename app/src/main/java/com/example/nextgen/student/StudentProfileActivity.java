package com.example.nextgen.student;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Button; // Kept as it might be needed for other buttons, but usually removed if no buttons are left
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.nextgen.R;
import com.example.nextgen.admin.StudentModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class StudentProfileActivity extends AppCompatActivity {

    private static final String TAG = "StudentProfileActivity";

    private ImageView ivProfileImage;
    private TextView tvProfileFullName, tvProfileBirthday, tvProfileEmail, tvProfileContact,
            tvProfileCourse, tvProfileSpecialization, tvProfileYear, tvProfileSection;
    // Inalis ang private Button btnEditProfile;

    private FirebaseAuth auth;
    private DatabaseReference studentsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_profile);

        // Enable the back button in the ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("My Profile");
        }

        // Initialize UI Components
        ivProfileImage = findViewById(R.id.ivProfileImage);
        tvProfileFullName = findViewById(R.id.tvProfileFullName);
        tvProfileBirthday = findViewById(R.id.tvProfileBirthday);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        tvProfileContact = findViewById(R.id.tvProfileContact);
        tvProfileCourse = findViewById(R.id.tvProfileCourse);
        tvProfileSpecialization = findViewById(R.id.tvProfileSpecialization);
        tvProfileYear = findViewById(R.id.tvProfileYear);
        tvProfileSection = findViewById(R.id.tvProfileSection);
        // Inalis ang btnEditProfile = findViewById(R.id.btnEditProfile);

        // Setup Firebase
        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        // Fetch and display student data
        fetchStudentData(currentUser.getUid());

        // Inalis ang Handle Edit Profile button click listener
    }

    // Handle the back button in the action bar
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void fetchStudentData(String uid) {
        studentsRef.orderByChild("uid").equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                StudentModel student = ds.getValue(StudentModel.class);
                                if (student != null) {
                                    populateUI(student);
                                    return;
                                }
                            }
                        }
                        Toast.makeText(StudentProfileActivity.this, "Student record not found", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Database Error: " + error.getMessage());
                        Toast.makeText(StudentProfileActivity.this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void populateUI(StudentModel student) {
        tvProfileFullName.setText(student.getFullName());
        tvProfileBirthday.setText(student.getBirthday());
        tvProfileEmail.setText(student.getEmail());
        tvProfileContact.setText(student.getContact());
        tvProfileCourse.setText(student.getCourseName());
        tvProfileSpecialization.setText(student.getSpecializationName());
        tvProfileYear.setText(student.getYearName());
        tvProfileSection.setText(student.getSectionName());

        // Load image
        if (student.getProfileImage() != null && !student.getProfileImage().isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(student.getProfileImage(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                ivProfileImage.setImageBitmap(bitmap);
            } catch (Exception e) {
                Log.e(TAG, "Error decoding image: " + e.getMessage());
                ivProfileImage.setImageResource(R.drawable.examinee_default);
            }
        } else {
            ivProfileImage.setImageResource(R.drawable.examinee_default);
        }
    }
}