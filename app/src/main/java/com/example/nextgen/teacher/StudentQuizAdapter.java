package com.example.nextgen.teacher;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class StudentQuizAdapter extends RecyclerView.Adapter<StudentQuizAdapter.VH> {

    public int getPositionForStudentId(String firstId) {
        return 0;
    }

    public void setHighlightStudentId(String firstId) {
    }

    public interface ActionListener {
        void onTogglePresent(String studentId, boolean newValue);
        void onToggleOngoing(String studentId, boolean newValue);
        void onTogglePresentClicked(String studentId, boolean newStatus);
        void onResetStudentQuiz(String studentId);
    }

    private final List<StudentQuizStatus> items = new ArrayList<>();
    private final String quizId;
    private final ActionListener actionListener;

    public StudentQuizAdapter(List<StudentQuizStatus> list, String quizId, ActionListener actionListener) {
        if (list != null) items.addAll(list);
        this.quizId = quizId;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_quiz_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        StudentQuizStatus s = items.get(position);

        // Student name
        holder.tvName.setText(s.getFullName() != null ? s.getFullName() : "");

        // Student ID or meta summary in secondary field
        holder.tvMeta.setText(s.getStudentId() != null ? s.getStudentId() : "");

        // Build full "Course - Specialization - Year - Section" display (skip empty parts)
        StringBuilder meta = new StringBuilder();
        if (s.getCourse() != null && !s.getCourse().trim().isEmpty()) {
            meta.append(s.getCourse().trim());
        }
        if (s.getSpecialization() != null && !s.getSpecialization().trim().isEmpty()) {
            if (meta.length() > 0) meta.append(" - ");
            meta.append(s.getSpecialization().trim());
        }
        if (s.getYear() != null && !s.getYear().trim().isEmpty()) {
            if (meta.length() > 0) meta.append(" - ");
            meta.append(s.getYear().trim());
        }
        if (s.getSection() != null && !s.getSection().trim().isEmpty()) {
            if (meta.length() > 0) meta.append(" - ");
            meta.append(s.getSection().trim());
        }
        String metaText = meta.length() > 0 ? meta.toString() : (s.getStudentId() != null ? s.getStudentId() : "");
        holder.tvCourseInfo.setText(metaText);

        // Answered counter
        holder.tvAnswered.setText("Answered: " + s.getQuestionsAnswered());

        // Present/Absent button appearance
        holder.updatePresentButton(s.isPresent());

        // Present button click (optimistic update + callback)
        holder.btnMarkPresent.setOnClickListener(v -> {
            boolean newStatus = !s.isPresent();
            // optimistic update
            s.setPresent(newStatus);
            holder.updatePresentButton(newStatus);
            if (actionListener != null) actionListener.onTogglePresentClicked(s.getStudentId(), newStatus);
        });

        // Reset button
        holder.btnReset.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onResetStudentQuiz(s.getStudentId());
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void replaceData(List<StudentQuizStatus> newList) {
        items.clear();
        if (newList != null) items.addAll(newList);
        notifyDataSetChanged();
    }

    // ViewHolder
    public static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvMeta, tvAnswered, tvCourseInfo;
        MaterialButton btnMarkPresent;
        MaterialButton btnReset;

        public VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvStudentName);
            tvMeta = itemView.findViewById(R.id.tvStudentMeta);
            tvAnswered = itemView.findViewById(R.id.tvQuestionsAnswered);
            tvCourseInfo = itemView.findViewById(R.id.tvCourseInfo);
            btnMarkPresent = itemView.findViewById(R.id.btnMarkPresent);
            btnReset = itemView.findViewById(R.id.btnResetStudent);
        }

        public void updatePresentButton(boolean isPresent) {
            if (isPresent) {
                btnMarkPresent.setText("Present");
                btnMarkPresent.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981"))); // green
                btnMarkPresent.setIconTint(ColorStateList.valueOf(Color.WHITE));
                btnMarkPresent.setTextColor(Color.WHITE);
            } else {
                btnMarkPresent.setText("Absent");
                btnMarkPresent.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444"))); // red
                btnMarkPresent.setIconTint(ColorStateList.valueOf(Color.WHITE));
                btnMarkPresent.setTextColor(Color.WHITE);
            }
        }
    }
}