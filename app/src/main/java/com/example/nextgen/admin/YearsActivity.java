package com.example.nextgen.admin;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.example.nextgen.model.YearModel;
import com.example.nextgen.adapter.YearsAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class YearsActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private LinearLayout curriculumDropdown, accountsDropdown;
    private ImageButton btnToggleSidebar;

    private Button addYearBtn;
    private RecyclerView yearRecyclerView;
    private DatabaseReference dbRef;
    private ArrayList<YearModel> yearList;
    private YearsAdapter adapter;
    private SessionManager sessionManager;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_years);

        // CRITICAL FIX FOR SIDEBAR HEIGHT
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        // Initialize SessionManager and Firebase Auth
        sessionManager = new SessionManager(this);
        auth = FirebaseAuth.getInstance();

        initializeViews();
        setupClickListeners();
        setupFirebase();
        loadYears();
    }

    private void initializeViews() {
        try {
            // Initialize main views
            drawerLayout = findViewById(R.id.drawerLayout);
            curriculumDropdown = findViewById(R.id.curriculumDropdown);
            accountsDropdown = findViewById(R.id.accountsDropdown);

            // CRITICAL: Initialize the sidebar button - FIXED ID
            btnToggleSidebar = findViewById(R.id.btnToggleSidebar);

            // Initialize RecyclerView and button
            addYearBtn = findViewById(R.id.addYearBtn);
            yearRecyclerView = findViewById(R.id.yearRecyclerView);

            // Additional fix for drawer layout
            if (drawerLayout != null) {
                drawerLayout.setFitsSystemWindows(true);
            }

            // Check if all views are properly initialized
            if (btnToggleSidebar == null) {
                Log.e("YearsActivity", "btnToggleSidebar is NULL");
                Toast.makeText(this, "Error: Sidebar button not found", Toast.LENGTH_LONG).show();
            }
            if (addYearBtn == null) {
                Log.e("YearsActivity", "addYearBtn is NULL");
            }
            if (yearRecyclerView == null) {
                Log.e("YearsActivity", "yearRecyclerView is NULL");
            }

        } catch (Exception e) {
            Log.e("YearsActivity", "Error in initializeViews: " + e.getMessage());
            Toast.makeText(this, "Error initializing views", Toast.LENGTH_LONG).show();
        }
    }

    private void setupClickListeners() {
        // Sidebar toggle - FIXED ID
        if (btnToggleSidebar != null) {
            btnToggleSidebar.setOnClickListener(v -> toggleSidebar());
        } else {
            Log.e("YearsActivity", "Cannot set click listener - btnToggleSidebar is null");
        }

        // Dropdown headers
        Button btnManageCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        Button btnManageAccountsHeader = findViewById(R.id.btnManageAccountsHeader);

        if (btnManageCurriculumHeader != null) {
            btnManageCurriculumHeader.setOnClickListener(v -> toggleDropdown(curriculumDropdown));
        }
        if (btnManageAccountsHeader != null) {
            btnManageAccountsHeader.setOnClickListener(v -> toggleDropdown(accountsDropdown));
        }

        // Sidebar navigation buttons
        setupSidebarNavigation();

        // Add Year button
        if (addYearBtn != null) {
            addYearBtn.setOnClickListener(v -> showAddEditDialog(null, null));
        }
    }

    private void setupSidebarNavigation() {
        // Curriculum management
        findViewById(R.id.btnManageSpecializations).setOnClickListener(v -> {
            startActivity(new Intent(this, SpecializationsActivity.class));
            drawerLayout.closeDrawer(Gravity.START);
        });

        findViewById(R.id.btnManageYears).setOnClickListener(v ->
                drawerLayout.closeDrawer(Gravity.START));

        findViewById(R.id.btnManageSections).setOnClickListener(v -> {
            startActivity(new Intent(this, SectionsActivity.class));
            drawerLayout.closeDrawer(Gravity.START);
        });

        findViewById(R.id.btnManageCourse).setOnClickListener(v -> {
            startActivity(new Intent(this, CourseActivity.class));
            drawerLayout.closeDrawer(Gravity.START);
        });

        findViewById(R.id.btnManageSubjects).setOnClickListener(v -> {
            startActivity(new Intent(this, SubjectActivity.class));
            drawerLayout.closeDrawer(Gravity.START);
        });

        // Accounts management
        findViewById(R.id.btnManageTeachers).setOnClickListener(v -> {
            startActivity(new Intent(this, TeacherActivity.class));
            drawerLayout.closeDrawer(Gravity.START);
        });

        findViewById(R.id.btnManageStudents).setOnClickListener(v -> {
            startActivity(new Intent(this, StudentActivity.class));
            drawerLayout.closeDrawer(Gravity.START);
        });

        // Logout - FIXED IMPLEMENTATION
        Button logoutBtn = findViewById(R.id.logoutBtn);
        if (logoutBtn != null) {
            logoutBtn.setOnClickListener(v -> {
                performLogout();
            });
        } else {
            Log.e("YearsActivity", "logoutBtn is NULL");
        }
    }

    private void performLogout() {
        // Show confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Logout");
        builder.setMessage("Are you sure you want to logout?");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            // Clear session and logout
            sessionManager.clearSession();

            // Sign out from Firebase Auth
            if (auth.getCurrentUser() != null) {
                auth.signOut();
            }

            // Navigate to login activity
            Intent intent = new Intent(YearsActivity.this, com.example.nextgen.MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

            Toast.makeText(YearsActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("No", (dialog, which) -> {
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void setupFirebase() {
        dbRef = FirebaseDatabase.getInstance().getReference("Years");
        yearList = new ArrayList<>();

        // Setup RecyclerView
        yearRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new YearsAdapter(yearList, this, dbRef);
        yearRecyclerView.setAdapter(adapter);
    }

    private void toggleSidebar() {
        if (drawerLayout.isDrawerOpen(Gravity.START)) {
            drawerLayout.closeDrawer(Gravity.START);
        } else {
            drawerLayout.openDrawer(Gravity.START);
        }
    }

    private void toggleDropdown(LinearLayout dropdown) {
        if (dropdown.getVisibility() == View.VISIBLE) {
            dropdown.setVisibility(View.GONE);
        } else {
            dropdown.setVisibility(View.VISIBLE);
        }
    }

    private void loadYears() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                yearList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String id = ds.getKey();
                    String name = ds.child("name").getValue(String.class);
                    if (name != null) {
                        yearList.add(new YearModel(id, name));
                    }
                }
                adapter.notifyDataSetChanged();

                // Show message if no years found
                if (yearList.isEmpty()) {
                    Toast.makeText(YearsActivity.this, "No years found. Add a new year.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(YearsActivity.this, "Error loading years: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("YearsActivity", "Database error: " + error.getMessage());
            }
        });
    }

    public void showAddEditDialog(String id, String currentName) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_year);

        // Set dialog width and make it focusable
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dialog.setCancelable(true);

        EditText etName = dialog.findViewById(R.id.etYearName);
        Button btnSave = dialog.findViewById(R.id.btnSaveYear);

        // Set dialog title based on action
        if (currentName != null) {
            // Edit mode
            etName.setText(currentName);
        }

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError("Enter year name");
                return;
            }

            saveYear(id, name);
            dialog.dismiss();
        });

        // Show keyboard when dialog opens
        etName.requestFocus();
        dialog.show();
    }

    private void saveYear(String id, String name) {
        if (id == null) {
            // Add new year
            String newId = dbRef.push().getKey();
            if (newId != null) {
                dbRef.child(newId).child("name").setValue(name)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Year added successfully", Toast.LENGTH_SHORT).show();
                            Log.d("YearsActivity", "Year added: " + name);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to add year: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.e("YearsActivity", "Error adding year: " + e.getMessage());
                        });
            }
        } else {
            // Update existing year
            dbRef.child(id).child("name").setValue(name)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Year updated successfully", Toast.LENGTH_SHORT).show();
                        Log.d("YearsActivity", "Year updated: " + name);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update year: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e("YearsActivity", "Error updating year: " + e.getMessage());
                    });
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up any resources if needed
    }
}