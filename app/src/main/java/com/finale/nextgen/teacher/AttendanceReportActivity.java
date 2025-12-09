package com.finale.nextgen.teacher;

import static com.finale.nextgen.teacher.StudentRecordActivity.formatDate;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * AttendanceReportActivity (updated - full)
 *
 * This implementation:
 * - Scans Attendance/<attendanceChildKey> for date nodes (yyyy-MM-dd) both direct and teacher-scoped.
 * - For each date node, reads STD-xxxx student entries and uses their "status" (Present/Late/Excused/Absent)
 *   to increment per-student counters and increments totalDays once per date per student.
 * - Day preview and single-day views include marks/remark alongside status.
 * - computePercentage uses the WEIGHTS map so Late/Excused/Absent/Present are weighted appropriately.
 *
 * Replace your existing AttendanceReportActivity.java with this file.
 */
public class AttendanceReportActivity extends AppCompatActivity {
    private String sectionId;
    private String sectionFallbackKey; // optional fallback key
    private DatabaseReference summaryRef;
    private DatabaseReference studentsRef;
    private DatabaseReference attendanceRoot;
    private RecyclerView recyclerView;
    private SummaryAdapter adapter;
    private final List<AttendanceSummaryModel> items = new ArrayList<>();
    private TextView tvReportTitle;
    private TextView tvLastUpdated;
    private TextView btnExport;
    private TextView btnToggleCalendar;
    private TextView tvDateInfo;
    private TextView btnComputeClassAverage;
    private ObservableHorizontalScrollView headerScroll;
    private final List<ObservableHorizontalScrollView> rowScrolls = new ArrayList<>();
    private boolean isSyncing = false;
    // Weights
    private static final Map<String, Integer> WEIGHTS = new HashMap<>();
    static {
        WEIGHTS.put("Present", 100);
        WEIGHTS.put("Late", 90);
        WEIGHTS.put("Excused", 100);
        WEIGHTS.put("Absent", 0);
    }
    private String teacherId;

    private static final Pattern DATE_KEY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final int MAX_RECURSIVE_DEPTH = 8;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_report);

        // bind views
        tvReportTitle = findViewById(R.id.tvReportTitle);
        recyclerView = findViewById(R.id.attendanceRecyclerView);
        tvLastUpdated = findViewById(R.id.tvLastUpdated);
        btnExport = findViewById(R.id.btnExport);
        headerScroll = findViewById(R.id.header_hsv);
        btnToggleCalendar = findViewById(R.id.btnToggleCalendar);
        tvDateInfo = findViewById(R.id.tvDateInfo);
        btnComputeClassAverage = findViewById(R.id.btnComputeClassAverage);

        sectionId = getIntent().getStringExtra("sectionId");
        sectionFallbackKey = getIntent().getStringExtra("sectionFallbackKey");

        String sectionDisplay = getIntent().getStringExtra("sectionDisplay");
        if (!TextUtils.isEmpty(sectionDisplay)) tvReportTitle.setText("Attendance Report — " + sectionDisplay);
        if (sectionId == null || sectionId.trim().isEmpty()) {
            tvReportTitle.setText("Attendance Report (section not provided)");
            return;
        }

        String teacherIdFromIntent = getIntent().getStringExtra("teacherId");
        if (!TextUtils.isEmpty(teacherIdFromIntent)) {
            teacherId = teacherIdFromIntent;
        } else {
            FirebaseUser cur = FirebaseAuth.getInstance().getCurrentUser();
            teacherId = (cur != null && !TextUtils.isEmpty(cur.getUid())) ? cur.getUid() : null;
        }

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        String attendanceChildKey = !TextUtils.isEmpty(sectionFallbackKey) ? sectionFallbackKey : sectionId;
        attendanceRoot = FirebaseDatabase.getInstance().getReference("Attendance").child(attendanceChildKey);

        if (teacherId != null) {
            summaryRef = FirebaseDatabase.getInstance().getReference("AttendanceSummary").child(sectionId).child(teacherId);
        } else {
            summaryRef = FirebaseDatabase.getInstance().getReference("AttendanceSummary").child(sectionId);
            Toast.makeText(this, "Teacher identity unavailable — showing section-level data (may include multiple teachers).", Toast.LENGTH_LONG).show();
        }

        adapter = new SummaryAdapter(items, this::registerRowScroll);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        if (btnExport != null) btnExport.setOnClickListener(v -> exportCsv());

        if (headerScroll != null) {
            headerScroll.setOnScrollChangedListener((src, x, y, oldx, oldy) -> {
                if (isSyncing) return;
                isSyncing = true;
                for (ObservableHorizontalScrollView r : rowScrolls) if (r != null) r.scrollTo(x, 0);
                isSyncing = false;
            });
        }

        if (btnToggleCalendar != null) btnToggleCalendar.setOnClickListener(v -> showCalendarDialog());
        if (btnComputeClassAverage != null) btnComputeClassAverage.setOnClickListener(v -> showPerStudentTotalsDialog());

        loadTodaySnapshot();
    }

    private void registerRowScroll(ObservableHorizontalScrollView row) {
        if (row == null) return;
        if (rowScrolls.contains(row)) return;
        rowScrolls.add(row);
        row.setOnScrollChangedListener((src, x, y, oldx, oldy) -> {
            if (isSyncing) return;
            isSyncing = true;
            if (headerScroll != null && src != headerScroll) headerScroll.scrollTo(x, 0);
            for (ObservableHorizontalScrollView r : rowScrolls) {
                if (r != null && r != src) r.scrollTo(x, 0);
            }
            isSyncing = false;
        });
    }

    // ---------- Percentage helpers ----------
    private static int computePercentage(long present, long late, long excused, long absent, int totalDays) {
        long sumCounts = present + late + excused + absent;
        int denom;
        if (totalDays > 0) {
            denom = totalDays;
        } else if (sumCounts > 0) {
            denom = (int) Math.min((long) Integer.MAX_VALUE, sumCounts);
        } else {
            return 0;
        }
        long weighted = present * WEIGHTS.get("Present") + late * WEIGHTS.get("Late") + excused * WEIGHTS.get("Excused") + absent * WEIGHTS.get("Absent");
        double avg = (double) weighted / (double) denom;
        int pct = (int) Math.round(avg);
        return Math.max(0, Math.min(100, pct));
    }

    private static int computePercentageFromCounts(Map<String, Long> counts, int totalDays) {
        if (counts == null) return 0;
        long present = counts.getOrDefault("Present", 0L);
        long late = counts.getOrDefault("Late", 0L);
        long excused = counts.getOrDefault("Excused", 0L);
        long absent = counts.getOrDefault("Absent", 0L);
        return computePercentage(present, late, excused, absent, totalDays);
    }

    // ---------- Calendar + day snapshot ----------
    private void showCalendarDialog() {
        View dlgView = LayoutInflater.from(this).inflate(R.layout.dialog_calendar, null);
        CalendarView dlgCalendar = dlgView.findViewById(R.id.dialogCalendarView);
        RecyclerView dlgRv = dlgView.findViewById(R.id.dialogRvDayRecords);
        TextView dlgTvInfo = dlgView.findViewById(R.id.dialogTvDateInfo);

        dlgRv.setLayoutManager(new LinearLayoutManager(this));
        final DayAdapter dayAdapter = new DayAdapter(new ArrayList<>());
        dlgRv.setAdapter(dayAdapter);

        long todayMillis = System.currentTimeMillis();
        dlgCalendar.setDate(todayMillis, false, true);
        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(todayMillis));
        dlgTvInfo.setText("Selected: " + todayKey);
        loadSingleDaySnapshotIntoAdapter(todayKey, dayAdapter, dlgTvInfo);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Pick a date to preview")
                .setView(dlgView)
                .setNegativeButton("Close", null)
                .create();

        dlgCalendar.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            String dateKey = formatDate(year, month, dayOfMonth);
            dlgTvInfo.setText("Selected: " + dateKey);
            loadSingleDaySnapshotIntoAdapter(dateKey, dayAdapter, dlgTvInfo);
        });

        dialog.show();
    }

    private void resolveDateNode(final String dateKey, final ValueEventListener listener) {
        if (attendanceRoot == null) return;

        attendanceRoot.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                scanAttendanceTopLevelForDate(dateKey, listener);
                return;
            }

            DataSnapshot rootSnap = task.getResult();
            DataSnapshot directDate = rootSnap.child(dateKey);
            if (directDate != null && directDate.exists()) {
                listener.onDataChange(directDate);
                return;
            }

            if (teacherId != null && !teacherId.isEmpty()) {
                DataSnapshot teacherDate = rootSnap.child(teacherId).child(dateKey);
                if (teacherDate != null && teacherDate.exists()) {
                    listener.onDataChange(teacherDate);
                    return;
                }
            }

            scanAttendanceTopLevelForDate(dateKey, listener);
        }).addOnFailureListener(e -> scanAttendanceTopLevelForDate(dateKey, listener));
    }

    private void scanAttendanceTopLevelForDate(final String dateKey, final ValueEventListener listener) {
        DatabaseReference attendanceTop = FirebaseDatabase.getInstance().getReference("Attendance");
        attendanceTop.get().addOnCompleteListener(allTask -> {
            if (!allTask.isSuccessful() || allTask.getResult() == null) {
                attendanceTop.child(dateKey).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) { listener.onDataChange(snapshot); }
                    @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) { listener.onCancelled(error); }
                });
                return;
            }

            DataSnapshot all = allTask.getResult();
            DataSnapshot found = null;
            for (DataSnapshot sectionNode : all.getChildren()) {
                DataSnapshot candidate = sectionNode.child(dateKey);
                if (candidate != null && candidate.exists()) {
                    found = candidate;
                    break;
                }
                if (teacherId != null && !teacherId.isEmpty()) {
                    DataSnapshot tCandidate = sectionNode.child(teacherId).child(dateKey);
                    if (tCandidate != null && tCandidate.exists()) {
                        found = tCandidate;
                        break;
                    }
                }
            }

            if (found != null) {
                Toast.makeText(this, "Found date node under a different section key (using fallback scan).", Toast.LENGTH_SHORT).show();
                listener.onDataChange(found);
            } else {
                attendanceTop.child(dateKey).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) { listener.onDataChange(snapshot); }
                    @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) { listener.onCancelled(error); }
                });
            }
        }).addOnFailureListener(e -> attendanceTop.child(dateKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) { listener.onDataChange(snapshot); }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) { listener.onCancelled(error); }
        }));
    }

    private void loadSingleDaySnapshotIntoAdapter(final String dateKey, final DayAdapter dayAdapter, final TextView infoView) {
        if (attendanceRoot == null) return;
        resolveDateNode(dateKey, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DayRecord> list = new ArrayList<>();
                if (snapshot != null && snapshot.exists()) {
                    boolean looksLikeTeacherScoped = false;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        if (child.hasChild("status") || child.hasChild("studentId")) {
                            looksLikeTeacherScoped = true;
                            break;
                        }
                    }
                    if (looksLikeTeacherScoped) {
                        for (DataSnapshot s : snapshot.getChildren()) {
                            String sid = s.getKey();
                            if (sid == null) continue;
                            String status = safeString(s.child("status").getValue(String.class));
                            String marks = safeString(s.child("marks").getValue(String.class));
                            if (marks.isEmpty()) marks = safeString(s.child("remark").getValue(String.class));
                            String name = safeString(s.child("studentName").getValue(String.class));
                            if (name.isEmpty()) name = "(Unknown)";
                            String combined = status;
                            if (!marks.isEmpty()) {
                                combined = (combined.isEmpty() ? marks : (combined + " — " + marks));
                            }
                            if (combined.isEmpty()) combined = "Not Marked";
                            list.add(new DayRecord(sid, name, combined));
                        }
                    } else {
                        for (DataSnapshot teacherNode : snapshot.getChildren()) {
                            for (DataSnapshot s : teacherNode.getChildren()) {
                                String sid = s.getKey();
                                if (sid == null) continue;
                                String status = safeString(s.child("status").getValue(String.class));
                                String marks = safeString(s.child("marks").getValue(String.class));
                                if (marks.isEmpty()) marks = safeString(s.child("remark").getValue(String.class));
                                String name = safeString(s.child("studentName").getValue(String.class));
                                if (name.isEmpty()) name = "(Unknown)";
                                String combined = status;
                                if (!marks.isEmpty()) {
                                    combined = (combined.isEmpty() ? marks : (combined + " — " + marks));
                                }
                                if (combined.isEmpty()) combined = "Not Marked";
                                list.add(new DayRecord(sid, name, combined));
                            }
                        }
                    }
                }
                Map<String, DayRecord> dedup = new HashMap<>();
                for (DayRecord r : list) if (!dedup.containsKey(r.id)) dedup.put(r.id, r);
                List<DayRecord> finalList = new ArrayList<>(dedup.values());
                Collections.sort(finalList, (a, b) -> a.name.compareToIgnoreCase(b.name));
                dayAdapter.setItems(finalList);
                if (infoView != null) infoView.setText(String.format(Locale.getDefault(), "%s — %d record(s)", dateKey, finalList.size()));
                if (tvDateInfo != null) tvDateInfo.setText(String.format(Locale.getDefault(), "%s — %d record(s)", dateKey, finalList.size()));
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                Toast.makeText(AttendanceReportActivity.this, "Failed to load attendance for " + dateKey, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadTodaySnapshot() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        if (tvDateInfo != null) tvDateInfo.setText("Selected: " + today);
        loadSingleDaySnapshotIntoMain(today);
    }

    private void loadSingleDaySnapshotIntoMain(final String dateKey) {
        if (attendanceRoot == null) return;
        resolveDateNode(dateKey, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<AttendanceSummaryModel> dayItems = new ArrayList<>();
                if (snapshot != null && snapshot.exists()) {
                    boolean looksLikeTeacherScoped = false;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        if (child.hasChild("status") || child.hasChild("studentId")) {
                            looksLikeTeacherScoped = true;
                            break;
                        }
                    }
                    if (looksLikeTeacherScoped) {
                        for (DataSnapshot s : snapshot.getChildren()) {
                            String studentId = s.getKey();
                            if (studentId == null) continue;
                            String status = safeString(s.child("status").getValue(String.class));
                            String marks = safeString(s.child("marks").getValue(String.class));
                            if (marks.isEmpty()) marks = safeString(s.child("remark").getValue(String.class));
                            String studentName = safeString(s.child("studentName").getValue(String.class));
                            if (studentName.isEmpty()) studentName = "(Unknown)";
                            Map<String, Long> counts = new HashMap<>();
                            counts.put("Present", status.equalsIgnoreCase("Present") ? 1L : 0L);
                            counts.put("Late", status.equalsIgnoreCase("Late") ? 1L : 0L);
                            counts.put("Excused", status.equalsIgnoreCase("Excused") ? 1L : 0L);
                            counts.put("Absent", status.equalsIgnoreCase("Absent") ? 1L : 0L);
                            int pct = computePercentageFromCounts(counts, 1);
                            String combinedStatus = status;
                            if (!marks.isEmpty()) combinedStatus = (combinedStatus.isEmpty() ? marks : (combinedStatus + " — " + marks));
                            AttendanceSummaryModel model = new AttendanceSummaryModel(studentId, studentName, pct, 1, counts, 0L, combinedStatus.isEmpty() ? "Not Marked" : combinedStatus);
                            dayItems.add(model);
                        }
                    } else {
                        Map<String, AttendanceSummaryModel> map = new HashMap<>();
                        for (DataSnapshot teacherNode : snapshot.getChildren()) {
                            for (DataSnapshot s : teacherNode.getChildren()) {
                                String studentId = s.getKey();
                                if (studentId == null) continue;
                                if (map.containsKey(studentId)) continue;
                                String status = safeString(s.child("status").getValue(String.class));
                                String marks = safeString(s.child("marks").getValue(String.class));
                                if (marks.isEmpty()) marks = safeString(s.child("remark").getValue(String.class));
                                String studentName = safeString(s.child("studentName").getValue(String.class));
                                if (studentName.isEmpty()) studentName = "(Unknown)";
                                Map<String, Long> counts = new HashMap<>();
                                counts.put("Present", status.equalsIgnoreCase("Present") ? 1L : 0L);
                                counts.put("Late", status.equalsIgnoreCase("Late") ? 1L : 0L);
                                counts.put("Excused", status.equalsIgnoreCase("Excused") ? 1L : 0L);
                                counts.put("Absent", status.equalsIgnoreCase("Absent") ? 1L : 0L);
                                int pct = computePercentageFromCounts(counts, 1);
                                String combinedStatus = status;
                                if (!marks.isEmpty()) combinedStatus = (combinedStatus.isEmpty() ? marks : (combinedStatus + " — " + marks));
                                map.put(studentId, new AttendanceSummaryModel(studentId, studentName, pct, 1, counts, 0L, combinedStatus.isEmpty() ? "Not Marked" : combinedStatus));
                            }
                        }
                        dayItems.addAll(map.values());
                    }
                }
                Collections.sort(dayItems, (o1, o2) -> o1.studentName.compareToIgnoreCase(o2.studentName));
                adapter.setItems(dayItems);
                if (tvDateInfo != null) tvDateInfo.setText(String.format(Locale.getDefault(), "%s — %d record(s)", dateKey, dayItems.size()));
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                Toast.makeText(AttendanceReportActivity.this, "Failed to load attendance for " + dateKey, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------- Totals dialog ----------
    private void showPerStudentTotalsDialog() {
        aggregateTotalsFromAttendanceDates();


        summaryRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                aggregateTotalsFromAttendanceDates();
                return;
            }
            DataSnapshot snap = task.getResult();
            if (!snap.exists()) {
                aggregateTotalsFromAttendanceDates();
                return;
            }

            boolean studentLevel = false;
            for (DataSnapshot c : snap.getChildren()) {
                if (c.hasChild("counts") || c.hasChild("totalDays") || c.hasChild("attendancePercentage")) {
                    studentLevel = true;
                }
                break;
            }

            if (studentLevel) {
                boolean anyMeaningful = false;
                for (DataSnapshot s : snap.getChildren()) {
                    long present = safeLong(s.child("counts").child("Present").getValue());
                    long late = safeLong(s.child("counts").child("Late").getValue());
                    long excused = safeLong(s.child("counts").child("Excused").getValue());
                    long absent = safeLong(s.child("counts").child("Absent").getValue());
                    int totalDays = safeInt(s.child("totalDays").getValue());
                    if (totalDays > 0 || (present + late + excused + absent) > 0) {
                        anyMeaningful = true;
                        break;
                    }
                }
                if (anyMeaningful) {
                    buildTotalsFromStudentSnapshot(snap);
                } else {
                    aggregateTotalsFromAttendanceDates();
                }
            } else {
                boolean anyCounts = false;
                outer: for (DataSnapshot teacherNode : snap.getChildren()) {
                    for (DataSnapshot s : teacherNode.getChildren()) {
                        if (s.hasChild("counts") || s.hasChild("status") || s.hasChild("totalDays")) {
                            anyCounts = true;
                            break outer;
                        }
                    }
                }
                if (anyCounts) {
                    aggregateTotalsAcrossTeachers(snap);
                } else {
                    aggregateTotalsFromAttendanceDates();
                }
            }
        });
    }
    /**
     * Use top-level Attendance/<attendanceChildKey> snapshot to aggregate totals across all date nodes found
     * (includes teacher-scoped date nodes and direct date nodes). Each saved student entry under a date increments totalDays.
     */
    private void aggregateTotalsFromAttendanceDates() {
        if (attendanceRoot == null) {
            Toast.makeText(this, "Attendance root not configured.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Loading attendance data...", Toast.LENGTH_SHORT).show();

        attendanceRoot.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Toast.makeText(this, "Failed to read Attendance for aggregation.", Toast.LENGTH_SHORT).show();
                return;
            }

            DataSnapshot sectionSnap = task.getResult();

            if (!sectionSnap.exists()) {
                Toast.makeText(this, "No attendance data found.", Toast.LENGTH_SHORT).show();
                return;
            }

            final Map<String, TotalsAccumulator> acc = new HashMap<>();
            final Map<String, java.util.Set<String>> seenByDate = new HashMap<>();
            int dateNodesFound = 0;

            // DEBUG: Log what teacherId we're filtering by
            String filterMsg = (teacherId != null && !teacherId.isEmpty())
                    ? "Filtering by teacher: " + teacherId
                    : "No teacher filter (showing all)";
            android.util.Log.d("AttendanceDebug", filterMsg);

            // Process all children under the section node
            for (DataSnapshot childNode : sectionSnap.getChildren()) {
                String childKey = childNode.getKey();
                if (childKey == null) continue;

                android.util.Log.d("AttendanceDebug", "Found child node: " + childKey);

                // Check if this child is a date node directly
                if (DATE_KEY.matcher(childKey).matches()) {
                    // Direct date structure: Attendance/fallback/2025-12-07/STD-xxxx
                    android.util.Log.d("AttendanceDebug", "Processing DIRECT date node: " + childKey);
                    dateNodesFound++;
                    processStudentsUnderDate(childNode, childKey, acc, seenByDate);
                } else {
                    // Assume this is a teacher node: Attendance/fallback/TCHR-xxxx/2025-12-07/STD-xxxx
                    String teacherKey = childKey;

                    // If we have a teacher filter, check if this matches
                    if (teacherId != null && !teacherId.isEmpty() && !teacherKey.equals(teacherId)) {
                        android.util.Log.d("AttendanceDebug", "Skipping teacher node (doesn't match filter): " + teacherKey);
                        continue;
                    }

                    android.util.Log.d("AttendanceDebug", "Processing teacher node: " + teacherKey);

                    // Look for date nodes under this teacher
                    for (DataSnapshot dateNode : childNode.getChildren()) {
                        String dateKey = dateNode.getKey();

                        if (dateKey != null && DATE_KEY.matcher(dateKey).matches()) {
                            android.util.Log.d("AttendanceDebug", "Found date under teacher: " + dateKey);
                            dateNodesFound++;
                            processStudentsUnderDate(dateNode, dateKey, acc, seenByDate);
                        }
                    }
                }
            }

            android.util.Log.d("AttendanceDebug", "Total date nodes found: " + dateNodesFound);
            android.util.Log.d("AttendanceDebug", "Total students in accumulator: " + acc.size());

            Toast.makeText(this, "Scanned " + dateNodesFound + " date node(s), " + acc.size() + " student(s)", Toast.LENGTH_LONG).show();

            // Build TotalsRow list
            final List<TotalsRow> rows = new ArrayList<>();
            final List<String> idsToResolve = new ArrayList<>();

            for (TotalsAccumulator t : acc.values()) {
                android.util.Log.d("AttendanceDebug", "Student " + t.id + ": P=" + t.present + " L=" + t.late + " E=" + t.excused + " A=" + t.absent + " Days=" + t.totalDays);

                int totalDays = t.totalDays;
                long sum = t.present + t.late + t.excused + t.absent;
                if (totalDays <= 0 && sum > 0) {
                    totalDays = (int) Math.min(Integer.MAX_VALUE, sum);
                }

                int pct = computePercentage(t.present, t.late, t.excused, t.absent, totalDays);
                TotalsRow r = new TotalsRow(t.id, t.name, t.present, t.late, t.excused, t.absent, totalDays, pct);
                rows.add(r);

                if (t.name == null || t.name.trim().isEmpty() || "(Unknown)".equals(t.name.trim())) {
                    idsToResolve.add(t.id);
                }
            }

            if (rows.isEmpty()) {
                Toast.makeText(this, "No student attendance records found.", Toast.LENGTH_SHORT).show();
                return;
            }

            resolveNamesAndShow(rows, idsToResolve);
        });
    }

    // Helper method to process students under a date node
    private void processStudentsUnderDate(DataSnapshot dateNode, String dateKey,
                                          Map<String, TotalsAccumulator> acc,
                                          Map<String, java.util.Set<String>> seenByDate) {

        java.util.Set<String> seenThisDate = seenByDate.computeIfAbsent(dateKey, d -> new java.util.HashSet<>());

        for (DataSnapshot studentNode : dateNode.getChildren()) {
            if (studentNode == null) continue;
            String studentKey = studentNode.getKey();
            if (studentKey == null) continue;

            // Avoid double counting
            if (seenThisDate.contains(studentKey)) {
                android.util.Log.d("AttendanceDebug", "Skipping duplicate: " + studentKey + " on " + dateKey);
                continue;
            }
            seenThisDate.add(studentKey);

            // Get or create accumulator
            TotalsAccumulator t = acc.get(studentKey);
            if (t == null) {
                t = new TotalsAccumulator();
                t.id = studentKey;
                acc.put(studentKey, t);
            }

            // Get student name
            String studentName = safeString(studentNode.child("studentName").getValue(String.class));
            if (studentName.isEmpty()) {
                studentName = safeString(studentNode.child("studentFullName").getValue(String.class));
            }
            if (!studentName.isEmpty()) {
                t.name = studentName;
            }

            // Get status
            String status = safeString(studentNode.child("status").getValue(String.class));

            if ("Present".equalsIgnoreCase(status)) {
                t.present++;
            } else if ("Late".equalsIgnoreCase(status)) {
                t.late++;
            } else if ("Excused".equalsIgnoreCase(status)) {
                t.excused++;
            } else if ("Absent".equalsIgnoreCase(status)) {
                t.absent++;
            } else {
                t.present++; // Default to present
            }

            t.totalDays++;

            android.util.Log.d("AttendanceDebug", "Processed " + studentKey + " on " + dateKey + " status=" + status);
        }
    }

    // helper to decide if a snapshot looks like a student node by heuristics (some nodes may be nested variably)
    private static boolean looksLikeStudentNode(DataSnapshot snap) {
        if (snap == null) return false;
        // heuristics: presence of studentId, studentName, status, marks or scalar leaf properties
        if (snap.hasChild("studentId") || snap.hasChild("studentName") || snap.hasChild("status") || snap.hasChild("marks") || snap.hasChild("remark")) return true;
        // if it contains immediate leaves (non-nested) like date/student properties, consider it student-like
        int childCount = 0;
        for (DataSnapshot c : snap.getChildren()) {
            childCount++;
            if (childCount > 4) break;
        }
        // if small number of children, likely student node
        return childCount <= 6;
    }

    // handle a student node snapshot and update accumulator (ensures seenThisDate prevents double count)
    private void handleStudentNode(DataSnapshot studentNode, java.util.Set<String> seenThisDate, Map<String, TotalsAccumulator> acc) {
        if (studentNode == null || !studentNode.exists()) return;
        String sid = studentNode.getKey();
        if (sid == null || sid.trim().isEmpty()) {
            sid = safeString(studentNode.child("studentId").getValue(String.class));
        }
        if (sid == null || sid.trim().isEmpty()) return;
        if (seenThisDate.contains(sid)) return;
        seenThisDate.add(sid);

        TotalsAccumulator t = acc.get(sid);
        if (t == null) {
            t = new TotalsAccumulator();
            t.id = sid;
            acc.put(sid, t);
        }

        // try to populate name if present
        String sName = safeString(studentNode.child("studentName").getValue(String.class));
        if (sName.isEmpty()) sName = safeString(studentNode.child("studentFullName").getValue(String.class));
        if (!sName.isEmpty()) t.name = sName;

        // prefer explicit counts if present
        DataSnapshot countsNode = studentNode.child("counts");
        if (countsNode != null && countsNode.exists()) {
            t.present += safeLong(countsNode.child("Present").getValue());
            t.late += safeLong(countsNode.child("Late").getValue());
            t.excused += safeLong(countsNode.child("Excused").getValue());
            t.absent += safeLong(countsNode.child("Absent").getValue());
        } else {
            // explicit per-date status (preferred)
            String status = safeString(studentNode.child("status").getValue(String.class));
            if (status.isEmpty()) status = safeString(studentNode.child("attendanceStatus").getValue(String.class));

            if (!status.isEmpty()) {
                if ("Present".equalsIgnoreCase(status)) t.present++;
                else if ("Late".equalsIgnoreCase(status)) t.late++;
                else if ("Excused".equalsIgnoreCase(status)) t.excused++;
                else if ("Absent".equalsIgnoreCase(status)) t.absent++;
                else t.present++; // unknown status -> count as present-like
            } else {
                // fallback: if there's a marks/remark but no status, treat as recorded day (present-like)
                String marks = safeString(studentNode.child("marks").getValue(String.class));
                if (marks.isEmpty()) marks = safeString(studentNode.child("remark").getValue(String.class));
                if (!marks.isEmpty()) {
                    t.present++;
                } else {
                    // saved student node without status/marks -> count as recorded day (present-like)
                    t.present++;
                }
            }
        }

        // increment totalDays for this date (one per date)
        t.totalDays += 1;
    }

    // Build totals from AttendanceSummary student-level snapshot
    private void buildTotalsFromStudentSnapshot(DataSnapshot snap) {
        final List<TotalsRow> rows = new ArrayList<>();
        final List<String> idsToResolve = new ArrayList<>();
        for (DataSnapshot s : snap.getChildren()) {
            String sid = s.getKey();
            if (sid == null) continue;
            String name = safeString(s.child("studentName").getValue(String.class));
            if (name.isEmpty()) name = safeString(s.child("name").getValue(String.class));
            if (name.isEmpty()) name = "(Unknown)";
            long present = safeLong(s.child("counts").child("Present").getValue());
            long late = safeLong(s.child("counts").child("Late").getValue());
            long excused = safeLong(s.child("counts").child("Excused").getValue());
            long absent = safeLong(s.child("counts").child("Absent").getValue());
            int totalDays = safeInt(s.child("totalDays").getValue());

            long sum = present + late + excused + absent;
            // If totalDays missing but counts exist, fallback to sum of counts so percentage and display make sense
            if (totalDays <= 0 && sum > 0) totalDays = (int) Math.min(Integer.MAX_VALUE, sum);

            int pct = computePercentage(present, late, excused, absent, totalDays);
            TotalsRow r = new TotalsRow(sid, name, present, late, excused, absent, totalDays, pct);
            rows.add(r);
            if (name == null || name.trim().isEmpty() || "(Unknown)".equals(name.trim())) idsToResolve.add(sid);
        }
        resolveNamesAndShow(rows, idsToResolve);
    }

    // Aggregate when AttendanceSummary snapshot is teacher-scoped
    private void aggregateTotalsAcrossTeachers(DataSnapshot snap) {
        Map<String, TotalsAccumulator> acc = new HashMap<>();
        for (DataSnapshot teacherNode : snap.getChildren()) {
            for (DataSnapshot s : teacherNode.getChildren()) {
                String sid = s.getKey();
                if (sid == null) continue;
                TotalsAccumulator t = acc.get(sid);
                if (t == null) {
                    t = new TotalsAccumulator();
                    t.id = sid;
                    t.name = safeString(s.child("studentName").getValue(String.class));
                    acc.put(sid, t);
                }
                DataSnapshot counts = s.child("counts");
                if (counts.exists()) {
                    t.present += safeLong(counts.child("Present").getValue());
                    t.late += safeLong(counts.child("Late").getValue());
                    t.excused += safeLong(counts.child("Excused").getValue());
                    t.absent += safeLong(counts.child("Absent").getValue());
                } else {
                    String status = safeString(s.child("status").getValue(String.class));
                    String marks = safeString(s.child("marks").getValue(String.class));
                    if (marks.isEmpty()) marks = safeString(s.child("remark").getValue(String.class));
                    if (!status.isEmpty()) {
                        if ("Present".equalsIgnoreCase(status)) t.present++;
                        else if ("Late".equalsIgnoreCase(status)) t.late++;
                        else if ("Excused".equalsIgnoreCase(status)) t.excused++;
                        else if ("Absent".equalsIgnoreCase(status)) t.absent++;
                        else t.present++;
                    } else if (!marks.isEmpty()) {
                        // If only a marks/remark exists but not a status, treat it as a present-like record
                        t.present++;
                    }
                }

                // totalDays handling: prefer explicit totalDays; otherwise increment by 1 for single-date nodes
                int nodeTotalDays = safeInt(s.child("totalDays").getValue());
                if (nodeTotalDays > 0) {
                    t.totalDays += nodeTotalDays;
                } else {
                    // If node looks like a single-day entry, count it as one day
                    if (s.hasChild("status") || s.hasChild("marks") || s.hasChild("remark") || s.hasChild("studentId")) {
                        t.totalDays += 1;
                    }
                }
            }
        }

        final List<TotalsRow> rows = new ArrayList<>();
        final List<String> idsToResolve = new ArrayList<>();
        for (TotalsAccumulator t : acc.values()) {
            long present = t.present;
            long late = t.late;
            long excused = t.excused;
            long absent = t.absent;
            int totalDays = t.totalDays;
            long sum = present + late + excused + absent;
            if (totalDays <= 0 && sum > 0) totalDays = (int) Math.min(Integer.MAX_VALUE, sum);
            int pct = computePercentage(present, late, excused, absent, totalDays);
            TotalsRow r = new TotalsRow(t.id, t.name, present, late, excused, absent, totalDays, pct);
            rows.add(r);
            if (t.name == null || t.name.trim().isEmpty() || "(Unknown)".equals(t.name.trim())) idsToResolve.add(t.id);
        }
        resolveNamesAndShow(rows, idsToResolve);
    }

    private void resolveNamesAndShow(final List<TotalsRow> rows, final List<String> idsToResolve) {
        if (idsToResolve.isEmpty()) {
            Collections.sort(rows, (a, b) -> a.name.compareToIgnoreCase(b.name));
            showTotalsDialog(rows);
            return;
        }

        final AtomicInteger remaining = new AtomicInteger(idsToResolve.size());
        for (String sid : idsToResolve) {
            studentsRef.child(sid).get().addOnCompleteListener(studentTask -> {
                if (studentTask.isSuccessful() && studentTask.getResult() != null && studentTask.getResult().exists()) {
                    DataSnapshot sSnap = studentTask.getResult();
                    String fullName = safeString(sSnap.child("fullName").getValue(String.class));
                    if (fullName.isEmpty()) fullName = safeString(sSnap.child("full_name").getValue(String.class));
                    if (fullName.isEmpty()) fullName = safeString(sSnap.child("fullNameDisplay").getValue(String.class));
                    if (fullName.isEmpty()) fullName = safeString(sSnap.child("name").getValue(String.class));
                    if (fullName.isEmpty()) {
                        String fn = safeString(sSnap.child("firstName").getValue(String.class));
                        String ln = safeString(sSnap.child("lastName").getValue(String.class));
                        if (!fn.isEmpty() || !ln.isEmpty()) fullName = (fn + " " + ln).trim();
                    }
                    if (!fullName.isEmpty()) {
                        synchronized (rows) {
                            for (TotalsRow rr : rows) {
                                if (rr.id.equals(sid)) rr.name = fullName;
                            }
                        }
                    }
                }
                if (remaining.decrementAndGet() == 0) {
                    Collections.sort(rows, (a, b) -> a.name.compareToIgnoreCase(b.name));
                    runOnUiThread(() -> showTotalsDialog(rows));
                }
            });
        }
    }

    private void showTotalsDialog(List<TotalsRow> rows) {
        View dlg = LayoutInflater.from(this).inflate(R.layout.dialog_totals, null);
        RecyclerView rv = dlg.findViewById(R.id.dialogTotalsRv);
        rv.setLayoutManager(new LinearLayoutManager(this));
        TotalsAdapter totalsAdapter = new TotalsAdapter(rows);
        rv.setAdapter(totalsAdapter);
        String title = (teacherId != null) ? "Class Totals (your schedule)" : "Class Totals (combined)";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dlg)
                .setPositiveButton("OK", null)
                .show();
    }

    // ---------- CSV export ----------
    private void exportCsv() {
        if (items.isEmpty()) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "Attendance CSV");
            share.putExtra(Intent.EXTRA_TEXT, "No attendance data available for export.");
            startActivity(Intent.createChooser(share, "Share CSV"));
            return;
        }

        StringJoiner sj = new StringJoiner("\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sj.add("No,StudentId,StudentName,Present,Late,Excused,Absent,Percentage,TotalDays,LastStatus,LastUpdated");
        int idx = 1;
        for (AttendanceSummaryModel m : items) {
            long present = m.counts.getOrDefault("Present", 0L);
            long late = m.counts.getOrDefault("Late", 0L);
            long excused = m.counts.getOrDefault("Excused", 0L);
            long absent = m.counts.getOrDefault("Absent", 0L);
            int totalDays = m.totalDays;
            int pct = computePercentageFromCounts(m.counts, totalDays);
            String lastUpdated = m.lastUpdated > 0 ? sdf.format(m.lastUpdated) : "";
            String nameEsc = "\"" + m.studentName.replace("\"", "\"\"") + "\"";
            String line = idx + "," + m.studentId + "," + nameEsc + "," + present + "," + late + "," + excused + "," + absent + "," + pct + "," + totalDays + "," + m.lastStatus + "," + lastUpdated;
            sj.add(line);
            idx++;
        }

        String subject = "Attendance CSV: " + sectionId + (teacherId != null ? " (teacher: " + teacherId + ")" : " (combined)");
        String csv = sj.toString();
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/csv");
        share.putExtra(Intent.EXTRA_SUBJECT, subject);
        share.putExtra(Intent.EXTRA_TEXT, csv);
        startActivity(Intent.createChooser(share, "Share CSV"));
    }

    // ---------- Utilities ----------
    private static int safeInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static long safeLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return 0L;
        }
    }

    static String safeString(String s) {
        return s == null ? "" : s;
    }

    // ---------- Internal models / adapters ----------
    private static class AttendanceSummaryModel {
        final String studentId;
        final String studentName;
        final int attendancePercentage;
        final int totalDays;
        final Map<String, Long> counts;
        final long lastUpdated;
        final String lastStatus;

        AttendanceSummaryModel(String studentId, String studentName, int attendancePercentage, int totalDays, Map<String, Long> counts, long lastUpdated, String lastStatus) {
            this.studentId = studentId;
            this.studentName = (studentName == null || studentName.trim().isEmpty()) ? "(Unknown)" : studentName;
            this.attendancePercentage = attendancePercentage;
            this.totalDays = totalDays;
            this.counts = counts != null ? counts : new HashMap<>();
            this.lastUpdated = lastUpdated;
            this.lastStatus = lastStatus;
        }
    }

    private static class DayRecord {
        final String id;
        final String name;
        final String status;

        DayRecord(String id, String name, String status) {
            this.id = id;
            this.name = name;
            this.status = status;
        }
    }

    private static class TotalsRow {
        final String id;
        String name;
        final long present, late, excused, absent;
        final int days, pct;

        TotalsRow(String id, String name, long present, long late, long excused, long absent, int days, int pct) {
            this.id = id;
            this.name = (name == null || name.trim().isEmpty()) ? "(Unknown)" : name;
            this.present = present;
            this.late = late;
            this.excused = excused;
            this.absent = absent;
            this.days = days;
            this.pct = pct;
        }
    }

    private static class TotalsAccumulator {
        String id;
        String name;
        long present = 0, late = 0, excused = 0, absent = 0;
        int totalDays = 0;
    }

    // DayAdapter / DayVH
    private static class DayAdapter extends RecyclerView.Adapter<DayVH> {
        private final List<DayRecord> data;
        DayAdapter(List<DayRecord> items) { data = items != null ? new ArrayList<>(items) : new ArrayList<>(); }
        void setItems(List<DayRecord> items) { data.clear(); if (items != null) data.addAll(items); notifyDataSetChanged(); }
        @NonNull @Override public DayVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_day_record, parent, false);
            return new DayVH(v);
        }
        @Override public void onBindViewHolder(@NonNull DayVH holder, int position) { holder.bind(data.get(position)); }
        @Override public int getItemCount() { return data.size(); }
    }
    private static class DayVH extends RecyclerView.ViewHolder {
        private final TextView tvName, tvId, tvStatus;
        DayVH(@NonNull View itemView) { super(itemView); tvName = itemView.findViewById(R.id.tvDayStudentName); tvId = itemView.findViewById(R.id.tvDayStudentId); tvStatus = itemView.findViewById(R.id.tvDayStatus); }
        void bind(DayRecord r) { tvName.setText(r.name); tvId.setText(r.id); tvStatus.setText(r.status == null || r.status.isEmpty() ? "Not Marked" : r.status); }
    }

    private static class TotalsAdapter extends RecyclerView.Adapter<TotalsVH> {
        private final List<TotalsRow> data;
        TotalsAdapter(List<TotalsRow> items) { data = items != null ? new ArrayList<>(items) : new ArrayList<>(); }
        @NonNull @Override public TotalsVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_totals_row, parent, false);
            return new TotalsVH(v);
        }
        @Override public void onBindViewHolder(@NonNull TotalsVH holder, int position) { holder.bind(data.get(position)); }
        @Override public int getItemCount() { return data.size(); }
    }
    private static class TotalsVH extends RecyclerView.ViewHolder {
        private final TextView tvName, tvCounts;
        TotalsVH(@NonNull View itemView) { super(itemView); tvName = itemView.findViewById(R.id.tvTotalsName); tvCounts = itemView.findViewById(R.id.tvTotalsCounts); }
        void bind(TotalsRow r) { tvName.setText(r.name + " (" + r.id + ")"); tvCounts.setText(String.format(Locale.getDefault(), "P:%d L:%d E:%d A:%d Days:%d %d%%", r.present, r.late, r.excused, r.absent, r.days, r.pct)); }
    }

    private static class SummaryAdapter extends RecyclerView.Adapter<SummaryVH> {
        private final List<AttendanceSummaryModel> data;
        private final RowScrollRegistrar registrar;
        interface RowScrollRegistrar { void register(ObservableHorizontalScrollView row); }
        SummaryAdapter(List<AttendanceSummaryModel> initial, RowScrollRegistrar registrar) { this.data = initial != null ? new ArrayList<>(initial) : new ArrayList<>(); this.registrar = registrar; }
        void setItems(List<AttendanceSummaryModel> items) { data.clear(); if (items != null) data.addAll(items); notifyDataSetChanged(); }
        @NonNull @Override public SummaryVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance_summary, parent, false);
            return new SummaryVH(v, registrar);
        }
        @Override public void onBindViewHolder(@NonNull SummaryVH holder, int position) { holder.bind(data.get(position), position + 1); }
        @Override public int getItemCount() { return data.size(); }
    }
    private static class SummaryVH extends RecyclerView.ViewHolder {
        private final TextView tvNo, tvStudent, tvP, tvL, tvE, tvA, tvPercent;
        SummaryVH(@NonNull View itemView, SummaryAdapter.RowScrollRegistrar registrar) {
            super(itemView);
            tvNo = itemView.findViewById(R.id.row_no);
            tvStudent = itemView.findViewById(R.id.row_student);
            tvP = itemView.findViewById(R.id.row_present);
            tvL = itemView.findViewById(R.id.row_late);
            tvE = itemView.findViewById(R.id.row_excused);
            tvA = itemView.findViewById(R.id.row_absent);
            tvPercent = itemView.findViewById(R.id.row_percentage);
            ObservableHorizontalScrollView rowHsv = itemView.findViewById(R.id.row_hsv);
            if (rowHsv != null && registrar != null) registrar.register(rowHsv);
        }
        void bind(AttendanceSummaryModel m, int index) {
            tvNo.setText(String.valueOf(index));
            tvStudent.setText(m.studentName + " (" + m.studentId + ")");
            tvP.setText(String.valueOf(m.counts.getOrDefault("Present", 0L)));
            tvL.setText(String.valueOf(m.counts.getOrDefault("Late", 0L)));
            tvE.setText(String.valueOf(m.counts.getOrDefault("Excused", 0L)));
            tvA.setText(String.valueOf(m.counts.getOrDefault("Absent", 0L)));
            int pct = computePercentageFromCounts(m.counts, m.totalDays);
            tvPercent.setText(pct + "%");
        }
    }
}