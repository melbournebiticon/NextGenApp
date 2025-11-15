package com.example.nextgen.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;

import java.util.List;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {

    private final List<SubjectModel> subjectList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onEditClick(int position);
        void onDeleteClick(int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public SubjectAdapter(List<SubjectModel> subjectList) {
        this.subjectList = subjectList;
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject, parent, false); // Make sure this matches your XML filename
        return new SubjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        SubjectModel subject = subjectList.get(position);
        holder.tvCode.setText(subject.getCode());
        holder.tvName.setText(subject.getName());
        holder.tvCourseInfo.setText(
                subject.getCourseName() + " - " +
                        subject.getSpecializationName() + " - " +
                        subject.getYearName() + " - " +
                        subject.getSectionName()
        );

        // Set click listeners for buttons
        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(position);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return subjectList.size();
    }

    public static class SubjectViewHolder extends RecyclerView.ViewHolder {
        TextView tvCode, tvName, tvCourseInfo;
        ImageButton btnEdit, btnDelete;

        public SubjectViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCode = itemView.findViewById(R.id.tvSubjectCode);
            tvName = itemView.findViewById(R.id.tvSubjectName);
            tvCourseInfo = itemView.findViewById(R.id.tvSubjectCourseInfo);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}