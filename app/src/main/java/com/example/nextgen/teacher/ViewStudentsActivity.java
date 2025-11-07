package com.example.nextgen.teacher;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.admin.CourseModel;
import com.example.nextgen.admin.StudentModel;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class ViewStudentsActivity extends AppCompatActivity {

    private Spinner spinnerCourses;
    private RecyclerView recyclerStudents;

    private DatabaseReference studentsRef, coursesRef;

    private List<CourseModel> courseList = new ArrayList<>();
    private List<StudentModel> studentList = new ArrayList<>();
    private ViewStudentsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_students_unique);

        spinnerCourses = findViewById(R.id.spinnerCourses);
        recyclerStudents = findViewById(R.id.recyclerStudents);

        recyclerStudents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ViewStudentsAdapter(studentList);
        recyclerStudents.setAdapter(adapter);

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");

        loadCourses();

        spinnerCourses.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CourseModel selectedCourse = courseList.get(position);
                loadStudents(selectedCourse.getId());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadCourses() {
        coursesRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot courseSnapshot) {
                courseList.clear();
                List<String> displayNames = new ArrayList<>();

                // Loop through all courses
                for (DataSnapshot ds : courseSnapshot.getChildren()) {
                    CourseModel course = ds.getValue(CourseModel.class);
                    if (course != null) {
                        String courseId = course.getId();

                        // Check if there are students assigned to this course
                        studentsRef.orderByChild("courseId").equalTo(courseId)
                                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot studentSnapshot) {
                                        if (studentSnapshot.exists()) {
                                            courseList.add(course);
                                            displayNames.add(course.getName() + " - " +
                                                    course.getSpecializationName() + " - " +
                                                    course.getYearName() + " - " +
                                                    course.getSectionName());

                                            // Update spinner when all courses are processed
                                            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                                    ViewStudentsActivity.this,
                                                    android.R.layout.simple_spinner_item,
                                                    displayNames
                                            );
                                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                            spinnerCourses.setAdapter(adapter);
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {}
                                });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ViewStudentsActivity.this, "Failed to load courses", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void loadStudents(String courseId) {
        studentsRef.orderByChild("courseId").equalTo(courseId)
                .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        studentList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            StudentModel s = ds.getValue(StudentModel.class);
                            if (s != null) {
                                studentList.add(s);
                            }
                        }
                        adapter.notifyDataSetChanged();

                        if (studentList.isEmpty()) {
                            Toast.makeText(ViewStudentsActivity.this, "No students found for this course", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ViewStudentsActivity.this, "Failed to load students", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
