package com.example.nextgen.teacher;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * StudentRecordActivity
 *
 * Purpose:
 * - Let teacher pick a student (from AttendanceSummary/{sectionId} population).
 * - Show per-day attendance records for that student under Attendance/{sectionId}/{date}/{studentId}.
 * - Calendar date selection shows the single-day record.
 * - Compute button calculates attendance percentage over all recorded dates (or last N days).
 *
 * Required extra: "sectionId" (String) — the section key used in Attendance and AttendanceSummary.
 *
 * Usage:
 * - From StudentAttendanceActivity, start with:
 *   Intent i = new Intent(this, StudentRecordActivity.class);
 *   i.putExtra("sectionId", selectedSectionId);
 *   startActivity(i);
 */
public class StudentRecordActivity extends AppCompatActivity {

    private static final String TAG = "StudentRecordActivity";

    private String sectionId;
    private DatabaseReference attendanceRef;
    private DatabaseReference summaryRef;

    private Spinner spinnerStudents;
    private CalendarView calendar;
    private TextView tvSelectedDateStatus;
    private RecyclerView rvRecords;
    private ProgressBar progress;
    private Button btnComputeAverage;
    private TextView tvAverage;

    private final List<StudentEntry> students = new ArrayList<>();
    private final List<RecordEntry> records = new ArrayList<>();
    private RecordAdapter recordAdapter;

    private String selectedStudentId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_record);

        spinnerStudents = findViewById(R.id.spinnerStudents);
        calendar = findViewById(R.id.calendarView);
        tvSelectedDateStatus = findViewById(R.id.tvSelectedDateStatus);
        rvRecords = findViewById(R.id.rvRecords);
        progress = findViewById(R.id.progressRecords);
        btnComputeAverage = findViewById(R.id.btnComputeAverage);
        tvAverage = findViewById(R.id.tvAverage);

        recordAdapter = new RecordAdapter(records);
        rvRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRecords.setAdapter(recordAdapter);

        sectionId = getIntent().getStringExtra("sectionId");
        if (sectionId == null || sectionId.trim().isEmpty()) {
            Toast.makeText(this, "No section specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        attendanceRef = FirebaseDatabase.getInstance().getReference("Attendance").child(sectionId);
        summaryRef = FirebaseDatabase.getInstance().getReference("AttendanceSummary").child(sectionId);

        loadStudentsFromSummary();

        // when spinner student selected -> load records
        spinnerStudents.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < students.size()) {
                    selectedStudentId = students.get(position).studentId;
                    tvAverage.setText("—");
                    loadAllRecordsForStudent(selectedStudentId);
                } else {
                    selectedStudentId = null;
                    records.clear();
                    recordAdapter.notifyDataSetChanged();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // calendar selection -> show single day
        calendar.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            String dateStr = formatDate(year, month, dayOfMonth);
            showRecordForDate(dateStr);
        });

        btnComputeAverage.setOnClickListener(v -> {
            if (selectedStudentId == null) {
                Toast.makeText(this, "Select a student first", Toast.LENGTH_SHORT).show();
                return;
            }
            computeAverageForStudent(selectedStudentId);
        });
    }

    private void loadStudentsFromSummary() {
        progress.setVisibility(View.VISIBLE);
        summaryRef.get().addOnCompleteListener(task -> {
            progress.setVisibility(View.GONE);
            students.clear();
            if (!task.isSuccessful() || task.getResult() == null) {
                Toast.makeText(this, "Failed to load students", Toast.LENGTH_SHORT).show();
                populateSpinnerEmpty();
                return;
            }
            DataSnapshot snap = task.getResult();
            for (DataSnapshot child : snap.getChildren()) {
                String sid = child.getKey();
                if (sid == null) continue;
                String name = child.child("studentName").getValue(String.class);
                if (name == null || name.trim().isEmpty()) name = child.child("name").getValue(String.class);
                if (name == null || name.trim().isEmpty()) name = "(Unknown)";
                students.add(new StudentEntry(sid, name));
            }
            // sort by name
            Collections.sort(students, Comparator.comparing(s -> s.studentName.toLowerCase(Locale.ROOT)));
            populateSpinner();
        });
    }

    private void populateSpinner() {
        List<String> labels = new ArrayList<>();
        for (StudentEntry s : students) labels.add(s.studentName + " (" + s.studentId + ")");
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStudents.setAdapter(a);
        if (!students.isEmpty()) spinnerStudents.setSelection(0);
    }

    private void populateSpinnerEmpty() {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStudents.setAdapter(a);
    }

    private void loadAllRecordsForStudent(String studentId) {
        if (studentId == null) return;
        progress.setVisibility(View.VISIBLE);
        // Attendance/<sectionId> has children for each date -> inside each date there may be child studentId
        attendanceRef.get().addOnCompleteListener(task -> {
            progress.setVisibility(View.GONE);
            records.clear();
            if (!task.isSuccessful() || task.getResult() == null) {
                Toast.makeText(this, "Failed to load attendance records", Toast.LENGTH_SHORT).show();
                recordAdapter.notifyDataSetChanged();
                return;
            }
            for (DataSnapshot dateSnap : task.getResult().getChildren()) {
                String dateKey = dateSnap.getKey(); // yyyy-MM-dd
                if (dateKey == null) continue;
                DataSnapshot studentSnap = dateSnap.child(studentId);
                if (!studentSnap.exists()) continue;
                String status = safeString(studentSnap.child("status").getValue(String.class));
                String name = safeString(studentSnap.child("studentName").getValue(String.class));
                long ts = 0L;
                Object tObj = studentSnap.child("timestamp").getValue(); // optional
                if (tObj instanceof Long) ts = (Long) tObj;
                records.add(new RecordEntry(dateKey, status, name, ts));
            }
            // sort dates desc
            Collections.sort(records, (r1, r2) -> r2.date.compareTo(r1.date));
            recordAdapter.notifyDataSetChanged();
            // update calendar selection UI to today by default: set status text for today's date
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(calendar.getDate()));
            showRecordForDate(today);
        });
    }

    private void showRecordForDate(String dateKey) {
        if (selectedStudentId == null) {
            tvSelectedDateStatus.setText("No student selected");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        attendanceRef.child(dateKey).child(selectedStudentId).get().addOnCompleteListener(task -> {
            progress.setVisibility(View.GONE);
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                tvSelectedDateStatus.setText(dateKey + " : Not marked");
                return;
            }
            DataSnapshot snap = task.getResult();
            String status = safeString(snap.child("status").getValue(String.class));
            String who = safeString(snap.child("studentName").getValue(String.class));
            tvSelectedDateStatus.setText(dateKey + " : " + status + (who.isEmpty() ? "" : " (" + who + ")"));
        });
    }

    private void computeAverageForStudent(String studentId) {
        if (studentId == null) return;
        progress.setVisibility(View.VISIBLE);
        attendanceRef.get().addOnCompleteListener(task -> {
            progress.setVisibility(View.GONE);
            if (!task.isSuccessful() || task.getResult() == null) {
                Toast.makeText(this, "Failed to load attendance for average", Toast.LENGTH_SHORT).show();
                return;
            }
            int present = 0, late = 0, excused = 0, absent = 0, total = 0;
            for (DataSnapshot dateSnap : task.getResult().getChildren()) {
                DataSnapshot studentSnap = dateSnap.child(studentId);
                if (!studentSnap.exists()) continue;
                String status = safeString(studentSnap.child("status").getValue(String.class));
                if (status.isEmpty()) continue;
                total++;
                switch (status.toLowerCase(Locale.ROOT)) {
                    case "present": present++; break;
                    case "late": late++; break;
                    case "excused": excused++; break;
                    case "absent": absent++; break;
                    default: break;
                }
            }
            if (total == 0) {
                tvAverage.setText("No records to compute");
                return;
            }
            // compute simple percentage: (present + excused + late*0.9) / total
            double weighted = present * 1.0 + excused * 1.0 + late * 0.9;
            double pct = (weighted / total) * 100.0;
            String text = String.format(Locale.getDefault(), "Average: %.1f%%  (P:%d L:%d E:%d A:%d / %d days)", pct, present, late, excused, absent, total);
            tvAverage.setText(text);
        });
    }

    private static String safeString(String s) {
        return s == null ? "" : s;
    }

    private static String formatDate(int year, int month, int day) {
        // month is 0-based in CalendarView callback
        int m = month + 1;
        return String.format(Locale.getDefault(), "%04d-%02d-%02d", year, m, day);
    }

    // ---- small POJOs & Adapter ----

    private static class StudentEntry {
        final String studentId;
        final String studentName;
        StudentEntry(String id, String name) { studentId = id; studentName = name; }
    }

    private static class RecordEntry {
        final String date; // yyyy-MM-dd
        final String status;
        final String studentName;
        final long timestamp; // optional
        RecordEntry(String date, String status, String studentName, long ts) {
            this.date = date; this.status = status; this.studentName = studentName; this.timestamp = ts;
        }
    }

    private static class RecordAdapter extends RecyclerView.Adapter<RecordVH> {
        private final List<RecordEntry> data;
        RecordAdapter(List<RecordEntry> initial) { this.data = initial != null ? initial : new ArrayList<>(); }
        @NonNull
        @Override public RecordVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance_record, parent, false);
            return new RecordVH(v);
        }
        @Override public void onBindViewHolder(@NonNull RecordVH holder, int position) { holder.bind(data.get(position)); }
        @Override public int getItemCount() { return data.size(); }
    }

    private static class RecordVH extends RecyclerView.ViewHolder {
        final TextView tvDate, tvStatus;
        RecordVH(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvRecordDate);
            tvStatus = itemView.findViewById(R.id.tvRecordStatus);
        }
        void bind(RecordEntry e) {
            tvDate.setText(e.date);
            tvStatus.setText(e.status + (e.studentName != null && !e.studentName.isEmpty() ? " (" + e.studentName + ")" : ""));
        }
    }
}