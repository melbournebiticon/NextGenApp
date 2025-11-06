package com.example.nextgen.admin;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.ImageView;
import android.content.Intent;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;   // ← add this
import java.io.ByteArrayOutputStream;
import android.graphics.Bitmap;
import android.util.Base64;
import android.app.DatePickerDialog;
import java.util.Calendar;



import androidx.annotation.NonNull;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class TeacherActivity extends AppCompatActivity {

    // SIDEBAR COMPONENTS
    private DrawerLayout drawerLayout;
    private LinearLayout sidebarLayout;
    private ImageButton btnToggleSidebar;
    private LinearLayout curriculumDropdown, accountsDropdown;

    private EditText etFullName, etBirthday, etEmail;
    private RecyclerView recyclerCourseSelection, recyclerSubjects, recyclerTeachers;
    private Button btnAddTeacher;

    // NEW UI ELEMENTS
    private TextView tvTeacherCount;
    private LinearLayout emptyState;
    private EditText etSearchTeacher;
    private ImageButton btnClearSearch;
    private Button btnSort;

    private List<SubjectModel> selectedCourseSubjects = new ArrayList<>();
    private List<CourseModel> courseOptionList = new ArrayList<>();
    private List<TeacherModel> teacherList = new ArrayList<>();

    private DatabaseReference teachersRef, coursesRef, subjectsRef, usersRef;
    private FirebaseAuth auth;


    private SubjectSelectionAdapter subjectAdapter;
    private TeacherAdapter teacherAdapter;
    private CourseSelectionAdapter courseSelectionAdapter;

<<<<<<< Updated upstream
    private Uri selectedImageUri;
    private ImageView currentEditProfileView;
    private String profileImage; // Base64-encoded profile picture

=======
    // DAGDAG: Sidebar state management
    private boolean isCurriculumExpanded = false;
    private boolean isAccountsExpanded = true; // Default expanded since we're in TeacherActivity
>>>>>>> Stashed changes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher);

        // ========== INITIALIZE SIDEBAR ==========
        initializeSidebar();

        // DAGDAG: Set initial sidebar state
        setInitialSidebarState();

        // Only activity views
        recyclerTeachers = findViewById(R.id.recyclerTeachers);
        btnAddTeacher = findViewById(R.id.btnAddTeacher);

        recyclerTeachers.setLayoutManager(new LinearLayoutManager(this));

        // Firebase refs
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        auth = FirebaseAuth.getInstance();

        // DAGDAG: Initialize new UI elements
        initializeNewUIElements();

        teacherAdapter = new TeacherAdapter(teacherList, new TeacherAdapter.OnTeacherActionListener() {
            @Override
            public void onUpdate(TeacherModel teacher) {
                showTeacherDialog(teacher);
            }

            @Override
            public void onDelete(TeacherModel teacher) {
                new AlertDialog.Builder(TeacherActivity.this)
                        .setTitle("Delete Teacher")
                        .setMessage("Are you sure you want to delete " + teacher.getFullName() + "?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // Delete from "Users"
                            if (teacher.getUid() != null && !teacher.getUid().isEmpty()) {
                                usersRef.child(teacher.getUid()).removeValue()
                                        .addOnSuccessListener(aVoid -> {
                                            // Delete from "Teachers"
                                            teachersRef.child(teacher.getId()).removeValue()
                                                    .addOnSuccessListener(aVoid2 ->
                                                            Toast.makeText(TeacherActivity.this, "Teacher deleted", Toast.LENGTH_SHORT).show()
                                                    )
                                                    .addOnFailureListener(e ->
                                                            Toast.makeText(TeacherActivity.this, "Failed to delete teacher record: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                                    );
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(TeacherActivity.this, "Failed to delete user record: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                        );
                            }
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
                    if (t != null) teacherList.add(t);
                }
                teacherAdapter.notifyDataSetChanged();
                updateTeacherCount(); // Add this line
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        loadCourses();

        btnAddTeacher.setOnClickListener(v -> showAddTeacherDialog());
    }

    // ========== INITIALIZE NEW UI ELEMENTS ==========
    private void initializeNewUIElements() {
        tvTeacherCount = findViewById(R.id.tvTeacherCount);
        emptyState = findViewById(R.id.emptyState);
        etSearchTeacher = findViewById(R.id.etSearchTeacher);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        btnSort = findViewById(R.id.btnSort);

        // Update teacher count
        updateTeacherCount();

        // Setup search functionality
        setupSearchFunctionality();

        // Setup sort functionality
        setupSortFunctionality();
    }

    private void updateTeacherCount() {
        if (tvTeacherCount == null) return;

        String countText = teacherList.size() + " teacher" + (teacherList.size() != 1 ? "s" : "");
        tvTeacherCount.setText(countText);

        // Show/hide empty state
        if (emptyState != null && recyclerTeachers != null) {
            if (teacherList.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                recyclerTeachers.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                recyclerTeachers.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupSearchFunctionality() {
        if (etSearchTeacher == null || btnClearSearch == null) return;

        etSearchTeacher.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Show/hide clear button
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                filterTeachers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearchTeacher.setText("");
            btnClearSearch.setVisibility(View.GONE);
        });
    }

    private void setupSortFunctionality() {
        if (btnSort == null) return;

        btnSort.setOnClickListener(v -> {
            // Toggle sort order
            boolean isAscending = btnSort.getText().toString().equals("A-Z");
            sortTeachers(isAscending);
            btnSort.setText(isAscending ? "Z-A" : "A-Z");
        });
    }

    private void sortTeachers(boolean ascending) {
        Collections.sort(teacherList, (t1, t2) -> {
            int result = t1.getFullName().compareToIgnoreCase(t2.getFullName());
            return ascending ? result : -result;
        });
        teacherAdapter.notifyDataSetChanged();
    }

    private void filterTeachers(String query) {
        List<TeacherModel> filteredList = new ArrayList<>();
        for (TeacherModel teacher : teacherList) {
            if (teacher.getFullName().toLowerCase().contains(query.toLowerCase()) ||
                    teacher.getEmail().toLowerCase().contains(query.toLowerCase())) {
                filteredList.add(teacher);
            }
        }
        teacherAdapter.updateList(filteredList);
        updateTeacherCount();
    }

    // ========== SIDEBAR INITIALIZATION ==========
    private void initializeSidebar() {
        drawerLayout = findViewById(R.id.drawerLayout);
        sidebarLayout = findViewById(R.id.sidebarLayout);
        btnToggleSidebar = findViewById(R.id.btnOpenSidebar);
        curriculumDropdown = findViewById(R.id.curriculumDropdown);
        accountsDropdown = findViewById(R.id.accountsDropdown);

        // Sidebar toggle - ITO ANG MAGPAPAGANA NG MENU BURGER
        btnToggleSidebar.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(sidebarLayout)) {
                drawerLayout.closeDrawer(sidebarLayout);
            } else {
                drawerLayout.openDrawer(sidebarLayout);
            }
        });

        // Setup sidebar navigation
        setupSidebarNavigation();
    }

    // DAGDAG: Method to set initial sidebar state
    private void setInitialSidebarState() {
        // Set Manage Teacher button as active
        Button btnManageTeachers = findViewById(R.id.btnManageTeachers);
        btnManageTeachers.setBackgroundResource(R.drawable.sidebar_button_pressed);

        // Set accounts dropdown as expanded by default
        accountsDropdown.setVisibility(View.VISIBLE);
        Button btnAccountsHeader = findViewById(R.id.btnManageAccountsHeader);
        btnAccountsHeader.setText("👤 Manage Accounts ▴");

        // Set curriculum dropdown as collapsed by default
        curriculumDropdown.setVisibility(View.GONE);
        Button btnCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        btnCurriculumHeader.setText("📘 Manage Curriculum ▾");
    }

    // ========== SIDEBAR NAVIGATION SETUP ==========
    private void setupSidebarNavigation() {
        // Curriculum dropdown - I-DECLARE ITO SA LABAS NG ONCLICK
        final Button btnCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        btnCurriculumHeader.setOnClickListener(v -> {
            if (curriculumDropdown.getVisibility() == View.VISIBLE) {
                curriculumDropdown.setVisibility(View.GONE);
                btnCurriculumHeader.setText("📘 Manage Curriculum ▾");
                isCurriculumExpanded = false;
            } else {
                curriculumDropdown.setVisibility(View.VISIBLE);
                btnCurriculumHeader.setText("📘 Manage Curriculum ▴");
                isCurriculumExpanded = true;

                // DAGDAG: Collapse accounts if needed for consistency
                if (isAccountsExpanded) {
                    accountsDropdown.setVisibility(View.GONE);
                    Button btnAccountsHeader = findViewById(R.id.btnManageAccountsHeader);
                    btnAccountsHeader.setText("👤 Manage Accounts ▾");
                    isAccountsExpanded = false;
                }
            }
        });

        // Accounts dropdown - I-DECLARE ITO SA LABAS NG ONCLICK
        final Button btnAccountsHeader = findViewById(R.id.btnManageAccountsHeader);
        btnAccountsHeader.setOnClickListener(v -> {
            if (accountsDropdown.getVisibility() == View.VISIBLE) {
                accountsDropdown.setVisibility(View.GONE);
                btnAccountsHeader.setText("👤 Manage Accounts ▾");
                isAccountsExpanded = false;
            } else {
                accountsDropdown.setVisibility(View.VISIBLE);
                btnAccountsHeader.setText("👤 Manage Accounts ▴");
                isAccountsExpanded = true;

                // DAGDAG: Collapse curriculum if needed for consistency
                if (isCurriculumExpanded) {
                    curriculumDropdown.setVisibility(View.GONE);
                    btnCurriculumHeader.setText("📘 Manage Curriculum ▾");
                    isCurriculumExpanded = false;
                }
            }
        });

        // Sidebar buttons functionality
        setupSidebarButtons();
    }

    private void setupSidebarButtons() {
        // Curriculum buttons
        Button btnManageSpecializations = findViewById(R.id.btnManageSpecializations);
        Button btnManageYears = findViewById(R.id.btnManageYears);
        Button btnManageSections = findViewById(R.id.btnManageSections);
        Button btnManageCourse = findViewById(R.id.btnManageCourse);
        Button btnManageSubjects = findViewById(R.id.btnManageSubjects);

        // Accounts buttons
        Button btnManageTeachers = findViewById(R.id.btnManageTeachers);
        Button btnManageStudents = findViewById(R.id.btnManageStudents);

        // Set click listeners for sidebar buttons
        btnManageSpecializations.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(TeacherActivity.this, SpecializationsActivity.class));
        });

        btnManageYears.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(TeacherActivity.this, YearsActivity.class));
        });

        btnManageSections.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(TeacherActivity.this, SectionsActivity.class));
        });

        btnManageCourse.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(TeacherActivity.this, CourseActivity.class));
        });

        btnManageSubjects.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(TeacherActivity.this, SubjectActivity.class));
        });

        btnManageTeachers.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            // We're already in TeacherActivity, so just close the drawer
            // DAGDAG: Highlight the active button
            resetSidebarButtonBackgrounds();
            btnManageTeachers.setBackgroundResource(R.drawable.sidebar_button_pressed);
        });

        btnManageStudents.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(TeacherActivity.this, StudentActivity.class));
        });

        // Logout button
        Button logoutBtn = findViewById(R.id.logoutBtn);
        logoutBtn.setOnClickListener(v -> {
            // Clear session
            SessionManager sessionManager = new SessionManager(this);
            sessionManager.clearSession();

            // Sign out from Firebase
            FirebaseAuth.getInstance().signOut();

            // Redirect to login
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        });
    }

    // DAGDAG: Method to reset sidebar button backgrounds
    private void resetSidebarButtonBackgrounds() {
        Button btnManageSpecializations = findViewById(R.id.btnManageSpecializations);
        Button btnManageYears = findViewById(R.id.btnManageYears);
        Button btnManageSections = findViewById(R.id.btnManageSections);
        Button btnManageCourse = findViewById(R.id.btnManageCourse);
        Button btnManageSubjects = findViewById(R.id.btnManageSubjects);
        Button btnManageTeachers = findViewById(R.id.btnManageTeachers);
        Button btnManageStudents = findViewById(R.id.btnManageStudents);

        btnManageSpecializations.setBackgroundResource(android.R.color.transparent);
        btnManageYears.setBackgroundResource(android.R.color.transparent);
        btnManageSections.setBackgroundResource(android.R.color.transparent);
        btnManageCourse.setBackgroundResource(android.R.color.transparent);
        btnManageSubjects.setBackgroundResource(android.R.color.transparent);
        btnManageTeachers.setBackgroundResource(android.R.color.transparent);
        btnManageStudents.setBackgroundResource(android.R.color.transparent);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(sidebarLayout)) {
            drawerLayout.closeDrawer(sidebarLayout);
        } else {
            super.onBackPressed();
        }
    }

    private void loadCourses() {
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel course = ds.getValue(CourseModel.class);
                    if (course != null) courseOptionList.add(course);
                }
                // No need to notify anything here in activity
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showAddTeacherDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_teacher, null);

        EditText etFullNameDialog = dialogView.findViewById(R.id.etFullName);
        EditText etBirthdayDialog = dialogView.findViewById(R.id.etBirthday);
        etBirthdayDialog.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePicker = new DatePickerDialog(TeacherActivity.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formattedDate = String.format("%04d-%02d-%02d",
                                selectedYear, selectedMonth + 1, selectedDay);
                        etBirthdayDialog.setText(formattedDate);
                    }, year, month, day);

            datePicker.show();
        });
        EditText etEmailDialog = dialogView.findViewById(R.id.etEmail);
        RecyclerView recyclerCourseDialog = dialogView.findViewById(R.id.recyclerCourseSelection);
        RecyclerView recyclerSubjectsDialog = dialogView.findViewById(R.id.recyclerSubjects);

        // ========== CALENDAR PICKER FOR BIRTHDAY ==========
        etBirthdayDialog.setFocusable(false);
        etBirthdayDialog.setOnClickListener(v -> showDatePickerDialog(etBirthdayDialog));

        // Setup RecyclerViews
        recyclerCourseDialog.setLayoutManager(new LinearLayoutManager(this));
        recyclerSubjectsDialog.setLayoutManager(new LinearLayoutManager(this));

        // Clone the course list
        List<CourseModel> dialogCourses = new ArrayList<>(courseOptionList);
        CourseSelectionAdapter courseAdapterDialog = new CourseSelectionAdapter(this, dialogCourses);
        recyclerCourseDialog.setAdapter(courseAdapterDialog);

        // ✅ BAGO: Load ALL subjects immediately (walang course selection muna)
        List<SubjectModel> allSubjects = new ArrayList<>();
        SubjectSelectionAdapter subjectAdapterDialog = new SubjectSelectionAdapter(allSubjects);
        recyclerSubjectsDialog.setAdapter(subjectAdapterDialog);

        // ✅ BAGO: Load all subjects from Firebase
        loadAllSubjects(subjectAdapterDialog);

        // ✅ BAGO: Magkaroon ng option para i-filter ang subjects base sa selected courses
        courseAdapterDialog.setOnCourseSelectionChanged(() -> {
            List<CourseModel> selectedCourses = courseAdapterDialog.getSelectedCourses();

            if (selectedCourses.isEmpty()) {
                // Kung walang course na selected, ipakita lahat ng subjects
                loadAllSubjects(subjectAdapterDialog);
            } else {
                // Kung may selected courses, i-filter ang subjects
                filterSubjectsByCourses(selectedCourses, subjectAdapterDialog);
            }
        });

        // FIXED: Use standard AlertDialog with built-in buttons (no custom button handling)
        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String fullName = etFullNameDialog.getText().toString().trim();
                    String birthday = etBirthdayDialog.getText().toString().trim();
                    String email = etEmailDialog.getText().toString().trim();

                    if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(birthday) || TextUtils.isEmpty(email)) {
                        Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<CourseModel> selectedCourses = courseAdapterDialog.getSelectedCourses();
                    if (selectedCourses.isEmpty()) {
                        Toast.makeText(this, "Select at least one course", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<String> assignedSubjects = new ArrayList<>();
                    for (SubjectModel s : subjectAdapterDialog.getSelectedSubjects()) {
                        assignedSubjects.add(s.getName());
                    }

                    // Generate teacher ID and create teacher
                    generateTeacherId(teacherId -> {
                        String[] parts = birthday.split("-"); // [YYYY, MM, DD]
                        String year = parts[0];  // kunin last 2 digits ng year
                        String month = parts[1];
                        String day = parts[2];
                        String password = month + day + year; // MMDDYY format

                        List<String> courseIds = new ArrayList<>();
                        List<String> courseDisplays = new ArrayList<>();
                        for (CourseModel c : selectedCourses) {
                            courseIds.add(c.getId());
                            courseDisplays.add(
                                    c.getName() + " - " +
                                            c.getSpecializationName() + " - " +
                                            c.getYearName() + " - " +
                                            c.getSectionName()

                            );

                        }

                        TeacherModel teacher = new TeacherModel(
                                teacherId,
                                fullName,
                                getDisplayName(fullName),
                                birthday,
                                email,
                                courseIds,
                                courseDisplays,
                                assignedSubjects,
                                password,
                                null
                        );

                        auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener(authTask -> {
                                    if (authTask.isSuccessful()) {
                                        FirebaseUser firebaseUser = authTask.getResult().getUser();
                                        String uid = firebaseUser.getUid(); // ✅ get actual UID
                                        teacher.setUid(uid); // ✅ assign UID to the object

                                        // Save role under "Users"
                                        usersRef.child(uid).child("role").setValue("teacher");

                                        // Save full teacher record
                                        teachersRef.child(teacherId).setValue(teacher)
                                                .addOnSuccessListener(aVoid ->
                                                        Toast.makeText(this, "Teacher added successfully", Toast.LENGTH_SHORT).show()
                                                )
                                                .addOnFailureListener(e ->
                                                        Toast.makeText(this, "Failed to save teacher: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                                );
                                    } else {
                                        Toast.makeText(this, "Auth failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });

                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ✅ BAGO: Method para i-load ang lahat ng subjects
    private void loadAllSubjects(SubjectSelectionAdapter adapter) {
        subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SubjectModel> allSubjects = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    SubjectModel subject = ds.getValue(SubjectModel.class);
                    if (subject != null) {
                        allSubjects.add(subject);
                    }
                }
                adapter.updateSubjects(allSubjects);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeacherActivity.this, "Failed to load subjects", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ✅ BAGO: Method para i-filter ang subjects base sa selected courses
    private void filterSubjectsByCourses(List<CourseModel> selectedCourses, SubjectSelectionAdapter adapter) {
        List<SubjectModel> filteredSubjects = new ArrayList<>();
        final int[] loadedCount = {0};

        for (CourseModel course : selectedCourses) {
            subjectsRef.orderByChild("courseId").equalTo(course.getId())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                SubjectModel subject = ds.getValue(SubjectModel.class);
                                if (subject != null && !filteredSubjects.contains(subject)) {
                                    filteredSubjects.add(subject);
                                }
                            }
                            loadedCount[0]++;
                            if (loadedCount[0] == selectedCourses.size()) {
                                adapter.updateSubjects(filteredSubjects);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            loadedCount[0]++;
                            if (loadedCount[0] == selectedCourses.size()) {
                                adapter.updateSubjects(filteredSubjects);
                            }
                        }
                    });
        }
    }

    // ========== DATE PICKER METHOD ==========
    private void showDatePickerDialog(EditText birthdayField) {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    // Format the date as MM/DD/YYYY
                    String selectedDate = String.format("%02d/%02d/%04d",
                            selectedMonth + 1, selectedDay, selectedYear);
                    birthdayField.setText(selectedDate);
                },
                year, month, day
        );

        // Set maximum date to today (cannot select future dates)
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void updateSelectedSubjects() {
        List<CourseModel> selectedCourses = courseSelectionAdapter.getSelectedCourses();

        selectedCourseSubjects.clear(); // reset subjects
        if (selectedCourses.isEmpty()) {
            subjectAdapter.updateSubjects(selectedCourseSubjects);
            return;
        }

        final int[] loadedCount = {0};
        List<SubjectModel> subjects = new ArrayList<>();

        for (CourseModel c : selectedCourses) {
            subjectsRef.orderByChild("courseId").equalTo(c.getId())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                SubjectModel s = ds.getValue(SubjectModel.class);
                                if (s != null && !subjects.contains(s)) {
                                    subjects.add(s); // just add, do NOT auto-select
                                }
                            }
                            loadedCount[0]++;
                            if (loadedCount[0] == selectedCourses.size()) {
                                selectedCourseSubjects.addAll(subjects);
                                subjectAdapter.updateSubjects(selectedCourseSubjects);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            loadedCount[0]++;
                            if (loadedCount[0] == selectedCourses.size()) {
                                selectedCourseSubjects.addAll(subjects);
                                subjectAdapter.updateSubjects(selectedCourseSubjects);
                            }
                        }
                    });
        }
    }

<<<<<<< Updated upstream
    private void addTeacher() {
        String fullName = etFullName.getText().toString().trim();
        String birthday = etBirthday.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(birthday) || TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        List<CourseModel> selectedCourses = courseSelectionAdapter.getSelectedCourses();
        if (selectedCourses.isEmpty()) {
            Toast.makeText(this, "Select at least one course", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get only selected subjects
        List<String> assignedSubjects = new ArrayList<>();
        for (SubjectModel s : subjectAdapter.getSelectedSubjects()) {
            assignedSubjects.add(s.getName());
        }

        if (assignedSubjects.isEmpty()) {
            Toast.makeText(this, "Select at least one subject", Toast.LENGTH_SHORT).show();
            return;
        }

        generateTeacherId(teacherId -> {
            String password = birthday.replaceAll("[^0-9]", "");

            List<String> courseIds = new ArrayList<>();
            List<String> courseDisplays = new ArrayList<>();
            for (CourseModel c : selectedCourses) {
                courseIds.add(c.getId());
                courseDisplays.add(
                        c.getName() + " - " +
                                c.getSpecializationName() + " - " +
                                c.getYearName() + " - " +
                                c.getSectionName()
                );

            }

            TeacherModel teacher = new TeacherModel(
                    teacherId,
                    fullName,
                    getDisplayName(fullName),
                    birthday,
                    email,
                    courseIds,
                    courseDisplays,
                    assignedSubjects,
                    password,
                    null
            );

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(authTask -> {
                        if (authTask.isSuccessful()) {
                            FirebaseUser firebaseUser = authTask.getResult().getUser();
                            usersRef.child(firebaseUser.getUid()).child("role").setValue("teacher");

                            teachersRef.child(teacherId).setValue(teacher)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this, "Teacher added successfully", Toast.LENGTH_SHORT).show();
                                        etFullName.setText("");
                                        etBirthday.setText("");
                                        etEmail.setText("");
                                        selectedCourseSubjects.clear();
                                        subjectAdapter.updateSubjects(selectedCourseSubjects);
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        } else {
                            Toast.makeText(this, "Auth creation failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
=======
    // DAGDAG: ADD THIS MISSING METHOD - ITO ANG SOLUTION SA ERROR
    public void addTeacher(View view) {
        showAddTeacherDialog();
>>>>>>> Stashed changes
    }

    private void fetchSelectedSubjects(List<CourseModel> selectedCourses, OnSubjectsFetchedListener listener) {
        List<SubjectModel> subjects = new ArrayList<>();
        if (selectedCourses.isEmpty()) {
            listener.onFetched(new ArrayList<>());
            return;
        }

        final int[] loadedCount = {0};
        for (CourseModel c : selectedCourses) {
            subjectsRef.orderByChild("courseId").equalTo(c.getId())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                SubjectModel s = ds.getValue(SubjectModel.class);
                                if (s != null && !subjects.contains(s)) {
                                    // ❌ REMOVE this line: s.setSelected(true);
                                    subjects.add(s);
                                }
                            }
                            loadedCount[0]++;
                            if (loadedCount[0] == selectedCourses.size()) {
                                listener.onFetched(subjectsToNames(subjects));
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            loadedCount[0]++;
                            if (loadedCount[0] == selectedCourses.size()) {
                                listener.onFetched(subjectsToNames(subjects));
                            }
                        }
                    });
        }
    }

    private List<String> subjectsToNames(List<SubjectModel> subjects) {
        List<String> names = new ArrayList<>();
        for (SubjectModel s : subjects) {
            names.add(s.getName());
        }
        return names;
    }

    interface OnSubjectsFetchedListener {
        void onFetched(List<String> assignedSubjects);
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
                Toast.makeText(TeacherActivity.this, "Error generating ID", Toast.LENGTH_SHORT).show();
            }
        });
    }

    interface OnIdGeneratedListener {
        void onGenerated(String teacherId);
    }


    private void showTeacherDialog(TeacherModel teacher) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_teacher_edit, null);

        EditText etEditFullName = dialogView.findViewById(R.id.etEditFullName);
        EditText etEditBirthday = dialogView.findViewById(R.id.etEditBirthday);
        etEditBirthday.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();

            // Preload current birthday if available
            if (!TextUtils.isEmpty(etEditBirthday.getText().toString())) {
                try {
                    String[] parts = etEditBirthday.getText().toString().split("-");
                    int y = Integer.parseInt(parts[0]);
                    int m = Integer.parseInt(parts[1]) - 1; // months start at 0
                    int d = Integer.parseInt(parts[2]);
                    calendar.set(y, m, d);
                } catch (Exception ignored) {}
            }

            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePicker = new DatePickerDialog(TeacherActivity.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formattedDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                        etEditBirthday.setText(formattedDate);
                    }, year, month, day);

            datePicker.show();
        });

        EditText etEditEmail = dialogView.findViewById(R.id.etEditEmail);
        RecyclerView recyclerEditCourses = dialogView.findViewById(R.id.recyclerEditCourses);
        RecyclerView recyclerEditSubjects = dialogView.findViewById(R.id.recyclerEditSubjects);
        ImageView ivEditProfile = dialogView.findViewById(R.id.ivEditProfile);
        ProgressBar progressBar = dialogView.findViewById(R.id.progressBarUpload);
        TextView tvProgress = dialogView.findViewById(R.id.tvUploadProgress);

        etEditFullName.setText(teacher.getFullName());
        etEditBirthday.setText(teacher.getBirthday());
        etEditEmail.setText(teacher.getEmail());

<<<<<<< Updated upstream
        // Load profile image
        if (teacher.getProfileImage() != null && !teacher.getProfileImage().isEmpty()) {
            byte[] decodedBytes = Base64.decode(teacher.getProfileImage(), Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            ivEditProfile.setImageBitmap(bitmap);
        } else {
            ivEditProfile.setImageResource(R.drawable.examinee_default);
        }

        currentEditProfileView = ivEditProfile;

        ivEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, 202);
        });

        // Setup recycler views
=======
        // ========== CALENDAR PICKER FOR EDIT DIALOG ==========
        etEditBirthday.setFocusable(false);
        etEditBirthday.setOnClickListener(v -> showDatePickerDialog(etEditBirthday));

>>>>>>> Stashed changes
        List<CourseModel> editCourseList = new ArrayList<>();
        CourseSelectionAdapter editCourseAdapter = new CourseSelectionAdapter(this, editCourseList);
        recyclerEditCourses.setLayoutManager(new LinearLayoutManager(this));
        recyclerEditCourses.setAdapter(editCourseAdapter);

        SubjectSelectionAdapter editSubjectAdapter = new SubjectSelectionAdapter(new ArrayList<>());
        recyclerEditSubjects.setLayoutManager(new LinearLayoutManager(this));
        recyclerEditSubjects.setAdapter(editSubjectAdapter);

        // Load all courses first
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                editCourseList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) editCourseList.add(c);
                }
                editCourseAdapter.notifyDataSetChanged();

                if (teacher.getCourseIds() != null)
                    editCourseAdapter.setPreselectedCoursesById(teacher.getCourseIds());

<<<<<<< Updated upstream
                // Load subjects of existing courses
                if (teacher.getCourseIds() != null && !teacher.getCourseIds().isEmpty()) {
                    final List<SubjectModel> loadedSubjects = new ArrayList<>();
                    final int[] loadedCount = {0};

                    for (String courseId : teacher.getCourseIds()) {
                        subjectsRef.orderByChild("courseId").equalTo(courseId)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        for (DataSnapshot ds : snapshot.getChildren()) {
                                            SubjectModel s = ds.getValue(SubjectModel.class);
                                            if (s != null && !loadedSubjects.contains(s))
                                                loadedSubjects.add(s);
                                        }
                                        loadedCount[0]++;
                                        if (loadedCount[0] == teacher.getCourseIds().size()) {
                                            editSubjectAdapter.updateSubjects(loadedSubjects);
                                            editSubjectAdapter.setPreselectedSubjects(teacher.getAssignedSubjects());
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {}
                                });
                    }
                }
=======
                // ✅ BAGO: Load all subjects immediately for edit dialog
                SubjectSelectionAdapter editSubjectAdapter = new SubjectSelectionAdapter(new ArrayList<>());
                recyclerEditSubjects.setLayoutManager(new LinearLayoutManager(TeacherActivity.this));
                recyclerEditSubjects.setAdapter(editSubjectAdapter);
                loadAllSubjects(editSubjectAdapter);

                // ✅ BAGO: Preselect subjects based on teacher's current assigned subjects
                editSubjectAdapter.setPreselectedSubjects(teacher.getAssignedSubjects());

                // ✅ BAGO: Update subjects filtering based on course selection
                editCourseAdapter.setOnCourseSelectionChanged(() -> {
                    List<CourseModel> selectedCourses = editCourseAdapter.getSelectedCourses();
                    if (selectedCourses.isEmpty()) {
                        loadAllSubjects(editSubjectAdapter);
                    } else {
                        filterSubjectsByCourses(selectedCourses, editSubjectAdapter);
                    }
                    // I-preselect ulit ang subjects after filtering
                    editSubjectAdapter.setPreselectedSubjects(teacher.getAssignedSubjects());
                });

                editCourseAdapter.notifySelectionChanged();
>>>>>>> Stashed changes
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

<<<<<<< Updated upstream
        // 🔹 Course selection listener (dynamically update subjects)
        editCourseAdapter.setOnCourseSelectionChanged(() -> {
            List<CourseModel> selectedCourses = editCourseAdapter.getSelectedCourses();
            List<SubjectModel> combinedSubjects = new ArrayList<>();
            final int[] loadedCount = {0};

            if (selectedCourses.isEmpty()) {
                editSubjectAdapter.updateSubjects(new ArrayList<>());
                return;
            }

            for (CourseModel c : selectedCourses) {
                subjectsRef.orderByChild("courseId").equalTo(c.getId())
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                for (DataSnapshot ds : snapshot.getChildren()) {
                                    SubjectModel s = ds.getValue(SubjectModel.class);
                                    if (s != null && !combinedSubjects.contains(s))
                                        combinedSubjects.add(s);
                                }
                                loadedCount[0]++;
                                if (loadedCount[0] == selectedCourses.size()) {
                                    editSubjectAdapter.updateSubjects(combinedSubjects);
                                    editSubjectAdapter.setPreselectedSubjects(teacher.getAssignedSubjects());
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
            }
        });

        // ✅ Show the actual dialog here
=======
        // FIXED: Remove the title from AlertDialog since it's already in the layout XML
>>>>>>> Stashed changes
        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Update", (dialog, which) -> {
                    updateTeacherData(teacher, etEditFullName, etEditBirthday, etEditEmail, editCourseAdapter, editSubjectAdapter);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
<<<<<<< Updated upstream



    private void updateTeacherData(
            TeacherModel teacher,
            EditText etEditFullName,
            EditText etEditBirthday,
            EditText etEditEmail,
            CourseSelectionAdapter editCourseAdapter,
            SubjectSelectionAdapter editSubjectAdapter
    ) {
        String oldEmail = teacher.getEmail();
        String oldPassword = teacher.getBirthday().replaceAll("[^0-9]", "");

        teacher.setFullName(etEditFullName.getText().toString().trim());
        teacher.setBirthday(etEditBirthday.getText().toString().trim());
        teacher.setEmail(etEditEmail.getText().toString().trim());
        teacher.setDisplayName(getDisplayName(teacher.getFullName()));

        if (selectedImageUri != null) {
            String base64Image = convertImageToBase64(selectedImageUri);
            if (base64Image != null)
                teacher.setProfileImage(base64Image);
        }

        List<CourseModel> updatedCourses = editCourseAdapter.getSelectedCourses();
        List<String> courseIds = new ArrayList<>();
        List<String> courseDisplays = new ArrayList<>();
        for (CourseModel c : updatedCourses) {
            courseIds.add(c.getId());
            courseDisplays.add(c.getName() + " - " + c.getSpecializationName() + " - " + c.getYearName() + " - " + c.getSectionName());
        }
        teacher.setCourseIds(courseIds);
        teacher.setCourseDisplays(courseDisplays);

        List<String> updatedSubjects = new ArrayList<>();
        for (SubjectModel s : editSubjectAdapter.getSelectedSubjects()) {
            updatedSubjects.add(s.getName());
        }
        teacher.setAssignedSubjects(updatedSubjects);

        teachersRef.child(teacher.getId()).setValue(teacher)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Teacher updated", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 202 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            if (currentEditProfileView != null) {
                currentEditProfileView.setImageURI(selectedImageUri);
            }
        }
    }
    private String convertImageToBase64(Uri imageUri) {
        try {
            Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            int maxSize = 400;
            int width = original.getWidth();
            int height = original.getHeight();
            float scale = Math.min((float) maxSize / width, (float) maxSize / height);
            int newWidth = Math.round(width * scale);
            int newHeight = Math.round(height * scale);
            Bitmap resized = Bitmap.createScaledBitmap(original, newWidth, newHeight, true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resized.compress(Bitmap.CompressFormat.JPEG, 60, baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.encodeToString(imageBytes, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }



}

=======
}
>>>>>>> Stashed changes
