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

import java.util.List;

public class StudentExamAdapter extends RecyclerView.Adapter<StudentExamAdapter.ViewHolder> {

    private final List<StudentExamStatus> students;
    private final String examId; // store examId

    // ✅ New constructor
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

        // Name and course info
        holder.tvStudentName.setText(student.getFullName());
        holder.tvCourseInfo.setText("Course: "
                + student.getCourse() + " - "
                + student.getSpecialization() + " - "
                + student.getYear() + " - "
                + student.getSection());

        // Answered counter
        holder.tvCounter.setText("Answered: " + student.getQuestionsAnswered());

        // Highlight if ongoing
        if(student.isOngoing()){
            holder.itemView.setBackgroundColor(Color.parseColor("#FFF9C4")); // light yellow
        } else {
            holder.itemView.setBackgroundColor(Color.WHITE);
        }

        // Present switch
        // Present switch
        // Inside onBindViewHolder

        holder.switchPresent.setOnCheckedChangeListener(null);
        holder.switchPresent.setChecked(student.isPresent());
        holder.switchPresent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            student.setPresent(isChecked);

            // --- Add logging here ---
            android.util.Log.d("StudentExamAdapter", "Switch toggled. examId: " + examId
                    + ", studentId: " + student.getStudentId()
                    + ", new present value: " + isChecked);

            if (examId != null && !examId.trim().isEmpty() &&
                    student.getStudentId() != null && !student.getStudentId().trim().isEmpty()) {

                DatabaseReference studentNode = FirebaseDatabase.getInstance()
                        .getReference("ExamStudents")
                        .child(examId.trim())
                        .child(student.getStudentId().trim());

                studentNode.get().addOnSuccessListener(snapshot -> {
                    android.util.Log.d("FirebaseCheck", "Value at path: " + snapshot.getValue());
                });


                // --- Add logging to check Firebase path ---
                android.util.Log.d("StudentExamAdapter", "Firebase path: "
                        + studentNode.toString());

                studentNode.child("present").setValue(isChecked)
                        .addOnCompleteListener(task -> {
                            if(task.isSuccessful()){
                                android.util.Log.d("StudentExamAdapter", "Present updated successfully!");
                            } else {
                                android.util.Log.e("StudentExamAdapter", "Failed to update present", task.getException());
                            }
                        });

                studentNode.child("ongoing").setValue(isChecked); // optional
            } else {
                android.util.Log.e("StudentExamAdapter", "Cannot update Firebase: examId or studentId is null");
            }
        });



        holder.btnReset.setOnClickListener(v -> {
            student.setQuestionsAnswered(0);
            student.setPresent(false);

            // Update UI
            holder.tvCounter.setText("Answered: 0");
            holder.switchPresent.setChecked(false);
            holder.itemView.setBackgroundColor(Color.WHITE);

            if (examId != null && !examId.trim().isEmpty() &&
                    student.getStudentId() != null && !student.getStudentId().trim().isEmpty()) {

                DatabaseReference studentNode = FirebaseDatabase.getInstance()
                        .getReference("ExamStudents")
                        .child(examId.trim())
                        .child(student.getStudentId().trim());

                // Trigger reset for the student app
                studentNode.child("reset").setValue(true);

                // Remove any previous score
                DatabaseReference scoreRef = FirebaseDatabase.getInstance()
                        .getReference("Scores")
                        .child(student.getStudentId())
                        .child(examId.trim());

                scoreRef.removeValue().addOnCompleteListener(task -> {
                    if(task.isSuccessful()){
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
