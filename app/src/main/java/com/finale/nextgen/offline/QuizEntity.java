package com.finale.nextgen.offline;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Room entity to cache AvailableQuizzes for offline display.
 */
@Entity(tableName = "cached_quizzes")
public class QuizEntity {
    @PrimaryKey
    @NonNull
    public String quizId;

    public String quizName;
    public String teacherName;
    public String subjectName;
    public String courseName;
    public String sectionName;
    public String specializationName;
    public String yearName;

    // nullable wrappers
    public Long scheduledAt;
    public Integer durationMinutes;
    public Long availableAt;
    public Boolean active;

    // optimistic local flag
    public Boolean present;

    // timestamp when saved locally (ms)
    public Long cachedAt;
}