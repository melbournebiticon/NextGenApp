package com.example.nextgen.student;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

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

    private String examId; // string examId from ExamModel
    private String examTitle;
    private DatabaseReference questionsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_exam);

        tvExamTitle = findViewById(R.id.tvExamTitle);
        rvQuestions = findViewById(R.id.rvQuestions);
        rvQuestions.setLayoutManager(new LinearLayoutManager(this));

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
    }

    private void loadQuestions() {
        questionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                questionList.clear();
                for (DataSnapshot questionSnap : snapshot.getChildren()) {
                    Question q = questionSnap.getValue(Question.class);
                    if (q != null) {
                        questionList.add(q);
                    }
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



}
