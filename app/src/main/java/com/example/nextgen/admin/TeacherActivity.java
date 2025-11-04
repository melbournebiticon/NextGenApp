package com.example.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class TeacherActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private RecyclerView recyclerTeachers;
    private Button btnAddTeacher;
    private EditText searchTeacher;
    private LinearLayout curriculumDropdown, accountsDropdown;

    private List<CourseModel> courseOptionList = new ArrayList<>();
    private List<SubjectModel> subjectOptionList = new ArrayList<>();
    private List<TeacherModel> teacherList = new ArrayList<>();

    private DatabaseReference teachersRef, coursesRef, subjectsRef, usersRef;
    private FirebaseAuth auth;
    private SessionManager sessionManager;

    private TeacherAdapter teacherAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher);

        initializeViews();
        setupSidebar();
        setupFirebase();
        setupRecyclerViews();
        loadTeachers();
        loadCourses();
        loadSubjects();
        setupClickListeners();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        fixSidebarHeight();
    }

    private void fixSidebarHeight() {
        View sidebarLayout = findViewById(R.id.sidebarLayout);
        if (sidebarLayout != null) {
            sidebarLayout.post(new Runnable() {
                @Override
                public void run() {
                    DisplayMetrics displayMetrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                    int screenHeight = displayMetrics.heightPixels;

                    ViewGroup.LayoutParams params = sidebarLayout.getLayoutParams();
                    params.height = screenHeight;
                    sidebarLayout.setLayoutParams(params);
                    sidebarLayout.requestLayout();

                    // Debug log
                    Log.d("SIDEBAR_FIX", "Sidebar height set to: " + screenHeight);
                }
            });
        }
    }

    private void initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        recyclerTeachers = findViewById(R.id.recyclerTeachers);
        btnAddTeacher = findViewById(R.id.btnAddTeacher);
        searchTeacher = findViewById(R.id.searchTeacher);
        curriculumDropdown = findViewById(R.id.curriculumDropdown);
        accountsDropdown = findViewById(R.id.accountsDropdown);
    }

    private void setupSidebar() {
        // CORRECT ID: btnToggleSidebar
        ImageButton btnToggleSidebar = findViewById(R.id.btnToggleSidebar);
        btnToggleSidebar.setOnClickListener(v -> toggleSidebar());

        // Dropdown headers
        Button btnManageCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        Button btnManageAccountsHeader = findViewById(R.id.btnManageAccountsHeader);

        // Toggle dropdowns (exactly like AdminDashboard)
        btnManageCurriculumHeader.setOnClickListener(v -> {
            if (curriculumDropdown.getVisibility() == View.VISIBLE) {
                curriculumDropdown.setVisibility(View.GONE);
            } else {
                curriculumDropdown.setVisibility(View.VISIBLE);
                accountsDropdown.setVisibility(View.GONE); // close other dropdown
            }
        });

        btnManageAccountsHeader.setOnClickListener(v -> {
            if (accountsDropdown.getVisibility() == View.VISIBLE) {
                accountsDropdown.setVisibility(View.GONE);
            } else {
                accountsDropdown.setVisibility(View.VISIBLE);
                curriculumDropdown.setVisibility(View.GONE); // close other dropdown
            }
        });

        // BUTTONS INSIDE CURRICULUM DROPDOWN
        findViewById(R.id.btnManageSpecializations).setOnClickListener(v -> {
            navigateToActivity("Specializations");
            drawerLayout.closeDrawer(android.view.Gravity.START);
        });

        findViewById(R.id.btnManageYears).setOnClickListener(v -> {
            navigateToActivity("Years");
            drawerLayout.closeDrawer(android.view.Gravity.START);
        });

        findViewById(R.id.btnManageSections).setOnClickListener(v -> {
            navigateToActivity("Sections");
            drawerLayout.closeDrawer(android.view.Gravity.START);
        });

        findViewById(R.id.btnManageCourse).setOnClickListener(v -> {
            navigateToActivity("Courses");
            drawerLayout.closeDrawer(android.view.Gravity.START);
        });

        findViewById(R.id.btnManageSubjects).setOnClickListener(v -> {
            navigateToActivity("Subjects");
            drawerLayout.closeDrawer(android.view.Gravity.START);
        });

        // BUTTONS INSIDE ACCOUNTS DROPDOWN
        findViewById(R.id.btnManageTeachers).setOnClickListener(v -> {
            Toast.makeText(this, "Already in Teachers", Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(android.view.Gravity.START);
        });

        findViewById(R.id.btnManageStudents).setOnClickListener(v -> {
            navigateToActivity("Examinees");
            drawerLayout.closeDrawer(android.view.Gravity.START);
        });

        // Logout button - FIXED: Same as AdminActivity
        Button logoutBtn = findViewById(R.id.logoutBtn);
        logoutBtn.setOnClickListener(v -> logout());
    }

    // NAVIGATION METHOD - FIXED VERSION
    private void navigateToActivity(String activityName) {
        Toast.makeText(this, "Navigating to " + activityName, Toast.LENGTH_SHORT).show();

        try {
            Intent intent = null;

            switch (activityName) {
                case "Specializations":
                    intent = new Intent(this, SpecializationsActivity.class);
                    break;
                case "Years":
                    intent = new Intent(this, YearsActivity.class);
                    break;
                case "Sections":
                    intent = new Intent(this, SectionsActivity.class);
                    break;
                case "Courses":
                    intent = new Intent(this, CourseActivity.class);
                    break;
                case "Subjects":
                    intent = new Intent(this, SubjectActivity.class);
                    break;
                case "Examinees":
                    intent = new Intent(this, StudentActivity.class);
                    break;
            }

            // ✅ DITO MAG-START ANG ACTIVITY
            if (intent != null) {
                startActivity(intent);
                // Optional: Add animation
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }

        } catch (Exception e) {
            Toast.makeText(this, activityName + " activity not available yet", Toast.LENGTH_SHORT).show();
        }
    }

    // HELPER METHODS
    private void toggleSidebar() {
        if (drawerLayout.isDrawerOpen(android.view.Gravity.START)) {
            drawerLayout.closeDrawer(android.view.Gravity.START);
        } else {
            drawerLayout.openDrawer(android.view.Gravity.START);
        }
    }

    // FIXED LOGOUT METHOD - Same as AdminActivity
    private void logout() {
        // Close sidebar first
        if (drawerLayout.isDrawerOpen(android.view.Gravity.START)) {
            drawerLayout.closeDrawer(android.view.Gravity.START);
        }

        // Clear session AND sign out from Firebase
        sessionManager = new SessionManager(this);
        sessionManager.clearSession();
        auth.signOut();

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        // Redirect to login screen
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupFirebase() {
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        auth = FirebaseAuth.getInstance();
    }

    private void setupRecyclerViews() {
        recyclerTeachers.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupClickListeners() {
        btnAddTeacher.setOnClickListener(v -> {
            showAddTeacherDialog();
        });
    }

    private void loadTeachers() {
        teacherAdapter = new TeacherAdapter(teacherList, new TeacherAdapter.OnTeacherActionListener() {
            @Override
            public void onUpdate(TeacherModel teacher) {
                showEditTeacherDialog(teacher);
            }

            @Override
            public void onDelete(TeacherModel teacher) {
                new AlertDialog.Builder(TeacherActivity.this)
                        .setTitle("Delete Teacher")
                        .setMessage("Are you sure you want to delete " + teacher.getFullName() + "?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            deleteTeacher(teacher);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        recyclerTeachers.setAdapter(teacherAdapter);

        teachersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                teacherList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    TeacherModel t = ds.getValue(TeacherModel.class);
                    if (t != null) {
                        t.setId(ds.getKey());
                        teacherList.add(t);
                    }
                }
                teacherAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeacherActivity.this, "Failed to load teachers", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteTeacher(TeacherModel teacher) {
        teachersRef.child(teacher.getId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(TeacherActivity.this, "Teacher deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(TeacherActivity.this, "Delete failed", Toast.LENGTH_SHORT).show());
    }

    private void loadCourses() {
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel course = ds.getValue(CourseModel.class);
                    if (course != null) {
                        course.setId(ds.getKey());
                        courseOptionList.add(course);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeacherActivity.this, "Failed to load courses", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSubjects() {
        subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                subjectOptionList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    SubjectModel subject = ds.getValue(SubjectModel.class);
                    if (subject != null) {
                        subjectOptionList.add(subject);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeacherActivity.this, "Failed to load subjects", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddTeacherDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Teacher");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_teacher, null);
        builder.setView(dialogView);

        EditText etFirstName = dialogView.findViewById(R.id.etFirstName);
        EditText etLastName = dialogView.findViewById(R.id.etLastName);
        EditText etBirthday = dialogView.findViewById(R.id.etBirthday);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        Spinner spinnerCourse = dialogView.findViewById(R.id.spinnerCourse);
        Spinner spinnerSubject = dialogView.findViewById(R.id.spinnerSubject);
        Button btnSaveTeacher = dialogView.findViewById(R.id.btnSaveTeacher);

        // Setup course spinner
        List<String> courseDisplayList = new ArrayList<>();
        courseDisplayList.add("Select Course");
        for (CourseModel course : courseOptionList) {
            courseDisplayList.add(course.getName() + " - " + course.getSpecializationName());
        }

        ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, courseDisplayList);
        courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCourse.setAdapter(courseAdapter);

        // Setup subject spinner
        List<String> subjectDisplayList = new ArrayList<>();
        subjectDisplayList.add("Select Subject");
        for (SubjectModel subject : subjectOptionList) {
            subjectDisplayList.add(subject.getName());
        }

        ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, subjectDisplayList);
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSubject.setAdapter(subjectAdapter);

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSaveTeacher.setOnClickListener(v -> {
            String firstName = etFirstName.getText().toString().trim();
            String lastName = etLastName.getText().toString().trim();
            String birthday = etBirthday.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String selectedCourse = spinnerCourse.getSelectedItem().toString();
            String selectedSubject = spinnerSubject.getSelectedItem().toString();

            if (TextUtils.isEmpty(firstName)) {
                etFirstName.setError("First name required");
                return;
            }
            if (TextUtils.isEmpty(lastName)) {
                etLastName.setError("Last name required");
                return;
            }
            if (TextUtils.isEmpty(birthday)) {
                etBirthday.setError("Birthday required");
                return;
            }
            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email required");
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Valid email required");
                return;
            }
            if (spinnerCourse.getSelectedItemPosition() == 0) {
                Toast.makeText(this, "Select a course", Toast.LENGTH_SHORT).show();
                return;
            }
            if (spinnerSubject.getSelectedItemPosition() == 0) {
                Toast.makeText(this, "Select a subject", Toast.LENGTH_SHORT).show();
                return;
            }

            String fullName = firstName + " " + lastName;
            addTeacherToDatabase(fullName, birthday, email, selectedCourse, selectedSubject, dialog);
        });
    }

    private void addTeacherToDatabase(String fullName, String birthday, String email,
                                      String selectedCourse, String selectedSubject, AlertDialog dialog) {
        generateTeacherId(teacherId -> {
            String password = birthday.replaceAll("[^0-9]", "");
            if (password.length() < 6) password = "123456";

            List<String> courseIds = new ArrayList<>();
            List<String> courseDisplays = new ArrayList<>();
            for (CourseModel course : courseOptionList) {
                String courseDisplay = course.getName() + " - " + course.getSpecializationName();
                if (courseDisplay.equals(selectedCourse)) {
                    courseIds.add(course.getId());
                    courseDisplays.add(courseDisplay);
                    break;
                }
            }

            List<String> assignedSubjects = new ArrayList<>();
            assignedSubjects.add(selectedSubject);

            TeacherModel teacher = new TeacherModel(
                    teacherId, fullName, getDisplayName(fullName), birthday, email,
                    courseIds, courseDisplays, assignedSubjects, password
            );

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(authTask -> {
                        if (authTask.isSuccessful()) {
                            FirebaseUser firebaseUser = authTask.getResult().getUser();
                            if (firebaseUser != null) {
                                usersRef.child(firebaseUser.getUid()).child("role").setValue("teacher");
                                teachersRef.child(teacherId).setValue(teacher)
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "Teacher added", Toast.LENGTH_SHORT).show();
                                            dialog.dismiss();
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
                                        });
                            }
                        } else {
                            Toast.makeText(this, "Create user failed", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private void showEditTeacherDialog(TeacherModel teacher) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Teacher");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_teacher_edit, null);
        builder.setView(dialogView);

        EditText etEditFullName = dialogView.findViewById(R.id.etEditFullName);
        EditText etEditBirthday = dialogView.findViewById(R.id.etEditBirthday);
        EditText etEditEmail = dialogView.findViewById(R.id.etEditEmail);
        Spinner spinnerEditCourse = dialogView.findViewById(R.id.spinnerEditCourse);
        Spinner spinnerEditSubject = dialogView.findViewById(R.id.spinnerEditSubject);
        Button btnUpdateTeacher = dialogView.findViewById(R.id.btnUpdateTeacher);

        etEditFullName.setText(teacher.getFullName());
        etEditBirthday.setText(teacher.getBirthday());
        etEditEmail.setText(teacher.getEmail());

        // Setup course spinner
        List<String> courseDisplayList = new ArrayList<>();
        courseDisplayList.add("Select Course");
        for (CourseModel course : courseOptionList) {
            courseDisplayList.add(course.getName() + " - " + course.getSpecializationName());
        }

        ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, courseDisplayList);
        courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEditCourse.setAdapter(courseAdapter);

        if (teacher.getCourseDisplays() != null && !teacher.getCourseDisplays().isEmpty()) {
            String currentCourse = teacher.getCourseDisplays().get(0);
            for (int i = 0; i < courseDisplayList.size(); i++) {
                if (courseDisplayList.get(i).equals(currentCourse)) {
                    spinnerEditCourse.setSelection(i);
                    break;
                }
            }
        }

        // Setup subject spinner
        List<String> subjectDisplayList = new ArrayList<>();
        subjectDisplayList.add("Select Subject");
        for (SubjectModel subject : subjectOptionList) {
            subjectDisplayList.add(subject.getName());
        }

        ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, subjectDisplayList);
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEditSubject.setAdapter(subjectAdapter);

        if (teacher.getAssignedSubjects() != null && !teacher.getAssignedSubjects().isEmpty()) {
            String currentSubject = teacher.getAssignedSubjects().get(0);
            for (int i = 0; i < subjectDisplayList.size(); i++) {
                if (subjectDisplayList.get(i).equals(currentSubject)) {
                    spinnerEditSubject.setSelection(i);
                    break;
                }
            }
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        btnUpdateTeacher.setOnClickListener(v -> {
            String fullName = etEditFullName.getText().toString().trim();
            String birthday = etEditBirthday.getText().toString().trim();
            String email = etEditEmail.getText().toString().trim();
            String selectedCourse = spinnerEditCourse.getSelectedItem().toString();
            String selectedSubject = spinnerEditSubject.getSelectedItem().toString();

            if (TextUtils.isEmpty(fullName)) {
                etEditFullName.setError("Full name required");
                return;
            }
            if (TextUtils.isEmpty(birthday)) {
                etEditBirthday.setError("Birthday required");
                return;
            }
            if (TextUtils.isEmpty(email)) {
                etEditEmail.setError("Email required");
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEditEmail.setError("Valid email required");
                return;
            }
            if (spinnerEditCourse.getSelectedItemPosition() == 0) {
                Toast.makeText(this, "Select a course", Toast.LENGTH_SHORT).show();
                return;
            }
            if (spinnerEditSubject.getSelectedItemPosition() == 0) {
                Toast.makeText(this, "Select a subject", Toast.LENGTH_SHORT).show();
                return;
            }

            updateTeacher(teacher, fullName, birthday, email, selectedCourse, selectedSubject, dialog);
        });
    }

    private void updateTeacher(TeacherModel teacher, String fullName, String birthday,
                               String email, String selectedCourse, String selectedSubject, AlertDialog dialog) {
        final String oldEmail = teacher.getEmail();
        final String oldPassword = teacher.getPassword();
        final String newEmail = email;
        final String newPassword = birthday.replaceAll("[^0-9]", "").length() < 6 ? "123456" : birthday.replaceAll("[^0-9]", "");

        teacher.setFullName(fullName);
        teacher.setBirthday(birthday);
        teacher.setEmail(email);
        teacher.setDisplayName(getDisplayName(fullName));

        List<String> courseIds = new ArrayList<>();
        List<String> courseDisplays = new ArrayList<>();
        for (CourseModel course : courseOptionList) {
            String courseDisplay = course.getName() + " - " + course.getSpecializationName();
            if (courseDisplay.equals(selectedCourse)) {
                courseIds.add(course.getId());
                courseDisplays.add(courseDisplay);
                break;
            }
        }
        teacher.setCourseIds(courseIds);
        teacher.setCourseDisplays(courseDisplays);

        List<String> assignedSubjects = new ArrayList<>();
        assignedSubjects.add(selectedSubject);
        teacher.setAssignedSubjects(assignedSubjects);
        teacher.setPassword(newPassword);

        teachersRef.child(teacher.getId()).setValue(teacher)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Teacher updated", Toast.LENGTH_SHORT).show();
                    if (!oldEmail.equals(newEmail) || !oldPassword.equals(newPassword)) {
                        updateAuthCredentials(oldEmail, oldPassword, newEmail, newPassword);
                    }
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateAuthCredentials(final String oldEmail, final String oldPassword,
                                       final String newEmail, final String newPassword) {
        auth.signInWithEmailAndPassword(oldEmail, oldPassword)
                .addOnCompleteListener(signInTask -> {
                    if (signInTask.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            if (!oldEmail.equals(newEmail)) {
                                user.updateEmail(newEmail);
                            }
                            if (!oldPassword.equals(newPassword)) {
                                user.updatePassword(newPassword);
                            }
                        }
                    }
                });
    }

    private String getDisplayName(String fullName) {
        String[] parts = fullName.split(" ");
        if (parts.length >= 2) {
            return parts[0].charAt(0) + "." + parts[parts.length - 1];
        }
        return fullName;
    }

    private void generateTeacherId(OnIdGeneratedListener listener) {
        teachersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Integer> numbers = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String id = ds.getKey();
                    if (id != null && id.startsWith("TCHR-")) {
                        try {
                            int num = Integer.parseInt(id.replace("TCHR-", ""));
                            numbers.add(num);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                int newNum = 1;
                while (numbers.contains(newNum)) newNum++;
                listener.onGenerated(String.format("TCHR-%04d", newNum));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onGenerated("TCHR-" + System.currentTimeMillis());
            }
        });
    }

    interface OnIdGeneratedListener {
        void onGenerated(String teacherId);
    }
}