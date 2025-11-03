package com.example.nextgen.admin;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    Button addBtn;
    RecyclerView recyclerView;
    DatabaseReference dbRef;
    ArrayList<SpecializationModel> specializationList;
    SpecializationAdapter adapter;

    // SIDEBAR VARIABLES
    private DrawerLayout drawerLayout;
    private LinearLayout curriculumDropdown, accountsDropdown;
    private FirebaseAuth auth;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_specializations);

        // INITIALIZE SIDEBAR
        initializeSidebarViews();
        setupSidebarNavigation();

        // INITIALIZE FIREBASE
        setupFirebase();

        // RECYCLERVIEW SETUP
        initializeRecyclerView();
        loadSpecializations();
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

        // Toggle dropdowns
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
            Toast.makeText(this, "Already in Specializations", Toast.LENGTH_SHORT).show();
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
            navigateToActivity("Examinees");
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
                case "Teachers":
                    intent = new Intent(this, TeacherActivity.class);
                    break;
                case "Examinees":
                    intent = new Intent(this, StudentActivity.class);
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
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupFirebase() {
        dbRef = FirebaseDatabase.getInstance().getReference("Specializations");
        auth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);
    }

    private void initializeRecyclerView() {
        addBtn = findViewById(R.id.addSpecializationBtn);
        recyclerView = findViewById(R.id.specializationRecyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        specializationList = new ArrayList<>();

        addBtn.setOnClickListener(v -> showAddEditDialog(null, null));
    }

    private void loadSpecializations() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                specializationList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String id = ds.getKey();
                    String name = ds.child("name").getValue(String.class);
                    specializationList.add(new SpecializationModel(id, name));
                }
                adapter = new SpecializationAdapter(specializationList, SpecializationsActivity.this, dbRef);
                recyclerView.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SpecializationsActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Modal for Add/Edit
    public void showAddEditDialog(String id, String currentName) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_specialization);

        // Get references to all views
        TextView dialogTitle = dialog.findViewById(R.id.tvDialogTitle);
        EditText etName = dialog.findViewById(R.id.etSpecializationName);
        Button btnSave = dialog.findViewById(R.id.btnSaveSpecialization);

        // Set title based on add/edit mode
        if (id == null) {
            dialogTitle.setText("Add New Specialization");
            btnSave.setText("Add Specialization");
        } else {
            dialogTitle.setText("Edit Specialization");
            btnSave.setText("Update Specialization");
        }

        // Pre-fill name if editing
        if (currentName != null) {
            etName.setText(currentName);
        }

        // Save button
        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                etName.setError("Enter specialization name");
                return;
            }

            if (id == null) {
                // Add new specialization
                String newId = dbRef.push().getKey();
                dbRef.child(newId).child("name").setValue(name)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(SpecializationsActivity.this, "Specialization added successfully", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(SpecializationsActivity.this, "Failed to add specialization", Toast.LENGTH_SHORT).show();
                        });
            } else {
                // Update existing specialization
                dbRef.child(id).child("name").setValue(name)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(SpecializationsActivity.this, "Specialization updated successfully", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(SpecializationsActivity.this, "Failed to update specialization", Toast.LENGTH_SHORT).show();
                        });
            }
        });

        dialog.show();
    }
}