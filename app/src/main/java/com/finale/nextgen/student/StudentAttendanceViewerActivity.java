package com.finale.nextgen.student;


import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.finale.nextgen.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class StudentAttendanceViewerActivity extends AppCompatActivity {


    private static final String TAG = "StuAttViewer";


    private CalendarView calendarView;
    private TextView tvDateStatus;
    private Button btnViewHistory;
    private Button btnRefresh;
    private RecyclerView rvHistory;


    private DatabaseReference studentsRef;
    private DatabaseReference attendanceRootRef;


    private String authNodeKey;    // Firebase Auth UID
    private String schoolId;       // school id like "STD-0003" (studentId field)
    private String studentFullName;


    private final SimpleDateFormat dateKeyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());


    // in-memory cache of attendance entries for this student keyed by date -> list
    private final Map<String, List<HistoryEntry>> attendanceByDate = new HashMap<>();


    // attendance root listener
    private ValueEventListener attendanceListener;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_attendance_viewer);


        calendarView = findViewById(R.id.calendarView);
        tvDateStatus = findViewById(R.id.tvDateStatus);
        btnViewHistory = findViewById(R.id.btnViewHistory);
        btnRefresh = findViewById(R.id.btnRefresh);
        rvHistory = findViewById(R.id.rvHistory);


        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        attendanceRootRef = FirebaseDatabase.getInstance().getReference("Attendance");


        // Get Firebase Auth UID
        FirebaseUser cur = FirebaseAuth.getInstance().getCurrentUser();
        if (cur != null) {
            authNodeKey = cur.getUid();
        }


        if (authNodeKey == null || authNodeKey.trim().isEmpty()) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_LONG).show();
            finish();
            return;
        }


        // Calendar -> load preview for selected date
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            String dateKey = formatDate(year, month, dayOfMonth);
            loadStatusForDate(dateKey);
        });


        // initial load today (but first fetch profile)
        String todayKey = dateKeyFmt.format(new Date());


        // load student profile then attach attendance listener
        fetchStudentProfile(() -> {
            // Only proceed if we have a school ID
            if (schoolId != null && !schoolId.isEmpty()) {
                attachAttendanceRealtimeListener();
                // initial UI load
                loadStatusForDate(todayKey);
                if (rvHistory != null) {
                    rvHistory.setLayoutManager(new LinearLayoutManager(this));
                    loadRecentHistoryIntoRv(60);
                }
            } else {
                // Show message that school ID is needed
                runOnUiThread(() -> {
                    tvDateStatus.setText("Please set your School ID in your profile to view attendance.");
                    Toast.makeText(this, "School ID is required to view attendance", Toast.LENGTH_LONG).show();
                });
            }
        });


        btnViewHistory.setOnClickListener(v -> showHistoryDialog());


        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                String tk = dateKeyFmt.format(new Date());
                loadStatusForDate(tk);
                loadRecentHistoryIntoRv(60);
                Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show();
            });
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // detach attendance listener
        try {
            if (attendanceRootRef != null && attendanceListener != null) attendanceRootRef.removeEventListener(attendanceListener);
        } catch (Exception ignored) { }
    }


    /**
     * FIXED: Always search by uid field to find correct student data.
     * This ensures we get the right studentId even if there are duplicate/wrong entries.
     */
    private void fetchStudentProfile(Runnable onComplete) {
        Log.d(TAG, "Searching for student with Firebase UID: " + authNodeKey);


        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot != null && snapshot.exists()) {
                    boolean foundMatch = false;


                    // Search all Students nodes to find where uid field matches authNodeKey
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String uidField = safeString(child.child("uid").getValue(String.class));


                        if (!uidField.isEmpty() && uidField.equals(authNodeKey)) {
                            // Found the correct student by uid field match
                            extractStudentData(child);
                            Log.d(TAG, "✓ Found student by uid field match");
                            Log.d(TAG, "  Student Key: " + child.getKey());
                            Log.d(TAG, "  School ID: " + schoolId);
                            Log.d(TAG, "  Full Name: " + studentFullName);
                            foundMatch = true;
                            break;
                        }
                    }


                    if (!foundMatch) {
                        // Fallback: try direct lookup by Firebase UID as key
                        Log.d(TAG, "No uid field match found, trying direct lookup...");
                        DataSnapshot directLookup = snapshot.child(authNodeKey);
                        if (directLookup.exists()) {
                            extractStudentData(directLookup);
                            Log.d(TAG, "✓ Found student by direct key lookup");
                            Log.d(TAG, "  School ID: " + schoolId);
                            Log.d(TAG, "  Full Name: " + studentFullName);
                        } else {
                            Log.w(TAG, "✗ Student profile not found anywhere!");
                            Toast.makeText(StudentAttendanceViewerActivity.this,
                                    "Student profile not found. Please complete your profile.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                } else {
                    Log.w(TAG, "Students node is empty");
                }


                if (onComplete != null) onComplete.run();
            }


            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "fetchStudentProfile cancelled: " + error.getMessage());
                if (onComplete != null) onComplete.run();
            }
        });
    }


    /**
     * Extract student data from snapshot
     */
    private void extractStudentData(DataSnapshot snapshot) {
        Object sid = snapshot.child("studentId").getValue();
        if (sid == null) sid = snapshot.child("student_number").getValue();
        if (sid != null) schoolId = String.valueOf(sid).trim();


        Object fn = snapshot.child("fullName").getValue();
        if (fn == null) fn = snapshot.child("name").getValue();
        if (fn != null) studentFullName = String.valueOf(fn).trim();


        // Fallback to Firebase Auth displayName if needed
        if ((studentFullName == null || studentFullName.isEmpty())) {
            FirebaseUser cur = FirebaseAuth.getInstance().getCurrentUser();
            if (cur != null) {
                String d = cur.getDisplayName();
                if (d != null && !d.trim().isEmpty()) studentFullName = d.trim();
            }
        }
    }


    /**
     * FIXED: Now strictly matches ONLY by school ID (studentId field).
     * This prevents showing other students' attendance records.
     */
    private void attachAttendanceRealtimeListener() {
        // Don't attach listener if no school ID
        if (schoolId == null || schoolId.isEmpty()) {
            Log.w(TAG, "No school ID available, cannot attach attendance listener");
            return;
        }


        attendanceListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot rootSnap) {
                attendanceByDate.clear();
                if (rootSnap == null || !rootSnap.exists()) {
                    Log.d(TAG, "Attendance root empty");
                    runOnUiThread(() -> {
                        String curDate = dateKeyFmt.format(new Date(calendarView.getDate()));
                        loadStatusForDate(curDate);
                        if (rvHistory != null && rvHistory.getAdapter() != null) rvHistory.getAdapter().notifyDataSetChanged();
                    });
                    return;
                }


                // FIXED: Only match by exact school ID
                String normalizedSchoolId = normalize(schoolId);
                Log.d(TAG, "Filtering attendance for school ID: " + schoolId);


                int matchCount = 0;
                for (DataSnapshot sectionSnap : rootSnap.getChildren()) {
                    // section key (e.g. fallback:bsit-ba-1-a)
                    for (DataSnapshot teacherSnap : sectionSnap.getChildren()) {
                        for (DataSnapshot dateSnap : teacherSnap.getChildren()) {
                            String dateKey = dateSnap.getKey();
                            for (DataSnapshot studentSnap : dateSnap.getChildren()) {
                                String childKey = safeString(studentSnap.getKey()); // e.g. STD-0007


                                // STRICT MATCH: Only match if childKey OR studentId field EXACTLY equals this student's schoolId
                                boolean matched = false;


                                // Match 1: Check if the child key equals school ID
                                if (!childKey.isEmpty() && normalize(childKey).equals(normalizedSchoolId)) {
                                    matched = true;
                                }


                                // Match 2: Check if studentId field equals school ID
                                if (!matched) {
                                    String sidField = safeString(studentSnap.child("studentId").getValue(String.class));
                                    if (!sidField.isEmpty() && normalize(sidField).equals(normalizedSchoolId)) {
                                        matched = true;
                                    }
                                }


                                // REMOVED: Name-based matching (this was causing the bug)
                                // We now ONLY match by exact student ID


                                if (matched) {
                                    matchCount++;
                                    String status = safeString(studentSnap.child("status").getValue(String.class));
                                    String sectionDisplay = safeString(studentSnap.child("section").getValue(String.class));
                                    if (sectionDisplay.isEmpty()) sectionDisplay = safeString(studentSnap.child("sectionDisplay").getValue(String.class));


                                    // Prefer teacherFullName then teacherName
                                    String teacherFullName = safeString(studentSnap.child("teacherFullName").getValue(String.class));
                                    if (teacherFullName.isEmpty()) teacherFullName = safeString(studentSnap.child("teacherName").getValue(String.class));


                                    String assignedSubject = safeString(studentSnap.child("assignedSubject").getValue(String.class));
                                    String studNameField = safeString(studentSnap.child("studentName").getValue(String.class));
                                    if (studNameField.isEmpty()) studNameField = safeString(studentSnap.child("fullName").getValue(String.class));


                                    // Store into attendanceByDate
                                    HistoryEntry he = new HistoryEntry(dateKey, status, sectionDisplay, teacherFullName, assignedSubject, studNameField, childKey);
                                    List<HistoryEntry> list = attendanceByDate.get(dateKey);
                                    if (list == null) {
                                        list = new ArrayList<>();
                                        attendanceByDate.put(dateKey, list);
                                    }
                                    list.add(he);
                                }
                            }
                        }
                    }
                }


                Log.d(TAG, "Found " + matchCount + " attendance records for school ID: " + schoolId);


                // sort entries per date
                for (List<HistoryEntry> list : attendanceByDate.values()) {
                    Collections.sort(list, new Comparator<HistoryEntry>() {
                        @Override public int compare(HistoryEntry a, HistoryEntry b) {
                            return a.date.compareTo(b.date);
                        }
                    });
                }


                runOnUiThread(() -> {
                    // refresh preview for the currently selected date
                    String curDate = dateKeyFmt.format(new Date(calendarView.getDate()));
                    loadStatusForDate(curDate);
                    // refresh history RV
                    loadRecentHistoryIntoRv(60);
                });
            }


            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "attendanceListener cancelled: " + error.getMessage());
            }
        };


        // attach
        attendanceRootRef.addValueEventListener(attendanceListener);
    }


    private void loadStatusForDate(String dateKey) {
        // Search in Students for node with matching uid field, then check attendanceHistory
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot studentNode = null;


                // Find the correct student node by uid field
                if (snapshot != null && snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String uidField = safeString(child.child("uid").getValue(String.class));
                        if (!uidField.isEmpty() && uidField.equals(authNodeKey)) {
                            studentNode = child;
                            break;
                        }
                    }
                }


                // Fallback to direct lookup if uid search failed
                if (studentNode == null && snapshot != null) {
                    studentNode = snapshot.child(authNodeKey);
                }


                if (studentNode != null && studentNode.exists()) {
                    DataSnapshot historySnap = studentNode.child("attendanceHistory").child(dateKey);
                    if (historySnap.exists()) {
                        displayDateStatus(dateKey, historySnap);
                        return;
                    }
                }


                // fallback to attendance cache
                displayFromCache(dateKey);
            }


            @Override public void onCancelled(@NonNull DatabaseError error) {
                tvDateStatus.setText(dateKey + ": Error loading");
            }
        });
    }


    private void displayDateStatus(String dateKey, DataSnapshot snap) {
        String status = snap.child("status").getValue(String.class);
        String section = snap.child("sectionDisplay").getValue(String.class);


        // prefer teacherFullName, fallback to teacherName
        String teacherFullName = snap.child("teacherFullName").getValue(String.class);
        if (teacherFullName == null || teacherFullName.isEmpty()) {
            teacherFullName = snap.child("teacherName").getValue(String.class);
        }
        String assignedSubject = snap.child("assignedSubject").getValue(String.class);


        String studentName = snap.child("studentName").getValue(String.class);
        StringBuilder sb = new StringBuilder();
        sb.append(dateKey).append(": ").append(status == null || status.isEmpty() ? "Not marked" : status);
        // include teacher on same line after status (preferred)
        if (teacherFullName != null && !teacherFullName.isEmpty()) {
            sb.append(" — By ").append(teacherFullName);
            if (assignedSubject != null && !assignedSubject.isEmpty()) sb.append(" (").append(assignedSubject).append(")");
        } else if (section != null && !section.isEmpty()) {
            sb.append(" — ").append(section);
        }
        if (studentName != null && !studentName.isEmpty()) sb.append("\n").append(studentName);
        tvDateStatus.setText(sb.toString());
    }


    private void displayFromCache(String dateKey) {
        List<HistoryEntry> list = attendanceByDate.get(dateKey);
        if (list == null || list.isEmpty()) {
            tvDateStatus.setText(dateKey + ": Not marked");
            return;
        }
        HistoryEntry primary = list.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(dateKey).append(": ").append(primary.status == null || primary.status.isEmpty() ? "Not marked" : primary.status);
        // show teacher name prominently if present
        if (primary.teacher != null && !primary.teacher.isEmpty()) {
            sb.append(" — By ").append(primary.teacher);
            if (primary.assignedSubject != null && !primary.assignedSubject.isEmpty()) sb.append(" (").append(primary.assignedSubject).append(")");
        } else if (primary.section != null && !primary.section.isEmpty()) {
            sb.append(" — ").append(primary.section);
        }
        if (primary.displayStudentName != null && !primary.displayStudentName.isEmpty()) sb.append("\n").append(primary.displayStudentName);
        sb.append("  (from teacher record)");
        tvDateStatus.setText(sb.toString());
    }


    private void loadRecentHistoryIntoRv(int limit) {
        List<HistoryEntry> built = new ArrayList<>();
        for (Map.Entry<String, List<HistoryEntry>> e : attendanceByDate.entrySet()) built.addAll(e.getValue());
        Collections.sort(built, (a, b) -> b.date.compareTo(a.date));
        HistoryAdapter ha = new HistoryAdapter(built);
        if (rvHistory != null) rvHistory.setAdapter(ha);
    }


    private void showHistoryDialog() {
        List<HistoryEntry> built = new ArrayList<>();
        for (Map.Entry<String, List<HistoryEntry>> e : attendanceByDate.entrySet()) built.addAll(e.getValue());
        Collections.sort(built, (a, b) -> b.date.compareTo(a.date));
        View dlg = LayoutInflater.from(this).inflate(R.layout.dialog_history_list, null);
        RecyclerView rv = dlg.findViewById(R.id.dialogHistoryRv);
        TextView tvEmpty = dlg.findViewById(R.id.dialogHistoryEmpty);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(this));
            rv.setAdapter(new HistoryAdapter(built));
        }
        if (tvEmpty != null) tvEmpty.setVisibility(built.isEmpty() ? View.VISIBLE : View.GONE);


        new AlertDialog.Builder(this)
                .setTitle("Attendance History")
                .setView(dlg)
                .setPositiveButton("Close", null)
                .show();
    }


    // --- Models & adapters ---
    private static class HistoryEntry {
        final String date;
        final String status;
        final String section;
        final String teacher;
        final String assignedSubject;
        final String displayStudentName;
        final String childKey;
        HistoryEntry(String date, String status, String section, String teacher, String assignedSubject, String displayStudentName, String childKey) {
            this.date = date;
            this.status = status;
            this.section = section;
            this.teacher = teacher;
            this.assignedSubject = assignedSubject;
            this.displayStudentName = displayStudentName;
            this.childKey = childKey;
        }
    }


    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryVH> {
        private final List<HistoryEntry> data;
        HistoryAdapter(List<HistoryEntry> items) { data = items != null ? items : new ArrayList<>(); }
        @NonNull @Override public HistoryVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_row, parent, false);
            return new HistoryVH(v);
        }
        @Override public void onBindViewHolder(@NonNull HistoryVH holder, int position) { holder.bind(data.get(position)); }
        @Override public int getItemCount() { return data.size(); }
    }


    private static class HistoryVH extends RecyclerView.ViewHolder {
        private final TextView tvDate, tvStatus, tvMeta, tvStudentName;
        HistoryVH(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvHistoryDate);
            tvStatus = itemView.findViewById(R.id.tvHistoryStatus);
            tvStudentName = itemView.findViewById(R.id.tvHistoryStudentName);
            tvMeta = itemView.findViewById(R.id.tvHistoryMeta);
        }
        void bind(HistoryEntry e) {
            tvDate.setText(e.date != null ? e.date : "");
            tvStatus.setText(e.status == null || e.status.isEmpty() ? "Not marked" : e.status);
            if (e.displayStudentName != null && !e.displayStudentName.isEmpty()) {
                tvStudentName.setText(e.displayStudentName);
                tvStudentName.setVisibility(View.VISIBLE);
            } else {
                tvStudentName.setVisibility(View.GONE);
            }
            StringBuilder meta = new StringBuilder();
            if (e.section != null && !e.section.isEmpty()) meta.append(e.section);
            // show teacher and subject in meta
            if (e.teacher != null && !e.teacher.isEmpty()) {
                if (meta.length() > 0) meta.append(" • ");
                meta.append("By ").append(e.teacher);
            }
            if (e.assignedSubject != null && !e.assignedSubject.isEmpty()) {
                if (meta.length() > 0) meta.append(" • ");
                meta.append(e.assignedSubject);
            }
            tvMeta.setText(meta.toString());
        }
    }


    private String safeString(String s) { return s == null ? "" : s.trim(); }
    private String normalize(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }


    private static String formatDate(int year, int month, int dayOfMonth) {
        int mm = month + 1;
        return String.format(Locale.getDefault(), "%04d-%02d-%02d", year, mm, dayOfMonth);
    }
}

