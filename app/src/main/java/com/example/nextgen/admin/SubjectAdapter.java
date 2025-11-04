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
    private final OnSubjectActionListener listener;

    // interface for edit/delete actions
    public interface OnSubjectActionListener {
        void onEdit(SubjectModel subject);
        void onDelete(SubjectModel subject);
    }

    public SubjectAdapter(List<SubjectModel> subjectList, OnSubjectActionListener listener) {
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

        holder.tvCode.setText(subject.getCode());
        holder.tvName.setText(subject.getName());
        holder.tvCourseInfo.setText(
                subject.getCourseName() + " - " +
                        subject.getSpecializationName() + " - " +
                        subject.getYearName() + " - " +
                        subject.getSectionName()
        );

        holder.btnEdit.setOnClickListener(v -> listener.onEdit(subject));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(subject));
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
