package com.example.nextgen.student;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.example.nextgen.teacher.Question;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;

import java.util.Arrays;
import java.util.List;

public class TakeExamAdapter extends RecyclerView.Adapter<TakeExamAdapter.QuestionViewHolder> {

    private Context context;
    private List<Question> questions;

    public TakeExamAdapter(Context context, List<Question> questions) {
        this.context = context;
        this.questions = questions;
    }

    @NonNull
    @Override
    public QuestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_student_exam, parent, false);
        return new QuestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuestionViewHolder holder, int position) {
        Question q = questions.get(position);
        holder.tvQuestion.setText((position + 1) + ". " + q.getQuestionText());

        // Show appropriate input based on type
        holder.spinnerAnswer.setVisibility(View.GONE);
        holder.etAnswer.setVisibility(View.GONE);

        if (q.getQuestionType().equals("Multiple Choice")) {
            holder.spinnerAnswer.setVisibility(View.VISIBLE);
            List<String> options = Arrays.asList(q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD());
            holder.spinnerAnswer.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, options));
        } else if (q.getQuestionType().equals("True/False")) {
            holder.spinnerAnswer.setVisibility(View.VISIBLE);
            holder.spinnerAnswer.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, Arrays.asList("True", "False")));
        } else { // Matching
            holder.etAnswer.setVisibility(View.VISIBLE);
            holder.etAnswer.setHint("Enter your answer");
        }
    }

    @Override
    public int getItemCount() {
        return questions.size();
    }

    public static class QuestionViewHolder extends RecyclerView.ViewHolder {

        TextView tvQuestion;
        Spinner spinnerAnswer;
        EditText etAnswer;

        public QuestionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuestion = itemView.findViewById(R.id.tvQuestion);
            spinnerAnswer = itemView.findViewById(R.id.spinnerAnswer);
            etAnswer = itemView.findViewById(R.id.etAnswer);
        }
    }
}

