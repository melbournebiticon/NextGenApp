package com.finale.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.database.*;


import java.util.ArrayList;
import java.util.List;

public class SubjectActivity extends AppCompatActivity  {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    // ORIGINAL COMPONENTS
    private EditText etSubjectCode, etSubjectName;
    private Spinner spinnerCourses;
    private Button btnAddSubject;
    private RecyclerView recyclerSubjects;

    // NEW UI COMPONENTS
    private TextView tvSubjectCount;
    private LinearLayout emptyState;

    private SubjectAdapter adapter;
    private final List<SubjectModel> subjectList = new ArrayList<>();
    private final List<SubjectOption> subjectOptionList = new ArrayList<>();

    private DatabaseReference subjectsRef, coursesRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject);

        initializeToolbarAndBackNavigation();

        ImageView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            // Go back to AdminActivity
            Intent intent = new Intent(this, AdminActivity.class);
            startActivity(intent);
            finish();
        });

        // INITIALIZE COMPONENTS
        etSubjectCode = findViewById(R.id.etSubjectCode);
        etSubjectName = findViewById(R.id.etSubjectName);
        spinnerCourses = findViewById(R.id.spinnerCourseOption);
        btnAddSubject = findViewById(R.id.btnAddSubject);
        recyclerSubjects = findViewById(R.id.recyclerSubjects);

        // NEW UI COMPONENTS INITIALIZATION
        tvSubjectCount = findViewById(R.id.tvSubjectCount);
        emptyState = findViewById(R.id.emptyState);

        // RecyclerView setup WITH CLICK LISTENERS
        recyclerSubjects.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectAdapter(subjectList);

        // ADD CLICK LISTENERS TO ADAPTER
        adapter.setOnItemClickListener(new SubjectAdapter.OnItemClickListener() {
            @Override
            public void onEditClick(int position) {
                editSubject(position);
            }

            @Override
            public void onDeleteClick(int position) {
                deleteSubject(position);
            }
        });

        recyclerSubjects.setAdapter(adapter);

        // Firebase
        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");

        // Load spinner options and existing subjects
        loadSubjectOptions();
        loadSubjects();

        // Add subject
        btnAddSubject.setOnClickListener(v -> addSubject());
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
        Intent intent = new Intent(this, AdminActivity.class);
        startActivity(intent);
        finish();
    }


    // ORIGINAL METHODS
    private void loadSubjectOptions() {
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                subjectOptionList.clear();
                List<String> displayNames = new ArrayList<>();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel course = ds.getValue(CourseModel.class);
                    if (course != null) {
                        SubjectOption option = new SubjectOption(
                                course.getId(),
                                course.getName(),
                                course.getSpecializationName(),
                                course.getYearName(),
                                course.getSectionName()
                        );
                        subjectOptionList.add(option);
                        displayNames.add(option.toString());
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(SubjectActivity.this,
                        android.R.layout.simple_spinner_item, displayNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCourses.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void addSubject() {
        String code = etSubjectCode.getText().toString().trim();
        String name = etSubjectName.getText().toString().trim();

        if (TextUtils.isEmpty(code)) {
            etSubjectCode.setError("Enter subject code");
            etSubjectCode.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(name)) {
            etSubjectName.setError("Enter subject name");
            etSubjectName.requestFocus();
            return;
        }

        int pos = spinnerCourses.getSelectedItemPosition();
        if (pos < 0) {
            Toast.makeText(this, "Select a course option", Toast.LENGTH_SHORT).show();
            return;
        }

        SubjectOption selectedOption = subjectOptionList.get(pos);

        String id = subjectsRef.push().getKey();
        if (id == null) {
            Toast.makeText(this, "Error generating ID", Toast.LENGTH_SHORT).show();
            return;
        }

        SubjectModel subject = new SubjectModel(
                id,
                code,
                name,
                selectedOption.getCourseId(),
                selectedOption.getCourseName(),
                selectedOption.getSpecializationName(),
                selectedOption.getYearName(),
                selectedOption.getSectionName()
        );

        subjectsRef.child(id).setValue(subject)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Subject added successfully", Toast.LENGTH_SHORT).show();
                    etSubjectCode.setText("");
                    etSubjectName.setText("");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void loadSubjects() {
        subjectsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                subjectList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    SubjectModel s = ds.getValue(SubjectModel.class);
                    if (s != null) subjectList.add(s);
                }

                // UPDATE UI WITH COUNT AND EMPTY STATE
                updateUI();
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    // NEW METHOD: Update UI based on data
    private void updateUI() {
        int count = subjectList.size();
        tvSubjectCount.setText(count + " subject" + (count != 1 ? "s" : ""));

        if (subjectList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerSubjects.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerSubjects.setVisibility(View.VISIBLE);
        }
    }

    // UPDATED METHOD: Edit Subject with Dialog
    private void editSubject(int position) {
        SubjectModel subject = subjectList.get(position);

        // Create edit dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Subject");

        // Inflate custom layout
        View dialogView = getLayoutInflater().inflate(R.layout.edit_subject_dialog, null);
        EditText etEditCode = dialogView.findViewById(R.id.etEditSubjectCode);
        EditText etEditName = dialogView.findViewById(R.id.etEditSubjectName);

        // Pre-fill current data
        etEditCode.setText(subject.getCode());
        etEditName.setText(subject.getName());

        builder.setView(dialogView);
        builder.setPositiveButton("Update", (dialog, which) -> {
            String newCode = etEditCode.getText().toString().trim();
            String newName = etEditName.getText().toString().trim();

            if (TextUtils.isEmpty(newCode)) {
                Toast.makeText(this, "Subject code cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(newName)) {
                Toast.makeText(this, "Subject name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Update subject in Firebase
            updateSubject(subject.getId(), newCode, newName);
        });
        builder.setNegativeButton("Cancel", null);

        builder.show();
    }

    // NEW METHOD: Update Subject in Firebase
    private void updateSubject(String subjectId, String newCode, String newName) {
        DatabaseReference subjectRef = subjectsRef.child(subjectId);

        subjectRef.child("code").setValue(newCode);
        subjectRef.child("name").setValue(newName)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Subject updated successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update subject: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // NEW METHOD: Delete Subject
    private void deleteSubject(int position) {
        SubjectModel subject = subjectList.get(position);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Delete Subject")
                .setMessage("Are you sure you want to delete " + subject.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    subjectsRef.child(subject.getId()).removeValue()
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(this, "Subject deleted", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}