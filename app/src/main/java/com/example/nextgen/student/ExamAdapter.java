package com.example.nextgen.student;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;

import java.util.List;

public class ExamAdapter extends RecyclerView.Adapter<ExamAdapter.ExamViewHolder> {

    private Context context;
    private List<ExamModel> examList;

    private int examId;

    public ExamAdapter(Context context, List<ExamModel> examList) {
        this.context = context;
        this.examList = examList;
    }

    @NonNull
    @Override
    public ExamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_student_exam_list, parent, false);
        return new ExamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExamViewHolder holder, int position) {
        ExamModel exam = examList.get(position);
        holder.tvExamTitle.setText(exam.getExamTitle());
        holder.tvCourseDisplay.setText(exam.getCourseDisplay());
        holder.tvTeacherName.setText("Teacher: " + exam.getTeacherName());

        holder.itemView.setOnClickListener(v -> {
            android.util.Log.d("ExamAdapter", "Opening TakeExamActivity with examId=" + exam.getExamId());
            Intent intent = new Intent(context, TakeExamActivity.class);
            intent.putExtra("examId", exam.getExamId());
            intent.putExtra("examTitle", exam.getExamTitle());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return examList.size();
    }

    public static class ExamViewHolder extends RecyclerView.ViewHolder {
        TextView tvExamTitle, tvCourseDisplay, tvTeacherName;

        public ExamViewHolder(@NonNull View itemView) {
            super(itemView);
            tvExamTitle = itemView.findViewById(R.id.tvExamTitle);
            tvCourseDisplay = itemView.findViewById(R.id.tvCourseDisplay);
            tvTeacherName = itemView.findViewById(R.id.tvTeacherName);
        }
    }
    public int getExamId() {
        return examId;
    }
}
