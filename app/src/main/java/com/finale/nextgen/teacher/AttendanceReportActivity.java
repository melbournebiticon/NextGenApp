package com.finale.nextgen.teacher;


import static com.finale.nextgen.teacher.StudentRecordActivity.formatDate;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.finale.nextgen.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;


/**
 * AttendanceReportActivity — updated per user's request:
 * - Keep existing aggregation logic unchanged.
 * - Use attendance fallback key to read data (unchanged).
 * - When exporting/displaying section in CSV/PDF, strip any "fallback:" prefix so the CSV section column shows e.g. "bsit-ba-1-a".
 * - Resolve teacher full name preferring the "fullName" (or similar) field so it shows "CHELSIE MAMARIL" not "C.MAMARIL".
 * - Ensure percentage values used in export are computed from the totals (no accidental inner-method overrides).
 *
 * Minimal changes: sanitize export section, prefer teacher.fullName, and remove accidental local overrides of percentage functions.
 */
public class AttendanceReportActivity extends AppCompatActivity {
    private String sectionId;
    private String sectionFallbackKey; // optional fallback key
    private DatabaseReference studentsRef;
    private DatabaseReference teachersRef;
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
    private String teacherFullName = ""; // resolved display name for exports and titles


    private static final Pattern DATE_KEY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");


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
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");


        // Use fallback key to read data if present (unchanged), but when exporting we will sanitize for display.
        String attendanceChildKey = !TextUtils.isEmpty(sectionFallbackKey) ? sectionFallbackKey : sectionId;
        attendanceRoot = FirebaseDatabase.getInstance().getReference("Attendance").child(attendanceChildKey);


        adapter = new SummaryAdapter(items, this::registerRowScroll);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);


        // wire export to options dialog (lets user choose CSV or PDF)
        if (btnExport != null) btnExport.setOnClickListener(v -> showExportOptionsDialog());


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


        resolveTeacherFullName();
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


    // ---------- Teacher resolution (prefer fullName) ----------
    private void resolveTeacherFullName() {
        teacherFullName = "";
        if (teacherId == null || teacherId.isEmpty()) return;


        // Prefer "fullName" (or fullName-like fields) over displayName.
        teachersRef.child(teacherId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                DataSnapshot snap = task.getResult();
                String full = safeString(snap.child("fullName").getValue(String.class));
                if (full.isEmpty()) full = safeString(snap.child("full_name").getValue(String.class));
                if (full.isEmpty()) full = safeString(snap.child("name").getValue(String.class));
                if (full.isEmpty()) full = safeString(snap.child("displayName").getValue(String.class));
                if (!full.isEmpty()) {
                    teacherFullName = full;
                    android.util.Log.d("AttendanceDebug", "Resolved teacherFullName from Teachers/<id>: " + teacherFullName);
                    return;
                }
            }
            // Fallback: maybe teacherId is a uid; search Teachers where uid == teacherId
            teachersRef.orderByChild("uid").equalTo(teacherId).get().addOnCompleteListener(qtask -> {
                if (qtask.isSuccessful() && qtask.getResult() != null && qtask.getResult().exists()) {
                    for (DataSnapshot child : qtask.getResult().getChildren()) {
                        String full = safeString(child.child("fullName").getValue(String.class));
                        if (full.isEmpty()) full = safeString(child.child("full_name").getValue(String.class));
                        if (full.isEmpty()) full = safeString(child.child("name").getValue(String.class));
                        if (full.isEmpty()) full = safeString(child.child("displayName").getValue(String.class));
                        if (!full.isEmpty()) {
                            teacherFullName = full;
                            android.util.Log.d("AttendanceDebug", "Resolved teacherFullName by uid query: " + teacherFullName);
                            break;
                        }
                    }
                } else {
                    teacherFullName = teacherId;
                }
            });
        });
    }


    // ---------- Export options ----------
    private void showExportOptionsDialog() {
        final String[] options = new String[] { "CSV (for Excel/Sheets/Docs)", "Excel (CSV mime)", "PDF (print-ready)" };
        new AlertDialog.Builder(this)
                .setTitle("Export attendance")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            exportAggregatedAs("text/csv", ".csv");
                            break;
                        case 1:
                            exportAggregatedAs("application/vnd.ms-excel", ".csv");
                            break;
                        case 2:
                            exportAggregatedAsPdf();
                            break;
                    }
                })
                .show();
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


    // ---------- Day snapshot loaders ----------
    private void loadSingleDaySnapshotIntoAdapter(final String dateKey, final DayAdapter dayAdapter, final TextView infoView) {
        if (attendanceRoot == null) return;
        resolveDateNode(dateKey, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DayRecord> list = new ArrayList<>();
                final List<String> idsToResolve = new ArrayList<>();
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
                            if (name.isEmpty()) {
                                // we'll resolve from Students/ later
                                name = "(Unknown)";
                                idsToResolve.add(sid);
                            }
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
                                if (name.isEmpty()) {
                                    name = "(Unknown)";
                                    idsToResolve.add(sid);
                                }
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


                // Resolve missing names from Students and update adapter
                if (!idsToResolve.isEmpty()) {
                    final AtomicInteger remaining = new AtomicInteger(idsToResolve.size());
                    for (String sid : idsToResolve) {
                        studentsRef.child(sid).get().addOnCompleteListener(task -> {
                            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                                DataSnapshot sSnap = task.getResult();
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
                                    synchronized (finalList) {
                                        for (DayRecord dr : finalList) {
                                            if (dr.id.equals(sid)) {
                                                int idx = finalList.indexOf(dr);
                                                if (idx >= 0) finalList.set(idx, new DayRecord(dr.id, fullName, dr.status));
                                            }
                                        }
                                    }
                                }
                            }
                            if (remaining.decrementAndGet() == 0) {
                                Collections.sort(finalList, (a, b) -> a.name.compareToIgnoreCase(b.name));
                                dayAdapter.setItems(finalList);
                            }
                        });
                    }
                }
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
                final List<String> idsToResolve = new ArrayList<>();
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
                            boolean needResolve = false;
                            if (studentName.isEmpty()) {
                                studentName = "(Unknown)";
                                needResolve = true;
                            }
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
                            if (needResolve) idsToResolve.add(studentId);
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
                                boolean needResolve = false;
                                if (studentName.isEmpty()) {
                                    studentName = "(Unknown)";
                                    needResolve = true;
                                }
                                Map<String, Long> counts = new HashMap<>();
                                counts.put("Present", status.equalsIgnoreCase("Present") ? 1L : 0L);
                                counts.put("Late", status.equalsIgnoreCase("Late") ? 1L : 0L);
                                counts.put("Excused", status.equalsIgnoreCase("Excused") ? 1L : 0L);
                                counts.put("Absent", status.equalsIgnoreCase("Absent") ? 1L : 0L);
                                int pct = computePercentageFromCounts(counts, 1);
                                String combinedStatus = status;
                                if (!marks.isEmpty()) combinedStatus = (combinedStatus.isEmpty() ? marks : (combinedStatus + " — " + marks));
                                map.put(studentId, new AttendanceSummaryModel(studentId, studentName, pct, 1, counts, 0L, combinedStatus.isEmpty() ? "Not Marked" : combinedStatus));
                                if (needResolve) idsToResolve.add(studentId);
                            }
                        }
                        dayItems.addAll(map.values());
                    }
                }
                Collections.sort(dayItems, (o1, o2) -> o1.studentName.compareToIgnoreCase(o2.studentName));
                adapter.setItems(dayItems);
                if (tvDateInfo != null) tvDateInfo.setText(String.format(Locale.getDefault(), "%s — %d record(s)", dateKey, dayItems.size()));


                if (!idsToResolve.isEmpty()) {
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
                                    synchronized (items) {
                                        for (int i = 0; i < items.size(); i++) {
                                            AttendanceSummaryModel mm = items.get(i);
                                            if (mm.studentId.equals(sid)) {
                                                AttendanceSummaryModel newModel = new AttendanceSummaryModel(mm.studentId, fullName, mm.attendancePercentage, mm.totalDays, mm.counts, mm.lastUpdated, mm.lastStatus);
                                                items.set(i, newModel);
                                            }
                                        }
                                    }
                                }
                            }
                            if (remaining.decrementAndGet() == 0) {
                                runOnUiThread(() -> adapter.setItems(new ArrayList<>(items)));
                            }
                        });
                    }
                }
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
    }


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


            for (DataSnapshot childNode : sectionSnap.getChildren()) {
                String childKey = childNode.getKey();
                if (childKey == null) continue;


                if (DATE_KEY.matcher(childKey).matches()) {
                    dateNodesFound++;
                    processStudentsUnderDate(childNode, childKey, acc, seenByDate);
                } else {
                    String teacherKey = childKey;
                    if (teacherId != null && !teacherId.isEmpty() && !teacherKey.equals(teacherId)) {
                        continue;
                    }
                    for (DataSnapshot dateNode : childNode.getChildren()) {
                        String dateKey = dateNode.getKey();
                        if (dateKey != null && DATE_KEY.matcher(dateKey).matches()) {
                            dateNodesFound++;
                            processStudentsUnderDate(dateNode, dateKey, acc, seenByDate);
                        }
                    }
                }
            }


            final List<TotalsRow> rows = new ArrayList<>();
            final List<String> idsToResolve = new ArrayList<>();


            for (TotalsAccumulator t : acc.values()) {
                int totalDays = t.totalDays;
                long sum = t.present + t.late + t.excused + t.absent;
                if (totalDays <= 0 && sum > 0) totalDays = (int) Math.min(Integer.MAX_VALUE, sum);
                int pct = computePercentage(t.present, t.late, t.excused, t.absent, totalDays);
                TotalsRow r = new TotalsRow(t.id, t.name, t.present, t.late, t.excused, t.absent, totalDays, pct);
                rows.add(r);
                if (t.name == null || t.name.trim().isEmpty() || "(Unknown)".equals(t.name.trim())) idsToResolve.add(t.id);
            }


            if (rows.isEmpty()) {
                Toast.makeText(this, "No student attendance records found.", Toast.LENGTH_SHORT).show();
                return;
            }


            resolveNamesAndShow(rows, idsToResolve);
        });
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
        String title = (!TextUtils.isEmpty(teacherFullName)) ? "Class Totals (" + teacherFullName + ")" : "Class Totals";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dlg)
                .setPositiveButton("OK", null)
                .show();
    }


    // ---------- processStudentsUnderDate (unchanged) ----------
    private void processStudentsUnderDate(DataSnapshot dateNode, String dateKey,
                                          Map<String, TotalsAccumulator> acc,
                                          Map<String, java.util.Set<String>> seenByDate) {


        java.util.Set<String> seenThisDate = seenByDate.computeIfAbsent(dateKey, d -> new java.util.HashSet<>());


        for (DataSnapshot studentNode : dateNode.getChildren()) {
            if (studentNode == null) continue;
            String studentKey = studentNode.getKey();
            if (studentKey == null) continue;


            if (seenThisDate.contains(studentKey)) continue;
            seenThisDate.add(studentKey);


            TotalsAccumulator t = acc.get(studentKey);
            if (t == null) {
                t = new TotalsAccumulator();
                t.id = studentKey;
                acc.put(studentKey, t);
            }


            String studentName = safeString(studentNode.child("studentName").getValue(String.class));
            if (studentName.isEmpty()) {
                studentName = safeString(studentNode.child("studentFullName").getValue(String.class));
            }
            if (!studentName.isEmpty() && (t.name == null || "(Unknown)".equals(t.name))) {
                t.name = studentName;
            }


            String status = safeString(studentNode.child("status").getValue(String.class));
            if (status.isEmpty()) status = safeString(studentNode.child("attendanceStatus").getValue(String.class));


            if ("Present".equalsIgnoreCase(status)) {
                t.present++;
            } else if ("Late".equalsIgnoreCase(status)) {
                t.late++;
            } else if ("Excused".equalsIgnoreCase(status)) {
                t.excused++;
            } else if ("Absent".equalsIgnoreCase(status)) {
                t.absent++;
            } else {
                String marks = safeString(studentNode.child("marks").getValue(String.class));
                if (marks.isEmpty()) marks = safeString(studentNode.child("remark").getValue(String.class));
                if (!marks.isEmpty()) t.present++;
                else t.present++;
            }


            t.totalDays++;
        }
    }


    // ---------- Export implementation that writes to Downloads and opens file ----------
    private void exportAggregatedAs(String mimeType, String extension) {
        if (attendanceRoot == null) {
            Toast.makeText(this, "Attendance root not configured.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Preparing export...", Toast.LENGTH_SHORT).show();


        attendanceRoot.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Toast.makeText(this, "Failed to read Attendance for export.", Toast.LENGTH_SHORT).show();
                return;
            }
            DataSnapshot sectionSnap = task.getResult();
            if (!sectionSnap.exists()) {
                Toast.makeText(this, "No attendance data available for export.", Toast.LENGTH_SHORT).show();
                return;
            }


            final Map<String, TotalsAccumulator> acc = new HashMap<>();
            final Map<String, java.util.Set<String>> seenByDate = new HashMap<>();
            for (DataSnapshot childNode : sectionSnap.getChildren()) {
                String childKey = childNode.getKey();
                if (childKey == null) continue;
                if (DATE_KEY.matcher(childKey).matches()) {
                    processStudentsUnderDate(childNode, childKey, acc, seenByDate);
                } else {
                    String teacherKey = childKey;
                    if (teacherId != null && !teacherId.isEmpty() && !teacherKey.equals(teacherId)) {
                        continue;
                    }
                    for (DataSnapshot dateNode : childNode.getChildren()) {
                        String dateKey = dateNode.getKey();
                        if (dateKey != null && DATE_KEY.matcher(dateKey).matches()) {
                            processStudentsUnderDate(dateNode, dateKey, acc, seenByDate);
                        }
                    }
                }
            }


            if (acc.isEmpty()) {
                Toast.makeText(this, "No student attendance records found for export.", Toast.LENGTH_SHORT).show();
                return;
            }


            final List<TotalsRow> rows = new ArrayList<>();
            final List<String> idsToResolve = new ArrayList<>();
            for (TotalsAccumulator t : acc.values()) {
                int totalDays = t.totalDays;
                long sum = t.present + t.late + t.excused + t.absent;
                if (totalDays <= 0 && sum > 0) totalDays = (int) Math.min(Integer.MAX_VALUE, sum);
                int pct = computePercentage(t.present, t.late, t.excused, t.absent, totalDays);
                TotalsRow r = new TotalsRow(t.id, t.name, t.present, t.late, t.excused, t.absent, totalDays, pct);
                rows.add(r);
                if (t.name == null || t.name.trim().isEmpty() || "(Unknown)".equals(t.name.trim())) idsToResolve.add(t.id);
            }


            if (idsToResolve.isEmpty()) {
                try {
                    byte[] bytes = buildCsvBytes(rows);
                    String displayName = "attendance_" + sanitizeSectionForFilename(sectionId) + "_" + System.currentTimeMillis() + extension;
                    saveBytesToDownloadsAndOpen(bytes, displayName, mimeType);
                } catch (Exception ex) {
                    Toast.makeText(this, "Export failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                }
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
                        try {
                            byte[] bytes = buildCsvBytes(rows);
                            String displayName = "attendance_" + sanitizeSectionForFilename(sectionId) + "_" + System.currentTimeMillis() + extension;
                            saveBytesToDownloadsAndOpen(bytes, displayName, mimeType);
                        } catch (Exception ex) {
                            Toast.makeText(this, "Export failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }


    private byte[] buildCsvBytes(List<TotalsRow> rows) {
        Collections.sort(rows, (a, b) -> a.name.compareToIgnoreCase(b.name));
        StringBuilder sb = new StringBuilder();
        sb.append("StudentId,StudentName,Section,TeacherFullName,Present,Late,Excused,Absent,TotalDays,Percentage\n");
        String sectionForExport = sanitizeSectionForExport(sectionId);
        String teacherForExport = !TextUtils.isEmpty(teacherFullName) ? teacherFullName : (teacherId != null ? teacherId : "");
        for (TotalsRow r : rows) {
            // recompute percentage to ensure it's 0-100 and not an accidental "1"
            int pct = computePercentage(r.present, r.late, r.excused, r.absent, r.days);
            String nameEsc = "\"" + r.name.replace("\"", "\"\"") + "\"";
            String sectionEsc = "\"" + sectionForExport.replace("\"", "\"\"") + "\"";
            String teacherEsc = "\"" + teacherForExport.replace("\"", "\"\"") + "\"";
            String line = r.id + "," + nameEsc + "," + sectionEsc + "," + teacherEsc + "," +
                    r.present + "," + r.late + "," + r.excused + "," + r.absent + "," + r.days + "," + pct;
            sb.append(line).append("\n");
        }
        // add UTF-8 BOM at beginning
        byte[] bom = new byte[] { (byte)0xEF, (byte)0xBB, (byte)0xBF };
        byte[] csvBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(csvBytes, 0, out, bom.length, csvBytes.length);
        return out;
    }


    private void exportAggregatedAsPdf() {
        if (attendanceRoot == null) {
            Toast.makeText(this, "Attendance root not configured.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Preparing PDF export...", Toast.LENGTH_SHORT).show();


        attendanceRoot.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Toast.makeText(this, "Failed to read Attendance for export.", Toast.LENGTH_SHORT).show();
                return;
            }
            DataSnapshot sectionSnap = task.getResult();
            if (!sectionSnap.exists()) {
                Toast.makeText(this, "No attendance data available for export.", Toast.LENGTH_SHORT).show();
                return;
            }


            final Map<String, TotalsAccumulator> acc = new HashMap<>();
            final Map<String, java.util.Set<String>> seenByDate = new HashMap<>();
            for (DataSnapshot childNode : sectionSnap.getChildren()) {
                String childKey = childNode.getKey();
                if (childKey == null) continue;
                if (DATE_KEY.matcher(childKey).matches()) {
                    processStudentsUnderDate(childNode, childKey, acc, seenByDate);
                } else {
                    String teacherKey = childKey;
                    if (teacherId != null && !teacherId.isEmpty() && !teacherKey.equals(teacherId)) {
                        continue;
                    }
                    for (DataSnapshot dateNode : childNode.getChildren()) {
                        String dateKey = dateNode.getKey();
                        if (dateKey != null && DATE_KEY.matcher(dateKey).matches()) {
                            processStudentsUnderDate(dateNode, dateKey, acc, seenByDate);
                        }
                    }
                }
            }


            if (acc.isEmpty()) {
                Toast.makeText(this, "No student attendance records found for export.", Toast.LENGTH_SHORT).show();
                return;
            }


            final List<TotalsRow> rows = new ArrayList<>();
            final List<String> idsToResolve = new ArrayList<>();
            for (TotalsAccumulator t : acc.values()) {
                int totalDays = t.totalDays;
                long sum = t.present + t.late + t.excused + t.absent;
                if (totalDays <= 0 && sum > 0) totalDays = (int) Math.min(Integer.MAX_VALUE, sum);
                int pct = computePercentage(t.present, t.late, t.excused, t.absent, totalDays);
                TotalsRow r = new TotalsRow(t.id, t.name, t.present, t.late, t.excused, t.absent, totalDays, pct);
                rows.add(r);
                if (t.name == null || t.name.trim().isEmpty() || "(Unknown)".equals(t.name.trim())) idsToResolve.add(t.id);
            }


            if (idsToResolve.isEmpty()) {
                try {
                    byte[] pdfBytes = buildPdfBytes(rows);
                    String displayName = "attendance_" + sanitizeSectionForFilename(sectionId) + "_" + System.currentTimeMillis() + ".pdf";
                    saveBytesToDownloadsAndOpen(pdfBytes, displayName, "application/pdf");
                } catch (Exception ex) {
                    Toast.makeText(this, "PDF export failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                }
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
                        try {
                            byte[] pdfBytes = buildPdfBytes(rows);
                            String displayName = "attendance_" + sanitizeSectionForFilename(sectionId) + "_" + System.currentTimeMillis() + ".pdf";
                            saveBytesToDownloadsAndOpen(pdfBytes, displayName, "application/pdf");
                        } catch (Exception ex) {
                            Toast.makeText(this, "PDF export failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }


    private byte[] buildPdfBytes(List<TotalsRow> rows) throws Exception {
        Collections.sort(rows, (a, b) -> a.name.compareToIgnoreCase(b.name));
        PdfDocument document = new PdfDocument();
        Paint paint = new Paint();
        paint.setTextSize(12);


        final int pageWidth = 595;
        final int pageHeight = 842;
        final int margin = 36;
        final int lineHeight = 18;
        int y = margin;


        PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create());
        Canvas canvas = page.getCanvas();


        paint.setTextSize(14);
        String title = "Attendance Report - " + sanitizeSectionForExport(sectionId);
        canvas.drawText(title, margin, y, paint);
        y += lineHeight * 2;


        paint.setTextSize(11);
        String subtitle = "Teacher: " + (!TextUtils.isEmpty(teacherFullName) ? teacherFullName : (teacherId != null ? teacherId : ""));
        canvas.drawText(subtitle, margin, y, paint);
        y += lineHeight * 2;


        paint.setFakeBoldText(true);
        String header = String.format("%-14s %-30s %5s %5s %5s %5s %6s %6s", "StudentId", "StudentName", "P", "L", "E", "A", "Days", "%");
        canvas.drawText(header, margin, y, paint);
        y += lineHeight;
        paint.setFakeBoldText(false);


        int pageNumber = 1;
        for (TotalsRow r : rows) {
            if (y + lineHeight > pageHeight - margin) {
                document.finishPage(page);
                pageNumber++;
                page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
                canvas = page.getCanvas();
                y = margin;
            }
            int pct = computePercentage(r.present, r.late, r.excused, r.absent, r.days);
            String nameTrim = r.name.length() > 28 ? r.name.substring(0, 28) + "…" : r.name;
            String line = String.format(Locale.getDefault(), "%-14s %-30s %5d %5d %5d %5d %6d %6d",
                    r.id, nameTrim, r.present, r.late, r.excused, r.absent, r.days, pct);
            canvas.drawText(line, margin, y, paint);
            y += lineHeight;
        }


        document.finishPage(page);


        File tmp = File.createTempFile("attendance_pdf_tmp", ".pdf", getCacheDir());
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            document.writeTo(fos);
        }
        document.close();


        byte[] bytes = new byte[(int) tmp.length()];
        java.io.FileInputStream fis = new java.io.FileInputStream(tmp);
        fis.read(bytes);
        fis.close();
        tmp.delete();
        return bytes;
    }


    private void saveBytesToDownloadsAndOpen(byte[] data, String displayName, String mimeType) {
        try {
            Uri uri = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
                cv.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NextGenAttendance");
                uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cv);
                if (uri == null) throw new Exception("Failed to create file in Downloads");
                try (OutputStream out = resolver.openOutputStream(uri)) {
                    out.write(data);
                    out.flush();
                }
            } else {
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File dir = new File(downloads, "NextGenAttendance");
                if (!dir.exists()) dir.mkdirs();
                File outFile = new File(dir, displayName);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(data);
                    fos.flush();
                }
                String authority = getPackageName() + ".fileprovider";
                uri = FileProvider.getUriForFile(this, authority, outFile);
            }


            if (uri == null) {
                Toast.makeText(this, "Failed to save file to Downloads.", Toast.LENGTH_LONG).show();
                return;
            }


            Intent open = new Intent(Intent.ACTION_VIEW);
            open.setDataAndType(uri, mimeType);
            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(open, "Open exported file with");
            startActivity(chooser);


            Toast.makeText(this, "Exported to Downloads/NextGenAttendance as " + displayName, Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            android.util.Log.e("AttendanceDebug", "saveBytesToDownloadsAndOpen failed: " + ex.getMessage(), ex);
            Toast.makeText(this, "Saving file failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    // ---------- Helpers ----------
    private static String sanitizeSectionForExport(String rawSection) {
        if (rawSection == null) return "";
        if (rawSection.startsWith("fallback:")) {
            return rawSection.substring("fallback:".length()).trim();
        }
        return rawSection.trim();
    }


    private static String sanitizeSectionForFilename(String rawSection) {
        String s = sanitizeSectionForExport(rawSection);
        if (s.isEmpty()) s = "section";
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
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
        String name = "(Unknown)";
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

