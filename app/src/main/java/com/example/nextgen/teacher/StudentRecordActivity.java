package com.example.nextgen.teacher;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * StudentRecordActivity
 *
 * Updated to:
 * - Show SECTION title at the top.
 * - List all students in the section in a RecyclerView (rvStudents).
 * - When a student is tapped, their calendar + records load below.
 * - Students are sorted by studentId then alphabetically by name.
 */
public class StudentRecordActivity extends AppCompatActivity {

    private static final String TAG = "StudentRecordActivity";

    private String sectionId;
    private DatabaseReference attendanceRef;
    private DatabaseReference summaryRef;

    private TextView tvSectionTitle;
    private RecyclerView rvStudents;
    private RecyclerView rvRecords;
    private CalendarView calendar;
    private TextView tvSelectedDateStatus;
    private ProgressBar progress;
    private Button btnComputeAverage;
    private TextView tvAverage;

    private final List<StudentEntry> students = new ArrayList<>();
    private final List<RecordEntry> records = new ArrayList<>();

    private StudentListAdapter studentListAdapter;
    private RecordAdapter recordAdapter;

    private String selectedStudentId = null;
    private String selectedStudentName = null;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_record);

        // Views
        tvSectionTitle = findViewById(R.id.tvSectionTitle);
        rvStudents = findViewById(R.id.rvStudents);
        rvRecords = findViewById(R.id.rvRecords);
        calendar = findViewById(R.id.calendarView);
        tvSelectedDateStatus = findViewById(R.id.tvSelectedDateStatus);
        progress = findViewById(R.id.progressRecords);
        btnComputeAverage = findViewById(R.id.btnComputeAverage);
        tvAverage = findViewById(R.id.tvAverage);

        // Adapters
        studentListAdapter = new StudentListAdapter(students, (studentId, studentName) -> {
            // on student click
            selectedStudentId = studentId;
            selectedStudentName = studentName;
            // load records and update UI
            tvSelectedDateStatus.setText("Select a date");
            tvAverage.setText("—");
            loadAllRecordsForStudent(studentId);
        });
        rvStudents.setLayoutManager(new LinearLayoutManager(this));
        rvStudents.setAdapter(studentListAdapter);

        recordAdapter = new RecordAdapter(records);
        rvRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRecords.setAdapter(recordAdapter);

        sectionId = getIntent().getStringExtra("sectionId");
        String sectionDisplay = getIntent().getStringExtra("sectionDisplay");
        if (sectionDisplay != null && !sectionDisplay.trim().isEmpty()) {
            tvSectionTitle.setText(sectionDisplay);
        } else {
            tvSectionTitle.setText("Student Attendance Records");
        }

        if (sectionId == null || sectionId.trim().isEmpty()) {
            Toast.makeText(this, "No section specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        attendanceRef = FirebaseDatabase.getInstance().getReference("Attendance").child(sectionId);
        summaryRef = FirebaseDatabase.getInstance().getReference("AttendanceSummary").child(sectionId);

        loadStudentsFromSummary();

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
                studentListAdapter.notifyDataSetChanged();
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

            // Sort by studentId then by name
            Collections.sort(students, new Comparator<StudentEntry>() {
                @Override
                public int compare(StudentEntry a, StudentEntry b) {
                    String aId = a.studentId == null ? "" : a.studentId.trim();
                    String bId = b.studentId == null ? "" : b.studentId.trim();
                    int cmp = aId.compareToIgnoreCase(bId);
                    if (cmp != 0) return cmp;
                    return a.studentName.compareToIgnoreCase(b.studentName);
                }
            });

            studentListAdapter.notifyDataSetChanged();

            // optionally auto-select first student
            if (!students.isEmpty()) {
                StudentEntry first = students.get(0);
                selectedStudentId = first.studentId;
                selectedStudentName = first.studentName;
                loadAllRecordsForStudent(selectedStudentId);
            }
        });
    }

    private void loadAllRecordsForStudent(String studentId) {
        if (studentId == null) return;
        progress.setVisibility(View.VISIBLE);
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

            // show today's record summary above compute button by default
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
            double weighted = present * 1.0 + excused * 1.0 + late * 0.9;
            double pct = (weighted / total) * 100.0;
            String text = String.format(Locale.getDefault(), "Average: %.1f%%  (P:%d L:%d E:%d A:%d / %d days)", pct, present, late, excused, absent, total);
            tvAverage.setText(text);
        });
    }

    private static String safeString(String s) {
        return s == null ? "" : s;
    }

    static String formatDate(int year, int month, int day) {
        int m = month + 1;
        return String.format(Locale.getDefault(), "%04d-%02d-%02d", year, m, day);
    }

    // ---- POJOs & Adapters ----

    private static class StudentEntry {
        final String studentId;
        final String studentName;
        StudentEntry(String id, String name) { studentId = id; studentName = name; }
    }

    private static class RecordEntry {
        final String date; final String status; final String studentName; final long timestamp;
        RecordEntry(String date, String status, String studentName, long ts) {
            this.date = date; this.status = status; this.studentName = studentName; this.timestamp = ts;
        }
    }

    private static class StudentListAdapter extends RecyclerView.Adapter<StudentVH> {
        interface OnStudentClick { void onClick(String studentId, String studentName); }
        private final List<StudentEntry> data;
        private final OnStudentClick listener;
        StudentListAdapter(List<StudentEntry> items, OnStudentClick l) { data = items != null ? items : new ArrayList<>(); listener = l; }
        @NonNull @Override public StudentVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student_list, parent, false);
            return new StudentVH(v, listener);
        }
        @Override public void onBindViewHolder(@NonNull StudentVH holder, int position) { holder.bind(data.get(position)); }
        @Override public int getItemCount() { return data.size(); }
    }

    private static class StudentVH extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvId;
        StudentVH(@NonNull View itemView, StudentListAdapter.OnStudentClick l) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvStudentName);
            tvId = itemView.findViewById(R.id.tvStudentId);
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos >= 0 && pos < ((RecyclerView) itemView.getParent()).getAdapter().getItemCount()) {
                    // we will call listener with bound values
                    CharSequence name = tvName.getText();
                    CharSequence idVal = tvId.getText();
                    if (l != null) l.onClick(String.valueOf(idVal), String.valueOf(name));
                }
            });
        }
        void bind(StudentEntry e) {
            tvName.setText(e.studentName);
            tvId.setText(e.studentId);
        }
    }

    private static class RecordAdapter extends RecyclerView.Adapter<RecordVH> {
        private final List<RecordEntry> data;
        RecordAdapter(List<RecordEntry> initial) { this.data = initial != null ? initial : new ArrayList<>(); }
        @NonNull @Override public RecordVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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