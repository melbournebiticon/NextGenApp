package com.example.nextgen.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
<<<<<<< HEAD
import android.widget.Toast;

=======
>>>>>>> origin/pushnyodito4
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.nextgen.R;
import java.util.List;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {

    private final List<SubjectModel> subjectList;
<<<<<<< HEAD
    private final OnItemClickListener listener;

    // Interface for click listeners
    public interface OnItemClickListener {
        void onEditClick(SubjectModel subject);
        void onDeleteClick(SubjectModel subject);
    }

    public SubjectAdapter(List<SubjectModel> subjectList, OnItemClickListener listener) {
=======
    private final OnSubjectActionListener listener;

    // interface for edit/delete actions
    public interface OnSubjectActionListener {
        void onEdit(SubjectModel subject);
        void onDelete(SubjectModel subject);
    }

    public SubjectAdapter(List<SubjectModel> subjectList, OnSubjectActionListener listener) {
>>>>>>> origin/pushnyodito4
        this.subjectList = subjectList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject, parent, false);
        return new SubjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        SubjectModel subject = subjectList.get(position);

<<<<<<< HEAD
        // Set subject data - CORRECTED METHOD NAMES
=======
>>>>>>> origin/pushnyodito4
        holder.tvCode.setText(subject.getCode());
        holder.tvName.setText(subject.getName());
        holder.tvCourseInfo.setText(
                subject.getCourseName() + " - " +
                        subject.getSpecializationName() + " - " +
                        subject.getYearName() + " - " +
                        subject.getSectionName()
        );

<<<<<<< HEAD
        // Set click listeners for edit and delete buttons
        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(subject);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(subject);
            }
        });

        // Optional: Set click listener for entire item
        holder.itemView.setOnClickListener(v -> {
            // You can add item click functionality here if needed
            Toast.makeText(holder.itemView.getContext(),
                    "Subject: " + subject.getName(), Toast.LENGTH_SHORT).show();
        });
=======
        holder.btnEdit.setOnClickListener(v -> listener.onEdit(subject));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(subject));
>>>>>>> origin/pushnyodito4
    }

    @Override
    public int getItemCount() {
        return subjectList.size();
    }

    // Method to update data
    public void updateData(List<SubjectModel> newSubjectList) {
        subjectList.clear();
        subjectList.addAll(newSubjectList);
        notifyDataSetChanged();
    }

    // Method to remove item
    public void removeItem(int position) {
        if (position >= 0 && position < subjectList.size()) {
            subjectList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public static class SubjectViewHolder extends RecyclerView.ViewHolder {
        TextView tvCode, tvName, tvCourseInfo;
        ImageButton btnEdit, btnDelete;

        public SubjectViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCode = itemView.findViewById(R.id.tvSubjectCode);
            tvName = itemView.findViewById(R.id.tvSubjectName);
            tvCourseInfo = itemView.findViewById(R.id.tvSubjectCourseInfo);
<<<<<<< HEAD
            btnEdit = itemView.findViewById(R.id.btnEditSubject);
            btnDelete = itemView.findViewById(R.id.btnDeleteSubject);
=======
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
>>>>>>> origin/pushnyodito4
        }
    }
}