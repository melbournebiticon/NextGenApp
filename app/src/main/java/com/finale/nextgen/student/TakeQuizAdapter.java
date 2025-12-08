package com.finale.nextgen.student;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.finale.nextgen.teacher.Question;

import java.util.ArrayList;
import java.util.List;

/**
 * TakeQuizAdapter - same behaviour as exam adapter but kept in a separate class for quizzes.
 *
 * Responsibilities:
 * - Render one question per item (your activity may show one item at a time or many).
 * - Support Multiple Choice, True/False and Matching Type (spinner).
 * - Restore and persist student answers into the Question objects.
 * - Avoid recycling bugs by removing/adding listeners carefully.
 *
 * NOTE: item_student_exam.xml is expected to contain the following ids:
 *   - tvQuestion, etAnswer, spinnerAnswer
 *   - radioGroupMCQ, rbOptionA, rbOptionB, rbOptionC, rbOptionD
 *   - radioGroupTF, rbTrue, rbFalse
 */
public class TakeQuizAdapter extends RecyclerView.Adapter<TakeQuizAdapter.QuestionViewHolder> {

    private static final String TAG = "TakeQuizAdapter";

    private final Context context;
    private final List<Question> questions;
    private final List<String> allMatchingAnswers;

    public TakeQuizAdapter(Context context, List<Question> questions, List<String> allMatchingAnswers) {
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
    public void onBindViewHolder(@NonNull final QuestionViewHolder holder, int position) {
        if (questions.isEmpty()) return;

        final Question q = questions.get(position);
        if (q == null) return;

        holder.tvQuestion.setText((q.getDisplayNumber() > 0 ? q.getDisplayNumber() + ". " : "") + q.getQuestionText());

        // Hide all input views initially
        holder.radioGroupMCQ.setVisibility(View.GONE);
        holder.radioGroupTF.setVisibility(View.GONE);
        holder.etAnswer.setVisibility(View.GONE);
        holder.spinnerAnswer.setVisibility(View.GONE);

        // Clear previous state/listeners
        holder.radioGroupMCQ.setOnCheckedChangeListener(null);
        holder.radioGroupTF.setOnCheckedChangeListener(null);
        holder.spinnerAnswer.setOnItemSelectedListener(null);
        if (holder.textWatcher != null) {
            holder.etAnswer.removeTextChangedListener(holder.textWatcher);
            holder.textWatcher = null;
        }

        // Clear UI selections
        holder.radioGroupMCQ.clearCheck();
        holder.radioGroupTF.clearCheck();
        holder.etAnswer.setText("");

        String type = q.getQuestionType() == null ? "" : q.getQuestionType().trim().toLowerCase();

        switch (type) {
            case "multiple choice":
                holder.radioGroupMCQ.setVisibility(View.VISIBLE);

                holder.rbOptionA.setText(q.getOptionA() != null ? q.getOptionA() : "");
                holder.rbOptionB.setText(q.getOptionB() != null ? q.getOptionB() : "");
                holder.rbOptionC.setText(q.getOptionC() != null ? q.getOptionC() : "");
                holder.rbOptionD.setText(q.getOptionD() != null ? q.getOptionD() : "");

                // Restore previously selected answer (if any)
                if (q.getStudentAnswer() != null) {
                    String ans = q.getStudentAnswer();
                    if (ans.equals(q.getOptionA())) holder.rbOptionA.setChecked(true);
                    else if (ans.equals(q.getOptionB())) holder.rbOptionB.setChecked(true);
                    else if (ans.equals(q.getOptionC())) holder.rbOptionC.setChecked(true);
                    else if (ans.equals(q.getOptionD())) holder.rbOptionD.setChecked(true);
                }

                holder.radioGroupMCQ.setOnCheckedChangeListener((group, checkedId) -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    RadioButton selected = group.findViewById(checkedId);
                    if (selected != null) {
                        String answerText = selected.getText() != null ? selected.getText().toString() : "";
                        questions.get(pos).setStudentAnswer(answerText);
                        Log.d(TAG, "MCQ answer set pos=" + pos + " answer=" + answerText);
                    }
                });
                break;

            case "true/false":
            case "truefalse":
            case "true / false":
                holder.radioGroupTF.setVisibility(View.VISIBLE);

                if ("True".equalsIgnoreCase(q.getStudentAnswer())) holder.rbTrue.setChecked(true);
                else if ("False".equalsIgnoreCase(q.getStudentAnswer())) holder.rbFalse.setChecked(true);

                holder.radioGroupTF.setOnCheckedChangeListener((group, checkedId) -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    RadioButton selected = group.findViewById(checkedId);
                    if (selected != null) {
                        String answerText = selected.getText() != null ? selected.getText().toString() : "";
                        questions.get(pos).setStudentAnswer(answerText);
                        Log.d(TAG, "TF answer set pos=" + pos + " answer=" + answerText);
                    }
                });
                break;

            case "matching type":
            case "matching":
            case "matching-type":
                holder.spinnerAnswer.setVisibility(View.VISIBLE);

                // Ensure options list contains at least one placeholder if empty
                List<String> matchingOptions = new ArrayList<>(allMatchingAnswers);
                if (matchingOptions.isEmpty()) matchingOptions.add("No options available");

                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, matchingOptions);
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                holder.spinnerAnswer.setAdapter(spinnerAdapter);

                // Restore selection safely (set selection before listener)
                if (q.getStudentAnswer() != null) {
                    int sel = matchingOptions.indexOf(q.getStudentAnswer());
                    holder.spinnerAnswer.setSelection(sel >= 0 ? sel : 0, false);
                } else {
                    holder.spinnerAnswer.setSelection(0, false);
                }

                // Now set listener
                holder.spinnerAnswer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        int currentPos = holder.getBindingAdapterPosition();
                        if (currentPos == RecyclerView.NO_POSITION) return;
                        questions.get(currentPos).setStudentAnswer(matchingOptions.get(pos));
                        Log.d(TAG, "Matching answer set pos=" + currentPos + " answer=" + matchingOptions.get(pos));
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                });
                break;

            default:
                // treat as short-text answer
                holder.etAnswer.setVisibility(View.VISIBLE);
                // restore if any
                if (q.getStudentAnswer() != null) holder.etAnswer.setText(q.getStudentAnswer());
                else holder.etAnswer.setText("");

                holder.textWatcher = new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                    @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                    @Override public void afterTextChanged(Editable s) {
                        int pos = holder.getBindingAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) return;
                        questions.get(pos).setStudentAnswer(s != null ? s.toString() : "");
                    }
                };
                holder.etAnswer.addTextChangedListener(holder.textWatcher);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return (questions != null) ? questions.size() : 0;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Question> newQuestions) {
        questions.clear();
        if (newQuestions != null) questions.addAll(newQuestions);
        notifyDataSetChanged();
    }

    // ================= VIEW HOLDER =================
    public static class QuestionViewHolder extends RecyclerView.ViewHolder {
        final TextView tvQuestion;
        final EditText etAnswer;
        TextWatcher textWatcher;
        final Spinner spinnerAnswer;

        final RadioGroup radioGroupMCQ;
        final RadioButton rbOptionA, rbOptionB, rbOptionC, rbOptionD;

        final RadioGroup radioGroupTF;
        final RadioButton rbTrue, rbFalse;

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
}