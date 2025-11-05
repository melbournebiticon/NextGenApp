package com.example.nextgen.teacher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.example.nextgen.R;

public class ExamAdapter extends RecyclerView.Adapter<ExamAdapter.ExamViewHolder> {

    private final Context context;
    private final List<Exam> examList;
    private final OnExamActionListener listener;

    // Interface for button actions
    public interface OnExamActionListener {
        void onEdit(Exam exam);
        void onDelete(Exam exam);
        void onViewStudents(Exam exam);
        void onGenerate(Exam exam);
        void onActivate(Exam exam, boolean isActive);
    }

    public ExamAdapter(Context context, List<Exam> examList, OnExamActionListener listener) {
        this.context = context;
        this.examList = examList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ExamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_exam, parent, false);
        return new ExamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExamViewHolder holder, int position) {
        Exam exam = examList.get(position);

        holder.tvExamName.setText(exam.getExamName());
        holder.tvSubject.setText("Subject: " + exam.getSubject());
        holder.tvSection.setText("Section: " + exam.getSection());
        holder.tvSchedule.setText("Schedule: " + exam.getFormattedSchedule());


        // Handle checkbox properly to prevent multiple triggers due to recycling
        holder.checkActivate.setOnCheckedChangeListener(null);
        holder.checkActivate.setChecked(exam.isActive());
        holder.checkActivate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            exam.setActive(isChecked);
            listener.onActivate(exam, isChecked);
        });

        // Button listeners
        holder.btnEdit.setOnClickListener(v -> listener.onEdit(exam));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(exam));
        holder.btnReset.setOnClickListener(v -> listener.onViewStudents(exam));
        holder.btnReset.setText("View"); // optional, change button text
        holder.btnGenerate.setOnClickListener(v -> listener.onGenerate(exam));
    }

    @Override
    public int getItemCount() {
        return examList.size();
    }

    static class ExamViewHolder extends RecyclerView.ViewHolder {
        TextView tvExamName, tvSubject, tvSection, tvSchedule;
        Button btnEdit, btnDelete, btnReset, btnGenerate;
        CheckBox checkActivate;

        public ExamViewHolder(@NonNull View itemView) {
            super(itemView);
            tvExamName = itemView.findViewById(R.id.tvExamName);
            tvSubject = itemView.findViewById(R.id.tvSubject);
            tvSection = itemView.findViewById(R.id.tvSection);
            tvSchedule = itemView.findViewById(R.id.tvSchedule);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnReset = itemView.findViewById(R.id.btnReset);
            btnGenerate = itemView.findViewById(R.id.btnGenerate);
            checkActivate = itemView.findViewById(R.id.checkActivate);
        }
    }
}
