package com.finale.nextgen.student;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;

import com.finale.nextgen.MainActivity;
import com.finale.nextgen.R;
import com.finale.nextgen.SessionManager;
import com.finale.nextgen.admin.StudentModel;
import com.finale.nextgen.student.ExamModel;
import com.finale.nextgen.student.ActivityModel;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class StudentDashboardActivity extends AppCompatActivity {

    private static final String TAG = "StudentDashboard";

    // Dashboard UI fields
    private ImageView btnProfileMenu, imgProfile, imgUpSign;
    private MaterialCardView profileCardHeader, card1, card2, card3, card4;
    private TextView tvProfileName, tvProfileID;
    private TextView welcomeText, dashboardText;

    // Firebase
    private FirebaseAuth auth;
    private DatabaseReference studentsRef, examsRef, activitiesRef;
    private String currentStudentUid;
    private StudentModel currentStudent;

    private ArrayList<ExamModel> studentExams = new ArrayList<>();
    private ArrayList<ActivityModel> studentActivities = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        // Find UI components
        btnProfileMenu = findViewById(R.id.btnProfileMenu);
        profileCardHeader = findViewById(R.id.profileCardHeader);
        card1 = findViewById(R.id.card1);
        card2 = findViewById(R.id.card2);
        card3 = findViewById(R.id.card3);
        card4 = findViewById(R.id.card4);
        imgProfile = findViewById(R.id.imgProfile);
        imgUpSign = findViewById(R.id.imgUpSign);
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileID = findViewById(R.id.tvProfileID);

        // Welcome text
        LinearLayout welcomeMessageContainer = findViewById(R.id.welcome_message_container);
        welcomeText = (TextView) welcomeMessageContainer.getChildAt(0);
        dashboardText = (TextView) welcomeMessageContainer.getChildAt(1);

        // Firebase
        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No logged-in user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentStudentUid = currentUser.getUid();
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        examsRef = FirebaseDatabase.getInstance().getReference("Exams");
        activitiesRef = FirebaseDatabase.getInstance().getReference("Activities");

        Log.d(TAG, "Fetch Dashboard for UID: " + currentStudentUid);

        // Fetch Student Data -- assuming node keys are UID for optimal fetch
        studentsRef.child(currentStudentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.d(TAG, "Snapshot exists: " + snapshot.exists());
                        Log.d(TAG, "Raw snapshot value: " + snapshot.getValue());

                        if (snapshot.exists()) {
                            StudentModel student = snapshot.getValue(StudentModel.class);
                            if (student != null) {
                                Log.d(TAG, "Fetched Student fullName: " + student.getFullName());
                                Log.d(TAG, "Student section: " + student.getSectionName());
                                Log.d(TAG, "Student course: " + student.getCourseName());
                                Log.d(TAG, "Student id: " + student.getStudentId());
                                currentStudent = student;
                                try {
                                    SessionManager sm = new SessionManager(StudentDashboardActivity.this);
                                    if (student.getStudentId() != null && !student.getStudentId().trim().isEmpty()) {
                                        sm.saveStudentId(student.getStudentId());
                                    }
                                    try { sm.saveStudentModel(student); } catch (Exception ignored) {}
                                    Log.d(TAG, "Saved studentId to SessionManager: " + student.getStudentId());
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to save studentId to SessionManager: " + e.getMessage());
                                }
                                populateStudentProfileCard(student);
                                // Fetch other related data for dashboard (exams, activities)
                                fetchExamsForStudent(student);
                                fetchActivitiesForStudent(student);
                            } else {
                                Log.e(TAG, "StudentModel is null (check your DB fields and model match)");
                                Toast.makeText(StudentDashboardActivity.this, "Student data error, contact admin.", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Log.e(TAG, "Student record not found for UID: " + currentStudentUid);
                            Toast.makeText(StudentDashboardActivity.this, "Student record not found", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to fetch student data: " + error.getMessage());
                        Toast.makeText(StudentDashboardActivity.this, "Failed to fetch student data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

        // Click Listeners for navigation
        btnProfileMenu.setOnClickListener(this::showProfilePopup); // Updated to popup
        profileCardHeader.setOnClickListener(v -> startActivity(new Intent(this, StudentProfileActivity.class)));
        card1.setOnClickListener(v -> startActivity(new Intent(this, ExamListActivity.class)));
        card2.setOnClickListener(v -> startActivity(new Intent(this, QuizListActivity.class)));
        card3.setOnClickListener(v -> {
            if (currentStudent != null) showSubjectSelection(currentStudent);
            else Toast.makeText(this, "Student data not loaded yet.", Toast.LENGTH_SHORT).show();
        });
        card4.setOnClickListener(v -> startActivity(new Intent(this, StudentAttendanceViewerActivity.class)));
        imgUpSign.setOnClickListener(v -> startActivity(new Intent(this, StudentProfileActivity.class)));
    }

    // --- Profile Popup Menu ---
    private void showProfilePopup(View anchor) {
        androidx.appcompat.widget.PopupMenu popupMenu = new androidx.appcompat.widget.PopupMenu(this, anchor);

        popupMenu.getMenu().add("View Profile");
        popupMenu.getMenu().add("Change Password");
        popupMenu.getMenu().add("Logout");

        popupMenu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            switch (title) {
                case "View Profile":
                    startActivity(new Intent(this, StudentProfileActivity.class));
                    return true;
                case "Change Password":
                    showChangePasswordDialog();
                    return true;
                case "Logout":
                    handleLogout();
                    return true;
                default:
                    return false;
            }
        });
        popupMenu.show();
    }

    // --- Logout ---
    private void handleLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setCancelable(true)
                .setPositiveButton("Yes", (dialog, which) -> {
                    new SessionManager(this).clearSession();
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // --- Change Password Dialog ---
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
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }

                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null || user.getEmail() == null) {
                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                com.google.firebase.auth.AuthCredential credential =
                        com.google.firebase.auth.EmailAuthProvider.getCredential(user.getEmail(), oldPass);

                user.reauthenticate(credential)
                        .addOnSuccessListener(aVoid -> user.updatePassword(newPass)
                                .addOnSuccessListener(aVoid1 -> {
                                    String hashedNewPass = hashPassword(newPass);
                                    String studentId = currentStudentUid;
                                    if (studentId != null) {
                                        studentsRef.child(studentId).child("password")
                                                .setValue(hashedNewPass)
                                                .addOnSuccessListener(aVoid2 -> {
                                                    Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show();
                                                    dialog.dismiss();
                                                })
                                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to update password in DB.", Toast.LENGTH_SHORT).show());
                                    } else {
                                        Toast.makeText(this, "Student ID missing.", Toast.LENGTH_SHORT).show();
                                    }
                                }))
                        .addOnFailureListener(e -> Toast.makeText(this, "Old password is incorrect or reauthentication failed.", Toast.LENGTH_SHORT).show());
            });
        });
        dialog.show();
    }

    private void fetchExamsForStudent(StudentModel student) {
        if (student == null) return;
        examsRef.orderByChild("courseName").equalTo(student.getCourseName())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.d(TAG, "Exam snapshot for student course: " + student.getCourseName());
                        studentExams.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            ExamModel exam = ds.getValue(ExamModel.class);
                            if (exam != null) {
                                boolean sectionOk = exam.getSectionName() != null && exam.getSectionName().equals(student.getSectionName());
                                boolean yearOk = exam.getYearName() != null && exam.getYearName().equals(student.getYearName());
                                boolean specOk = exam.getSpecializationName() != null && exam.getSpecializationName().equals(student.getSpecializationName());
                                if (sectionOk && yearOk && specOk) {
                                    studentExams.add(exam);
                                    Log.d(TAG, "Fetched Exam: " + exam.getExamTitle() + " | " + exam.getExamId());
                                }
                            }
                        }
                        Log.d(TAG, "Total exams fetched: " + studentExams.size());
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Exam fetch cancelled: " + error.getMessage());
                    }
                });
    }

    private void fetchActivitiesForStudent(StudentModel student) {
        if (student == null) return;
        activitiesRef.orderByChild("sectionName").equalTo(student.getSectionName())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.d(TAG, "Activity snapshot for section: " + student.getSectionName());
                        studentActivities.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            ActivityModel activity = ds.getValue(ActivityModel.class);
                            if (activity != null) {
                                studentActivities.add(activity);
                                Log.d(TAG, "Fetched Activity: " + activity.getTitle() + " | " + activity.getActivityId());
                            }
                        }
                        Log.d(TAG, "Total activities fetched: " + studentActivities.size());
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Activity fetch cancelled: " + error.getMessage());
                    }
                });
    }

    private void populateStudentProfileCard(StudentModel student) {
        if (student == null) return;
        tvProfileName.setText(student.getFullName() != null ? student.getFullName() : "No name");
        tvProfileID.setText(student.getStudentId() != null ? "Student ID: " + student.getStudentId() : "No ID");
        if (student.getProfileImage() != null && !student.getProfileImage().isEmpty()) {
            try {
                byte[] decodedBytes = android.util.Base64.decode(student.getProfileImage(), android.util.Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                imgProfile.setImageBitmap(bitmap);
                btnProfileMenu.setImageBitmap(bitmap);
            } catch (Exception e) {
                imgProfile.setImageResource(R.drawable.examinee_default);
                btnProfileMenu.setImageResource(R.drawable.examinee_default);
            }
        } else {
            imgProfile.setImageResource(R.drawable.examinee_default);
            btnProfileMenu.setImageResource(R.drawable.examinee_default);
        }
    }

    // --- Show subject selection dialog for Activities card ---
    private void showSubjectSelection(StudentModel student) {
        // ... retain your subject selection logic (unchanged)
    }

    // --- Hash password utility (if needed) ---
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
            Log.e(TAG, "SHA-256 algorithm not found.", e);
            return null;
        }
    }
}