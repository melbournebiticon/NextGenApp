package com.example.nextgen.admin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ImageButton;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;

import java.util.ArrayList;
import java.util.List;

public class TeacherAdapter extends RecyclerView.Adapter<TeacherAdapter.TeacherViewHolder> {

<<<<<<< Updated upstream
    private final List<TeacherModel> teacherList;
    private final OnTeacherActionListener actionListener;
=======
    private List<TeacherModel> teacherList;
    private final List<TeacherModel> originalTeacherList; // Store original list for filtering
    private final OnTeacherActionListener actionListener; // 🔹 Callback for Update/Delete
>>>>>>> Stashed changes

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
        holder.tvEmail.setText(teacher.getEmail() != null ? teacher.getEmail() : "N/A");

        List<String> courses = teacher.getCourseDisplays();
        holder.tvCourse.setText((courses != null && !courses.isEmpty())
                ? String.join("\n", courses)
                : "No courses assigned");

        List<String> subjects = teacher.getAssignedSubjects();
        holder.tvSubjects.setText((subjects != null && !subjects.isEmpty())
                ? String.join(", ", subjects)
                : "No subjects assigned");

        // ✅ Load profile image
        if (teacher.getProfileImage() != null && !teacher.getProfileImage().isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(teacher.getProfileImage(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                holder.ivProfile.setImageBitmap(bitmap);
            } catch (Exception e) {
                holder.ivProfile.setImageResource(R.drawable.examinee_default); // fallback
            }
        } else {
            holder.ivProfile.setImageResource(R.drawable.examinee_default);
        }

        // 🔹 Button click listeners
        holder.btnUpdate.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onUpdate(teacher);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onDelete(teacher);
        });
    }

    @Override
    public int getItemCount() {
        return teacherList.size();
    }

    // 🔹 NEW METHOD: Update the list for search functionality
    public void updateList(List<TeacherModel> filteredList) {
        this.teacherList = filteredList;
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
        ImageView ivProfile;
        ImageButton btnUpdate, btnDelete;

        public TeacherViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTeacherId = itemView.findViewById(R.id.tvTeacherId);
            tvDisplayName = itemView.findViewById(R.id.tvDisplayName);
            tvFullName = itemView.findViewById(R.id.tvFullName);
            tvCourse = itemView.findViewById(R.id.tvCourse);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvSubjects = itemView.findViewById(R.id.tvSubjects);
            ivProfile = itemView.findViewById(R.id.ivProfile); // 👈 Make sure this exists in XML
            btnUpdate = itemView.findViewById(R.id.btnUpdateTeacher);
            btnDelete = itemView.findViewById(R.id.btnDeleteTeacher);
        }
    }

    // 🔹 Interface to communicate with TeacherActivity
    public interface OnTeacherActionListener {
        void onUpdate(TeacherModel teacher);
        void onDelete(TeacherModel teacher);
    }
<<<<<<< Updated upstream
}
=======
}
>>>>>>> Stashed changes
