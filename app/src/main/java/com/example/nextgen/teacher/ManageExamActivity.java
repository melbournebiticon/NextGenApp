package com.example.nextgen.teacher;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
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
import android.util.Log;


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
import java.util.HashMap;

// 🏆 NEW Imports for Material Time Picker (for previous request)
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;


public class ManageExamActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ExamAdapter adapter;
    private AppDatabase db;
    private Button btnAddExam;
    private List<String> courseDisplayList = new ArrayList<>();

    private DatabaseReference teachersRef;
    private String teacherId;
    private List<String> assignedSubjects = new ArrayList<>();
    private String courseDisplay = "";

    private static boolean isFirebaseInitialized = false;
    private SessionManager sessionManager;


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

        sessionManager = new SessionManager(this);

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
        teachersRef.child(teacherId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                assignedSubjects.clear();
                List<String> courseDisplays = new ArrayList<>();

                List<String> courseIds = new ArrayList<>();
                for (DataSnapshot courseSnap : snapshot.child("courseIds").getChildren()) {
                    String id = courseSnap.getValue(String.class);
                    if (id != null) courseIds.add(id);
                }
// Save course IDs in SessionManager (you’ll need to add methods for this)
                sessionManager.saveCourseIds(courseIds);


                // 🔹 Fetch assigned subjects
                for (DataSnapshot subSnap : snapshot.child("assignedSubjects").getChildren()) {
                    String subject = subSnap.getValue(String.class);
                    if (subject != null) assignedSubjects.add(subject);
                }

                // 🔹 Fetch multiple course displays
                for (DataSnapshot courseSnap : snapshot.child("courseDisplays").getChildren()) {
                    String course = courseSnap.getValue(String.class);
                    if (course != null) courseDisplays.add(course);
                }

                // 🔹 Save teacher full name to SessionManager
                String fullName = snapshot.child("fullName").getValue(String.class);
                if (fullName != null && !fullName.isEmpty()) {
                    sessionManager.saveSession(teacherId, "teacher", fullName);
                }

                if (courseDisplays.isEmpty()) {
                    Toast.makeText(ManageExamActivity.this, "No courses assigned.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ManageExamActivity.this,
                            "Loaded " + courseDisplays.size() + " course(s).", Toast.LENGTH_SHORT).show();
                }

                if (assignedSubjects.isEmpty()) {
                    Toast.makeText(ManageExamActivity.this, "No assigned subjects found.", Toast.LENGTH_SHORT).show();
                }

                // 🔹 Store courses for dialog use
                courseDisplayList = courseDisplays;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ManageExamActivity.this,
                        "Error loading teacher data: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }



    private void loadExams() {
        List<Exam> examList = new ArrayList<>();

        DatabaseReference examsRef = FirebaseDatabase.getInstance()
                .getReference("Exams")
                .child(teacherId);

        examsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                examList.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Exam exam = new Exam();
                    exam.setFirebaseKey(child.child("examId").getValue(String.class));
                    exam.setExamName(child.child("examTitle").getValue(String.class));
                    exam.setSubject(child.child("subjectName").getValue(String.class));
                    exam.setTeacherId(child.child("teacherId").getValue(String.class));
                    exam.setDurationMinutes(
                            child.child("durationMinutes").getValue(Integer.class) != null ?
                                    child.child("durationMinutes").getValue(Integer.class) : 0
                    );
                    exam.setScheduledAt(
                            child.child("scheduledAt").getValue(Long.class) != null ?
                                    child.child("scheduledAt").getValue(Long.class) : 0
                    );
                    exam.setActive(
                            child.child("active").getValue(Boolean.class) != null ?
                                    child.child("active").getValue(Boolean.class) : false
                    );
                    exam.setSection(child.child("courseDisplay").getValue(String.class));

                    // Note: You might want to update your Exam object here to store the new 'scheduledDateDisplay'
                    // exam.setScheduledDateDisplay(child.child("scheduledDateDisplay").getValue(String.class));

                    examList.add(exam);

                    // Optional: save to Room for offline support
                    new Thread(() -> {
                        db.examDao().insert(exam);
                    }).start();
                }

                adapter = new ExamAdapter(ManageExamActivity.this, examList,
                        new ExamAdapter.OnExamActionListener() {
                            @Override
                            public void onEdit(Exam exam) {
                                showEditExamDialog(exam);
                            }

                            @Override
                            public void onDelete(Exam exam) {
                                new Thread(() -> {
                                    db.examDao().deleteById(exam.getId());
                                    deleteExamFromFirebase(exam);
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
                                String examId = exam.getFirebaseKey();
                                if (examId == null || examId.isEmpty()) examId = String.valueOf(exam.getId());
                                intent.putExtra("examId", examId);
                                intent.putExtra("examTitle", exam.getExamName());
                                startActivity(intent);
                            }

                            @Override
                            public void onActivate(Exam exam, boolean isActive) {
                                new Thread(() -> {
                                    exam.setActive(isActive);
                                    db.examDao().updateExam(exam);
                                    syncExamToFirebase(exam);
                                }).start();
                            }
                        });

                recyclerView.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ManageExamActivity.this,
                        "Failed to fetch exams: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteExamFromFirebase(Exam exam) {
        if (exam.getFirebaseKey() != null && !exam.getFirebaseKey().isEmpty()) {
            FirebaseDatabase.getInstance()
                    .getReference("Exams")
                    .child(exam.getTeacherId())
                    .child(exam.getFirebaseKey())
                    .removeValue()
                    .addOnSuccessListener(aVoid -> Log.d("FirebaseDelete", "Exam deleted successfully"))
                    .addOnFailureListener(e -> Log.e("FirebaseDelete", "Failed to delete exam", e));
        }
    }




    // ===== ADD EXAM DIALOG (Updated with Material TimePicker) =====
    private void showAddExamDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_exam, null);

        final EditText etExamName = dialogView.findViewById(R.id.etExamName);
        final Spinner spCourse = dialogView.findViewById(R.id.spCourse);
        final Spinner spSubject = dialogView.findViewById(R.id.spSubject);
        final Spinner spDuration = dialogView.findViewById(R.id.spDuration);
        final TextView tvSchedule = dialogView.findViewById(R.id.tvSchedule);

        String[] durations = {"30 minutes", "60 minutes", "120 minutes"};
        spDuration.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, durations));

        final Calendar selectedDate = Calendar.getInstance();
        final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

        // 🏆 START: Updated Time Picker Logic (Using Material TimePicker)
        tvSchedule.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            new DatePickerDialog(this, (datePicker, year, month, day) -> {
                now.set(year, month, day);

                MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                        .setTitleText("Select Exam Time")
                        .setHour(now.get(Calendar.HOUR_OF_DAY))
                        .setMinute(now.get(Calendar.MINUTE))
                        .setTimeFormat(TimeFormat.CLOCK_12H)
                        // .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD) // Uncomment kung gusto mo ng direct keyboard
                        .build();

                timePicker.addOnPositiveButtonClickListener(dialog -> {
                    int hour = timePicker.getHour();
                    int minute = timePicker.getMinute();

                    // Update the Calendar object with the new time
                    now.set(Calendar.HOUR_OF_DAY, hour);
                    now.set(Calendar.MINUTE, minute);

                    selectedDate.setTimeInMillis(now.getTimeInMillis());
                    tvSchedule.setText(sdf.format(now.getTime()));
                });

                timePicker.show(getSupportFragmentManager(), "TIME_PICKER");

            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
        });
        // 🏆 END: Updated Time Picker Logic

        // Course spinner setup
        List<String> courseList = courseDisplayList.isEmpty()
                ? List.of("No Course Assigned")
                : courseDisplayList;

        spCourse.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, courseList));

        // Subject spinner
        ArrayList<String> subjectList = new ArrayList<>();
        ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, subjectList);
        spSubject.setAdapter(subjectAdapter);

        // Update subjects when a course is selected
        spCourse.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedCourseDisplay = courseList.get(position);
                DatabaseReference subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");

                subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        subjectList.clear();
                        for (DataSnapshot subjectSnap : snapshot.getChildren()) {
                            String name = subjectSnap.child("name").getValue(String.class);
                            String courseName = subjectSnap.child("courseName").getValue(String.class);
                            String specializationName = subjectSnap.child("specializationName").getValue(String.class);
                            String yearName = subjectSnap.child("yearName").getValue(String.class);
                            String sectionName = subjectSnap.child("sectionName").getValue(String.class);



                            String display = courseName + " - " + specializationName + " - " + yearName + " - " + sectionName;

                            if (display.equals(selectedCourseDisplay) && assignedSubjects.contains(name)) {
                                subjectList.add(name);
                            }
                        }
                        if (subjectList.isEmpty()) subjectList.add("No subjects found for this course");
                        subjectAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ManageExamActivity.this, "Failed to load subjects", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Exam")
                .setView(dialogView)
                .setPositiveButton("Save", (d, which) -> {
                    String name = etExamName.getText().toString().trim();
                    String course = spCourse.getSelectedItem() != null ? spCourse.getSelectedItem().toString() : "";
                    String subject = spSubject.getSelectedItem() != null ? spSubject.getSelectedItem().toString() : "";
                    String selectedDuration = spDuration.getSelectedItem().toString();
                    long scheduledAt = selectedDate.getTimeInMillis();

                    if (name.isEmpty() || subject.isEmpty() || course.isEmpty() || subject.equals("No subjects found for this course")) {
                        Toast.makeText(ManageExamActivity.this, "Please fill all fields properly", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int duration = Integer.parseInt(selectedDuration.split(" ")[0]);

                    new Thread(() -> {
                        Exam newExam = new Exam(subject, name, duration, scheduledAt, course);
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



    // ===== SYNC EXAM TO FIREBASE (UPDATED) =====
    private void syncExamToFirebase(Exam exam) {
        DatabaseReference examsRef = FirebaseDatabase.getInstance().getReference("Exams").child(teacherId);

        // Generate key if needed
        String firebaseKey = exam.getFirebaseKey();
        if (firebaseKey == null || firebaseKey.isEmpty()) {
            firebaseKey = examsRef.push().getKey();
            exam.setFirebaseKey(firebaseKey);

            // Update Room DB with the new key
            new Thread(() -> db.examDao().updateExam(exam)).start();
        }

        // Create ExamModel (for Firebase)
        String createdAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());

        // Example: "BSIT - SD - 1 - A"
        String[] parts = exam.getSection().split(" - ");
        String courseName = parts.length > 0 ? parts[0] : "";
        String specializationName = parts.length > 1 ? parts[1] : "";
        String yearName = parts.length > 2 ? parts[2] : "";
        String sectionName = parts.length > 3 ? parts[3] : "";
        String teacherName = sessionManager.getFullName();

        // Build courseDisplay like student query expects
        String courseDisplayValue = courseName + " - " + specializationName + " - " + yearName + " - " + sectionName;

        // 🏆 NEW: I-convert ang Milliseconds pabalik sa readable String format
        // Ito ang magbibigay ng "Oct 28, 2025 10:00 AM" sa database.
        SimpleDateFormat readableSDF = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        String scheduledDateDisplayValue = readableSDF.format(new java.util.Date(exam.getScheduledAt()));


        ExamModelTeacher examModel = new ExamModelTeacher(
                firebaseKey,
                exam.getExamName(),
                exam.getSubject(),
                "", // courseId
                courseName,
                specializationName,
                yearName,
                sectionName,
                teacherId,
                teacherName,
                exam.getDurationMinutes(),
                exam.getScheduledAt(), // LONG value (Epoch Time)
                exam.isActive(),
                createdAt,
                courseDisplayValue,
                scheduledDateDisplayValue // 🏆 NEW FIELD: Readable Date String
        );


        // Save to Firebase
        examsRef.child(firebaseKey).setValue(examModel)
                .addOnSuccessListener(aVoid -> Log.d("FirebaseSync", "Exam synced successfully with readable date: " + scheduledDateDisplayValue))
                .addOnFailureListener(e -> Log.e("FirebaseSync", "Failed to sync exam", e));
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


    // ===== EDIT EXAM DIALOG (Updated with Material TimePicker) =====
    private void showEditExamDialog(Exam exam) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_exam, null);

        EditText etExamName = dialogView.findViewById(R.id.etExamName);
        Spinner spSubject = dialogView.findViewById(R.id.spSubject);
        Spinner spCourse = dialogView.findViewById(R.id.spCourse); // Added course spinner

        Spinner spDuration = dialogView.findViewById(R.id.spDuration);
        TextView tvSchedule = dialogView.findViewById(R.id.tvSchedule);

        // Course spinner setup (from add dialog, but simplified for edit)
        spCourse.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, courseDisplayList));
        // Simple setting of course selection based on current exam data
        int courseIndex = courseDisplayList.indexOf(exam.getSection());
        if (courseIndex >= 0) spCourse.setSelection(courseIndex);


        ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, assignedSubjects);
        spSubject.setAdapter(subjectAdapter);

        etExamName.setText(exam.getExamName());


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

        // 🏆 START: Updated Time Picker Logic (Using Material TimePicker)
        tvSchedule.setOnClickListener(v -> {
            // Note: selectedDate already has the exam time
            int initialHour = selectedDate.get(Calendar.HOUR_OF_DAY);
            int initialMinute = selectedDate.get(Calendar.MINUTE);

            new DatePickerDialog(this, (datePicker, year, month, day) -> {
                selectedDate.set(year, month, day);

                MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                        .setTitleText("Select Exam Time")
                        .setHour(initialHour)
                        .setMinute(initialMinute)
                        .setTimeFormat(TimeFormat.CLOCK_12H)
                        // .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                        .build();

                timePicker.addOnPositiveButtonClickListener(dialog -> {
                    int hour = timePicker.getHour();
                    int minute = timePicker.getMinute();

                    // Update the Calendar object with the new time
                    selectedDate.set(Calendar.HOUR_OF_DAY, hour);
                    selectedDate.set(Calendar.MINUTE, minute);

                    tvSchedule.setText(sdf.format(selectedDate.getTime()));
                });

                timePicker.show(getSupportFragmentManager(), "EDIT_TIME_PICKER");

            }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
        });
        // 🏆 END: Updated Time Picker Logic

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Exam")
                .setView(dialogView)
                .setPositiveButton("Save", (d, which) -> {
                    exam.setExamName(etExamName.getText().toString().trim());
                    exam.setSubject(spSubject.getSelectedItem().toString());
                    exam.setSection(spCourse.getSelectedItem().toString()); // Update section

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