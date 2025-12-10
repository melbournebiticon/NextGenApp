package com.finale.nextgen.teacher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;

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
        void onGenerateQuestions(Quiz quiz);
    }

    private final Context context;
    // NOTE: We intentionally keep a reference to the passed list (do NOT copy it) so that
    // external code (ManageQuizActivity) can modify the same list and keep adapter/RecyclerView in sync.
    private final List<Quiz> quizList;
    private final OnQuizActionListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

    public QuizAdapter(Context context, List<Quiz> quizList, OnQuizActionListener listener) {
        this.context = context;
        // Keep the original list reference when provided; fall back to a new list if null.
        this.quizList = quizList != null ? quizList : new ArrayList<>();
        this.listener = listener;

        // Enable stable ids so RecyclerView can track items across updates and animations.
        // We also override getItemId below.
        setHasStableIds(true);
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

        // Avoid triggering listener when setting checked state programmatically
        holder.switchActive.setOnCheckedChangeListener(null);
        holder.switchActive.setChecked(quiz.isActive());
        holder.switchActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onActivate(quiz, isChecked);
        });

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
            if (listener != null) listener.onGenerateQuestions(quiz);
        });
    }

    @Override
    public int getItemCount() {
        return quizList.size();
    }

    /**
     * Provide stable ids derived from firebaseKey so RecyclerView can handle changes safely.
     * Return RecyclerView.NO_ID when no stable id can be produced.
     */
    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= quizList.size()) return RecyclerView.NO_ID;
        Quiz q = quizList.get(position);
        if (q == null) return RecyclerView.NO_ID;
        String key = q.getFirebaseKey();
        if (key == null) return RecyclerView.NO_ID;
        // Use hashCode converted to long. Collisions are unlikely for distinct firebase keys.
        return (long) key.hashCode();
    }

    // ===== Dynamic List Management =====
    // These helpers operate on the adapter's list reference. Ensure they are called on the main thread.

    public void updateQuizList(List<Quiz> updatedList) {
        quizList.clear();
        if (updatedList != null) quizList.addAll(updatedList);
        notifyDataSetChanged();
    }

    /**
     * Insert at top: preferred for optimistic adds so the teacher immediately sees the new quiz.
     */
    public void addQuizAtTop(Quiz quiz) {
        if (quiz == null) return;
        quizList.add(0, quiz);
        notifyItemInserted(0);
    }

    /**
     * Append at end (keeps previous behavior if needed).
     */
    public void addQuiz(Quiz quiz) {
        if (quiz == null) return;
        int pos = quizList.size();
        quizList.add(quiz);
        notifyItemInserted(pos);
    }

    /**
     * Remove by firebaseKey (more robust than object equality).
     */
    public void removeQuizByKey(String firebaseKey) {
        if (firebaseKey == null) return;
        for (int i = 0; i < quizList.size(); i++) {
            Quiz q = quizList.get(i);
            if (q != null && firebaseKey.equals(q.getFirebaseKey())) {
                quizList.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    public void removeQuiz(Quiz quiz) {
        if (quiz == null) return;
        removeQuizByKey(quiz.getFirebaseKey());
    }

    /**
     * Replace existing item matched by firebaseKey.
     */
    public void updateQuiz(Quiz updatedQuiz) {
        if (updatedQuiz == null) return;
        String key = updatedQuiz.getFirebaseKey();
        if (key == null) return;
        for (int i = 0; i < quizList.size(); i++) {
            Quiz q = quizList.get(i);
            if (q != null && key.equals(q.getFirebaseKey())) {
                quizList.set(i, updatedQuiz);
                notifyItemChanged(i);
                return;
            }
        }
    }

    // ===== ViewHolder =====
    static class QuizViewHolder extends RecyclerView.ViewHolder {
        TextView tvQuizName, tvSubject, tvSection, tvDuration, tvSchedule;
        Button btnEdit, btnDelete, btnViewStudents, btnGenerateQuestions;
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
            btnGenerateQuestions = itemView.findViewById(R.id.btnGenerateQuestions);
            switchActive = itemView.findViewById(R.id.switchActiveQuiz);
        }
    }
}