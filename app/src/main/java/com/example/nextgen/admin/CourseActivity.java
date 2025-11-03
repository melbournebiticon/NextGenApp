package com.example.nextgen.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.view.View;
import android.view.LayoutInflater;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class CourseActivity extends AppCompatActivity {

    private EditText etCourseName;
    private Button btnAddCourse;

    private RecyclerView recyclerCourses;

    private CourseAdapter adapter;
    private final List<CourseModel> courseList = new ArrayList<>();
    private final List<CourseOption> courseOptionList = new ArrayList<>();
    private DatabaseReference coursesRef, courseOptionsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course);

        etCourseName = findViewById(R.id.etCourseName);
        btnAddCourse = findViewById(R.id.btnAddCourse);
        recyclerCourses = findViewById(R.id.recyclerCourses);

        recyclerCourses.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CourseAdapter(this, courseList);
        recyclerCourses.setAdapter(adapter);

        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        courseOptionsRef = FirebaseDatabase.getInstance().getReference("CourseOptions");

        // Load course options into spinner
        loadCourseOptions();

        btnAddCourse.setOnClickListener(v -> showAddCourseDialog());


        // Load all courses in RecyclerView
        loadCourses();
    }
    private void showAddCourseDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_course, null);

        EditText etCourseNameDialog = dialogView.findViewById(R.id.etCourseName);
        Spinner spinnerSectionsDialog = dialogView.findViewById(R.id.spinnerSection);

        // Populate spinner options
        List<String> spinnerNames = new ArrayList<>();
        for (CourseOption option : courseOptionList) {
            spinnerNames.add(option.getSpecializationName() + " - " +
                    option.getSectionName() + " - " +
                    option.getYearName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, spinnerNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSectionsDialog.setAdapter(adapter);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Add Course")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = etCourseNameDialog.getText().toString().trim();
                    int selectedPos = spinnerSectionsDialog.getSelectedItemPosition();

                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(this, "Enter course name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (selectedPos < 0) {
                        Toast.makeText(this, "Select a course option", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    CourseOption selectedOption = courseOptionList.get(selectedPos);
                    String id = coursesRef.push().getKey();
                    if (id == null) return;

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
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Course added", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
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
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
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
}
