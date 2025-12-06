package com.finale.nextgen.teacher;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.finale.nextgen.SessionManager;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ManageQuizActivity - updated to avoid publishing "Unknown" as teacherName.
 *
 * Changes:
 * - publishQuizForStudents now prefers sessionManager.getFullName() when available.
 * - If sessionManager lacks a full name, the code attempts to read Teachers/{teacherId}/fullName
 *   and only writes teacherName if a non-empty value is found. Otherwise it omits teacherName
 *   (teacherId is still published).
 */
public class ManageQuizActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private QuizAdapter adapter;
    private List<String> courseDisplayList = new ArrayList<>();
    private List<String> assignedSubjects = new ArrayList<>();
    private SessionManager sessionManager;
    private DatabaseReference teachersRef;
    private String teacherId;
    private static boolean isFirebaseInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_quiz);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        if (!isFirebaseInitialized) {
            FirebaseApp.initializeApp(this);
            isFirebaseInitialized = true;
        }

        recyclerView = findViewById(R.id.recyclerQuizzes);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        sessionManager = new SessionManager(this);
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
        teacherId = sessionManager.getUserId();

        if (teacherId != null) fetchTeacherData(teacherId);

        findViewById(R.id.btnAddQuiz).setOnClickListener(v -> {
            if (assignedSubjects.isEmpty()) {
                Toast.makeText(this, "No assigned subjects to create a quiz.", Toast.LENGTH_SHORT).show();
            } else showAddQuizDialog();
        });

        loadQuizzes();
    }

    // Fetch teacher's subjects and courses from Firebase
    private void fetchTeacherData(String teacherId) {
        assignedSubjects.clear();
        courseDisplayList.clear();

        teachersRef.child(teacherId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.child("assignedSubjects").exists()) {
                    for (DataSnapshot subSnap : snapshot.child("assignedSubjects").getChildren()) {
                        String subject = subSnap.getValue(String.class);
                        if (subject != null && !assignedSubjects.contains(subject))
                            assignedSubjects.add(subject);
                    }
                }

                if (snapshot.child("courseDisplays").exists()) {
                    for (DataSnapshot courseSnap : snapshot.child("courseDisplays").getChildren()) {
                        String course = courseSnap.getValue(String.class);
                        if (course != null && !courseDisplayList.contains(course))
                            courseDisplayList.add(course);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ManageQuizActivity.this,
                        "Error loading teacher data: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadQuizzes() {
        DatabaseReference quizzesRef = FirebaseDatabase.getInstance()
                .getReference("Quizzes").child(teacherId);

        quizzesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Quiz> quizList = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Quiz quiz = child.getValue(Quiz.class);
                    if (quiz != null) quizList.add(quiz);
                }

                adapter = new QuizAdapter(ManageQuizActivity.this, quizList, new QuizAdapter.OnQuizActionListener() {
                    @Override
                    public void onEdit(Quiz quiz) { showEditQuizDialog(quiz); }

                    @Override
                    public void onDelete(Quiz quiz) {
                        new AlertDialog.Builder(ManageQuizActivity.this)
                                .setTitle("Delete Quiz")
                                .setMessage("Are you sure you want to delete this quiz and all its questions?")
                                .setPositiveButton("Yes", (dialog, which) -> deleteQuizFromFirebase(quiz))
                                .setNegativeButton("No", null)
                                .show();
                    }

                    @Override
                    public void onActivate(Quiz quiz, boolean isActive) {
                        quiz.setActive(isActive);
                        syncQuizToFirebase(quiz); // will update public index as well
                    }

                    @Override
                    public void onViewStudents(Quiz quiz) {
                        // Split section (courseDisplay) into parts: "Course - Specialization - Year - Section"
                        String section = quiz.getSection() != null ? quiz.getSection() : "";
                        String[] parts = section.split(" - ");
                        String quizCourse = parts.length > 0 ? parts[0].trim() : "";
                        String quizSpecialization = parts.length > 1 ? parts[1].trim() : "";
                        String quizYear = parts.length > 2 ? parts[2].trim() : "";
                        String quizSection = parts.length > 3 ? parts[3].trim() : "";

                        Intent intent = new Intent(ManageQuizActivity.this, QuizMonitorActivity.class);

                        // Provide both quiz-specific and exam-named extras for compatibility
                        intent.putExtra("quizId", quiz.getFirebaseKey());
                        intent.putExtra("quizName", quiz.getQuizName());
                        intent.putExtra("quizCourseName", quizCourse);
                        intent.putExtra("quizSpecialization", quizSpecialization);
                        intent.putExtra("quizYearName", quizYear);
                        intent.putExtra("quizSectionName", quizSection);

                        // also add exam* extras to support monitor implementations expecting those keys
                        intent.putExtra("examId", quiz.getFirebaseKey());
                        intent.putExtra("examTitle", quiz.getQuizName());
                        intent.putExtra("examCourseName", quizCourse);
                        intent.putExtra("examSpecialization", quizSpecialization);
                        intent.putExtra("examYearName", quizYear);
                        intent.putExtra("examSectionName", quizSection);

                        startActivity(intent);
                    }

                    @Override
                    public void onGenerateQuestions(Quiz quiz) {
                        // Open GenerateQuizActivity
                        Intent intent = new Intent(ManageQuizActivity.this, GenerateQuizActivity.class);
                        intent.putExtra("quizId", quiz.getFirebaseKey());
                        intent.putExtra("quizName", quiz.getQuizName());
                        startActivity(intent);
                    }
                });

                recyclerView.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ManageQuizActivity.this, "Failed to fetch quizzes: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddQuizDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_quiz, null);
        EditText etQuizName = dialogView.findViewById(R.id.etQuizName);
        Spinner spCourse = dialogView.findViewById(R.id.spCourse);
        Spinner spSubject = dialogView.findViewById(R.id.spSubject);
        Spinner spDuration = dialogView.findViewById(R.id.spDuration);
        TextView tvSchedule = dialogView.findViewById(R.id.tvSchedule);

        List<String> courseList = courseDisplayList.isEmpty() ? List.of("No Course Assigned") : courseDisplayList;
        spCourse.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, courseList));

        ArrayList<String> subjectList = new ArrayList<>();
        ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, subjectList);
        spSubject.setAdapter(subjectAdapter);

        spCourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCourse = courseList.get(position);
                DatabaseReference subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");

                subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        subjectList.clear();
                        for (DataSnapshot subjectSnap : snapshot.getChildren()) {
                            String subjectId = subjectSnap.getKey();
                            String subjectName = subjectSnap.child("name").getValue(String.class);

                            String courseName = subjectSnap.child("courseName").getValue(String.class);
                            String specializationName = subjectSnap.child("specializationName").getValue(String.class);
                            String yearName = subjectSnap.child("yearName").getValue(String.class);
                            String sectionName = subjectSnap.child("sectionName").getValue(String.class);

                            String display = courseName + " - " + specializationName + " - " + yearName + " - " + sectionName;

                            if (display.equals(selectedCourse) && assignedSubjects.contains(subjectId)) {
                                subjectList.add(subjectName);
                            }
                        }
                        if (subjectList.isEmpty()) subjectList.add("No subjects found for this course");
                        subjectAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        String[] durations = {"15", "30", "60"};
        spDuration.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, durations));

        final Calendar selectedDate = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        tvSchedule.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            new DatePickerDialog(this, (datePicker, year, month, day) -> {
                now.set(year, month, day);
                MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_12H)
                        .setHour(now.get(Calendar.HOUR_OF_DAY))
                        .setMinute(now.get(Calendar.MINUTE))
                        .setTitleText("Select Quiz Time")
                        .build();

                timePicker.addOnPositiveButtonClickListener(dialog -> {
                    now.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                    now.set(Calendar.MINUTE, timePicker.getMinute());
                    selectedDate.setTimeInMillis(now.getTimeInMillis());
                    tvSchedule.setText(sdf.format(now.getTime()));
                });

                timePicker.show(getSupportFragmentManager(), "QUIZ_TIME_PICKER");
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Add Quiz")
                .setView(dialogView)
                .setPositiveButton("Save", (d, which) -> {
                    String name = etQuizName.getText().toString().trim();
                    String course = spCourse.getSelectedItem().toString();
                    String subject = spSubject.getSelectedItem().toString();
                    int duration = Integer.parseInt(spDuration.getSelectedItem().toString());
                    long scheduledAt = selectedDate.getTimeInMillis();

                    if (name.isEmpty() || subject.isEmpty() || course.isEmpty() || subject.equals("No subjects found for this course")) {
                        Toast.makeText(this, "Please fill all fields properly", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Quiz newQuiz = new Quiz(name, subject, duration, scheduledAt, course, teacherId);
                    syncQuizToFirebase(newQuiz);
                    loadQuizzes();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditQuizDialog(Quiz quiz) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_quiz, null);
        EditText etQuizName = dialogView.findViewById(R.id.etQuizName);
        Spinner spCourse = dialogView.findViewById(R.id.spCourse);
        Spinner spSubject = dialogView.findViewById(R.id.spSubject);
        Spinner spDuration = dialogView.findViewById(R.id.spDuration);
        TextView tvSchedule = dialogView.findViewById(R.id.tvSchedule);

        etQuizName.setText(quiz.getQuizName());

        List<String> courseList = courseDisplayList;
        spCourse.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, courseList));
        int courseIndex = courseList.indexOf(quiz.getSection());
        if (courseIndex >= 0) spCourse.setSelection(courseIndex);

        ArrayList<String> subjectList = new ArrayList<>();
        ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, subjectList);
        spSubject.setAdapter(subjectAdapter);

        spCourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCourse = courseList.get(position);
                DatabaseReference subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");

                subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        subjectList.clear();
                        for (DataSnapshot subjectSnap : snapshot.getChildren()) {
                            String subjectId = subjectSnap.getKey();
                            String subjectName = subjectSnap.child("name").getValue(String.class);

                            String courseName = subjectSnap.child("courseName").getValue(String.class);
                            String specializationName = subjectSnap.child("specializationName").getValue(String.class);
                            String yearName = subjectSnap.child("yearName").getValue(String.class);
                            String sectionName = subjectSnap.child("sectionName").getValue(String.class);

                            String display = courseName + " - " + specializationName + " - " + yearName + " - " + sectionName;

                            if (display.equals(selectedCourse) && assignedSubjects.contains(subjectId)) {
                                subjectList.add(subjectName);
                            }
                        }
                        if (subjectList.isEmpty()) subjectList.add("No subjects found for this course");
                        subjectAdapter.notifyDataSetChanged();

                        int index = subjectList.indexOf(quiz.getSubject());
                        if (index >= 0) spSubject.setSelection(index);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        String[] durations = {"15", "30", "60"};
        spDuration.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, durations));
        spDuration.setSelection(java.util.Arrays.asList(durations).indexOf(String.valueOf(quiz.getDurationMinutes())));

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        Calendar selectedDate = Calendar.getInstance();
        selectedDate.setTimeInMillis(quiz.getScheduledAt());
        tvSchedule.setText(sdf.format(selectedDate.getTime()));

        tvSchedule.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            now.setTimeInMillis(selectedDate.getTimeInMillis());
            new DatePickerDialog(this, (datePicker, year, month, day) -> {
                now.set(year, month, day);
                MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_12H)
                        .setHour(now.get(Calendar.HOUR_OF_DAY))
                        .setMinute(now.get(Calendar.MINUTE))
                        .setTitleText("Select Quiz Time")
                        .build();

                timePicker.addOnPositiveButtonClickListener(dialog -> {
                    now.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                    now.set(Calendar.MINUTE, timePicker.getMinute());
                    selectedDate.setTimeInMillis(now.getTimeInMillis());
                    tvSchedule.setText(sdf.format(now.getTime()));
                });

                timePicker.show(getSupportFragmentManager(), "EDIT_QUIZ_TIME_PICKER");
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Edit Quiz")
                .setView(dialogView)
                .setPositiveButton("Save", (d, which) -> {
                    quiz.setQuizName(etQuizName.getText().toString().trim());
                    quiz.setSection(spCourse.getSelectedItem().toString());
                    quiz.setSubject(spSubject.getSelectedItem().toString());
                    quiz.setDurationMinutes(Integer.parseInt(spDuration.getSelectedItem().toString()));
                    quiz.setScheduledAt(selectedDate.getTimeInMillis());

                    syncQuizToFirebase(quiz);
                    loadQuizzes();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Save quiz under teacher node and publish a lightweight summary to AvailableQuizzes so students can discover it.
     */
    private void syncQuizToFirebase(Quiz quiz) {
        DatabaseReference quizzesRef = FirebaseDatabase.getInstance()
                .getReference("Quizzes").child(teacherId);
        String firebaseKey = quiz.getFirebaseKey();
        if (firebaseKey == null || firebaseKey.isEmpty()) {
            firebaseKey = quizzesRef.push().getKey();
            quiz.setFirebaseKey(firebaseKey);
        }

        quizzesRef.child(firebaseKey).setValue(quiz)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseSync", "Quiz synced successfully");
                    // publish lightweight summary for students
                    publishQuizForStudents(quiz);
                })
                .addOnFailureListener(e -> Log.e("FirebaseSync", "Failed to sync quiz", e));
    }

    /**
     * Publish a lightweight summary of the quiz to a public node students can query.
     * Path: AvailableQuizzes/{quizId}
     *
     * This version avoids writing "Unknown" as teacherName by:
     *  - preferring sessionManager.getFullName()
     *  - if missing, attempting to read Teachers/{teacherId}/fullName and using it if non-empty
     *  - only writing teacherName when we have a non-empty real name
     */
    private void publishQuizForStudents(Quiz quiz) {
        if (quiz == null || quiz.getFirebaseKey() == null) return;

        final String key = quiz.getFirebaseKey();
        final DatabaseReference publicRef = FirebaseDatabase.getInstance().getReference("AvailableQuizzes").child(key);

        final Map<String, Object> summary = new HashMap<>();
        summary.put("quizId", key);
        summary.put("quizName", quiz.getQuizName());
        summary.put("subjectName", quiz.getSubject());
        summary.put("teacherId", teacherId);
        // do not put teacherName here yet - we will add it only when we have a valid non-empty name
        summary.put("section", quiz.getSection()); // "Course - Spec - Year - Section"
        summary.put("durationMinutes", quiz.getDurationMinutes());
        summary.put("scheduledAt", quiz.getScheduledAt());
        summary.put("active", quiz.isActive());

        // prefer stored session name
        String sessionFullName = sessionManager != null ? sessionManager.getFullName() : null;
        if (sessionFullName != null && !sessionFullName.trim().isEmpty()) {
            summary.put("teacherName", sessionFullName.trim());
            // write summary immediately
            publicRef.setValue(summary)
                    .addOnSuccessListener(aVoid -> Log.d("PublishQuiz", "Published quiz to AvailableQuizzes/" + key + " with teacherName from session"))
                    .addOnFailureListener(e -> Log.e("PublishQuiz", "Failed to publish quiz", e));
            return;
        }

        // session name not present -> try to fetch from Teachers/{teacherId}/fullName
        if (teacherId != null && !teacherId.trim().isEmpty()) {
            teachersRef.child(teacherId).child("fullName").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String fullName = snapshot.getValue(String.class);
                    if (fullName != null && !fullName.trim().isEmpty()) {
                        summary.put("teacherName", fullName.trim());
                        Log.d("PublishQuiz", "Fetched teacher fullName for publish: " + fullName);
                    } else {
                        Log.d("PublishQuiz", "No fullName in Teachers/" + teacherId + "; publishing without teacherName");
                    }
                    publicRef.setValue(summary)
                            .addOnSuccessListener(aVoid -> Log.d("PublishQuiz", "Published quiz to AvailableQuizzes/" + key))
                            .addOnFailureListener(e -> Log.e("PublishQuiz", "Failed to publish quiz", e));
                }

                @Override public void onCancelled(@NonNull DatabaseError error) {
                    Log.w("PublishQuiz", "Failed to fetch teacher fullName; publishing without teacherName: " + error.getMessage());
                    // proceed to publish without teacherName (teacherId still present)
                    publicRef.setValue(summary)
                            .addOnSuccessListener(aVoid -> Log.d("PublishQuiz", "Published quiz to AvailableQuizzes/" + key + " (fallback)"))
                            .addOnFailureListener(e -> Log.e("PublishQuiz", "Failed to publish quiz", e));
                }
            });
        } else {
            // no teacherId - just publish summary without teacherName (unlikely)
            publicRef.setValue(summary)
                    .addOnSuccessListener(aVoid -> Log.d("PublishQuiz", "Published quiz to AvailableQuizzes/" + key + " (no teacherId)"))
                    .addOnFailureListener(e -> Log.e("PublishQuiz", "Failed to publish quiz", e));
        }
    }

    /**
     * Update only the 'active' flag in the public index (useful for toggles).
     */
    private void updatePublicQuizActiveFlag(String quizId, boolean active) {
        if (quizId == null) return;
        DatabaseReference publicRef = FirebaseDatabase.getInstance()
                .getReference("AvailableQuizzes").child(quizId).child("active");
        publicRef.setValue(active)
                .addOnSuccessListener(aVoid -> Log.d("PublishQuiz", "Updated active flag for " + quizId))
                .addOnFailureListener(e -> Log.e("PublishQuiz", "Failed to update active flag", e));
    }

    /**
     * Delete quiz from teacher node, questions node, and public AvailableQuizzes index.
     */
    private void deleteQuizFromFirebase(Quiz quiz) {
        if (quiz.getFirebaseKey() != null) {
            String quizKey = quiz.getFirebaseKey();

            DatabaseReference quizRef = FirebaseDatabase.getInstance()
                    .getReference("Quizzes").child(teacherId).child(quizKey);
            DatabaseReference questionsRef = FirebaseDatabase.getInstance()
                    .getReference("Questions").child(quizKey);
            DatabaseReference publicRef = FirebaseDatabase.getInstance()
                    .getReference("AvailableQuizzes").child(quizKey);

            // Delete teacher node first
            quizRef.removeValue().addOnSuccessListener(aVoid -> {
                // Then delete questions
                questionsRef.removeValue()
                        .addOnSuccessListener(qVoid -> {
                            // Then remove public listing
                            publicRef.removeValue()
                                    .addOnSuccessListener(pv -> {
                                        Toast.makeText(this, "Quiz and related questions deleted", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("FirebaseDelete", "Failed to remove public index", e);
                                        Toast.makeText(this, "Quiz deleted but failed to remove public index", Toast.LENGTH_SHORT).show();
                                    });
                        })
                        .addOnFailureListener(e -> {
                            Log.e("FirebaseDelete", "Failed to delete questions", e);
                            Toast.makeText(this, "Failed to delete related questions", Toast.LENGTH_SHORT).show();
                        });
            }).addOnFailureListener(e -> {
                Log.e("FirebaseDelete", "Failed to delete quiz", e);
                Toast.makeText(this, "Failed to delete quiz", Toast.LENGTH_SHORT).show();
            });
        }
    }
}