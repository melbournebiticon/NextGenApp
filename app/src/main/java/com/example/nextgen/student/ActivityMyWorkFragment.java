package com.example.nextgen.student;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import android.widget.VideoView;import java.io.File;
import java.io.FileOutputStream;





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
    private TextView tvStatus, tvFileName, tvScore, tvViewed;
    private Button btnSelectFile, btnSubmitFile;
    private ImageView imgPreview;

    private DatabaseReference submissionsRef;
    private String studentId, activityId;
    private LinearLayout previewContainer;
    private TextView tvPreviewFileName;

    private VideoView videoPreview;
    private TextView tvFileLink;



    public static ActivityMyWorkFragment newInstance(String activityId, String dueDate) {
        ActivityMyWorkFragment fragment = new ActivityMyWorkFragment();
        Bundle args = new Bundle();
        args.putString("activityId", activityId);
        args.putString("dueDate", dueDate); // 👈 match the key name
        fragment.setArguments(args);
        return fragment;
    }




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
        tvFileLink = view.findViewById(R.id.tvFileLink);




        submissionsRef = FirebaseDatabase.getInstance().getReference("Submissions");
        studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Get activityId from arguments
        if (getArguments() != null) {
            activityId = getArguments().getString("activityId");
            String dueDate = getArguments().getString("dueDate"); // 👈 use dueDate, not deadline

            Log.d("ActivityMyWorkFragment", "📦 Received activityId: " + activityId + ", dueDate: " + dueDate);

            if (dueDate != null && isPastDeadline(dueDate)) {
                tvStatus.setText("Deadline passed. Submission disabled.");
                btnSelectFile.setVisibility(View.GONE);
                btnSubmitFile.setVisibility(View.GONE);
            } else {
                checkExistingSubmission();
            }
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

        submissionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;

                for (DataSnapshot subSnap : snapshot.getChildren()) {
                    String subStudentId = subSnap.child("studentId").getValue(String.class);
                    String subActivityId = subSnap.child("activityId").getValue(String.class);

                    if (studentId.equals(subStudentId) && activityId.equals(subActivityId)) {
                        String fileName = subSnap.child("fileName").getValue(String.class);
                        String score = subSnap.child("score").getValue(String.class);
                        Object viewedObj = subSnap.child("viewed").getValue();
                        boolean viewed = viewedObj instanceof Boolean ? (Boolean) viewedObj :
                                viewedObj instanceof String ? Boolean.parseBoolean((String) viewedObj) : false;
                        String fileData = subSnap.child("fileData").getValue(String.class);

                        // 1️⃣ Already submitted
                        tvStatus.setText("Already submitted:");
                        previewContainer.setVisibility(View.VISIBLE);
                        tvPreviewFileName.setText(fileName != null ? fileName : "Unknown file");

                        // 2️⃣ Score / viewed
                        if (score != null && !score.equals("Pending")) {
                            tvScore.setText("Score: " + score);
                            tvViewed.setText(viewed ? "Viewed by instructor" : "Not yet viewed");
                        } else {
                            tvScore.setText("Your score will appear here");
                            tvViewed.setText(viewed ? "Viewed by instructor" : "Not yet viewed");
                        }

                        // 3️⃣ Handle preview by file type
                        // 3️⃣ Handle preview by file type
                        imgPreview.setVisibility(View.GONE);
                        videoPreview.setVisibility(View.GONE);
                        tvFileLink.setVisibility(View.GONE); // for docs

                        if (fileData != null && fileName != null) {
                            byte[] decoded = Base64.decode(fileData, Base64.DEFAULT);

                            if (fileName.endsWith(".jpg") || fileName.endsWith(".png")) {
                                Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                                imgPreview.setImageBitmap(bitmap);
                                imgPreview.setVisibility(View.VISIBLE);

                            } else if (fileName.endsWith(".mp4")) {
                                try {
                                    File tempVideo = new File(getContext().getCacheDir(), fileName);
                                    try (FileOutputStream fos = new FileOutputStream(tempVideo)) {
                                        fos.write(decoded);
                                    }

                                    Uri uri = androidx.core.content.FileProvider.getUriForFile(
                                            getContext(),
                                            getContext().getPackageName() + ".fileprovider",
                                            tempVideo
                                    );

                                    videoPreview.setVideoURI(uri);
                                    videoPreview.setVisibility(View.VISIBLE);
                                    videoPreview.start();

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            } else if (fileName.endsWith(".pdf") || fileName.endsWith(".docx") ||
                                    fileName.endsWith(".xlsx") || fileName.endsWith(".txt")) {

                                // Save file to cache
                                File tempFile = new File(getContext().getCacheDir(), fileName);
                                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                                    fos.write(decoded);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                // Get secure URI via FileProvider
                                Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                                        getContext(),
                                        getContext().getPackageName() + ".fileprovider",
                                        tempFile
                                );

                                tvFileLink.setText(fileName + " (Tap to open)");
                                tvFileLink.setVisibility(View.VISIBLE);
                                tvFileLink.setTextColor(getResources().getColor(R.color.link_color));
                                tvFileLink.setOnClickListener(v -> {
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    if (fileName.endsWith(".pdf"))
                                        intent.setDataAndType(fileUri, "application/pdf");
                                    else if (fileName.endsWith(".docx"))
                                        intent.setDataAndType(fileUri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                                    else if (fileName.endsWith(".xlsx"))
                                        intent.setDataAndType(fileUri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                                    else
                                        intent.setDataAndType(fileUri, "*/*");

                                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    try {
                                        startActivity(intent);
                                    } catch (Exception e) {
                                        Toast.makeText(getContext(), "No app found to open this file", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }


                        // 4️⃣ Hide buttons after submission
                        btnSelectFile.setVisibility(View.GONE);
                        btnSubmitFile.setVisibility(View.GONE);

                        found = true;
                        break;
                    }
                }

                if (!found) {
                    // No submission yet
                    tvStatus.setText("No submission yet.");
                    previewContainer.setVisibility(View.GONE);
                    imgPreview.setVisibility(View.GONE);
                    videoPreview.setVisibility(View.GONE);
                    tvFileLink.setVisibility(View.GONE);

                    btnSelectFile.setVisibility(View.VISIBLE);
                    btnSelectFile.setEnabled(true);
                    btnSubmitFile.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to check submissions", Toast.LENGTH_SHORT).show();
            }
        });
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
            tvFileName.setText(getFileName(selectedFileUri));
            tvStatus.setText("File selected, ready to upload");
            btnSubmitFile.setVisibility(View.VISIBLE);
            btnSubmitFile.setEnabled(true);
        }
    }

    private void uploadFileToRealtime(Uri fileUri) {
        btnSubmitFile.setEnabled(false);
        btnSelectFile.setEnabled(false);
        tvStatus.setText("Uploading file...");

        try {
            InputStream inputStream = getContext().getContentResolver().openInputStream(fileUri);
            int fileSize = inputStream.available();
            inputStream.close();

            double fileSizeMB = fileSize / (1024.0 * 1024.0);
            if (fileSizeMB > 2.0) {
                tvStatus.setText("File too large (" + String.format(Locale.getDefault(), "%.2f", fileSizeMB) + " MB). Max 2 MB.");
                Toast.makeText(getContext(), "Please choose a smaller file (max 2 MB).", Toast.LENGTH_LONG).show();
                btnSubmitFile.setEnabled(true);
                btnSelectFile.setEnabled(true);
                return;
            }

            inputStream = getContext().getContentResolver().openInputStream(fileUri);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();

            byte[] fileBytes = outputStream.toByteArray();
            String base64File = Base64.encodeToString(fileBytes, Base64.DEFAULT);
            String fileName = getFileName(fileUri);
            String submittedAt = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault())
                    .format(Calendar.getInstance().getTime());

            String submissionId = submissionsRef.push().getKey();
            Map<String, Object> submissionMap = new HashMap<>();
            submissionMap.put("studentId", studentId);
            submissionMap.put("activityId", activityId);
            submissionMap.put("fileName", fileName);
            submissionMap.put("fileData", base64File);
            submissionMap.put("submittedAt", submittedAt);
            submissionMap.put("score", "Pending");
            submissionMap.put("viewed", false);

            if (submissionId != null) {
                submissionsRef.child(submissionId).setValue(submissionMap)
                        .addOnSuccessListener(aVoid -> {
                            if (isAdded() && getContext() != null) {  // ✅ Check fragment is still attached
                                tvStatus.setText("Uploaded successfully!");
                                btnSubmitFile.setEnabled(false);
                                btnSubmitFile.setVisibility(View.GONE);
                                btnSelectFile.setVisibility(View.GONE); // hide select too
                                Toast.makeText(getContext(), "Submission successful!", Toast.LENGTH_SHORT).show();
                                checkExistingSubmission(); // refresh UI
                            }
                        })
                        .addOnFailureListener(e -> {
                            if (isAdded() && getContext() != null) {
                                tvStatus.setText("Failed to upload file.");
                                btnSubmitFile.setEnabled(true);
                                btnSelectFile.setEnabled(true);
                                Toast.makeText(getContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });

            }

        } catch (Exception e) {
            Log.e("RealtimeUpload", "Upload failed", e);
            tvStatus.setText("Upload failed: " + e.getMessage());
            btnSubmitFile.setEnabled(true);
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null)) {
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
    private boolean isPastDeadline(String dueDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date dueDateTime = sdf.parse(dueDate);
            return Calendar.getInstance().getTime().after(dueDateTime);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("DeadlineCheck", "❌ Date parsing error: " + e.getMessage());
            return false;
        }
    }





}
