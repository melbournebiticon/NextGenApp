package com.example.nextgen.teacher;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;

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

        // --- UI & State ---
        updateButtonUI(holder.btnMarkPresent, student.isPresent());
        holder.itemView.setBackgroundColor(student.isPresent() ? Color.parseColor("#FFF9C4") : Color.WHITE);

        // --- BUTTON TOGGLE ---
        holder.btnMarkPresent.setOnClickListener(v -> {
            boolean newValue = !student.isPresent(); // toggle
            student.setPresent(newValue);
            updateButtonUI(holder.btnMarkPresent, newValue);
            holder.itemView.setBackgroundColor(newValue ? Color.parseColor("#FFF9C4") : Color.WHITE);

            updateFirebase(student, newValue);
        });

        // --- RESET BUTTON ---
        holder.btnReset.setOnClickListener(v -> {
            student.setQuestionsAnswered(0);
            student.setPresent(false);

            holder.tvCounter.setText("Answered: 0");
            updateButtonUI(holder.btnMarkPresent, false);
            holder.itemView.setBackgroundColor(Color.WHITE);

            resetStudentFirebase(student);
        });
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    // ------------------------------------------------------------------------
    // 🔹 Helper Methods
    // ------------------------------------------------------------------------

    private void updateButtonUI(Button btn, boolean present) {
        if (present) {
            btn.setText("Present");
        } else {
            btn.setText("Absent");
        }

        // Optional: re-apply same background (already in XML)
        btn.setBackgroundResource(R.drawable.btn_present_absent);
    }

    private void updateFirebase(StudentExamStatus student, boolean present) {
        if (examId == null || student.getStudentId() == null) return;

        DatabaseReference studentNode = FirebaseDatabase.getInstance()
                .getReference("ExamStudents")
                .child(examId)
                .child(student.getStudentId());

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("present", present);

        studentNode.updateChildren(updates);
    }

    private void resetStudentFirebase(StudentExamStatus student) {
        if (examId == null || student.getStudentId() == null) return;

        DatabaseReference studentNode = FirebaseDatabase.getInstance()
                .getReference("ExamStudents")
                .child(examId)
                .child(student.getStudentId());

        HashMap<String, Object> resetUpdates = new HashMap<>();
        resetUpdates.put("reset", true);
        resetUpdates.put("present", false);
        resetUpdates.put("ongoing", false);

        studentNode.updateChildren(resetUpdates);

        DatabaseReference scoreRef = FirebaseDatabase.getInstance()
                .getReference("Scores")
                .child(student.getStudentId())
                .child(examId);

        scoreRef.removeValue();
    }

    // ------------------------------------------------------------------------
    // 🔹 ViewHolder
    // ------------------------------------------------------------------------
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStudentName, tvCourseInfo, tvCounter;
        Button btnReset, btnMarkPresent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvCourseInfo = itemView.findViewById(R.id.tvCourseInfo);
            tvCounter = itemView.findViewById(R.id.tvCounter);
            btnReset = itemView.findViewById(R.id.btnReset);
            btnMarkPresent = itemView.findViewById(R.id.btnMarkPresent);
        }
    }
}
