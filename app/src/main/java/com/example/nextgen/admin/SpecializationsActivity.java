package com.example.nextgen.admin;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class SpecializationsActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

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

        // Initialize Toolbar and Navigation
        initializeToolbarAndNavigation();

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
        navigationView.setCheckedItem(R.id.nav_specializations);
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
            // We're already in SpecializationsActivity
            // Just close the drawer
        } else if (id == R.id.nav_years) {
            startActivity(new Intent(this, YearsActivity.class));
        } else if (id == R.id.nav_sections) {
            startActivity(new Intent(this, SectionsActivity.class));
        } else if (id == R.id.nav_courses) {
            startActivity(new Intent(this, CourseActivity.class));
        } else if (id == R.id.nav_subjects) {
            startActivity(new Intent(this, SubjectActivity.class));
        } else if (id == R.id.nav_teachers) {
            startActivity(new Intent(this, TeacherActivity.class));
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