package com.finale.nextgen.student;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.finale.nextgen.teacher.Question;

import java.util.ArrayList;
import java.util.List;
import android.util.Log;

public class TakeExamAdapter extends RecyclerView.Adapter<TakeExamAdapter.QuestionViewHolder> {

    private Context context;
    private List<Question> questions;
    private List<String> allMatchingAnswers;

    public TakeExamAdapter(Context context, List<Question> questions, List<String> allMatchingAnswers) {
        this.context = context;
        this.questions = (questions != null) ? questions : new ArrayList<>();
        this.allMatchingAnswers = (allMatchingAnswers != null) ? allMatchingAnswers : new ArrayList<>();
    }

    @NonNull
    @Override
    public QuestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_student_exam, parent, false);
        return new QuestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuestionViewHolder holder, int position) {
        if (questions.isEmpty()) return;

        Question q = questions.get(position);

        holder.tvQuestion.setText(q.getDisplayNumber() + ". " + q.getQuestionText());

        // Hide all views first
        holder.radioGroupMCQ.setVisibility(View.GONE);
        holder.radioGroupTF.setVisibility(View.GONE);
        holder.etAnswer.setVisibility(View.GONE);
        holder.spinnerAnswer.setVisibility(View.GONE);

        // Clear previous state
        holder.radioGroupMCQ.clearCheck();
        holder.radioGroupTF.clearCheck();
        holder.etAnswer.setText("");

        if (holder.textWatcher != null) {
            holder.etAnswer.removeTextChangedListener(holder.textWatcher);
            holder.textWatcher = null;
        }

        holder.radioGroupMCQ.setOnCheckedChangeListener(null);
        holder.radioGroupTF.setOnCheckedChangeListener(null);
        holder.spinnerAnswer.setOnItemSelectedListener(null);

        switch (q.getQuestionType().toLowerCase()) {

            // -----------------------------------------------------------
            // MULTIPLE CHOICE
            // -----------------------------------------------------------
            case "multiple choice":
                holder.radioGroupMCQ.setVisibility(View.VISIBLE);

                holder.rbOptionA.setText(q.getOptionA());
                holder.rbOptionB.setText(q.getOptionB());
                holder.rbOptionC.setText(q.getOptionC());
                holder.rbOptionD.setText(q.getOptionD());

                // Restore answer
                if (q.getStudentAnswer() != null) {
                    String ans = q.getStudentAnswer();
                    if (ans.equals(q.getOptionA())) holder.rbOptionA.setChecked(true);
                    else if (ans.equals(q.getOptionB())) holder.rbOptionB.setChecked(true);
                    else if (ans.equals(q.getOptionC())) holder.rbOptionC.setChecked(true);
                    else if (ans.equals(q.getOptionD())) holder.rbOptionD.setChecked(true);
                }

                // NEW — FIXED — no substring
                holder.radioGroupMCQ.setOnCheckedChangeListener((group, checkedId) -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        RadioButton selected = group.findViewById(checkedId);
                        if (selected != null) {
                            String answerText = selected.getText().toString(); // full text
                            questions.get(pos).setStudentAnswer(answerText);
                        }
                    }
                });
                break;

            // -----------------------------------------------------------
            // TRUE OR FALSE
            // -----------------------------------------------------------
            case "true/false":
                holder.radioGroupTF.setVisibility(View.VISIBLE);

                if ("True".equalsIgnoreCase(q.getStudentAnswer())) holder.rbTrue.setChecked(true);
                else if ("False".equalsIgnoreCase(q.getStudentAnswer())) holder.rbFalse.setChecked(true);

                holder.radioGroupTF.setOnCheckedChangeListener((group, checkedId) -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        RadioButton selected = group.findViewById(checkedId);
                        if (selected != null) {
                            questions.get(pos).setStudentAnswer(selected.getText().toString());
                        }
                    }
                });
                break;

            // -----------------------------------------------------------
            // MATCHING TYPE
            // -----------------------------------------------------------
            default:
                holder.spinnerAnswer.setVisibility(View.VISIBLE);

                List<String> matchingOptions = allMatchingAnswers;

                Log.d("TakeExamAdapter", "Question: " + q.getQuestionText());
                Log.d("TakeExamAdapter", "Matching options: " + matchingOptions);

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_spinner_item,
                        matchingOptions
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                holder.spinnerAnswer.setAdapter(adapter);

                // Restore previous answer
                if (q.getStudentAnswer() != null) {
                    int index = matchingOptions.indexOf(q.getStudentAnswer());
                    if (index >= 0) holder.spinnerAnswer.setSelection(index);
                }

                holder.spinnerAnswer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        int currentPos = holder.getBindingAdapterPosition();
                        if (currentPos != RecyclerView.NO_POSITION) {
                            questions.get(currentPos).setStudentAnswer(matchingOptions.get(pos));
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
                break;
        }
    }

    @Override
    public int getItemCount() {
        return (questions != null) ? questions.size() : 0;
    }

    public static class QuestionViewHolder extends RecyclerView.ViewHolder {
        TextView tvQuestion;
        EditText etAnswer;
        TextWatcher textWatcher;
        RadioGroup radioGroupMCQ;
        RadioButton rbOptionA, rbOptionB, rbOptionC, rbOptionD;
        RadioGroup radioGroupTF;
        RadioButton rbTrue, rbFalse;
        Spinner spinnerAnswer;

        public QuestionViewHolder(@NonNull View itemView) {
            super(itemView);

            tvQuestion = itemView.findViewById(R.id.tvQuestion);
            etAnswer = itemView.findViewById(R.id.etAnswer);
            spinnerAnswer = itemView.findViewById(R.id.spinnerAnswer);

            radioGroupMCQ = itemView.findViewById(R.id.radioGroupMCQ);
            rbOptionA = itemView.findViewById(R.id.rbOptionA);
            rbOptionB = itemView.findViewById(R.id.rbOptionB);
            rbOptionC = itemView.findViewById(R.id.rbOptionC);
            rbOptionD = itemView.findViewById(R.id.rbOptionD);

            radioGroupTF = itemView.findViewById(R.id.radioGroupTF);
            rbTrue = itemView.findViewById(R.id.rbTrue);
            rbFalse = itemView.findViewById(R.id.rbFalse);
        }
    }

    public List<Question> getQuestions() {
        return questions;
    }
}
