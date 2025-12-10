package com.finale.nextgen.student;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.finale.nextgen.R;
import com.finale.nextgen.admin.StudentModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * StudentProfileActivity
 *
 * Updated lookup behavior:
 * - Prefer Students nodes keyed by studentId (e.g. "STD-0003") where that node contains a child "uid" equal to the
 *   current FirebaseAuth UID. This is done by querying orderByChild("uid").equalTo(uid) first.
 * - If no match found, fallback to Students/{uid} (legacy schema where node key == uid).
 * - If still not found, fallback to query by email.
 *
 * The resolved node key is stored in currentNodeKey and used for any DB writes (password update, etc).
 * The UI displays the model's studentId when available, otherwise falls back to the node key when that appears to be a studentId.
 */
public class StudentProfileActivity extends AppCompatActivity {

    private static final String TAG = "StudentProfileActivity";

    private ImageView ivProfileImage;
    private TextView tvProfileFullName, tvProfileBirthday, tvProfileEmail, tvProfileContact,
            tvProfileCourse, tvProfileSpecialization, tvProfileYear, tvProfileSection, tvStudentId;

    private FirebaseAuth auth;
    private DatabaseReference studentsRef;

    // resolved Students node key for the current user (could be "STD-0003" or the UID)
    private String currentNodeKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_profile);

        // toolbar
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        topAppBar.setNavigationOnClickListener(v -> finish());

        // UI refs
        ivProfileImage = findViewById(R.id.ivProfileImage);
        tvProfileFullName = findViewById(R.id.tvProfileFullName);
        tvProfileBirthday = findViewById(R.id.tvProfileBirthday);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        tvProfileContact = findViewById(R.id.tvProfileContact);
        tvProfileCourse = findViewById(R.id.tvProfileCourse);
        tvProfileSpecialization = findViewById(R.id.tvProfileSpecialization);
        tvProfileYear = findViewById(R.id.tvProfileYear);
        tvProfileSection = findViewById(R.id.tvProfileSection);
        // your XML uses tvStudentId for display
        tvStudentId = findViewById(R.id.tvStudentId);

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        // Fetch data preferring studentId-keyed nodes that contain uid==currentUser.getUid()
        fetchStudentDataPreferStudentId(currentUser);
    }

    /**
     * Lookup strategy:
     * 1) Query Students where child 'uid' == currentUser.uid (this will find nodes keyed by studentId like "STD-0003")
     * 2) If not found, check Students/{uid} (legacy where node key is auth uid)
     * 3) If still not found, try Students where child 'email' == currentUser.email
     */
    private void fetchStudentDataPreferStudentId(FirebaseUser currentUser) {
        final String uid = currentUser.getUid();
        final String email = currentUser.getEmail();

        // 1) Find node where child "uid" == uid (this will return node key like "STD-0003" if your DB uses studentId as key)
        studentsRef.orderByChild("uid").equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot != null && snapshot.exists()) {
                            // pick first match (should be unique)
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                currentNodeKey = ds.getKey(); // likely "STD-0003"
                                StudentModel student = ds.getValue(StudentModel.class);
                                if (student != null) {
                                    populateUI(student);
                                    return;
                                }
                            }
                        }
                        // 2) fallback: Students/{uid}
                        studentsRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override public void onDataChange(@NonNull DataSnapshot snap2) {
                                if (snap2 != null && snap2.exists()) {
                                    currentNodeKey = snap2.getKey(); // likely uid
                                    StudentModel student = snap2.getValue(StudentModel.class);
                                    if (student != null) {
                                        populateUI(student);
                                        return;
                                    }
                                }
                                // 3) fallback: query by email if available
                                if (email != null && !email.trim().isEmpty()) {
                                    studentsRef.orderByChild("email").equalTo(email)
                                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                                @Override public void onDataChange(@NonNull DataSnapshot snap3) {
                                                    if (snap3 != null && snap3.exists()) {
                                                        for (DataSnapshot ds3 : snap3.getChildren()) {
                                                            currentNodeKey = ds3.getKey();
                                                            StudentModel student = ds3.getValue(StudentModel.class);
                                                            if (student != null) {
                                                                populateUI(student);
                                                                return;
                                                            }
                                                        }
                                                    }
                                                    // nothing found
                                                    Toast.makeText(StudentProfileActivity.this, "Student record not found", Toast.LENGTH_SHORT).show();
                                                    Log.w(TAG, "No Students node matched uid/email");
                                                }
                                                @Override public void onCancelled(@NonNull DatabaseError error) {
                                                    Log.e(TAG, "Email lookup cancelled: " + error.getMessage());
                                                    Toast.makeText(StudentProfileActivity.this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                } else {
                                    Toast.makeText(StudentProfileActivity.this, "Student record not found", Toast.LENGTH_SHORT).show();
                                    Log.w(TAG, "No Students record for uid and no email to try");
                                }
                            }
                            @Override public void onCancelled(@NonNull DatabaseError error) {
                                Log.w(TAG, "Direct child lookup cancelled: " + error.getMessage());
                                Toast.makeText(StudentProfileActivity.this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "UID-query cancelled: " + error.getMessage());
                        // fallback to Students/{uid}
                        studentsRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override public void onDataChange(@NonNull DataSnapshot snap2) {
                                if (snap2 != null && snap2.exists()) {
                                    currentNodeKey = snap2.getKey();
                                    StudentModel student = snap2.getValue(StudentModel.class);
                                    if (student != null) populateUI(student);
                                } else {
                                    Toast.makeText(StudentProfileActivity.this, "Student record not found", Toast.LENGTH_SHORT).show();
                                }
                            }
                            @Override public void onCancelled(@NonNull DatabaseError error2) {
                                Log.e(TAG, "Fallback lookup cancelled: " + error2.getMessage());
                                Toast.makeText(StudentProfileActivity.this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }

    private void populateUI(@NonNull StudentModel student) {
        // Defensive null checks
        tvProfileFullName.setText(student.getFullName() != null ? student.getFullName() : "");
        tvProfileBirthday.setText(student.getBirthday() != null ? student.getBirthday() : "");
        tvProfileEmail.setText(student.getEmail() != null ? student.getEmail() : "");
        tvProfileContact.setText(student.getContact() != null ? student.getContact() : "");
        tvProfileCourse.setText(student.getCourseName() != null ? student.getCourseName() : "");
        tvProfileSpecialization.setText(student.getSpecializationName() != null ? student.getSpecializationName() : "");
        tvProfileYear.setText(student.getYearName() != null ? student.getYearName() : "");
        tvProfileSection.setText(student.getSectionName() != null ? student.getSectionName() : "");

        // Display studentId (schoolId). Prefer the model's studentId; if missing, try to use currentNodeKey when it looks like STD-xxxx
        String sid = student.getStudentId();
        if (sid == null || sid.trim().isEmpty()) {
            // currentNodeKey may itself be the studentId (e.g. "STD-0003"), use it as fallback
            if (currentNodeKey != null && currentNodeKey.startsWith("STD-")) {
                sid = currentNodeKey;
            } else {
                sid = "";
            }
        }
        if (tvStudentId != null) {
            tvStudentId.setText("Student ID: " + sid);
        }

        // Load image safely (Base64)
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

    // showChangePasswordDialog updated to use currentNodeKey if available when updating DB
    private void showChangePasswordDialog() {
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
                if (user == null || user.getEmail() == null) {
                    Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Re-authenticate then update
                auth.signInWithEmailAndPassword(user.getEmail(), oldPass)
                        .addOnCompleteListener(authTask -> {
                            if (authTask.isSuccessful()) {
                                user.updatePassword(newPass).addOnCompleteListener(updateTask -> {
                                    if (updateTask.isSuccessful()) {
                                        // Update password in Realtime Database using resolved node key if available
                                        if (currentNodeKey != null && !currentNodeKey.isEmpty()) {
                                            studentsRef.child(currentNodeKey).child("password").setValue(hashPassword(newPass))
                                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update password in DB: " + e.getMessage()));
                                        } else {
                                            // Fallback: try query by uid field
                                            studentsRef.orderByChild("uid").equalTo(user.getUid())
                                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                                        @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                            if (snapshot.exists()) {
                                                                for (DataSnapshot ds : snapshot.getChildren()) {
                                                                    ds.getRef().child("password").setValue(hashPassword(newPass));
                                                                    break;
                                                                }
                                                            } else {
                                                                Log.w(TAG, "Could not find student node to update password (fallback).");
                                                            }
                                                        }
                                                        @Override public void onCancelled(@NonNull DatabaseError error) {
                                                            Log.e(TAG, "DB Error when updating password (fallback): " + error.getMessage());
                                                        }
                                                    });
                                        }

                                        Toast.makeText(StudentProfileActivity.this,
                                                "Password changed successfully!", Toast.LENGTH_SHORT).show();
                                        dialog.dismiss();
                                    } else {
                                        Toast.makeText(StudentProfileActivity.this,
                                                "Error: " + updateTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } else {
                                Toast.makeText(StudentProfileActivity.this, "Old password is incorrect", Toast.LENGTH_SHORT).show();
                            }
                        });
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