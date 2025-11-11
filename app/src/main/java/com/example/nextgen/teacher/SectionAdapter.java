package com.example.nextgen.teacher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import java.util.List;

public class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.ViewHolder> {

    private List<StudentModel> studentList;

    public SectionAdapter(List<StudentModel> studentList) {
        this.studentList = studentList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_enrolled, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudentModel student = studentList.get(position);
        holder.tvStudentName.setText(student.getFullName());
        holder.tvStudentId.setText(student.getStudentId());

        // Check submission
        if (student.getSubmission() != null) {
            // Show file name or score
            holder.tvSubmission.setText(student.getSubmission().getFileName() + " (" + student.getSubmission().getScore() + ")");
        } else {
            holder.tvSubmission.setText("No submission");
        }
    }


    @Override
    public int getItemCount() {
        return studentList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStudentName, tvStudentId, tvSubmission;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvStudentId = itemView.findViewById(R.id.tvStudentId);
            tvSubmission = itemView.findViewById(R.id.tvSubmission); // add this in your item_student_enrolled.xml
        }

    }
}
