package com.example.nextgen.student;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.drawerlayout.widget.DrawerLayout; // Import for Navigation Drawer

import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.example.nextgen.admin.StudentModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
// TANGGALIN NA ANG LAHAT NG FAB IMPORTS
// import com.google.android.material.floatingactionbutton.FloatingActionButton;
// import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationView; // Import for Navigation Drawer

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class StudentDashboardActivity extends AppCompatActivity
        implements BottomNavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "StudentDashboard";

    // --- Dashboard UI Fields ---
    private TextView tvGreeting;
    private TextView tvStudentNameHeader;
    private TextView tvTotalExams;
    private TextView tvAvgScore;
    private LinearLayout emptyStateLayout;

    private DrawerLayout drawerLayout; // New: Drawer Layout
    private NavigationView navigationView; // New: Navigation View

    // --- Profile Menu ---
    private MaterialCardView btnProfileMenu;

    // --- Firebase ---
    private FirebaseAuth auth;
    private DatabaseReference studentsRef;
    private DatabaseReference scoresRef;
    private DatabaseReference examsRef;
    private String currentStudentUid;
    private StudentModel currentStudent;

    // --- RecyclerView for Exams ---
    private RecyclerView rvExams;
    private ExamAdapter examAdapter;

    private List<ExamModel> examList = Collections.synchronizedList(new ArrayList<>());
    private boolean isFetchingExams = false;  // Flag to prevent overlapping fetches

    // --- Constants ---
    private static final long MAX_LOGIN_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(15);
    private final int REFRESH_INTERVAL = 3000;

    // --- Handler for periodic exam refresh ---
    private Handler handler = new Handler();
    private Runnable examRefreshRunnable;

    private ImageView imgProfileMenu;
    private Button btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

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
        examsRef = FirebaseDatabase.getInstance().getReference("Exams");
        scoresRef = FirebaseDatabase.getInstance().getReference("Scores");

        // --- Dashboard UI Elements ---
        tvGreeting = findViewById(R.id.tvGreeting);
        tvStudentNameHeader = findViewById(R.id.tvStudentNameHeader);
        tvTotalExams = findViewById(R.id.tvTotalExams);
        tvAvgScore = findViewById(R.id.tvAvgScore);
        emptyStateLayout = findViewById(R.id.emptyState);

        // --- Navigation Drawer Setup (NEW) ---
        // Assuming your layout is a DrawerLayout with ID drawer_layout
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            // 1. Scanner Logic (Check your menu XML if R.id.nav_scanner exists)
            if (id == R.id.nav_scanner) {
                Intent intent = new Intent(StudentDashboardActivity.this, StudentQRScannerActivity.class);
                startActivity(intent);
                drawerLayout.closeDrawer(navigationView);
                return true;

                // 2. Logout Logic (Using the correct ID from your XML: R.id.menu_logout)
            } else if (id == R.id.menu_logout) {
                handleLogout();
                drawerLayout.closeDrawer(navigationView);
                return true;

                // 3. Change Password Logic (Using the correct ID from your XML: R.id.menu_change_password)
            } else if (id == R.id.menu_change_password) {
                showChangePasswordDialog();
                drawerLayout.closeDrawer(navigationView);
                return true;

                // 4. View Profile Logic (Using the correct ID from your XML: R.id.menu_view_profile)
            } else if (id == R.id.menu_view_profile) {
                startActivity(new Intent(StudentDashboardActivity.this, StudentProfileActivity.class));
                drawerLayout.closeDrawer(navigationView);
                return true;
            }

            return false;
        });


        // --- Profile Popup Menu ---
        // Pinalitan ang logic na ito para gumamit ng NavigationView.
        // Kung gusto mo pa rin ng popup, panatilihin ang btnProfileMenu at showProfilePopup.
        // Dahil inilipat na natin ang Profile actions sa Navigation Drawer, maaari mo nang i-set up
        // ang button na ito para i-open ang drawer.

        btnProfileMenu = findViewById(R.id.btnProfileMenu);
        imgProfileMenu = findViewById(R.id.imgProfileMenu);

        // Maaari mo itong palitan para i-open ang drawer sa halip na magpakita ng popup
        // btnProfileMenu.setOnClickListener(v -> drawerLayout.openDrawer(navigationView));

        // Kung gusto mo pa rin ng popup, gamitin ang lumang code:
        btnProfileMenu.setOnClickListener(this::showProfilePopup);

        // --- RecyclerView for Exams ---
        rvExams = findViewById(R.id.rvExams);
        rvExams.setLayoutManager(new LinearLayoutManager(this));
        examAdapter = new ExamAdapter(this, examList);
        rvExams.setAdapter(examAdapter);

        // --- Bottom Navigation Setup ---
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);

        // TANGGALIN ANG FAB SCANNER BLOCK DITO
        // ...


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
                                    populateStudentData(student);
                                    showStudentSubjects();
                                    startPeriodicExamFetch(student);
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

    // --- Periodic Exam Fetch ---
    private void startPeriodicExamFetch(StudentModel student) {
        if (examRefreshRunnable != null) handler.removeCallbacks(examRefreshRunnable);
        examRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isFetchingExams) {  // Only fetch if not already running
                    Log.d(TAG, "Running periodic exam fetch");
                    fetchExamsForStudent(student);
                }
                fetchStudentStats(tvTotalExams, tvAvgScore);  // This can run independently
                handler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
        handler.post(examRefreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null && examRefreshRunnable != null) handler.removeCallbacks(examRefreshRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && studentsRef != null) {
            studentsRef.orderByChild("uid").equalTo(currentUser.getUid())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                for (DataSnapshot ds : snapshot.getChildren()) {
                                    StudentModel student = ds.getValue(StudentModel.class);
                                    if (student != null) startPeriodicExamFetch(student);
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) { /* Do nothing */ }
                    });
        }
    }

    // --- Bottom Navigation Handler ---
    // --- Bottom Navigation Handler ---
    @Override
    public boolean onNavigationItemSelected(@NonNull android.view.MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_view_activities) {
            if (currentStudent != null) {
                showSubjectSelection(currentStudent);
            } else {
                Toast.makeText(this, "Student data not loaded yet.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        // NEW: Handle Scanner from Bottom Navigation
        if (id == R.id.nav_scanner) {
            Intent intent = new Intent(StudentDashboardActivity.this, StudentQRScannerActivity.class);
            startActivity(intent);
            return true;
        }

        // Existing: Handle Grades/Profile from Bottom Navigation
        if (id == R.id.nav_view_profile) {
            // Add navigation logic for Grades here, e.g.:
            // Intent intent = new Intent(this, StudentGradesActivity.class);
            // startActivity(intent);
            return true;
        }

        return false;
    }


    // --- Logout ---
    private void handleLogout() {
        new SessionManager(this).clearSession();
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // --- Profile Popup Menu ---
    // Pwede itong tanggalin kung ang Profile actions ay nasa Navigation Drawer na.
    // Pero hinayaan ko ito kung sakaling gagamitin mo ang button na ito para sa mabilisang action.
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

    // --- Populate Student Data ---
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
        fetchStudentStats(tvTotalExams, tvAvgScore);
    }

    // --- Show subject selection dialog ---
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

    // --- Fetch student stats ---
    private void fetchStudentStats(final TextView tvTotalExams, final TextView tvAvgScore) {
        if (currentStudentUid == null) return;

        scoresRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int totalExamsTaken = 0;
                long totalScoreSum = 0;
                String studentId = currentStudentUid;

                if (snapshot.hasChild(studentId)) {
                    DataSnapshot studentScoresSnap = snapshot.child(studentId);
                    for (DataSnapshot examScoreSnap : studentScoresSnap.getChildren()) {
                        String percentageStr = examScoreSnap.child("percentage").getValue(String.class);
                        if (percentageStr != null && percentageStr.endsWith("%")) {
                            try {
                                int percentage = Integer.parseInt(percentageStr.replace("%", "").trim());
                                totalScoreSum += percentage;
                                totalExamsTaken++;
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Error parsing percentage score: " + percentageStr);
                            }
                        }
                    }
                }

                if (totalExamsTaken > 0) {
                    int averageScore = (int) (totalScoreSum / totalExamsTaken);
                    tvTotalExams.setText(String.valueOf(totalExamsTaken));
                    tvAvgScore.setText(averageScore + "%");
                } else {
                    tvTotalExams.setText("0");
                    tvAvgScore.setText("0%");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch student stats: " + error.getMessage());
                tvTotalExams.setText("?");
                tvAvgScore.setText("?%");
            }
        });
    }

    // --- Fetch exams for student ---
    private void fetchExamsForStudent(StudentModel student) {
        if (isFetchingExams) return;  // Prevent overlapping
        isFetchingExams = true;

        String studentCourseDisplay = student.getCourseName()
                + " - " + student.getSpecializationName()
                + " - " + student.getYearName()
                + " - " + student.getSectionName();

        long currentTime = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

        String studentId = student.getStudentId();

        examsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ExamModel> tempExamList = Collections.synchronizedList(new ArrayList<>());  // Temporary list for collecting data

                List<DataSnapshot> eligibleExams = new ArrayList<>();
                for (DataSnapshot teacherSnap : snapshot.getChildren()) {
                    for (DataSnapshot examSnap : teacherSnap.getChildren()) {
                        ExamModel exam = examSnap.getValue(ExamModel.class);
                        Long scheduledAtLong = examSnap.child("scheduledAt").getValue(Long.class);
                        Integer durationMinutesInt = examSnap.child("durationMinutes").getValue(Integer.class);

                        if (exam != null && scheduledAtLong != null && durationMinutesInt != null &&
                                exam.getCourseDisplay().equals(studentCourseDisplay) &&
                                exam.isActive()) {
                            eligibleExams.add(examSnap);
                        }
                    }
                }

                final int totalEligibleExams = eligibleExams.size();
                final int[] examsProcessed = {0};

                if (totalEligibleExams == 0) {
                    runOnUiThread(() -> {
                        synchronized (examList) {
                            examList.clear();
                            updateExamRecyclerView();
                        }
                        isFetchingExams = false;
                    });
                    return;
                }

                for (DataSnapshot examSnap : eligibleExams) {
                    ExamModel exam = examSnap.getValue(ExamModel.class);
                    String examId = examSnap.getKey();

                    exam.setExamId(examId);
                    exam.setScheduledAt(examSnap.child("scheduledAt").getValue(Long.class));
                    exam.setDurationMinutes(examSnap.child("durationMinutes").getValue(Integer.class));

                    // Check if exam has been taken
                    scoresRef.child(studentId).child(examId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot scoreSnapshot) {
                                    synchronized (tempExamList) {
                                        if (scoreSnapshot.exists()) {
                                            exam.setStatus("TAKEN");
                                            exam.setAvailable(false);
                                        } else {
                                            long start = exam.getScheduledAt();
                                            long endLogin = start + MAX_LOGIN_WINDOW_MILLIS;

                                            if (currentTime < start) {
                                                exam.setStatus("Scheduled: Starts at " + sdf.format(new Date(start)));
                                                exam.setAvailable(false);
                                            } else if (currentTime <= endLogin) {
                                                exam.setStatus("AVAILABLE NOW (Login closes at " + sdf.format(new Date(endLogin)) + ")");
                                                exam.setAvailable(true);
                                            } else {
                                                exam.setStatus("EXPIRED: Login window closed at " + sdf.format(new Date(endLogin)));
                                                exam.setAvailable(false);
                                            }
                                        }

                                        // Fetch "present"
                                        DatabaseReference examStudentRef = FirebaseDatabase.getInstance()
                                                .getReference("ExamStudents")
                                                .child(examId)
                                                .child(studentId);

                                        examStudentRef.child("present").addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override
                                            public void onDataChange(@NonNull DataSnapshot presentSnap) {
                                                synchronized (tempExamList) {
                                                    Boolean present = presentSnap.getValue(Boolean.class);
                                                    Log.d(TAG, "Fetched present value: " + present + " for exam: " + exam.getExamTitle());

                                                    // Use boolean logic for availability: Must be initially available AND marked present
                                                    exam.setPresent(present != null && present);
                                                    exam.setAvailable(exam.isAvailable() && exam.isPresent());

                                                    tempExamList.add(exam);
                                                    examsProcessed[0]++;
                                                    if (examsProcessed[0] == totalEligibleExams) {
                                                        runOnUiThread(() -> {
                                                            synchronized (examList) {
                                                                examList.clear();
                                                                examList.addAll(tempExamList);
                                                                updateExamRecyclerView();
                                                            }
                                                            isFetchingExams = false;
                                                        });
                                                    }
                                                }
                                            }

                                            @Override
                                            public void onCancelled(@NonNull DatabaseError error) {
                                                // IMPORTANT: Handle cancellation and ensure fetching flag is reset
                                                Log.e(TAG, "Failed to check student attendance: " + error.getMessage());
                                                synchronized (tempExamList) {
                                                    examsProcessed[0]++; // Still count as processed to avoid infinite wait
                                                    if (examsProcessed[0] == totalEligibleExams) {
                                                        runOnUiThread(() -> {
                                                            isFetchingExams = false; // Reset flag after all attempts are done
                                                        });
                                                    }
                                                }
                                            }
                                        });
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    // This listener is inside the loop, so we must handle counter and flag here too.
                                    Log.e(TAG, "Failed to check exam score: " + error.getMessage());
                                    synchronized (tempExamList) {
                                        examsProcessed[0]++;
                                        if (examsProcessed[0] == totalEligibleExams) {
                                            runOnUiThread(() -> {
                                                isFetchingExams = false;
                                            });
                                        }
                                    }
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Outer listener cancellation
                Log.e(TAG, "Failed to fetch eligible exams: " + error.getMessage());
                runOnUiThread(() -> {
                    synchronized (examList) {
                        examList.clear();
                        updateExamRecyclerView();
                    }
                    isFetchingExams = false;
                });
            }
        });
    }

    // New helper method to handle UI updates for the RecyclerView
    private void updateExamRecyclerView() {
        if (examList.isEmpty()) {
            rvExams.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            rvExams.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
            examAdapter.notifyDataSetChanged();
        }
    }
}