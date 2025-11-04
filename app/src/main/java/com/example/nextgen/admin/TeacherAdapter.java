package com.example.nextgen.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;

import java.util.ArrayList;
import java.util.List;

public class TeacherAdapter extends RecyclerView.Adapter<TeacherAdapter.TeacherViewHolder> {

    private final List<TeacherModel> teacherList;
    private final OnTeacherActionListener actionListener;

    public TeacherAdapter(List<TeacherModel> teacherList, OnTeacherActionListener actionListener) {
        this.teacherList = teacherList != null ? teacherList : new ArrayList<>();
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public TeacherViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_teacher, parent, false);
        return new TeacherViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TeacherViewHolder holder, int position) {
        TeacherModel teacher = teacherList.get(position);

        try {
            holder.tvTeacherId.setText(teacher.getId() != null ? teacher.getId() : "N/A");
            holder.tvDisplayName.setText(teacher.getDisplayName() != null ? teacher.getDisplayName() : "N/A");
            holder.tvFullName.setText(teacher.getFullName() != null ? teacher.getFullName() : "N/A");

            List<String> courses = teacher.getCourseDisplays();
            if (courses != null && !courses.isEmpty()) {
                holder.tvCourse.setText(String.join("\n", courses));
            } else {
                holder.tvCourse.setText("No courses assigned");
            }

            holder.tvEmail.setText(teacher.getEmail() != null ? teacher.getEmail() : "N/A");

            List<String> subjects = teacher.getAssignedSubjects();
            if (subjects != null && !subjects.isEmpty()) {
                holder.tvSubjects.setText(String.join(", ", subjects));
            } else {
                holder.tvSubjects.setText("No subjects assigned");
            }

            // Button click listeners with error handling
            holder.btnUpdate.setOnClickListener(v -> {
                try {
                    if (actionListener != null) {
                        actionListener.onUpdate(teacher);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(holder.itemView.getContext(), "Error updating teacher", Toast.LENGTH_SHORT).show();
                }
            });

            holder.btnDelete.setOnClickListener(v -> {
                try {
                    if (actionListener != null) {
                        actionListener.onDelete(teacher);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(holder.itemView.getContext(), "Error deleting teacher", Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return teacherList.size();
    }

    public static class TeacherViewHolder extends RecyclerView.ViewHolder {
        TextView tvTeacherId, tvDisplayName, tvFullName, tvCourse, tvEmail, tvSubjects;
        Button btnUpdate, btnDelete;

        public TeacherViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTeacherId = itemView.findViewById(R.id.tvTeacherId);
            tvDisplayName = itemView.findViewById(R.id.tvDisplayName);
            tvFullName = itemView.findViewById(R.id.tvFullName);
            tvCourse = itemView.findViewById(R.id.tvCourse);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvSubjects = itemView.findViewById(R.id.tvSubjects);
            btnUpdate = itemView.findViewById(R.id.btnUpdateTeacher);
            btnDelete = itemView.findViewById(R.id.btnDeleteTeacher);
        }
    }

    public interface OnTeacherActionListener {
        void onUpdate(TeacherModel teacher);
        void onDelete(TeacherModel teacher);
    }
}