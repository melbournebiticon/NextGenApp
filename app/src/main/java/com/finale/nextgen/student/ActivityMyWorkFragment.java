package com.finale.nextgen.student;

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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.finale.nextgen.R;
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

    private static final String TAG = "ActivityMyWorkFragment";
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

    // New fields for deadline handling
    private long deadlineMillis = -1;
    private boolean pastDeadline = false;

    // Broadcast action used to notify other activities/fragments when a submission is made/updated
    public static final String ACTION_SUBMISSION_UPDATED = "com.finale.nextgen.SUBMISSION_UPDATED";
    public static final String EXTRA_ACTIVITY_ID = "activityId";

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

        // find views on the inflated view
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

        // debug logs to help identify missing IDs in layout
        if (btnSelectFile == null) Log.e(TAG, "btnSelectFile is null — check fragment_activity_my_work.xml");
        if (btnSubmitFile == null) Log.e(TAG, "btnSubmitFile is null");
        if (tvStatus == null) Log.e(TAG, "tvWorkStatus is null");
        if (tvFileName == null) Log.e(TAG, "tvFileName is null");
        if (tvScore == null) Log.e(TAG, "tvScore is null");
        if (tvViewed == null) Log.e(TAG, "tvViewed is null");
        if (imgPreview == null) Log.e(TAG, "imgPreview is null");
        if (previewContainer == null) Log.e(TAG, "previewContainer is null");
        if (tvPreviewFileName == null) Log.e(TAG, "tvPreviewFileName is null");
        if (videoPreview == null) Log.e(TAG, "videoPreview is null");
        if (tvMaxScore == null) Log.e(TAG, "tvMaxScore is null");

        submissionsRef = FirebaseDatabase.getInstance().getReference("Submissions");

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            studentId = null;
            Log.w(TAG, "FirebaseAuth current user is null");
        }

        if (getArguments() != null) {
            activityId = getArguments().getString("activityId");
            maxScore = getArguments().getString("maxScore", "0");
            if (tvMaxScore != null) {
                tvMaxScore.setText("Max Score: " + maxScore);
            } else {
                Log.w(TAG, "tvMaxScore is null, cannot set Max Score text");
            }
            // fetch activity deadline first, then check submissions (deadline check affects ability to submit)
            fetchActivityDeadline();
        } else {
            // no arguments: still try to check existing submission if studentId available
            checkExistingSubmission();
        }

        if (btnSelectFile != null) {
            btnSelectFile.setOnClickListener(v -> {
                // Prevent picking a file if the deadline passed and the student hasn't submitted and no resubmit requested
                if (pastDeadline && currentSubmissionId == null && !resubmitRequested) {
                    Toast.makeText(getContext(), "Deadline has passed — submissions are closed.", Toast.LENGTH_SHORT).show();
                    return;
                }
                openFilePicker();
            });
        }

        if (btnSubmitFile != null) {
            btnSubmitFile.setOnClickListener(v -> {
                // Double-check before uploading
                if (pastDeadline && currentSubmissionId == null && !resubmitRequested) {
                    Toast.makeText(getContext(), "Cannot submit — deadline has passed.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (selectedFileUri != null) {
                    uploadFileToRealtime(selectedFileUri);
                } else {
                    Toast.makeText(getContext(), "Please select a file first", Toast.LENGTH_SHORT).show();
                }
            });
        }

        return view;
    }

    /**
     * Load deadline info from Firebase. Accepted fields:
     * - Activities/{activityId}/deadlineMillis  (long, millis since epoch)
     * - Activities/{activityId}/deadline        (string or long)
     * - Activities/{activityId}/dueDate         (string or long)
     *
     * Supports date format "yyyy-MM-dd HH:mm" (e.g. "2025-12-09 23:55") as shown in your screenshot.
     */
    private void fetchActivityDeadline() {
        if (activityId == null) {
            // still check submissions (no deadline available)
            checkExistingSubmission();
            return;
        }

        DatabaseReference activitiesRef = FirebaseDatabase.getInstance().getReference("Activities").child(activityId);
        activitiesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean foundDeadline = false;
                try {
                    Object dlMillisObj = snapshot.child("deadlineMillis").getValue();
                    if (dlMillisObj != null) {
                        if (dlMillisObj instanceof Number) {
                            deadlineMillis = ((Number) dlMillisObj).longValue();
                            foundDeadline = true;
                        } else {
                            String s = dlMillisObj.toString();
                            try {
                                deadlineMillis = Long.parseLong(s);
                                foundDeadline = true;
                            } catch (NumberFormatException ignored) { }
                        }
                    }

                    if (!foundDeadline) {
                        Object dlObj = snapshot.child("deadline").getValue();
                        if (dlObj != null) {
                            String dlStr = dlObj.toString();
                            // try parse as epoch millis first
                            try {
                                deadlineMillis = Long.parseLong(dlStr);
                                foundDeadline = true;
                            } catch (NumberFormatException ignored) {
                                // try common date formats
                                String[] patterns = new String[] {
                                        "yyyy-MM-dd HH:mm:ss",
                                        "yyyy-MM-dd HH:mm",           // supports "2025-12-09 23:55"
                                        "yyyy-MM-dd'T'HH:mm:ss'Z'",
                                        "yyyy-MM-dd hh:mm a",
                                        "yyyy-MM-dd"
                                };
                                for (String p : patterns) {
                                    try {
                                        SimpleDateFormat sdf = new SimpleDateFormat(p, Locale.getDefault());
                                        sdf.setLenient(false);
                                        deadlineMillis = sdf.parse(dlStr).getTime();
                                        foundDeadline = true;
                                        break;
                                    } catch (Exception ex) { }
                                }
                            }
                        }
                    }

                    // Also check dueDate field if still not found (your screenshot shows dueDate)
                    if (!foundDeadline) {
                        Object dueObj = snapshot.child("dueDate").getValue();
                        if (dueObj != null) {
                            String dueStr = dueObj.toString();
                            try {
                                deadlineMillis = Long.parseLong(dueStr);
                                foundDeadline = true;
                            } catch (NumberFormatException ignored) {
                                String[] patterns = new String[] {
                                        "yyyy-MM-dd HH:mm:ss",
                                        "yyyy-MM-dd HH:mm",
                                        "yyyy-MM-dd'T'HH:mm:ss'Z'",
                                        "yyyy-MM-dd hh:mm a",
                                        "yyyy-MM-dd"
                                };
                                for (String p : patterns) {
                                    try {
                                        SimpleDateFormat sdf = new SimpleDateFormat(p, Locale.getDefault());
                                        sdf.setLenient(false);
                                        deadlineMillis = sdf.parse(dueStr).getTime();
                                        foundDeadline = true;
                                        break;
                                    } catch (Exception ex) { }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse deadline", e);
                }

                if (foundDeadline && deadlineMillis > 0) {
                    pastDeadline = System.currentTimeMillis() > deadlineMillis;
                } else {
                    // if no deadline configured or parse failed, allow submissions
                    pastDeadline = false;
                }

                checkExistingSubmission();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                pastDeadline = false;
                Log.w(TAG, "fetchActivityDeadline cancelled: " + error.getMessage());
                checkExistingSubmission();
            }
        });
    }

    private void checkExistingSubmission() {
        if (activityId == null || studentId == null) {
            // cannot check without activityId or studentId
            // Set UI conservatively: hide submit if missing activityId
            if (tvStatus != null) tvStatus.setText("No activity selected.");
            if (btnSelectFile != null) btnSelectFile.setVisibility(View.GONE);
            if (btnSubmitFile != null) btnSubmitFile.setVisibility(View.GONE);
            return;
        }

        submissionsRef.orderByChild("studentId").equalTo(studentId)
                .addListenerForSingleValueEvent(new ValueEventListener() { // single read is enough here
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

                        if (!found) {
                            // no existing submission found
                            resetUIForNoSubmission();

                            // If deadline passed and no submission found, disable submit controls
                            if (pastDeadline) {
                                if (btnSelectFile != null) btnSelectFile.setVisibility(View.GONE);
                                if (btnSubmitFile != null) btnSubmitFile.setVisibility(View.GONE);
                                if (tvStatus != null) tvStatus.setText("Deadline passed — submissions are closed.");
                                Toast.makeText(getContext(), "Deadline has passed. You cannot submit this activity.", Toast.LENGTH_LONG).show();
                            } else {
                                // allow selecting file
                                if (btnSelectFile != null) btnSelectFile.setVisibility(View.VISIBLE);
                                if (btnSubmitFile != null) btnSubmitFile.setVisibility(View.GONE);
                                if (tvStatus != null) tvStatus.setText("No submission yet.");
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.w(TAG, "checkExistingSubmission cancelled: " + error.getMessage());
                        Toast.makeText(getContext(), "Failed to check submissions", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateUIForSubmission(String fileName, String score, boolean viewed) {
        if (tvStatus != null) tvStatus.setText(resubmitRequested ? "Resubmit requested by instructor:" : "Already submitted:");
        if (previewContainer != null) previewContainer.setVisibility(View.VISIBLE);
        if (tvPreviewFileName != null) tvPreviewFileName.setText(fileName != null ? fileName : "Unknown file");
        if (tvScore != null) tvScore.setText(!"Pending".equalsIgnoreCase(score) ?
                "Score: " + score + "/" + maxScore :
                "Your score will appear here (Max: " + maxScore + ")");
        if (tvViewed != null) tvViewed.setText(viewed ? "Viewed by instructor" : "Not yet viewed");

        // If instructor requested resubmit, allow resubmission even if deadline passed
        if (btnSelectFile != null) btnSelectFile.setVisibility(resubmitRequested ? View.VISIBLE : View.GONE);
        if (btnSubmitFile != null) btnSubmitFile.setVisibility(resubmitRequested ? View.VISIBLE : View.GONE);

        // We can only preview local selectedFileUri. If there is no local uri, don't attempt to preview remote base64.
        previewFile(selectedFileUri, fileName);
    }

    private void resetUIForNoSubmission() {
        if (tvStatus != null) tvStatus.setText("No submission yet.");
        if (previewContainer != null) previewContainer.setVisibility(View.GONE);
        if (btnSelectFile != null) btnSelectFile.setVisibility(View.VISIBLE);
        if (btnSubmitFile != null) btnSubmitFile.setVisibility(View.GONE);
        if (tvViewed != null) tvViewed.setText("");
        resubmitRequested = false;
        currentSubmissionId = null;
        if (imgPreview != null) imgPreview.setVisibility(View.GONE);
        if (videoPreview != null) videoPreview.setVisibility(View.GONE);
    }

    private void openFilePicker() {
        Context ctx = getContext();
        if (ctx == null || getActivity() == null) {
            Log.w(TAG, "Context or Activity is null, cannot open file picker");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Select file"), FILE_PICK_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICK_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            // Prevent accepting a selected file if deadline passed and no prior submission and not resubmitting
            if (pastDeadline && currentSubmissionId == null && !resubmitRequested) {
                Toast.makeText(getContext(), "Deadline has passed — cannot select files for submission.", Toast.LENGTH_SHORT).show();
                return;
            }

            selectedFileUri = data.getData();
            String fileName = getFileName(selectedFileUri);
            if (tvFileName != null) tvFileName.setText(fileName);
            if (tvStatus != null) tvStatus.setText("File selected, ready to upload");
            if (btnSubmitFile != null) {
                btnSubmitFile.setVisibility(View.VISIBLE);
                btnSubmitFile.setEnabled(true);
            }

            previewFile(selectedFileUri, fileName);
        }
    }

    private void previewFile(Uri uri, String fileName) {
        if (uri == null) return;
        Context ctx = getContext();
        if (ctx == null) return;

        String extension = "";
        String mimeType = ctx.getContentResolver().getType(uri);
        if (mimeType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        }

        if (extension == null) extension = "";

        if (imgPreview != null) imgPreview.setVisibility(View.GONE);
        if (videoPreview != null) videoPreview.setVisibility(View.GONE);

        try {
            if ((extension.equalsIgnoreCase("jpg") || extension.equalsIgnoreCase("jpeg") ||
                    extension.equalsIgnoreCase("png") || extension.equalsIgnoreCase("gif")) && imgPreview != null) {
                imgPreview.setVisibility(View.VISIBLE);
                imgPreview.setImageURI(uri);
            } else if ((extension.equalsIgnoreCase("mp4") || extension.equalsIgnoreCase("3gp") ||
                    extension.equalsIgnoreCase("webm")) && videoPreview != null) {
                videoPreview.setVisibility(View.VISIBLE);
                videoPreview.setVideoURI(uri);
                videoPreview.start();
            }
        } catch (Exception e) {
            Log.w(TAG, "previewFile failed: " + e.getMessage());
        }
    }

    private void uploadFileToRealtime(Uri fileUri) {
        Context context = getContext();
        if (context == null) return;

        // Final guard: do not upload if deadline passed and student had no prior submission and not resubmitting
        if (pastDeadline && currentSubmissionId == null && !resubmitRequested) {
            Toast.makeText(context, "Cannot upload — deadline has passed.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (btnSubmitFile != null) btnSubmitFile.setEnabled(false);
        if (btnSelectFile != null) btnSelectFile.setEnabled(false);
        if (tvStatus != null) tvStatus.setText(resubmitRequested ? "Resubmitting..." : "Uploading file...");

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
            int fileSize = inputStream.available();
            inputStream.close();

            double fileSizeMB = fileSize / (1024.0 * 1024.0);
            if (fileSizeMB > 2.0) {
                Toast.makeText(context, "File too large (" + String.format(Locale.getDefault(), "%.2f", fileSizeMB) + " MB). Max 2 MB.", Toast.LENGTH_LONG).show();
                if (btnSubmitFile != null) btnSubmitFile.setEnabled(true);
                if (btnSelectFile != null) btnSelectFile.setEnabled(true);
                if (tvStatus != null) tvStatus.setText("File too large");
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
            String submittedAt = String.valueOf(System.currentTimeMillis()); // store epoch millis for easier sorting

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

            // If resubmitting reuse currentSubmissionId, otherwise push a new submission ID
            String submissionId = resubmitRequested && currentSubmissionId != null ? currentSubmissionId : submissionsRef.push().getKey();
            if (submissionId != null) {
                // ensure we set/update currentSubmissionId immediately so the UI won't think there's no submission
                currentSubmissionId = submissionId;

                submissionsRef.child(submissionId).setValue(submissionMap)
                        .addOnSuccessListener(aVoid -> {
                            if (tvStatus != null) tvStatus.setText(resubmitRequested ? "Resubmitted successfully!" : "Uploaded successfully!");
                            // Refresh the UI to reflect the stored submission
                            // call checkSingleSubmission to read back the stored node and update UI properly
                            checkExistingSubmission();

                            // Notify parent Activity and other components that a submission was made/updated
                            notifySubmissionUpdated();

                            // Also set result so parent activities launched for result can detect change
                            if (getActivity() != null) {
                                getActivity().setResult(Activity.RESULT_OK);
                            }

                            Toast.makeText(context, resubmitRequested ? "Resubmission successful!" : "Submission successful!", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            if (tvStatus != null) tvStatus.setText("Failed to upload file.");
                            if (btnSubmitFile != null) btnSubmitFile.setEnabled(true);
                            if (btnSelectFile != null) btnSelectFile.setEnabled(true);
                            Toast.makeText(context, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            } else {
                throw new Exception("Unable to create submission ID");
            }

        } catch (Exception e) {
            Log.e(TAG, "RealtimeUpload failed", e);
            if (tvStatus != null) tvStatus.setText("Upload failed: " + e.getMessage());
            if (btnSubmitFile != null) btnSubmitFile.setEnabled(true);
            if (btnSelectFile != null) btnSelectFile.setEnabled(true);
            Toast.makeText(context, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Send a local broadcast that other parts of the app can listen to (e.g., StudentActivitiesActivity)
    private void notifySubmissionUpdated() {
        try {
            Context ctx = getContext();
            if (ctx == null) return;
            Intent intent = new Intent(ACTION_SUBMISSION_UPDATED);
            intent.putExtra(EXTRA_ACTIVITY_ID, activityId);
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent);
        } catch (Exception e) {
            Log.w(TAG, "notifySubmissionUpdated failed: " + e.getMessage());
        }
    }

    private String getFileName(Uri uri) {
        if (uri == null) return "unknown_file";
        String result = null;
        Context context = getContext();
        if ("content".equals(uri.getScheme()) && context != null) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) result = cursor.getString(nameIndex);
                }
            } catch (Exception e) {
                Log.w(TAG, "getFileName failed: " + e.getMessage());
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