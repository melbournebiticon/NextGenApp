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

/**
 * AttendanceReportActivity
 *
 * Behavior:
 * - Prefers teacherId passed in Intent extras ("teacherId"). If absent, falls back to authenticated UID.
 * - Reads day snapshots from Attendance/{sectionId}/{teacherId}/{date} when teacherId is provided,
 *   otherwise from Attendance/{sectionId}/{date} (which may contain teacher child nodes).
 * - Reads totals from AttendanceSummary/{sectionId}/{teacherId} when teacherId is provided,
 *   otherwise reads AttendanceSummary/{sectionId} and aggregates across teacher children.
 *
 * - Percentage computation uses counts + totalDays (or derives totalDays from sum(counts) when missing).
 *   Weights: Present=100, Late=90, Excused=100, Absent=0.
 */
public class AttendanceReportActivity extends AppCompatActivity {

    private static final String TAG = "AttendanceReport";

    private String sectionId;
    private DatabaseReference summaryRef;     // AttendanceSummary/{sectionId}/{maybeTeacherId or aggregated}
    private DatabaseReference studentsRef;
    private DatabaseReference attendanceRef;  // Attendance/{sectionId}/{maybeTeacherId}

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

    // Weights: Present=100, Late=90, Excused=100, Absent=0
    private static final Map<String, Integer> WEIGHTS = new HashMap<>();
    static {
        WEIGHTS.put("Present", 100);
        WEIGHTS.put("Late", 90);
        WEIGHTS.put("Excused", 100);
        WEIGHTS.put("Absent", 0);
    }

    private String teacherId; // current teacher id if available or passed via intent

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
        String sectionDisplay = getIntent().getStringExtra("sectionDisplay");
        if (!TextUtils.isEmpty(sectionDisplay)) tvReportTitle.setText("Attendance Report — " + sectionDisplay);

        if (sectionId == null || sectionId.trim().isEmpty()) {
            tvReportTitle.setText("Attendance Report (section not provided)");
            return;
        }

        // Prefer teacherId passed by intent (StudentAttendanceActivity sets it), else try auth UID
        String teacherIdFromIntent = getIntent().getStringExtra("teacherId");
        if (!TextUtils.isEmpty(teacherIdFromIntent)) {
            teacherId = teacherIdFromIntent;
        } else {
            FirebaseUser cur = FirebaseAuth.getInstance().getCurrentUser();
            teacherId = (cur != null && !TextUtils.isEmpty(cur.getUid())) ? cur.getUid() : null;
        }

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        if (teacherId != null) {
            attendanceRef = FirebaseDatabase.getInstance().getReference("Attendance").child(sectionId).child(teacherId);
            summaryRef = FirebaseDatabase.getInstance().getReference("AttendanceSummary").child(sectionId).child(teacherId);
        } else {
            attendanceRef = FirebaseDatabase.getInstance().getReference("Attendance").child(sectionId);
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

    // Register a row's horizontal scroll view so it syncs with header and other rows
    private void registerRowScroll(ObservableHorizontalScrollView row) {
        if (row == null) return;
        if (rowScrolls.contains(row)) return;
        rowScrolls.add(row);
        row.setOnScrollChangedListener((src, x, y, oldx, oldy) -> {
            if (isSyncing) return;
            isSyncing = true;
            // scroll header if this row is not the header
            if (headerScroll != null && src != headerScroll) headerScroll.scrollTo(x, 0);
            for (ObservableHorizontalScrollView r : rowScrolls) {
                if (r != null && r != src) r.scrollTo(x, 0);
            }
            isSyncing = false;
        });
    }

    // ---------- Percentage helpers ----------
    private static int computePercentage(long present, long late, long excused, long absent, int totalDays) {
        if (totalDays <= 0) return 0;
        long weighted = present * WEIGHTS.get("Present")
                + late * WEIGHTS.get("Late")
                + excused * WEIGHTS.get("Excused")
                + absent * WEIGHTS.get("Absent");
        // weighted range: 0 .. totalDays * 100
        double avg = (double) weighted / (double) totalDays; // yields 0..100
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

    // ---------- Calendar + day snapshot (handles both teacher-scoped and section-scoped shapes) ----------
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

    private void loadSingleDaySnapshotIntoAdapter(final String dateKey, final DayAdapter dayAdapter, final TextView infoView) {
        if (attendanceRef == null) return;
        attendanceRef.child(dateKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
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
                            String name = safeString(s.child("studentName").getValue(String.class));
                            if (name.isEmpty()) name = "(Unknown)";
                            list.add(new DayRecord(sid, name, status));
                        }
                    } else {
                        // merge teacher children
                        for (DataSnapshot teacherNode : snapshot.getChildren()) {
                            for (DataSnapshot s : teacherNode.getChildren()) {
                                String sid = s.getKey();
                                if (sid == null) continue;
                                String status = safeString(s.child("status").getValue(String.class));
                                String name = safeString(s.child("studentName").getValue(String.class));
                                if (name.isEmpty()) name = "(Unknown)";
                                list.add(new DayRecord(sid, name, status));
                            }
                        }
                    }
                }
                // dedupe by student id, keep first occurrence
                Map<String, DayRecord> dedup = new HashMap<>();
                for (DayRecord r : list) if (!dedup.containsKey(r.id)) dedup.put(r.id, r);
                List<DayRecord> finalList = new ArrayList<>(dedup.values());
                Collections.sort(finalList, (a, b) -> a.name.compareToIgnoreCase(b.name));
                dayAdapter.setItems(finalList);
                if (infoView != null) infoView.setText(String.format(Locale.getDefault(), "%s — %d record(s)", dateKey, finalList.size()));
                if (tvDateInfo != null) tvDateInfo.setText(String.format(Locale.getDefault(), "%s — %d record(s)", dateKey, finalList.size()));
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
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
        if (attendanceRef == null) return;
        attendanceRef.child(dateKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
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
                            String studentName = safeString(s.child("studentName").getValue(String.class));
                            if (studentName.isEmpty()) studentName = "(Unknown)";

                            Map<String, Long> counts = new HashMap<>();
                            counts.put("Present", status.equalsIgnoreCase("Present") ? 1L : 0L);
                            counts.put("Late", status.equalsIgnoreCase("Late") ? 1L : 0L);
                            counts.put("Excused", status.equalsIgnoreCase("Excused") ? 1L : 0L);
                            counts.put("Absent", status.equalsIgnoreCase("Absent") ? 1L : 0L);

                            int pct = computePercentageFromCounts(counts, 1);

                            AttendanceSummaryModel model = new AttendanceSummaryModel(studentId, studentName, pct, 1, counts, 0L, status.isEmpty() ? "Not Marked" : status);
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
                                String studentName = safeString(s.child("studentName").getValue(String.class));
                                if (studentName.isEmpty()) studentName = "(Unknown)";
                                Map<String, Long> counts = new HashMap<>();
                                counts.put("Present", status.equalsIgnoreCase("Present") ? 1L : 0L);
                                counts.put("Late", status.equalsIgnoreCase("Late") ? 1L : 0L);
                                counts.put("Excused", status.equalsIgnoreCase("Excused") ? 1L : 0L);
                                counts.put("Absent", status.equalsIgnoreCase("Absent") ? 1L : 0L);
                                int pct = computePercentageFromCounts(counts, 1);
                                map.put(studentId, new AttendanceSummaryModel(studentId, studentName, pct, 1, counts, 0L, status.isEmpty() ? "Not Marked" : status));
                            }
                        }
                        dayItems.addAll(map.values());
                    }
                }
                Collections.sort(dayItems, (o1, o2) -> o1.studentName.compareToIgnoreCase(o2.studentName));
                adapter.setItems(dayItems);
                if (tvDateInfo != null) tvDateInfo.setText(String.format(Locale.getDefault(), "%s — %d record(s)", dateKey, dayItems.size()));
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                Toast.makeText(AttendanceReportActivity.this, "Failed to load attendance for " + dateKey, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------- Totals dialog (reads scoped summary or aggregates across teachers if needed) ----------
    private void showPerStudentTotalsDialog() {
        if (summaryRef == null) return;

        // summaryRef may point to AttendanceSummary/{section}/{teacherId} (student children)
        // or AttendanceSummary/{section} (teacher children -> student children)
        summaryRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Toast.makeText(this, "Failed to load summary", Toast.LENGTH_SHORT).show();
                return;
            }

            DataSnapshot snap = task.getResult();
            if (!snap.exists()) {
                Toast.makeText(this, "No summary data for this section (for current teacher or section-level).", Toast.LENGTH_SHORT).show();
                return;
            }

            // Detect shape: if first child has "counts" or "totalDays", it's student-level
            boolean studentLevel = false;
            for (DataSnapshot c : snap.getChildren()) {
                if (c.hasChild("counts") || c.hasChild("totalDays") || c.hasChild("attendancePercentage")) {
                    studentLevel = true;
                }
                break;
            }

            if (studentLevel) {
                // simple path: each child is a student summary
                buildTotalsFromStudentSnapshot(snap);
            } else {
                // aggregated path: snap children are teacher nodes; aggregate across them
                aggregateTotalsAcrossTeachers(snap);
            }
        });
    }

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

            int pct = computePercentage(present, late, excused, absent, totalDays);

            TotalsRow r = new TotalsRow(sid, name, present, late, excused, absent, totalDays, pct);
            rows.add(r);

            if (name == null || name.trim().isEmpty() || "(Unknown)".equals(name.trim())) idsToResolve.add(sid);
        }

        resolveNamesAndShow(rows, idsToResolve);
    }

    private void aggregateTotalsAcrossTeachers(DataSnapshot snap) {
        // snap children are teacher nodes; iterate each teacher and each student under them
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
                // add counts if present
                DataSnapshot counts = s.child("counts");
                if (counts.exists()) {
                    t.present += safeLong(counts.child("Present").getValue());
                    t.late += safeLong(counts.child("Late").getValue());
                    t.excused += safeLong(counts.child("Excused").getValue());
                    t.absent += safeLong(counts.child("Absent").getValue());
                } else {
                    // fallback: this node might be a single-day record (rare here) -> check status
                    String status = safeString(s.child("status").getValue(String.class));
                    if (!status.isEmpty()) {
                        if ("Present".equalsIgnoreCase(status)) t.present++;
                        else if ("Late".equalsIgnoreCase(status)) t.late++;
                        else if ("Excused".equalsIgnoreCase(status)) t.excused++;
                        else if ("Absent".equalsIgnoreCase(status)) t.absent++;
                    }
                }
                // accumulate totalDays if present
                t.totalDays += safeInt(s.child("totalDays").getValue());
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
            // if totalDays is zero, derive from counts
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
            String line = idx + "," + m.studentId + "," + nameEsc + "," + present + "," + late + "," + excused + "," + absent + "," +
                    pct + "," + totalDays + "," + m.lastStatus + "," + lastUpdated;
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
    private static int safeInt(Object o) { if (o == null) return 0; if (o instanceof Number) return ((Number) o).intValue(); try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; } }
    private static long safeLong(Object o) { if (o == null) return 0L; if (o instanceof Number) return ((Number) o).longValue(); try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return 0L; } }
    private static String safeString(String s) { return s == null ? "" : s; }

    // ---------- Internal models / adapters ----------
    private static class AttendanceSummaryModel {
        final String studentId;
        final String studentName;
        final int attendancePercentage;
        final int totalDays;
        final Map<String, Long> counts;
        final long lastUpdated;
        final String lastStatus;
        AttendanceSummaryModel(String studentId, String studentName, int attendancePercentage, int totalDays,
                               Map<String, Long> counts, long lastUpdated, String lastStatus) {
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
        DayRecord(String id, String name, String status) { this.id = id; this.name = name; this.status = status; }
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

    // TotalsAdapter / TotalsVH
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
        void bind(TotalsRow r) {
            tvName.setText(r.name + " (" + r.id + ")");
            tvCounts.setText(String.format(Locale.getDefault(), "P:%d L:%d E:%d A:%d Days:%d %d%%", r.present, r.late, r.excused, r.absent, r.days, r.pct));
        }
    }

    // SummaryAdapter / SummaryVH
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