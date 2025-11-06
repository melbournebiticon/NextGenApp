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

public class ExamMonitorActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private StudentExamAdapter adapter;
    private String examTitle;
    private String examId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_monitor);

        recyclerView = findViewById(R.id.recyclerStudents);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        examTitle = getIntent().getStringExtra("examTitle");
        examId = getIntent().getStringExtra("examId");
        android.util.Log.d("ExamMonitor", "examId from intent: " + examId);

        setTitle("Monitoring: " + examTitle);


        // Load students for this exam
        loadStudents();
    }

    // <-- Replace the old loadStudents() with this
    private void loadStudents() {
        // Get each part from intent
        String examSpecialization = getIntent().getStringExtra("examSpecialization");
        String examSectionName = getIntent().getStringExtra("examSectionName");
        String examYearName = getIntent().getStringExtra("examYearName");
        String examCourseName = getIntent().getStringExtra("examCourseName"); // NEW

        // Log intent values
        android.util.Log.d("ExamMonitor", "ExamSpecialization: " + examSpecialization);
        android.util.Log.d("ExamMonitor", "ExamSectionName: " + examSectionName);
        android.util.Log.d("ExamMonitor", "ExamYearName: " + examYearName);
        android.util.Log.d("ExamMonitor", "ExamCourseName: " + examCourseName);

        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<StudentExamStatus> students = new ArrayList<>();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    StudentModel student = ds.getValue(StudentModel.class);
                    if (student != null) {
                        String studentId = student.getStudentId();
                        // Skip students with missing IDs
                        // Remove 'String' and just assign
                        studentId = student.getStudentId();
                        if (studentId == null || studentId.trim().isEmpty()) {
                            // fallback: use Firebase key
                            studentId = ds.getKey();  // Firebase node key
                            student.setStudentId(studentId);

                            // Optionally update Firebase so studentId is saved for next time
                            ds.getRef().child("studentId").setValue(studentId);
                        }



                        // Null-safe strings
                        String studentSpec = student.getSpecializationName() != null ? student.getSpecializationName().trim() : "";
                        String studentSection = student.getSectionName() != null ? student.getSectionName().trim() : "";
                        String studentYear = student.getYearName() != null ? student.getYearName().trim() : "";
                        String studentCourse = student.getCourseName() != null ? student.getCourseName().trim() : "";

                        String examSpec = examSpecialization != null ? examSpecialization.trim() : "";
                        String examSection = examSectionName != null ? examSectionName.trim() : "";
                        String examYear = examYearName != null ? examYearName.trim() : "";
                        String examCourse = examCourseName != null ? examCourseName.trim() : "";

                        // Compare all fields
                        if (studentSpec.equalsIgnoreCase(examSpec)
                                && studentSection.equalsIgnoreCase(examSection)
                                && studentYear.equalsIgnoreCase(examYear)
                                && studentCourse.equalsIgnoreCase(examCourse)) {

                            students.add(new StudentExamStatus(
                                    studentId,
                                    student.getFullName(),
                                    false, // not yet joined
                                    false, // not ongoing
                                    0,     // answered 0
                                    studentCourse,
                                    studentSpec,
                                    studentYear,
                                    studentSection
                            ));

                        }
                    }
                }


                android.util.Log.d("ExamMonitor", "Total matched students: " + students.size());
                adapter = new StudentExamAdapter(students, examId); // ✅ matches constructor
                recyclerView.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ExamMonitorActivity.this, "Failed to load students", Toast.LENGTH_SHORT).show();
                android.util.Log.e("ExamMonitor", "Firebase error: " + error.getMessage());
            }
        });
    }
    private void resetStudentExam(String studentId) {
        DatabaseReference scoreRef = FirebaseDatabase.getInstance()
                .getReference("Scores")
                .child(studentId)
                .child(examId);

        scoreRef.removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(ExamMonitorActivity.this, "Exam reset for student: " + studentId, Toast.LENGTH_SHORT).show();
                // Optionally refresh the list or UI
                loadStudents();
            } else {
                Toast.makeText(ExamMonitorActivity.this, "Failed to reset exam for student: " + studentId, Toast.LENGTH_SHORT).show();
            }
        });
    }





}

