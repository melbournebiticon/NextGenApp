package com.example.nextgen.admin;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
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
import com.google.firebase.database.*;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StudentActivity extends AppCompatActivity {
    // DAGDAG: SIDEBAR COMPONENTS
    private DrawerLayout drawerLayout;
    private LinearLayout sidebarLayout;
    private ImageButton btnToggleSidebar;
    private LinearLayout curriculumDropdown, accountsDropdown;

    // DAGDAG: Sidebar state management
    private boolean isCurriculumExpanded = false;
    private boolean isAccountsExpanded = true;

    // ORIGINAL COMPONENTS - WALANG BINAGO
    private Spinner spEditCourse;
    private Uri selectedImageUri;
    private ImageView currentEditProfileView;

    private EditText etFullName, etBirthday, etEmail, etContact;
    private Spinner spinnerCourses;
    private RecyclerView recyclerStudents;
    private Button btnAddStudent;

    // NEW: UI Components for enhanced layout
    private TextView tvStudentCount;
    private LinearLayout emptyState;
    private Button btnSort;
    private EditText etSearchStudent;
    private ImageButton btnClearSearch;

    private List<CourseModel> courseOptionList = new ArrayList<>();
    private List<StudentModel> studentList = new ArrayList<>();
    private List<StudentModel> filteredStudentList = new ArrayList<>();

    private DatabaseReference studentsRef, coursesRef, usersRef;
    private FirebaseAuth auth;

    private StudentAdapter studentAdapter;
    private boolean isAscending = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student);

        // DAGDAG: INITIALIZE SIDEBAR
        initializeSidebar();
        setInitialSidebarState();

        // ORIGINAL CODE - WALANG BINAGO
        recyclerStudents = findViewById(R.id.recyclerStudents);
        btnAddStudent = findViewById(R.id.btnAddStudent);

        // NEW: Initialize enhanced UI components
        tvStudentCount = findViewById(R.id.tvStudentCount);
        emptyState = findViewById(R.id.emptyState);
        btnSort = findViewById(R.id.btnSort);
        etSearchStudent = findViewById(R.id.etSearchStudent);
        btnClearSearch = findViewById(R.id.btnClearSearch);

        recyclerStudents.setLayoutManager(new LinearLayoutManager(this));

        // Firebase
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        auth = FirebaseAuth.getInstance();

        studentAdapter = new StudentAdapter(filteredStudentList, new StudentAdapter.OnStudentActionListener() {
            @Override
            public void onUpdate(StudentModel student) {
                showEditStudentDialog(student); // CHANGED: Edit functionality instead of just view
            }

            @Override
            public void onDelete(StudentModel student) {
                new AlertDialog.Builder(StudentActivity.this)
                        .setTitle("Delete Examinee")
                        .setMessage("Are you sure you want to delete " + student.getFullName() + "?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // First, delete from Users node
                            if (student.getUid() != null && !student.getUid().isEmpty()) {
                                usersRef.child(student.getUid()).removeValue()
                                        .addOnSuccessListener(aVoid -> {
                                            // Optional: Delete from Firebase Auth (only if admin has access)
                                            FirebaseUser currentUser = auth.getCurrentUser();
                                            if (currentUser != null && currentUser.getUid().equals(student.getUid())) {
                                                currentUser.delete()
                                                        .addOnSuccessListener(aVoid2 ->
                                                                Toast.makeText(StudentActivity.this, "Auth user deleted", Toast.LENGTH_SHORT).show())
                                                        .addOnFailureListener(e ->
                                                                Toast.makeText(StudentActivity.this, "Auth delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                            }
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(StudentActivity.this, "Failed to delete user record: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                            }

                            // Then, delete from Students node
                            studentsRef.child(student.getStudentId()).removeValue()
                                    .addOnSuccessListener(aVoid ->
                                            Toast.makeText(StudentActivity.this, "Examinee deleted", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e ->
                                            Toast.makeText(StudentActivity.this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        recyclerStudents.setAdapter(studentAdapter);

        // NEW: Setup enhanced UI functionality
        setupEnhancedUI();

        // Load students
        studentsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                studentList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StudentModel s = ds.getValue(StudentModel.class);
                    if (s != null) studentList.add(s);
                }
                filterAndSortStudents();
                updateUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        loadCourses();

        // CHANGED: Show popup dialog instead of direct form
        btnAddStudent.setOnClickListener(v -> showAddStudentDialog());
    }

    // NEW: Setup enhanced UI functionality
    private void setupEnhancedUI() {
        // Sort button functionality
        btnSort.setOnClickListener(v -> {
            isAscending = !isAscending;
            btnSort.setText(isAscending ? "A-Z" : "Z-A");
            filterAndSortStudents();
        });

        // Search functionality
        etSearchStudent.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAndSortStudents();
                btnClearSearch.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Clear search functionality
        btnClearSearch.setOnClickListener(v -> {
            etSearchStudent.setText("");
            btnClearSearch.setVisibility(View.GONE);
        });
    }

    // NEW: Filter and sort students
    private void filterAndSortStudents() {
        String searchQuery = etSearchStudent.getText().toString().trim().toLowerCase();

        filteredStudentList.clear();

        if (TextUtils.isEmpty(searchQuery)) {
            filteredStudentList.addAll(studentList);
        } else {
            for (StudentModel student : studentList) {
                if (student.getFullName().toLowerCase().contains(searchQuery) ||
                        student.getEmail().toLowerCase().contains(searchQuery) ||
                        student.getStudentId().toLowerCase().contains(searchQuery) ||
                        student.getCourseName().toLowerCase().contains(searchQuery)) {
                    filteredStudentList.add(student);
                }
            }
        }

        // Sort by name
        Collections.sort(filteredStudentList, new Comparator<StudentModel>() {
            @Override
            public int compare(StudentModel s1, StudentModel s2) {
                return isAscending ?
                        s1.getFullName().compareToIgnoreCase(s2.getFullName()) :
                        s2.getFullName().compareToIgnoreCase(s1.getFullName());
            }
        });

        studentAdapter.notifyDataSetChanged();
    }

    // NEW: Update UI with count and empty state
    private void updateUI() {
        int count = filteredStudentList.size();
        tvStudentCount.setText(count + " examinee" + (count != 1 ? "s" : ""));

        if (filteredStudentList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerStudents.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerStudents.setVisibility(View.VISIBLE);
        }
    }

    // DAGDAG: Show Add Student Dialog
    private void showAddStudentDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_student, null);

        EditText etFullNameDialog = dialogView.findViewById(R.id.etFullName);
        EditText etBirthdayDialog = dialogView.findViewById(R.id.etBirthday);
        EditText etEmailDialog = dialogView.findViewById(R.id.etEmail);
        EditText etContactDialog = dialogView.findViewById(R.id.etContact);
        Spinner spinnerCoursesDialog = dialogView.findViewById(R.id.spinnerCourses);

        // CHANGED: Calendar picker for birthday
        etBirthdayDialog.setFocusable(false);
        etBirthdayDialog.setOnClickListener(v -> showDatePickerDialog(etBirthdayDialog));

        // Load courses into spinner
        ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCoursesDialog.setAdapter(courseAdapter);

        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> displayNames = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) {
                        displayNames.add(c.getName() + " - " +
                                c.getSpecializationName() + " - " +
                                c.getYearName() + " - " +
                                c.getSectionName());
                    }
                }
                courseAdapter.clear();
                courseAdapter.addAll(displayNames);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        new AlertDialog.Builder(this)
                .setTitle("Add Examinee")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String fullName = etFullNameDialog.getText().toString().trim();
                    String birthday = etBirthdayDialog.getText().toString().trim();
                    String email = etEmailDialog.getText().toString().trim();
                    String contact = etContactDialog.getText().toString().trim();
                    int coursePos = spinnerCoursesDialog.getSelectedItemPosition();

                    if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(birthday) ||
                            TextUtils.isEmpty(email) || TextUtils.isEmpty(contact) || coursePos < 0) {
                        Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    CourseModel selectedCourse = courseOptionList.get(coursePos);
                    addStudentToDatabase(fullName, birthday, email, contact, selectedCourse);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // CHANGED: Edit student dialog instead of just view
    private void showEditStudentDialog(StudentModel student) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_student, null);

        EditText etFullNameDialog = dialogView.findViewById(R.id.etFullName);
        EditText etBirthdayDialog = dialogView.findViewById(R.id.etBirthday);
        EditText etEmailDialog = dialogView.findViewById(R.id.etEmail);
        EditText etContactDialog = dialogView.findViewById(R.id.etContact);
        Spinner spinnerCoursesDialog = dialogView.findViewById(R.id.spinnerCourses);

        // Pre-fill existing data
        etFullNameDialog.setText(student.getFullName());
        etBirthdayDialog.setText(student.getBirthday());
        etEmailDialog.setText(student.getEmail());
        etContactDialog.setText(student.getContact());

        // Calendar picker for birthday
        etBirthdayDialog.setFocusable(false);
        etBirthdayDialog.setOnClickListener(v -> showDatePickerDialog(etBirthdayDialog));

        // Load courses into spinner
        ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCoursesDialog.setAdapter(courseAdapter);

        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> displayNames = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) {
                        displayNames.add(c.getName() + " - " +
                                c.getSpecializationName() + " - " +
                                c.getYearName() + " - " +
                                c.getSectionName());
                    }
                }
                courseAdapter.clear();
                courseAdapter.addAll(displayNames);

                // Set current course selection
                String currentCourseDisplay = student.getCourseName() + " - " +
                        student.getSpecializationName() + " - " +
                        student.getYearName() + " - " +
                        student.getSectionName();
                int position = displayNames.indexOf(currentCourseDisplay);
                if (position >= 0) {
                    spinnerCoursesDialog.setSelection(position);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        new AlertDialog.Builder(this)
                .setTitle("Edit Examinee")
                .setView(dialogView)
                .setPositiveButton("Update", (dialog, which) -> {
                    String fullName = etFullNameDialog.getText().toString().trim();
                    String birthday = etBirthdayDialog.getText().toString().trim();
                    String email = etEmailDialog.getText().toString().trim();
                    String contact = etContactDialog.getText().toString().trim();
                    int coursePos = spinnerCoursesDialog.getSelectedItemPosition();

                    if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(birthday) ||
                            TextUtils.isEmpty(email) || TextUtils.isEmpty(contact) || coursePos < 0) {
                        Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    CourseModel selectedCourse = courseOptionList.get(coursePos);
                    updateStudentInDatabase(student, fullName, birthday, email, contact, selectedCourse);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // NEW: Update student in database
    private void updateStudentInDatabase(StudentModel student, String fullName, String birthday,
                                         String email, String contact, CourseModel selectedCourse) {
        // Update student data
        student.setFullName(fullName);
        student.setBirthday(birthday);
        student.setEmail(email);
        student.setContact(contact);
        student.setCourseId(selectedCourse.getId());
        student.setCourseName(selectedCourse.getName());
        student.setSpecializationName(selectedCourse.getSpecializationName());
        student.setYearName(selectedCourse.getYearName());
        student.setSectionName(selectedCourse.getSectionName());

        // Update in Firebase
        studentsRef.child(student.getStudentId()).setValue(student)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Examinee updated successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );

        // Update email in Firebase Auth if changed
        if (!student.getEmail().equals(email)) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null && user.getUid().equals(student.getUid())) {
                user.updateEmail(email)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(this, "Email updated in authentication", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        }
    }

    // DAGDAG: Date picker method
    private void showDatePickerDialog(EditText birthdayField) {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    // Format the date as YYYY-MM-DD
                    String selectedDate = String.format("%04d-%02d-%02d",
                            selectedYear, selectedMonth + 1, selectedDay);
                    birthdayField.setText(selectedDate);
                },
                year, month, day
        );

        // Set maximum date to today (cannot select future dates)
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    // DAGDAG: Extracted add student logic
    private void addStudentToDatabase(String fullName, String birthday, String email, String contact, CourseModel selectedCourse) {
        generateStudentId(studentId -> {
            // Auto password from birthday: YYYYMMDD
            String password = birthday.replaceAll("[^0-9]", "");

            StudentModel student = new StudentModel(
                    studentId,
                    fullName,
                    birthday,
                    email,
                    contact,
                    selectedCourse.getId(),
                    selectedCourse.getName(),
                    selectedCourse.getSpecializationName(),
                    selectedCourse.getYearName(),
                    selectedCourse.getSectionName(),
                    "",            // profileImage
                    password,
                    ""             // uid
            );

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(authTask -> {
                        if (authTask.isSuccessful()) {
                            FirebaseUser firebaseUser = authTask.getResult().getUser();
                            String uid = firebaseUser.getUid();

                            // Save role in Users node
                            usersRef.child(uid).child("role").setValue("student");
                            usersRef.child(uid).child("studentId").setValue(studentId);

                            // Attach UID to student model
                            student.setUid(uid);

                            // Save student in Students node
                            studentsRef.child(studentId).setValue(student)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this, "Examinee added successfully", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                    );
                        } else {
                            Toast.makeText(this, "Auth failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    // DAGDAG: SIDEBAR INITIALIZATION
    private void initializeSidebar() {
        drawerLayout = findViewById(R.id.drawerLayout);
        sidebarLayout = findViewById(R.id.sidebarLayout);
        btnToggleSidebar = findViewById(R.id.btnOpenSidebar);
        curriculumDropdown = findViewById(R.id.curriculumDropdown);
        accountsDropdown = findViewById(R.id.accountsDropdown);

        // Sidebar toggle
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
        // Set Manage Examinee button as active (highlighted)
        Button btnManageStudents = findViewById(R.id.btnManageStudents);
        btnManageStudents.setBackgroundResource(R.drawable.sidebar_button_pressed);

        // Set accounts dropdown as expanded by default
        accountsDropdown.setVisibility(View.VISIBLE);
        Button btnAccountsHeader = findViewById(R.id.btnManageAccountsHeader);
        btnAccountsHeader.setText("👤 Manage Accounts ▴");

        // Set curriculum dropdown as collapsed by default
        curriculumDropdown.setVisibility(View.GONE);
        Button btnCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        btnCurriculumHeader.setText("📘 Manage Curriculum ▾");
    }

    // DAGDAG: SIDEBAR NAVIGATION SETUP
    private void setupSidebarNavigation() {
        // Curriculum dropdown
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

                // Collapse accounts if needed for consistency
                if (isAccountsExpanded) {
                    accountsDropdown.setVisibility(View.GONE);
                    Button btnAccountsHeader = findViewById(R.id.btnManageAccountsHeader);
                    btnAccountsHeader.setText("👤 Manage Accounts ▾");
                    isAccountsExpanded = false;
                }
            }
        });

        // Accounts dropdown
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

                // Collapse curriculum if needed for consistency
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

    // DAGDAG: Setup sidebar buttons functionality
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
            startActivity(new Intent(StudentActivity.this, SpecializationsActivity.class));
        });

        btnManageYears.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(StudentActivity.this, YearsActivity.class));
        });

        btnManageSections.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(StudentActivity.this, SectionsActivity.class));
        });

        btnManageCourse.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(StudentActivity.this, CourseActivity.class));
        });

        btnManageSubjects.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(StudentActivity.this, SubjectActivity.class));
        });

        btnManageTeachers.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(StudentActivity.this, TeacherActivity.class));
        });

        // Manage Students - Close drawer lang, walang navigation
        btnManageStudents.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            // Walang startActivity kasi nasa StudentActivity na tayo
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

    // DAGDAG: Back pressed handling for sidebar
    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(sidebarLayout)) {
            drawerLayout.closeDrawer(sidebarLayout);
        } else {
            super.onBackPressed();
        }
    }

    // ORIGINAL METHODS - WALANG BINAGO
    private void loadCourses() {
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                List<String> displayNames = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) {
                        courseOptionList.add(c);
                        displayNames.add(c.getName() + " - " +
                                c.getSpecializationName() + " - " +
                                c.getYearName() + " - " +
                                c.getSectionName());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void generateStudentId(OnIdGeneratedListener listener) {
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Integer> numbers = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String id = ds.getKey();
                    if (id != null && id.startsWith("STD-")) {
                        try {
                            int num = Integer.parseInt(id.replace("STD-", ""));
                            numbers.add(num);
                        } catch (NumberFormatException ignored) {}
                    }
                }

                int newNum = 1;
                while (numbers.contains(newNum)) newNum++;
                String newId = String.format("STD-%04d", newNum);
                listener.onGenerated(newId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(StudentActivity.this, "Error generating ID", Toast.LENGTH_SHORT).show();
            }
        });
    }

    interface OnIdGeneratedListener {
        void onGenerated(String studentId);
    }
}