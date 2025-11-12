package com.example.nextgen.teacher;

import android.os.Bundle;
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
import android.util.Log;


public class SubjectSectionActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SectionAdapter sectionAdapter;
    private List<StudentModel> studentList;
    private DatabaseReference studentsRef;
    private DatabaseReference submissionsRef;
    private String activityId; // add at top

    private SubmissionModel submission;

    public void setSubmission(SubmissionModel submission) { this.submission = submission; }
    public SubmissionModel getSubmission() { return submission; }




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_section);

        activityId = getIntent().getStringExtra("activityId");


        recyclerView = findViewById(R.id.recyclerViewStudents);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        studentList = new ArrayList<>();
        sectionAdapter = new SectionAdapter(studentList);
        recyclerView.setAdapter(sectionAdapter);

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        submissionsRef = FirebaseDatabase.getInstance().getReference("Submissions");

        // Get course info from intent (passed from TeacherActivities or Subject)
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
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
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
                        Log.d("SUB_LOG", "Loaded student: " + student.getFullName() + " (" + student.getStudentId() + ")");
                    }
                }

                if (studentList.isEmpty()) {
                    Toast.makeText(SubjectSectionActivity.this, "No students found in this class", Toast.LENGTH_SHORT).show();
                }
                if (activityId != null) {
                    loadSubmissions(activityId);
                }

                sectionAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("SUB_LOG", "Failed to load students: " + error.getMessage());
            }
        });
    }

    private void loadSubmissions(String activityId) {
        submissionsRef.orderByChild("activityId").equalTo(activityId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<SubmissionModel> submissionList = new ArrayList<>();
                        for (DataSnapshot snap : snapshot.getChildren()) {
                            SubmissionModel submission = snap.getValue(SubmissionModel.class);
                            if (submission != null) {
                                submission.setId(snap.getKey());
                                submissionList.add(submission);
                                Log.d("SUB_LOG", "Loaded submission: " + submission.getFileName() +
                                        " by student: " + submission.getStudentId() +
                                        " for activity: " + submission.getActivityId());
                            }
                        }

                        if (submissionList.isEmpty()) {
                            Toast.makeText(SubjectSectionActivity.this, "No submissions yet", Toast.LENGTH_SHORT).show();
                            Log.d("SUB_LOG", "No submissions found for activityId: " + activityId);
                        } else {
                            displaySubmissions(submissionList);
                        }
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
                // Compare Firebase UID instead of studentId
                if (s.getStudentId().equals(student.getUid())) {
                    student.setSubmission(s);
                    break;
                }
            }
        }
        sectionAdapter.notifyDataSetChanged();
    }



}
