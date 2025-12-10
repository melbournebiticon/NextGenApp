package com.finale.nextgen.teacher;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.finale.nextgen.R;
import com.finale.nextgen.SessionManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class CreateActivityActivity extends AppCompatActivity {

    EditText etTitle, etDescription, etDueDate;
    Spinner spTargetCourse, spTargetSubject, spMainTerm, spSubTerm, spMaxScore;
    Button btnPickDate, btnCreate;
    Calendar calendar;
    String teacherName;

    // 🔥 EDIT MODE VARIABLES
    String activityId = null;
    boolean isEditMode = false;

    DatabaseReference activitiesRef, coursesRef, subjectsRef, teacherRef;
    SessionManager sessionManager;

    ArrayList<String> courseDisplayList = new ArrayList<>();
    ArrayList<String> assignedSubjects = new ArrayList<>();
    ArrayList<String> subjectIdList = new ArrayList<>();
    ArrayList<String> subjectCodeList = new ArrayList<>();
    ArrayList<String> maxScoreOptions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        TextView tvToolbarTitle = toolbar.findViewById(R.id.tvToolbarTitle);
        tvToolbarTitle.setText("Create New Activity");
        toolbar.setNavigationOnClickListener(v -> finish());

        sessionManager = new SessionManager(this);

        etTitle = findViewById(R.id.etActivityTitle);
        etDescription = findViewById(R.id.etActivityDescription);
        etDueDate = findViewById(R.id.etDueDate);
        spTargetCourse = findViewById(R.id.spTargetCourseSection);
        spTargetSubject = findViewById(R.id.spTargetSubject);
        spMainTerm = findViewById(R.id.spMainTerm);
        spSubTerm = findViewById(R.id.spSubTerm);
        spMaxScore = findViewById(R.id.spMaxScore);
        btnPickDate = findViewById(R.id.btnPickDate);
        btnCreate = findViewById(R.id.btnCreateActivity);

        calendar = Calendar.getInstance();
        activitiesRef = FirebaseDatabase.getInstance().getReference("Activities");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        teacherRef = FirebaseDatabase.getInstance().getReference("Teachers");

        // ⭐ CHECK EDIT MODE
        checkIfEditMode();

        fetchTeacherData();
        setupDatePicker();
        setupSpinners();
        setupMaxScoreSpinner();

        btnCreate.setOnClickListener(v -> {
            if (isEditMode) updateActivity();
            else createActivity();
        });

        spTargetCourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadSubjectsForCourse(courseDisplayList.get(position));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // 🔥 CHECK IF EDIT MODE ---------------------------------------------------
    private void checkIfEditMode() {

        activityId = getIntent().getStringExtra("ACTIVITY_ID");

        if (activityId != null) {
            isEditMode = true;
            btnCreate.setText("Update Activity");

            // Load passed data
            etTitle.setText(getIntent().getStringExtra("TITLE"));
            etDescription.setText(getIntent().getStringExtra("DESCRIPTION"));
            etDueDate.setText(getIntent().getStringExtra("DUE_DATE"));

            // Store values for spinners — set after loading
            selectedCourseBeforeEdit = getIntent().getStringExtra("COURSE_DISPLAY");
            selectedSubjectBeforeEdit = getIntent().getStringExtra("SUBJECT");
            selectedMainTermBeforeEdit = getIntent().getStringExtra("MAIN_TERM");
            selectedSubTermBeforeEdit = getIntent().getStringExtra("SUB_TERM");
            selectedMaxScoreBeforeEdit = getIntent().getStringExtra("MAX_SCORE");

            // If due date provided, attempt to parse and set calendar so pickers start from that value
            String due = getIntent().getStringExtra("DUE_DATE");
            if (due != null && !due.trim().isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                try {
                    calendar.setTime(sdf.parse(due));
                } catch (ParseException ignored) { /* keep current calendar */ }
            }
        }
    }

    // Temp holders for spinner values before loaded
    String selectedCourseBeforeEdit = "";
    String selectedSubjectBeforeEdit = "";
    String selectedMainTermBeforeEdit = "";
    String selectedSubTermBeforeEdit = "";
    String selectedMaxScoreBeforeEdit = "";

    // ------------------------------------------------------------------------

    private void setupMaxScoreSpinner() {
        maxScoreOptions.clear();
        for (int i = 10; i <= 100; i += 10) maxScoreOptions.add(String.valueOf(i));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, maxScoreOptions);
        spMaxScore.setAdapter(adapter);

        // If edit mode and a previous max score exists, set it
        spMaxScore.post(() -> {
            if (isEditMode && selectedMaxScoreBeforeEdit != null && !selectedMaxScoreBeforeEdit.isEmpty()) {
                int pos = adapter.getPosition(selectedMaxScoreBeforeEdit);
                if (pos >= 0) spMaxScore.setSelection(pos);
            }
        });
    }

    private void setupDatePicker() {
        btnPickDate.setOnClickListener(v -> {
            final Calendar now = Calendar.getInstance();
            DatePickerDialog datePicker = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        // set selected date on calendar
                        calendar.set(year, month, dayOfMonth);
                        // then show time picker (no time restrictions per your request)
                        showTimePickerAndSet();
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );

            // disable past dates
            datePicker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePicker.show();
        });
    }

    /**
     * Show a TimePickerDialog without hour validation (user can pick any time).
     * After selection the combined date+time is formatted and placed into etDueDate.
     */
    private void showTimePickerAndSet() {
        int initialHour = calendar.get(Calendar.HOUR_OF_DAY);
        int initialMinute = calendar.get(Calendar.MINUTE);

        new android.app.TimePickerDialog(
                this,
                (timeView, hour, minute) -> {
                    // set calendar time and update EditText (no restriction)
                    calendar.set(Calendar.HOUR_OF_DAY, hour);
                    calendar.set(Calendar.MINUTE, minute);

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    etDueDate.setText(sdf.format(calendar.getTime()));
                },
                initialHour,
                initialMinute,
                false
        ).show();
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> mainTermAdapter =
                ArrayAdapter.createFromResource(this,
                        R.array.main_term_options, android.R.layout.simple_spinner_dropdown_item);

        ArrayAdapter<CharSequence> subTermAdapter =
                ArrayAdapter.createFromResource(this,
                        R.array.sub_term_options, android.R.layout.simple_spinner_dropdown_item);

        spMainTerm.setAdapter(mainTermAdapter);
        spSubTerm.setAdapter(subTermAdapter);

        // 🔥 Set spinner values if EDIT mode
        spMainTerm.post(() -> {
            if (isEditMode && selectedMainTermBeforeEdit != null) {
                int pos = mainTermAdapter.getPosition(selectedMainTermBeforeEdit);
                if (pos >= 0) spMainTerm.setSelection(pos);
            }
        });

        spSubTerm.post(() -> {
            if (isEditMode && selectedSubTermBeforeEdit != null) {
                int pos = subTermAdapter.getPosition(selectedSubTermBeforeEdit);
                if (pos >= 0) spSubTerm.setSelection(pos);
            }
        });
    }

    private void fetchTeacherData() {
        String teacherId = sessionManager.getUserId();
        if (teacherId == null) return;

        teacherRef.child(teacherId).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        courseDisplayList.clear();
                        assignedSubjects.clear();
                        teacherName = snapshot.child("fullName").getValue(String.class);

                        for (DataSnapshot courseSnap : snapshot.child("courseDisplays").getChildren()) {
                            String course = courseSnap.getValue(String.class);
                            if (course != null) courseDisplayList.add(course);
                        }

                        for (DataSnapshot subSnap : snapshot.child("assignedSubjects").getChildren()) {
                            String subject = subSnap.getValue(String.class);
                            if (subject != null) assignedSubjects.add(subject);
                        }

                        if (courseDisplayList.isEmpty()) courseDisplayList.add("No Course Assigned");

                        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                CreateActivityActivity.this,
                                android.R.layout.simple_spinner_dropdown_item,
                                courseDisplayList
                        );
                        spTargetCourse.setAdapter(adapter);

                        // 🔥 SET spinner to old value if EDIT
                        spTargetCourse.post(() -> {
                            if (isEditMode && selectedCourseBeforeEdit != null) {
                                int pos = adapter.getPosition(selectedCourseBeforeEdit);
                                if (pos >= 0) spTargetCourse.setSelection(pos);
                            }
                        });

                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(CreateActivityActivity.this,
                                "Failed to load teacher data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadSubjectsForCourse(String selectedCourseDisplay) {
        ArrayList<String> subjectList = new ArrayList<>();
        ArrayAdapter<String> subjectAdapter =
                new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_dropdown_item, subjectList);

        spTargetSubject.setAdapter(subjectAdapter);

        subjectsRef.addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        subjectList.clear();
                        subjectIdList.clear();
                        subjectCodeList.clear();

                        for (DataSnapshot subjectSnap : snapshot.getChildren()) {

                            String subjectId = subjectSnap.getKey();
                            String name = subjectSnap.child("name").getValue(String.class);
                            String code = subjectSnap.child("code").getValue(String.class);

                            String courseName = subjectSnap.child("courseName").getValue(String.class);
                            String specializationName = subjectSnap.child("specializationName").getValue(String.class);
                            String yearName = subjectSnap.child("yearName").getValue(String.class);
                            String sectionName = subjectSnap.child("sectionName").getValue(String.class);

                            String display = courseName + " - " +
                                    specializationName + " - " +
                                    yearName + " - " +
                                    sectionName;

                            if (display.equals(selectedCourseDisplay)
                                    && assignedSubjects.contains(subjectId)) {

                                subjectList.add(name);
                                subjectIdList.add(subjectId);
                                subjectCodeList.add(code);
                            }
                        }

                        if (subjectList.isEmpty()) {
                            subjectList.add("No subjects found for this course");
                            subjectIdList.add("");
                        }

                        subjectAdapter.notifyDataSetChanged();

                        // 🔥 Set original selected subject if EDIT
                        spTargetSubject.post(() -> {
                            if (isEditMode && selectedSubjectBeforeEdit != null) {

                                int pos = subjectList.indexOf(selectedSubjectBeforeEdit);

                                if (pos >= 0) spTargetSubject.setSelection(pos);
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(CreateActivityActivity.this,
                                "Failed to load subjects", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ✨ CREATE NEW ACTIVITY -------------------------------------------------
    private void createActivity() {

        if (!validateInputs()) return;

        String teacherId = sessionManager.getUserId();
        String newActivityId = activitiesRef.push().getKey();

        Map<String, Object> map = createActivityMap(newActivityId);

        activitiesRef.child(newActivityId).setValue(map)
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, "Activity posted!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ✨ UPDATE EXISTING ACTIVITY --------------------------------------------
    private void updateActivity() {

        if (!validateInputs()) return;

        Map<String, Object> map = createActivityMap(activityId);

        activitiesRef.child(activityId).updateChildren(map)
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, "Activity updated!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ------------------------------------------------------------------------

    private Map<String, Object> createActivityMap(String id) {

        Map<String, Object> map = new HashMap<>();

        map.put("id", id);
        map.put("teacherId", sessionManager.getUserId());
        map.put("teacherName", teacherName);

        map.put("title", etTitle.getText().toString().trim());
        map.put("description", etDescription.getText().toString().trim());
        map.put("dueDate", etDueDate.getText().toString().trim());

        map.put("courseDisplay", spTargetCourse.getSelectedItem().toString());
        map.put("subject", spTargetSubject.getSelectedItem().toString());

        map.put("subjectId", getSelectedSubjectId());
        map.put("subjectCode", getSelectedSubjectCode());

        map.put("mainTerm", spMainTerm.getSelectedItem().toString());
        map.put("subTerm", spSubTerm.getSelectedItem().toString());

        map.put("maxScore", spMaxScore.getSelectedItem().toString());

        map.put("createdAt", System.currentTimeMillis());

        return map;
    }

    private boolean validateInputs() {
        if (etTitle.getText().toString().trim().isEmpty() ||
                etDescription.getText().toString().trim().isEmpty() ||
                etDueDate.getText().toString().trim().isEmpty()) {

            Toast.makeText(this, "Please fill all fields properly", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private String getSelectedSubjectId() {
        int pos = spTargetSubject.getSelectedItemPosition();
        if (pos >= 0 && pos < subjectIdList.size()) return subjectIdList.get(pos);
        return null;
    }

    private String getSelectedSubjectCode() {
        int pos = spTargetSubject.getSelectedItemPosition();
        if (pos >= 0 && pos < subjectCodeList.size()) return subjectCodeList.get(pos);
        return null;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}