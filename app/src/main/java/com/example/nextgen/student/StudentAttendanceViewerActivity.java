package com.example.nextgen.student;

import android.annotation.SuppressLint;
import android.os.Bundle;
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

import com.example.nextgen.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class StudentAttendanceViewerActivity extends AppCompatActivity {

    private CalendarView calendarView;
    private TextView tvDateStatus;
    private Button btnViewHistory;
    private Button btnRefresh;
    private RecyclerView rvHistory; // optional; may be null if not present in layout

    private DatabaseReference studentsRef;
    private String studentId;

    private final SimpleDateFormat dateKeyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_attendance_viewer);

        calendarView = findViewById(R.id.calendarView);
        tvDateStatus = findViewById(R.id.tvDateStatus);
        btnViewHistory = findViewById(R.id.btnViewHistory);
        btnRefresh = findViewById(R.id.btnRefresh);
        rvHistory = findViewById(R.id.rvHistory); // may be null if layout omitted

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        // Determine studentId: prefer Intent extra, else FirebaseAuth uid (student logged in)
        String fromIntent = getIntent().getStringExtra("studentId");
        if (fromIntent != null && !fromIntent.trim().isEmpty()) {
            studentId = fromIntent;
        } else {
            if (FirebaseAuth.getInstance().getCurrentUser() != null)
                studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        if (studentId == null || studentId.trim().isEmpty()) {
            Toast.makeText(this, "Student ID not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Listen for latest notification (teacher mark)
        studentsRef.child(studentId).child("attendanceNotifications").child("latest")
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot == null || !snapshot.exists()) return;
                        String status = snapshot.child("status").getValue(String.class);
                        String date = snapshot.child("date").getValue(String.class);
                        String teacher = snapshot.child("teacherName").getValue(String.class);
                        String section = snapshot.child("sectionDisplay").getValue(String.class);

                        StringBuilder sb = new StringBuilder();
                        sb.append("Attendance updated");
                        if (date != null && !date.isEmpty()) sb.append(" (").append(date).append(")");
                        sb.append(": ").append(status == null || status.isEmpty() ? "Not marked" : status);
                        if (teacher != null && !teacher.isEmpty()) sb.append("\nBy: ").append(teacher);
                        if (section != null && !section.isEmpty()) sb.append("\nSection: ").append(section);

                        // Show dialog notification
                        new AlertDialog.Builder(StudentAttendanceViewerActivity.this)
                                .setTitle("Attendance Notice")
                                .setMessage(sb.toString())
                                .setPositiveButton("OK", null)
                                .show();
                    }
                    @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) { /* ignore */ }
                });

        // Calendar selection -> preview status for that date
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            String dateKey = formatDate(year, month, dayOfMonth);
            loadStatusForDate(dateKey);
        });

        // show today's status initially
        String todayKey = dateKeyFmt.format(new Date());
        loadStatusForDate(todayKey);
        // set calendar to today
        calendarView.setDate(System.currentTimeMillis(), false, true);

        // History button: show recent entries dialog
        btnViewHistory.setOnClickListener(v -> showHistoryDialog());

        // Refresh button reloads today's status and inline history
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                String tk = dateKeyFmt.format(new Date());
                loadStatusForDate(tk);
                if (rvHistory != null) loadRecentHistoryIntoRv(60);
                Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show();
            });
        }

        // Optional: in-layout history RecyclerView
        if (rvHistory != null) {
            rvHistory.setLayoutManager(new LinearLayoutManager(this));
            // populate with recent entries (non-blocking)
            loadRecentHistoryIntoRv(60);
        }
    }

    private void loadStatusForDate(String dateKey) {
        studentsRef.child(studentId).child("attendanceHistory").child(dateKey)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot == null || !snapshot.exists()) {
                            tvDateStatus.setText(dateKey + ": Not marked");
                            return;
                        }
                        String status = snapshot.child("status").getValue(String.class);
                        String section = snapshot.child("sectionDisplay").getValue(String.class);
                        String teacher = snapshot.child("teacherName").getValue(String.class);
                        StringBuilder text = new StringBuilder();
                        text.append(dateKey).append(": ").append(status == null || status.isEmpty() ? "Not marked" : status);
                        if (section != null && !section.isEmpty()) text.append(" — ").append(section);
                        if (teacher != null && !teacher.isEmpty()) text.append(" (by ").append(teacher).append(")");
                        tvDateStatus.setText(text.toString());
                    }
                    @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                        tvDateStatus.setText(dateKey + ": Error loading");
                    }
                });
    }

    private void showHistoryDialog() {
        DatabaseReference histRef = studentsRef.child(studentId).child("attendanceHistory");
        // last N entries; using keys yyyy-MM-dd so orderByKey works
        histRef.orderByKey().limitToLast(180)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<HistoryEntry> list = new ArrayList<>();
                        if (snapshot != null && snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String dateKey = ds.getKey();
                                String status = ds.child("status").getValue(String.class);
                                String section = ds.child("sectionDisplay").getValue(String.class);
                                String teacher = ds.child("teacherName").getValue(String.class);
                                list.add(new HistoryEntry(dateKey, status, section, teacher));
                            }
                        }
                        Collections.reverse(list); // newest first
                        showHistoryDialogWithList(list);
                    }
                    @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                        Toast.makeText(StudentAttendanceViewerActivity.this, "Failed to load history", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showHistoryDialogWithList(List<HistoryEntry> entries) {
        View dlg = LayoutInflater.from(this).inflate(R.layout.dialog_history_list, null);
        RecyclerView rv = dlg.findViewById(R.id.dialogHistoryRv);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(this));
            HistoryAdapter ha = new HistoryAdapter(entries);
            rv.setAdapter(ha);
        }

        new AlertDialog.Builder(this)
                .setTitle("Attendance History")
                .setView(dlg)
                .setPositiveButton("Close", null)
                .show();
    }

    private void loadRecentHistoryIntoRv(int limit) {
        if (rvHistory == null) return;
        DatabaseReference histRef = studentsRef.child(studentId).child("attendanceHistory");
        histRef.orderByKey().limitToLast(limit)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<HistoryEntry> list = new ArrayList<>();
                        if (snapshot != null && snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String dateKey = ds.getKey();
                                String status = ds.child("status").getValue(String.class);
                                String section = ds.child("sectionDisplay").getValue(String.class);
                                String teacher = ds.child("teacherName").getValue(String.class);
                                list.add(new HistoryEntry(dateKey, status, section, teacher));
                            }
                        }
                        Collections.reverse(list);
                        HistoryAdapter ha = new HistoryAdapter(list);
                        rvHistory.setAdapter(ha);
                    }
                    @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) { /* ignore */ }
                });
    }

    // --- Models & adapters for history dialog ---
    private static class HistoryEntry {
        final String date;
        final String status;
        final String section;
        final String teacher;
        HistoryEntry(String date, String status, String section, String teacher) {
            this.date = date;
            this.status = status;
            this.section = section;
            this.teacher = teacher;
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
        private final TextView tvDate, tvStatus, tvMeta;
        HistoryVH(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvHistoryDate);
            tvStatus = itemView.findViewById(R.id.tvHistoryStatus);
            tvMeta = itemView.findViewById(R.id.tvHistoryMeta);
        }
        void bind(HistoryEntry e) {
            tvDate.setText(e.date != null ? e.date : "");
            tvStatus.setText(e.status == null || e.status.isEmpty() ? "Not marked" : e.status);
            StringBuilder meta = new StringBuilder();
            if (e.section != null && !e.section.isEmpty()) meta.append(e.section);
            if (e.teacher != null && !e.teacher.isEmpty()) {
                if (meta.length() > 0) meta.append(" • ");
                meta.append("By ").append(e.teacher);
            }
            tvMeta.setText(meta.toString());
            // Optional: tint status badge color if you change layout to include dynamic background tinting
        }
    }

    // Utility: formatDate used earlier in teacher code
    private static String formatDate(int year, int month, int dayOfMonth) {
        // month is 0-based from CalendarView listener
        int mm = month + 1;
        return String.format(Locale.getDefault(), "%04d-%02d-%02d", year, mm, dayOfMonth);
    }
}