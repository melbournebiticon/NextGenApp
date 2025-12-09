package com.finale.nextgen;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.finale.nextgen.student.StudentDashboardActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ForgotPasswordPrefs";
    private static final String KEY_LAST_RESET_TIME = "last_reset_time";
    private static final long COOLDOWN_MILLIS = 5 * 60 * 1000; // 5 minutes

    EditText emailEt, passwordEt;
    Button loginBtn;
    TextView forgotPasswordTv;

    FirebaseAuth auth;
    DatabaseReference dbRef;
    SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        emailEt = findViewById(R.id.email);
        passwordEt = findViewById(R.id.password);
        loginBtn = findViewById(R.id.loginBtn);
        forgotPasswordTv = findViewById(R.id.forgotPasswordTv);

        auth = FirebaseAuth.getInstance();
        dbRef = FirebaseDatabase.getInstance().getReference("Users");
        sessionManager = new SessionManager(this);

        // ✅ Check if session exists
        if (sessionManager.isLoggedIn()) {
            redirectUser(sessionManager.getRole());
            finish();
        }

        // Login button
        loginBtn.setOnClickListener(v -> loginUser());

        // Forgot password
        forgotPasswordTv.setOnClickListener(v -> showForgotPasswordDialog());
    }

    // Login method
    private void loginUser() {
        String inputIdOrEmail = emailEt.getText().toString().trim();
        String password = passwordEt.getText().toString().trim();

        if (inputIdOrEmail.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (inputIdOrEmail.contains("@")) {
            // ✅ Teacher login by email
            DatabaseReference teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
            teachersRef.orderByChild("email").equalTo(inputIdOrEmail)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                for (DataSnapshot teacherSnap : snapshot.getChildren()) {
                                    final String teacherId = teacherSnap.child("id").getValue(String.class);
                                    String teacherEmail = teacherSnap.child("email").getValue(String.class);

                                    if (teacherEmail != null) {
                                        auth.signInWithEmailAndPassword(teacherEmail, password)
                                                .addOnCompleteListener(task -> {
                                                    if (task.isSuccessful()) {
                                                        sessionManager.saveSession(teacherId, "teacher");
                                                        redirectUser("teacher");
                                                        finish();
                                                    } else {
                                                        Toast.makeText(MainActivity.this, "Login Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                                                    }
                                                });
                                    } else {
                                        Toast.makeText(MainActivity.this, "Teacher email not found in DB", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            } else {
                                // Not a teacher, fallback to normal email login
                                auth.signInWithEmailAndPassword(inputIdOrEmail, password)
                                        .addOnCompleteListener(task -> {
                                            if (task.isSuccessful()) {
                                                FirebaseUser user = auth.getCurrentUser();
                                                if (user != null && user.isEmailVerified()) {
                                                    dbRef.child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                                                        @Override
                                                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                            String role = snapshot.child("role").getValue(String.class);
                                                            String teacherId = snapshot.child("teacherId").getValue(String.class);
                                                            String studentId = snapshot.child("studentId").getValue(String.class);

                                                            if (role != null) {
                                                                String idToStore = (role.equals("teacher")) ? teacherId : (role.equals("student") ? studentId : user.getUid());
                                                                sessionManager.saveSession(idToStore, role);
                                                                redirectUser(role);
                                                                finish();
                                                            } else {
                                                                Toast.makeText(MainActivity.this, "Role not found!", Toast.LENGTH_SHORT).show();
                                                            }
                                                        }

                                                        @Override
                                                        public void onCancelled(@NonNull DatabaseError error) {
                                                            Toast.makeText(MainActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                                        }
                                                    });
                                                } else {
                                                    user.sendEmailVerification().addOnCompleteListener(verifyTask -> {
                                                        if (verifyTask.isSuccessful()) {
                                                            Toast.makeText(MainActivity.this, "Verification email sent. Please check your inbox.", Toast.LENGTH_LONG).show();
                                                        } else {
                                                            Toast.makeText(MainActivity.this, "Failed to send verification: " + verifyTask.getException().getMessage(), Toast.LENGTH_LONG).show();
                                                        }
                                                    });
                                                    auth.signOut();
                                                }
                                            } else {
                                                Toast.makeText(MainActivity.this, "Login Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                                            }
                                        });
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(MainActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });

        } else {
            // Teacher or Student login by ID
            String inputId = inputIdOrEmail.toUpperCase();
            DatabaseReference teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
            teachersRef.child(inputId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String teacherEmail = snapshot.child("email").getValue(String.class);
                        final String teacherIdFromDb = snapshot.child("id").getValue(String.class);
                        if (teacherEmail != null && teacherIdFromDb != null) {
                            auth.signInWithEmailAndPassword(teacherEmail, password)
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful()) {
                                            FirebaseUser user = auth.getCurrentUser();
                                            if (user != null && user.isEmailVerified()) {
                                                sessionManager.saveSession(teacherIdFromDb, "teacher");
                                                redirectUser("teacher");
                                                finish();
                                            } else {
                                                Toast.makeText(MainActivity.this, "Please verify your email first.", Toast.LENGTH_LONG).show();
                                                if (user != null) {
                                                    user.sendEmailVerification()
                                                            .addOnCompleteListener(verifyTask -> {
                                                                if (verifyTask.isSuccessful()) {
                                                                    Toast.makeText(MainActivity.this, "Verification email sent. Check inbox.", Toast.LENGTH_LONG).show();
                                                                }
                                                            });
                                                }
                                                auth.signOut(); // prevent login until verified
                                            }
                                        } else {
                                            Toast.makeText(MainActivity.this, "Login Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    });

                        } else {
                            Toast.makeText(MainActivity.this, "Teacher email or ID missing in DB", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // Student login fallback
                        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
                        studentsRef.child(inputId).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                    String studentEmail = snapshot.child("email").getValue(String.class);
                                    if (studentEmail != null) {
                                        auth.signInWithEmailAndPassword(studentEmail, password)
                                                .addOnCompleteListener(task -> {
                                                    if (task.isSuccessful()) {
                                                        FirebaseUser user = auth.getCurrentUser();
                                                        if (user != null && user.isEmailVerified()) {
                                                            // Proceed if email is verified
                                                            sessionManager.saveSession(inputId, "student");
                                                            redirectUser("student");
                                                            finish();
                                                        } else {
                                                            Toast.makeText(MainActivity.this, "Please verify your email first.", Toast.LENGTH_LONG).show();
                                                            if (user != null) {
                                                                user.sendEmailVerification()
                                                                        .addOnCompleteListener(verifyTask -> {
                                                                            if (verifyTask.isSuccessful()) {
                                                                                Toast.makeText(MainActivity.this, "Verification email sent. Check inbox.", Toast.LENGTH_LONG).show();
                                                                            }
                                                                        });
                                                            }
                                                            auth.signOut(); // Prevent login until verified
                                                        }
                                                    } else {
                                                        Toast.makeText(MainActivity.this, "Login Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                                                    }
                                                });

                                    } else {
                                        Toast.makeText(MainActivity.this, "Student email not found in DB", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Toast.makeText(MainActivity.this, "ID not found", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Toast.makeText(MainActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(MainActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // Redirect user based on role
    private void redirectUser(String role) {
        Intent intent;
        switch (role) {
            case "admin":
                intent = new Intent(MainActivity.this, com.finale.nextgen.admin.AdminActivity.class);
                break;
            case "teacher":
                intent = new Intent(MainActivity.this, com.finale.nextgen.teacher.TeacherDashboardActivity.class);
                break;
            case "student":
                intent = new Intent(MainActivity.this, StudentDashboardActivity.class);
                break;
            default:
                Toast.makeText(this, "Unknown role!", Toast.LENGTH_SHORT).show();
                return;
        }
        startActivity(intent);
    }

    // Forgot password dialog
    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Password");

        final EditText input = new EditText(this);
        input.setHint("Enter your email");
        builder.setView(input);

        builder.setPositiveButton("Send", (dialog, which) -> {
            String email = input.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(MainActivity.this, "Email cannot be empty", Toast.LENGTH_SHORT).show();
            } else {
                sendPasswordResetEmail(email);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void sendPasswordResetEmail(String email) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long lastResetTime = prefs.getLong(KEY_LAST_RESET_TIME, 0);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastResetTime < COOLDOWN_MILLIS) {
            long minutesLeft = (COOLDOWN_MILLIS - (currentTime - lastResetTime)) / 60000;
            Toast.makeText(this, "Please wait " + minutesLeft + " minute(s) before requesting again.", Toast.LENGTH_LONG).show();
            return;
        }

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Save the timestamp
                        prefs.edit().putLong(KEY_LAST_RESET_TIME, currentTime).apply();
                        Toast.makeText(MainActivity.this, "Password reset email sent. Check your inbox.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

}
