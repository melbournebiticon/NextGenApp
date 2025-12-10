package com.finale.nextgen.teacher;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.finale.nextgen.R;
import com.finale.nextgen.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * StudentAttendanceActivity
 *
 * Full file with teacher-side improvements:
 * - Attendance records saved at Attendance/{sectionId}/{teacherId}/{yyyy-MM-dd}/{studentKey}
 * - When persisting attendance, teacher full name (Teachers/{teacherId}.fullName or displayName)
 *   and the assigned subject display (Subjects/{subjectId}.name) are resolved and written into:
 *     - Attendance (per-teacher)
 *     - Students/{...}/attendanceHistory (student-facing copy)
 *     - Teachers/{teacherId}/attendanceHistory (teacher-facing preview copy)  <-- NEW
 * - Keeps per-teacher AttendanceSummary updates and previous behavior.
 */
public class StudentAttendanceActivity extends AppCompatActivity {


    private static final String TAG = "StudentAttendanceAct";


    private RecyclerView recyclerView;
    private AttendanceAdapter adapter;
    private final ArrayList<StudentModel> studentList = new ArrayList<>();
    private Button viewReportBtn;
    private Spinner sectionSpinner;


    private String term = "Prelim";
    private DatabaseReference studentsRef;
    private SessionManager sessionManager;
    private SectionItem selectedSection;


    // default weights used for summary calculations
    private static final Map<String, Integer> WEIGHTS = new HashMap<>();


    static {
        WEIGHTS.put("Present", 100);
        WEIGHTS.put("Late", 90);
        WEIGHTS.put("Excused", 100);
        WEIGHTS.put("Absent", 0);
    }


    private interface SectionsCallback {
        void onResult(List<SectionItem> items);
    }


    private final List<SectionItem> sectionItems = new ArrayList<>();
    private final List<String> sectionDisplayList = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_attendance);


        // Views
        recyclerView = findViewById(R.id.recyclerView);
        viewReportBtn = findViewById(R.id.viewReportBtn);
        sectionSpinner = findViewById(R.id.sectionSpinner);


        // Session manager
        sessionManager = new SessionManager(this);


        // Firebase refs
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");


        // Adapter + RecyclerView
        adapter = new AttendanceAdapter(this, studentList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setAdapter(adapter);


        // Persist changes immediately when adapter notifies, but show confirmation dialog first.
        adapter.setOnAttendanceChangedListener((student, position, previousStatus) -> {
            if (selectedSection == null) {
                Toast.makeText(StudentAttendanceActivity.this, "Select a section first to save attendance.", Toast.LENGTH_SHORT).show();
                // revert UI to previous
                if (student != null && previousStatus != null) {
                    student.setAttendanceStatus(previousStatus);
                    adapter.notifyItemChanged(position);
                }
                return;
            }
            if (student == null) return;


            final String newStatus = student.getAttendanceStatus();
            final String previous = previousStatus == null ? "" : previousStatus;


            // Confirm with teacher before saving
            String message = "Mark " + student.getFullName() + " as " + newStatus + "?\n\nPrevious: " + (previous.isEmpty() ? "Not marked" : previous);
            new AlertDialog.Builder(this)
                    .setTitle("Confirm attendance")
                    .setMessage(message)
                    .setPositiveButton("Yes", (dialog, which) -> {
                        // Proceed with save
                        // 1) Update Students node attendanceStatus for quick reference (if possible)
                        String dbKey = student.getId();
                        if (!isNullOrEmpty(dbKey)) {
                            studentsRef.child(dbKey).child("attendanceStatus")
                                    .setValue(newStatus, (error, ref) -> {
                                        if (error != null) {
                                            Log.w(TAG, "Failed to update Students/" + dbKey + "/attendanceStatus: " + error.getMessage());
                                        }
                                    });
                        } else {
                            // fallback: update by studentId matches
                            final String studentNumber = student.getStudentId();
                            if (!isNullOrEmpty(studentNumber)) {
                                Query q = studentsRef.orderByChild("studentId").equalTo(studentNumber);
                                q.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        if (!snapshot.exists()) return;
                                        for (DataSnapshot ds : snapshot.getChildren()) {
                                            String foundKey = ds.getKey();
                                            if (foundKey == null) continue;
                                            studentsRef.child(foundKey).child("attendanceStatus")
                                                    .setValue(newStatus);
                                        }
                                    }
                                    @Override public void onCancelled(@NonNull DatabaseError error) { }
                                });
                            }
                        }


                        // 2) Persist the attendance entry and update summary (per-teacher)
                        persistAttendanceChange(selectedSection, student, previous);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        // revert UI to previousStatus
                        student.setAttendanceStatus(previousStatus);
                        adapter.notifyItemChanged(position);
                    })
                    .setCancelable(false)
                    .show();
        });


        // Spinner adapter
        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sectionDisplayList);
        sectionSpinner.setAdapter(spinnerAdapter);


        // Load sections for teacher (use session userId if available)
        String sessionTeacherId = sessionManager.getUserId();
        Log.d(TAG, "Session Teacher ID: " + sessionTeacherId);
        if (!isNullOrEmpty(sessionTeacherId)) {
            loadSectionsForTeacher(sessionTeacherId);
        } else {
            resolveTeacherKeyAndLoadSections();
        }


        // View Report button — pass teacherId so report reads per-teacher nodes
        if (viewReportBtn != null) {
            viewReportBtn.setOnClickListener(v -> {
                if (selectedSection == null) {
                    Toast.makeText(StudentAttendanceActivity.this, "Please select a section first.", Toast.LENGTH_SHORT).show();
                    return;
                }


                // Use actual ID or build fallback key
                String sectionKey = selectedSection.getId();
                if (isNullOrEmpty(sectionKey)) {
                    sectionKey = buildFallbackSectionKey(selectedSection);
                    Log.d(TAG, "Using fallback key for report: " + sectionKey);
                }


                // Determine teacher key to pass (prefer SessionManager, else FirebaseAuth UID)
                String teacherKeyForReport = sessionManager.getUserId();
                if (isNullOrEmpty(teacherKeyForReport)) {
                    FirebaseUser cur = FirebaseAuth.getInstance().getCurrentUser();
                    if (cur != null && !isNullOrEmpty(cur.getUid())) teacherKeyForReport = cur.getUid();
                }


                Log.d(TAG, "ViewReport clicked: sectionKey=" + sectionKey + " display=" + selectedSection.getDisplay() + " teacherKey=" + teacherKeyForReport);


                Intent i = new Intent(StudentAttendanceActivity.this, AttendanceReportActivity.class);
                i.putExtra("sectionId", sectionKey);
                i.putExtra("sectionDisplay", selectedSection.getDisplay());
                if (!isNullOrEmpty(teacherKeyForReport)) i.putExtra("teacherId", teacherKeyForReport);
                startActivity(i);
            });
            viewReportBtn.setEnabled(false); // Will be enabled on section selection
        }


        // Spinner selection -> load students via adapter
        sectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < sectionItems.size()) {
                    selectedSection = sectionItems.get(position);


                    String selDisplay = selectedSection == null ? "null" : selectedSection.getDisplay();
                    String selId = (selectedSection == null ? null : selectedSection.getId());
                    Log.d(TAG, "Spinner selected: " + selDisplay + " id=" + selId);


                    // Enable viewReport button whenever a section is selected (we will pass fallback key if id missing)
                    if (viewReportBtn != null) viewReportBtn.setEnabled(selectedSection != null);


                    adapter.loadStudentsForSection(selectedSection,
                            FirebaseDatabase.getInstance().getReference("Students"),
                            new AttendanceAdapter.OnLoadListener() {
                                @Override
                                public void onLoadStarted() {
                                    Log.d(TAG, "Loading students...");
                                }


                                @Override
                                public void onLoadFinished(int count) {
                                    Log.d(TAG, "Students loaded: " + count);
                                    if (count == 0)
                                        Toast.makeText(StudentAttendanceActivity.this, "No students found for this section.", Toast.LENGTH_SHORT).show();
                                }


                                @Override
                                public void onLoadFailed(String errorMessage) {
                                    Log.w(TAG, "Failed to load students: " + errorMessage);
                                    Toast.makeText(StudentAttendanceActivity.this, "Failed to load students: " + errorMessage, Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    selectedSection = null;
                    if (viewReportBtn != null) viewReportBtn.setEnabled(false);
                    studentList.clear();
                    adapter.setStudents(studentList);
                }
            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedSection = null;
                if (viewReportBtn != null) viewReportBtn.setEnabled(false);
                studentList.clear();
                adapter.setStudents(studentList);
            }
        });
    }


   /* -------------------------
      Attendance persistence (per-teacher)
      ------------------------- */


    private void persistAttendanceChange(SectionItem section, StudentModel student, String previousStatus) {
        if (section == null || student == null) return;


        // Use real sectionId if available; otherwise create fallback key so marking still works.
        String realSectionId = section.getId();
        boolean usingFallback;
        String writeSectionId;
        if (isNullOrEmpty(realSectionId)) {
            writeSectionId = buildFallbackSectionKey(section);
            usingFallback = true;
            Log.w(TAG, "persistAttendanceChange: using fallback section key: " + writeSectionId);
        } else {
            writeSectionId = realSectionId;
            usingFallback = false;
        }


        // teacherId for per-teacher records (use SessionManager teacher key first)
        String teacherId = sessionManager.getUserId();
        if (isNullOrEmpty(teacherId)) {
            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
            if (current != null) teacherId = current.getUid();
            else teacherId = "unknown-teacher";
        }


        // Resolve teacher profile to get full name and assigned subject (if available)
        DatabaseReference teachersRef = FirebaseDatabase.getInstance().getReference("Teachers").child(teacherId);
        String finalTeacherId = teacherId;
        String finalTeacherId1 = teacherId;
        teachersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot teacherSnap) {
                String teacherFullName = "";
                String assignedSubjectName = "";


                if (teacherSnap != null && teacherSnap.exists()) {
                    Object fn = teacherSnap.child("fullName").getValue();
                    if (fn == null) fn = teacherSnap.child("displayName").getValue();
                    if (fn != null) teacherFullName = String.valueOf(fn);


                    // If teacher has assignedSubjects (ids), take the first and resolve its display name
                    if (teacherSnap.hasChild("assignedSubjects")) {
                        for (DataSnapshot as : teacherSnap.child("assignedSubjects").getChildren()) {
                            String subjId = as.getValue(String.class);
                            if (!isNullOrEmpty(subjId)) {
                                // fetch subject name
                                DatabaseReference subjRef = FirebaseDatabase.getInstance().getReference("Subjects").child(subjId);
                                String finalTeacherFullName = teacherFullName;
                                subjRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override public void onDataChange(@NonNull DataSnapshot subjSnap) {
                                        String subjName = subjSnap.child("name").getValue(String.class);
                                        writeAttendanceWithTeacherInfo(writeSectionId, finalTeacherId, finalTeacherFullName, subjName != null ? subjName : "", section, student, previousStatus, usingFallback);
                                    }
                                    @Override public void onCancelled(@NonNull DatabaseError error) {
                                        // subject lookup failed; still write attendance without subject
                                        writeAttendanceWithTeacherInfo(writeSectionId, finalTeacherId, finalTeacherFullName, assignedSubjectName, section, student, previousStatus, usingFallback);
                                    }
                                });
                                return; // only handle first assignedSubject
                            }
                        }
                    }
                }


                // fallback: use FirebaseAuth displayName if teacher node lacked fullName or assignedSubject
                if (isNullOrEmpty(teacherFullName)) {
                    FirebaseUser cu = FirebaseAuth.getInstance().getCurrentUser();
                    if (cu != null && cu.getDisplayName() != null) teacherFullName = cu.getDisplayName();
                }


                // write attendance immediately if no assignedSubject to resolve
                writeAttendanceWithTeacherInfo(writeSectionId, finalTeacherId1, teacherFullName, assignedSubjectName, section, student, previousStatus, usingFallback);
            }


            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to read teacher profile: " + error.getMessage());
                // fallback to auth displayName
                String fallbackName = "";
                FirebaseUser cu = FirebaseAuth.getInstance().getCurrentUser();
                if (cu != null && cu.getDisplayName() != null) fallbackName = cu.getDisplayName();
                writeAttendanceWithTeacherInfo(writeSectionId, finalTeacherId1, fallbackName, "", section, student, previousStatus, usingFallback);
            }
        });
    }


    /**
     * Centralized write that uses provided teacherFullName and assignedSubjectName.
     * Writes to Attendance per-teacher node and then writes Students/{...}/attendanceHistory,
     * Teachers/{teacherId}/attendanceHistory (teacher preview) and notification copies.
     */
    private void writeAttendanceWithTeacherInfo(String writeSectionId,
                                                String teacherId,
                                                String teacherFullName,
                                                String assignedSubjectName,
                                                SectionItem section,
                                                StudentModel student,
                                                String previousStatus,
                                                boolean usingFallback) {
        final String sid = !isNullOrEmpty(student.getStudentId()) ? student.getStudentId() : student.getId();
        if (isNullOrEmpty(sid)) {
            Log.w(TAG, "writeAttendanceWithTeacherInfo: no student id");
            return;
        }


        final String newStatus = !isNullOrEmpty(student.getAttendanceStatus()) ? student.getAttendanceStatus() : "Absent";
        final String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());


        DatabaseReference attendanceNode = FirebaseDatabase.getInstance()
                .getReference("Attendance")
                .child(writeSectionId)
                .child(teacherId)
                .child(date)
                .child(sid);


        Map<String, Object> data = new HashMap<>();
        data.put("studentId", sid);
        data.put("studentName", student.getFullName());
        data.put("status", newStatus);
        data.put("term", term);
        data.put("sectionId", section.getId());
        if (usingFallback) data.put("sectionFallbackKey", writeSectionId);
        data.put("section", section.getDisplay());
        data.put("courseName", section.getCourseName());
        data.put("specializationName", section.getSpecializationName());
        data.put("yearName", section.getYearName());
        data.put("date", date);
        data.put("teacherId", teacherId);
        data.put("teacherFullName", teacherFullName);           // write teacher full name
        if (!isNullOrEmpty(assignedSubjectName)) data.put("assignedSubject", assignedSubjectName); // assigned subject
        data.put("timestamp", ServerValue.TIMESTAMP);


        String finalWriteSectionId = writeSectionId;
        String finalTeacherId = teacherId;


        attendanceNode.setValue(data, (error, ref) -> {
            if (error != null) {
                Log.w(TAG, "Failed to write attendance for " + sid + ": " + error.getMessage());
                Toast.makeText(StudentAttendanceActivity.this, "Failed to save attendance for " + student.getFullName(), Toast.LENGTH_SHORT).show();
                return;
            }
            Log.d(TAG, "Attendance written for " + sid + " status=" + newStatus + " under sectionNode=" + finalWriteSectionId + " teacher=" + finalTeacherId);
            updateSummaryTransaction(finalWriteSectionId, finalTeacherId, sid, previousStatus, newStatus);
            Toast.makeText(StudentAttendanceActivity.this, "Saved: " + student.getFullName() + " → " + newStatus, Toast.LENGTH_SHORT).show();


            // Student-facing copy (includes teacherFullName and assignedSubject)
            Map<String, Object> studentCopy = new HashMap<>();
            studentCopy.put("status", newStatus);
            studentCopy.put("date", date);
            studentCopy.put("sectionDisplay", section.getDisplay());
            studentCopy.put("sectionId", section.getId());
            if (usingFallback) studentCopy.put("sectionFallbackKey", finalWriteSectionId);
            studentCopy.put("teacherId", finalTeacherId);
            studentCopy.put("teacherFullName", teacherFullName);
            if (!isNullOrEmpty(assignedSubjectName)) studentCopy.put("assignedSubject", assignedSubjectName);
            studentCopy.put("timestamp", ServerValue.TIMESTAMP);


            // Teacher-facing preview copy (so teacher can later "compute all" and view saved previews)
            Map<String, Object> teacherCopy = new HashMap<>();
            teacherCopy.put("studentId", sid);
            teacherCopy.put("studentName", student.getFullName());
            teacherCopy.put("status", newStatus);
            teacherCopy.put("date", date);
            teacherCopy.put("sectionDisplay", section.getDisplay());
            teacherCopy.put("sectionId", section.getId());
            if (usingFallback) teacherCopy.put("sectionFallbackKey", finalWriteSectionId);
            teacherCopy.put("assignedSubject", assignedSubjectName != null ? assignedSubjectName : "");
            teacherCopy.put("teacherFullName", teacherFullName);
            teacherCopy.put("timestamp", ServerValue.TIMESTAMP);


            // Try direct path Students/{sid} for student copy
            DatabaseReference possibleStudentNode = studentsRef.child(sid);
            possibleStudentNode.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    if (snap != null && snap.exists()) {
                        possibleStudentNode.child("attendanceHistory").child(date).setValue(studentCopy, (err1, r1) -> {
                            if (err1 != null) Log.w(TAG, "Failed write Students/" + sid + "/attendanceHistory/" + date + " : " + err1.getMessage());
                        });
                        possibleStudentNode.child("attendanceNotifications").child("latest").setValue(studentCopy, (err2, r2) -> {
                            if (err2 != null) Log.w(TAG, "Failed write Students/" + sid + "/attendanceNotifications/latest : " + err2.getMessage());
                        });
                    } else {
                        // fallback: search by studentId property
                        Query q = studentsRef.orderByChild("studentId").equalTo(sid);
                        q.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot == null || !snapshot.exists()) {
                                    Log.w(TAG, "Could not find student node for sid=" + sid + " to write history.");
                                    return;
                                }
                                for (DataSnapshot ds : snapshot.getChildren()) {
                                    DatabaseReference studentNode = ds.getRef();
                                    studentNode.child("attendanceHistory").child(date).setValue(studentCopy, (err1, r1) -> {
                                        if (err1 != null) Log.w(TAG, "Failed write Students/" + ds.getKey() + "/attendanceHistory/" + date + " : " + err1.getMessage());
                                    });
                                    studentNode.child("attendanceNotifications").child("latest").setValue(studentCopy, (err2, r2) -> {
                                        if (err2 != null) Log.w(TAG, "Failed write Students/" + ds.getKey() + "/attendanceNotifications/latest : " + err2.getMessage());
                                    });
                                }
                            }
                            @Override public void onCancelled(@NonNull DatabaseError error) {
                                Log.w(TAG, "Failed lookup student by studentId=" + sid + " : " + error.getMessage());
                            }
                        });
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Failed checking Students/" + sid + " existence: " + error.getMessage());
                }
            });


            // Write teacher-facing preview copy under Teachers/{teacherId}/attendanceHistory/{date}/{studentId}
            DatabaseReference teacherHistoryRef = FirebaseDatabase.getInstance()
                    .getReference("Teachers")
                    .child(finalTeacherId)
                    .child("attendanceHistory")
                    .child(date)
                    .child(sid);


            teacherHistoryRef.setValue(teacherCopy, (errT, rT) -> {
                if (errT != null) {
                    Log.w(TAG, "Failed to write teacher preview Teachers/" + finalTeacherId + "/attendanceHistory/" + date + "/" + sid + " : " + errT.getMessage());
                } else {
                    Log.d(TAG, "Teacher preview saved for " + sid + " under Teachers/" + finalTeacherId + "/attendanceHistory/" + date);
                }
            });
        });
    }


    private String buildFallbackSectionKey(SectionItem s) {
        String parts = (safe(s.getCourseName()) + "-" + safe(s.getSpecializationName()) + "-" + safe(s.getYearName()) + "-" + safe(s.getSectionName()));
        String normalized = parts.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9\\-]", "");
        if (normalized.isEmpty()) normalized = "unknown-section";
        return "fallback:" + normalized;
    }


    // helper
    private boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }


    private String safe(String v) {
        return v == null ? "" : v;
    }


    /**
     * Update the per-teacher summary under AttendanceSummary/{sectionId}/{teacherId}/{studentId}
     * Uses a transaction to increment/decrement the counts and recompute weighted score & percentage.
     */
    private void updateSummaryTransaction(String sectionId, String teacherId, String studentId, String previousStatus, String newStatus) {
        DatabaseReference summaryRef = FirebaseDatabase.getInstance()
                .getReference("AttendanceSummary")
                .child(sectionId)
                .child(teacherId)
                .child(studentId);


        summaryRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Map<String, Object> summary = (currentData.getValue() instanceof Map) ? (Map<String, Object>) currentData.getValue() : new HashMap<>();
                Map<String, Object> counts = (summary.get("counts") instanceof Map) ? (Map<String, Object>) summary.get("counts") : new HashMap<>();


                int present = toInt(counts.get("Present"));
                int late = toInt(counts.get("Late"));
                int excused = toInt(counts.get("Excused"));
                int absent = toInt(counts.get("Absent"));
                int totalDays = toInt(summary.get("totalDays"));


                if ((previousStatus == null || previousStatus.isEmpty()) && (newStatus != null && !newStatus.isEmpty())) {
                    totalDays++;
                    incrementByStatus(newStatus, counts);
                } else if ((previousStatus != null && !previousStatus.isEmpty()) && (newStatus == null || newStatus.isEmpty())) {
                    totalDays = Math.max(0, totalDays - 1);
                    decrementByStatus(previousStatus, counts);
                } else if (previousStatus != null && newStatus != null && !previousStatus.equals(newStatus)) {
                    decrementByStatus(previousStatus, counts);
                    incrementByStatus(newStatus, counts);
                }


                present = toInt(counts.get("Present"));
                late = toInt(counts.get("Late"));
                excused = toInt(counts.get("Excused"));
                absent = toInt(counts.get("Absent"));


                long weightedScore = (long) present * WEIGHTS.get("Present")
                        + (long) late * WEIGHTS.get("Late")
                        + (long) excused * WEIGHTS.get("Excused")
                        + (long) absent * WEIGHTS.get("Absent");


                int attendancePercentage = 0;
                if (totalDays > 0) {
                    // weightedScore range is 0..totalDays*100
                    double pct = ((double) weightedScore) / ((double) totalDays * 100.0) * 100.0;
                    attendancePercentage = (int) Math.round(pct);
                    attendancePercentage = Math.max(0, Math.min(100, attendancePercentage));
                }


                Map<String, Object> newSummary = new HashMap<>();
                newSummary.put("totalDays", totalDays);


                Map<String, Object> countsOut = new HashMap<>();
                countsOut.put("Present", present);
                countsOut.put("Late", late);
                countsOut.put("Excused", excused);
                countsOut.put("Absent", absent);
                newSummary.put("counts", countsOut);


                newSummary.put("weightedScore", weightedScore);
                newSummary.put("attendancePercentage", attendancePercentage);
                newSummary.put("lastUpdated", ServerValue.TIMESTAMP);
                // keep studentName if present in currentData
                if (summary.containsKey("studentName")) newSummary.put("studentName", summary.get("studentName"));


                currentData.setValue(newSummary);
                return Transaction.success(currentData);
            }


            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) Log.w(TAG, "Summary transaction failed: " + error.getMessage());
                else Log.d(TAG, "Summary updated for " + sectionId + "/" + teacherId + "/" + studentId + " committed=" + committed);
            }
        });
    }


    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception e) {
            return 0;
        }
    }


    private static void incrementByStatus(String status, Map<String, Object> counts) {
        if (status == null) return;
        int cur = toInt(counts.get(status));
        counts.put(status, cur + 1);
    }


    private static void decrementByStatus(String status, Map<String, Object> counts) {
        if (status == null) return;
        int cur = toInt(counts.get(status));
        counts.put(status, Math.max(0, cur - 1));
    }


   /* -------------------------
      Section loading & spinner population (unchanged)
      ------------------------- */


    private void resolveTeacherKeyAndLoadSections() {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }


        final String authUid = current.getUid();
        final String authEmail = current.getEmail();
        final String authDisplay = current.getDisplayName();


        DatabaseReference teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");


        // Try a few strategies to find the teacher node key
        Query byUid = teachersRef.orderByChild("uid").equalTo(authUid);
        byUid.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot ds : snapshot.getChildren()) { loadSectionsForTeacher(ds.getKey()); return; }
                }
                teachersRef.child(authUid).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot directSnap) {
                        if (directSnap.exists()) { loadSectionsForTeacher(directSnap.getKey()); return; }
                        if (!isNullOrEmpty(authEmail)) {
                            Query byEmail = teachersRef.orderByChild("email").equalTo(authEmail);
                            byEmail.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override public void onDataChange(@NonNull DataSnapshot emailSnap) {
                                    if (emailSnap.exists()) {
                                        for (DataSnapshot ds2 : emailSnap.getChildren()) { loadSectionsForTeacher(ds2.getKey()); return; }
                                    }
                                    tryLookupByNameFallback();
                                }
                                @Override public void onCancelled(@NonNull DatabaseError error) { tryLookupByNameFallback(); }
                                private void tryLookupByNameFallback() {
                                    if (!isNullOrEmpty(authDisplay)) {
                                        Query byFullName = teachersRef.orderByChild("fullName").equalTo(authDisplay);
                                        byFullName.addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override public void onDataChange(@NonNull DataSnapshot nameSnap) {
                                                if (nameSnap.exists()) {
                                                    for (DataSnapshot ds3 : nameSnap.getChildren()) { loadSectionsForTeacher(ds3.getKey()); return; }
                                                }
                                                Query byDisp = teachersRef.orderByChild("displayName").equalTo(authDisplay);
                                                byDisp.addListenerForSingleValueEvent(new ValueEventListener() {
                                                    @Override public void onDataChange(@NonNull DataSnapshot dispSnap) {
                                                        if (dispSnap.exists()) {
                                                            for (DataSnapshot ds4 : dispSnap.getChildren()) { loadSectionsForTeacher(ds4.getKey()); return; }
                                                        }
                                                        loadSectionsByTeacherIdFallback(authUid);
                                                    }
                                                    @Override public void onCancelled(@NonNull DatabaseError error) { loadSectionsByTeacherIdFallback(authUid); }
                                                });
                                            }
                                            @Override public void onCancelled(@NonNull DatabaseError error) { loadSectionsByTeacherIdFallback(authUid); }
                                        });
                                    } else { loadSectionsByTeacherIdFallback(authUid); }
                                }
                            });
                        } else {
                            if (!isNullOrEmpty(authDisplay)) {
                                Query byFullName = teachersRef.orderByChild("fullName").equalTo(authDisplay);
                                byFullName.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override public void onDataChange(@NonNull DataSnapshot nameSnap) {
                                        if (nameSnap.exists()) {
                                            for (DataSnapshot ds3 : nameSnap.getChildren()) { loadSectionsForTeacher(ds3.getKey()); return; }
                                        }
                                        Query byDisp = teachersRef.orderByChild("displayName").equalTo(authDisplay);
                                        byDisp.addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override public void onDataChange(@NonNull DataSnapshot dispSnap) {
                                                if (dispSnap.exists()) {
                                                    for (DataSnapshot ds4 : dispSnap.getChildren()) { loadSectionsForTeacher(ds4.getKey()); return; }
                                                }
                                                loadSectionsByTeacherIdFallback(authUid);
                                            }
                                            @Override public void onCancelled(@NonNull DatabaseError error) { loadSectionsByTeacherIdFallback(authUid); }
                                        });
                                    }
                                    @Override public void onCancelled(@NonNull DatabaseError error) { loadSectionsByTeacherIdFallback(authUid); }
                                });
                            } else { loadSectionsByTeacherIdFallback(authUid); }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) { loadSectionsByTeacherIdFallback(authUid); }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { loadSectionsByTeacherIdFallback(authUid); }
        });
    }


    private void loadSectionsForTeacher(String teacherKey) {
        DatabaseReference teachersRef = FirebaseDatabase.getInstance().getReference("Teachers").child(teacherKey);
        teachersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot teacherSnap) {
                if (!teacherSnap.exists()) { loadSectionsByTeacherIdFallback(teacherKey); return; }


                // 1) assignedSections (section IDs)
                List<String> assignedSectionIds = new ArrayList<>();
                if (teacherSnap.hasChild("assignedSections")) {
                    for (DataSnapshot ds : teacherSnap.child("assignedSections").getChildren()) {
                        String secId = ds.getValue(String.class);
                        if (!isNullOrEmpty(secId)) assignedSectionIds.add(secId);
                    }
                }
                if (!assignedSectionIds.isEmpty()) { loadSectionsByIds(assignedSectionIds); return; }


                // 2) assignedSubjects
                List<String> assignedSubjectIds = new ArrayList<>();
                if (teacherSnap.hasChild("assignedSubjects")) {
                    for (DataSnapshot ds : teacherSnap.child("assignedSubjects").getChildren()) {
                        String subjId = ds.getValue(String.class);
                        if (!isNullOrEmpty(subjId)) assignedSubjectIds.add(subjId);
                    }
                }


                // 3) courseIds
                List<String> courseIds = new ArrayList<>();
                if (teacherSnap.hasChild("courseIds")) {
                    for (DataSnapshot ds : teacherSnap.child("courseIds").getChildren()) {
                        String cid = ds.getValue(String.class);
                        if (!isNullOrEmpty(cid)) courseIds.add(cid);
                    }
                }


                // 4) courseDisplays
                List<String> courseDisplays = new ArrayList<>();
                if (teacherSnap.hasChild("courseDisplays")) {
                    for (DataSnapshot ds : teacherSnap.child("courseDisplays").getChildren()) {
                        String v = ds.getValue(String.class);
                        if (v != null) courseDisplays.add(v);
                    }
                }


                // Decision tree similar to your original logic
                if (!assignedSubjectIds.isEmpty()) {
                    loadSectionsBySubjectIds(assignedSubjectIds, foundBySubject -> {
                        if (foundBySubject != null && !foundBySubject.isEmpty())
                            applySectionsToSpinner(foundBySubject);
                        else if (!courseIds.isEmpty()) {
                            loadSectionsByCourseIds(courseIds, foundByCourseIds -> {
                                if (foundByCourseIds != null && !foundByCourseIds.isEmpty())
                                    applySectionsToSpinner(foundByCourseIds);
                                else if (!courseDisplays.isEmpty()) {
                                    applySectionsFromDisplays(courseDisplays);
                                } else loadSectionsByTeacherIdFallback(teacherKey);
                            });
                        } else if (!courseDisplays.isEmpty()) {
                            applySectionsFromDisplays(courseDisplays);
                        } else loadSectionsByTeacherIdFallback(teacherKey);
                    });
                    return;
                }


                if (!courseIds.isEmpty()) {
                    loadSectionsByCourseIds(courseIds, foundByCourseIds -> {
                        if (foundByCourseIds != null && !foundByCourseIds.isEmpty())
                            applySectionsToSpinner(foundByCourseIds);
                        else if (!courseDisplays.isEmpty()) applySectionsFromDisplays(courseDisplays);
                        else loadSectionsByTeacherIdFallback(teacherKey);
                    });
                    return;
                }


                if (!courseDisplays.isEmpty()) {
                    applySectionsFromDisplays(courseDisplays);
                    return;
                }


                loadSectionsByTeacherIdFallback(teacherKey);
            }


            @Override public void onCancelled(@NonNull DatabaseError error) { loadSectionsByTeacherIdFallback(teacherKey); }
        });
    }


    private void applySectionsFromDisplays(List<String> courseDisplays) {
        List<SectionItem> items = new ArrayList<>();
        for (String display : courseDisplays) {
            String[] parts = display.split(" - ");
            String courseName = parts.length > 0 ? parts[0] : null;
            String specialization = parts.length > 1 ? parts[1] : null;
            String year = parts.length > 2 ? parts[2] : null;
            String sectionName = parts.length > 3 ? parts[3] : null;
            items.add(new SectionItem(null, courseName, specialization, year, sectionName));
        }
        applySectionsToSpinner(items);
    }


    private void loadSectionsByIds(List<String> sectionIds) {
        DatabaseReference sectionsRef = FirebaseDatabase.getInstance().getReference("Sections");
        Map<String, SectionItem> found = new LinkedHashMap<>();
        final int[] remaining = {sectionIds.size()};


        if (sectionIds.isEmpty()) {
            applySectionsToSpinner(new ArrayList<>());
            return;
        }


        for (String sid : sectionIds) {
            if (sid == null) {
                remaining[0]--;
                continue;
            }


            sectionsRef.child(sid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot secSnap) {


                    if (secSnap.exists()) {
                        String courseName = secSnap.child("courseName").getValue(String.class);
                        String specializationName = secSnap.child("specializationName").getValue(String.class);
                        String yearName = secSnap.child("yearName").getValue(String.class);
                        String sectionName = secSnap.child("sectionName").getValue(String.class);


                        SectionItem item = new SectionItem(
                                secSnap.getKey(),
                                courseName,
                                specializationName,
                                yearName,
                                sectionName
                        );


                        found.put(sid, item);
                    }


                    remaining[0]--;
                    if (remaining[0] == 0) {
                        applySectionsToSpinner(new ArrayList<>(found.values()));
                    }
                }


                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "loadSectionsByIds cancelled: " + error.getMessage());
                    remaining[0]--;
                    if (remaining[0] == 0) {
                        applySectionsToSpinner(new ArrayList<>(found.values()));
                    }
                }
            });
        }
    }


    private void loadSectionsBySubjectIds(List<String> subjectIds, SectionsCallback callback) {
        DatabaseReference sectionsRef = FirebaseDatabase.getInstance().getReference("Sections");
        Map<String, SectionItem> found = new LinkedHashMap<>();
        final int[] remaining = {subjectIds.size()};
        if (subjectIds.isEmpty()) { callback.onResult(new ArrayList<>()); return; }


        for (String sid : subjectIds) {
            if (sid == null) { remaining[0]--; continue; }
            sectionsRef.orderByChild("subjectId").equalTo(sid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String key = ds.getKey();
                                String courseName = ds.child("courseName").getValue(String.class);
                                String specializationName = ds.child("specializationName").getValue(String.class);
                                String yearName = ds.child("yearName").getValue(String.class);
                                String sectionName = ds.child("sectionName").getValue(String.class);
                                SectionItem si = new SectionItem(key, courseName, specializationName, yearName, sectionName);
                                try { si.setId(key); } catch (Exception ignore) {}
                                found.put(key, si);
                            }
                            remaining[0]--;
                            if (remaining[0] <= 0) callback.onResult(new ArrayList<>(found.values()));
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {
                            remaining[0]--;
                            if (remaining[0] <= 0) callback.onResult(new ArrayList<>(found.values()));
                        }
                    });
        }
    }


    private void loadSectionsByCourseIds(List<String> courseIds, SectionsCallback callback) {
        DatabaseReference sectionsRef = FirebaseDatabase.getInstance().getReference("Sections");
        Map<String, SectionItem> found = new LinkedHashMap<>();
        final int[] remaining = {courseIds.size()};
        if (courseIds.isEmpty()) { callback.onResult(new ArrayList<>()); return; }


        for (String cid : courseIds) {
            if (cid == null) { remaining[0]--; continue; }
            sectionsRef.orderByChild("courseId").equalTo(cid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String key = ds.getKey();
                                String courseName = ds.child("courseName").getValue(String.class);
                                String specializationName = ds.child("specializationName").getValue(String.class);
                                String yearName = ds.child("yearName").getValue(String.class);
                                String sectionName = ds.child("sectionName").getValue(String.class);
                                SectionItem si = new SectionItem(key, courseName, specializationName, yearName, sectionName);
                                try { si.setId(key); } catch (Exception ignore) {}
                                found.put(key, si);
                            }
                            remaining[0]--;
                            if (remaining[0] <= 0) callback.onResult(new ArrayList<>(found.values()));
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {
                            remaining[0]--;
                            if (remaining[0] <= 0) callback.onResult(new ArrayList<>(found.values()));
                        }
                    });
        }
    }


    private void loadSectionsByTeacherIdFallback(String teacherId) {
        DatabaseReference sectionsRef = FirebaseDatabase.getInstance().getReference("Sections");
        sectionsRef.orderByChild("teacherId").equalTo(teacherId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<SectionItem> result = new ArrayList<>();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String key = ds.getKey();
                            String courseName = ds.child("courseName").getValue(String.class);
                            String specializationName = ds.child("specializationName").getValue(String.class);
                            String yearName = ds.child("yearName").getValue(String.class);
                            String sectionName = ds.child("sectionName").getValue(String.class);
                            SectionItem si = new SectionItem(key, courseName, specializationName, yearName, sectionName);
                            try { si.setId(key); } catch (Exception ignore) {}
                            result.add(si);
                        }
                        applySectionsToSpinner(result);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) { applySectionsToSpinner(new ArrayList<>()); }
                });
    }


    private void applySectionsToSpinner(List<SectionItem> items) {
        sectionItems.clear();
        sectionItems.addAll(items);


        sectionDisplayList.clear();
        for (SectionItem s : sectionItems) sectionDisplayList.add(s.getDisplay());


        if (!sectionDisplayList.isEmpty()) {
            Map<String, SectionItem> displayToItem = new HashMap<>();
            for (SectionItem s : sectionItems) displayToItem.put(s.getDisplay(), s);


            List<String> sortedDisplays = new ArrayList<>(sectionDisplayList);
            Collections.sort(sortedDisplays);


            sectionItems.clear();
            sectionDisplayList.clear();
            for (String d : sortedDisplays) {
                SectionItem si = displayToItem.get(d);
                if (si != null) {
                    sectionItems.add(si);
                    sectionDisplayList.add(d);
                }
            }
        }


        runOnUiThread(() -> {
            spinnerAdapter.notifyDataSetChanged();
            if (!sectionDisplayList.isEmpty()) {
                sectionSpinner.setSelection(0);
                // enable viewReport as soon as we set a selection; fallback key will be used if id missing
                if (viewReportBtn != null) viewReportBtn.setEnabled(true);
                resolveMissingSectionIds();
            } else {
                studentList.clear();
                adapter.setStudents(studentList);
                if (viewReportBtn != null) viewReportBtn.setEnabled(false);
                Toast.makeText(StudentAttendanceActivity.this, "No sections assigned to you.", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void resolveMissingSectionIds() {
        boolean needResolve = false;
        for (SectionItem s : sectionItems) {
            if (isNullOrEmpty(s.getId())) {
                needResolve = true;
                break;
            }
        }
        if (!needResolve) return;


        DatabaseReference sectionsRef = FirebaseDatabase.getInstance().getReference("Sections");
        for (int idx = 0; idx < sectionItems.size(); idx++) {
            SectionItem item = sectionItems.get(idx);
            if (!isNullOrEmpty(item.getId())) continue;
            final int position = idx;
            final String wantSectionName = item.getSectionName();
            if (isNullOrEmpty(wantSectionName)) continue;


            sectionsRef.orderByChild("sectionName").equalTo(wantSectionName)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snap) {
                            for (DataSnapshot ds : snap.getChildren()) {
                                String courseName = ds.child("courseName").getValue(String.class);
                                String spec = ds.child("specializationName").getValue(String.class);
                                String year = ds.child("yearName").getValue(String.class);


                                boolean matchCourse = isNullOrEmpty(item.getCourseName()) || item.getCourseName().trim().equalsIgnoreCase(safe(courseName));
                                boolean matchSpec = isNullOrEmpty(item.getSpecializationName()) || item.getSpecializationName().trim().equalsIgnoreCase(safe(spec));
                                boolean matchYear = isNullOrEmpty(item.getYearName()) || item.getYearName().trim().equalsIgnoreCase(safe(year));


                                if (matchCourse && matchSpec && matchYear) {
                                    try { item.setId(ds.getKey()); } catch (Exception ignore) {}
                                    Log.d(TAG, "Resolved section id for display=" + item.getDisplay() + " -> id=" + ds.getKey());
                                    runOnUiThread(() -> {
                                        spinnerAdapter.notifyDataSetChanged();
                                        int sel = sectionSpinner.getSelectedItemPosition();
                                        if (sel == position) {
                                            selectedSection = sectionItems.get(sel);
                                            if (viewReportBtn != null)
                                                viewReportBtn.setEnabled(true);
                                        }
                                    });
                                    break;
                                }
                            }
                        }


                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.w(TAG, "Failed resolving section ids: " + error.getMessage());
                        }
                    });
        }
    }
}

