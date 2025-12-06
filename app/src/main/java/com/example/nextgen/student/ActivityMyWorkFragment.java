package com.example.nextgen.student;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.nextgen.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import android.database.Cursor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ActivityMyWorkFragment extends Fragment {

    private static final int FILE_PICK_REQUEST = 100;
    private Uri selectedFileUri;

    private TextView tvStatus, tvFileName, tvScore, tvViewed, tvPreviewFileName, tvMaxScore;
    private Button btnSelectFile, btnSubmitFile;
    private ImageView imgPreview;
    private VideoView videoPreview;
    private CardView previewContainer;

    private DatabaseReference submissionsRef;
    private String studentId, activityId;
    private String currentSubmissionId;
    private boolean resubmitRequested = false;
    private String maxScore = "0";

    public static ActivityMyWorkFragment newInstance(String activityId, String maxScore) {
        ActivityMyWorkFragment fragment = new ActivityMyWorkFragment();
        Bundle args = new Bundle();
        args.putString("activityId", activityId);
        args.putString("maxScore", maxScore);
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_activity_my_work, container, false);

        btnSelectFile = view.findViewById(R.id.btnSelectFile);
        btnSubmitFile = view.findViewById(R.id.btnSubmitFile);
        tvStatus = view.findViewById(R.id.tvWorkStatus);
        tvFileName = view.findViewById(R.id.tvFileName);
        tvScore = view.findViewById(R.id.tvScore);
        tvViewed = view.findViewById(R.id.tvViewed);
        imgPreview = view.findViewById(R.id.imgPreview);
        previewContainer = view.findViewById(R.id.previewContainer);
        tvPreviewFileName = view.findViewById(R.id.tvPreviewFileName);
        videoPreview = view.findViewById(R.id.videoPreview);
        tvMaxScore = view.findViewById(R.id.tvMaxScore);

        submissionsRef = FirebaseDatabase.getInstance().getReference("Submissions");
        studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (getArguments() != null) {
            activityId = getArguments().getString("activityId");
            maxScore = getArguments().getString("maxScore", "0");
            tvMaxScore.setText("Max Score: " + maxScore);
            checkExistingSubmission();
        }

        btnSelectFile.setOnClickListener(v -> openFilePicker());
        btnSubmitFile.setOnClickListener(v -> {
            if (selectedFileUri != null) {
                uploadFileToRealtime(selectedFileUri);
            } else {
                Toast.makeText(getContext(), "Please select a file first", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    private void checkExistingSubmission() {
        if (activityId == null) return;

        submissionsRef.orderByChild("studentId").equalTo(studentId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean found = false;

                        for (DataSnapshot subSnap : snapshot.getChildren()) {
                            Object subActivityIdObj = subSnap.child("activityId").getValue();
                            String subActivityId = subActivityIdObj != null ? subActivityIdObj.toString() : null;

                            if (activityId.equals(subActivityId)) {
                                currentSubmissionId = subSnap.getKey();
                                resubmitRequested = Boolean.TRUE.equals(subSnap.child("resubmitRequested").getValue(Boolean.class));

                                Object fileNameObj = subSnap.child("fileName").getValue();
                                String fileName = fileNameObj != null ? fileNameObj.toString() : null;

                                // Safe conversion of score
                                Object scoreObj = subSnap.child("score").getValue();
                                String score = "Pending";
                                if (scoreObj != null) {
                                    if (scoreObj instanceof Number) {
                                        score = String.valueOf(((Number) scoreObj).intValue());
                                    } else {
                                        score = scoreObj.toString();
                                    }
                                }

                                boolean viewed = Boolean.TRUE.equals(subSnap.child("viewed").getValue(Boolean.class));

                                updateUIForSubmission(fileName, score, viewed);
                                found = true;
                                break;
                            }
                        }

                        if (!found) resetUIForNoSubmission();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(getContext(), "Failed to check submissions", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateUIForSubmission(String fileName, String score, boolean viewed) {
        tvStatus.setText(resubmitRequested ? "Resubmit requested by instructor:" : "Already submitted:");
        previewContainer.setVisibility(View.VISIBLE);
        tvPreviewFileName.setText(fileName != null ? fileName : "Unknown file");
        tvScore.setText(!"Pending".equalsIgnoreCase(score) ?
                "Score: " + score + "/" + maxScore :
                "Your score will appear here (Max: " + maxScore + ")");
        tvViewed.setText(viewed ? "Viewed by instructor" : "Not yet viewed");
        btnSelectFile.setVisibility(resubmitRequested ? View.VISIBLE : View.GONE);
        btnSubmitFile.setVisibility(resubmitRequested ? View.VISIBLE : View.GONE);
        previewFile(selectedFileUri, fileName);
    }

    private void resetUIForNoSubmission() {
        tvStatus.setText("No submission yet.");
        previewContainer.setVisibility(View.GONE);
        btnSelectFile.setVisibility(View.VISIBLE);
        btnSubmitFile.setVisibility(View.GONE);
        tvViewed.setText("");
        resubmitRequested = false;
        currentSubmissionId = null;
        imgPreview.setVisibility(View.GONE);
        videoPreview.setVisibility(View.GONE);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Select file"), FILE_PICK_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICK_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedFileUri = data.getData();
            String fileName = getFileName(selectedFileUri);
            tvFileName.setText(fileName);
            tvStatus.setText("File selected, ready to upload");
            btnSubmitFile.setVisibility(View.VISIBLE);
            btnSubmitFile.setEnabled(true);

            previewFile(selectedFileUri, fileName);
        }
    }

    private void previewFile(Uri uri, String fileName) {
        if (uri == null) return;

        String extension = "";
        String mimeType = getContext().getContentResolver().getType(uri);
        if (mimeType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        }

        if (extension == null) extension = "";

        imgPreview.setVisibility(View.GONE);
        videoPreview.setVisibility(View.GONE);

        if (extension.equalsIgnoreCase("jpg") || extension.equalsIgnoreCase("jpeg") ||
                extension.equalsIgnoreCase("png") || extension.equalsIgnoreCase("gif")) {
            imgPreview.setVisibility(View.VISIBLE);
            imgPreview.setImageURI(uri);
        } else if (extension.equalsIgnoreCase("mp4") || extension.equalsIgnoreCase("3gp") ||
                extension.equalsIgnoreCase("webm")) {
            videoPreview.setVisibility(View.VISIBLE);
            videoPreview.setVideoURI(uri);
            videoPreview.start();
        }
    }

    private void uploadFileToRealtime(Uri fileUri) {
        Context context = getContext();
        if (context == null) return;

        btnSubmitFile.setEnabled(false);
        btnSelectFile.setEnabled(false);
        tvStatus.setText(resubmitRequested ? "Resubmitting..." : "Uploading file...");

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
            int fileSize = inputStream.available();
            inputStream.close();

            double fileSizeMB = fileSize / (1024.0 * 1024.0);
            if (fileSizeMB > 2.0) {
                Toast.makeText(context, "File too large (" + String.format(Locale.getDefault(), "%.2f", fileSizeMB) + " MB). Max 2 MB.", Toast.LENGTH_LONG).show();
                btnSubmitFile.setEnabled(true);
                btnSelectFile.setEnabled(true);
                tvStatus.setText("File too large");
                return;
            }

            inputStream = context.getContentResolver().openInputStream(fileUri);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();

            String base64File = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
            String fileName = getFileName(fileUri);
            String submittedAt = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault())
                    .format(Calendar.getInstance().getTime());

            Map<String, Object> submissionMap = new HashMap<>();
            submissionMap.put("studentId", studentId);
            submissionMap.put("activityId", activityId);
            submissionMap.put("fileName", fileName);
            submissionMap.put("fileData", base64File);
            submissionMap.put("submittedAt", submittedAt);
            submissionMap.put("score", "Pending");
            submissionMap.put("viewed", false);
            submissionMap.put("resubmitRequested", false);
            submissionMap.put("maxScore", maxScore);

            String submissionId = resubmitRequested && currentSubmissionId != null ? currentSubmissionId : submissionsRef.push().getKey();
            if (submissionId != null) {
                submissionsRef.child(submissionId).setValue(submissionMap)
                        .addOnSuccessListener(aVoid -> {
                            tvStatus.setText(resubmitRequested ? "Resubmitted successfully!" : "Uploaded successfully!");
                            checkExistingSubmission();
                            Toast.makeText(context, resubmitRequested ? "Resubmission successful!" : "Submission successful!", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            tvStatus.setText("Failed to upload file.");
                            btnSubmitFile.setEnabled(true);
                            btnSelectFile.setEnabled(true);
                            Toast.makeText(context, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            }

        } catch (Exception e) {
            Log.e("RealtimeUpload", "Upload failed", e);
            tvStatus.setText("Upload failed: " + e.getMessage());
            btnSubmitFile.setEnabled(true);
            btnSelectFile.setEnabled(true);
            Toast.makeText(context, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        Context context = getContext();
        if ("content".equals(uri.getScheme()) && context != null) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) result = cursor.getString(nameIndex);
                }
            }
        }
        if (result == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                result = cut != -1 ? path.substring(cut + 1) : path;
            } else {
                result = "unknown_file";
            }
        }
        return result;
    }
}
