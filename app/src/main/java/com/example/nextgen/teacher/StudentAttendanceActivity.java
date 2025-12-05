package com.example.nextgen.teacher;

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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
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
 * NOTE: Minor change — enable "View Report" as soon as a section is selected (even if section.id
 * isn't resolved yet). When clicked we pass a fallback key if the real id is missing.
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

        // Persist changes immediately when adapter notifies.
        adapter.setOnAttendanceChangedListener((student, position, previousStatus) -> {
            if (selectedSection == null) {
                Toast.makeText(StudentAttendanceActivity.this, "Select a section first to save attendance.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (student == null) return;

            final String newStatus = student.getAttendanceStatus();

            // 1) Update Students node attendanceStatus for quick reference (if possible)
            String dbKey = student.getId(); // adapter sets this when loading students (ds.getKey())
            if (!isNullOrEmpty(dbKey)) {
                studentsRef.child(dbKey).child("attendanceStatus")
                        .setValue(newStatus, (error, ref) -> {
                            if (error != null) {
                                Log.w(TAG, "Failed to update Students/" + dbKey + "/attendanceStatus: " + error.getMessage());
                                // don't block further operations
                            } else {
                                Log.d(TAG, "Updated Students/" + dbKey + "/attendanceStatus -> " + newStatus);
                            }
                        });
            } else {
                // if dbKey not available, try to resolve by studentId and update all matches
                final String studentNumber = student.getStudentId();
                if (!isNullOrEmpty(studentNumber)) {
                    Query q = studentsRef.orderByChild("studentId").equalTo(studentNumber);
                    q.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (!snapshot.exists()) {
                                Log.w(TAG, "No Students node found for studentId=" + studentNumber);
                                return;
                            }
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String foundKey = ds.getKey();
                                if (foundKey == null) continue;
                                studentsRef.child(foundKey).child("attendanceStatus")
                                        .setValue(newStatus, (error, ref) -> {
                                            if (error != null) {
                                                Log.w(TAG, "Failed to update Students/" + foundKey + "/attendanceStatus: " + error.getMessage());
                                            } else {
                                                Log.d(TAG, "Updated Students/" + foundKey + "/attendanceStatus -> " + newStatus);
                                            }
                                        });
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.w(TAG, "Student lookup cancelled: " + error.getMessage());
                        }
                    });
                } else {
                    Log.w(TAG, "Cannot update Students node: both dbKey and studentId are missing.");
                }
            }

            // 2) Persist the attendance entry under Attendance/<section>/<date>/<studentKey> and update summary
            persistAttendanceChange(selectedSection, student, previousStatus);
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

        // View Report button
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

                Log.d(TAG, "ViewReport clicked: sectionKey=" + sectionKey + " display=" + selectedSection.getDisplay());

                Intent i = new Intent(StudentAttendanceActivity.this, AttendanceReportActivity.class);
                i.putExtra("sectionId", sectionKey);
                i.putExtra("sectionDisplay", selectedSection.getDisplay());
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
       Attendance persistence
       ------------------------- */

    private void persistAttendanceChange(SectionItem section, StudentModel student, String previousStatus) {
        if (section == null || student == null) return;

        // Use real sectionId if available; otherwise create fallback key so marking still works.
        String realSectionId = section.getId();
        boolean usingFallback = false;
        String writeSectionId = realSectionId;
        if (isNullOrEmpty(writeSectionId)) {
            writeSectionId = buildFallbackSectionKey(section);
            usingFallback = true;
            Log.w(TAG, "persistAttendanceChange: using fallback section key: " + writeSectionId);
        }

        final String sid = !isNullOrEmpty(student.getStudentId()) ? student.getStudentId() : student.getId();
        if (isNullOrEmpty(sid)) {
            Log.w(TAG, "persistAttendanceChange: no student id");
            return;
        }

        final String newStatus = !isNullOrEmpty(student.getAttendanceStatus()) ? student.getAttendanceStatus() : "Absent";
        final String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        DatabaseReference attendanceNode = FirebaseDatabase.getInstance()
                .getReference("Attendance").child(writeSectionId).child(date).child(sid);

        Map<String, Object> data = new HashMap<>();
        data.put("studentId", sid);
        data.put("studentName", student.getFullName());
        data.put("status", newStatus);
        data.put("term", term);
        data.put("sectionId", realSectionId); // original id (may be null)
        if (usingFallback) data.put("sectionFallbackKey", writeSectionId);
        data.put("section", section.getDisplay());
        data.put("courseName", section.getCourseName());
        data.put("specializationName", section.getSpecializationName());
        data.put("yearName", section.getYearName());
        data.put("date", date);

        String finalWriteSectionId = writeSectionId;
        attendanceNode.setValue(data, (error, ref) -> {
            if (error != null) {
                Log.w(TAG, "Failed to write attendance for " + sid + ": " + error.getMessage());
                Toast.makeText(StudentAttendanceActivity.this, "Failed to save attendance for " + student.getFullName(), Toast.LENGTH_SHORT).show();
                return;
            }
            Log.d(TAG, "Attendance written for " + sid + " status=" + newStatus + " under sectionNode=" + finalWriteSectionId);
            updateSummaryTransaction(finalWriteSectionId, sid, previousStatus, newStatus);
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

    private void updateSummaryTransaction(String sectionId, String studentId, String previousStatus, String newStatus) {
        DatabaseReference summaryRef = FirebaseDatabase.getInstance()
                .getReference("AttendanceSummary").child(sectionId).child(studentId);

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
                    attendancePercentage = (int) Math.round(((double) weightedScore) / (totalDays * 100.0) * 100.0);
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

                currentData.setValue(newSummary);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) Log.w(TAG, "Summary transaction failed: " + error.getMessage());
                else Log.d(TAG, "Summary updated for " + sectionId + "/" + studentId + " committed=" + committed);
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
       Section loading & spinner population
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