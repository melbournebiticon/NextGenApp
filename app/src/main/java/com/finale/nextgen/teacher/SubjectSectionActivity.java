package com.finale.nextgen.teacher;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        // Get activity info
        activityId = getIntent().getStringExtra("activityId");
        maxScore = getIntent().getStringExtra("maxScore");
        if (maxScore == null || maxScore.isEmpty()) maxScore = "100";

        recyclerView = findViewById(R.id.recyclerViewStudents);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        studentList = new ArrayList<>();

        sectionAdapter = new SectionAdapter(studentList, maxScore);
        recyclerView.setAdapter(sectionAdapter);

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        submissionsRef = FirebaseDatabase.getInstance().getReference("Submissions");

        // Get class info
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
        studentsRef.addValueEventListener(new ValueEventListener() {
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
                    loadSubmissions(activityId);
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
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot snap : snapshot.getChildren()) {

                            // Read snapshot as Map to avoid Firebase conversion crash
                            Map<String, Object> data = (Map<String, Object>) snap.getValue();
                            if (data == null) continue;

                            SubmissionModel submission = new SubmissionModel();
                            submission.setSubmissionId(snap.getKey());
                            submission.setActivityId((String) data.get("activityId"));
                            submission.setStudentId((String) data.get("studentId"));
                            submission.setFileName((String) data.get("fileName"));
                            submission.setFileData((String) data.get("fileData"));

                            // Firebase-safe score conversion
                            submission.setScore((String) data.get("score"));

                            // Safe maxScore
                            Object maxScoreObj = data.get("maxScore");
                            submission.setMaxScore(maxScoreObj == null ? maxScore : String.valueOf(maxScoreObj));

                            // Boolean fields
                            Object resubmit = data.get("resubmitRequested");
                            submission.setResubmitRequested(resubmit instanceof Boolean && (Boolean) resubmit);

                            Object viewed = data.get("viewed");
                            submission.setViewed(viewed instanceof Boolean && (Boolean) viewed);

                            // Link to student
                            for (StudentModel student : studentList) {
                                if (student.getUid().equals(submission.getStudentId())) {
                                    student.setSubmission(submission);
                                    break;
                                }
                            }
                        }
                        sectionAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("SUB_LOG", "Failed to load submissions: " + error.getMessage());
                    }
                });
    }

    // Immediate local update
    public void updateStudentSubmission(String studentUid, SubmissionModel newSubmission) {
        // Pass Object instead of String to avoid Firebase conversion issues
        Object scoreObj = newSubmission.getScore();
        newSubmission.setScore((String) scoreObj);

        if (newSubmission.getMaxScore() == null || newSubmission.getMaxScore().isEmpty()) {
            newSubmission.setMaxScore(maxScore);
        }

        for (StudentModel student : studentList) {
            if (student.getUid().equals(studentUid)) {
                student.setSubmission(newSubmission);
                sectionAdapter.notifyDataSetChanged();
                break;
            }
        }
    }
}
