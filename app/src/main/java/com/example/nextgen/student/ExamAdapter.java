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
import com.google.android.material.chip.Chip;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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

        // --- 2. SET STATUS CHIP ---
        // --- STATUS / BUTTON VISIBILITY (with local pending handling) ---
        boolean isPendingLocally = false;
        String statusLower = (examStatus != null) ? examStatus.toLowerCase() : "";

// detect "pending" in status (your dashboard sets "TAKEN (Pending sync)")
        if (statusLower.contains("pending")) isPendingLocally = true;

        if (!exam.isPresent()) {
            holder.chipStatus.setText("ABSENT");
            holder.chipStatus.setChipBackgroundColorResource(R.color.error);
            holder.chipStatus.setTextColor(Color.WHITE);

            holder.btnTakeExam.setVisibility(View.GONE);
            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(v ->
                    Toast.makeText(context, "You are marked ABSENT for this exam.", Toast.LENGTH_LONG).show()
            );

        } else if (isPendingLocally) {
            // Locally-submitted but waiting for sync
            holder.chipStatus.setText("SUBMITTED (Pending)");
            holder.chipStatus.setChipBackgroundColorResource(R.color.md_theme_primary);
            holder.chipStatus.setTextColor(Color.WHITE);

            holder.btnTakeExam.setVisibility(View.GONE);
            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(v ->
                    Toast.makeText(context, "You have already submitted this exam (pending sync).", Toast.LENGTH_LONG).show()
            );

        } else if (exam.isAvailable() && !statusLower.contains("taken")) {
            holder.chipStatus.setText("AVAILABLE");
            holder.chipStatus.setChipBackgroundColorResource(R.color.green);
            holder.chipStatus.setTextColor(Color.WHITE);

            holder.btnTakeExam.setVisibility(View.VISIBLE);
            holder.btnTakeExam.setText("Take Exam");
            holder.btnTakeExam.setBackgroundColor(Color.parseColor("#4CAF50"));
            holder.btnTakeExam.setOnClickListener(v -> startTakeExamActivity(exam));

            holder.itemView.setClickable(false);
            holder.itemView.setOnClickListener(null);

        } else if (statusLower.contains("taken")) {
            holder.chipStatus.setText("TAKEN");
            holder.chipStatus.setChipBackgroundColorResource(R.color.md_theme_primary);
            holder.chipStatus.setTextColor(Color.WHITE);

            holder.btnTakeExam.setVisibility(View.GONE);
            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(v ->
                    Toast.makeText(context, "Status: TAKEN", Toast.LENGTH_LONG).show()
            );

        } else if (statusLower.contains("expired")) {
            holder.chipStatus.setText("EXPIRED");
            holder.chipStatus.setChipBackgroundColorResource(R.color.error);
            holder.chipStatus.setTextColor(Color.WHITE);

            holder.btnTakeExam.setVisibility(View.GONE);
            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(v ->
                    Toast.makeText(context, "Status: EXPIRED", Toast.LENGTH_LONG).show()
            );

        } else {
            holder.chipStatus.setText("SCHEDULED");
            holder.chipStatus.setChipBackgroundColorResource(R.color.blue_500);
            holder.chipStatus.setTextColor(Color.WHITE);

            holder.btnTakeExam.setVisibility(View.GONE);
            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(v ->
                    Toast.makeText(context, "Status: SCHEDULED", Toast.LENGTH_LONG).show()
            );
        }
    }

    // Start TakeExamActivity
    private void startTakeExamActivity(ExamModel exam) {
        android.util.Log.d("ExamAdapter", "Attempting to start exam: " + exam.getExamId());

        String studentId = com.example.nextgen.SessionManager.getStudentId(context);
        if (studentId == null || studentId.isEmpty()) {
            Toast.makeText(context, "Student ID not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check local Room for a pending submission for this exam+student
        new Thread(() -> {
            com.example.nextgen.offline.AppDatabase db = com.example.nextgen.offline.AppDatabase.getInstance(context);
            com.example.nextgen.offline.PendingSubmission pending = null;
            try {
                pending = db.pendingSubmissionDao().findPendingByExamAndStudent(exam.getExamId(), studentId);
            } catch (Exception e) {
                android.util.Log.e("ExamAdapter", "Room check failed: " + e.getMessage());
            }

            if (pending != null) {
                // Found local pending submission -> block retake
                ((android.app.Activity) context).runOnUiThread(() ->
                        Toast.makeText(context, "You already submitted this exam (pending upload).", Toast.LENGTH_LONG).show()
                );
                return;
            }

            // No local pending -> proceed to mark ongoing (if online) and launch exam
            ((android.app.Activity) context).runOnUiThread(() -> {
                if (isNetworkAvailable(context)) {
                    DatabaseReference ref = FirebaseDatabase.getInstance()
                            .getReference("ExamStudents")
                            .child(exam.getExamId())
                            .child(studentId);

                    ref.child("ongoing").setValue(true)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(context, "Exam started! Marked as ongoing.", Toast.LENGTH_SHORT).show();
                                launchExamIntent(exam);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(context, "Failed to update ongoing, starting offline: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                launchExamIntent(exam);
                            });
                } else {
                    // Offline: directly start the exam activity (offline mode)
                    launchExamIntent(exam);
                }
            });
        }).start();
    }

    // New helper for launching:
    private void launchExamIntent(ExamModel exam) {
        Intent intent = new Intent(context, TakeExamActivity.class);
        intent.putExtra("examId", exam.getExamId());
        intent.putExtra("examTitle", exam.getExamTitle());
        context.startActivity(intent);
    }

    // Copy/paste this utility as well (top of class):
    private boolean isNetworkAvailable(Context context) {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    @Override
    public int getItemCount() {
        return examList.size();
    }

    public static class ExamViewHolder extends RecyclerView.ViewHolder {
        TextView tvExamTitle, tvCourseDisplay, tvTeacherName, tvSchedule;
        Chip chipStatus;
        Button btnTakeExam;

        public ExamViewHolder(@NonNull View itemView) {
            super(itemView);
            tvExamTitle = itemView.findViewById(R.id.tvExamTitle);
            tvCourseDisplay = itemView.findViewById(R.id.tvCourseDisplay);
            tvTeacherName = itemView.findViewById(R.id.tvTeacherName);
            tvSchedule = itemView.findViewById(R.id.tvSchedule);
            chipStatus = itemView.findViewById(R.id.chipStatus); // <- use Chip
            btnTakeExam = itemView.findViewById(R.id.btnTakeExam);
        }
    }
}
