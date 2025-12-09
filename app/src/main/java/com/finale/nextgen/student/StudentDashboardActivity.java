package com.finale.nextgen.student;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.finale.nextgen.MainActivity;

import com.finale.nextgen.R;
import com.finale.nextgen.SessionManager;
import com.finale.nextgen.admin.StudentModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StudentDashboardActivity extends AppCompatActivity
        implements BottomNavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "StudentDashboard";

    // --- Dashboard UI Fields ---
    private TextView tvGreeting;
    private TextView tvStudentNameHeader;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    // --- Profile Menu ---
    private MaterialCardView btnProfileMenu;
    private ImageView imgProfileMenu;

    private LinearLayout layoutOfflinePrep;
    private TextView tvOfflinePrep;
    private Button btnLogout;

    // --- Firebase ---
    private FirebaseAuth auth;
    private DatabaseReference studentsRef;
    private String currentStudentUid;
    private StudentModel currentStudent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        // Cards
        MaterialCardView cardExam = findViewById(R.id.cardExam);
        MaterialCardView cardQuiz = findViewById(R.id.cardQuiz);
        MaterialCardView cardActivity = findViewById(R.id.cardActivity);
        MaterialCardView cardAttendance = findViewById(R.id.cardAttendance);

        cardExam.setOnClickListener(v -> startActivity(new Intent(this, ExamListActivity.class)));
        cardQuiz.setOnClickListener(v -> startActivity(new Intent(this, QuizListActivity.class)));
        cardAttendance.setOnClickListener(v ->
                startActivity(new Intent(this, StudentAttendanceViewerActivity.class))
        );

        cardActivity.setOnClickListener(v -> {
            if (currentStudent != null) showSubjectSelection(currentStudent);
            else Toast.makeText(this, "Student data not loaded yet.", Toast.LENGTH_SHORT).show();
        });

        // --- Dashboard UI Elements ---
        tvGreeting = findViewById(R.id.tvGreeting);
        tvStudentNameHeader = findViewById(R.id.tvStudentNameHeader);
        layoutOfflinePrep = findViewById(R.id.layoutOfflinePrep);
        tvOfflinePrep = findViewById(R.id.tvOfflinePrep);
        btnProfileMenu = findViewById(R.id.btnProfileMenu);
        imgProfileMenu = findViewById(R.id.imgProfileMenu);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Drawer Navigation Setup
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_scanner) {
                startActivity(new Intent(this, StudentQRScannerActivity.class));
                drawerLayout.closeDrawer(navigationView);
                return true;
            } else if (id == R.id.menu_logout) {
                handleLogout();
                drawerLayout.closeDrawer(navigationView);
                return true;
            } else if (id == R.id.menu_change_password) {
                showChangePasswordDialog();
                drawerLayout.closeDrawer(navigationView);
                return true;
            } else if (id == R.id.menu_view_profile) {
                startActivity(new Intent(this, StudentProfileActivity.class));
                drawerLayout.closeDrawer(navigationView);
                return true;
            }
            return false;
        });
        btnProfileMenu.setOnClickListener(this::showProfilePopup);

        // --- Firebase Auth ---
        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No logged-in user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentStudentUid = currentUser.getUid();
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        // --- Fetch Student Data ---
        studentsRef.orderByChild("uid").equalTo(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                StudentModel student = ds.getValue(StudentModel.class);
                                if (student != null) {
                                    currentStudent = student;
                                    try {
                                        SessionManager sm = new SessionManager(StudentDashboardActivity.this);
                                        if (student.getStudentId() != null && !student.getStudentId().trim().isEmpty()) {
                                            sm.saveStudentId(student.getStudentId());
                                        }
                                        try { sm.saveStudentModel(student); } catch (Exception ignored) {}
                                        Log.d(TAG, "Saved studentId to SessionManager: " + student.getStudentId());
                                    } catch (Exception e) {
                                        Log.w(TAG, "Failed to save studentId to SessionManager: " + e.getMessage());
                                    }
                                    populateStudentData(student);
                                    showStudentSubjects();
                                }
                            }
                        } else {
                            Toast.makeText(StudentDashboardActivity.this, "Student record not found", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(StudentDashboardActivity.this, "Failed to fetch data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // --- Navigation Interface (BottomNavigationView, if present) ---
    @Override
    public boolean onNavigationItemSelected(@NonNull android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_view_activities) {
            if (currentStudent != null) showSubjectSelection(currentStudent);
            else Toast.makeText(this, "Student data not loaded yet.", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.nav_scanner) {
            startActivity(new Intent(StudentDashboardActivity.this, StudentQRScannerActivity.class));
            return true;
        }
        if (id == R.id.nav_view_profile) {
            return true;
        }
        return false;
    }

    // --- Populate Student Data & Greeting ---
    private void populateStudentData(StudentModel student) {
        if (student == null) return;
        tvStudentNameHeader.setText(student.getFullName());
        if (student.getProfileImage() != null && !student.getProfileImage().isEmpty()) {
            try {
                byte[] decodedBytes = android.util.Base64.decode(student.getProfileImage(), android.util.Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                if (imgProfileMenu != null) imgProfileMenu.setImageBitmap(bitmap);
            } catch (Exception e) {
                if (imgProfileMenu != null) imgProfileMenu.setImageResource(R.drawable.examinee_default);
            }
        } else {
            if (imgProfileMenu != null) imgProfileMenu.setImageResource(R.drawable.examinee_default);
        }
    }

    private String getGreetingMessage() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) {
            return "Magandang Umaga,";
        } else if (hour >= 12 && hour < 18) {
            return "Magandang Hapon,";
        } else {
            return "Magandang Gabi,";
        }
    }

    private void showStudentSubjects() {
        tvGreeting.setText(getGreetingMessage());
        // You may add other lightweight dashboard logic here if needed
    }

    // --- Show subject selection dialog for Activities card ---
    private void showSubjectSelection(StudentModel student) {
        if (student == null) return;

        DatabaseReference teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
        DatabaseReference subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");

        teachersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot teacherSnapshot) {
                final Map<String, String> subjectTeacherMap = new HashMap<>();
                for (DataSnapshot tSnap : teacherSnapshot.getChildren()) {
                    String teacherName = tSnap.child("fullName").getValue(String.class);
                    DataSnapshot assignedSubjects = tSnap.child("assignedSubjects");
                    if (assignedSubjects.exists()) {
                        for (DataSnapshot subSnap : assignedSubjects.getChildren()) {
                            String subId = subSnap.getValue(String.class);
                            if (subId != null && teacherName != null) {
                                subjectTeacherMap.put(subId, teacherName);
                            }
                        }
                    }
                }

                subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> subjectDisplays = new ArrayList<>();
                        List<Map<String, String>> subjectData = new ArrayList<>();
                        for (DataSnapshot snap : snapshot.getChildren()) {
                            final String subjectId = snap.getKey();
                            final String subjectCode = snap.child("code").getValue(String.class);
                            final String subjectName = snap.child("name").getValue(String.class);
                            final String courseName = snap.child("courseName").getValue(String.class);
                            final String specializationName = snap.child("specializationName").getValue(String.class);
                            final String yearName = snap.child("yearName").getValue(String.class);
                            final String sectionName = snap.child("sectionName").getValue(String.class);

                            if (subjectCode != null && courseName != null
                                    && courseName.equals(student.getCourseName())
                                    && specializationName != null && specializationName.equals(student.getSpecializationName())
                                    && yearName != null && yearName.equals(student.getYearName())
                                    && sectionName != null && sectionName.equals(student.getSectionName())) {

                                final String teacherName = subjectTeacherMap.getOrDefault(subjectId, "N/A");
                                final String courseDisplay = courseName + " - " + specializationName + " - " + yearName + " - " + sectionName;

                                subjectDisplays.add(subjectCode + " - " + subjectName);
                                Map<String, String> data = new HashMap<>();
                                data.put("subjectName", subjectName);
                                data.put("subjectCode", subjectCode);
                                data.put("teacherName", teacherName);
                                data.put("subjectId", subjectId);
                                data.put("courseDisplay", courseDisplay);
                                subjectData.add(data);
                            }
                        }

                        if (subjectDisplays.isEmpty()) {
                            Toast.makeText(StudentDashboardActivity.this, "No subjects found for your class.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(StudentDashboardActivity.this);
                        builder.setTitle("Select a Subject");
                        ListView listView = new ListView(StudentDashboardActivity.this);
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(StudentDashboardActivity.this, android.R.layout.simple_list_item_1, subjectDisplays);
                        listView.setAdapter(adapter);
                        builder.setView(listView);
                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
                        AlertDialog dialog = builder.create();

                        listView.setOnItemClickListener((parent, view, position, id) -> {
                            Map<String, String> selectedData = subjectData.get(position);
                            Intent intent = new Intent(StudentDashboardActivity.this, StudentActivitiesActivity.class);
                            intent.putExtra("subjectName", selectedData.get("subjectName"));
                            intent.putExtra("subjectCode", selectedData.get("subjectCode"));
                            intent.putExtra("teacherName", selectedData.get("teacherName"));
                            intent.putExtra("subjectId", selectedData.get("subjectId"));
                            intent.putExtra("courseDisplay", selectedData.get("courseDisplay"));
                            startActivity(intent);
                            dialog.dismiss();
                        });

                        dialog.show();
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(StudentDashboardActivity.this, "Failed to fetch subjects: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(StudentDashboardActivity.this, "Failed to fetch teachers: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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