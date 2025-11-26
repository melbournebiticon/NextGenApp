package com.example.nextgen.teacher;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class SubjectSectionActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SectionAdapter sectionAdapter;
    private List<StudentModel> studentList;
    private DatabaseReference studentsRef;
    private DatabaseReference submissionsRef;
    private String activityId;
    private String maxScore; // Maximum score for this activity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_section);

        activityId = getIntent().getStringExtra("activityId");
        maxScore = getIntent().getStringExtra("maxScore");

        // Ensure maxScore is never null
        if (maxScore == null || maxScore.isEmpty()) {
            maxScore = "100";
        }

        recyclerView = findViewById(R.id.recyclerViewStudents);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        studentList = new ArrayList<>();

        // Pass maxScore to adapter
        sectionAdapter = new SectionAdapter(studentList, maxScore);
        recyclerView.setAdapter(sectionAdapter);

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        submissionsRef = FirebaseDatabase.getInstance().getReference("Submissions");

        // Get course info from intent
        String courseName = getIntent().getStringExtra("courseName");
        String specialization = getIntent().getStringExtra("specializationName");
        String year = getIntent().getStringExtra("yearName");
        String section = getIntent().getStringExtra("sectionName");

        if (courseName != null && specialization != null && year != null && section != null) {
            loadStudentsByClass(courseName, specialization, year, section);
        } else {
            Toast.makeText(this, "Class information not found!", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadStudentsByClass(String courseName, String specialization, String year, String section) {
        studentsRef.addValueEventListener(new ValueEventListener() { // continuous listener
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                studentList.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    StudentModel student = snap.getValue(StudentModel.class);
                    if (student != null &&
                            courseName.equals(student.getCourseName()) &&
                            specialization.equals(student.getSpecializationName()) &&
                            year.equals(student.getYearName()) &&
                            section.equals(student.getSectionName())) {
                        studentList.add(student);
                    }
                }
                sectionAdapter.notifyDataSetChanged();
                if (activityId != null) {
                    loadSubmissions(activityId); // also continuous
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("SUB_LOG", "Failed to load students: " + error.getMessage());
            }
        });
    }

    private void loadSubmissions(String activityId) {
        submissionsRef.orderByChild("activityId").equalTo(activityId)
                .addValueEventListener(new ValueEventListener() { // continuous listener
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot snap : snapshot.getChildren()) {
                            SubmissionModel submission = snap.getValue(SubmissionModel.class);
                            if (submission != null) {
                                submission.setId(snap.getKey());
                                // find the corresponding student and update submission
                                for (StudentModel student : studentList) {
                                    if (student.getUid().equals(submission.getStudentId())) {
                                        student.setSubmission(submission);
                                        break;
                                    }
                                }
                            }
                        }
                        sectionAdapter.notifyDataSetChanged(); // instantly reflect any change
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("SUB_LOG", "Failed to load submissions: " + error.getMessage());
                    }
                });
    }

    private void displaySubmissions(List<SubmissionModel> submissions) {
        for (StudentModel student : studentList) {
            for (SubmissionModel s : submissions) {
                if (s.getStudentId().equals(student.getUid())) {
                    student.setSubmission(s);
                    break;
                }
            }
        }
        sectionAdapter.notifyDataSetChanged();
    }

    // Update a single student's submission immediately (score or resubmit)
    public void updateStudentSubmission(String studentUid, SubmissionModel newSubmission) {
        for (StudentModel student : studentList) {
            if (student.getUid().equals(studentUid)) {
                student.setSubmission(newSubmission);
                sectionAdapter.notifyDataSetChanged(); // instantly reflect change
                break;
            }
        }
    }
}
