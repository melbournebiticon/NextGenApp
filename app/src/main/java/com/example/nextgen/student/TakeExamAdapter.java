package com.example.nextgen.student;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.teacher.Question;

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

        // 🛑 FIX 1: Gumamit ng pre-computed question number (galing sa TakeExamActivity)
        holder.tvQuestion.setText(q.getDisplayNumber() + ". " + q.getQuestionText());

        // Hide all first
        holder.radioGroupMCQ.setVisibility(View.GONE);
        holder.radioGroupTF.setVisibility(View.GONE);
        holder.etAnswer.setVisibility(View.GONE);

        // 🛑 FIX 2a: Remove any previous TextWatcher to prevent view recycling issues
        if (holder.textWatcher != null) {
            holder.etAnswer.removeTextChangedListener(holder.textWatcher);
            holder.textWatcher = null;
        }

        // MULTIPLE CHOICE
        if (q.getQuestionType().equalsIgnoreCase("Multiple Choice")) {
            holder.radioGroupMCQ.setVisibility(View.VISIBLE);

            holder.rbOptionA.setText("A. " + q.getOptionA());
            holder.rbOptionB.setText("B. " + q.getOptionB());
            holder.rbOptionC.setText("C. " + q.getOptionC());
            holder.rbOptionD.setText("D. " + q.getOptionD());

            holder.radioGroupMCQ.clearCheck();

            // Set student's previous answer
            if (q.getStudentAnswer() != null) {
                String ans = q.getStudentAnswer();
                if (ans.equals(q.getOptionA())) holder.rbOptionA.setChecked(true);
                else if (ans.equals(q.getOptionB())) holder.rbOptionB.setChecked(true);
                else if (ans.equals(q.getOptionC())) holder.rbOptionC.setChecked(true);
                else if (ans.equals(q.getOptionD())) holder.rbOptionD.setChecked(true);
            }

            // Set new listener
            holder.radioGroupMCQ.setOnCheckedChangeListener((group, checkedId) -> {
                RadioButton selected = group.findViewById(checkedId);
                if (selected != null) {
                    // Extract answer text (Option A/B/C/D)
                    String answerText = selected.getText().toString().substring(3).trim();
                    q.setStudentAnswer(answerText);
                }
            });
        }

        // TRUE OR FALSE
        else if (q.getQuestionType().equalsIgnoreCase("True/False")) {
            holder.radioGroupTF.setVisibility(View.VISIBLE);
            holder.radioGroupTF.clearCheck();

            if ("True".equalsIgnoreCase(q.getStudentAnswer())) holder.rbTrue.setChecked(true);
            else if ("False".equalsIgnoreCase(q.getStudentAnswer())) holder.rbFalse.setChecked(true);

            holder.radioGroupTF.setOnCheckedChangeListener((group, checkedId) -> {
                RadioButton selected = group.findViewById(checkedId);
                if (selected != null) q.setStudentAnswer(selected.getText().toString());
            });
        }

        // MATCHING TYPE or IDENTIFICATION
        else {
            holder.etAnswer.setVisibility(View.VISIBLE);
            holder.etAnswer.setHint("Enter your answer");

            // Set student's previous answer
            if (q.getStudentAnswer() != null)
                holder.etAnswer.setText(q.getStudentAnswer());
            else
                holder.etAnswer.setText(""); // Clear the text if no answer yet

            // 🛑 FIX 2b: Create new TextWatcher and attach it
            holder.textWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // Save answer immediately upon change
                    q.setStudentAnswer(s.toString().trim());
                }
                @Override public void afterTextChanged(Editable s) {}
            };

            holder.etAnswer.addTextChangedListener(holder.textWatcher);
        }
    }

    @Override
    public int getItemCount() {
        return questions.size();
    }

    public static class QuestionViewHolder extends RecyclerView.ViewHolder {
        TextView tvQuestion;
        EditText etAnswer;

        // 🛑 FIX 2c: Add TextWatcher variable to ViewHolder for proper cleanup
        TextWatcher textWatcher;

        RadioGroup radioGroupMCQ;
        RadioButton rbOptionA, rbOptionB, rbOptionC, rbOptionD;

        RadioGroup radioGroupTF;
        RadioButton rbTrue, rbFalse;

        public QuestionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuestion = itemView.findViewById(R.id.tvQuestion);
            etAnswer = itemView.findViewById(R.id.etAnswer);

            radioGroupMCQ = itemView.findViewById(R.id.radioGroupMCQ);
            rbOptionA = itemView.findViewById(R.id.rbOptionA);
            rbOptionB = itemView.findViewById(R.id.rbOptionB);
            rbOptionC = itemView.findViewById(R.id.rbOptionC);
            rbOptionD = itemView.findViewById(R.id.rbOptionD);

            radioGroupTF = itemView.findViewById(R.id.radioGroupTF);
            rbTrue = itemView.findViewById(R.id.rbTrue);
            rbFalse = itemView.findViewById(R.id.rbFalse);

            // Critical: I-clear ang listeners kapag ni-recycle ang View
            radioGroupMCQ.setOnCheckedChangeListener(null);
            radioGroupTF.setOnCheckedChangeListener(null);
        }
    }

    public List<Question> getQuestions() {
        return questions;
    }
}