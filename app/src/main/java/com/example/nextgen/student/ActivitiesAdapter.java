package com.example.nextgen.student;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

public class ActivitiesAdapter extends RecyclerView.Adapter<ActivitiesAdapter.ViewHolder> {

    private final List<ActivityModel> list;
    private final Context context;

    public ActivitiesAdapter(Context context, List<ActivityModel> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity_student, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActivityModel activity = list.get(position);

        // Display basic info
        holder.tvTitle.setText(activity.getTitle() != null ? activity.getTitle() : "N/A");
        holder.tvDueDate.setText(activity.getDueDate() != null ? activity.getDueDate() : "N/A");
        holder.tvDescription.setText(activity.getDescription() != null ? activity.getDescription() : "N/A");
        String subject = activity.getSubject() != null ? activity.getSubject() : "N/A";
        String teacher = activity.getTeacherName() != null ? activity.getTeacherName() : "N/A";
        holder.tvTeacher.setText(subject + " • " + teacher);

        // Reset chip
        setChip(holder, "Loading...", R.color.white, R.drawable.ic_clock);

        // Firebase submission logic
        String studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference submissionRef = FirebaseDatabase.getInstance()
                .getReference("Submissions")
                .child(activity.getActivityId())
                .child(studentId);

        submissionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int maxScoreInt = 0;
                try {
                    if (activity.getMaxScore() != null) {
                        maxScoreInt = Integer.parseInt(activity.getMaxScore());
                    }
                } catch (NumberFormatException e) {
                    Log.w("ActivitiesAdapter", "Invalid maxScore: " + activity.getMaxScore());
                }

                if (!snapshot.exists()) {
                    setChip(holder, "Pending", R.color.dark_blue_700, R.drawable.ic_clock);
                    return;
                }

                Boolean resubmitRequested = snapshot.child("resubmitRequested").getValue(Boolean.class);

                // === Safe score conversion ===
                Object scoreObj = snapshot.child("score").getValue();
                String scoreStr;
                if (scoreObj == null) {
                    scoreStr = "Pending";
                } else if (scoreObj instanceof Number) {
                    scoreStr = String.valueOf(((Number) scoreObj).intValue());
                } else {
                    scoreStr = scoreObj.toString();
                }

                if (resubmitRequested != null && resubmitRequested) {
                    setChip(holder, "Resubmit Requested", R.color.teal_700, R.drawable.ic_reset);
                    return;
                }

                if ("Pending".equalsIgnoreCase(scoreStr)) {
                    setChip(holder, "Submitted", R.color.teal_700, R.drawable.ic_upload);
                    return;
                }

                // Parse numeric score safely
                int scoreValue;
                try {
                    scoreValue = Integer.parseInt(scoreStr);
                } catch (NumberFormatException e) {
                    Log.w("ActivitiesAdapter", "Non-numeric score: " + scoreStr);
                    setChip(holder, "Submitted", R.color.teal_700, R.drawable.ic_upload);
                    return;
                }

                setChip(holder, "Done (" + scoreValue + "/" + maxScoreInt + ")", R.color.teal_700, R.drawable.ic_check_circle);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setChip(holder, "Pending", R.color.dark_blue_700, R.drawable.ic_clock);
            }
        });

        // Click -> Open ActivityDetails
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ActivityDetailsActivity.class);
            intent.putExtra("activityId", activity.getActivityId());
            intent.putExtra("title", activity.getTitle());
            intent.putExtra("description", activity.getDescription());
            intent.putExtra("subjectCode", activity.getSubjectCode());
            intent.putExtra("subjectName", activity.getSubject());
            intent.putExtra("teacherName", activity.getTeacherName());
            intent.putExtra("dueDate", activity.getDueDate());
            intent.putExtra("mainTerm", activity.getMainTerm());
            intent.putExtra("subTerm", activity.getSubTerm());
            intent.putExtra("maxScore", activity.getMaxScore());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    private void setChip(ViewHolder holder, String text, int colorRes, int iconRes) {
        holder.chipStatus.setText(text);
        holder.chipStatus.setChipBackgroundColorResource(colorRes);
        holder.chipStatus.setChipIconResource(iconRes);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDueDate, tvDescription, tvTeacher;
        Chip chipStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvActivityTitle);
            tvDueDate = itemView.findViewById(R.id.tvActivityDueDate);
            tvDescription = itemView.findViewById(R.id.tvActivityDescription);
            tvTeacher = itemView.findViewById(R.id.tvActivityTeacher);
            chipStatus = itemView.findViewById(R.id.chipStatus);
        }
    }
}
