package com.example.nextgen.student;

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
import com.example.nextgen.R;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.teacher.Question;

import java.util.ArrayList;
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

        // Use displayNumber for sequential numbering
        holder.tvQuestion.setText(q.getDisplayNumber() + ". " + q.getQuestionText());

        // Hide all first
        holder.radioGroupMCQ.setVisibility(View.GONE);
        holder.radioGroupTF.setVisibility(View.GONE);
        holder.etAnswer.setVisibility(View.GONE);
        holder.spinnerAnswer.setVisibility(View.GONE);

        // Remove previous TextWatcher to prevent recycling issues
        if (holder.textWatcher != null) {
            holder.etAnswer.removeTextChangedListener(holder.textWatcher);
            holder.textWatcher = null;
        }

        switch (q.getQuestionType().toLowerCase()) {

            case "multiple choice":
                holder.radioGroupMCQ.setVisibility(View.VISIBLE);
                holder.rbOptionA.setText("A. " + q.getOptionA());
                holder.rbOptionB.setText("B. " + q.getOptionB());
                holder.rbOptionC.setText("C. " + q.getOptionC());
                holder.rbOptionD.setText("D. " + q.getOptionD());

                holder.radioGroupMCQ.clearCheck();

                if (q.getStudentAnswer() != null) {
                    String ans = q.getStudentAnswer();
                    if (ans.equals(q.getOptionA())) holder.rbOptionA.setChecked(true);
                    else if (ans.equals(q.getOptionB())) holder.rbOptionB.setChecked(true);
                    else if (ans.equals(q.getOptionC())) holder.rbOptionC.setChecked(true);
                    else if (ans.equals(q.getOptionD())) holder.rbOptionD.setChecked(true);
                }

                holder.radioGroupMCQ.setOnCheckedChangeListener((group, checkedId) -> {
                    RadioButton selected = group.findViewById(checkedId);
                    if (selected != null) {
                        String answerText = selected.getText().toString().substring(3).trim();
                        q.setStudentAnswer(answerText);
                    }
                });
                break;

            case "true/false":
                holder.radioGroupTF.setVisibility(View.VISIBLE);
                holder.radioGroupTF.clearCheck();

                if ("True".equalsIgnoreCase(q.getStudentAnswer())) holder.rbTrue.setChecked(true);
                else if ("False".equalsIgnoreCase(q.getStudentAnswer()))
                    holder.rbFalse.setChecked(true);

                holder.radioGroupTF.setOnCheckedChangeListener((group, checkedId) -> {
                    RadioButton selected = group.findViewById(checkedId);
                    if (selected != null) q.setStudentAnswer(selected.getText().toString());
                });
                break;

            default: // Matching Type
                holder.spinnerAnswer.setVisibility(View.VISIBLE);

                // Collect all correct answers for Matching Type questions
                List<String> allAnswers = new ArrayList<>();
                for (Question qItem : questions) {
                    if ("matching type".equalsIgnoreCase(qItem.getQuestionType()) && qItem.getCorrectAnswer() != null) {
                        if (!allAnswers.contains(qItem.getCorrectAnswer())) {
                            allAnswers.add(qItem.getCorrectAnswer());
                        }
                    }
                }

                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(context,
                        android.R.layout.simple_spinner_item, allAnswers);
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                holder.spinnerAnswer.setAdapter(spinnerAdapter);

                // Pre-select student's previous answer if exists
                if (q.getStudentAnswer() != null) {
                    int positionInSpinner = allAnswers.indexOf(q.getStudentAnswer());
                    if (positionInSpinner >= 0)
                        holder.spinnerAnswer.setSelection(positionInSpinner);
                } else {
                    holder.spinnerAnswer.setSelection(0);
                    q.setStudentAnswer(allAnswers.get(0)); // default to first
                }

                holder.spinnerAnswer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        q.setStudentAnswer(allAnswers.get(pos));
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

                break;
        }
    }

    @Override
    public int getItemCount() {
        return questions.size();
    }

    public static class QuestionViewHolder extends RecyclerView.ViewHolder {
        TextView tvQuestion;
        EditText etAnswer;
        Spinner spinnerAnswer;
        TextWatcher textWatcher;

        RadioGroup radioGroupMCQ;
        RadioButton rbOptionA, rbOptionB, rbOptionC, rbOptionD;

        RadioGroup radioGroupTF;
        RadioButton rbTrue, rbFalse;

        public QuestionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuestion = itemView.findViewById(R.id.tvQuestion);
            spinnerAnswer = itemView.findViewById(R.id.spinnerAnswer);

            radioGroupMCQ = itemView.findViewById(R.id.radioGroupMCQ);
            rbOptionA = itemView.findViewById(R.id.rbOptionA);
            rbOptionB = itemView.findViewById(R.id.rbOptionB);
            rbOptionC = itemView.findViewById(R.id.rbOptionC);
            rbOptionD = itemView.findViewById(R.id.rbOptionD);

            radioGroupTF = itemView.findViewById(R.id.radioGroupTF);
            rbTrue = itemView.findViewById(R.id.rbTrue);
            rbFalse = itemView.findViewById(R.id.rbFalse);

            // Clear listeners to prevent recycled view issues
            radioGroupMCQ.setOnCheckedChangeListener(null);
            radioGroupTF.setOnCheckedChangeListener(null);
        }
    }

    public List<Question> getQuestions() {
        return questions;
    }
}