package com.example.nextgen.student;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;

import java.util.List;

public class ExamAdapter extends RecyclerView.Adapter<ExamAdapter.ExamViewHolder> {

    private Context context;
    private List<ExamModel> examList;

    public ExamAdapter(Context context, List<ExamModel> examList) {
        this.context = context;
        this.examList = examList;
    }

    @NonNull
    @Override
    public ExamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_student_exam_list, parent, false);
        return new ExamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExamViewHolder holder, int position) {
        ExamModel exam = examList.get(position);
        String examStatus = exam.getStatus();

        // --- 1. SET TEXT DATA ---
        holder.tvExamTitle.setText(exam.getExamTitle());
        holder.tvCourseDisplay.setText(exam.getCourseDisplay());
        holder.tvTeacherName.setText("Teacher: " + exam.getTeacherName());
        holder.tvSchedule.setText("Schedule: " + exam.getScheduledDateDisplay());
        holder.tvStatus.setText(examStatus);

        // --- 2. SET STATUS COLORS ---
        if (examStatus.contains("TAKEN")) {
            holder.tvStatus.setTextColor(Color.parseColor("#9C27B0")); // Purple: TAKEN
        } else if (exam.isAvailable()) {
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50")); // Green: AVAILABLE NOW
        } else if (examStatus.contains("EXPIRED")) {
            holder.tvStatus.setTextColor(Color.parseColor("#F44336")); // Red: EXPIRED
        } else {
            holder.tvStatus.setTextColor(Color.parseColor("#2196F3")); // Blue: Scheduled
        }

        // --- HANDLE TAKE EXAM BUTTON & PRESENCE ---
        if (!exam.isPresent()) {
            // Student is absent → show message, hide button
            holder.btnTakeExam.setVisibility(View.GONE);
            holder.tvStatus.setText("You are marked ABSENT for this exam");
            holder.tvStatus.setTextColor(Color.parseColor("#F44336")); // Red for absent
            holder.tvStatus.setVisibility(View.VISIBLE);

            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(v ->
                    Toast.makeText(context, "You are marked ABSENT for this exam.", Toast.LENGTH_LONG).show()
            );

        } else if (exam.isAvailable() && !exam.getStatus().contains("TAKEN")) {
            // Student is present and exam available → show Take Exam button
            holder.btnTakeExam.setVisibility(View.VISIBLE);
            holder.btnTakeExam.setText("Take Exam");
            holder.btnTakeExam.setBackgroundColor(Color.parseColor("#4CAF50"));
            holder.btnTakeExam.setOnClickListener(v -> startTakeExamActivity(exam));

            holder.itemView.setClickable(false);
            holder.itemView.setOnClickListener(null);

        } else {
            // Exam already taken or not available
            holder.btnTakeExam.setVisibility(View.GONE);
            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(v ->
                    Toast.makeText(context, "Status: " + exam.getStatus(), Toast.LENGTH_LONG).show()
            );
        }


    }

    // Start TakeExamActivity
    private void startTakeExamActivity(ExamModel exam) {
        android.util.Log.d("ExamAdapter", "Starting exam: " + exam.getExamId());

        // ✅ Get studentId from SessionManager or your student model
        String studentId = com.example.nextgen.SessionManager.getStudentId(context);

        if (studentId == null || studentId.isEmpty()) {
            Toast.makeText(context, "Student ID not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Update ongoing = true in Firebase
        com.google.firebase.database.DatabaseReference ref =
                com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("ExamStudents")
                        .child(exam.getExamId())
                        .child(studentId);

        ref.child("ongoing").setValue(true)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(context, "Exam started! Marked as ongoing.", Toast.LENGTH_SHORT).show();

                    // ✅ Proceed to TakeExamActivity
                    Intent intent = new Intent(context, TakeExamActivity.class);
                    intent.putExtra("examId", exam.getExamId());
                    intent.putExtra("examTitle", exam.getExamTitle());
                    context.startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Failed to update ongoing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }


    @Override
    public int getItemCount() {
        return examList.size();
    }

    public static class ExamViewHolder extends RecyclerView.ViewHolder {
        TextView tvExamTitle, tvCourseDisplay, tvTeacherName, tvSchedule, tvStatus;
        Button btnTakeExam;

        public ExamViewHolder(@NonNull View itemView) {
            super(itemView);
            tvExamTitle = itemView.findViewById(R.id.tvExamTitle);
            tvCourseDisplay = itemView.findViewById(R.id.tvCourseDisplay);
            tvTeacherName = itemView.findViewById(R.id.tvTeacherName);
            tvSchedule = itemView.findViewById(R.id.tvSchedule);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnTakeExam = itemView.findViewById(R.id.btnTakeExam);
        }
    }
}
