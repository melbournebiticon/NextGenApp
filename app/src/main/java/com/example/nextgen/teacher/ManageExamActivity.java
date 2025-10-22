package com.example.nextgen.teacher;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.nextgen.admin.TeacherModel;
import android.content.SharedPreferences;



import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;


public class ManageExamActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ExamAdapter adapter;
    private AppDatabase db;
    private Button btnAddExam;

    private DatabaseReference teachersRef;
    private String teacherId;
    private List<String> assignedSubjects = new ArrayList<>();
    private String courseDisplay = "";

    private static boolean isFirebaseInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_exam);

        if (!isFirebaseInitialized) {
            FirebaseApp.initializeApp(this);
            isFirebaseInitialized = true;
        }

        recyclerView = findViewById(R.id.recyclerExams);
        btnAddExam = findViewById(R.id.btnAddExam);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = AppDatabase.getInstance(this);
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");

        SessionManager sessionManager = new SessionManager(this);
        teacherId = sessionManager.getUserId();

        if (teacherId != null) {
            fetchTeacherData(teacherId);
        } else {
            Toast.makeText(this, "Teacher ID not found!", Toast.LENGTH_SHORT).show();
        }

        loadExams();

        btnAddExam.setOnClickListener(v -> {
            if (assignedSubjects.isEmpty()) {
                Toast.makeText(this, "No assigned subjects to create an exam.", Toast.LENGTH_SHORT).show();
            } else {
                showAddExamDialog();
            }
        });
    }

    // ===== FETCH TEACHER DATA (courseDisplay + assignedSubjects) =====
    private void fetchTeacherData(String teacherId) {
        teachersRef.child(teacherId).keepSynced(true);

        teachersRef.child(teacherId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                assignedSubjects.clear();

                // Load multiple courseDisplays if available
                assignedSubjects.clear();
                List<String> courseDisplays = new ArrayList<>();

                for (DataSnapshot subSnap : snapshot.child("assignedSubjects").getChildren()) {
                    String subject = subSnap.getValue(String.class);
                    if (subject != null) assignedSubjects.add(subject);
                }

// ✅ Fetch all courseDisplays
                for (DataSnapshot courseSnap : snapshot.child("courseDisplays").getChildren()) {
                    String course = courseSnap.getValue(String.class);
                    if (course != null) courseDisplays.add(course);
                }

                if (courseDisplays.isEmpty()) {
                    Toast.makeText(ManageExamActivity.this, "No courses assigned.", Toast.LENGTH_SHORT).show();
                } else {
                    courseDisplay = String.join(", ", courseDisplays); // Optional, if you still want a single string
                }


                if (assignedSubjects.isEmpty()) {
                    Toast.makeText(ManageExamActivity.this, "No assigned subjects found.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ManageExamActivity.this,
                            "Loaded " + assignedSubjects.size() + " assigned subjects.",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ManageExamActivity.this,
                        "Error loading teacher data: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ===== LOAD LOCAL EXAMS (Room offline support) =====
    private void loadExams() {
        new Thread(() -> {
            List<Exam> examList = db.examDao().getAllExams();

            runOnUiThread(() -> {
                adapter = new ExamAdapter(this, examList, new ExamAdapter.OnExamActionListener() {
                    @Override
                    public void onEdit(Exam exam) {
                        showEditExamDialog(exam);
                    }

                    @Override
                    public void onDelete(Exam exam) {
                        new Thread(() -> {
                            db.examDao().deleteById(exam.getId());
                            runOnUiThread(ManageExamActivity.this::loadExams);
                        }).start();
                    }

                    @Override
                    public void onReset(Exam exam) {
                        new Thread(() -> {
                            exam.setActive(false);
                            db.examDao().updateExam(exam);
                            runOnUiThread(ManageExamActivity.this::loadExams);
                        }).start();
                    }

                    @Override
                    public void onGenerate(Exam exam) {
                        Intent intent = new Intent(ManageExamActivity.this, GenerateQuestionsActivity.class);
                        intent.putExtra("examId", exam.getId());
                        intent.putExtra("examTitle", exam.getExamName());
                        startActivity(intent);
                    }

                    @Override
                    public void onActivate(Exam exam, boolean isActive) {
                        new Thread(() -> {
                            exam.setActive(isActive);
                            db.examDao().updateExam(exam);
                        }).start();
                    }
                });

                recyclerView.setAdapter(adapter);
            });
        }).start();
    }

    // ===== ADD EXAM DIALOG =====
    private void showAddExamDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_exam, null);

        EditText etExamName = dialogView.findViewById(R.id.etExamName);
        Spinner spCourse = dialogView.findViewById(R.id.spCourse);
        Spinner spSubject = dialogView.findViewById(R.id.spSubject);
        EditText etSection = dialogView.findViewById(R.id.etSection);
        Spinner spDuration = dialogView.findViewById(R.id.spDuration);
        TextView tvSchedule = dialogView.findViewById(R.id.tvSchedule);

        String[] durations = {"30 minutes", "60 minutes", "120 minutes"};
        spDuration.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, durations));

        Calendar selectedDate = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

        tvSchedule.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            new DatePickerDialog(this, (datePicker, year, month, day) -> {
                now.set(year, month, day);
                new TimePickerDialog(this, (timePicker, hour, minute) -> {
                    now.set(Calendar.HOUR_OF_DAY, hour);
                    now.set(Calendar.MINUTE, minute);
                    selectedDate.setTimeInMillis(now.getTimeInMillis());
                    tvSchedule.setText(sdf.format(now.getTime()));
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
        });

        // ✅ Load single course and subjects
        List<String> courseList = new ArrayList<>();
        if (courseDisplay != null && !courseDisplay.isEmpty()) {
            courseList.add(courseDisplay);
        } else {
            courseList.add("No Course Assigned");
        }

        ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, courseList);
        spCourse.setAdapter(courseAdapter);

        ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, assignedSubjects);
        spSubject.setAdapter(subjectAdapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Exam")
                .setView(dialogView)
                .setPositiveButton("Save", (d, which) -> {
                    String name = etExamName.getText().toString().trim();
                    String course = spCourse.getSelectedItem() != null ? spCourse.getSelectedItem().toString() : "";
                    String subject = spSubject.getSelectedItem() != null ? spSubject.getSelectedItem().toString() : "";
                    String section = etSection.getText().toString().trim();
                    String selectedDuration = spDuration.getSelectedItem().toString();

                    if (name.isEmpty() || subject.isEmpty() || section.isEmpty() || course.isEmpty()) {
                        Toast.makeText(ManageExamActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int duration = Integer.parseInt(selectedDuration.split(" ")[0]);
                    long scheduledAt = selectedDate.getTimeInMillis();

                    new Thread(() -> {
                        Exam newExam = new Exam(subject, name, duration, scheduledAt, section);
                        db.examDao().insert(newExam);
                        syncExamToFirebase(newExam);
                        runOnUiThread(() -> {
                            loadExams();
                            Toast.makeText(ManageExamActivity.this, "Exam added successfully", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
    }

    // ===== SYNC EXAM TO FIREBASE =====
    private void syncExamToFirebase(Exam exam) {
        DatabaseReference examsRef = FirebaseDatabase.getInstance().getReference("Exams").child(teacherId);
        String key = examsRef.push().getKey();
        if (key != null) {
            examsRef.child(key).setValue(exam);
        }
    }
    private void cacheTeacherData(TeacherModel teacher) {
        SharedPreferences prefs = getSharedPreferences("TeacherPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("teacherId", teacher.getId());
        editor.putString("fullName", teacher.getFullName());
        editor.putString("email", teacher.getEmail());
        editor.putStringSet("assignedSubjects", new HashSet<>(teacher.getAssignedSubjects()));
        editor.putStringSet("courseDisplays", new HashSet<>(teacher.getCourseDisplays()));
        editor.apply();
    }


    // ===== EDIT EXAM DIALOG =====
    private void showEditExamDialog(Exam exam) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_exam, null);

        EditText etExamName = dialogView.findViewById(R.id.etExamName);
        Spinner spSubject = dialogView.findViewById(R.id.spSubject);
        EditText etSection = dialogView.findViewById(R.id.etSection);
        Spinner spDuration = dialogView.findViewById(R.id.spDuration);
        TextView tvSchedule = dialogView.findViewById(R.id.tvSchedule);

        ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, assignedSubjects);
        spSubject.setAdapter(subjectAdapter);

        etExamName.setText(exam.getExamName());
        etSection.setText(exam.getSection());

        int subjectIndex = assignedSubjects.indexOf(exam.getSubject());
        if (subjectIndex >= 0) spSubject.setSelection(subjectIndex);

        String[] durations = {"30 minutes", "60 minutes", "120 minutes"};
        spDuration.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, durations));

        int durationPos = (exam.getDurationMinutes() == 60) ? 1 :
                (exam.getDurationMinutes() == 120) ? 2 : 0;
        spDuration.setSelection(durationPos);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        Calendar selectedDate = Calendar.getInstance();
        selectedDate.setTimeInMillis(exam.getScheduledAt());
        tvSchedule.setText(sdf.format(selectedDate.getTime()));

        tvSchedule.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            new DatePickerDialog(this, (datePicker, year, month, day) -> {
                now.set(year, month, day);
                new TimePickerDialog(this, (timePicker, hour, minute) -> {
                    now.set(Calendar.HOUR_OF_DAY, hour);
                    now.set(Calendar.MINUTE, minute);
                    selectedDate.setTimeInMillis(now.getTimeInMillis());
                    tvSchedule.setText(sdf.format(now.getTime()));
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Exam")
                .setView(dialogView)
                .setPositiveButton("Save", (d, which) -> {
                    exam.setExamName(etExamName.getText().toString().trim());
                    exam.setSubject(spSubject.getSelectedItem().toString());
                    exam.setSection(etSection.getText().toString().trim());
                    String selectedDuration = spDuration.getSelectedItem().toString();
                    exam.setDurationMinutes(Integer.parseInt(selectedDuration.split(" ")[0]));
                    exam.setScheduledAt(selectedDate.getTimeInMillis());

                    new Thread(() -> {
                        db.examDao().updateExam(exam);
                        syncExamToFirebase(exam);
                        runOnUiThread(this::loadExams);
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
    }

}
