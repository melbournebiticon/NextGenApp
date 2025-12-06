package com.finale.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.google.firebase.database.*;


import java.util.ArrayList;
import java.util.List;

public class CourseActivity extends AppCompatActivity {


    private Toolbar toolbar;

    // ORIGINAL COMPONENTS - WALANG BINAGO
    private EditText etCourseName;
    private Button btnAddCourse;
    private Spinner spinnerSections;
    private RecyclerView recyclerCourses;
    private LinearLayout emptyState;
    private TextView tvCourseCount;

    private CourseAdapter adapter;
    private final List<CourseModel> courseList = new ArrayList<>();
    private final List<CourseOption> courseOptionList = new ArrayList<>();
    private DatabaseReference coursesRef, courseOptionsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course);

        initializeToolbarAndBackNavigation();

        ImageView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            // Go back to AdminActivity
            Intent intent = new Intent(this, AdminActivity.class);
            startActivity(intent);
            finish();
        });


        // ORIGINAL CODE - WALANG BINAGO
        etCourseName = findViewById(R.id.etCourseName);
        btnAddCourse = findViewById(R.id.btnAddCourse);
        spinnerSections = findViewById(R.id.spinnerSection);
        recyclerCourses = findViewById(R.id.recyclerCourses);
        emptyState = findViewById(R.id.emptyState);
        tvCourseCount = findViewById(R.id.tvCourseCount); // FIXED: May ID na ito sa XML

        recyclerCourses.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CourseAdapter(this, courseList);
        recyclerCourses.setAdapter(adapter);

        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        courseOptionsRef = FirebaseDatabase.getInstance().getReference("CourseOptions");

        // Load course options into spinner
        loadCourseOptions();

        btnAddCourse.setOnClickListener(v -> addCourse());

        // Load all courses in RecyclerView
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


    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, AdminActivity.class);
        startActivity(intent);
        finish();
    }



    private void loadCourseOptions() {
        courseOptionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                List<String> names = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseOption option = ds.getValue(CourseOption.class);
                    if (option != null) {
                        courseOptionList.add(option);
                        names.add(option.getSpecializationName() + " - " + option.getSectionName() + " - " + option.getYearName());
                    }
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(CourseActivity.this,
                        android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerSections.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void addCourse() {
        String name = etCourseName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Enter course name", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedPos = spinnerSections.getSelectedItemPosition();
        if (selectedPos < 0) {
            Toast.makeText(this, "Select a course option", Toast.LENGTH_SHORT).show();
            return;
        }

        CourseOption selectedOption = courseOptionList.get(selectedPos);

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
                    Toast.makeText(this, "Course added", Toast.LENGTH_SHORT).show();
                    etCourseName.setText("");
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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
                updateUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void updateUI() {
        int count = courseList.size();

        // FIXED: Hindi na kailangan ng null check kung may ID na sa XML
        tvCourseCount.setText(count + " course" + (count != 1 ? "s" : ""));

        if (courseList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerCourses.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerCourses.setVisibility(View.VISIBLE);
        }
    }
}