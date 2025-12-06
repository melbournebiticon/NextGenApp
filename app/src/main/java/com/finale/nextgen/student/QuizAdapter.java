package com.finale.nextgen.student;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.finale.nextgen.R;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuizAdapter extends RecyclerView.Adapter<QuizAdapter.VH> {

    public interface OnQuizClickListener { void onQuizClick(QuizModel quiz); }

    private final List<QuizModel> list;
    private final OnQuizClickListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
    private String highlightQuizId = null;

    public QuizAdapter(@Nullable List<QuizModel> list, OnQuizClickListener listener) {
        this.list = list != null ? list : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_quiz_list, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        if (position < 0 || position >= list.size()) {
            holder.bindEmpty();
            return;
        }

        final QuizModel q = list.get(position);
        if (q == null) {
            holder.bindEmpty();
            return;
        }

        // Displaying the quiz details
        holder.tvTitle.setText(q.getQuizName() != null ? q.getQuizName() : "Quiz");
        holder.tvSubject.setText(q.getCourseName() != null ? q.getCourseName() : "");
        holder.tvTeacher.setText(q.getTeacherName() != null ? q.getTeacherName() : "");
        long sched = q.getScheduledAt() != null ? q.getScheduledAt() : 0L;
        holder.tvSchedule.setText(sched > 0 ? sdf.format(new Date(sched)) : "No schedule");

        // Check if the student is present
        boolean isPresent = q.getPresent();  // Assuming `getPresent()` is a method that returns whether the student is present

        // Disable interaction if the student is absent
        if (isPresent) {
            holder.itemView.setEnabled(true);  // Enable click if present
            holder.itemView.setAlpha(1f); // Full opacity if present
        } else {
            holder.itemView.setEnabled(false); // Disable click if absent
            holder.itemView.setAlpha(0.5f); // Lower opacity if absent
        }

        // Highlighting logic (if a quiz is to be highlighted)
        if (highlightQuizId != null && highlightQuizId.equals(q.getQuizId())) {
            holder.itemView.setBackgroundColor(Color.parseColor("#FFF9C4")); // Light highlight
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null && isPresent) {
                listener.onQuizClick(q);  // Only allow interaction if the student is present
            }
        });
    }

    @Override public int getItemCount() { return list.size(); }

    public void updateData(@NonNull List<QuizModel> newList) {
        list.clear();
        list.addAll(newList);
        notifyDataSetChanged();
    }

    public void setHighlightQuizId(@Nullable String quizId) {
        this.highlightQuizId = quizId;
        notifyDataSetChanged();
    }

    public int getPositionForQuizId(@NonNull String quizId) {
        if (quizId == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            QuizModel e = list.get(i);
            if (e != null && quizId.equals(e.getQuizId())) return i;
        }
        return -1;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSubject, tvTeacher, tvSchedule;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvQuizTitle);
            tvSubject = itemView.findViewById(R.id.tvQuizSubject);
            tvTeacher = itemView.findViewById(R.id.tvQuizTeacher);
            tvSchedule = itemView.findViewById(R.id.tvQuizSchedule);
        }
        void bindEmpty() {
            tvTitle.setText("");
            tvSubject.setText("");
            tvTeacher.setText("");
            tvSchedule.setText("");
            itemView.setOnClickListener(null);
            itemView.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}
