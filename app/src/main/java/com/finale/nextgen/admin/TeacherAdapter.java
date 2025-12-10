package com.finale.nextgen.admin;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeacherAdapter extends RecyclerView.Adapter<TeacherAdapter.TeacherViewHolder> {

    private List<TeacherModel> teacherList;
    private final List<TeacherModel> originalTeacherList; // Store original list for filtering
    private final OnTeacherActionListener actionListener; // 🔹 Callback for Update/Delete

    // Map from subjectId -> subjectName used for display only
    private Map<String, String> subjectIdNameMap = new HashMap<>();

    public TeacherAdapter(List<TeacherModel> teacherList, OnTeacherActionListener actionListener) {
        this.teacherList = teacherList != null ? teacherList : new ArrayList<>();
        this.originalTeacherList = new ArrayList<>(this.teacherList); // Keep copy of original data
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

        holder.tvTeacherId.setText(teacher.getId() != null ? teacher.getId() : "N/A");
        holder.tvDisplayName.setText(teacher.getDisplayName() != null ? teacher.getDisplayName() : "N/A");
        holder.tvFullName.setText(teacher.getFullName() != null ? teacher.getFullName() : "N/A");
        List<String> courses = teacher.getCourseDisplays();
        if (courses != null && !courses.isEmpty()) {
            holder.tvCourse.setText(TextUtils.join("\n", courses));
        } else {
            holder.tvCourse.setText("No courses assigned");
        }

        holder.tvEmail.setText(teacher.getEmail() != null ? teacher.getEmail() : "N/A");

        List<String> subjectIds = teacher.getAssignedSubjects();
        if (subjectIds != null && !subjectIds.isEmpty()) {
            // Map IDs -> names using the subjectIdNameMap. fallback to ID if name not found.
            List<String> subjectNames = new ArrayList<>(subjectIds.size());
            for (String id : subjectIds) {
                if (id == null) continue;
                String name = subjectIdNameMap.get(id);
                subjectNames.add(name != null ? name : id);
            }
            holder.tvSubjects.setText(TextUtils.join(", ", subjectNames));
        } else {
            holder.tvSubjects.setText("No subjects assigned");
        }

        // 🔹 Button click listeners
        holder.btnUpdate.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onUpdate(teacher);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onDelete(teacher);
            }
        });
    }

    @Override
    public int getItemCount() {
        return teacherList.size();
    }

    // 🔹 NEW METHOD: supply subjectId->name mapping (call this after subjects are loaded)
    public void setSubjectIdNameMap(Map<String, String> map) {
        subjectIdNameMap = map != null ? map : new HashMap<>();
        notifyDataSetChanged();
    }

    // 🔹 NEW HELPER: build and set map directly from a list of SubjectModel
    public void setSubjectsListForMapping(List<SubjectModel> subjects) {
        Map<String, String> map = new HashMap<>();
        if (subjects != null) {
            for (SubjectModel s : subjects) {
                if (s != null && s.getId() != null) {
                    map.put(s.getId(), s.getName());
                }
            }
        }
        setSubjectIdNameMap(map);
    }

    // 🔹 NEW METHOD: Update the list for search functionality
    public void updateList(List<TeacherModel> filteredList) {
        this.teacherList = filteredList != null ? filteredList : new ArrayList<>();
        notifyDataSetChanged();
    }

    // 🔹 OPTIONAL: Method to reset to original list
    public void resetList() {
        this.teacherList = new ArrayList<>(originalTeacherList);
        notifyDataSetChanged();
    }

    // 🔹 OPTIONAL: Method to get current list (for count updates)
    public List<TeacherModel> getCurrentList() {
        return teacherList;
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

    // 🔹 Interface to communicate with TeacherActivity
    public interface OnTeacherActionListener {
        void onUpdate(TeacherModel teacher);
        void onDelete(TeacherModel teacher);
    }
}