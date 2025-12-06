package com.finale.nextgen.admin;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.finale.nextgen.R;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CourseSelectionAdapter extends RecyclerView.Adapter<CourseSelectionAdapter.ViewHolder> {

    private final Context context;
    private final List<CourseModel> courseList;
    private final Map<String, List<SubjectModel>> selectedSubjectsPerCourse = new HashMap<>();
    private OnCourseSelectionChangedListener selectionChangedListener;

    public CourseSelectionAdapter(Context context, List<CourseModel> courseList) {
        this.context = context;
        this.courseList = courseList;
    }

    // Overloaded constructor for preselected courses by IDs
    public CourseSelectionAdapter(Context context, List<CourseModel> courseList, List<String> preselectedCourseIds) {
        this(context, courseList);
        if (preselectedCourseIds != null) {
            for (String id : preselectedCourseIds) {
                selectedSubjectsPerCourse.put(id, new ArrayList<>()); // empty list initially
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_course_selection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CourseModel course = courseList.get(position);

        holder.tvCourseName.setText(course.getName() + " - " +
                course.getSpecializationName() + " - " +
                course.getYearName() + " - " +
                course.getSectionName());

        holder.checkBox.setChecked(selectedSubjectsPerCourse.containsKey(course.getId()));

        holder.checkBox.setOnClickListener(v -> {
            if (holder.checkBox.isChecked()) {
                loadSubjectsForCourse(course);
            } else {
                selectedSubjectsPerCourse.remove(course.getId());
                notifySelectionChanged();
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (!holder.checkBox.isChecked()) {
                holder.checkBox.setChecked(true);
                loadSubjectsForCourse(course);
            } else {
                holder.checkBox.setChecked(false);
                selectedSubjectsPerCourse.remove(course.getId());
                notifySelectionChanged();
            }
        });
    }

    private void loadSubjectsForCourse(CourseModel course) {
        DatabaseReference subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        subjectsRef.orderByChild("courseId").equalTo(course.getId())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<SubjectModel> subjects = new ArrayList<>();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            SubjectModel s = ds.getValue(SubjectModel.class);
                            if (s != null) subjects.add(s);
                        }
                        showSubjectSelectionDialog(course, subjects);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(context, "Failed to load subjects", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showSubjectSelectionDialog(CourseModel course, List<SubjectModel> subjects) {
        boolean[] checkedItems = new boolean[subjects.size()];
        String[] subjectNames = new String[subjects.size()];

        List<SubjectModel> preSelected = selectedSubjectsPerCourse.get(course.getId());

        for (int i = 0; i < subjects.size(); i++) {
            subjectNames[i] = subjects.get(i).getName();
            if (preSelected != null) {
                for (SubjectModel s : preSelected) {
                    if (s.getId().equals(subjects.get(i).getId())) {
                        checkedItems[i] = true;
                        break;
                    }
                }
            }
        }

        new AlertDialog.Builder(context)
                .setTitle("Select Subjects for " + course.getName())
                .setMultiChoiceItems(subjectNames, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                .setPositiveButton("OK", (dialog, which) -> {
                    List<SubjectModel> selected = new ArrayList<>();
                    for (int i = 0; i < subjects.size(); i++) {
                        if (checkedItems[i]) selected.add(subjects.get(i));
                    }
                    if (selected.isEmpty()) {
                        selectedSubjectsPerCourse.remove(course.getId());
                    } else {
                        selectedSubjectsPerCourse.put(course.getId(), selected);
                    }
                    notifySelectionChanged();
                    notifyDataSetChanged();
                })
                .setNegativeButton("Cancel", (dialog, which) -> notifyDataSetChanged())
                .show();
    }

    public List<CourseModel> getSelectedCourses() {
        List<CourseModel> selected = new ArrayList<>();
        for (CourseModel c : courseList) {
            if (selectedSubjectsPerCourse.containsKey(c.getId())) {
                selected.add(c);
            }
        }
        return selected;
    }

    public Map<String, List<SubjectModel>> getSelectedCoursesWithSubjects() {
        return selectedSubjectsPerCourse;
    }

    public void setOnCourseSelectionChanged(OnCourseSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public void notifySelectionChanged() {
        if (selectionChangedListener != null) selectionChangedListener.onChanged();
    }

    public void notifySelectionChangedForAll() {
        if (selectionChangedListener != null) selectionChangedListener.onChanged();
    }

    public void setPreselectedCoursesById(List<String> preselectedCourseIds) {
        if (preselectedCourseIds != null) {
            for (String id : preselectedCourseIds) {
                if (!selectedSubjectsPerCourse.containsKey(id)) {
                    selectedSubjectsPerCourse.put(id, new ArrayList<>());
                }
            }
            notifySelectionChanged();
        }
    }

    @Override
    public int getItemCount() {
        return courseList.size();
    }

    public interface OnCourseSelectionChangedListener {
        void onChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCourseName;
        CheckBox checkBox;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCourseName = itemView.findViewById(R.id.tvCourseName);
            checkBox = itemView.findViewById(R.id.checkBoxCourse);
        }
    }
}
