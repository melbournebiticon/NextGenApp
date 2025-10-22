package com.example.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.example.nextgen.teacher.AppDatabase;
import com.google.firebase.auth.FirebaseAuth;

public class AdminActivity extends AppCompatActivity {

    Button btnSpecializations, btnYears, btnSections, btnCourse, btnSubjects, btnTeachers, btnStudents, logoutBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        btnSpecializations = findViewById(R.id.btnManageSpecializations);
        btnYears = findViewById(R.id.btnManageYears);
        btnSections = findViewById(R.id.btnManageSections);
        btnCourse = findViewById(R.id.btnManageCourse);
        btnSubjects = findViewById(R.id.btnManageSubjects);
        btnTeachers = findViewById(R.id.btnManageTeachers);
        btnStudents = findViewById(R.id.btnManageStudents);
        logoutBtn = findViewById(R.id.logoutBtn);

        btnSpecializations.setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, SpecializationsActivity.class))
        );

        btnYears.setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, YearsActivity.class))
        );
        btnSections.setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, SectionsActivity.class))
        );
        btnCourse.setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, CourseActivity.class))
        );
        btnSubjects.setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, SubjectActivity.class))
        );
        btnTeachers.setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, TeacherActivity.class))
        );
        btnStudents.setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, StudentActivity.class))
        );


        logoutBtn.setOnClickListener(v -> {
            // Clear session
            SessionManager sessionManager = new SessionManager(this);
            sessionManager.clearSession();

            // Sign out from Firebase
            FirebaseAuth.getInstance().signOut();

            // Redirect to login
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
