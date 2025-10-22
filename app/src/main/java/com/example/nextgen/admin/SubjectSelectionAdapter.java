package com.example.nextgen.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;

import java.util.ArrayList;
import java.util.List;

public class SubjectSelectionAdapter extends RecyclerView.Adapter<SubjectSelectionAdapter.SubjectViewHolder> {

    private final List<SubjectModel> subjectList;
    private final List<SubjectModel> selectedSubjects = new ArrayList<>();

    public SubjectSelectionAdapter(List<SubjectModel> subjectList) {
        this.subjectList = subjectList;
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject_selectable, parent, false); // ✅ new layout with checkbox
        return new SubjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        SubjectModel subject = subjectList.get(position);

        holder.tvName.setText(subject.getName());
        holder.tvCourseInfo.setText(subject.getCourseName() + " - " +
                subject.getSpecializationName() + " - " +
                subject.getYearName() + " - " +
                subject.getSectionName());

        // ✅ Sync checkbox with state
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(selectedSubjects.contains(subject));

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!selectedSubjects.contains(subject)) {
                    selectedSubjects.add(subject);
                }
            } else {
                selectedSubjects.remove(subject);
            }
        });
    }

    @Override
    public int getItemCount() {
        return subjectList.size();
    }

    public List<SubjectModel> getSelectedSubjects() {
        return selectedSubjects;
    }

    static class SubjectViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCourseInfo;
        CheckBox checkBox;

        SubjectViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvSubjectName);
            tvCourseInfo = itemView.findViewById(R.id.tvSubjectCourseInfo);
            checkBox = itemView.findViewById(R.id.cbSelectSubject);
        }
    }
    public void setPreselectedSubjects(List<String> assignedSubjects) {
        if (assignedSubjects == null) return;

        for (SubjectModel subject : subjectList) {
            subject.setSelected(assignedSubjects.contains(subject.getName()));
        }
        notifyDataSetChanged();
    }

}
