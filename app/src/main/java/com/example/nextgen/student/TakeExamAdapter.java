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

<<<<<<< HEAD
import java.util.Arrays;
=======
import java.util.ArrayList;
>>>>>>> origin/pushnyodito4
import java.util.List;

public class TakeExamAdapter extends RecyclerView.Adapter<TakeExamAdapter.QuestionViewHolder> {

    private Context context;
    private List<Question> questions; // Note: This field is now guaranteed not to be null

    public TakeExamAdapter(Context context, List<Question> questions) {
        this.context = context;
        // FIX: Ensure the list is never null. If the list passed is null, initialize as an empty list.
        this.questions = (questions != null) ? questions : new ArrayList<>();
    }

    @NonNull
    @Override
    public QuestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_student_exam, parent, false);
        return new QuestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuestionViewHolder holder, int position) {
        // Safe check, although the constructor already minimizes the risk
        if (questions.isEmpty()) return;

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

<<<<<<< HEAD
            // Restore previous answer if exists
            if (q.getStudentAnswer() != null) {
                int index = options.indexOf(q.getStudentAnswer());
                if (index >= 0) holder.spinnerAnswer.setSelection(index);
            }

            holder.spinnerAnswer.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                    q.setStudentAnswer(options.get(pos));
=======
        // Remove previous RadioGroup listeners to prevent recycling issues
        holder.radioGroupMCQ.setOnCheckedChangeListener(null);
        holder.radioGroupTF.setOnCheckedChangeListener(null);

        // Reset text/check states
        holder.radioGroupMCQ.clearCheck();
        holder.radioGroupTF.clearCheck();
        holder.etAnswer.setText("");


        switch (q.getQuestionType().toLowerCase()) {

            case "multiple choice":
                holder.radioGroupMCQ.setVisibility(View.VISIBLE);
                holder.rbOptionA.setText("A. " + q.getOptionA());
                holder.rbOptionB.setText("B. " + q.getOptionB());
                holder.rbOptionC.setText("C. " + q.getOptionC());
                holder.rbOptionD.setText("D. " + q.getOptionD());

                // Restore previous answer
                if (q.getStudentAnswer() != null) {
                    String ans = q.getStudentAnswer();
                    // We check the full option text since that's what we saved.
                    if (ans.equals(q.getOptionA())) holder.rbOptionA.setChecked(true);
                    else if (ans.equals(q.getOptionB())) holder.rbOptionB.setChecked(true);
                    else if (ans.equals(q.getOptionC())) holder.rbOptionC.setChecked(true);
                    else if (ans.equals(q.getOptionD())) holder.rbOptionD.setChecked(true);
>>>>>>> origin/pushnyodito4
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });

<<<<<<< HEAD
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
=======
                // Attach new listener
                holder.radioGroupMCQ.setOnCheckedChangeListener((group, checkedId) -> {
                    // Safety check to prevent crashing if the view is recycled during a transition
                    int selectedPosition = holder.getBindingAdapterPosition();
                    if (selectedPosition != RecyclerView.NO_POSITION) {
                        Question currentQuestion = questions.get(selectedPosition);
                        RadioButton selected = group.findViewById(checkedId);
                        if (selected != null) {
                            // Only capture the answer text (excluding "A. ", "B. ", etc.)
                            String answerText = selected.getText().toString().substring(3).trim();
                            currentQuestion.setStudentAnswer(answerText);
                        }
                    }
                });
                break;

            case "true/false":
                holder.radioGroupTF.setVisibility(View.VISIBLE);

                // Restore previous answer
                if ("True".equalsIgnoreCase(q.getStudentAnswer())) holder.rbTrue.setChecked(true);
                else if ("False".equalsIgnoreCase(q.getStudentAnswer())) holder.rbFalse.setChecked(true);

                // Attach new listener
                holder.radioGroupTF.setOnCheckedChangeListener((group, checkedId) -> {
                    int selectedPosition = holder.getBindingAdapterPosition();
                    if (selectedPosition != RecyclerView.NO_POSITION) {
                        Question currentQuestion = questions.get(selectedPosition);
                        RadioButton selected = group.findViewById(checkedId);
                        if (selected != null) currentQuestion.setStudentAnswer(selected.getText().toString());
                    }
                });
                break;
>>>>>>> origin/pushnyodito4

            if (q.getStudentAnswer() != null) {
                holder.etAnswer.setText(q.getStudentAnswer());
            }

<<<<<<< HEAD
            holder.etAnswer.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    q.setStudentAnswer(s.toString().trim());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
=======
                // Restore previous answer
                if (q.getStudentAnswer() != null)
                    holder.etAnswer.setText(q.getStudentAnswer());
                else
                    holder.etAnswer.setText("");

                // Attach new TextWatcher
                holder.textWatcher = new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        // Use getBindingAdapterPosition() to ensure we reference the correct item
                        int currentPosition = holder.getBindingAdapterPosition();
                        if (currentPosition != RecyclerView.NO_POSITION) {
                            questions.get(currentPosition).setStudentAnswer(s.toString().trim());
                        }
                    }
                    @Override public void afterTextChanged(Editable s) {}
                };
                holder.etAnswer.addTextChangedListener(holder.textWatcher);
                break;
>>>>>>> origin/pushnyodito4
        }
    }

    @Override
    public int getItemCount() {
        // FIX: Add Null Check here to prevent the NullPointerException crash.
        // It returns 0 if the list is null, which prevents crashing while the view initializes.
        return (questions != null) ? questions.size() : 0;
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
<<<<<<< HEAD
=======

            radioGroupMCQ = itemView.findViewById(R.id.radioGroupMCQ);
            rbOptionA = itemView.findViewById(R.id.rbOptionA);
            rbOptionB = itemView.findViewById(R.id.rbOptionB);
            rbOptionC = itemView.findViewById(R.id.rbOptionC);
            rbOptionD = itemView.findViewById(R.id.rbOptionD);

            radioGroupTF = itemView.findViewById(R.id.radioGroupTF);
            rbTrue = itemView.findViewById(R.id.rbTrue);
            rbFalse = itemView.findViewById(R.id.rbFalse);

            // Setting listeners to null in the constructor is good practice to reset them,
            // even though they are set to null/cleared in onBindViewHolder.
            radioGroupMCQ.setOnCheckedChangeListener(null);
            radioGroupTF.setOnCheckedChangeListener(null);
>>>>>>> origin/pushnyodito4
        }
    }

    // Optional helper to get all student answers for submission
    public List<Question> getQuestions() {
        return questions;
    }
}

