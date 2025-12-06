package com.finale.nextgen.admin;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
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

import com.finale.nextgen.MainActivity;
import com.finale.nextgen.R;
import com.finale.nextgen.SessionManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StudentActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    private RecyclerView recyclerStudents;

    private FloatingActionButton addStudentFab;

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

        initializeToolbarAndBackNavigation();

        ImageView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            // Go back to AdminActivity
            Intent intent = new Intent(this, AdminActivity.class);
            startActivity(intent);
            finish();
        });


        addStudentFab = findViewById(R.id.addStudentFab);
        addStudentFab.setOnClickListener(v -> showAddStudentDialog());

        // NEW: Initialize enhanced UI components
        tvStudentCount = findViewById(R.id.tvStudentCount);
        emptyState = findViewById(R.id.emptyState);
        btnSort = findViewById(R.id.btnSort);
        etSearchStudent = findViewById(R.id.etSearchStudent);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        recyclerStudents = findViewById(R.id.studentRecyclerView);
        recyclerStudents.setLayoutManager(new LinearLayoutManager(this));


        // Firebase
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        auth = FirebaseAuth.getInstance();

        studentAdapter = new StudentAdapter(filteredStudentList, new StudentAdapter.OnStudentActionListener() {
            @Override
            public void onUpdate(StudentModel student) {
                showEditStudentDialog(student);
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

    }

    private void initializeToolbarAndBackNavigation() {
        // Setup Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // I-set up ang 'Up' o 'Back' arrow
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
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

        //
        // Sort by name (null-safe)
        Collections.sort(filteredStudentList, new Comparator<StudentModel>() {
            @Override
            public int compare(StudentModel s1, StudentModel s2) {
                String name1 = s1.getFullName() != null ? s1.getFullName() : "";
                String name2 = s2.getFullName() != null ? s2.getFullName() : "";

                return isAscending ?
                        name1.compareToIgnoreCase(name2) :
                        name2.compareToIgnoreCase(name1);
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


    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // === ISA LANG ANG SET NG METHODS PARA SA ADD/EDIT DIALOGS ===

    private void showAddStudentDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_student, null);
        setupStudentDialog(dialogView, null);
    }

    private void showEditStudentDialog(StudentModel student) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_student, null);
        setupStudentDialog(dialogView, student);
    }

    private void setupStudentDialog(View dialogView, StudentModel existingStudent) {
        EditText etFullNameDialog = dialogView.findViewById(R.id.etFullName);
        EditText etBirthdayDialog = dialogView.findViewById(R.id.etBirthday);
        EditText etEmailDialog = dialogView.findViewById(R.id.etEmail);
        EditText etContactDialog = dialogView.findViewById(R.id.etContact);
        Spinner spinnerCoursesDialog = dialogView.findViewById(R.id.spinnerCourses);

        // Calendar picker for birthday
        etBirthdayDialog.setFocusable(false);
        etBirthdayDialog.setOnClickListener(v -> showDatePickerDialog(etBirthdayDialog));

        // Load courses into spinner
        ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCoursesDialog.setAdapter(courseAdapter);

        // Pre-fill data if editing
        if (existingStudent != null) {
            etFullNameDialog.setText(existingStudent.getFullName());
            etBirthdayDialog.setText(existingStudent.getBirthday());
            etEmailDialog.setText(existingStudent.getEmail());
            etContactDialog.setText(existingStudent.getContact());
        }

        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> displayNames = new ArrayList<>();
                courseOptionList.clear();

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
                courseAdapter.clear();
                courseAdapter.addAll(displayNames);

                // Set current course selection for edit mode
                if (existingStudent != null) {
                    String currentCourseDisplay = existingStudent.getCourseName() + " - " +
                            existingStudent.getSpecializationName() + " - " +
                            existingStudent.getYearName() + " - " +
                            existingStudent.getSectionName();
                    int position = displayNames.indexOf(currentCourseDisplay);
                    if (position >= 0) {
                        spinnerCoursesDialog.setSelection(position);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setNegativeButton("Cancel", null);

        if (existingStudent != null) {
            // Edit mode
            builder.setTitle("Edit Examinee")
                    .setPositiveButton("Update", (dialog, which) -> {
                        processStudentData(etFullNameDialog, etBirthdayDialog, etEmailDialog,
                                etContactDialog, spinnerCoursesDialog, existingStudent);
                    });
        } else {
            // Add mode
            builder.setTitle("Add Examinee")
                    .setPositiveButton("Add", (dialog, which) -> {
                        processStudentData(etFullNameDialog, etBirthdayDialog, etEmailDialog,
                                etContactDialog, spinnerCoursesDialog, null);
                    });
        }

        builder.show();
    }

    private void processStudentData(EditText etFullName, EditText etBirthday, EditText etEmail,
                                    EditText etContact, Spinner spinnerCourses, StudentModel existingStudent) {
        String fullName = etFullName.getText().toString().trim();
        String birthday = etBirthday.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String contact = etContact.getText().toString().trim();
        int coursePos = spinnerCourses.getSelectedItemPosition();

        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(birthday) ||
                TextUtils.isEmpty(email) || TextUtils.isEmpty(contact) || coursePos < 0) {
            Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        CourseModel selectedCourse = courseOptionList.get(coursePos);

        if (existingStudent != null) {
            // Update existing student
            updateStudentInDatabase(existingStudent, fullName, birthday, email, contact, selectedCourse);
        } else {
            // Add new student
            addStudentToDatabase(fullName, birthday, email, contact, selectedCourse);
        }
    }

    private void showDatePickerDialog(EditText birthdayField) {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String selectedDate = String.format("%04d-%02d-%02d",
                            selectedYear, selectedMonth + 1, selectedDay);
                    birthdayField.setText(selectedDate);
                },
                year, month, day
        );

        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void addStudentToDatabase(String fullName, String birthday, String email, String contact, CourseModel selectedCourse) {
        generateStudentId(studentId -> {
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
                    "", // profileImage
                    password,
                    ""  // uid
            );

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(authTask -> {
                        if (authTask.isSuccessful()) {
                            FirebaseUser firebaseUser = authTask.getResult().getUser();
                            String uid = firebaseUser.getUid();

                            usersRef.child(uid).child("role").setValue("student");
                            usersRef.child(uid).child("studentId").setValue(studentId);

                            student.setUid(uid);

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

    private void updateStudentInDatabase(StudentModel student, String fullName, String birthday,
                                         String email, String contact, CourseModel selectedCourse) {
        student.setFullName(fullName);
        student.setBirthday(birthday);
        student.setEmail(email);
        student.setContact(contact);
        student.setCourseId(selectedCourse.getId());
        student.setCourseName(selectedCourse.getName());
        student.setSpecializationName(selectedCourse.getSpecializationName());
        student.setYearName(selectedCourse.getYearName());
        student.setSectionName(selectedCourse.getSectionName());

        studentsRef.child(student.getStudentId()).setValue(student)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Examinee updated successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );

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

    private void loadCourses() {
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) {
                        courseOptionList.add(c);
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