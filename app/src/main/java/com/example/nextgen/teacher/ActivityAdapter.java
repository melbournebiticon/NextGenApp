package com.example.nextgen.teacher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ViewHolder> {

    private List<ActivityModel> list;
    private TeacherActivitiesActivity.ActivityActionListener listener;

    public ActivityAdapter(List<ActivityModel> list, TeacherActivitiesActivity.ActivityActionListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity_teacher, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActivityModel activity = list.get(position);
        holder.tvTitle.setText(activity.getTitle());

        String subjectInfo = activity.getSubjectCode() + " - " + activity.getSubject();
        String classInfo = activity.getCourseDisplay();
        holder.tvSubject.setText(subjectInfo + (classInfo != null ? " (" + classInfo + ")" : ""));

        // Set real description and due date
        holder.tvActivityDescription.setText(activity.getDescription());
        holder.tvDueDate.setText(activity.getDueDate());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onViewSubmissions(activity);
            }
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditActivity(activity);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteActivity(activity);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSubject, tvActivityDescription, tvDueDate;
        MaterialButton btnEdit, btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvActivityTitle);
            tvSubject = itemView.findViewById(R.id.tvActivitySubject);
            tvActivityDescription = itemView.findViewById(R.id.tvActivityDescription); // ADD THIS
            tvDueDate = itemView.findViewById(R.id.tvActivityDueDate); // ADD THIS
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}