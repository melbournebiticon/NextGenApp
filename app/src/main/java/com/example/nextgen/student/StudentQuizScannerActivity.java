package com.example.nextgen.student;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.nextgen.R;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.example.nextgen.SessionManager;
import com.example.nextgen.sync.ExamMetadata;
import com.example.nextgen.sync.QuizPresenceHelper;

/**
 * StudentQuizScannerActivity
 *
 * Added: gallery picker support (button in scanner UI) using ActivityResultContracts.OpenDocument.
 * - Wire a Button with id btnSelectFromGallery in activity_qr_scanner.xml to enable selecting an image.
 * - Decodes selected image and feeds result into existing handleScannedQuiz flow.
 */
public class StudentQuizScannerActivity extends AppCompatActivity {

    private static final String TAG = "StudentQuizScanner";
    private static final int CAMERA_PERMISSION_REQUEST = 300;

    private PreviewView previewView;
    private volatile boolean scannedOnce = false;

    // ActivityResult launcher for picking images from gallery/storage
    private ActivityResultLauncher<String[]> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);

        previewView = findViewById(R.id.previewView);

        // register the image picker launcher
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            // try to persist permission for URI if possible (optional)
            try {
                final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (Exception ignored) {}
            // decode in background and handle if QR found
            decodeQrFromImageUri(uri);
        });

        // wire gallery button in scanner UI (optional) - add a Button with id btnSelectFromGallery in layout
        try {
            Button btnGallery = findViewById(R.id.btnSelectFromGallery);
            if (btnGallery != null) {
                btnGallery.setOnClickListener(v -> pickImageFromGallery());
            }
        } catch (Exception ignored) {}

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    // Launch the system picker for images (MIME filter)
    private void pickImageFromGallery() {
        pickImageLauncher.launch(new String[] { "image/*" });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.w(TAG, "Camera start failed", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Camera failed to start", Toast.LENGTH_SHORT).show();
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                });
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::analyzeImage);

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        androidx.camera.core.Preview preview = new androidx.camera.core.Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        @SuppressWarnings("UnsafeOptInUsageError")
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        try {
            ByteBuffer buffer = mediaImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            int width = imageProxy.getWidth();
            int height = imageProxy.getHeight();

            LuminanceSource source = new PlanarYUVLuminanceSource(
                    bytes, width, height, 0, 0, width, height, false);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            try {
                Result result = new MultiFormatReader().decode(bitmap);
                if (!scannedOnce && result != null && result.getText() != null && !result.getText().trim().isEmpty()) {
                    scannedOnce = true;
                    try {
                        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                        if (v != null) v.vibrate(100);
                    } catch (Exception ignored) {}
                    handleScannedQuiz(result.getText().trim());
                }
            } catch (NotFoundException e) {
                // no QR found in this frame — continue
            } catch (Exception e) {
                Log.w(TAG, "ZXing decode error", e);
            }
        } catch (Exception e) {
            Log.w(TAG, "analyzeImage error", e);
        } finally {
            imageProxy.close();
        }
    }

    // Parse payload, save presence locally and return result to caller (quiz list)
    private void handleScannedQuiz(String qrText) {
        // 1) Robustly parse quiz/exam id
        String parsedId = null;
        ExamMetadata meta = null;

        if (qrText == null) {
            scannedOnce = false;
            return;
        }

        String trimmed = qrText.trim();

        // support deep link: nextgen://quiz/{id} or nextgen://exam/{id}
        if (trimmed.startsWith("nextgen://")) {
            String[] parts = trimmed.split("/");
            if (parts.length >= 3) {
                parsedId = parts[parts.length - 1];
            }
        }

        // try JSON payload
        if (parsedId == null) {
            try {
                org.json.JSONObject obj = new org.json.JSONObject(trimmed);
                // prefer quizId, then examId, then id
                if (obj.has("quizId")) parsedId = obj.optString("quizId", null);
                else if (obj.has("examId")) parsedId = obj.optString("examId", null);
                else if (obj.has("id")) parsedId = obj.optString("id", null);

                if (parsedId != null) {
                    meta = new ExamMetadata();
                    meta.examTitle = obj.optString("quizName", obj.optString("examTitle", null));
                    long raw = obj.optLong("scheduledAt", 0L);
                    meta.scheduledAt = (raw > 0 && raw < 1_000_000_000_000L) ? raw * 1000L : raw;
                    meta.durationMinutes = obj.optInt("durationMinutes", 0);
                    meta.teacherName = obj.optString("teacherName", null);
                    meta.teacherId = obj.optString("teacherId", null);
                    // keep any section/course fields if present
                    meta.courseName = obj.optString("courseName", obj.optString("subjectName", null));
                    meta.sectionName = obj.optString("section", null);
                }
            } catch (org.json.JSONException ignore) {
                // not JSON
            }
        }

        // fallback plain text (could be "quiz:XYZ" or "exam:XYZ" or just id)
        if (parsedId == null) {
            parsedId = trimmed;
        }

        // normalize id: strip prefixes
        if (parsedId != null) {
            String t = parsedId.trim();
            if (t.toLowerCase().startsWith("quiz:")) t = t.substring("quiz:".length()).trim();
            else if (t.toLowerCase().startsWith("exam:")) t = t.substring("exam:".length()).trim();
            parsedId = t;
        }

        final String finalQuizId = parsedId;
        final ExamMetadata finalMeta = meta;

        if (finalQuizId == null || finalQuizId.isEmpty()) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Invalid QR payload", Toast.LENGTH_SHORT).show();
                scannedOnce = false;
            });
            return;
        }

        // 2) Resolve student key to use in DB (studentId expected by teacher)
        SessionManager sessionManager = new SessionManager(this);
        String storedStudentId = null;
        try { storedStudentId = sessionManager.getStudentId(this); } catch (Exception ignored) {}

        if (storedStudentId != null && !storedStudentId.isEmpty()) {
            // proceed using storedStudentId
            performPresenceSave(finalQuizId, finalMeta, storedStudentId);
            return;
        }

        // attempt to resolve via FirebaseAuth uid
        String uid = null;
        try { if (FirebaseAuth.getInstance().getCurrentUser() != null) uid = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}

        if (uid == null || uid.isEmpty()) {
            // no uid and no stored studentId — can't reliably write to teacher's key
            runOnUiThread(() -> {
                Toast.makeText(this, "Student ID not found (not logged in)", Toast.LENGTH_LONG).show();
                scannedOnce = false;
            });
            return;
        }

        // Query Students node for mapping uid -> studentId
        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        String finalUid = uid;
        studentsRef.orderByChild("uid").equalTo(uid).limitToFirst(1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String resolved = null;
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String sid = ds.child("studentId").getValue(String.class);
                                if (sid != null && !sid.isEmpty()) { resolved = sid; break; }
                            }
                        }
                        if (resolved != null && !resolved.isEmpty()) {
                            // save into session for future use
                            try { sessionManager.saveStudentId(resolved); } catch (Exception ignored) {}
                            performPresenceSave(finalQuizId, finalMeta, resolved);
                        } else {
                            // fallback: use uid as key (but teacher may be using studentId keys)
                            Log.w(TAG, "StudentId not found for uid=" + finalUid + " — falling back to using uid as key");
                            performPresenceSave(finalQuizId, finalMeta, finalUid);
                            runOnUiThread(() -> Toast.makeText(StudentQuizScannerActivity.this,
                                    "Student mapping not found; presence written using UID. Teacher may not see it until mapping exists.", Toast.LENGTH_LONG).show());
                        }
                    }
                    @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                        Log.w(TAG, "Failed to lookup studentId for uid: " + error.getMessage());
                        // fallback to uid
                        performPresenceSave(finalQuizId, finalMeta, finalUid);
                    }
                });
    }

    /**
     * Saves presence locally (enqueue) and attempts immediate server writes to both QuizStudents and ExamStudents
     * using the provided studentKey (either resolved studentId or fallback uid).
     */
    private void performPresenceSave(String quizId, ExamMetadata meta, String studentKey) {
        if (quizId == null || studentKey == null) return;

        // Save locally + enqueue (always) using the quiz-specific helper
        try {
            QuizPresenceHelper.saveQuizPresenceLocallyAndEnqueue(getApplicationContext(), quizId, studentKey, meta);
            Log.d(TAG, "Quiz presence saved locally/enqueued for quiz=" + quizId + " studentKey=" + studentKey);
        } catch (Exception e) {
            Log.w(TAG, "QuizPresenceHelper.saveQuizPresenceLocallyAndEnqueue failed: " + e.getMessage(), e);
        }

        // Debug display name from SessionManager if available
        SessionManager sessionManager = new SessionManager(this);
        String displayName = sessionManager.getStudentModel() != null ? sessionManager.getStudentModel().getFullName() : "unknown";

        // If network available, write immediately to QuizStudents only (quiz-specific path)
        if (isNetworkAvailable()) {
            try {
                FirebaseDatabase db = FirebaseDatabase.getInstance();

                DatabaseReference qRef = db.getReference("QuizStudents").child(quizId).child(studentKey).child("present");
                qRef.setValue(true).addOnCompleteListener(task -> {
                    Log.d(TAG, "Immediate write to QuizStudents completed success=" + task.isSuccessful() + " quiz=" + quizId + " key=" + studentKey);
                    if (!task.isSuccessful() && task.getException() != null) Log.w(TAG, "Immediate QuizStudents write error", task.getException());
                });

                // debug node
                DatabaseReference dbg = db.getReference("PresenceDebug").child(quizId).child(studentKey);
                Map<String, Object> dbgMap = new HashMap<>();
                dbgMap.put("timestamp", System.currentTimeMillis());
                dbgMap.put("displayName", displayName);
                dbgMap.put("source", "scanner");
                dbg.setValue(dbgMap).addOnCompleteListener(t -> Log.d(TAG, "PresenceDebug write success=" + t.isSuccessful()));
            } catch (Exception e) {
                Log.w(TAG, "Immediate write exception: " + e.getMessage(), e);
            }
        } else {
            Log.d(TAG, "No network available; quiz presence only enqueued for quiz=" + quizId + " key=" + studentKey);
        }

        // Return result to caller (QuizListActivity) — quizId normalized already
        Intent result = new Intent();
        result.putExtra("scanned_text", quizId);
        if (meta != null && meta.examTitle != null) result.putExtra("quizName", meta.examTitle);

        runOnUiThread(() -> {
            Toast.makeText(this, "Marked present. Returning to list...", Toast.LENGTH_SHORT).show();
            setResult(Activity.RESULT_OK, result);
            finish();
        });
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    // decode image helper (kept for debugging/edge-cases).
    private void decodeQrFromImageUri(Uri uri) {
        new Thread(() -> {
            Bitmap bmp = null;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) return;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bmp = BitmapFactory.decodeStream(is, null, opts);
            } catch (Exception e) {
                return;
            }

            if (bmp == null) return;

            try {
                int width = bmp.getWidth();
                int height = bmp.getHeight();
                int[] pixels = new int[width * height];
                bmp.getPixels(pixels, 0, width, 0, 0, width, height);

                com.google.zxing.RGBLuminanceSource source = new com.google.zxing.RGBLuminanceSource(width, height, pixels);
                BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
                Result result = new MultiFormatReader().decode(binaryBitmap);
                if (result != null && result.getText() != null) handleScannedQuiz(result.getText().trim());
            } catch (NotFoundException ignored) {
            } catch (Exception ignored) {
            } finally {
                if (!bmp.isRecycled()) bmp.recycle();
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }
}