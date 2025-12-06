package com.finale.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.finale.nextgen.R;

public class AcademicsActivity extends AppCompatActivity {
    // Change to CardView, as defined in your XML layout
    CardView btnSpecializations, btnYears, btnSections, btnCourses, btnSubjects;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_academics);

        // Link Java variables to the correct XML CardView IDs
        btnSpecializations = findViewById(R.id.cardSpecializations);
        btnYears = findViewById(R.id.cardYears);
        btnSections = findViewById(R.id.cardSections);
        btnCourses = findViewById(R.id.cardCourses);
        btnSubjects = findViewById(R.id.cardSubjects);

        btnSpecializations.setOnClickListener(v ->
                startActivity(new Intent(this, SpecializationsActivity.class)));
        btnYears.setOnClickListener(v ->
                startActivity(new Intent(this, YearsActivity.class)));
        btnSections.setOnClickListener(v ->
                startActivity(new Intent(this, SectionsActivity.class)));
        btnCourses.setOnClickListener(v ->
                startActivity(new Intent(this, CourseActivity.class)));
        btnSubjects.setOnClickListener(v ->
                startActivity(new Intent(this, SubjectActivity.class)));
    }
}
