package com.finale.nextgen.student;

import android.content.Context;
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
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import android.util.Log;

public class TakeQuizAdapter extends RecyclerView.Adapter<TakeQuizAdapter.QuestionViewHolder> {

    private Context context;
    private List<Question> questions;

    private int totalQuestions = 1;
    private List<String> allMatchingAnswers;
    private String buttonText;

    // Listener to pass next/submit actions to the Activity
    public interface OnActionListener {
        void onNextOrSubmit(int position, String actionText);
    }
    private OnActionListener actionListener;

    public void setOnActionListener(OnActionListener listener) {
        this.actionListener = listener;
    }

    public TakeQuizAdapter(Context context, List<Question> questions, List<String> allMatchingAnswers, String buttonText, int totalQuestions) {
        this.context = context;
        this.questions = (questions != null) ? questions : new ArrayList<>();
        this.allMatchingAnswers = (allMatchingAnswers != null) ? allMatchingAnswers : new ArrayList<>();
        this.buttonText = (buttonText != null) ? buttonText : "Next";
        this.totalQuestions = totalQuestions;
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
        int currentQuestion = q.getDisplayNumber();

        // === PROGRESS BAR ===
        holder.tvProgress.setText("Question " + currentQuestion + "/" + totalQuestions);

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

        // Logic for each question type
        switch (q.getQuestionType() != null ? q.getQuestionType().toLowerCase() : "") {
            case "multiple choice":
                holder.radioGroupMCQ.setVisibility(View.VISIBLE);

                holder.rbOptionA.setText(q.getOptionA());
                holder.rbOptionB.setText(q.getOptionB());
                holder.rbOptionC.setText(q.getOptionC());
                holder.rbOptionD.setText(q.getOptionD());

                if (q.getStudentAnswer() != null) {
                    String ans = q.getStudentAnswer();
                    if (ans.equals(q.getOptionA())) holder.rbOptionA.setChecked(true);
                    else if (ans.equals(q.getOptionB())) holder.rbOptionB.setChecked(true);
                    else if (ans.equals(q.getOptionC())) holder.rbOptionC.setChecked(true);
                    else if (ans.equals(q.getOptionD())) holder.rbOptionD.setChecked(true);
                }

                holder.radioGroupMCQ.setOnCheckedChangeListener((group, checkedId) -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        RadioButton selected = group.findViewById(checkedId);
                        if (selected != null) {
                            String answerText = selected.getText().toString();
                            questions.get(pos).setStudentAnswer(answerText);
                        }
                    }
                });
                break;

            case "true/false":
                holder.radioGroupTF.setVisibility(View.VISIBLE);
                holder.rbTrue.setChecked(false);
                holder.rbFalse.setChecked(false);

                if ("True".equalsIgnoreCase(q.getStudentAnswer())) {
                    holder.rbTrue.setChecked(true);
                } else if ("False".equalsIgnoreCase(q.getStudentAnswer())) {
                    holder.rbFalse.setChecked(true);
                }

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

            default:
                holder.spinnerAnswer.setVisibility(View.VISIBLE);

                List<String> matchingOptions = allMatchingAnswers;

                Log.d("TakeQuizAdapter", "Question: " + q.getQuestionText());
                Log.d("TakeQuizAdapter", "Matching options: " + matchingOptions);

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

        holder.btnNextOrSubmit.setOnClickListener(v -> {
            int actualPos = holder.getBindingAdapterPosition();
            if (actualPos != RecyclerView.NO_POSITION && actionListener != null) {
                Question currQ = questions.get(actualPos); // resolved variable conflict
                String answer = currQ != null ? currQ.getStudentAnswer() : null;
                if (answer == null || answer.trim().isEmpty()) {
                    if (context != null) {
                        android.widget.Toast.makeText(context, "You haven't answered this question yet. Please answer before moving on.", android.widget.Toast.LENGTH_SHORT).show();
                    }
                } else {
                    actionListener.onNextOrSubmit(actualPos, buttonText);
                }
            }
        });
    }


    @Override
    public int getItemCount() {
        return (questions != null) ? questions.size() : 0;
    }

    public static class QuestionViewHolder extends RecyclerView.ViewHolder {
        TextView tvProgress;
        TextView tvQuestion;
        EditText etAnswer;
        TextWatcher textWatcher;
        RadioGroup radioGroupMCQ;
        RadioButton rbOptionA, rbOptionB, rbOptionC, rbOptionD;
        RadioGroup radioGroupTF;
        RadioButton rbTrue, rbFalse;
        Spinner spinnerAnswer;
        MaterialButton btnNextOrSubmit;

        public QuestionViewHolder(@NonNull View itemView) {
            super(itemView);

            tvProgress = itemView.findViewById(R.id.tvProgress);
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

            btnNextOrSubmit = itemView.findViewById(R.id.btnNextOrSubmit);
        }
    }

    public List<Question> getQuestions() {
        return questions;
    }
}