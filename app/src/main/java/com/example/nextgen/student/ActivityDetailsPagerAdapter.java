package com.example.nextgen.student;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ActivityDetailsPagerAdapter extends FragmentStateAdapter {

    private final String subjectCode;
    private final String subjectName;
    private final String teacherName;
    private final String description;
    private final String dueDate;
    private final String activityId;
    private final String mainTerm;
    private final String subTerm;
    private final String maxScore;

    public ActivityDetailsPagerAdapter(@NonNull FragmentActivity activity,
                                       String subjectCode,
                                       String subjectName,
                                       String teacherName,
                                       String description,
                                       String dueDate,
                                       String activityId,
                                       String mainTerm,
                                       String subTerm,
                                       String maxScore) {
        super(activity);
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.teacherName = teacherName;
        this.description = description;
        this.dueDate = dueDate;
        this.activityId = activityId;
        this.mainTerm = mainTerm;
        this.subTerm = subTerm;
        this.maxScore = maxScore;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: // Details tab
                return ActivityDetailsFragment.newInstance(
                        subjectCode,
                        subjectName,
                        teacherName,
                        description,
                        dueDate,
                        mainTerm,
                        subTerm,
                        maxScore // Pass Max Score
                );
            case 1: // My Work tab
                return ActivityMyWorkFragment.newInstance(activityId, maxScore);
            default:
                throw new IllegalArgumentException("Invalid tab position: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return 2; // Details + My Work
    }

    public void setActivityId(String activityId) {
    }
}
