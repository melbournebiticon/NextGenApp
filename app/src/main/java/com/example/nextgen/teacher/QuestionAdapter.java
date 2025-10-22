package com.example.nextgen.teacher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import com.example.nextgen.R;

public class QuestionAdapter extends RecyclerView.Adapter<QuestionAdapter.QuestionViewHolder> {

    public interface OnQuestionActionListener {
        void onEdit(Question question);
        void onDelete(Question question);
    }

    private final Context context;
    private final List<Question> questionList;
    private final OnQuestionActionListener listener;

    public QuestionAdapter(Context context, List<Question> questionList, OnQuestionActionListener listener) {
        this.context = context;
        this.questionList = questionList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public QuestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_question, parent, false);
        return new QuestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuestionViewHolder holder, int position) {
        Question q = questionList.get(position);

        // Set question text
        holder.tvQuestionText.setText(q.getQuestionText());

        // Build detailed info depending on type
        StringBuilder detail = new StringBuilder("Type: " + q.getQuestionType() + "\n");

        switch (q.getQuestionType()) {
            case "Multiple Choice":
                detail.append("A: ").append(q.getOptionA())
                        .append(" | B: ").append(q.getOptionB())
                        .append(" | C: ").append(q.getOptionC())
                        .append(" | D: ").append(q.getOptionD())
                        .append("\nAnswer: ").append(q.getCorrectAnswer());
                break;
            case "True/False":
                detail.append("Answer: ").append(q.getCorrectAnswer());
                break;
            case "Matching Type":
                detail.append("Answer: ").append(q.getCorrectAnswer());
                break;
            default:
                detail.append("Answer: ").append(q.getCorrectAnswer());
        }

        holder.tvQuestionDetails.setText(detail.toString());

        // Button actions
        holder.btnEdit.setOnClickListener(v -> listener.onEdit(q));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(q));
    }

    @Override
    public int getItemCount() {
        return questionList.size();
    }

    public static class QuestionViewHolder extends RecyclerView.ViewHolder {
        TextView tvQuestionText, tvQuestionDetails;
        Button btnEdit, btnDelete;
        LinearLayout container;

        public QuestionViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.questionContainer);
            tvQuestionText = itemView.findViewById(R.id.tvQuestionText);
            tvQuestionDetails = itemView.findViewById(R.id.tvQuestionType);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
