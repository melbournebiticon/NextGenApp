package com.example.nextgen.student;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class StudentActivitiesActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    SessionManager sessionManager;
    DatabaseReference activitiesRef;
    List<ActivityModel> activityList;
    ActivitiesAdapter adapter;

    TextView tvSubjectCode, tvSubjectName, tvTeacherName;
    Button btnPerformance;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_activities);

        sessionManager = new SessionManager(this);

        // 🟩 Header Views
        tvSubjectCode = findViewById(R.id.tvSubjectCode);
        tvSubjectName = findViewById(R.id.tvSubjectName);
        tvTeacherName = findViewById(R.id.tvTeacherName);
        btnPerformance = findViewById(R.id.btnPerformance);

        // 🟦 RecyclerView setup
        recyclerView = findViewById(R.id.recyclerStudentActivities);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        activityList = new ArrayList<>();
        adapter = new ActivitiesAdapter(activityList);
        recyclerView.setAdapter(adapter);

        activitiesRef = FirebaseDatabase.getInstance().getReference("Activities");

        // 🟨 Get data from intent
        String subjectId = getIntent().getStringExtra("subjectId");
        String courseDisplay = getIntent().getStringExtra("courseDisplay");
        String subjectCode = getIntent().getStringExtra("subjectCode");
        String subjectName = getIntent().getStringExtra("subjectName");
        String teacherName = getIntent().getStringExtra("teacherName");

        // 🟪 Validate
        if (subjectId == null || courseDisplay == null) {
            Toast.makeText(this, "No subject selected.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 🟩 Display header info
        tvSubjectCode.setText((subjectCode != null ? subjectCode : "N/A"));
        tvSubjectName.setText((subjectName != null ? subjectName : "N/A"));
        tvTeacherName.setText((teacherName != null ? teacherName : "N/A"));

        // 🟦 Button action
        btnPerformance.setOnClickListener(v ->
                Toast.makeText(this, "Performance screen coming soon!", Toast.LENGTH_SHORT).show()
        );

        // 🟧 Load activities
        loadStudentActivities(subjectId, courseDisplay);
    }

    private void loadStudentActivities(String subjectId, String courseDisplay) {
        activitiesRef.orderByChild("courseDisplay").equalTo(courseDisplay)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        activityList.clear();
                        for (DataSnapshot snap : snapshot.getChildren()) {
                            ActivityModel activity = snap.getValue(ActivityModel.class);
                            if (activity != null && subjectId.equals(activity.getSubjectId())) {
                                activityList.add(activity);
                            }
                        }

                        if (activityList.isEmpty()) {
                            Toast.makeText(StudentActivitiesActivity.this, "No activities for this subject.", Toast.LENGTH_SHORT).show();
                        }

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(StudentActivitiesActivity.this, "Failed to load activities", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ===== RecyclerView Adapter =====
    private static class ActivitiesAdapter extends RecyclerView.Adapter<ActivitiesAdapter.ViewHolder> {
        private final List<ActivityModel> list;

        public ActivitiesAdapter(List<ActivityModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity_student, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ActivityModel activity = list.get(position);
            holder.tvTitle.setText(activity.getTitle());
            holder.tvSubject.setText(activity.getSubject());
            holder.tvDueDate.setText(activity.getDueDate());
            holder.tvDescription.setText(activity.getDescription());
            holder.tvTeacher.setText(activity.getTeacherName());
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvSubject, tvDueDate, tvDescription, tvTeacher;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvActivityTitle);
                tvSubject = itemView.findViewById(R.id.tvActivitySubject);
                tvDueDate = itemView.findViewById(R.id.tvActivityDueDate);
                tvDescription = itemView.findViewById(R.id.tvActivityDescription);
                tvTeacher = itemView.findViewById(R.id.tvActivityTeacher); // new
            }

        }
    }
}
