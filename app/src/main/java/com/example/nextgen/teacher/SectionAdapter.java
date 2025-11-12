package com.example.nextgen.teacher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import java.util.List;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import android.util.Base64;
import java.io.File;
import java.io.FileOutputStream;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.database.FirebaseDatabase;



public class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.ViewHolder> {

    private List<StudentModel> studentList;

    public SectionAdapter(List<StudentModel> studentList) {
        this.studentList = studentList;
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

        holder.btnViewWork.setOnClickListener(v -> {
            StudentModel currentStudent = studentList.get(holder.getAdapterPosition());

            if (currentStudent.getSubmission() == null) {
                Toast.makeText(v.getContext(), "No submission from this student", Toast.LENGTH_SHORT).show();
                return;
            }

            SubmissionModel submission = currentStudent.getSubmission();
            Context context = v.getContext();

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(currentStudent.getFullName());
            builder.setMessage("File: " + submission.getFileName() + "\nScore: " + submission.getScore());

            builder.setPositiveButton("View File", (dialog, which) -> {
                if (submission == null || submission.getSubmissionId() == null) {
                    Toast.makeText(context, "Submission not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    // Decode file
                    byte[] fileBytes = Base64.decode(submission.getFileData(), Base64.DEFAULT);
                    File tempFile = new File(context.getCacheDir(), "submission_" + submission.getFileName());
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        fos.write(fileBytes);
                        fos.flush();
                    }

                    // File URI
                    Uri fileUri = FileProvider.getUriForFile(
                            context,
                            context.getPackageName() + ".provider",
                            tempFile
                    );

                    // Detect MIME type
                    String mimeType = getMimeType(context, fileUri);
                    Intent openIntent = new Intent(Intent.ACTION_VIEW);
                    openIntent.setDataAndType(fileUri, mimeType);
                    openIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(openIntent);

                    // 🔹 Mark as viewed in Firebase
                    FirebaseDatabase.getInstance().getReference("Submissions")
                            .child(submission.getSubmissionId())
                            .child("viewed")
                            .setValue(true)
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(context, "Marked as viewed", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(context, "Failed to update viewed: " + e.getMessage(), Toast.LENGTH_LONG).show());

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(context, "Failed to open file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });



            builder.setNegativeButton("Give Score", (dialog, which) -> {
                AlertDialog.Builder scoreDialog = new AlertDialog.Builder(context);
                final EditText input = new EditText(context);
                input.setHint("Enter score");
                scoreDialog.setView(input);

                scoreDialog.setPositiveButton("Save", (d, w) -> {
                    String newScore = input.getText().toString().trim();

                    if (newScore.isEmpty()) {
                        Toast.makeText(context, "Please enter a score", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (submission == null) {
                        Toast.makeText(context, "No submission data found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (submission.getSubmissionId() == null || submission.getSubmissionId().isEmpty()) {
                        Toast.makeText(context, "Submission ID missing, cannot save score", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FirebaseDatabase.getInstance().getReference("Submissions")
                            .child(submission.getSubmissionId())
                            .child("score")
                            .setValue(newScore)
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(context, "Score saved!", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(context, "Failed to save score: " + e.getMessage(), Toast.LENGTH_LONG).show());
                });

                scoreDialog.setNegativeButton("Cancel", null);
                scoreDialog.show();
            });


            builder.show();
        });



        // Check submission
        if (student.getSubmission() != null) {
            // Show file name or score
            holder.tvSubmission.setText(student.getSubmission().getFileName() + " (" + student.getSubmission().getScore() + ")");
        } else {
            holder.tvSubmission.setText("No submission");
        }
    }


    @Override
    public int getItemCount() {
        return studentList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStudentName, tvStudentId, tvSubmission;
        Button btnViewWork;


        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvStudentId = itemView.findViewById(R.id.tvStudentId);
            tvSubmission = itemView.findViewById(R.id.tvSubmission); // add this in your item_student_enrolled.xml
            btnViewWork = itemView.findViewById(R.id.btnViewWork);

        }

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
