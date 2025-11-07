package com.example.nextgen.teacher;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Switch;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;

import java.util.HashMap;
import java.util.List;

public class StudentExamAdapter extends RecyclerView.Adapter<StudentExamAdapter.ViewHolder> {

    private final List<StudentExamStatus> students;
    private final String examId;

    public StudentExamAdapter(List<StudentExamStatus> students, String examId) {
        this.students = students;
        this.examId = examId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_view_exam_teacher, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudentExamStatus student = students.get(position);

        // --- BASIC DISPLAY ---
        holder.tvStudentName.setText(student.getFullName());
        holder.tvCourseInfo.setText("Course: "
                + student.getCourse() + " - "
                + student.getSpecialization() + " - "
                + student.getYear() + " - "
                + student.getSection());
        holder.tvCounter.setText("Answered: " + student.getQuestionsAnswered());

        // --- HIGHLIGHT IF ONGOING ---
        holder.itemView.setBackgroundColor(student.isOngoing()
                ? Color.parseColor("#FFF9C4") // yellow highlight
                : Color.WHITE);

        // --- HANDLE SWITCH ---
        holder.switchPresent.setOnCheckedChangeListener(null);
        holder.switchPresent.setChecked(student.isPresent());
        holder.switchPresent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            student.setPresent(isChecked);

            if (examId == null || examId.trim().isEmpty()
                    || student.getStudentId() == null || student.getStudentId().trim().isEmpty()) {
                android.util.Log.e("StudentExamAdapter", "Cannot update Firebase: examId or studentId missing");
                return;
            }

            DatabaseReference studentNode = FirebaseDatabase.getInstance()
                    .getReference("ExamStudents")
                    .child(examId.trim())
                    .child(student.getStudentId().trim());

            HashMap<String, Object> updates = new HashMap<>();
            updates.put("present", isChecked); // only mark present

            studentNode.updateChildren(updates).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    android.util.Log.d("StudentExamAdapter",
                            "Updated present=" + isChecked + " for " + student.getFullName());
                } else {
                    android.util.Log.e("StudentExamAdapter", "Failed to update student status", task.getException());
                }
            });


            // --- Update UI color instantly ---
            holder.itemView.setBackgroundColor(isChecked
                    ? Color.parseColor("#FFF9C4") // yellow if ongoing
                    : Color.WHITE);
        });

        // --- RESET BUTTON ---
        holder.btnReset.setOnClickListener(v -> {
            student.setQuestionsAnswered(0);
            student.setPresent(false);

            holder.tvCounter.setText("Answered: 0");
            holder.switchPresent.setChecked(false);
            holder.itemView.setBackgroundColor(Color.WHITE);

            if (examId != null && !examId.trim().isEmpty()
                    && student.getStudentId() != null && !student.getStudentId().trim().isEmpty()) {

                DatabaseReference studentNode = FirebaseDatabase.getInstance()
                        .getReference("ExamStudents")
                        .child(examId.trim())
                        .child(student.getStudentId().trim());

                HashMap<String, Object> resetUpdates = new HashMap<>();
                resetUpdates.put("reset", true);
                resetUpdates.put("present", false);
                resetUpdates.put("ongoing", false); // ✅ also stop ongoing

                studentNode.updateChildren(resetUpdates);

                // Remove previous score
                DatabaseReference scoreRef = FirebaseDatabase.getInstance()
                        .getReference("Scores")
                        .child(student.getStudentId())
                        .child(examId.trim());

                scoreRef.removeValue().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        android.util.Log.d("StudentExamAdapter", "Score reset successfully!");
                    } else {
                        android.util.Log.e("StudentExamAdapter", "Failed to reset score", task.getException());
                    }
                });
            }
        });
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStudentName, tvCourseInfo, tvCounter;
        Switch switchPresent;
        Button btnReset;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvCourseInfo = itemView.findViewById(R.id.tvCourseInfo);
            tvCounter = itemView.findViewById(R.id.tvCounter);
            switchPresent = itemView.findViewById(R.id.switchPresent);
            btnReset = itemView.findViewById(R.id.btnReset);
        }
    }
}
