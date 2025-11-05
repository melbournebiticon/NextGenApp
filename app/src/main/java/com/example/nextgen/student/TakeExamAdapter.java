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

import java.util.ArrayList;
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

        // Use displayNumber for sequential numbering
        holder.tvQuestion.setText(q.getDisplayNumber() + ". " + q.getQuestionText());

        // Hide all first
        holder.radioGroupMCQ.setVisibility(View.GONE);
        holder.radioGroupTF.setVisibility(View.GONE);
        holder.etAnswer.setVisibility(View.GONE);

        // Remove previous TextWatcher to prevent recycling issues
        if (holder.textWatcher != null) {
            holder.etAnswer.removeTextChangedListener(holder.textWatcher);
            holder.textWatcher = null;
        }

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
                }

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

            default: // Matching Type or Identification
                holder.etAnswer.setVisibility(View.VISIBLE);
                holder.etAnswer.setHint("Enter your answer");

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
        EditText etAnswer;
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

            // Setting listeners to null in the constructor is good practice to reset them,
            // even though they are set to null/cleared in onBindViewHolder.
            radioGroupMCQ.setOnCheckedChangeListener(null);
            radioGroupTF.setOnCheckedChangeListener(null);
        }
    }

    public List<Question> getQuestions() {
        return questions;
    }
}

