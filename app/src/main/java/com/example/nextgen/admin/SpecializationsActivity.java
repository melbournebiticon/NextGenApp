package com.example.nextgen.admin;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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

public class SpecializationsActivity extends AppCompatActivity {

    // SIDEBAR COMPONENTS
    private DrawerLayout drawerLayout;
    private LinearLayout sidebarLayout;
    private ImageButton btnToggleSidebar;
    private LinearLayout curriculumDropdown, accountsDropdown;

    // Sidebar state management
    private boolean isCurriculumExpanded = true;
    private boolean isAccountsExpanded = false;

    // ORIGINAL COMPONENTS
    Button addBtn;
    RecyclerView recyclerView;
    DatabaseReference dbRef;
    ArrayList<SpecializationModel> specializationList;
    SpecializationAdapter adapter;

    // NEW UI COMPONENTS
    private LinearLayout emptyState;
    private TextView tvSpecializationCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_specializations);

        // INITIALIZE SIDEBAR
        initializeSidebar();
        setInitialSidebarState();

        // ORIGINAL CODE
        addBtn = findViewById(R.id.addSpecializationBtn);
        recyclerView = findViewById(R.id.specializationRecyclerView);

        // NEW UI COMPONENTS INITIALIZATION
        emptyState = findViewById(R.id.emptyState);
        tvSpecializationCount = findViewById(R.id.tvSpecializationCount);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        specializationList = new ArrayList<>();
        dbRef = FirebaseDatabase.getInstance().getReference("Specializations");

        addBtn.setOnClickListener(v -> showAddEditDialog(null, null));

        loadSpecializations();
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
        // Set Manage Specializations button as active (highlighted)
        Button btnManageSpecializations = findViewById(R.id.btnManageSpecializations);
        btnManageSpecializations.setBackgroundResource(R.drawable.sidebar_button_pressed);

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
            // No navigation needed since we're already in SpecializationsActivity
        });

        btnManageYears.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SpecializationsActivity.this, YearsActivity.class));
        });

        btnManageSections.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SpecializationsActivity.this, SectionsActivity.class));
        });

        btnManageCourse.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SpecializationsActivity.this, CourseActivity.class));
        });

        btnManageSubjects.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SpecializationsActivity.this, SubjectActivity.class));
        });

        btnManageTeachers.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SpecializationsActivity.this, TeacherActivity.class));
        });

        btnManageStudents.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SpecializationsActivity.this, StudentActivity.class));
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

    private void loadSpecializations() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                specializationList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String id = ds.getKey();
                    String name = ds.child("name").getValue(String.class);
                    specializationList.add(new SpecializationModel(id, name));
                }

                // NEW: Update UI with count and empty state
                updateUI();

                // Pass the activity itself, not a method reference
                adapter = new SpecializationAdapter(specializationList, SpecializationsActivity.this, dbRef);
                recyclerView.setAdapter(adapter);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(SpecializationsActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // NEW METHOD: Update UI based on data
    private void updateUI() {
        int count = specializationList.size();
        tvSpecializationCount.setText(count + " specialization" + (count != 1 ? "s" : ""));

        if (specializationList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    // Modal for Add/Edit
    public void showAddEditDialog(String id, String currentName) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_specialization);

        EditText etName = dialog.findViewById(R.id.etSpecializationName);
        Button btnSave = dialog.findViewById(R.id.btnSaveSpecialization);

        // NEW: Set dialog title based on action
        TextView tvDialogTitle = dialog.findViewById(R.id.tvDialogTitle);
        if (id != null) {
            tvDialogTitle.setText("Edit Specialization");
        } else {
            tvDialogTitle.setText("Add Specialization");
        }

        if (currentName != null) etName.setText(currentName);

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError("Enter name");
                return;
            }

            if (id == null) {
                // Add new specialization
                String newId = dbRef.push().getKey();
                dbRef.child(newId).child("name").setValue(name);
                Toast.makeText(this, "Specialization added successfully", Toast.LENGTH_SHORT).show();
            } else {
                // Edit existing specialization
                dbRef.child(id).child("name").setValue(name);
                Toast.makeText(this, "Specialization updated successfully", Toast.LENGTH_SHORT).show();
            }

            dialog.dismiss();
        });

        // NEW: Add cancel button functionality
        Button btnCancel = dialog.findViewById(R.id.btnCancelSpecialization);
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}