package com.example.nextgen.offline;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "offline_exams")
public class ExamEntity {

    @PrimaryKey
    @NonNull
    public String examId;

    public String examTitle;

    public String courseId;
    public String courseName;
    public String specializationName;
    public String yearName;
    public String sectionName;

    public String teacherName;
    public String createdAt;

    public boolean active;
    public Integer durationMinutes = 0;
    public Long scheduledAt = 0L;

    public String scheduledDateDisplay;
    public String status;
    public boolean isAvailable;
    public boolean present;

    // For filtering per student (optional, not PK)
    public String studentUid;
}