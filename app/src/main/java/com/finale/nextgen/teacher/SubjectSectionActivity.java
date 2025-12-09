package com.finale.nextgen.teacher;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SubjectSectionActivity - updated to normalize "Pending" -> null so UI doesn't treat it as graded.
 *
 * Key points:
 * - When loading submissions we convert score == "Pending" (case-insensitive) to null.
 * - updateStudentSubmission also normalizes "Pending".
 * - Adapter (SectionAdapter) should rely on submission.getScore() == null to decide Grade button state.
 */
public class SubjectSectionActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SectionAdapter sectionAdapter;
    private List<StudentModel> studentList;
    private DatabaseReference studentsRef;
    private DatabaseReference submissionsRef;
    private DatabaseReference activitiesRef;
    private String activityId;
    private String maxScore; // activity-level maxScore (fallback)
    private boolean gradingEnabled = false;

    // Class info passed via intent
    private String courseNameIntent, specializationIntent, yearIntent, sectionIntent;

    private TextView tvMaxScore;
    private TextView tvDueDate;
    private TextView tvSubjectTitle;
    private TextView tvCourseDisplay;

    private static final String TAG = "SubjectSectionActivity";

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_section);

        // header views (optional)
        tvMaxScore = findViewById(R.id.tvMaxScore);
        tvDueDate = findViewById(R.id.tvDueDate);
        tvSubjectTitle = findViewById(R.id.tvSubjectTitle);
        tvCourseDisplay = findViewById(R.id.tvCourseDisplay);

        // Get activity info from intent
        activityId = getIntent().getStringExtra("activityId");
        maxScore = null;
        gradingEnabled = false;

        // Save class info from intent
        courseNameIntent = getIntent().getStringExtra("courseName");
        specializationIntent = getIntent().getStringExtra("specializationName");
        yearIntent = getIntent().getStringExtra("yearName");
        sectionIntent = getIntent().getStringExtra("sectionName");

        // set header text from intent (if present)
        if (tvSubjectTitle != null) {
            String title = getIntent().getStringExtra("title");
            tvSubjectTitle.setText(title != null ? title : "Activity Submissions");
        }
        if (tvCourseDisplay != null) {
            String courseDisplay = getIntent().getStringExtra("courseDisplay");
            tvCourseDisplay.setText(courseDisplay != null ? courseDisplay : "");
        }

        recyclerView = findViewById(R.id.recyclerViewStudents);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        studentList = new ArrayList<>();

        // Firebase refs
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        submissionsRef = FirebaseDatabase.getInstance().getReference("Submissions");
        activitiesRef = FirebaseDatabase.getInstance().getReference("Activities");

        // Fetch activity maxScore first (if activityId is supplied)
        if (activityId != null && !activityId.isEmpty()) {
            fetchActivityMaxScoreAndInit(activityId);
        } else {
            // no activityId -> init adapter (grading disabled)
            maxScore = null;
            gradingEnabled = false;
            initAdapterAndLoad();
        }
    }

    private void fetchActivityMaxScoreAndInit(String activityId) {
        activitiesRef.child(activityId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object ms = snapshot.child("maxScore").getValue();
                if (ms != null) {
                    String msStr = String.valueOf(ms).trim();
                    if (!msStr.isEmpty()) {
                        maxScore = msStr;
                        gradingEnabled = true;
                        Log.d(TAG, "Fetched activity maxScore=" + maxScore + " for activityId=" + activityId);
                    } else {
                        maxScore = null;
                        gradingEnabled = false;
                        Toast.makeText(SubjectSectionActivity.this, "Activity maxScore not set — grading disabled.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    maxScore = null;
                    gradingEnabled = false;
                    Toast.makeText(SubjectSectionActivity.this, "Activity maxScore not set — grading disabled.", Toast.LENGTH_LONG).show();
                }

                // update header UI
                if (tvMaxScore != null) tvMaxScore.setText("Max Score: " + (maxScore != null ? maxScore : "-"));
                Object due = snapshot.child("dueDate").getValue();
                if (tvDueDate != null) tvDueDate.setText("Due: " + (due != null ? String.valueOf(due) : "-"));

                initAdapterAndLoad();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to fetch activity data: " + error.getMessage());
                maxScore = null;
                gradingEnabled = false;
                Toast.makeText(SubjectSectionActivity.this, "Failed to load activity data — grading disabled.", Toast.LENGTH_LONG).show();
                initAdapterAndLoad();
            }
        });
    }

    private void initAdapterAndLoad() {
        sectionAdapter = new SectionAdapter(studentList, maxScore, gradingEnabled);
        recyclerView.setAdapter(sectionAdapter);

        if (courseNameIntent != null && specializationIntent != null && yearIntent != null && sectionIntent != null) {
            loadStudentsByClass(courseNameIntent, specializationIntent, yearIntent, sectionIntent);
        } else {
            Toast.makeText(this, "Class information not found!", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadStudentsByClass(String courseName, String specialization, String year, String section) {
        studentsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                studentList.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    StudentModel student = snap.getValue(StudentModel.class);
                    if (student != null &&
                            courseName.equals(student.getCourseName()) &&
                            specialization.equals(student.getSpecializationName()) &&
                            year.equals(student.getYearName()) &&
                            section.equals(student.getSectionName())) {
                        studentList.add(student);
                    }
                }
                if (sectionAdapter != null) sectionAdapter.notifyDataSetChanged();
                if (activityId != null) loadSubmissions(activityId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load students: " + error.getMessage());
            }
        });
    }

    /**
     * IMPORTANT: normalize "Pending" (case-insensitive) to null so adapter doesn't treat it as graded.
     */
    private void loadSubmissions(String activityId) {
        submissionsRef.orderByChild("activityId").equalTo(activityId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (StudentModel s : studentList) s.setSubmission(null);

                        for (DataSnapshot snap : snapshot.getChildren()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> data = (Map<String, Object>) snap.getValue();
                            if (data == null) continue;

                            SubmissionModel submission = new SubmissionModel();
                            submission.setSubmissionId(snap.getKey());
                            submission.setActivityId(asString(data.get("activityId")));
                            submission.setStudentId(asString(data.get("studentId")));
                            submission.setFileName(asString(data.get("fileName")));
                            submission.setFileData(asString(data.get("fileData")));

                            // normalize score and treat "Pending" as no-score
                            String rawScore = asString(data.get("score"));
                            if (rawScore != null && "pending".equalsIgnoreCase(rawScore.trim())) {
                                rawScore = null;
                            }
                            submission.setScore(rawScore);

                            // prefer submission node's maxScore, fallback to activity-level (but keep null if absent)
                            Object nodeMax = data.get("maxScore");
                            if (nodeMax != null && String.valueOf(nodeMax).trim().length() > 0) {
                                submission.setMaxScore(String.valueOf(nodeMax));
                            } else if (maxScore != null && !maxScore.isEmpty()) {
                                submission.setMaxScore(maxScore);
                            } else {
                                submission.setMaxScore(null);
                            }

                            submission.setResubmitRequested(asBoolean(data.get("resubmitRequested")));
                            submission.setViewed(asBoolean(data.get("viewed")));
                            submission.setSubmittedAt(asString(data.get("submittedAt")));

                            // attach submission to matching student
                            if (submission.getStudentId() != null) {
                                boolean linked = false;
                                for (int i = 0; i < studentList.size(); i++) {
                                    StudentModel student = studentList.get(i);
                                    if (student.getUid() != null && student.getUid().equals(submission.getStudentId())) {
                                        student.setSubmission(submission);
                                        linked = true;
                                        Log.d(TAG, "Linked submissionId=" + submission.getSubmissionId() + " to studentUid=" + student.getUid());
                                        break;
                                    }
                                }
                                if (!linked) {
                                    Log.d(TAG, "Submission for unknown studentId=" + submission.getStudentId() + " submissionId=" + submission.getSubmissionId());
                                }
                            } else {
                                Log.w(TAG, "Submission missing studentId: submissionId=" + submission.getSubmissionId());
                            }
                        }

                        if (sectionAdapter != null) sectionAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load submissions: " + error.getMessage());
                    }
                });
    }

    // Helper converters (safe)
    private String asString(Object o) {
        if (o == null) return null;
        try {
            return String.valueOf(o);
        } catch (Exception e) {
            Log.w(TAG, "asString conversion failed: " + e.getMessage());
            return null;
        }
    }

    private boolean asBoolean(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).intValue() != 0;
        String s = String.valueOf(o).toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    /**
     * Update a single student's submission locally and refresh only that row.
     * Normalizes "Pending" -> null when updating local model.
     */
    public void updateStudentSubmission(String studentUid, SubmissionModel newSubmission) {
        if (newSubmission == null || studentUid == null) return;

        // normalize "Pending"
        if (newSubmission.getScore() != null && "pending".equalsIgnoreCase(newSubmission.getScore().trim())) {
            newSubmission.setScore(null);
        }

        if ((newSubmission.getMaxScore() == null || newSubmission.getMaxScore().isEmpty()) && maxScore != null && !maxScore.isEmpty()) {
            newSubmission.setMaxScore(maxScore);
        }

        int updatedIndex = -1;
        String targetSubmissionId = newSubmission.getSubmissionId();

        for (int i = 0; i < studentList.size(); i++) {
            StudentModel student = studentList.get(i);
            if (student.getUid() != null && student.getUid().equals(studentUid)) {
                SubmissionModel existing = student.getSubmission();
                if (existing != null) {
                    String existingId = existing.getSubmissionId() != null ? existing.getSubmissionId() : existing.getId();
                    if (targetSubmissionId != null && targetSubmissionId.equals(existingId)) {
                        student.setSubmission(newSubmission);
                        updatedIndex = i;
                        break;
                    }
                } else {
                    student.setSubmission(newSubmission);
                    updatedIndex = i;
                    break;
                }
            }
        }

        if (updatedIndex == -1 && targetSubmissionId != null) {
            for (int i = 0; i < studentList.size(); i++) {
                StudentModel student = studentList.get(i);
                SubmissionModel existing = student.getSubmission();
                if (existing != null) {
                    String existingId = existing.getSubmissionId() != null ? existing.getSubmissionId() : existing.getId();
                    if (targetSubmissionId.equals(existingId)) {
                        student.setSubmission(newSubmission);
                        updatedIndex = i;
                        break;
                    }
                }
            }
        }

        if (updatedIndex != -1 && sectionAdapter != null) {
            sectionAdapter.notifyItemChanged(updatedIndex);
        } else if (sectionAdapter != null) {
            sectionAdapter.notifyDataSetChanged();
        }
    }

    // grading method intentionally left to adapter's transaction implementation (SectionAdapter handles the grade transaction UI).
}