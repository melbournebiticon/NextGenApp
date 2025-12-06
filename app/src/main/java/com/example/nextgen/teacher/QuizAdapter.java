package com.example.nextgen.teacher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QuizAdapter extends RecyclerView.Adapter<QuizAdapter.QuizViewHolder> {

    public interface OnQuizActionListener {
        void onEdit(Quiz quiz);
        void onDelete(Quiz quiz);
        void onActivate(Quiz quiz, boolean isActive);
        void onViewStudents(Quiz quiz);
        void onGenerateQuestions(Quiz quiz); // ✅ Single Generate Questions callback
    }

    private final Context context;
    private final List<Quiz> quizList;
    private final OnQuizActionListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

    public QuizAdapter(Context context, List<Quiz> quizList, OnQuizActionListener listener) {
        this.context = context;
        this.quizList = quizList != null ? new ArrayList<>(quizList) : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public QuizViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_quiz, parent, false);
        return new QuizViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuizViewHolder holder, int position) {
        Quiz quiz = quizList.get(position);

        holder.tvQuizName.setText(quiz.getQuizName() != null ? quiz.getQuizName() : "No Name");
        holder.tvSubject.setText("Subject: " + (quiz.getSubject() != null ? quiz.getSubject() : "-"));
        holder.tvSection.setText("Section: " + (quiz.getSection() != null ? quiz.getSection() : "-"));
        holder.tvDuration.setText("Duration: " + quiz.getDurationMinutes() + " mins");
        holder.tvSchedule.setText(
                quiz.getScheduledAt() > 0 ? "Scheduled: " + sdf.format(quiz.getScheduledAt()) : "Scheduled: Not Set"
        );

        // Set switch state safely
        holder.switchActive.setOnCheckedChangeListener(null);
        holder.switchActive.setChecked(quiz.isActive());
        holder.switchActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onActivate(quiz, isChecked);
        });

        // Button listeners
        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(quiz);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(quiz);
        });

        holder.btnViewStudents.setOnClickListener(v -> {
            if (listener != null) listener.onViewStudents(quiz);
        });

        holder.btnGenerateQuestions.setOnClickListener(v -> {
            if (listener != null) listener.onGenerateQuestions(quiz); // ✅ Fixed callback
        });
    }

    @Override
    public int getItemCount() {
        return quizList.size();
    }

    // ===== Dynamic List Management =====
    public void updateQuizList(List<Quiz> updatedList) {
        quizList.clear();
        if (updatedList != null) quizList.addAll(updatedList);
        notifyDataSetChanged();
    }

    public void addQuiz(Quiz quiz) {
        if (quiz == null) return;
        quizList.add(quiz);
        notifyItemInserted(quizList.size() - 1);
    }

    public void removeQuiz(Quiz quiz) {
        if (quiz == null) return;
        int index = quizList.indexOf(quiz);
        if (index >= 0) {
            quizList.remove(index);
            notifyItemRemoved(index);
        }
    }

    public void updateQuiz(Quiz updatedQuiz) {
        if (updatedQuiz == null) return;
        for (int i = 0; i < quizList.size(); i++) {
            if (updatedQuiz.getFirebaseKey() != null &&
                    updatedQuiz.getFirebaseKey().equals(quizList.get(i).getFirebaseKey())) {
                quizList.set(i, updatedQuiz);
                notifyItemChanged(i);
                break;
            }
        }
    }

    // ===== ViewHolder =====
    static class QuizViewHolder extends RecyclerView.ViewHolder {
        TextView tvQuizName, tvSubject, tvSection, tvDuration, tvSchedule;
        Button btnEdit, btnDelete, btnViewStudents, btnGenerateQuestions; // ✅ Added button
        Switch switchActive;

        public QuizViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuizName = itemView.findViewById(R.id.tvQuizName);
            tvSubject = itemView.findViewById(R.id.tvSubject);
            tvSection = itemView.findViewById(R.id.tvSection);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvSchedule = itemView.findViewById(R.id.tvSchedule);

            btnEdit = itemView.findViewById(R.id.btnEditQuiz);
            btnDelete = itemView.findViewById(R.id.btnDeleteQuiz);
            btnViewStudents = itemView.findViewById(R.id.btnViewStudentsQuiz);
            btnGenerateQuestions = itemView.findViewById(R.id.btnGenerateQuestions); // ✅ Added
            switchActive = itemView.findViewById(R.id.switchActiveQuiz);
        }
    }
}
