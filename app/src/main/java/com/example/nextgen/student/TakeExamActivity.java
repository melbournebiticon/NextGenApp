package com.example.nextgen.student;

import android.os.Bundle;
import android.widget.TextView;
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
import com.example.nextgen.teacher.Question;


import java.util.ArrayList;
import java.util.List;

public class TakeExamActivity extends AppCompatActivity {

    private TextView tvExamTitle;
    private RecyclerView rvQuestions;
    private TakeExamAdapter questionAdapter;
    private List<Question> questionList = new ArrayList<>();

    private int examId;
    private String examTitle;
    private DatabaseReference questionsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_exam);

        tvExamTitle = findViewById(R.id.tvExamTitle);
        rvQuestions = findViewById(R.id.rvQuestions);
        rvQuestions.setLayoutManager(new LinearLayoutManager(this));

        // Get exam data
        examId = getIntent().getIntExtra("examId", -1);
        examTitle = getIntent().getStringExtra("examTitle");

        tvExamTitle.setText("Exam: " + examTitle);

        // Firebase reference
        questionsRef = FirebaseDatabase.getInstance().getReference("questions");

        loadQuestions();
    }

    private void loadQuestions() {
        questionsRef.orderByChild("examId").equalTo(examId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        questionList.clear();
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                Question q = ds.getValue(Question.class);
                                if (q != null) {
                                    questionList.add(q);
                                }
                            }
                            questionAdapter = new TakeExamAdapter(TakeExamActivity.this, questionList);
                            rvQuestions.setAdapter(questionAdapter);
                        } else {
                            Toast.makeText(TakeExamActivity.this, "No questions found for this exam", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(TakeExamActivity.this, "Failed to load questions: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
