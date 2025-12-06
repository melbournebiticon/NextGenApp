package com.finale.nextgen.student;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.finale.nextgen.R;
import com.finale.nextgen.admin.StudentModel;
import com.google.android.material.appbar.MaterialToolbar; // Import MaterialToolbar
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class StudentProfileActivity extends AppCompatActivity {

    private static final String TAG = "StudentProfileActivity";

    private ImageView ivProfileImage;
    private TextView tvProfileFullName, tvProfileBirthday, tvProfileEmail, tvProfileContact,
            tvProfileCourse, tvProfileSpecialization, tvProfileYear, tvProfileSection;

    private FirebaseAuth auth;
    private DatabaseReference studentsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_profile);

        // --- NEW: Setup MaterialToolbar instead of standard ActionBar ---
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        // Set the toolbar as the ActionBar (optional, but good practice)
        setSupportActionBar(topAppBar);
        // Enable the back button and set the listener
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            // Title is already set in XML, but we can set it here too if needed
            // getSupportActionBar().setTitle("Student Profile");
        }
        // Handle the navigation icon click (the back button)
        topAppBar.setNavigationOnClickListener(v -> finish());
        // --- END NEW ---


        // Initialize UI Components (IDs remain the same, which is correct)
        ivProfileImage = findViewById(R.id.ivProfileImage);
        tvProfileFullName = findViewById(R.id.tvProfileFullName);
        tvProfileBirthday = findViewById(R.id.tvProfileBirthday);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        tvProfileContact = findViewById(R.id.tvProfileContact);
        tvProfileCourse = findViewById(R.id.tvProfileCourse);
        tvProfileSpecialization = findViewById(R.id.tvProfileSpecialization);
        tvProfileYear = findViewById(R.id.tvProfileYear);
        tvProfileSection = findViewById(R.id.tvProfileSection);


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
    }

    /*
     * INALIS: onSupportNavigateUp()
     * Ang pag-handle ng back button (Navigation Icon) ay inilipat na sa
     * topAppBar.setNavigationOnClickListener(v -> finish());
     */

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

    // showChangePasswordDialog() at hashPassword() methods remain unchanged as they are functional
    private void showChangePasswordDialog() {
        // Inflate the dialog layout
        View view = getLayoutInflater().inflate(R.layout.dialog_change_password, null);

        EditText etOldPassword = view.findViewById(R.id.etOldPassword);
        EditText etNewPassword = view.findViewById(R.id.etNewPassword);
        EditText etConfirmPassword = view.findViewById(R.id.etConfirmPassword);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Password")
                .setView(view)
                .setPositiveButton("Change", null)
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String oldPass = etOldPassword.getText().toString().trim();
                String newPass = etNewPassword.getText().toString().trim();
                String confirmPass = etConfirmPassword.getText().toString().trim();

                if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!newPass.equals(confirmPass)) {
                    Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }

                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null && user.getEmail() != null) {
                    // Re-authenticate user
                    auth.signInWithEmailAndPassword(user.getEmail(), oldPass)
                            .addOnCompleteListener(authTask -> {
                                if (authTask.isSuccessful()) {
                                    // Update password in Firebase Auth
                                    user.updatePassword(newPass)
                                            .addOnCompleteListener(updateTask -> {
                                                if (updateTask.isSuccessful()) {
                                                    // Update password in Realtime Database (hashed)
                                                    studentsRef.orderByChild("uid").equalTo(user.getUid())
                                                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                                                @Override
                                                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                                    if (snapshot.exists()) {
                                                                        for (DataSnapshot ds : snapshot.getChildren()) {
                                                                            ds.getRef().child("password").setValue(hashPassword(newPass));
                                                                            break; // only one record per UID
                                                                        }
                                                                    }
                                                                }

                                                                @Override
                                                                public void onCancelled(@NonNull DatabaseError error) {
                                                                    Log.e(TAG, "DB Error: " + error.getMessage());
                                                                }
                                                            });

                                                    Toast.makeText(StudentProfileActivity.this,
                                                            "Password changed successfully!", Toast.LENGTH_SHORT).show();
                                                    dialog.dismiss();
                                                }
                                                else {
                                                    Toast.makeText(StudentProfileActivity.this,
                                                            "Error: " + updateTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                } else {
                                    Toast.makeText(StudentProfileActivity.this, "Old password is incorrect", Toast.LENGTH_SHORT).show();
                                }
                            });
                }
            });
        });

        dialog.show();
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return password; // fallback: plain text (not ideal)
        }
    }
}