package com.example.nextgen.admin;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
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

public class SectionsActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private LinearLayout curriculumDropdown, accountsDropdown;
    private ImageButton btnToggleSidebar;

    private Button btnAddSection;
    private RecyclerView recyclerSections;

    private DatabaseReference dbSections, dbSpecializations, dbYears, dbCourseOptions;
    private ArrayList<SectionModel> sectionList = new ArrayList<>();
    private SectionAdapter adapter;

    private ArrayList<String> specializationList = new ArrayList<>();
    private ArrayList<String> specializationIdList = new ArrayList<>();

    private ArrayList<String> yearList = new ArrayList<>();
    private ArrayList<String> yearIdList = new ArrayList<>();

    private SessionManager sessionManager;
    private FirebaseAuth auth;

    // CourseOption inner class
    public static class CourseOption {
        public String yearId;
        public String yearName;
        public String sectionId;
        public String sectionName;
        public String specializationId;
        public String specializationName;

        public CourseOption() {
            // Default constructor required for Firebase
        }

        public CourseOption(String yearId, String yearName, String sectionId, String sectionName, String specializationId, String specializationName) {
            this.yearId = yearId;
            this.yearName = yearName;
            this.sectionId = sectionId;
            this.sectionName = sectionName;
            this.specializationId = specializationId;
            this.specializationName = specializationName;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sections);

        // FIX FOR SIDEBAR HEIGHT ISSUE
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        // Initialize SessionManager and Firebase Auth
        sessionManager = new SessionManager(this);
        auth = FirebaseAuth.getInstance();

        initializeViews();
        setupClickListeners();
        setupFirebase();
        loadSections();

        // Additional fix for drawer layout
        if (drawerLayout != null) {
            drawerLayout.setFitsSystemWindows(true);
        }
    }

    private void initializeViews() {
        try {
            // Initialize main views
            drawerLayout = findViewById(R.id.drawerLayout);
            curriculumDropdown = findViewById(R.id.curriculumDropdown);
            accountsDropdown = findViewById(R.id.accountsDropdown);

            // Initialize sidebar button
            btnToggleSidebar = findViewById(R.id.btnToggleSidebar);

            // Initialize content views
            btnAddSection = findViewById(R.id.btnAddSection);
            recyclerSections = findViewById(R.id.recyclerSections);

            // Check if all views are properly initialized
            if (btnToggleSidebar == null) {
                Log.e("SectionsActivity", "btnToggleSidebar is NULL");
            }
            if (btnAddSection == null) {
                Log.e("SectionsActivity", "btnAddSection is NULL");
            }
            if (recyclerSections == null) {
                Log.e("SectionsActivity", "recyclerSections is NULL");
            }
            if (drawerLayout == null) {
                Log.e("SectionsActivity", "drawerLayout is NULL");
            }

        } catch (Exception e) {
            Log.e("SectionsActivity", "Error in initializeViews: " + e.getMessage());
            Toast.makeText(this, "Error initializing views", Toast.LENGTH_LONG).show();
        }
    }

    private void setupClickListeners() {
        // Sidebar toggle
        if (btnToggleSidebar != null) {
            btnToggleSidebar.setOnClickListener(v -> toggleSidebar());
        } else {
            Log.e("SectionsActivity", "btnToggleSidebar is null - cannot set click listener");
        }

        // Dropdown headers
        Button btnManageCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        Button btnManageAccountsHeader = findViewById(R.id.btnManageAccountsHeader);

        if (btnManageCurriculumHeader != null) {
            btnManageCurriculumHeader.setOnClickListener(v -> toggleDropdown(curriculumDropdown));
        } else {
            Log.e("SectionsActivity", "btnManageCurriculumHeader is null");
        }

        if (btnManageAccountsHeader != null) {
            btnManageAccountsHeader.setOnClickListener(v -> toggleDropdown(accountsDropdown));
        } else {
            Log.e("SectionsActivity", "btnManageAccountsHeader is null");
        }

        // Sidebar navigation buttons
        setupSidebarNavigation();

        // Add Section button
        if (btnAddSection != null) {
            btnAddSection.setOnClickListener(v -> showAddSectionDialog());
        } else {
            Log.e("SectionsActivity", "btnAddSection is null - cannot set click listener");
        }
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
            Log.e("SectionsActivity", "btnManageSpecializations is null");
        }

        if (btnYears != null) {
            btnYears.setOnClickListener(v -> {
                startActivity(new Intent(this, YearsActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("SectionsActivity", "btnManageYears is null");
        }

        if (btnSections != null) {
            btnSections.setOnClickListener(v -> {
                // Already in Sections activity, just close drawer
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("SectionsActivity", "btnManageSections is null");
        }

        if (btnCourse != null) {
            btnCourse.setOnClickListener(v -> {
                startActivity(new Intent(this, CourseActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("SectionsActivity", "btnManageCourse is null");
        }

        if (btnSubjects != null) {
            btnSubjects.setOnClickListener(v -> {
                startActivity(new Intent(this, SubjectActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("SectionsActivity", "btnManageSubjects is null");
        }

        if (btnTeachers != null) {
            btnTeachers.setOnClickListener(v -> {
                startActivity(new Intent(this, TeacherActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("SectionsActivity", "btnManageTeachers is null");
        }

        if (btnStudents != null) {
            btnStudents.setOnClickListener(v -> {
                startActivity(new Intent(this, StudentActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        } else {
            Log.e("SectionsActivity", "btnManageStudents is null");
        }

        // Logout
        if (logoutBtn != null) {
            logoutBtn.setOnClickListener(v -> performLogout());
        } else {
            Log.e("SectionsActivity", "logoutBtn is null");
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
            Intent intent = new Intent(SectionsActivity.this, com.example.nextgen.MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            Toast.makeText(SectionsActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
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
        dbSections = FirebaseDatabase.getInstance().getReference("Sections");
        dbSpecializations = FirebaseDatabase.getInstance().getReference("Specializations");
        dbYears = FirebaseDatabase.getInstance().getReference("Years");
        dbCourseOptions = FirebaseDatabase.getInstance().getReference("CourseOptions");

        // Setup RecyclerView
        if (recyclerSections != null) {
            recyclerSections.setLayoutManager(new LinearLayoutManager(this));
            adapter = new SectionAdapter(sectionList, new SectionAdapter.OnSectionActionListener() {
                @Override
                public void onEdit(SectionModel section) {
                    showEditSectionDialog(section);
                }

<<<<<<< HEAD
                @Override
                public void onDelete(SectionModel section) {
                    showDeleteConfirmationDialog(section);
                }
            });
            recyclerSections.setAdapter(adapter);
        }
    }

    private void showDeleteConfirmationDialog(SectionModel section) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Section");
        builder.setMessage("Are you sure you want to delete '" + section.name + "'?");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            dbSections.child(section.id).removeValue()
                    .addOnSuccessListener(aVoid -> {
                        // Also remove from CourseOptions
                        dbCourseOptions.child(section.id).removeValue();
                        Toast.makeText(SectionsActivity.this, "Section deleted successfully", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(SectionsActivity.this, "Failed to delete section", Toast.LENGTH_SHORT).show());
=======
            @Override
            public void onDelete(SectionModel section) {
                new AlertDialog.Builder(SectionsActivity.this)
                        .setTitle("Confirm Delete")
                        .setMessage("Are you sure you want to delete section \"" + section.name + "\"?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            dbSections.child(section.id).removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        // Also remove from CourseOptions
                                        dbCourseOptions.child(section.id).removeValue();
                                        Toast.makeText(SectionsActivity.this, "Section deleted", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(SectionsActivity.this, "Failed to delete", Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();
            }

>>>>>>> origin/pushnyodito4
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void loadSections() {
        dbSections.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                sectionList.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    SectionModel section = data.getValue(SectionModel.class);
                    if (section != null) {
                        section.id = data.getKey(); // keep Firebase key
                        sectionList.add(section);
                    }
                }
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                // Show message if no sections found
                if (sectionList.isEmpty()) {
                    Toast.makeText(SectionsActivity.this, "No sections found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SectionsActivity.this, "Failed to load sections: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ----------------- ADD SECTION -----------------
    private void showAddSectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_section, null);
        builder.setView(view);

        EditText sectionNameEt = view.findViewById(R.id.sectionNameEt);
        Spinner spinnerSpecialization = view.findViewById(R.id.spinnerSpecialization);
        Spinner spinnerYear = view.findViewById(R.id.spinnerYear);
        Button saveBtn = view.findViewById(R.id.saveSectionBtn);

        loadSpecializations(spinnerSpecialization);
        loadYears(spinnerYear);

        AlertDialog dialog = builder.create();
        dialog.show();

        saveBtn.setOnClickListener(v -> {
            String sectionName = sectionNameEt.getText().toString().trim();
            int specPosition = spinnerSpecialization.getSelectedItemPosition();
            int yearPosition = spinnerYear.getSelectedItemPosition();

            if (sectionName.isEmpty()) {
                sectionNameEt.setError("Enter section name");
                sectionNameEt.requestFocus();
                return;
            }
            if (specPosition <= 0 || yearPosition <= 0) {
                Toast.makeText(this, "Please select specialization and year", Toast.LENGTH_SHORT).show();
                return;
            }

            String specializationId = specializationIdList.get(specPosition);
            String specializationName = specializationList.get(specPosition);
            String yearId = yearIdList.get(yearPosition);
            String yearName = yearList.get(yearPosition);

            String sectionId = dbSections.push().getKey();
            SectionModel section = new SectionModel(
                    sectionId,
                    sectionName,
                    specializationId,
                    yearId,
                    specializationName,
                    yearName
            );

            dbSections.child(sectionId).setValue(section)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Section added successfully!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();

                        // Add to CourseOptions
                        CourseOption option = new CourseOption(
                                yearId,
                                yearName,
                                sectionId,
                                sectionName,
                                specializationId,
                                specializationName
                        );

                        dbCourseOptions.child(sectionId).setValue(option);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to add section: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        });
    }

    // ----------------- EDIT SECTION -----------------
    private void showEditSectionDialog(SectionModel section) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_section, null);
        builder.setView(view);

        EditText sectionNameEt = view.findViewById(R.id.sectionNameEt);
        Spinner spinnerSpecialization = view.findViewById(R.id.spinnerSpecialization);
        Spinner spinnerYear = view.findViewById(R.id.spinnerYear);
        Button saveBtn = view.findViewById(R.id.saveSectionBtn);
        saveBtn.setText("Update Section");

        // Prefill fields
        sectionNameEt.setText(section.name);
        loadSpecializations(spinnerSpecialization, section.specializationId);
        loadYears(spinnerYear, section.yearId);

        AlertDialog dialog = builder.create();
        dialog.show();

        saveBtn.setOnClickListener(v -> {
            String sectionName = sectionNameEt.getText().toString().trim();
            int specPosition = spinnerSpecialization.getSelectedItemPosition();
            int yearPosition = spinnerYear.getSelectedItemPosition();

            if (sectionName.isEmpty()) {
                sectionNameEt.setError("Enter section name");
                sectionNameEt.requestFocus();
                return;
            }

            if (specPosition <= 0 || yearPosition <= 0) {
                Toast.makeText(this, "Please select specialization and year", Toast.LENGTH_SHORT).show();
                return;
            }

            String specializationId = specializationIdList.get(specPosition);
            String specializationName = specializationList.get(specPosition);
            String yearId = yearIdList.get(yearPosition);
            String yearName = yearList.get(yearPosition);

            // Use the existing section.id here
            SectionModel updatedSection = new SectionModel(
                    section.id,
                    sectionName,
                    specializationId,
                    yearId,
                    specializationName,
                    yearName
            );

            dbSections.child(section.id).setValue(updatedSection)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Section updated successfully!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();

                        // Update CourseOptions
                        CourseOption option = new CourseOption(
                                yearId,
                                yearName,
                                section.id,
                                sectionName,
                                specializationId,
                                specializationName
                        );
                        dbCourseOptions.child(section.id).setValue(option);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to update section: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        });
    }

    // ----------------- LOAD SPINNERS -----------------
    private void loadSpecializations(Spinner spinner) {
        loadSpecializations(spinner, null);
    }

    private void loadSpecializations(Spinner spinner, String preselectId) {
        dbSpecializations.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                specializationList.clear();
                specializationIdList.clear();

                specializationList.add("Select Specialization");
                specializationIdList.add("");

                int preselectIndex = 0; // Default to "Select Specialization"

                for (DataSnapshot data : snapshot.getChildren()) {
                    String id = data.getKey();
                    String name = data.child("name").getValue(String.class);
                    if (name != null) {
                        specializationList.add(name);
                        specializationIdList.add(id);
                        if (id.equals(preselectId)) preselectIndex = specializationList.size() - 1;
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(SectionsActivity.this,
                        android.R.layout.simple_spinner_item, specializationList);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);

                spinner.setSelection(preselectIndex);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SectionsActivity.this, "Failed to load specializations", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadYears(Spinner spinner) {
        loadYears(spinner, null);
    }

    private void loadYears(Spinner spinner, String preselectId) {
        dbYears.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                yearList.clear();
                yearIdList.clear();

                yearList.add("Select Year");
                yearIdList.add("");

                int preselectIndex = 0; // Default to "Select Year"

                for (DataSnapshot data : snapshot.getChildren()) {
                    String id = data.getKey();
                    String name = data.child("name").getValue(String.class);
                    if (name != null) {
                        yearList.add(name);
                        yearIdList.add(id);
                        if (id.equals(preselectId)) preselectIndex = yearList.size() - 1;
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(SectionsActivity.this,
                        android.R.layout.simple_spinner_item, yearList);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);

                spinner.setSelection(preselectIndex);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SectionsActivity.this, "Failed to load years", Toast.LENGTH_SHORT).show();
            }
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