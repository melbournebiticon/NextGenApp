package com.example.nextgen.student;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * StudentAttendanceViewerActivity
 *
 * Updated so teacher name is displayed prominently wherever a teacher-marked attendance is shown:
 * - In the date preview (loadStatusForDate) the teacher is shown on the same line as status when possible:
 *     e.g. "2025-12-06: Present — By Prof. Santos"
 * - In history rows the meta already shows "By <teacher>".
 *
 * All other behavior (attendance cache, prompting to save school id, Students history preference) remains intact.
 */
public class StudentAttendanceViewerActivity extends AppCompatActivity {

    private static final String TAG = "StuAttViewer";

    private CalendarView calendarView;
    private TextView tvDateStatus;
    private Button btnViewHistory;
    private Button btnRefresh;
    private RecyclerView rvHistory;

    private DatabaseReference studentsRef;
    private DatabaseReference attendanceRootRef;

    private String authNodeKey;    // node key used for Students (usually FirebaseAuth UID)
    private String schoolId;       // school id like "STD-0007" (studentId field)
    private String studentFullName;

    private final SimpleDateFormat dateKeyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // in-memory cache of attendance entries for this student keyed by date -> list
    private final Map<String, List<HistoryEntry>> attendanceByDate = new HashMap<>();

    // flags to prompt user once
    private boolean promptedToSaveSchoolId = false;

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

        // Determine authNodeKey (prefer Intent extra else FirebaseAuth uid)
        String fromIntent = getIntent().getStringExtra("studentId");
        if (fromIntent != null && !fromIntent.trim().isEmpty()) {
            authNodeKey = fromIntent.trim();
        } else {
            FirebaseUser cur = FirebaseAuth.getInstance().getCurrentUser();
            if (cur != null) authNodeKey = cur.getUid();
        }

        if (authNodeKey == null || authNodeKey.trim().isEmpty()) {
            Toast.makeText(this, "Student ID not available", Toast.LENGTH_LONG).show();
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
            attachAttendanceRealtimeListener();
            // initial UI load
            loadStatusForDate(todayKey);
            if (rvHistory != null) {
                rvHistory.setLayoutManager(new LinearLayoutManager(this));
                loadRecentHistoryIntoRv(60);
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
     * Read Students/{authNodeKey} to get studentId (school id) and fullName.
     * If not found, we leave schoolId null (we will detect candidate from Attendance scanning).
     */
    private void fetchStudentProfile(Runnable onComplete) {
        studentsRef.child(authNodeKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot != null && snapshot.exists()) {
                    Object sid = snapshot.child("studentId").getValue();
                    if (sid == null) sid = snapshot.child("student_number").getValue();
                    if (sid != null) schoolId = String.valueOf(sid).trim();
                    Object fn = snapshot.child("fullName").getValue();
                    if (fn == null) fn = snapshot.child("name").getValue();
                    if (fn != null) studentFullName = String.valueOf(fn).trim();
                    Log.d(TAG, "fetchStudentProfile -> schoolId=" + schoolId + " fullName=" + studentFullName);
                } else {
                    Log.d(TAG, "fetchStudentProfile: Students/" + authNodeKey + " not found or empty");
                    // try to use auth displayName as fullName fallback
                    FirebaseUser cur = FirebaseAuth.getInstance().getCurrentUser();
                    if (cur != null && (studentFullName == null || studentFullName.isEmpty())) {
                        String d = cur.getDisplayName();
                        if (d != null && !d.trim().isEmpty()) studentFullName = d.trim();
                    }
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
     * Attach a realtime listener on Attendance root and filter entries for THIS student's identifiers
     * (schoolId and fullName). When scanning, if we see a studentId in attendance and local Students node
     * does not have schoolId, we prompt the user once to save it.
     */
    private void attachAttendanceRealtimeListener() {
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

                // candidate identifiers
                Set<String> candidates = new HashSet<>();
                if (schoolId != null && !schoolId.isEmpty()) candidates.add(normalize(schoolId));
                if (studentFullName != null && !studentFullName.isEmpty()) candidates.add(normalize(studentFullName));

                // we'll capture first studentId seen in attendance to offer saving it
                String firstStudentIdSeen = null;

                for (DataSnapshot sectionSnap : rootSnap.getChildren()) {
                    // section key (e.g. fallback:bsit-ba-1-a)
                    for (DataSnapshot teacherSnap : sectionSnap.getChildren()) {
                        for (DataSnapshot dateSnap : teacherSnap.getChildren()) {
                            String dateKey = dateSnap.getKey();
                            for (DataSnapshot studentSnap : dateSnap.getChildren()) {
                                String childKey = safeString(studentSnap.getKey()); // e.g. STD-0007
                                String status = safeString(studentSnap.child("status").getValue(String.class));
                                String sectionDisplay = safeString(studentSnap.child("section").getValue(String.class));
                                if (sectionDisplay.isEmpty()) sectionDisplay = safeString(studentSnap.child("sectionDisplay").getValue(String.class));
                                String teacherName = safeString(studentSnap.child("teacherName").getValue(String.class));
                                String sidField = safeString(studentSnap.child("studentId").getValue(String.class));
                                String studNameField = safeString(studentSnap.child("studentName").getValue(String.class));
                                if (studNameField.isEmpty()) studNameField = safeString(studentSnap.child("fullName").getValue(String.class));

                                if (firstStudentIdSeen == null && !sidField.isEmpty()) firstStudentIdSeen = sidField;

                                // match checks: childKey equals schoolId OR studentId field equals schoolId OR name contains full name
                                boolean matched = false;
                                if (!childKey.isEmpty() && schoolId != null && normalize(childKey).equals(normalize(schoolId))) matched = true;
                                if (!matched && !sidField.isEmpty() && schoolId != null && normalize(sidField).equals(normalize(schoolId))) matched = true;
                                if (!matched && studentFullName != null && !studentFullName.isEmpty() && !studNameField.isEmpty()) {
                                    String nName = normalize(studNameField);
                                    if (nName.contains(normalize(studentFullName)) || normalize(studentFullName).contains(nName)) matched = true;
                                }

                                if (matched) {
                                    // store into attendanceByDate (include teacherName to show)
                                    HistoryEntry he = new HistoryEntry(dateKey, status, sectionDisplay, teacherName, studNameField, childKey);
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

                // if we don't have a saved schoolId and we saw one in attendance records, prompt the user once
                if ((schoolId == null || schoolId.isEmpty()) && !promptedToSaveSchoolId && firstStudentIdSeen != null) {
                    promptedToSaveSchoolId = true;
                    String finalFirstStudentIdSeen = firstStudentIdSeen;
                    runOnUiThread(() -> promptToSaveSchoolId(finalFirstStudentIdSeen));
                }

                // sort entries per date (optional) and update UI
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

    /**
     * If the app detects a likely school id in Attendance but Students/{authNodeKey}.studentId is empty,
     * prompt the user once to save it into their Students node.
     */
    private void promptToSaveSchoolId(String discoveredSchoolId) {
        if (discoveredSchoolId == null || discoveredSchoolId.isEmpty()) return;
        EditText et = new EditText(this);
        et.setText(discoveredSchoolId);

        new AlertDialog.Builder(this)
                .setTitle("Save your school ID?")
                .setMessage("We found a school ID in teacher attendance records: " + discoveredSchoolId +
                        "\n\nIf this is your school ID, saving it will let the app automatically show your attendance.")
                .setView(et)
                .setCancelable(false)
                .setPositiveButton("Save", (d, w) -> {
                    String entered = et.getText() == null ? "" : et.getText().toString().trim();
                    if (entered.isEmpty()) {
                        Toast.makeText(this, "School ID cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("studentId", entered);
                    if (studentFullName != null && !studentFullName.isEmpty()) updates.put("fullName", studentFullName);
                    studentsRef.child(authNodeKey).updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                schoolId = entered;
                                Toast.makeText(this, "Saved school ID.", Toast.LENGTH_SHORT).show();
                                // re-scan attendance by re-attaching listener
                                try {
                                    if (attendanceRootRef != null && attendanceListener != null) {
                                        attendanceRootRef.removeEventListener(attendanceListener);
                                        attendanceRootRef.addValueEventListener(attendanceListener);
                                    }
                                } catch (Exception ex) { Log.w(TAG, "re-attach attendanceListener failed: " + ex.getMessage()); }
                                loadStatusForDate(dateKeyFmt.format(new Date()));
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to save school ID. Try again.", Toast.LENGTH_SHORT).show();
                                Log.w(TAG, "Failed to save schoolId: " + e.getMessage());
                            });
                })
                .setNegativeButton("Skip", (d, w) -> {
                    Toast.makeText(this, "You can set your School ID later from profile.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void loadStatusForDate(String dateKey) {
        // prefer Students/{authNodeKey}/attendanceHistory/{date}
        studentsRef.child(authNodeKey).child("attendanceHistory").child(dateKey)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        if (snap != null && snap.exists()) {
                            String status = snap.child("status").getValue(String.class);
                            String section = snap.child("sectionDisplay").getValue(String.class);
                            String teacher = snap.child("teacherName").getValue(String.class);
                            String studentName = snap.child("studentName").getValue(String.class);
                            StringBuilder sb = new StringBuilder();
                            sb.append(dateKey).append(": ").append(status == null || status.isEmpty() ? "Not marked" : status);
                            // include teacher on same line after status (preferred)
                            if (teacher != null && !teacher.isEmpty()) {
                                sb.append(" — By ").append(teacher);
                            } else if (section != null && !section.isEmpty()) {
                                sb.append(" — ").append(section);
                            }
                            if (studentName != null && !studentName.isEmpty()) sb.append("\n").append(studentName);
                            tvDateStatus.setText(sb.toString());
                        } else {
                            // fallback to attendance cache
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
                            } else if (primary.section != null && !primary.section.isEmpty()) {
                                sb.append(" — ").append(primary.section);
                            }
                            if (primary.displayStudentName != null && !primary.displayStudentName.isEmpty()) sb.append("\n").append(primary.displayStudentName);
                            sb.append("  (from teacher record)");
                            tvDateStatus.setText(sb.toString());
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        tvDateStatus.setText(dateKey + ": Error loading");
                    }
                });
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
        final String displayStudentName;
        final String childKey;
        HistoryEntry(String date, String status, String section, String teacher, String displayStudentName, String childKey) {
            this.date = date;
            this.status = status;
            this.section = section;
            this.teacher = teacher;
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
            if (e.teacher != null && !e.teacher.isEmpty()) {
                if (meta.length() > 0) meta.append(" • ");
                meta.append("By ").append(e.teacher);
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