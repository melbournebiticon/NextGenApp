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

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

/**
 * ManageQuizActivity - fixes for RecyclerView inconsistency and optimistic insert
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

    // Make quizList a field so we can remove items and notify adapter immediately
    private final List<Quiz> quizList = new ArrayList<>();

    private static final String TAG = "ManageQuizActivity";

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

        // Create adapter once and set to RecyclerView (avoids re-creating adapter during updates)
        adapter = new QuizAdapter(ManageQuizActivity.this, quizList, new QuizAdapter.OnQuizActionListener() {
            @Override public void onEdit(Quiz quiz) { showEditQuizDialog(quiz); }
            @Override public void onDelete(Quiz quiz) {
                new AlertDialog.Builder(ManageQuizActivity.this)
                        .setTitle("Delete Quiz")
                        .setMessage("Are you sure you want to delete this quiz and all its questions?")
                        .setPositiveButton("Yes", (dialog, which) -> deleteQuizFromFirebase(quiz))
                        .setNegativeButton("No", null)
                        .show();
            }
            @Override public void onActivate(Quiz quiz, boolean isActive) { quiz.setActive(isActive); syncQuizToFirebase(quiz); }
            @Override public void onViewStudents(Quiz quiz) {
                String section = quiz.getSection() != null ? quiz.getSection() : "";
                String[] parts = section.split(" - ");
                String quizCourse = parts.length > 0 ? parts[0].trim() : "";
                String quizSpecialization = parts.length > 1 ? parts[1].trim() : "";
                String quizYear = parts.length > 2 ? parts[2].trim() : "";
                String quizSection = parts.length > 3 ? parts[3].trim() : "";

                Intent intent = new Intent(ManageQuizActivity.this, QuizMonitorActivity.class);

                intent.putExtra("quizId", quiz.getFirebaseKey());
                intent.putExtra("quizName", quiz.getQuizName());
                intent.putExtra("quizCourseName", quizCourse);
                intent.putExtra("quizSpecialization", quizSpecialization);
                intent.putExtra("quizYearName", quizYear);
                intent.putExtra("quizSectionName", quizSection);

                intent.putExtra("examId", quiz.getFirebaseKey());
                intent.putExtra("examTitle", quiz.getQuizName());
                intent.putExtra("examCourseName", quizCourse);
                intent.putExtra("examSpecialization", quizSpecialization);
                intent.putExtra("examYearName", quizYear);
                intent.putExtra("examSectionName", quizSection);

                startActivity(intent);
            }
            @Override public void onGenerateQuestions(Quiz quiz) {
                Intent intent = new Intent(ManageQuizActivity.this, GenerateQuizActivity.class);
                intent.putExtra("quizId", quiz.getFirebaseKey());
                intent.putExtra("quizName", quiz.getQuizName());
                startActivity(intent);
            }
        });
        // Use stable ids (requires QuizAdapter.getItemId override)
        adapter.setHasStableIds(true);
        recyclerView.setAdapter(adapter);

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
                // Update quizList (same instance) and notify adapter on main thread
                quizList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Quiz quiz = child.getValue(Quiz.class);
                    if (quiz != null) quizList.add(quiz);
                }

                runOnUiThread(() -> {
                    try {
                        // Safe full refresh (keeps adapter instance stable)
                        adapter.notifyDataSetChanged();
                    } catch (Exception ex) {
                        Log.w(TAG, "notifyDataSetChanged failed, scheduling on recyclerView.post", ex);
                        recyclerView.post(() -> adapter.notifyDataSetChanged());
                    }
                });
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
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCourse = courseList.get(position);
                DatabaseReference subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");

                subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
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
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        String[] durations = {"15", "30", "60"};
        spDuration.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, durations));

        final Calendar selectedDate = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        tvSchedule.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            DatePickerDialog dpd = new DatePickerDialog(this, (datePicker, year, month, day) -> {
                now.set(year, month, day);
                showTimePickerWithRange(now, selectedDate, sdf, tvSchedule);
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
            dpd.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            dpd.show();
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

                    if (teacherId == null || teacherId.trim().isEmpty()) {
                        Toast.makeText(this, "Teacher not identified. Cannot create quiz.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DatabaseReference quizzesRef = FirebaseDatabase.getInstance()
                            .getReference("Quizzes").child(teacherId);
                    String newPushKey = quizzesRef.push().getKey();
                    if (newPushKey == null) newPushKey = String.valueOf(System.currentTimeMillis());

                    Quiz newQuiz = new Quiz(name, subject, duration, scheduledAt, course, teacherId);
                    newQuiz.setFirebaseKey(newPushKey);

                    // Optimistic insert: update list and notify on main thread
                    runOnUiThread(() -> {
                        quizList.add(0, newQuiz);
                        adapter.notifyItemInserted(0);
                        recyclerView.scrollToPosition(0);
                    });

                    // Sync to Firebase in background. If it fails, remove the optimistic item.
                    syncQuizToFirebase(newQuiz);
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
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCourse = courseList.get(position);
                DatabaseReference subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");

                subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
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
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
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
            DatePickerDialog dpd = new DatePickerDialog(this, (datePicker, year, month, day) -> {
                now.set(year, month, day);
                showTimePickerWithRange(now, selectedDate, sdf, tvSchedule);
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
            dpd.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            dpd.show();
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
                    // update local list immediately
                    int idx = findQuizIndexByKey(quiz.getFirebaseKey());
                    if (idx >= 0) {
                        quizList.set(idx, quiz);
                        runOnUiThread(() -> adapter.notifyItemChanged(idx));
                    } else runOnUiThread(() -> adapter.notifyDataSetChanged());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTimePickerWithRange(Calendar dateCandidate, Calendar selectedDate, SimpleDateFormat sdf, TextView tvSchedule) {
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(dateCandidate.get(Calendar.HOUR_OF_DAY))
                .setMinute(dateCandidate.get(Calendar.MINUTE))
                .setTitleText("Select Quiz Time (7:00 - 21:00)")
                .build();

        timePicker.addOnPositiveButtonClickListener(dialog -> {
            int hour = timePicker.getHour();
            int minute = timePicker.getMinute();

            if (hour < 7 || hour > 21) {
                new AlertDialog.Builder(this)
                        .setTitle("Invalid time")
                        .setMessage("Please choose a time between 7:00 AM and 9:00 PM.")
                        .setPositiveButton("Pick time again", (d, w) -> showTimePickerWithRange(dateCandidate, selectedDate, sdf, tvSchedule))
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }

            dateCandidate.set(Calendar.HOUR_OF_DAY, hour);
            dateCandidate.set(Calendar.MINUTE, minute);
            selectedDate.setTimeInMillis(dateCandidate.getTimeInMillis());
            tvSchedule.setText(sdf.format(dateCandidate.getTime()));
        });

        timePicker.show(getSupportFragmentManager(), "QUIZ_TIME_PICKER");
    }

    private int findQuizIndexByKey(String key) {
        if (key == null) return -1;
        for (int i = 0; i < quizList.size(); i++) {
            Quiz q = quizList.get(i);
            if (q != null && key.equals(q.getFirebaseKey())) return i;
        }
        return -1;
    }

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
                    publishQuizForStudents(quiz);
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseSync", "Failed to sync quiz", e);
                    // remove optimistic UI entry on main thread
                    runOnUiThread(() -> {
                        int idx = findQuizIndexByKey(quiz.getFirebaseKey());
                        if (idx >= 0) {
                            quizList.remove(idx);
                            adapter.notifyItemRemoved(idx);
                        }
                    });
                });
    }

    private void publishQuizForStudents(Quiz quiz) {
        if (quiz == null || quiz.getFirebaseKey() == null) return;

        final String key = quiz.getFirebaseKey();
        final DatabaseReference publicRef = FirebaseDatabase.getInstance().getReference("AvailableQuizzes").child(key);

        final Map<String, Object> summary = new HashMap<>();
        summary.put("quizId", key);
        summary.put("quizName", quiz.getQuizName());
        summary.put("subjectName", quiz.getSubject());
        summary.put("teacherId", teacherId);
        summary.put("section", quiz.getSection());
        summary.put("durationMinutes", quiz.getDurationMinutes());
        summary.put("scheduledAt", quiz.getScheduledAt());
        summary.put("active", quiz.isActive());

        String sessionFullName = sessionManager != null ? sessionManager.getFullName() : null;
        if (sessionFullName != null && !sessionFullName.trim().isEmpty()) {
            summary.put("teacherName", sessionFullName.trim());
            publicRef.setValue(summary)
                    .addOnSuccessListener(aVoid -> Log.d("PublishQuiz", "Published quiz to AvailableQuizzes/" + key + " with teacherName from session"))
                    .addOnFailureListener(e -> Log.e("PublishQuiz", "Failed to publish quiz", e));
            return;
        }

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
                    publicRef.setValue(summary)
                            .addOnSuccessListener(aVoid -> Log.d("PublishQuiz", "Published quiz to AvailableQuizzes/" + key + " (fallback)"))
                            .addOnFailureListener(e -> Log.e("PublishQuiz", "Failed to publish quiz", e));
                }
            });
        } else {
            publicRef.setValue(summary)
                    .addOnSuccessListener(aVoid -> Log.d("PublishQuiz", "Published quiz to AvailableQuizzes/" + key + " (no teacherId)"))
                    .addOnFailureListener(e -> Log.e("PublishQuiz", "Failed to publish quiz", e));
        }
    }

    private void deleteQuizFromFirebase(Quiz quiz) {
        if (quiz.getFirebaseKey() == null) return;
        final String quizKey = quiz.getFirebaseKey();

        DatabaseReference quizRef = FirebaseDatabase.getInstance()
                .getReference("Quizzes").child(teacherId).child(quizKey);
        DatabaseReference questionsRef = FirebaseDatabase.getInstance()
                .getReference("Questions").child(quizKey);
        DatabaseReference publicRef = FirebaseDatabase.getInstance()
                .getReference("AvailableQuizzes").child(quizKey);

        // Remove from UI immediately on main thread
        runOnUiThread(() -> {
            int idx = findQuizIndexByKey(quizKey);
            if (idx >= 0) {
                quizList.remove(idx);
                adapter.notifyItemRemoved(idx);
            }
        });

        // Delete teacher node first
        quizRef.removeValue().addOnSuccessListener(aVoid -> {
            questionsRef.removeValue()
                    .addOnSuccessListener(qVoid -> {
                        publicRef.removeValue()
                                .addOnSuccessListener(pv -> Log.d(TAG, "Deleted quiz and related nodes for " + quizKey))
                                .addOnFailureListener(e -> Log.e("FirebaseDelete", "Failed to remove public index", e));
                    })
                    .addOnFailureListener(e -> Log.e("FirebaseDelete", "Failed to delete questions", e));
        }).addOnFailureListener(e -> {
            Log.e("FirebaseDelete", "Failed to delete quiz", e);
            // If deletion failed, reload list to re-sync UI
            loadQuizzes();
        });
    }
}

