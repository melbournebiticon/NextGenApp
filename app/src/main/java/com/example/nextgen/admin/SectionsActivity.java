package com.example.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;

public class SectionsActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    // ORIGINAL COMPONENTS
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

        initializeToolbarAndBackNavigation();

        ImageView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            // Go back to AdminActivity
            Intent intent = new Intent(this, AdminActivity.class);
            startActivity(intent);
            finish();
        });

        // ORIGINAL CODE
        FloatingActionButton fabAddSection = findViewById(R.id.addSectionFab);
        fabAddSection.setOnClickListener(v -> showAddSectionDialog());

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


        // Load existing sections
        loadSections();
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
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
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