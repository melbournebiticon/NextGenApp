package com.finale.nextgen.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;

import java.util.ArrayList;
import java.util.List;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.StudentViewHolder> {

    private List<StudentModel> studentList;
    private final List<StudentModel> originalStudentList; // Store original list for filtering
    private final OnStudentActionListener listener;

    public interface OnStudentActionListener {
        void onUpdate(StudentModel student);
        void onDelete(StudentModel student);
    }

    public StudentAdapter(List<StudentModel> studentList, OnStudentActionListener listener) {
        this.studentList = studentList != null ? studentList : new ArrayList<>();
        this.originalStudentList = new ArrayList<>(this.studentList); // Keep copy of original data
        this.listener = listener;
    }

    @NonNull
    @Override
    public StudentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student, parent, false);
        return new StudentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StudentViewHolder holder, int position) {
        StudentModel student = studentList.get(position);

        holder.tvFullName.setText(student.getFullName());
        holder.tvSection.setText(student.getSpecializationName() + " - " + student.getYearName());
        holder.tvCourse.setText(student.getCourseName());

        if (student.getProfileImage() != null && !student.getProfileImage().isEmpty()) {
            try {
                byte[] decodedBytes = android.util.Base64.decode(student.getProfileImage(), android.util.Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                holder.ivProfile.setImageBitmap(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
                holder.ivProfile.setImageResource(R.drawable.examinee_default);
            }
        } else {
            holder.ivProfile.setImageResource(R.drawable.examinee_default);
        }

        holder.ivEdit.setOnClickListener(v -> listener.onUpdate(student));
        holder.ivDelete.setOnClickListener(v -> listener.onDelete(student));
    }

    @Override
    public int getItemCount() {
        return studentList.size();
    }

    // 🔹 NEW METHOD: Update the list for search functionality
    public void updateList(List<StudentModel> filteredList) {
        this.studentList = filteredList;
        notifyDataSetChanged();
    }

    // 🔹 OPTIONAL: Method to reset to original list
    public void resetList() {
        this.studentList = new ArrayList<>(originalStudentList);
        notifyDataSetChanged();
    }

    // 🔹 OPTIONAL: Method to get current list (for count updates)
    public List<StudentModel> getCurrentList() {
        return studentList;
    }

    static class StudentViewHolder extends RecyclerView.ViewHolder {
        TextView tvFullName, tvSection, tvCourse;
        ImageView ivProfile, ivEdit, ivDelete;

        public StudentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFullName = itemView.findViewById(R.id.tvFullName);
            tvSection = itemView.findViewById(R.id.tvSection);
            tvCourse = itemView.findViewById(R.id.tvCourse);
            ivProfile = itemView.findViewById(R.id.ivProfile);
            ivEdit = itemView.findViewById(R.id.ivEdit);
            ivDelete = itemView.findViewById(R.id.ivDelete);
        }
    }
}