package com.example.nextgen.student;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ActivityDetailsPagerAdapter extends FragmentStateAdapter {

    private final String subjectCode, subjectName, teacherName, description, dueDate;
    private final String activityId;

    public ActivityDetailsPagerAdapter(@NonNull FragmentActivity activity,
                                       String subjectCode,
                                       String subjectName,
                                       String teacherName,
                                       String description,
                                       String dueDate,
                                       String activityId) {
        super(activity);
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.teacherName = teacherName;
        this.description = description;
        this.dueDate = dueDate;
        this.activityId = activityId; // ✅ store it
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return ActivityDetailsFragment.newInstance(
                    subjectCode, subjectName, teacherName, description, dueDate
            );
        } else {
            // ✅ Pass both activityId and dueDate properly
            return ActivityMyWorkFragment.newInstance(activityId, dueDate);
        }
    }

    @Override
    public int getItemCount() {
        return 2; // Details + My Work
    }
}
