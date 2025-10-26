package com.example.nextgen.student;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.teacher.Question;

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

        // Hide both inputs initially
        holder.spinnerAnswer.setVisibility(View.GONE);
        holder.etAnswer.setVisibility(View.GONE);

        if (q.getQuestionType().equals("Multiple Choice")) {
            holder.spinnerAnswer.setVisibility(View.VISIBLE);
            List<String> options = Arrays.asList(q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, options);
            holder.spinnerAnswer.setAdapter(adapter);

            // Restore previous answer if exists
            if (q.getStudentAnswer() != null) {
                int index = options.indexOf(q.getStudentAnswer());
                if (index >= 0) holder.spinnerAnswer.setSelection(index);
            }

            holder.spinnerAnswer.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                    q.setStudentAnswer(options.get(pos));
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });

        } else if (q.getQuestionType().equals("True/False")) {
            holder.spinnerAnswer.setVisibility(View.VISIBLE);
            List<String> tfOptions = Arrays.asList("True", "False");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, tfOptions);
            holder.spinnerAnswer.setAdapter(adapter);

            if (q.getStudentAnswer() != null) {
                int index = tfOptions.indexOf(q.getStudentAnswer());
                if (index >= 0) holder.spinnerAnswer.setSelection(index);
            }

            holder.spinnerAnswer.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                    q.setStudentAnswer(tfOptions.get(pos));
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });

        } else { // Matching Type
            holder.etAnswer.setVisibility(View.VISIBLE);
            holder.etAnswer.setHint("Enter your answer");

            if (q.getStudentAnswer() != null) {
                holder.etAnswer.setText(q.getStudentAnswer());
            }

            holder.etAnswer.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    q.setStudentAnswer(s.toString().trim());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
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

    // Optional helper to get all student answers for submission
    public List<Question> getQuestions() {
        return questions;
    }
}
