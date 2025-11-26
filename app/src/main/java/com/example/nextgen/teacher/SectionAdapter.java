package com.example.nextgen.teacher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Base64;
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

import com.example.nextgen.R;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.ViewHolder> {

    private List<StudentModel> studentList;
    private String maxScore; // fallback max score

    public SectionAdapter(List<StudentModel> studentList, String maxScore) {
        this.studentList = studentList;
        this.maxScore = maxScore != null ? maxScore : "100";
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

        // Show submission info with score/maxScore and viewed status
        if (student.getSubmission() != null) {
            SubmissionModel sub = student.getSubmission();
            String status = sub.isResubmitRequested() ? "(Resubmit requested) " : "";

            String actualMaxScore = sub.getMaxScore() != null ? sub.getMaxScore() : maxScore;

            String scoreText = (sub.getScore() != null && !sub.getScore().isEmpty())
                    ? sub.getScore() + "/" + actualMaxScore
                    : "Pending";

            if (sub.isViewed()) {
                status += "[Viewed by Instructor]";
                holder.tvSubmission.setTextColor(Color.GREEN); // make viewed text green
            } else {
                holder.tvSubmission.setTextColor(Color.WHITE); // default color
            }

            holder.tvSubmission.setText(
                    sub.getFileName() != null ? sub.getFileName() + " " + status + " - " + scoreText
                            : "No submission"
            );
        } else {
            holder.tvSubmission.setText("No submission");
            holder.tvSubmission.setTextColor(Color.WHITE);
        }

        // View Work Button
        holder.btnViewWork.setOnClickListener(v -> {
            SubmissionModel submission = student.getSubmission();
            if (submission == null || submission.getFileData() == null) {
                Toast.makeText(v.getContext(), "No submission from this student", Toast.LENGTH_SHORT).show();
                return;
            }

            Context context = v.getContext();
            String actualMaxScore = submission.getMaxScore() != null ? submission.getMaxScore() : maxScore;
            String scoreDisplay = submission.getScore() != null ? submission.getScore() + "/" + actualMaxScore : "Pending";

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(student.getFullName());
            builder.setMessage("File: " + submission.getFileName() + "\nScore: " + scoreDisplay +
                    (submission.isViewed() ? "\n[Viewed by Instructor]" : ""));
            builder.setPositiveButton("View File", (dialog, which) -> openSubmissionFile(context, submission, holder));
            builder.setNegativeButton("Give Score", (dialog, which) -> giveScoreDialog(context, submission, student, holder));
            builder.show();
        });

        // Resubmit Button
        holder.btnResubmit.setOnClickListener(v -> {
            SubmissionModel submission = student.getSubmission();
            if (submission == null) {
                Toast.makeText(v.getContext(), "Student hasn't submitted yet", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(v.getContext())
                    .setTitle("Request Resubmit")
                    .setMessage("Are you sure you want to request a resubmission from " + student.getFullName() + "?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        FirebaseDatabase.getInstance().getReference("Submissions")
                                .child(submission.getSubmissionId())
                                .child("resubmitRequested")
                                .setValue(true);
                        FirebaseDatabase.getInstance().getReference("Submissions")
                                .child(submission.getSubmissionId())
                                .child("fileData").setValue(null);
                        FirebaseDatabase.getInstance().getReference("Submissions")
                                .child(submission.getSubmissionId())
                                .child("fileName").setValue(null);
                        FirebaseDatabase.getInstance().getReference("Submissions")
                                .child(submission.getSubmissionId())
                                .child("score").setValue(null);

                        // Update local object immediately
                        submission.setResubmitRequested(true);
                        submission.setFileData(null);
                        submission.setFileName(null);
                        submission.setScore(null);
                        submission.setViewed(false);
                        notifyItemChanged(holder.getAdapterPosition());

                        Toast.makeText(v.getContext(), "Resubmit requested. Student can now submit again!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
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

    private void openSubmissionFile(Context context, SubmissionModel submission, ViewHolder holder) {
        try {
            byte[] fileBytes = Base64.decode(submission.getFileData(), Base64.DEFAULT);
            File tempFile = new File(context.getCacheDir(), "submission_" + submission.getFileName());
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(fileBytes);
            }

            Uri fileUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    tempFile
            );

            String mimeType = getMimeType(context, fileUri);
            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(fileUri, mimeType);
            openIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(openIntent);

            // Mark as viewed in Firebase & locally
            submission.setViewed(true);
            FirebaseDatabase.getInstance().getReference("Submissions")
                    .child(submission.getSubmissionId())
                    .child("viewed")
                    .setValue(true);

            notifyItemChanged(holder.getAdapterPosition()); // update UI instantly
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to open file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void giveScoreDialog(Context context, SubmissionModel submission, StudentModel student, ViewHolder holder) {
        AlertDialog.Builder scoreDialog = new AlertDialog.Builder(context);
        final EditText input = new EditText(context);
        String actualMaxScore = submission.getMaxScore() != null ? submission.getMaxScore() : maxScore;
        input.setHint("Enter score (max " + actualMaxScore + ")");
        scoreDialog.setView(input);

        scoreDialog.setPositiveButton("Save", (d, w) -> {
            String newScore = input.getText().toString().trim();
            if (newScore.isEmpty()) {
                Toast.makeText(context, "Please enter a score", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                float scoreVal = Float.parseFloat(newScore);
                float maxVal = Float.parseFloat(actualMaxScore);
                if (scoreVal > maxVal) {
                    Toast.makeText(context, "Score cannot exceed max score (" + actualMaxScore + ")", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(context, "Invalid score format", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save score & maxScore to Firebase
            FirebaseDatabase.getInstance().getReference("Submissions")
                    .child(submission.getSubmissionId())
                    .child("score")
                    .setValue(newScore);
            FirebaseDatabase.getInstance().getReference("Submissions")
                    .child(submission.getSubmissionId())
                    .child("maxScore")
                    .setValue(actualMaxScore);
            FirebaseDatabase.getInstance().getReference("Submissions")
                    .child(submission.getSubmissionId())
                    .child("resubmitRequested")
                    .setValue(false);
            FirebaseDatabase.getInstance().getReference("Submissions")
                    .child(submission.getSubmissionId())
                    .child("viewed")
                    .setValue(true);

            // Update local object & UI instantly
            submission.setScore(newScore);
            submission.setMaxScore(actualMaxScore);
            submission.setResubmitRequested(false);
            submission.setViewed(true);
            notifyItemChanged(holder.getAdapterPosition());

            Toast.makeText(context, "Score saved!", Toast.LENGTH_SHORT).show();
        });

        scoreDialog.setNegativeButton("Cancel", null);
        scoreDialog.show();
    }

    private String getMimeType(Context context, Uri uri) {
        String type = context.getContentResolver().getType(uri);
        if (type == null) {
            String path = uri.getPath();
            if (path != null) {
                if (path.endsWith(".pdf")) return "application/pdf";
                if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
                if (path.endsWith(".png")) return "image/png";
                if (path.endsWith(".mp4")) return "video/mp4";
                if (path.endsWith(".doc") || path.endsWith(".docx")) return "application/msword";
            }
        }
        return type != null ? type : "*/*";
    }
}
