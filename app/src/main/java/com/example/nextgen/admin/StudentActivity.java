package com.example.nextgen.admin;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.nextgen.R;
import com.example.nextgen.MainActivity;
import com.example.nextgen.SessionManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class StudentActivity extends AppCompatActivity {
    private Uri selectedImageUri;
    private ImageView currentEditProfileView;
    private String currentStudentId;

    // SIDEBAR VARIABLES
    private DrawerLayout drawerLayout;
    private LinearLayout curriculumDropdown, accountsDropdown;

<<<<<<< HEAD
    // FORM VARIABLES
    private EditText searchStudent;
=======
    private EditText etFullName, etBirthday, etEmail, etContact;

>>>>>>> origin/pushnyodito4
    private RecyclerView recyclerStudents;
    private Button btnAddStudent;
    private ImageButton btnClearSearch;

    // USE CourseModel AND StudentModel
    private List<CourseModel> courseOptionList = new ArrayList<>();
    private List<StudentModel> studentList = new ArrayList<>();
    private List<StudentModel> filteredStudentList = new ArrayList<>();

    private DatabaseReference studentsRef, coursesRef, usersRef;
    private FirebaseAuth auth;
    private SessionManager sessionManager;

    private StudentAdapter studentAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student);

<<<<<<< HEAD
        // INITIALIZE SIDEBAR
        initializeSidebarViews();
        setupSidebarNavigation();

        // INITIALIZE FORM ELEMENTS
        initializeFormViews();
        setupSearchFunctionality();
=======
        recyclerStudents = findViewById(R.id.recyclerStudents);
        btnAddStudent = findViewById(R.id.btnAddStudent);




        recyclerStudents.setLayoutManager(new LinearLayoutManager(this));
>>>>>>> origin/pushnyodito4

        // Firebase
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        auth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);

        // INITIALIZE ADAPTER WITH STUDENTMODEL
        studentAdapter = new StudentAdapter(filteredStudentList, new StudentAdapter.OnStudentActionListener() {
            @Override
            public void onUpdate(StudentModel student) {
                showEditStudentDialog(student);
            }

            @Override
            public void onDelete(StudentModel student) {
                showDeleteConfirmation(student);
            }
        });
        recyclerStudents.setAdapter(studentAdapter);

        // Load students
        studentsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                studentList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StudentModel student = ds.getValue(StudentModel.class);
                    if (student != null) {
                        student.setStudentId(ds.getKey()); // Set the studentId from Firebase key
                        studentList.add(student);
                    }
                }
                // Update filtered list
                filterStudents(searchStudent.getText().toString());
                studentAdapter.notifyDataSetChanged();

                // Show message
                if (studentList.isEmpty()) {
                    Toast.makeText(StudentActivity.this, "No students found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(StudentActivity.this, "Failed to load students", Toast.LENGTH_SHORT).show();
            }
        });

<<<<<<< HEAD
        loadCourses();
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

    private void initializeFormViews() {
        recyclerStudents = findViewById(R.id.recyclerStudents);
        btnAddStudent = findViewById(R.id.btnAddStudent);
        searchStudent = findViewById(R.id.searchStudent);
        btnClearSearch = findViewById(R.id.btnClearSearch);

        recyclerStudents.setLayoutManager(new LinearLayoutManager(this));

        // Set click listener for Add Student button to show popup form
        btnAddStudent.setOnClickListener(v -> showAddStudentDialog());
    }

    private void showAddStudentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Inflate custom dialog layout - GAMITIN ANG IYONG XML LAYOUT
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_student, null);
        builder.setView(dialogView);

        // Initialize dialog views FROM XML LAYOUT
        TextInputEditText etFullName = dialogView.findViewById(R.id.etFullName);
        TextInputEditText etBirthday = dialogView.findViewById(R.id.etBirthday);
        TextInputEditText etEmail = dialogView.findViewById(R.id.etEmail);
        TextInputEditText etContact = dialogView.findViewById(R.id.etContact);
        Spinner spinnerCourses = dialogView.findViewById(R.id.spinnerCourses);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnSubmit = dialogView.findViewById(R.id.btnSubmit);

        // Setup course spinner
        setupDialogCourseSpinner(spinnerCourses);

        // Date picker for birthday
        etBirthday.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePicker = new DatePickerDialog(StudentActivity.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formattedDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                        etBirthday.setText(formattedDate);
                    }, year, month, day);
            datePicker.show();
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Cancel button
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Submit button
        btnSubmit.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String birthday = etBirthday.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String contact = etContact.getText().toString().trim();
            int coursePos = spinnerCourses.getSelectedItemPosition();

            if (TextUtils.isEmpty(fullName)) {
                etFullName.setError("Full name is required");
                return;
            }
            if (TextUtils.isEmpty(birthday)) {
                etBirthday.setError("Birthday is required");
                return;
            }
            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email is required");
                return;
            }
            if (TextUtils.isEmpty(contact)) {
                etContact.setError("Contact number is required");
                return;
            }
            if (coursePos < 0 || coursePos >= courseOptionList.size()) {
                Toast.makeText(this, "Please select a course", Toast.LENGTH_SHORT).show();
                return;
            }

            addStudent(fullName, birthday, email, contact, coursePos);
            dialog.dismiss();
        });
    }

    private void showEditStudentDialog(StudentModel student) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Inflate custom dialog layout - GAMITIN ANG IYONG XML LAYOUT
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_student_edit, null);
        builder.setView(dialogView);

        // Initialize dialog views FROM XML LAYOUT
        EditText etFullName = dialogView.findViewById(R.id.etFullName);
        EditText etBirthday = dialogView.findViewById(R.id.etBirthday);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etContact = dialogView.findViewById(R.id.etContact);
        Spinner spinnerCourses = dialogView.findViewById(R.id.spinnerCourses);
        ImageView ivProfile = dialogView.findViewById(R.id.ivProfile);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnSubmit = dialogView.findViewById(R.id.btnSubmit);

        // Populate fields with current student data
        etFullName.setText(student.getFullName());
        etBirthday.setText(student.getBirthday());
        etEmail.setText(student.getEmail());
        etContact.setText(student.getContact());

        // Set profile image
        if (student.getProfileImage() != null && !student.getProfileImage().isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(student.getProfileImage(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                ivProfile.setImageBitmap(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
                ivProfile.setImageResource(R.drawable.examinee_default);
            }
        } else {
            ivProfile.setImageResource(R.drawable.examinee_default);
        }

        // Setup course spinner and select current course
        setupEditCourseSpinner(spinnerCourses, student);

        // Date picker for birthday
        etBirthday.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePicker = new DatePickerDialog(StudentActivity.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formattedDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                        etBirthday.setText(formattedDate);
                    }, year, month, day);
            datePicker.show();
        });

        // Profile image click listener
        ivProfile.setOnClickListener(v -> {
            currentEditProfileView = ivProfile;
            currentStudentId = student.getStudentId();
            openImagePicker();
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Cancel button
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Submit button
        btnSubmit.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String birthday = etBirthday.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String contact = etContact.getText().toString().trim();
            int coursePos = spinnerCourses.getSelectedItemPosition();

            if (TextUtils.isEmpty(fullName)) {
                etFullName.setError("Full name is required");
                return;
            }
            if (TextUtils.isEmpty(birthday)) {
                etBirthday.setError("Birthday is required");
                return;
            }
            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email is required");
                return;
            }
            if (TextUtils.isEmpty(contact)) {
                etContact.setError("Contact number is required");
                return;
            }
            if (coursePos < 0 || coursePos >= courseOptionList.size()) {
                Toast.makeText(this, "Please select a course", Toast.LENGTH_SHORT).show();
                return;
            }

            updateStudent(student, fullName, birthday, email, contact, coursePos);
            dialog.dismiss();
        });
    }

    private void showDeleteConfirmation(StudentModel student) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Examinee")
                .setMessage("Are you sure you want to delete " + student.getFullName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteStudentFromFirebase(student);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupDialogCourseSpinner(Spinner spinner) {
        List<String> displayNames = new ArrayList<>();
        for (CourseModel course : courseOptionList) {
            String displayName = course.getCourseName() + " - " +
                    course.getSpecializationName() + " - " +
                    course.getYearName() + " - " +
                    course.getSectionName();
            displayNames.add(displayName);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void setupEditCourseSpinner(Spinner spinner, StudentModel student) {
        List<String> displayNames = new ArrayList<>();
        int selectedPosition = 0;

        for (int i = 0; i < courseOptionList.size(); i++) {
            CourseModel course = courseOptionList.get(i);
            String displayName = course.getCourseName() + " - " +
                    course.getSpecializationName() + " - " +
                    course.getYearName() + " - " +
                    course.getSectionName();
            displayNames.add(displayName);

            // Check if this course matches the student's current course
            if (course.getId().equals(student.getCourseId())) {
                selectedPosition = i;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selectedPosition);
    }

    private void setupSearchFunctionality() {
        searchStudent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterStudents(s.toString());

                // Show/hide clear button
                if (s.length() > 0) {
                    btnClearSearch.setVisibility(View.VISIBLE);
                } else {
                    btnClearSearch.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            searchStudent.setText("");
            btnClearSearch.setVisibility(View.GONE);
            filterStudents("");
        });
    }

    private void filterStudents(String searchText) {
        filteredStudentList.clear();

        if (searchText.isEmpty()) {
            filteredStudentList.addAll(studentList);
        } else {
            String query = searchText.toLowerCase().trim();
            for (StudentModel student : studentList) {
                if (student.getFullName().toLowerCase().contains(query) ||
                        student.getEmail().toLowerCase().contains(query) ||
                        student.getCourseName().toLowerCase().contains(query) ||
                        student.getContact().contains(query)) {
                    filteredStudentList.add(student);
                }
            }
        }

        studentAdapter.notifyDataSetChanged();

        // Show empty state if no results
        if (filteredStudentList.isEmpty() && !searchText.isEmpty()) {
            Toast.makeText(this, "No examinees found", Toast.LENGTH_SHORT).show();
        }
    }

    // DELETE STUDENT METHOD
    private void deleteStudentFromFirebase(StudentModel student) {
        String studentId = student.getStudentId();
        String uid = student.getUid();

        if (uid != null && !uid.isEmpty()) {
            usersRef.child(uid).removeValue()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "User record deleted", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to delete user record: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }

        studentsRef.child(studentId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Student deleted successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // UPDATE STUDENT METHOD
    private void updateStudent(StudentModel student, String fullName, String birthday, String email, String contact, int coursePos) {
        CourseModel selectedCourse = courseOptionList.get(coursePos);

        // Update student object
        student.setFullName(fullName);
        student.setBirthday(birthday);
        student.setEmail(email);
        student.setContact(contact);
        student.setCourseId(selectedCourse.getId());
        student.setCourseName(selectedCourse.getCourseName());
        student.setSpecializationName(selectedCourse.getSpecializationName());
        student.setYearName(selectedCourse.getYearName());
        student.setSectionName(selectedCourse.getSectionName());

        // Update profile image if new one was selected
        if (selectedImageUri != null) {
            String base64Image = convertImageToBase64(selectedImageUri);
            if (base64Image != null) {
                student.setProfileImage(base64Image);
            }
        }

        // Update in Firebase
        studentsRef.child(student.getStudentId()).setValue(student)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Student updated successfully!", Toast.LENGTH_SHORT).show();
                    selectedImageUri = null; // Reset after update
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update student: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // SIDEBAR METHODS
    private void initializeSidebarViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        curriculumDropdown = findViewById(R.id.curriculumDropdown);
        accountsDropdown = findViewById(R.id.accountsDropdown);
    }

    private void setupSidebarNavigation() {
        ImageButton btnToggleSidebar = findViewById(R.id.btnToggleSidebar);
        btnToggleSidebar.setOnClickListener(v -> toggleSidebar());

        Button btnManageCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        Button btnManageAccountsHeader = findViewById(R.id.btnManageAccountsHeader);

        btnManageCurriculumHeader.setOnClickListener(v -> {
            if (curriculumDropdown.getVisibility() == View.VISIBLE) {
                curriculumDropdown.setVisibility(View.GONE);
            } else {
                curriculumDropdown.setVisibility(View.VISIBLE);
                accountsDropdown.setVisibility(View.GONE);
            }
        });

        btnManageAccountsHeader.setOnClickListener(v -> {
            if (accountsDropdown.getVisibility() == View.VISIBLE) {
                accountsDropdown.setVisibility(View.GONE);
            } else {
                accountsDropdown.setVisibility(View.VISIBLE);
                curriculumDropdown.setVisibility(View.GONE);
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
            navigateToActivity("Teachers");
            drawerLayout.closeDrawer(android.view.Gravity.START);
        });

        findViewById(R.id.btnManageStudents).setOnClickListener(v -> {
            Toast.makeText(this, "Already in Examinees", Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(android.view.Gravity.START);
        });

        // Logout button
        Button logoutBtn = findViewById(R.id.logoutBtn);
        logoutBtn.setOnClickListener(v -> logout());
    }

    // NAVIGATION METHOD
    private void navigateToActivity(String activityName) {
        Toast.makeText(this, "Navigating to " + activityName, Toast.LENGTH_SHORT).show();

        try {
            Intent intent = null;

            switch (activityName) {
                case "Teachers":
                    intent = new Intent(this, TeacherActivity.class);
                    break;
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
            }

            if (intent != null) {
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }

        } catch (Exception e) {
            Toast.makeText(this, "Cannot open " + activityName, Toast.LENGTH_LONG).show();
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

    private void logout() {
        // Close sidebar first
        if (drawerLayout.isDrawerOpen(android.view.Gravity.START)) {
            drawerLayout.closeDrawer(android.view.Gravity.START);
        }

        // Clear session AND sign out from Firebase
        sessionManager.clearSession();
        auth.signOut();

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        // Redirect to login screen
        Intent intent = new Intent(StudentActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
=======

        btnAddStudent.setOnClickListener(v -> addStudentDialog());

>>>>>>> origin/pushnyodito4
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

<<<<<<< HEAD
                if (courseOptionList.isEmpty()) {
                    Toast.makeText(StudentActivity.this, "No courses available", Toast.LENGTH_SHORT).show();
=======
                ArrayAdapter<String> adapter = new ArrayAdapter<>(StudentActivity.this,
                        android.R.layout.simple_spinner_item, displayNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void addStudentDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_student_add, null);

        EditText etFullName = dialogView.findViewById(R.id.etFullName);
        EditText etBirthday = dialogView.findViewById(R.id.etBirthday);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etContact = dialogView.findViewById(R.id.etContact);
        Spinner spCourse = dialogView.findViewById(R.id.spinnerCourses);
        ImageView ivProfile = dialogView.findViewById(R.id.ivProfile);

        // Birthday picker
        etBirthday.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog picker = new DatePickerDialog(this, (view, y, m, d) -> {
                etBirthday.setText(String.format("%04d-%02d-%02d", y, m+1, d));
            }, year, month, day);
            picker.show();
        });

        // Load courses dynamically from Firebase
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                List<String> courseNames = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) {
                        courseOptionList.add(c);
                        courseNames.add(c.getCourseName() + " - " +
                                c.getSpecializationName() + " - " +
                                c.getYearName() + " - " +
                                c.getSectionName());
                    }
>>>>>>> origin/pushnyodito4
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        StudentActivity.this,
                        android.R.layout.simple_spinner_item,
                        courseNames
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spCourse.setAdapter(adapter);
            }

            @Override
<<<<<<< HEAD
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(StudentActivity.this, "Failed to load courses", Toast.LENGTH_SHORT).show();
            }
        });
    }
=======
            public void onCancelled(@NonNull DatabaseError error) {}
        });
>>>>>>> origin/pushnyodito4

    private void addStudent(String fullName, String birthday, String email, String contact, int coursePos) {
        CourseModel selectedCourse = courseOptionList.get(coursePos);

<<<<<<< HEAD
        generateStudentId(studentId -> {
            String password = birthday.replaceAll("[^0-9]", "");
            if (password.length() < 6) {
                password = "123456";
            }

            // Create StudentModel object using setters
            StudentModel student = new StudentModel();
            student.setStudentId(studentId);
            student.setFullName(fullName);
            student.setBirthday(birthday);
            student.setEmail(email);
            student.setContact(contact);
            student.setCourseId(selectedCourse.getId());
            student.setCourseName(selectedCourse.getCourseName());
            student.setSpecializationName(selectedCourse.getSpecializationName());
            student.setYearName(selectedCourse.getYearName());
            student.setSectionName(selectedCourse.getSectionName());
            student.setProfileImage("");
            student.setPassword(password);
            student.setUid(""); // Will be set after Firebase auth
=======
        // Create AlertDialog
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle("Add Student")
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .create();

        dialog.show();
>>>>>>> origin/pushnyodito4

        // Override positive button to prevent auto-dismiss
        Button btnAdd = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        btnAdd.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String birthday = etBirthday.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String contact = etContact.getText().toString().trim();
            int coursePos = spCourse.getSelectedItemPosition();

            if (fullName.isEmpty() || birthday.isEmpty() || email.isEmpty() || contact.isEmpty() || coursePos < 0) {
                Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show();
                return;
            }

<<<<<<< HEAD
                            // Attach UID to student
                            student.setUid(uid);

                            // Save student in Students node
                            studentsRef.child(studentId).setValue(student)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(StudentActivity.this, "Student added successfully!", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(StudentActivity.this, "Failed to save student: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            Toast.makeText(StudentActivity.this, "Auth failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
=======
            CourseModel selectedCourse = courseOptionList.get(coursePos);

            // Generate student ID and password
            generateStudentId(studentId -> {
                String[] parts = birthday.split("-");
                String password = parts.length == 3 ? parts[1] + parts[2] + parts[0] : "123456"; // MMDDYYYY fallback

                StudentModel student = new StudentModel(
                        studentId,
                        fullName,
                        birthday,
                        email,
                        contact,
                        selectedCourse.getId(),
                        selectedCourse.getCourseName(),
                        selectedCourse.getSpecializationName(),
                        selectedCourse.getYearName(),
                        selectedCourse.getSectionName(),
                        "", // profileImage
                        password,
                        ""  // uid
                );

                auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(authTask -> {
                            if (authTask.isSuccessful()) {
                                FirebaseUser firebaseUser = authTask.getResult().getUser();
                                String uid = firebaseUser.getUid();
                                student.setUid(uid);

                                usersRef.child(uid).child("role").setValue("student");
                                usersRef.child(uid).child("studentId").setValue(studentId);

                                studentsRef.child(studentId).setValue(student)
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "Student added", Toast.LENGTH_SHORT).show();
                                            studentList.add(student);
                                            studentAdapter.notifyItemInserted(studentList.size() - 1);
                                            dialog.dismiss();
                                        });
                            } else {
                                Toast.makeText(this, "Auth failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
            });
>>>>>>> origin/pushnyodito4
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

    // IMAGE PICKER METHOD
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, 101);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            if (currentEditProfileView != null) {
                currentEditProfileView.setImageURI(selectedImageUri);
            }
        }
    }

    // CONVERT IMAGE TO BASE64
    private String convertImageToBase64(Uri imageUri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            return Base64.encodeToString(byteArray, Base64.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    interface OnIdGeneratedListener {
        void onGenerated(String studentId);
    }
}