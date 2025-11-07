package com.example.nextgen.teacher;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import android.view.View;



import java.util.ArrayList;
import java.util.List;

public class ExamMonitorActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private StudentExamAdapter adapter;
    private String examTitle;
    private String examId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_monitor);

        recyclerView = findViewById(R.id.recyclerStudents);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        examTitle = getIntent().getStringExtra("examTitle");
        examId = getIntent().getStringExtra("examId");
        android.util.Log.d("ExamMonitor", "examId from intent: " + examId);

        setTitle("Monitoring: " + examTitle);


        // Show QR button
        findViewById(R.id.btnShowQR).setOnClickListener(v -> showQrDialog(examId));

        // Load students for this exam
        loadStudents();
    }

    // <-- Replace the old loadStudents() with this
    private void loadStudents() {
        String examSpecialization = getIntent().getStringExtra("examSpecialization");
        String examSectionName = getIntent().getStringExtra("examSectionName");
        String examYearName = getIntent().getStringExtra("examYearName");
        String examCourseName = getIntent().getStringExtra("examCourseName");

        DatabaseReference studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        DatabaseReference examStudentsRef = FirebaseDatabase.getInstance()
                .getReference("ExamStudents")
                .child(examId); // ✅ read per-exam student status

        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<StudentExamStatus> students = new ArrayList<>();

                examStudentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot examSnap) {
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            StudentModel student = ds.getValue(StudentModel.class);
                            if (student != null) {
                                String studentId = student.getStudentId();
                                if (studentId == null || studentId.trim().isEmpty()) {
                                    studentId = ds.getKey();
                                    student.setStudentId(studentId);
                                    ds.getRef().child("studentId").setValue(studentId);
                                }

                                // Compare course/year/spec/section
                                String studentSpec = student.getSpecializationName() != null ? student.getSpecializationName().trim() : "";
                                String studentSection = student.getSectionName() != null ? student.getSectionName().trim() : "";
                                String studentYear = student.getYearName() != null ? student.getYearName().trim() : "";
                                String studentCourse = student.getCourseName() != null ? student.getCourseName().trim() : "";

                                if (studentSpec.equalsIgnoreCase(examSpecialization)
                                        && studentSection.equalsIgnoreCase(examSectionName)
                                        && studentYear.equalsIgnoreCase(examYearName)
                                        && studentCourse.equalsIgnoreCase(examCourseName)) {

                                    // ✅ Check if student has an entry in ExamStudents
                                    boolean present = false;
                                    boolean ongoing = false;
                                    int questionsAnswered = 0;

                                    if (examSnap.hasChild(studentId)) {
                                        DataSnapshot studentExamNode = examSnap.child(studentId);
                                        present = Boolean.TRUE.equals(studentExamNode.child("present").getValue(Boolean.class));
                                        ongoing = Boolean.TRUE.equals(studentExamNode.child("ongoing").getValue(Boolean.class));
                                        Integer answered = studentExamNode.child("questionsAnswered").getValue(Integer.class);
                                        if (answered != null) questionsAnswered = answered;
                                    }

                                    students.add(new StudentExamStatus(
                                            studentId,
                                            student.getFullName(),
                                            present,
                                            ongoing,
                                            questionsAnswered,
                                            studentCourse,
                                            studentSpec,
                                            studentYear,
                                            studentSection
                                    ));
                                }
                            }
                        }

                        android.util.Log.d("ExamMonitor", "Total matched students: " + students.size());
                        adapter = new StudentExamAdapter(students, examId);
                        recyclerView.setAdapter(adapter);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        android.util.Log.e("ExamMonitor", "ExamStudents load error: " + error.getMessage());
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.e("ExamMonitor", "Students load error: " + error.getMessage());
            }
        });
    }

    private void resetStudentExam(String studentId) {
        DatabaseReference scoreRef = FirebaseDatabase.getInstance()
                .getReference("Scores")
                .child(studentId)
                .child(examId);

        scoreRef.removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(ExamMonitorActivity.this, "Exam reset for student: " + studentId, Toast.LENGTH_SHORT).show();
                // Optionally refresh the list or UI
                loadStudents();
            } else {
                Toast.makeText(ExamMonitorActivity.this, "Failed to reset exam for student: " + studentId, Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void showQrDialog(String examId) {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            int size = 300;
            BitMatrix bitMatrix = writer.encode(examId, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            View dialogView = getLayoutInflater().inflate(R.layout.dialog_exam_qr, null);
            ImageView qrImageView = dialogView.findViewById(R.id.qrImageView);
            qrImageView.setImageBitmap(bitmap);

            androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create();

            dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
            dialog.show();

        } catch (WriterException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate QR", Toast.LENGTH_SHORT).show();
        }
    }
    private void generateExamQR(String examId) {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            int size = 500;
            BitMatrix bitMatrix = writer.encode(examId, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            ImageView qrImage = findViewById(R.id.qrImageView); // Make sure you add this ImageView in XML
            qrImage.setImageBitmap(bitmap);

        } catch (WriterException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate QR", Toast.LENGTH_SHORT).show();
        }
    }






}

