package com.example.nextgen.student;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.nextgen.R;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import com.example.nextgen.SessionManager;
import com.example.nextgen.sync.ExamMetadata;
import com.example.nextgen.sync.PresenceHelper;

public class StudentQRScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 200;
    private PreviewView previewView;
    private boolean scannedOnce = false;

    // Image picker launcher
    private ActivityResultLauncher<String[]> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);

        previewView = findViewById(R.id.previewView);

        // Register image picker (OpenDocument) to select images from gallery/storage
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    // Optionally take persistable permission
                    try {
                        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    } catch (Exception ignored) { }
                    decodeQrFromImageUri(uri);
                }
        );

        // Wire optional gallery button if present in layout
        try {
            Button btnGallery = findViewById(R.id.btnSelectFromGallery);
            if (btnGallery != null) {
                btnGallery.setOnClickListener(v -> pickImageFromGallery());
            }
        } catch (Exception ignored) { }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        }
    }

    // Launch the image picker
    private void pickImageFromGallery() {
        // MIME filter for images
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
                e.printStackTrace();
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
        if (mediaImage != null) {
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
                if (!scannedOnce) {
                    scannedOnce = true;
                    handleScannedExam(result.getText());
                }
            } catch (NotFoundException e) {
                // no QR found yet
            } finally {
                imageProxy.close();
            }
        } else {
            imageProxy.close();
        }
    }

    // Decode a QR from a selected image Uri (background thread)
    private void decodeQrFromImageUri(Uri uri) {
        new Thread(() -> {
            Bitmap bmp = null;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to open image", Toast.LENGTH_SHORT).show());
                    return;
                }

                // decode bounds to compute sample size
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, bounds);
            } catch (Exception e) {
                // continue to fallback decode
            }

            // Re-open stream and decode with modest sampling to avoid OOM
            try (InputStream is2 = getContentResolver().openInputStream(uri)) {
                if (is2 == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to open image", Toast.LENGTH_SHORT).show());
                    return;
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2; // adjust if needed
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bmp = BitmapFactory.decodeStream(is2, null, opts);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show());
                return;
            }

            if (bmp == null) {
                runOnUiThread(() -> Toast.makeText(this, "Could not decode image", Toast.LENGTH_SHORT).show());
                return;
            }

            try {
                int width = bmp.getWidth();
                int height = bmp.getHeight();
                int[] pixels = new int[width * height];
                bmp.getPixels(pixels, 0, width, 0, 0, width, height);

                LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
                BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
                MultiFormatReader reader = new MultiFormatReader();

                Result result = reader.decode(binaryBitmap);
                final String qrText = result.getText();

                runOnUiThread(() -> {
                    // Use same handling as camera scan
                    handleScannedExam(qrText);
                });
            } catch (NotFoundException nf) {
                runOnUiThread(() -> Toast.makeText(this, "No QR code found in that image", Toast.LENGTH_SHORT).show());
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to decode QR: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (!bmp.isRecycled()) bmp.recycle();
            }
        }).start();
    }

    // inside handleScannedExam(String qrText)
    private void handleScannedExam(String qrText) {
        String examId = null;
        ExamMetadata meta = null;

        // Try parse JSON payload first
        try {
            org.json.JSONObject obj = new org.json.JSONObject(qrText);
            if (obj.has("examId")) {
                examId = obj.optString("examId", null);
                meta = new ExamMetadata();
                meta.examTitle = obj.optString("examTitle", null);
                if (obj.has("scheduledAt")) {
                    long val = obj.optLong("scheduledAt", 0L);
                    meta.scheduledAt = val < 1_000_000_000_000L && val > 0 ? val * 1000L : val;
                }
                if (obj.has("durationMinutes")) meta.durationMinutes = obj.optInt("durationMinutes", 0);
                meta.teacherName = obj.optString("teacherName", null);
                meta.courseName = obj.optString("courseName", null);
                meta.specializationName = obj.optString("specializationName", null);
                meta.yearName = obj.optString("yearName", null);
                meta.sectionName = obj.optString("sectionName", null);
                meta.courseDisplay = obj.optString("courseDisplay", null);
            } else {
                // not JSON with examId - treat full qrText as examId
                examId = qrText;
                meta = null;
            }
        } catch (org.json.JSONException e) {
            // Not JSON — assume the QR just contains examId
            examId = qrText;
            meta = null;
        }

        final String finalExamId = examId;
        final ExamMetadata finalMeta = meta;
        if (finalExamId == null || finalExamId.isEmpty()) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Invalid QR payload", Toast.LENGTH_SHORT).show();
                scannedOnce = false;
            });
            return;
        }

        // Get studentId
        SessionManager sessionManager = new SessionManager(this);
        final String studentId = sessionManager.getStudentId(this);
        if (studentId == null || studentId.isEmpty()) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Student ID not found", Toast.LENGTH_LONG).show();
                scannedOnce = false;
            });
            return;
        }

        // Save locally (works offline). PresenceHelper will enqueue sync.
        PresenceHelper.savePresenceLocallyAndEnqueue(getApplicationContext(), finalExamId, studentId, finalMeta);

        runOnUiThread(() -> {
            Toast.makeText(StudentQRScannerActivity.this, "Presence saved locally.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
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
            finish();
        }
    }
}