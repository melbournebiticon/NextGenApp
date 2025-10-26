package com.example.nextgen.student;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.nextgen.R;

public class ResultActivity extends AppCompatActivity {

    private TextView tvCourseCode, tvSubjectName, tvTeacherName;
    private TextView tvStudentName, tvStudentId;
    private TextView tvScoreRaw, tvScorePercent, tvEquivalentGrade;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        // Views
        tvCourseCode = findViewById(R.id.tvCourseCode);
        tvSubjectName = findViewById(R.id.tvSubjectName);
        tvTeacherName = findViewById(R.id.tvTeacherName);

        tvStudentName = findViewById(R.id.tvStudentName);
        tvStudentId = findViewById(R.id.tvStudentId);

        tvScoreRaw = findViewById(R.id.tvScoreRaw);
        tvScorePercent = findViewById(R.id.tvScorePercent);
        tvEquivalentGrade = findViewById(R.id.tvEquivalentGrade);

        // Get data from Intent
        String courseCode = getIntent().getStringExtra("courseCode");
        String subjectName = getIntent().getStringExtra("subjectName");
        String teacherName = getIntent().getStringExtra("teacherName");
        String studentName = getIntent().getStringExtra("studentName");
        String studentId = getIntent().getStringExtra("studentId");
        int totalScore = getIntent().getIntExtra("totalScore", 0);
        int maxScore = getIntent().getIntExtra("maxScore", 0);

        // Populate views
        tvCourseCode.setText(courseCode);
        tvSubjectName.setText(subjectName);
        tvTeacherName.setText(teacherName);

        tvStudentName.setText(studentName);
        tvStudentId.setText(studentId);

        tvScoreRaw.setText(totalScore + "/" + maxScore);

        double percent = maxScore > 0 ? (totalScore * 100.0) / maxScore : 0;
        tvScorePercent.setText(String.format("%.2f%%", percent));

        String grade = percent >= 75 ? "Passed" : "Failed";
        tvEquivalentGrade.setText(grade);
    }
}
