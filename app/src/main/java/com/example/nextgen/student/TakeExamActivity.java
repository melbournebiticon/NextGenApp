package com.example.nextgen.student;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.teacher.Question;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class TakeExamActivity extends AppCompatActivity {

    private TextView tvExamTitle;
    private RecyclerView rvQuestions;
    private TakeExamAdapter questionAdapter;
    private List<Question> questionList = new ArrayList<>();
    private Button btnSubmit;

    private String examId;
    private String examTitle;
    private DatabaseReference questionsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_exam);

        tvExamTitle = findViewById(R.id.tvExamTitle);
        rvQuestions = findViewById(R.id.rvQuestions);
        rvQuestions.setLayoutManager(new LinearLayoutManager(this));
        btnSubmit = findViewById(R.id.btnSubmitExam); // Make sure you have this in your XML

        // Get exam info from intent
        examId = getIntent().getStringExtra("examId");
        examTitle = getIntent().getStringExtra("examTitle");

        Log.d("TakeExam", "ExamID received: " + examId + ", ExamTitle: " + examTitle);

        if (examId == null || examId.isEmpty()) {
            Toast.makeText(this, "Invalid exam ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvExamTitle.setText("Exam: " + examTitle);

        // Reference to questions node
        questionsRef = FirebaseDatabase.getInstance().getReference("Questions").child(examId);

        loadQuestions();

        // Submit button logic
        btnSubmit.setOnClickListener(v -> submitExam());
    }

    private void loadQuestions() {
        questionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                questionList.clear();
                for (DataSnapshot questionSnap : snapshot.getChildren()) {
                    Question q = questionSnap.getValue(Question.class);
                    if (q != null) questionList.add(q);
                }

                if (questionList.isEmpty()) {
                    Toast.makeText(TakeExamActivity.this, "No questions found for this exam", Toast.LENGTH_SHORT).show();
                } else {
                    questionAdapter = new TakeExamAdapter(TakeExamActivity.this, questionList);
                    rvQuestions.setAdapter(questionAdapter);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TakeExamActivity.this, "Error loading questions: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void submitExam() {
        if (questionList.isEmpty()) {
            Toast.makeText(this, "No questions to submit", Toast.LENGTH_SHORT).show();
            return;
        }

        int totalQuestions = questionList.size();
        int correctAnswers = 0;

        for (Question q : questionList) {
            String studentAns = q.getStudentAnswer();
            if (studentAns != null && studentAns.equalsIgnoreCase(q.getCorrectAnswer())) {
                correctAnswers++;
            }
        }

        // Launch ResultActivity
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("courseCode", "CS101"); // fetch actual course code
        intent.putExtra("subjectName", "Introduction to Programming"); // fetch actual
        intent.putExtra("teacherName", "Mr. Smith"); // fetch actual
        intent.putExtra("studentName", "Juan Dela Cruz"); // fetch from profile
        intent.putExtra("studentId", "2025-0001"); // fetch from profile
        intent.putExtra("totalScore", correctAnswers);
        intent.putExtra("maxScore", totalQuestions);
        startActivity(intent);
        finish();
    }

}
