package com.example.nextgen.teacher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;

import java.util.List;

public class StudentExamAdapter extends RecyclerView.Adapter<StudentExamAdapter.ViewHolder> {

    private final List<StudentExamStatus> students;

    public StudentExamAdapter(List<StudentExamStatus> students) {
        this.students = students;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_view_exam_teacher, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudentExamStatus student = students.get(position);

        holder.tvStudentName.setText(student.getFullName());

        // Show Present / Ongoing / Absent
        if (!student.isPresent()) {
            holder.tvStudentStatus.setText("Absent");
        } else if (student.isOngoing()) {
            holder.tvStudentStatus.setText("Ongoing");
        } else {
            holder.tvStudentStatus.setText("Present");
        }

        holder.tvStudentCounter.setText("Answered: " + student.getQuestionsAnswered());
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStudentName, tvStudentStatus, tvStudentCounter;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvStudentStatus = itemView.findViewById(R.id.tvStudentStatus);
            tvStudentCounter = itemView.findViewById(R.id.tvStudentCounter);
        }
    }
}
