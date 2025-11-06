package com.example.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class SectionsActivity extends AppCompatActivity {

    // SIDEBAR COMPONENTS
    private DrawerLayout drawerLayout;
    private LinearLayout sidebarLayout;
    private ImageButton btnToggleSidebar;
    private LinearLayout curriculumDropdown, accountsDropdown;

    // Sidebar state management
    private boolean isCurriculumExpanded = true;
    private boolean isAccountsExpanded = false;

    // ORIGINAL COMPONENTS
    Button btnAddSection;
    RecyclerView recyclerSections;

    DatabaseReference dbSections, dbSpecializations, dbYears, dbCourseOptions;

    ArrayList<SectionModel> sectionList = new ArrayList<>();
    SectionAdapter adapter;

    ArrayList<String> specializationList = new ArrayList<>();
    ArrayList<String> specializationIdList = new ArrayList<>();

    ArrayList<String> yearList = new ArrayList<>();
    ArrayList<String> yearIdList = new ArrayList<>();

    // NEW UI COMPONENTS
    private TextView tvSectionCount;
    private LinearLayout emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sections);

        // INITIALIZE SIDEBAR
        initializeSidebar();
        setInitialSidebarState();

        // ORIGINAL CODE
        btnAddSection = findViewById(R.id.btnAddSection);
        recyclerSections = findViewById(R.id.recyclerSections);

        // NEW UI COMPONENTS INITIALIZATION
        tvSectionCount = findViewById(R.id.tvSectionCount);
        emptyState = findViewById(R.id.emptyState);

        // Firebase references
        dbSections = FirebaseDatabase.getInstance().getReference("Sections");
        dbSpecializations = FirebaseDatabase.getInstance().getReference("Specializations");
        dbYears = FirebaseDatabase.getInstance().getReference("Years");
        dbCourseOptions = FirebaseDatabase.getInstance().getReference("CourseOptions");

        // Setup RecyclerView
        recyclerSections.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SectionAdapter(sectionList, new SectionAdapter.OnSectionActionListener() {
            @Override
            public void onEdit(SectionModel section) {
                showEditSectionDialog(section);
            }

            @Override
            public void onDelete(SectionModel section) {
                dbSections.child(section.id).removeValue()
                        .addOnSuccessListener(aVoid -> {
                            // Also remove from CourseOptions
                            dbCourseOptions.child(section.id).removeValue();
                            Toast.makeText(SectionsActivity.this, "Section deleted", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(SectionsActivity.this, "Failed to delete", Toast.LENGTH_SHORT).show());
            }
        });
        recyclerSections.setAdapter(adapter);

        btnAddSection.setOnClickListener(v -> showAddSectionDialog());

        // Load existing sections
        loadSections();
    }

    // SIDEBAR INITIALIZATION
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

    // Method to set initial sidebar state
    private void setInitialSidebarState() {
        // Set Manage Sections button as active (highlighted)
        Button btnManageSections = findViewById(R.id.btnManageSections);
        btnManageSections.setBackgroundResource(R.drawable.sidebar_button_pressed);

        // Set curriculum dropdown as expanded by default
        curriculumDropdown.setVisibility(View.VISIBLE);
        Button btnCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        btnCurriculumHeader.setText("📘 Manage Curriculum ▴");

        // Set accounts dropdown as collapsed by default
        accountsDropdown.setVisibility(View.GONE);
        Button btnAccountsHeader = findViewById(R.id.btnManageAccountsHeader);
        btnAccountsHeader.setText("👤 Manage Accounts ▾");
    }

    // SIDEBAR NAVIGATION SETUP
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

    // Setup sidebar buttons functionality
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
            startActivity(new Intent(SectionsActivity.this, SpecializationsActivity.class));
        });

        btnManageYears.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SectionsActivity.this, YearsActivity.class));
        });

        btnManageSections.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            // No navigation needed since we're already in SectionsActivity
        });

        btnManageCourse.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SectionsActivity.this, CourseActivity.class));
        });

        btnManageSubjects.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SectionsActivity.this, SubjectActivity.class));
        });

        btnManageTeachers.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SectionsActivity.this, TeacherActivity.class));
        });

        btnManageStudents.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SectionsActivity.this, StudentActivity.class));
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

    // Back pressed handling for sidebar
    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(sidebarLayout)) {
            drawerLayout.closeDrawer(sidebarLayout);
        } else {
            super.onBackPressed();
        }
    }

    // ORIGINAL METHODS - WITH UI UPDATES
    private void loadSections() {
        dbSections.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                sectionList.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    SectionModel section = data.getValue(SectionModel.class);
                    if (section != null) {
                        section.id = data.getKey();
                        sectionList.add(section);
                    }
                }

                // UPDATE UI WITH COUNT AND EMPTY STATE
                updateUI();
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SectionsActivity.this, "Failed to load sections.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // NEW METHOD: Update UI based on data
    private void updateUI() {
        int count = sectionList.size();
        tvSectionCount.setText(count + " section" + (count != 1 ? "s" : ""));

        if (sectionList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerSections.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerSections.setVisibility(View.VISIBLE);
        }
    }

    // ----------------- ADD -----------------
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
            if (specPosition < 0 || yearPosition < 0) {
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
                            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        });
    }

    // ----------------- EDIT -----------------
    private void showEditSectionDialog(SectionModel section) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_section, null);
        builder.setView(view);

        EditText sectionNameEt = view.findViewById(R.id.sectionNameEt);
        Spinner spinnerSpecialization = view.findViewById(R.id.spinnerSpecialization);
        Spinner spinnerYear = view.findViewById(R.id.spinnerYear);
        Button saveBtn = view.findViewById(R.id.saveSectionBtn);
        saveBtn.setText("Update");

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
                            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
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

                int preselectIndex = -1;
                for (DataSnapshot data : snapshot.getChildren()) {
                    String id = data.getKey();
                    String name = data.child("name").getValue(String.class);
                    if (name != null) {
                        specializationList.add(name);
                        specializationIdList.add(id);
                        if (id.equals(preselectId)) preselectIndex = specializationIdList.size() - 1;
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(SectionsActivity.this,
                        android.R.layout.simple_spinner_item, specializationList);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);

                if (preselectIndex >= 0) spinner.setSelection(preselectIndex);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
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

                int preselectIndex = -1;
                for (DataSnapshot data : snapshot.getChildren()) {
                    String id = data.getKey();
                    String name = data.child("name").getValue(String.class);
                    if (name != null) {
                        yearList.add(name);
                        yearIdList.add(id);
                        if (id.equals(preselectId)) preselectIndex = yearIdList.size() - 1;
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(SectionsActivity.this,
                        android.R.layout.simple_spinner_item, yearList);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);

                if (preselectIndex >= 0) spinner.setSelection(preselectIndex);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }
}