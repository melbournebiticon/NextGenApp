package com.finale.nextgen.teacher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.ViewHolder> {

    private final List<StudentModel> studentList;
    private final String fallbackMaxScore;
    private final DatabaseReference submissionsRef;

    public SectionAdapter(List<StudentModel> studentList, String maxScore) {
        this.studentList = studentList;
        this.fallbackMaxScore = (maxScore != null && !maxScore.isEmpty()) ? maxScore : "100";
        this.submissionsRef = FirebaseDatabase.getInstance().getReference("Submissions");
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_enrolled, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudentModel student = studentList.get(position);

        holder.tvStudentName.setText(student.getFullName());
        holder.tvStudentId.setText(student.getStudentId());

        if (student.getSubmission() != null) {
            SubmissionModel sub = student.getSubmission();

            if ((sub.getSubmissionId() == null || sub.getSubmissionId().isEmpty()) && sub.getId() != null) {
                sub.setSubmissionId(sub.getId());
            }

            Log.d("DEBUG_SUBMISSION", "Submission ID → " + sub.getSubmissionId());

            String actualMax = (sub.getMaxScore() != null && !sub.getMaxScore().isEmpty())
                    ? sub.getMaxScore()
                    : fallbackMaxScore;

            boolean hasScore = sub.getScore() != null && !sub.getScore().isEmpty();

            String display = (sub.getFileName() != null ? sub.getFileName() : "No file");

            if (sub.isResubmitRequested())
                display += "  [RESUBMIT]";

            if (hasScore)
                display += "  ✅ " + sub.getScore() + "/" + actualMax;
            else
                display += "  ⏳ Pending";

            holder.tvSubmission.setText(display);

            // COLOR INDICATOR
            if (sub.isResubmitRequested())
                holder.tvSubmission.setTextColor(Color.YELLOW);
            else if (hasScore)
                holder.tvSubmission.setTextColor(Color.parseColor("#4CAF50")); // green
            else
                holder.tvSubmission.setTextColor(Color.WHITE);

        } else {
            holder.tvSubmission.setText("No submission");
            holder.tvSubmission.setTextColor(Color.GRAY);
        }

        holder.btnViewWork.setOnClickListener(v -> {
            SubmissionModel submission = student.getSubmission();

            if (submission == null || submission.getFileData() == null) {
                Toast.makeText(v.getContext(), "No file submitted yet", Toast.LENGTH_SHORT).show();
                return;
            }

            showSubmissionDialog(v.getContext(), submission, student, holder);
        });

        holder.btnResubmit.setOnClickListener(
                v -> requestResubmit(v.getContext(), student, holder)
        );
    }

    @Override
    public int getItemCount() {
        return studentList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStudentName, tvStudentId, tvSubmission;
        Button btnViewWork, btnResubmit;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvStudentId = itemView.findViewById(R.id.tvStudentId);
            tvSubmission = itemView.findViewById(R.id.tvSubmission);
            btnViewWork = itemView.findViewById(R.id.btnViewWork);
            btnResubmit = itemView.findViewById(R.id.btnResubmit);
        }
    }

    // ========================
    // ✅ SUBMISSION DIALOG
    // ========================
    private void showSubmissionDialog(Context context,
                                      SubmissionModel submission,
                                      StudentModel student,
                                      ViewHolder holder) {

        String actualMax = (submission.getMaxScore() != null && !submission.getMaxScore().isEmpty())
                ? submission.getMaxScore()
                : fallbackMaxScore;

        String score = (submission.getScore() != null)
                ? submission.getScore() + "/" + actualMax
                : "Pending";

        new AlertDialog.Builder(context)
                .setTitle(student.getFullName())
                .setMessage(
                        "File: " + submission.getFileName() +
                                "\nScore: " + score +
                                (submission.isViewed() ? "\n[Viewed]" : "")
                )
                .setPositiveButton("Open File", (dialog, which) ->
                        openSubmissionFile(context, submission, holder))

                .setNegativeButton("Give Score",
                        (dialog, which) ->
                                giveScoreDialog(context, submission, student, holder))
                .show();
    }

    // ========================
    // ✅ OPEN FILE
    // ========================
    private void openSubmissionFile(Context context,
                                    SubmissionModel submission,
                                    ViewHolder holder) {

        try {
            byte[] fileBytes = Base64.decode(submission.getFileData(), Base64.DEFAULT);
            File tempFile = new File(context.getCacheDir(),
                    "submission_" + submission.getSubmissionId());

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(fileBytes);
            }

            Uri fileUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    tempFile
            );

            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(fileUri, getMimeType(context, fileUri));
            openIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(openIntent);

            submission.setViewed(true);

            if (submission.getSubmissionId() != null) {
                submissionsRef.child(submission.getSubmissionId())
                        .child("viewed")
                        .setValue(true);
            }

            if (holder.getAdapterPosition() != RecyclerView.NO_POSITION)
                notifyItemChanged(holder.getAdapterPosition());

        } catch (Exception e) {
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ========================
    // ✅ SCORE DIALOG (INT STORAGE)
    // ========================
    private void giveScoreDialog(Context context,
                                 SubmissionModel submission,
                                 StudentModel student,
                                 ViewHolder holder) {

        View view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_give_score, null);

        EditText input = view.findViewById(R.id.etScore);

        int max = (submission.getMaxScore() != null && !submission.getMaxScore().isEmpty())
                ? Integer.parseInt(submission.getMaxScore())
                : Integer.parseInt(fallbackMaxScore);

        input.setHint("Max: " + max);

        if (submission.getScore() != null)
            input.setText(submission.getScore());

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Give score to " + student.getFullName())
                .setView(view)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        Button saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

        saveBtn.setOnClickListener(v -> {

            String scoreStr = input.getText().toString().trim();

            if (scoreStr.isEmpty()) {
                Toast.makeText(context, "Enter score", Toast.LENGTH_SHORT).show();
                return;
            }

            String id = submission.getSubmissionId();

            if (id == null || id.isEmpty()) {
                Toast.makeText(context, "Submission ID missing", Toast.LENGTH_SHORT).show();
                return;
            }

            int scoreInt;
            try {
                scoreInt = Integer.parseInt(scoreStr);

                if (scoreInt > max) {
                    Toast.makeText(context,
                            "Max allowed: " + max,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(context, "Invalid number", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save as integers in Firebase
            submissionsRef.child(id).child("score").setValue(scoreInt);
            submissionsRef.child(id).child("maxScore").setValue(max);
            submissionsRef.child(id).child("resubmitRequested").setValue(false);
            submissionsRef.child(id).child("viewed").setValue(true);

            // Update local object
            submission.setScore(String.valueOf(scoreInt));
            submission.setMaxScore(String.valueOf(max));
            submission.setResubmitRequested(false);
            submission.setViewed(true);

            if (holder.getAdapterPosition() != RecyclerView.NO_POSITION)
                notifyItemChanged(holder.getAdapterPosition());

            Toast.makeText(context, "Score saved ✅", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }

    // ========================
    // ✅ REQUEST RESUBMIT
    // ========================
    private void requestResubmit(Context context,
                                 StudentModel student,
                                 ViewHolder holder) {

        SubmissionModel submission = student.getSubmission();

        if (submission == null) {
            Toast.makeText(context,
                    "No submission yet",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(context)
                .setTitle("Request resubmission?")
                .setMessage("Student: " + student.getFullName())
                .setPositiveButton("Yes", (dialog, which) -> {

                    String id = submission.getSubmissionId();

                    if (id == null || id.isEmpty()) {
                        Toast.makeText(context,
                                "Submission ID not found",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    submissionsRef.child(id).child("resubmitRequested").setValue(true);
                    submissionsRef.child(id).child("score").setValue(null);
                    submissionsRef.child(id).child("fileData").setValue(null);
                    submissionsRef.child(id).child("fileName").setValue(null);
                    submissionsRef.child(id).child("viewed").setValue(false);

                    submission.setScore(null);
                    submission.setFileData(null);
                    submission.setFileName(null);
                    submission.setViewed(false);
                    submission.setResubmitRequested(true);

                    if (holder.getAdapterPosition() != RecyclerView.NO_POSITION)
                        notifyItemChanged(holder.getAdapterPosition());

                    Toast.makeText(context,
                            "Resubmit requested ✅",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ========================
    // ✅ MIME TYPE
    // ========================
    private String getMimeType(Context context, Uri uri) {

        String type = context.getContentResolver().getType(uri);

        if (type == null && uri.getPath() != null) {
            String path = uri.getPath();
            if (path.endsWith(".pdf")) return "application/pdf";
            if (path.endsWith(".jpg")) return "image/jpeg";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".doc") || path.endsWith(".docx")) return "application/msword";
        }

        return type != null ? type : "*/*";
    }
}
