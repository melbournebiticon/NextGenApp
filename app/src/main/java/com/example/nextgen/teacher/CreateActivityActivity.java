package com.example.nextgen.teacher;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateActivityActivity extends AppCompatActivity {

    EditText etTitle, etDescription, etDueDate;
    Spinner spTargetCourse, spTargetSubject;
    Button btnPickDate, btnCreate;
    Calendar calendar;
    String teacherName; // add this

    DatabaseReference activitiesRef, coursesRef, subjectsRef, teacherRef;
    SessionManager sessionManager;

    ArrayList<String> courseDisplayList = new ArrayList<>();
    ArrayList<String> assignedSubjects = new ArrayList<>();
    ArrayList<String> subjectIdList = new ArrayList<>();
    ArrayList<String> subjectCodeList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_activity);

        sessionManager = new SessionManager(this);

        etTitle = findViewById(R.id.etActivityTitle);
        etDescription = findViewById(R.id.etActivityDescription);
        etDueDate = findViewById(R.id.etDueDate);
        spTargetCourse = findViewById(R.id.spTargetCourseSection);   // new spinner for course-section
        spTargetSubject = findViewById(R.id.spTargetSubject); // new spinner for subject
        btnPickDate = findViewById(R.id.btnPickDate);
        btnCreate = findViewById(R.id.btnCreateActivity);

        calendar = Calendar.getInstance();
        activitiesRef = FirebaseDatabase.getInstance().getReference("Activities");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        teacherRef = FirebaseDatabase.getInstance().getReference("Teachers");

        fetchTeacherData();

        // Date picker
        btnPickDate.setOnClickListener(v -> {
            DatePickerDialog datePicker = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(year, month, dayOfMonth);

                        // After picking date, open time picker
                        new android.app.TimePickerDialog(
                                this,
                                (timeView, hourOfDay, minute) -> {
                                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                    calendar.set(Calendar.MINUTE, minute);

                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                                    etDueDate.setText(sdf.format(calendar.getTime()));
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                false
                        ).show();
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePicker.show();
        });


        btnCreate.setOnClickListener(v -> createActivity());

        // Update subjects when course changes
        spTargetCourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCourse = courseDisplayList.get(position);
                loadSubjectsForCourse(selectedCourse);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void fetchTeacherData() {
        String teacherId = sessionManager.getUserId();
        if (teacherId == null) return;

        teacherRef.child(teacherId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseDisplayList.clear();
                assignedSubjects.clear();

                // Get teacher name
                teacherName = snapshot.child("fullName").getValue(String.class); // assuming "name" field exists

                for (DataSnapshot courseSnap : snapshot.child("courseDisplays").getChildren()) {
                    String course = courseSnap.getValue(String.class);
                    if (course != null) courseDisplayList.add(course);
                }

                for (DataSnapshot subSnap : snapshot.child("assignedSubjects").getChildren()) {
                    String subject = subSnap.getValue(String.class);
                    if (subject != null) assignedSubjects.add(subject);
                }

                if (courseDisplayList.isEmpty()) courseDisplayList.add("No Course Assigned");

                ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(CreateActivityActivity.this,
                        android.R.layout.simple_spinner_dropdown_item, courseDisplayList);
                spTargetCourse.setAdapter(courseAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CreateActivityActivity.this, "Failed to load teacher data", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void loadSubjectsForCourse(String selectedCourseDisplay) {
        ArrayList<String> subjectList = new ArrayList<>();
        ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, subjectList);
        spTargetSubject.setAdapter(subjectAdapter);

        subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                subjectList.clear();
                subjectIdList.clear(); // ✅ clear previous IDs
                subjectCodeList.clear();

                for (DataSnapshot subjectSnap : snapshot.getChildren()) {
                    String subjectId = subjectSnap.getKey();
                    String name = subjectSnap.child("name").getValue(String.class);
                    String code = subjectSnap.child("code").getValue(String.class); // ✅ fetch code
                    String courseName = subjectSnap.child("courseName").getValue(String.class);
                    String specializationName = subjectSnap.child("specializationName").getValue(String.class);
                    String yearName = subjectSnap.child("yearName").getValue(String.class);
                    String sectionName = subjectSnap.child("sectionName").getValue(String.class);

                    String display = courseName + " - " + specializationName + " - " + yearName + " - " + sectionName;

                    if (display.equals(selectedCourseDisplay) && assignedSubjects.contains(subjectId)) {
                        subjectList.add(name);
                        subjectIdList.add(subjectId);
                        subjectCodeList.add(code); // ✅ save code
                    }
                }


                if (subjectList.isEmpty()) {
                    subjectList.add("No subjects found for this course");
                    subjectIdList.add(""); // to keep alignment
                }

                subjectAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CreateActivityActivity.this, "Failed to load subjects", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void createActivity() {
        String title = etTitle.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();
        String dueDate = etDueDate.getText().toString().trim();
        String selectedCourse = spTargetCourse.getSelectedItem() != null ? spTargetCourse.getSelectedItem().toString() : "";
        String selectedSubject = spTargetSubject.getSelectedItem() != null ? spTargetSubject.getSelectedItem().toString() : "";

        if (title.isEmpty() || desc.isEmpty() || dueDate.isEmpty() ||
                selectedCourse.isEmpty() || selectedSubject.isEmpty() ||
                selectedSubject.equals("No subjects found for this course")) {
            Toast.makeText(this, "Please fill all fields properly", Toast.LENGTH_SHORT).show();
            return;
        }

        String teacherId = sessionManager.getUserId();
        String activityId = activitiesRef.push().getKey();

        Map<String, Object> activityMap = new HashMap<>();
        activityMap.put("id", activityId);
        activityMap.put("teacherId", teacherId);
        activityMap.put("teacherName", teacherName);
        activityMap.put("title", title);
        activityMap.put("description", desc);
        activityMap.put("dueDate", dueDate);
        activityMap.put("courseDisplay", selectedCourse);
        activityMap.put("subject", selectedSubject);
        activityMap.put("subjectCode", getSelectedSubjectCode()); // ✅ new line
        activityMap.put("subjectId", getSelectedSubjectId());
        activityMap.put("createdAt", System.currentTimeMillis());

        if (activityId != null) {
            activitiesRef.child(activityId).setValue(activityMap)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Activity posted!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this,
                            "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }
    private String getSelectedSubjectId() {
        int pos = spTargetSubject.getSelectedItemPosition();
        if (pos >= 0 && pos < subjectIdList.size()) {
            return subjectIdList.get(pos);
        }
        return null;
    }
    private String getSelectedSubjectCode() {
        int pos = spTargetSubject.getSelectedItemPosition();
        if (pos >= 0 && pos < subjectCodeList.size()) {
            return subjectCodeList.get(pos);
        }
        return null;
    }


}
