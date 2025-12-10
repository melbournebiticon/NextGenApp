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


import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.webkit.MimeTypeMap;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SectionAdapter
 * - Treats literal "Pending" (case-insensitive / containing "pending") as no-score.
 * - Prevents optimistic UI flip by tracking gradingInProgress.
 * - Ensures transaction verifies submission.studentId in DB before writing score.
 * - After transaction re-reads DB canonical values and normalizes score before updating UI.
 * - Viewing the file does not mutate local submission.score; only DB listener will update UI.
 * - Supports requesting resubmit even when the student has not submitted yet by creating a placeholder
 *   submission node (requires adapter to be constructed with activityId).
 */
public class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.ViewHolder> {

    private final List<StudentModel> studentList;
    private final String fallbackMaxScore;
    private final DatabaseReference submissionsRef;
    private static final String TAG = "SectionAdapter";

    // Track submissionIds currently being graded to avoid optimistic UI flips
    private final Set<String> gradingInProgress = Collections.synchronizedSet(new HashSet<>());

    // Activity id to attach created placeholder submissions
    private final String activityId;

    public SectionAdapter(List<StudentModel> studentList, String maxScore, boolean gradingEnabled, String activityId) {
        this.studentList = studentList;
        this.fallbackMaxScore = (maxScore != null && !maxScore.isEmpty()) ? maxScore : "100";
        this.submissionsRef = FirebaseDatabase.getInstance().getReference("Submissions");
        this.activityId = activityId;
    }

    @NonNull
    @Override
    public SectionAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_enrolled, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SectionAdapter.ViewHolder holder, int position) {
        StudentModel student = studentList.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvStudentName.setText(student.getFullName() != null ? student.getFullName() : "Unnamed");
        holder.tvStudentId.setText(student.getStudentId() != null ? student.getStudentId() : "");

        SubmissionModel sub = student.getSubmission();

        if (sub != null) {
            // normalize submissionId
            if ((sub.getSubmissionId() == null || sub.getSubmissionId().isEmpty()) && sub.getId() != null) {
                sub.setSubmissionId(sub.getId());
            }

            // Defensive normalization: treat "Pending" as no-score so UI won't mark graded
            String normalized = normalizeScore(sub.getScore());
            sub.setScore(normalized);

            // file name
            String fileText = sub.getFileName() != null && !sub.getFileName().isEmpty()
                    ? sub.getFileName()
                    : "No file";
            holder.tvSubmission.setText(fileText);

            // choose max: prefer node maxScore, else fallback activity max
            String actualMax = (sub.getMaxScore() != null && !sub.getMaxScore().isEmpty())
                    ? sub.getMaxScore()
                    : fallbackMaxScore;

            boolean hasScore = isScorePresent(sub.getScore());

            // grading-in-progress?
            boolean inProgress = sub.getSubmissionId() != null && gradingInProgress.contains(sub.getSubmissionId());

            // tvScore: show score/max when graded, or "Saving..." when inProgress
            if (hasScore) {
                holder.tvScore.setVisibility(View.VISIBLE);
                holder.tvScore.setText(sub.getScore() + "/" + actualMax);
                holder.tvScore.setTextColor(Color.WHITE);
                holder.tvScore.setBackgroundResource(R.drawable.rounded_card);
            } else if (inProgress) {
                holder.tvScore.setVisibility(View.VISIBLE);
                holder.tvScore.setText("Saving...");
                holder.tvScore.setTextColor(Color.WHITE);
                holder.tvScore.setBackgroundResource(R.drawable.rounded_card);
            } else {
                holder.tvScore.setVisibility(View.GONE);
                holder.tvScore.setText("");
            }

            // color coding for submission text (viewed does not affect grade button)
            if (sub.isResubmitRequested()) {
                holder.tvSubmission.setTextColor(Color.parseColor("#FB8C00"));
            } else if (hasScore) {
                holder.tvSubmission.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                holder.tvSubmission.setTextColor(Color.DKGRAY);
            }

            // ALWAYS show Grade button when a submission exists.
            // Only disable when there's a real score OR when a transactional save is in progress.
            holder.btnGrade.setVisibility(View.VISIBLE);
            if (hasScore) {
                holder.btnGrade.setEnabled(false);
                holder.btnGrade.setText("Graded");
                holder.btnGrade.setAlpha(0.6f);
            } else if (inProgress) {
                holder.btnGrade.setEnabled(false);
                holder.btnGrade.setText("Saving...");
                holder.btnGrade.setAlpha(0.6f);
            } else {
                holder.btnGrade.setEnabled(true);
                holder.btnGrade.setText("Grade");
                holder.btnGrade.setAlpha(1f);
            }

            // ViewWork: always visible (but will show toast if no file)
            holder.btnViewWork.setVisibility(View.VISIBLE);

            // resubmit button visible/enabled
            holder.btnResubmit.setVisibility(View.VISIBLE);
            holder.btnResubmit.setEnabled(true);

        } else {
            // no submission
            holder.tvSubmission.setText("No submission");
            holder.tvSubmission.setTextColor(Color.GRAY);
            holder.tvScore.setVisibility(View.GONE);
            holder.tvScore.setText("");
            holder.btnGrade.setVisibility(View.GONE);
            holder.btnGrade.setEnabled(false);
            holder.btnResubmit.setVisibility(View.VISIBLE); // allow resubmit even when no submission
            holder.btnResubmit.setEnabled(true);
            holder.btnViewWork.setVisibility(View.GONE);
        }

        Log.d(TAG, "onBind pos=" + position +
                " uid=" + (student.getUid() != null ? student.getUid() : "null") +
                " subId=" + (sub != null ? sub.getSubmissionId() : "null") +
                " score=" + (sub != null ? sub.getScore() : "null") +
                " max=" + (sub != null ? sub.getMaxScore() : "null")
        );

        // Click handlers
        holder.btnGrade.setOnClickListener(v -> {
            SubmissionModel submission = student.getSubmission();
            if (submission == null) {
                Toast.makeText(ctx, "No submission to grade", Toast.LENGTH_SHORT).show();
                return;
            }
            showGradeDialogTransactional(ctx, submission, student, holder);
        });

        holder.btnViewWork.setOnClickListener(v -> {
            SubmissionModel submission = student.getSubmission();
            if (submission == null || submission.getFileData() == null) {
                Toast.makeText(ctx, "No file submitted yet", Toast.LENGTH_SHORT).show();
                return;
            }
            openSubmissionFile(ctx, submission, holder);
        });

        holder.btnResubmit.setOnClickListener(v -> requestResubmit(ctx, student, holder));
    }

    @Override
    public int getItemCount() {
        return studentList != null ? studentList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStudentName, tvStudentId, tvSubmission, tvScore;
        Button btnViewWork, btnResubmit, btnGrade;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvStudentId = itemView.findViewById(R.id.tvStudentId);
            tvSubmission = itemView.findViewById(R.id.tvSubmission);
            tvScore = itemView.findViewById(R.id.tvScore);
            btnViewWork = itemView.findViewById(R.id.btnViewWork);
            btnResubmit = itemView.findViewById(R.id.btnResubmit);
            btnGrade = itemView.findViewById(R.id.btnGrade);
        }
    }

    // Open file helper
    private void openSubmissionFile(Context context, SubmissionModel submission, ViewHolder holder) {
        try {
            if (submission == null || submission.getFileData() == null) {
                Toast.makeText(context, "No file data available", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1) Extract base64 payload and mime from possible data URI
            String fileData = submission.getFileData();
            String possibleMime = extractMimeFromDataUri(fileData); // may be null
            byte[] fileBytes = extractBase64Bytes(fileData);
            if (fileBytes == null || fileBytes.length == 0) {
                Toast.makeText(context, "Failed to decode file data", Toast.LENGTH_LONG).show();
                return;
            }

            // 2) Determine extension and mime-type fallback from fileName
            String originalName = submission.getFileName();
            String ext = getExtensionFromName(originalName);
            String mime = possibleMime != null ? possibleMime : guessMimeFromExtension(ext);

            // If mime still null, try to guess via contentResolver (write temp file with guessed extension first)
            if (mime == null) {
                // fallback to binary
                mime = "*/*";
            }

            // 3) Save bytes to cache with safe file name + extension
            String safeBase = "submission_" + (submission.getSubmissionId() != null ? submission.getSubmissionId() : System.currentTimeMillis());
            String safeName = sanitizeFilename(safeBase);
            if (ext != null && !ext.isEmpty()) safeName = safeName + "." + ext;
            File tempFile = new File(context.getCacheDir(), safeName);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(fileBytes);
                fos.flush();
            }

            // 4) Build URI via FileProvider (authority uses ".provider" as in your manifest)
            String authority = context.getPackageName() + ".provider";
            Uri fileUri = FileProvider.getUriForFile(context, authority, tempFile);

            // 5) Create VIEW intent with chooser and grant permissions
            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(fileUri, mime);
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(openIntent, "Open submission");
            // Ensure we don't crash if no app can open it
            try {
                context.startActivity(chooser);
            } catch (ActivityNotFoundException ex) {
                // If there is no app to handle mime type, fallback to generic view
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.setDataAndType(fileUri, "*/*");
                fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    context.startActivity(Intent.createChooser(fallback, "Open submission with"));
                } catch (ActivityNotFoundException ex2) {
                    Toast.makeText(context, "No app available to open this file.", Toast.LENGTH_LONG).show();
                }
            }

            // 6) Mark viewed flag in DB (do not change score here)
            if (submission.getSubmissionId() != null) {
                submissionsRef.child(submission.getSubmissionId()).child("viewed").setValue(true);
            }

        } catch (IOException e) {
            Log.e(TAG, "openSubmissionFile: IO error: " + e.getMessage(), e);
            Toast.makeText(context, "Error opening file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "openSubmissionFile: " + e.getMessage(), e);
            Toast.makeText(context, "Unexpected error opening file", Toast.LENGTH_LONG).show();
        }
    }

    // Helper: extract base64 bytes from data URI or raw base64 string
    private static byte[] extractBase64Bytes(String fileData) {
        if (fileData == null) return null;
        try {
            String b64 = fileData;
            int comma = fileData.indexOf(',');
            if (fileData.startsWith("data:") && comma > 0) {
                b64 = fileData.substring(comma + 1);
            }
            return android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
        } catch (Exception e) {
            Log.w(TAG, "extractBase64Bytes failed: " + e.getMessage());
            try {
                // second attempt: decode raw bytes using default charset (rare)
                return fileData.getBytes(StandardCharsets.ISO_8859_1);
            } catch (Exception ignored) { }
            return null;
        }
    }

    // Helper: if fileData starts with data:<mime>;base64,... returns the mime
    private static String extractMimeFromDataUri(String fileData) {
        if (fileData == null) return null;
        try {
            if (fileData.startsWith("data:")) {
                int semi = fileData.indexOf(';');
                if (semi > 5) {
                    return fileData.substring(5, semi);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // Helper: get extension from filename (without dot), null if none
    private static String getExtensionFromName(String name) {
        if (name == null) return null;
        int idx = name.lastIndexOf('.');
        if (idx >= 0 && idx < name.length() - 1) {
            String ext = name.substring(idx + 1).toLowerCase();
            // sanitize common extensions
            ext = ext.replaceAll("[^a-z0-9]", "");
            return ext;
        }
        return null;
    }

    // Helper: guess mime from extension
    private static String guessMimeFromExtension(String ext) {
        if (ext == null) return null;
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime;
    }

    // Helper: sanitize filename for filesystem (keeps ascii safe chars)
    private static String sanitizeFilename(String name) {
        if (name == null) name = "file";
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.length() > 80) safe = safe.substring(0, 80);
        return safe;
    }

    // Grade dialog with transaction + DB re-read to ensure UI shows DB values
    private void showGradeDialogTransactional(Context context,
                                              SubmissionModel submission,
                                              StudentModel student,
                                              ViewHolder holder) {
        // safety: if already graded, no-op
        if (isScorePresent(submission.getScore())) {
            Toast.makeText(context, "This submission is already graded.", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_give_score, null);
        EditText input = view.findViewById(R.id.etScore);

        int max = (submission.getMaxScore() != null && !submission.getMaxScore().isEmpty())
                ? Integer.parseInt(submission.getMaxScore())
                : Integer.parseInt(fallbackMaxScore);
        input.setHint("Max: " + max);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Give score to " + (student.getFullName() != null ? student.getFullName() : "student"))
                .setView(view)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        Button saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        saveBtn.setOnClickListener(v -> {
            final String scoreStr = input.getText().toString().trim();
            if (scoreStr.isEmpty()) {
                Toast.makeText(context, "Enter score", Toast.LENGTH_SHORT).show();
                return;
            }
            final String id = submission.getSubmissionId();
            if (id == null || id.isEmpty()) {
                Toast.makeText(context, "Submission ID missing", Toast.LENGTH_SHORT).show();
                return;
            }

            final int scoreInt;
            try {
                scoreInt = Integer.parseInt(scoreStr);
                if (scoreInt > max) {
                    Toast.makeText(context, "Max allowed: " + max, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (scoreInt < 0) {
                    Toast.makeText(context, "Score cannot be negative", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(context, "Invalid number", Toast.LENGTH_SHORT).show();
                return;
            }

            // Mark grading in progress to avoid optimistic "Graded" UI
            gradingInProgress.add(id);
            int pos = findStudentIndexBySubmissionId(id);
            if (pos != -1) notifyItemChanged(pos);

            submissionsRef.child(id).runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                    // Guard: ensure DB studentId matches the submission's studentId
                    Object dbStudentIdObj = currentData.child("studentId").getValue();
                    String dbStudentId = dbStudentIdObj != null ? String.valueOf(dbStudentIdObj) : null;
                    if (submission.getStudentId() != null && (dbStudentId == null || !dbStudentId.equals(submission.getStudentId()))) {
                        Log.w(TAG, "Transaction abort: DB studentId mismatch (db=" + dbStudentId + " expected=" + submission.getStudentId() + ")");
                        return Transaction.abort();
                    }

                    Object existingScoreObj = currentData.child("score").getValue();
                    String existingScore = existingScoreObj != null ? String.valueOf(existingScoreObj).trim() : null;
                    // Treat "Pending" in DB as not graded (abort only if existingScore is non-empty and not 'pending')
                    if (existingScore != null && existingScore.length() > 0 && !existingScore.equalsIgnoreCase("pending")) {
                        return Transaction.abort();
                    }

                    currentData.child("score").setValue(String.valueOf(scoreInt));
                    currentData.child("maxScore").setValue(String.valueOf(max));
                    currentData.child("resubmitRequested").setValue(false);
                    currentData.child("viewed").setValue(true);
                    currentData.child("gradedAt").setValue(String.valueOf(System.currentTimeMillis()));
                    try {
                        String graderId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                        if (graderId != null) currentData.child("gradedBy").setValue(graderId);
                    } catch (Exception ignored) {}
                    return Transaction.success(currentData);
                }

                @Override
                public void onComplete(final com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot snapshot) {
                    // Remove in-progress flag
                    gradingInProgress.remove(id);

                    if (committed) {
                        // Re-read DB to get canonical values and update local model/UI
                        submissionsRef.child(id).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snap) {
                                String dbScore = snap.child("score").getValue() != null ? String.valueOf(snap.child("score").getValue()) : null;
                                String dbMax = snap.child("maxScore").getValue() != null ? String.valueOf(snap.child("maxScore").getValue()) : null;
                                String dbFileName = snap.child("fileName").getValue() != null ? String.valueOf(snap.child("fileName").getValue()) : submission.getFileName();
                                String dbFileData = snap.child("fileData").getValue() != null ? String.valueOf(snap.child("fileData").getValue()) : submission.getFileData();
                                boolean dbResubmit = snap.child("resubmitRequested").getValue() != null && !"false".equals(String.valueOf(snap.child("resubmitRequested").getValue()));
                                boolean dbViewed = snap.child("viewed").getValue() != null && !"false".equals(String.valueOf(snap.child("viewed").getValue()));

                                // Normalize DB score (treat "pending" as null)
                                dbScore = normalizeScore(dbScore);

                                submission.setScore(dbScore);
                                submission.setMaxScore(dbMax);
                                submission.setFileName(dbFileName);
                                submission.setFileData(dbFileData);
                                submission.setResubmitRequested(dbResubmit);
                                submission.setViewed(dbViewed);

                                int updatedIndex = -1;
                                for (int i = 0; i < studentList.size(); i++) {
                                    StudentModel s = studentList.get(i);
                                    if (s != null && s.getUid() != null && s.getUid().equals(submission.getStudentId())) {
                                        s.setSubmission(submission);
                                        updatedIndex = i;
                                        break;
                                    }
                                }

                                if (updatedIndex != -1) {
                                    notifyItemChanged(updatedIndex);
                                    Log.d(TAG, "Post-transaction DB read: updated index=" + updatedIndex + " subId=" + submission.getSubmissionId()
                                            + " dbScore=" + dbScore + " dbMax=" + dbMax);
                                } else {
                                    notifyDataSetChanged();
                                    Log.d(TAG, "Post-transaction DB read: fallback notifyDataSetChanged subId=" + submission.getSubmissionId());
                                }

                                Toast.makeText(context, "Score saved ✅", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Log.w(TAG, "Post-transaction read cancelled: " + (error != null ? error.getMessage() : "null"));
                                notifyDataSetChanged();
                                dialog.dismiss();
                            }
                        });
                    } else {
                        if (error != null) Log.w(TAG, "Transaction failed: " + error.getMessage());
                        Toast.makeText(context, "Cannot grade — submission already graded by someone else.", Toast.LENGTH_LONG).show();
                        int idx = findStudentIndexBySubmissionId(id);
                        if (idx != -1) notifyItemChanged(idx);
                        dialog.dismiss();
                    }
                }
            });
        });
    }

    // Helper to find student index by submissionId
    private int findStudentIndexBySubmissionId(String submissionId) {
        if (submissionId == null) return -1;
        for (int i = 0; i < studentList.size(); i++) {
            StudentModel s = studentList.get(i);
            SubmissionModel sub = s.getSubmission();
            if (sub != null) {
                String sid = sub.getSubmissionId() != null ? sub.getSubmissionId() : sub.getId();
                if (submissionId.equals(sid)) return i;
            }
        }
        return -1;
    }

    // Request resubmit
    private void requestResubmit(Context context, StudentModel student, ViewHolder holder) {
        SubmissionModel submission = student.getSubmission();

        new AlertDialog.Builder(context)
                .setTitle("Request resubmission?")
                .setMessage("Student: " + (student.getFullName() != null ? student.getFullName() : "student"))
                .setPositiveButton("Yes", (dialog, which) -> {
                    // If submission exists, update it. If not, create placeholder submission node.
                    if (submission != null && submission.getSubmissionId() != null && !submission.getSubmissionId().isEmpty()) {
                        String id = submission.getSubmissionId();

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("resubmitRequested", true);
                        updates.put("score", null);
                        updates.put("fileData", null);
                        updates.put("fileName", null);
                        updates.put("viewed", false);

                        submissionsRef.child(id).updateChildren(updates)
                                .addOnSuccessListener(aVoid -> {
                                    submission.setScore(null);
                                    submission.setFileData(null);
                                    submission.setFileName(null);
                                    submission.setViewed(false);
                                    submission.setResubmitRequested(true);
                                    if (holder.getAdapterPosition() != RecyclerView.NO_POSITION) notifyItemChanged(holder.getAdapterPosition());
                                    Toast.makeText(context, "Resubmit requested ✅", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> Toast.makeText(context, "Failed to request resubmit: " + e.getMessage(), Toast.LENGTH_LONG).show());

                    } else {
                        // Create placeholder submission node so teacher can request resubmit even if student didn't upload
                        if (activityId == null || activityId.isEmpty()) {
                            Toast.makeText(context, "Cannot request resubmit: activity id missing", Toast.LENGTH_LONG).show();
                            return;
                        }
                        String newId = submissionsRef.push().getKey();
                        if (newId == null) {
                            Toast.makeText(context, "Failed to create resubmit request", Toast.LENGTH_LONG).show();
                            return;
                        }

                        Map<String, Object> map = new HashMap<>();
                        map.put("activityId", activityId);
                        map.put("studentId", student.getUid());
                        map.put("resubmitRequested", true);
                        map.put("score", null);
                        map.put("fileData", null);
                        map.put("fileName", null);
                        map.put("viewed", false);
                        map.put("submittedAt", String.valueOf(System.currentTimeMillis()));

                        submissionsRef.child(newId).updateChildren(map)
                                .addOnSuccessListener(aVoid -> {
                                    // build local SubmissionModel placeholder and attach
                                    SubmissionModel placeholder = new SubmissionModel();
                                    placeholder.setSubmissionId(newId);
                                    placeholder.setActivityId(activityId);
                                    placeholder.setStudentId(student.getUid());
                                    placeholder.setFileName(null);
                                    placeholder.setFileData(null);
                                    placeholder.setScore(null);
                                    placeholder.setMaxScore(fallbackMaxScore);
                                    placeholder.setViewed(false);
                                    placeholder.setResubmitRequested(true);
                                    // attach locally
                                    student.setSubmission(placeholder);
                                    if (holder.getAdapterPosition() != RecyclerView.NO_POSITION) notifyItemChanged(holder.getAdapterPosition());
                                    Toast.makeText(context, "Resubmit requested ✅ (placeholder created)", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> Toast.makeText(context, "Failed to create resubmit request: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Mime type helper
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

    // Helper: treat "Pending" (or values containing "pending") as empty
    private String normalizeScore(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.length() == 0) return null;
        if (s.equalsIgnoreCase("pending") || s.toLowerCase().contains("pending")) return null;
        return s;
    }

    private boolean isScorePresent(String raw) {
        return normalizeScore(raw) != null;
    }
}