package com.example.nextgen.admin;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.android.material.navigation.NavigationView;
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

public class TeacherActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    private RecyclerView recyclerTeachers;
    private Button btnAddTeacher;

    // NEW UI ELEMENTS
    private TextView tvTeacherCount;
    private LinearLayout emptyState;
    private EditText etSearchTeacher;
    private ImageButton btnClearSearch;
    private Button btnSort;

    private List<TeacherModel> teacherList = new ArrayList<>();
    private List<CourseModel> courseOptionList = new ArrayList<>();

    private DatabaseReference teachersRef, coursesRef, subjectsRef, usersRef;
    private FirebaseAuth auth;

    private TeacherAdapter teacherAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher);

        // Initialize Toolbar and Navigation
        initializeToolbarAndNavigation();

        // Initialize activity views
        recyclerTeachers = findViewById(R.id.recyclerTeachers);
        btnAddTeacher = findViewById(R.id.btnAddTeacher);

        recyclerTeachers.setLayoutManager(new LinearLayoutManager(this));

        // Firebase refs
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        auth = FirebaseAuth.getInstance();

        // Initialize new UI elements
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
                updateTeacherCount();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        loadCourses();

        btnAddTeacher.setOnClickListener(v -> showAddTeacherDialog());
    }

    private void initializeToolbarAndNavigation() {
        // Setup Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Setup Drawer Layout and Navigation
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Setup toggle button
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Set navigation item selected listener
        navigationView.setNavigationItemSelectedListener(this);

        // Highlight current menu item
        navigationView.setCheckedItem(R.id.nav_teachers);
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

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        // Close drawer first
        drawerLayout.closeDrawer(GravityCompat.START);

        // Handle navigation item clicks
        if (id == R.id.nav_dashboard) {
            startActivity(new Intent(this, AdminActivity.class));
            finish();
        } else if (id == R.id.nav_specializations) {
            startActivity(new Intent(this, SpecializationsActivity.class));
        } else if (id == R.id.nav_years) {
            startActivity(new Intent(this, YearsActivity.class));
        } else if (id == R.id.nav_sections) {
            startActivity(new Intent(this, SectionsActivity.class));
        } else if (id == R.id.nav_courses) {
            startActivity(new Intent(this, CourseActivity.class));
        } else if (id == R.id.nav_subjects) {
            startActivity(new Intent(this, SubjectActivity.class));
        } else if (id == R.id.nav_teachers) {
            // We're already in TeacherActivity
            // Just close the drawer
        } else if (id == R.id.nav_students) {
            startActivity(new Intent(this, StudentActivity.class));
        } else if (id == R.id.nav_logout) {
            performLogout();
        }

        return true;
    }

    private void performLogout() {
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
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
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
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showAddTeacherDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_teacher, null);

        EditText etFullNameDialog = dialogView.findViewById(R.id.etFullName);
        EditText etBirthdayDialog = dialogView.findViewById(R.id.etBirthday);
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

        // Load ALL subjects immediately (walang course selection muna)
        List<SubjectModel> allSubjects = new ArrayList<>();
        SubjectSelectionAdapter subjectAdapterDialog = new SubjectSelectionAdapter(allSubjects);
        recyclerSubjectsDialog.setAdapter(subjectAdapterDialog);

        // Load all subjects from Firebase
        loadAllSubjects(subjectAdapterDialog);

        // Magkaroon ng option para i-filter ang subjects base sa selected courses
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

        // Use standard AlertDialog with built-in buttons
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

                        // Add null for uid parameter
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
                                null  // uid will be set after Firebase auth creation
                        );

                        auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener(authTask -> {
                                    if (authTask.isSuccessful()) {
                                        FirebaseUser firebaseUser = authTask.getResult().getUser();
                                        // Set the uid after user creation
                                        teacher.setUid(firebaseUser.getUid());

                                        usersRef.child(firebaseUser.getUid()).child("role").setValue("teacher");
                                        teachersRef.child(teacherId).setValue(teacher)
                                                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Teacher added", Toast.LENGTH_SHORT).show())
                                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save teacher: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                    } else {
                                        Toast.makeText(this, "Auth failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Method para i-load ang lahat ng subjects
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

    // Method para i-filter ang subjects base sa selected courses
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

    // DAGDAG: ADD THIS MISSING METHOD - ITO ANG SOLUTION SA ERROR
    public void addTeacher(View view) {
        showAddTeacherDialog();
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

    // Teacher edit dialog
    private void showTeacherDialog(TeacherModel teacher) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_teacher_edit, null);

        EditText etEditFullName = dialogView.findViewById(R.id.etEditFullName);
        EditText etEditBirthday = dialogView.findViewById(R.id.etEditBirthday);
        EditText etEditEmail = dialogView.findViewById(R.id.etEditEmail);
        RecyclerView recyclerEditCourses = dialogView.findViewById(R.id.recyclerEditCourses);
        RecyclerView recyclerEditSubjects = dialogView.findViewById(R.id.recyclerEditSubjects);

        etEditFullName.setText(teacher.getFullName());
        etEditBirthday.setText(teacher.getBirthday());
        etEditEmail.setText(teacher.getEmail());

        // ========== CALENDAR PICKER FOR EDIT DIALOG ==========
        etEditBirthday.setFocusable(false);
        etEditBirthday.setOnClickListener(v -> showDatePickerDialog(etEditBirthday));

        List<CourseModel> editCourseList = new ArrayList<>();
        CourseSelectionAdapter editCourseAdapter = new CourseSelectionAdapter(this, editCourseList);
        recyclerEditCourses.setLayoutManager(new LinearLayoutManager(this));
        recyclerEditCourses.setAdapter(editCourseAdapter);

        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                editCourseList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) editCourseList.add(c);
                }
                editCourseAdapter.notifyDataSetChanged();

                // Preselect teacher courses
                if (teacher.getCourseIds() != null) {
                    editCourseAdapter.setPreselectedCoursesById(teacher.getCourseIds());
                }

                // Load all subjects immediately for edit dialog
                SubjectSelectionAdapter editSubjectAdapter = new SubjectSelectionAdapter(new ArrayList<>());
                recyclerEditSubjects.setLayoutManager(new LinearLayoutManager(TeacherActivity.this));
                recyclerEditSubjects.setAdapter(editSubjectAdapter);
                loadAllSubjects(editSubjectAdapter);

                // Preselect subjects based on teacher's current assigned subjects
                editSubjectAdapter.setPreselectedSubjects(teacher.getAssignedSubjects());

                // Update subjects filtering based on course selection
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
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        // Remove the title from AlertDialog since it's already in the layout XML
        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Update", (dialog, which) -> {
                    String oldEmail = teacher.getEmail();
                    String oldBirthdayPassword = teacher.getBirthday().replaceAll("[^0-9]", "");

                    teacher.setFullName(etEditFullName.getText().toString().trim());
                    teacher.setBirthday(etEditBirthday.getText().toString().trim());
                    teacher.setEmail(etEditEmail.getText().toString().trim());
                    teacher.setDisplayName(getDisplayName(teacher.getFullName()));

                    // Courses
                    List<CourseModel> updatedCourses = editCourseAdapter.getSelectedCourses();
                    List<String> courseIds = new ArrayList<>();
                    List<String> courseDisplays = new ArrayList<>();
                    for (CourseModel c : updatedCourses) {
                        courseIds.add(c.getId());
                        courseDisplays.add(
                                c.getName() + " - " +
                                        c.getSpecializationName() + " - " +
                                        c.getYearName() + " - " +
                                        c.getSectionName()
                        );
                    }
                    teacher.setCourseIds(courseIds);
                    teacher.setCourseDisplays(courseDisplays);

                    // Subjects
                    List<String> updatedSubjects = new ArrayList<>();
                    RecyclerView.Adapter<?> adapter = recyclerEditSubjects.getAdapter();
                    if (adapter instanceof SubjectSelectionAdapter) {
                        SubjectSelectionAdapter editSubjectAdapter = (SubjectSelectionAdapter) adapter;
                        for (SubjectModel s : editSubjectAdapter.getSelectedSubjects()) {
                            updatedSubjects.add(s.getName());
                        }
                    }
                    teacher.setAssignedSubjects(updatedSubjects);

                    // Save to Firebase
                    teachersRef.child(teacher.getId()).setValue(teacher)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Teacher updated", Toast.LENGTH_SHORT).show();

                                // Update Auth email/password if changed
                                if (!oldEmail.equals(teacher.getEmail()) || !oldBirthdayPassword.equals(teacher.getBirthday().replaceAll("[^0-9]", ""))) {
                                    auth.signInWithEmailAndPassword(oldEmail, oldBirthdayPassword)
                                            .addOnCompleteListener(signInTask -> {
                                                if (signInTask.isSuccessful()) {
                                                    FirebaseUser user = auth.getCurrentUser();
                                                    if (user != null) {
                                                        if (!oldEmail.equals(teacher.getEmail()))
                                                            user.updateEmail(teacher.getEmail());
                                                        if (!oldBirthdayPassword.equals(teacher.getBirthday().replaceAll("[^0-9]", "")))
                                                            user.updatePassword(teacher.getBirthday().replaceAll("[^0-9]", ""));
                                                    }
                                                }
                                            });
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}