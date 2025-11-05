package com.example.nextgen.admin;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class CourseActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private LinearLayout curriculumDropdown, accountsDropdown;
    private ImageButton btnToggleSidebar;

    private Button btnAddCourse;
    private RecyclerView recyclerCourses;

    private CourseAdapter adapter;
    private final List<CourseModel> courseList = new ArrayList<>();
    private final List<CourseOption> courseOptionList = new ArrayList<>();
    private DatabaseReference coursesRef, courseOptionsRef;

    private SessionManager sessionManager;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course);

        // CRITICAL FIX FOR SIDEBAR HEIGHT
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

<<<<<<< HEAD
        // Initialize SessionManager and Firebase Auth
        sessionManager = new SessionManager(this);
        auth = FirebaseAuth.getInstance();
=======
        recyclerCourses.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CourseAdapter(this, courseList, courseOptionList);
        recyclerCourses.setAdapter(adapter);
>>>>>>> origin/pushnyodito4

        initializeViews();
        setupClickListeners();
        setupFirebase();
        loadCourseOptions();
        loadCourses();

        // Create adapter AFTER course options are loaded
        adapter = new CourseAdapter(this, courseList, courseOptionList);
        recyclerCourses.setAdapter(adapter);
    }

    private void initializeViews() {
        try {
            // Initialize main views
            drawerLayout = findViewById(R.id.drawerLayout);
            curriculumDropdown = findViewById(R.id.curriculumDropdown);
            accountsDropdown = findViewById(R.id.accountsDropdown);

            // Initialize sidebar button
            btnToggleSidebar = findViewById(R.id.btnToggleSidebar);

            // Initialize course views - REMOVED EditText from activity layout
            btnAddCourse = findViewById(R.id.btnAddCourse);
            recyclerCourses = findViewById(R.id.recyclerCourses);

            // Additional fix for drawer layout
            if (drawerLayout != null) {
                drawerLayout.setFitsSystemWindows(true);
            }

            // Setup RecyclerView
            recyclerCourses.setLayoutManager(new LinearLayoutManager(this));
            adapter = new CourseAdapter(this, courseList);
            recyclerCourses.setAdapter(adapter);

            // Check if all views are properly initialized
            if (btnToggleSidebar == null) {
                Log.e("CourseActivity", "btnToggleSidebar is NULL");
            }

        } catch (Exception e) {
            Log.e("CourseActivity", "Error in initializeViews: " + e.getMessage());
            Toast.makeText(this, "Error initializing views", Toast.LENGTH_LONG).show();
        }
    }

    private void setupClickListeners() {
        // Sidebar toggle
        if (btnToggleSidebar != null) {
            btnToggleSidebar.setOnClickListener(v -> toggleSidebar());
        } else {
            Log.e("CourseActivity", "btnToggleSidebar is null - cannot set click listener");
        }

        // Dropdown headers
        Button btnManageCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        Button btnManageAccountsHeader = findViewById(R.id.btnManageAccountsHeader);

        if (btnManageCurriculumHeader != null) {
            btnManageCurriculumHeader.setOnClickListener(v -> toggleDropdown(curriculumDropdown));
        } else {
            Log.e("CourseActivity", "btnManageCurriculumHeader is null");
        }

        if (btnManageAccountsHeader != null) {
            btnManageAccountsHeader.setOnClickListener(v -> toggleDropdown(accountsDropdown));
        } else {
            Log.e("CourseActivity", "btnManageAccountsHeader is null");
        }

        // Sidebar navigation buttons
        setupSidebarNavigation();

        // Add Course button - NOW OPENS DIALOG
        if (btnAddCourse != null) {
            btnAddCourse.setOnClickListener(v -> showAddCourseDialog());
        } else {
            Log.e("CourseActivity", "btnAddCourse is null - cannot set click listener");
        }
    }

    private void showAddCourseDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_course, null);
        builder.setView(dialogView);

        EditText etCourseName = dialogView.findViewById(R.id.etCourseName);
        Spinner spinnerSections = dialogView.findViewById(R.id.spinnerSection);
        Button btnSave = dialogView.findViewById(R.id.btnSaveCourse);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelCourse);

        // Load course options for spinner in dialog
        loadCourseOptionsForDialog(spinnerSections);

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSave.setOnClickListener(v -> {
            String name = etCourseName.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                etCourseName.setError("Enter course name");
                return;
            }

            int selectedPos = spinnerSections.getSelectedItemPosition();
            if (selectedPos < 0 || courseOptionList.isEmpty()) {
                Toast.makeText(this, "Select a course option", Toast.LENGTH_SHORT).show();
                return;
            }

            CourseOption selectedOption = courseOptionList.get(selectedPos);
            addCourseToFirebase(name, selectedOption);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
    }

    private void loadCourseOptionsForDialog(Spinner spinner) {
        courseOptionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseOption option = ds.getValue(CourseOption.class);
                    if (option != null) {
                        courseOptionList.add(option);
<<<<<<< HEAD
                        names.add(option.getSpecializationName() + " - " + option.getSectionName() + " - " + option.getYearName());
                    }
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(CourseActivity.this,
                        android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
=======
                    }
                }

                // Create adapter AFTER course options are loaded
                adapter = new CourseAdapter(CourseActivity.this, courseList, courseOptionList);
                recyclerCourses.setAdapter(adapter);
>>>>>>> origin/pushnyodito4
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void addCourseToFirebase(String name, CourseOption selectedOption) {
        String id = coursesRef.push().getKey();
        if (id == null) {
            Toast.makeText(this, "Error generating ID", Toast.LENGTH_SHORT).show();
            return;
        }

        CourseModel course = new CourseModel(
                id,
                name,
                selectedOption.getSpecializationId(),
                selectedOption.getSpecializationName(),
                selectedOption.getYearId(),
                selectedOption.getYearName(),
                selectedOption.getSectionId(),
                selectedOption.getSectionName()
        );

        coursesRef.child(id).setValue(course)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Course added successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void setupSidebarNavigation() {
        // Curriculum management
        View btnSpecializations = findViewById(R.id.btnManageSpecializations);
        View btnYears = findViewById(R.id.btnManageYears);
        View btnSections = findViewById(R.id.btnManageSections);
        View btnCourse = findViewById(R.id.btnManageCourse);
        View btnSubjects = findViewById(R.id.btnManageSubjects);
        View btnTeachers = findViewById(R.id.btnManageTeachers);
        View btnStudents = findViewById(R.id.btnManageStudents);
        View logoutBtn = findViewById(R.id.logoutBtn);

        if (btnSpecializations != null) {
            btnSpecializations.setOnClickListener(v -> {
                startActivity(new Intent(this, SpecializationsActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("CourseActivity", "btnManageSpecializations is null");
        }

        if (btnYears != null) {
            btnYears.setOnClickListener(v -> {
                startActivity(new Intent(this, YearsActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("CourseActivity", "btnManageYears is null");
        }

        if (btnSections != null) {
            btnSections.setOnClickListener(v -> {
                startActivity(new Intent(this, SectionsActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("CourseActivity", "btnManageSections is null");
        }

        if (btnCourse != null) {
            btnCourse.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("CourseActivity", "btnManageCourse is null");
        }

        if (btnSubjects != null) {
            btnSubjects.setOnClickListener(v -> {
                startActivity(new Intent(this, SubjectActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("CourseActivity", "btnManageSubjects is null");
        }

        if (btnTeachers != null) {
            btnTeachers.setOnClickListener(v -> {
                startActivity(new Intent(this, TeacherActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("CourseActivity", "btnManageTeachers is null");
        }

        if (btnStudents != null) {
            btnStudents.setOnClickListener(v -> {
                startActivity(new Intent(this, StudentActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("CourseActivity", "btnManageStudents is null");
        }

        // Logout
        if (logoutBtn != null) {
            logoutBtn.setOnClickListener(v -> performLogout());
        } else {
            Log.e("CourseActivity", "logoutBtn is null");
        }
    }

    private void performLogout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Logout");
        builder.setMessage("Are you sure you want to logout?");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            sessionManager.clearSession();
            if (auth.getCurrentUser() != null) {
                auth.signOut();
            }
            Intent intent = new Intent(CourseActivity.this, com.example.nextgen.MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            Toast.makeText(CourseActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void toggleSidebar() {
        if (drawerLayout != null) {
            if (drawerLayout.isDrawerOpen(Gravity.START)) {
                drawerLayout.closeDrawer(Gravity.START);
            } else {
                drawerLayout.openDrawer(Gravity.START);
            }
        }
    }

    private void toggleDropdown(LinearLayout dropdown) {
        if (dropdown != null) {
            if (dropdown.getVisibility() == View.VISIBLE) {
                dropdown.setVisibility(View.GONE);
            } else {
                dropdown.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupFirebase() {
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        courseOptionsRef = FirebaseDatabase.getInstance().getReference("CourseOptions");
    }

    private void loadCourseOptions() {
        // This is now handled in the dialog
    }


    private void loadCourses() {
        coursesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel course = ds.getValue(CourseModel.class);
                    if (course != null) courseList.add(course);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Close drawer when activity resumes
        if (drawerLayout != null && drawerLayout.isDrawerOpen(Gravity.START)) {
            drawerLayout.closeDrawer(Gravity.START);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(Gravity.START)) {
            drawerLayout.closeDrawer(Gravity.START);
        } else {
            super.onBackPressed();
        }
    }
}