package com.example.nextgen.student;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.example.nextgen.admin.StudentModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;
import android.util.Log;


public class StudentDashboardActivity extends AppCompatActivity {

    private ImageView ivProfile;
    private TextView tvFullName, tvBirthday, tvEmail, tvContact,
            tvCourse, tvSpecialization, tvYear, tvSection;
    private Button btnLogout;

    private FirebaseAuth auth;
    private DatabaseReference studentsRef;

    private RecyclerView rvExams;
    private ExamAdapter examAdapter;
    private List<ExamModel> examList = new ArrayList<>();
    private DatabaseReference examsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        // RecyclerView for exams
        rvExams = findViewById(R.id.rvExams);
        rvExams.setLayoutManager(new LinearLayoutManager(this));

        examsRef = FirebaseDatabase.getInstance().getReference("Exams");

        // Initialize UI
        ivProfile = findViewById(R.id.ivProfile);
        tvFullName = findViewById(R.id.tvFullName);
        tvBirthday = findViewById(R.id.tvBirthday);
        tvEmail = findViewById(R.id.tvEmail);
        tvContact = findViewById(R.id.tvContact);
        tvCourse = findViewById(R.id.tvCourse);
        tvYear = findViewById(R.id.tvYear);
        tvSection = findViewById(R.id.tvSection);
        tvSpecialization = findViewById(R.id.tvSpecialization);
        btnLogout = findViewById(R.id.logoutBtn);

        // Firebase Auth
        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No logged-in user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        // Fetch student data
        studentsRef.orderByChild("uid").equalTo(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                StudentModel student = ds.getValue(StudentModel.class);
                                if (student != null) {
                                    populateStudentData(student);
                                    fetchExamsForStudent(student); // <-- fetch exams after loading student
                                }
                            }
                        } else {
                            Toast.makeText(StudentDashboardActivity.this, "Student record not found", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(StudentDashboardActivity.this, "Failed to fetch data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

        // Logout
        btnLogout.setOnClickListener(v -> {
            new SessionManager(this).clearSession();
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void populateStudentData(StudentModel student) {
        tvFullName.setText(student.getFullName());
        tvBirthday.setText(student.getBirthday());
        tvEmail.setText(student.getEmail());
        tvContact.setText(student.getContact());
        tvCourse.setText(student.getCourseName());
        tvSpecialization.setText(student.getSpecializationName());
        tvYear.setText(student.getYearName());
        tvSection.setText(student.getSectionName());

        if (student.getProfileImage() != null && !student.getProfileImage().isEmpty()) {
            byte[] decodedBytes = android.util.Base64.decode(student.getProfileImage(), android.util.Base64.DEFAULT);
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            ivProfile.setImageBitmap(bitmap);
        } else {
            ivProfile.setImageResource(R.drawable.examinee_default);
        }
    }

    // ===== Fetch exams assigned to this student's course/section =====
    private void fetchExamsForStudent(StudentModel student) {
        // Correct order to match Firebase courseDisplay
        String studentCourseDisplay = student.getCourseName()
                + " - " + student.getSpecializationName()
                + " - " + student.getYearName()
                + " - " + student.getSectionName();

        Log.d("DEBUG_COURSE_DISPLAY", "Querying exams for: " + studentCourseDisplay);

        // Log all existing exams courseDisplay values for debugging
        examsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String examCourseDisplay = ds.child("courseDisplay").getValue(String.class);
                    Log.d("DEBUG_EXAMS_RAW", "Exam courseDisplay in DB: " + examCourseDisplay);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        examsRef.orderByChild("courseDisplay").equalTo(studentCourseDisplay)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        examList.clear();
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                ExamModel exam = ds.getValue(ExamModel.class);
                                if (exam != null) {
                                    examList.add(exam);
                                }
                            }
                            examAdapter = new ExamAdapter(StudentDashboardActivity.this, examList);
                            rvExams.setAdapter(examAdapter);
                        } else {
                            Toast.makeText(StudentDashboardActivity.this, "No exams found for your course.", Toast.LENGTH_SHORT).show();
                            Log.d("DEBUG_COURSE_DISPLAY", "Exams snapshot is empty");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(StudentDashboardActivity.this, "Failed to fetch exams: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }




}
