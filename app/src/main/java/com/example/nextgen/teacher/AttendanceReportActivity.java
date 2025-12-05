package com.example.nextgen.teacher;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * AttendanceReportActivity - synchronized horizontal scrolling header + rows
 */
public class AttendanceReportActivity extends AppCompatActivity {

    private static final String TAG = "AttendanceReport";
    private String sectionId;
    private DatabaseReference summaryRef;
    private DatabaseReference studentsRef;
    private RecyclerView recyclerView;
    private SummaryAdapter adapter;
    private final List<AttendanceSummaryModel> items = new ArrayList<>();
    private TextView tvReportTitle;
    private TextView tvLastUpdated;
    private TextView btnExport;

    // header scroll view
    private ObservableHorizontalScrollView headerScroll;
    // track row scroll views registered by adapter
    private final List<ObservableHorizontalScrollView> rowScrolls = new ArrayList<>();
    private boolean isSyncing = false;

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
        headerScroll = findViewById(R.id.header_hsv); // make sure activity layout header HSV has this id

        sectionId = getIntent().getStringExtra("sectionId");
        String sectionDisplay = getIntent().getStringExtra("sectionDisplay");

        if (!TextUtils.isEmpty(sectionDisplay)) {
            tvReportTitle.setText("Attendance Report — " + sectionDisplay);
        } else {
            tvReportTitle.setText("Attendance Report");
        }

        if (sectionId == null || sectionId.trim().isEmpty()) {
            tvReportTitle.setText("Attendance Report (section not provided)");
            // no data to load
            return;
        }

        summaryRef = FirebaseDatabase.getInstance().getReference("AttendanceSummary").child(sectionId);
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        adapter = new SummaryAdapter(items, this::registerRowScroll);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // export button
        btnExport.setOnClickListener(v -> exportCsv());

        // header scroll listener -> sync to rows
        if (headerScroll != null) {
            headerScroll.setOnScrollChangedListener((src, x, y, oldx, oldy) -> {
                if (isSyncing) return;
                isSyncing = true;
                for (ObservableHorizontalScrollView r : rowScrolls) {
                    if (r != null) r.scrollTo(x, 0);
                }
                isSyncing = false;
            });
        }

        // listen for summary changes
        summaryRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                items.clear();

                long maxLastUpdated = 0L;
                final List<String> missingNames = new ArrayList<>();

                if (snapshot != null && snapshot.exists()) {
                    for (DataSnapshot s : snapshot.getChildren()) {
                        String studentId = s.getKey();
                        if (studentId == null) continue;

                        int attendancePercentage = safeInt(s.child("attendancePercentage").getValue());
                        int totalDays = safeInt(s.child("totalDays").getValue());

                        String studentName = safeString(s.child("studentName").getValue(String.class));
                        if (studentName.isEmpty()) studentName = safeString(s.child("name").getValue(String.class));
                        boolean nameMissing = studentName.isEmpty() || "(Unknown)".equals(studentName.trim());
                        if (nameMissing) {
                            studentName = "(Unknown)";
                            missingNames.add(studentId);
                        }

                        Map<String, Long> counts = new HashMap<>();
                        DataSnapshot countsSnap = s.child("counts");
                        counts.put("Present", safeLong(countsSnap.child("Present").getValue()));
                        counts.put("Late", safeLong(countsSnap.child("Late").getValue()));
                        counts.put("Excused", safeLong(countsSnap.child("Excused").getValue()));
                        counts.put("Absent", safeLong(countsSnap.child("Absent").getValue()));

                        long lu = 0L;
                        Object luObj = s.child("lastUpdated").getValue();
                        if (luObj instanceof Long) lu = (Long) luObj;
                        else if (luObj instanceof Double) lu = ((Double) luObj).longValue();
                        else if (luObj instanceof Integer) lu = ((Integer) luObj).longValue();

                        if (lu > maxLastUpdated) maxLastUpdated = lu;

                        String lastStatus = safeString(s.child("lastStatus").getValue(String.class));
                        if (lastStatus.isEmpty()) lastStatus = "Not Marked";

                        AttendanceSummaryModel model = new AttendanceSummaryModel(
                                studentId,
                                studentName,
                                attendancePercentage,
                                totalDays,
                                counts,
                                lu,
                                lastStatus
                        );
                        items.add(model);
                    }
                }

                // sort by lastUpdated desc
                Collections.sort(items, new Comparator<AttendanceSummaryModel>() {
                    @Override
                    public int compare(AttendanceSummaryModel o1, AttendanceSummaryModel o2) {
                        return Long.compare(o2.lastUpdated, o1.lastUpdated);
                    }
                });

                adapter.setItems(items);

                // update lastUpdated text
                if (maxLastUpdated > 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
                    tvLastUpdated.setText("Last updated: " + sdf.format(maxLastUpdated));
                } else {
                    tvLastUpdated.setText("Last updated: —");
                }

                // Fetch missing names and write them back to summary (so next load has names)
                if (!missingNames.isEmpty()) {
                    fetchAndPersistMissingNames(missingNames);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to load summary: " + error.getMessage());
                tvLastUpdated.setText("Last updated: —");
            }
        });
    }

    // Called by adapter to register each row's scroll view so we can sync them with header/other rows
    private void registerRowScroll(ObservableHorizontalScrollView row) {
        if (row == null) return;
        if (rowScrolls.contains(row)) return;
        rowScrolls.add(row);

        // row scroll listener -> propagate to header and other rows
        row.setOnScrollChangedListener((src, x, y, oldx, oldy) -> {
            if (isSyncing) return;
            isSyncing = true;
            // sync header
            if (headerScroll != null) headerScroll.scrollTo(x, 0);
            // sync other rows
            for (ObservableHorizontalScrollView r : rowScrolls) {
                if (r != null && r != src) r.scrollTo(x, 0);
            }
            isSyncing = false;
        });
    }

    private void fetchAndPersistMissingNames(List<String> missingIds) {
        for (String sid : missingIds) {
            if (sid == null) continue;
            studentsRef.child(sid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    if (snap == null || !snap.exists()) return;

                    // Try several common name fields
                    String fullName = safeString(snap.child("fullName").getValue(String.class));
                    if (fullName.isEmpty()) fullName = safeString(snap.child("full_name").getValue(String.class));
                    if (fullName.isEmpty()) fullName = safeString(snap.child("fullNameDisplay").getValue(String.class));
                    if (fullName.isEmpty()) fullName = safeString(snap.child("name").getValue(String.class));

                    if (fullName == null || fullName.trim().isEmpty()) {
                        // Try building from first/last
                        String fn = safeString(snap.child("firstName").getValue(String.class));
                        String ln = safeString(snap.child("lastName").getValue(String.class));
                        if (!fn.isEmpty() || !ln.isEmpty()) fullName = (fn + " " + ln).trim();
                    }

                    if (fullName == null || fullName.trim().isEmpty()) return;

                    // 1) update AttendanceSummary/{sectionId}/{sid}/studentName so subsequent loads have it
                    String finalFullName = fullName;
                    summaryRef.child(sid).child("studentName").setValue(fullName)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Log.d(TAG, "Wrote studentName to summary for sid=" + sid + " -> " + finalFullName);
                                } else {
                                    Log.w(TAG, "Failed to write studentName to summary for sid=" + sid + ": " + (task.getException() != null ? task.getException().getMessage() : "unknown"));
                                }
                            });

                    // 2) update local in-memory list and refresh adapter UI
                    boolean changed = false;
                    synchronized (items) {
                        for (int i = 0; i < items.size(); i++) {
                            AttendanceSummaryModel m = items.get(i);
                            if (sid.equals(m.studentId) && ("(Unknown)".equals(m.studentName) || m.studentName == null || m.studentName.trim().isEmpty())) {
                                AttendanceSummaryModel updated = new AttendanceSummaryModel(
                                        m.studentId,
                                        fullName,
                                        m.attendancePercentage,
                                        m.totalDays,
                                        m.counts,
                                        m.lastUpdated,
                                        m.lastStatus
                                );
                                items.set(i, updated);
                                changed = true;
                            }
                        }
                    }
                    if (changed) runOnUiThread(() -> adapter.setItems(items));
                }

                @Override public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Failed to fetch student " + sid + ": " + error.getMessage());
                }
            });
        }
    }

    private void exportCsv() {
        if (items.isEmpty()) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "Attendance CSV");
            share.putExtra(Intent.EXTRA_TEXT, "No attendance data available for export.");
            startActivity(Intent.createChooser(share, "Share CSV"));
            return;
        }

        // Build CSV in-memory
        StringJoiner sj = new StringJoiner("\n");
        // header
        sj.add("No,StudentId,StudentName,Present,Late,Excused,Absent,Percentage,TotalDays,LastStatus,LastUpdated");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        int idx = 1;
        for (AttendanceSummaryModel m : items) {
            long present = m.counts.getOrDefault("Present", 0L);
            long late = m.counts.getOrDefault("Late", 0L);
            long excused = m.counts.getOrDefault("Excused", 0L);
            long absent = m.counts.getOrDefault("Absent", 0L);
            String lastUpdated = m.lastUpdated > 0 ? sdf.format(m.lastUpdated) : "";
            // escape commas in name
            String nameEsc = "\"" + m.studentName.replace("\"", "\"\"") + "\"";
            String line = idx + "," + m.studentId + "," + nameEsc + "," + present + "," + late + "," + excused + "," + absent + "," +
                    m.attendancePercentage + "," + m.totalDays + "," + m.lastStatus + "," + lastUpdated;
            sj.add(line);
            idx++;
        }

        String csv = sj.toString();

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/csv");
        share.putExtra(Intent.EXTRA_SUBJECT, "Attendance CSV: " + sectionId);
        share.putExtra(Intent.EXTRA_TEXT, csv);
        startActivity(Intent.createChooser(share, "Share CSV"));
    }

    // safe helpers
    private static int safeInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private static long safeLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return 0L; }
    }

    private static String safeString(String s) {
        return s == null ? "" : s;
    }

    // Model
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
            this.counts = counts != null ? counts : new HashMap<String, Long>();
            this.lastUpdated = lastUpdated;
            this.lastStatus = lastStatus;
        }
    }

    // Adapter & VH
    private static class SummaryAdapter extends RecyclerView.Adapter<SummaryVH> {
        private final List<AttendanceSummaryModel> data;
        private final RowScrollRegistrar registrar;

        interface RowScrollRegistrar {
            void register(ObservableHorizontalScrollView row);
        }

        SummaryAdapter(List<AttendanceSummaryModel> initial, RowScrollRegistrar registrar) {
            this.data = initial != null ? new ArrayList<>(initial) : new ArrayList<AttendanceSummaryModel>();
            this.registrar = registrar;
        }

        void setItems(List<AttendanceSummaryModel> items) {
            data.clear();
            if (items != null) data.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public SummaryVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance_summary, parent, false);
            return new SummaryVH(v, registrar);
        }

        @Override
        public void onBindViewHolder(@NonNull SummaryVH holder, int position) {
            holder.bind(data.get(position), position + 1);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    private static class SummaryVH extends RecyclerView.ViewHolder {
        private final TextView tvNo;
        private final TextView tvStudent;
        private final TextView tvP;
        private final TextView tvL;
        private final TextView tvE;
        private final TextView tvA;
        private final TextView tvPercent;

        SummaryVH(@NonNull View itemView, SummaryAdapter.RowScrollRegistrar registrar) {
            super(itemView);
            tvNo = itemView.findViewById(R.id.row_no);
            tvStudent = itemView.findViewById(R.id.row_student);
            tvP = itemView.findViewById(R.id.row_present);
            tvL = itemView.findViewById(R.id.row_late);
            tvE = itemView.findViewById(R.id.row_excused);
            tvA = itemView.findViewById(R.id.row_absent);
            tvPercent = itemView.findViewById(R.id.row_percentage);

            // find the row HSV and register it for scroll sync
            ObservableHorizontalScrollView rowHsv = itemView.findViewById(R.id.row_hsv);
            if (rowHsv != null && registrar != null) {
                registrar.register(rowHsv);
            }
        }

        void bind(AttendanceSummaryModel m, int index) {
            tvNo.setText(String.valueOf(index));
            tvStudent.setText(m.studentName + " (" + m.studentId + ")");
            tvP.setText(String.valueOf(m.counts.getOrDefault("Present", 0L)));
            tvL.setText(String.valueOf(m.counts.getOrDefault("Late", 0L)));
            tvE.setText(String.valueOf(m.counts.getOrDefault("Excused", 0L)));
            tvA.setText(String.valueOf(m.counts.getOrDefault("Absent", 0L)));
            tvPercent.setText(m.attendancePercentage + "%");
        }
    }
}