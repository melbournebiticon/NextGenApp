package com.example.nextgen.sync;

/**
 * Simple holder for exam metadata that you may embed in QR payloads
 * or fetch from Firebase before saving locally.
 */
public class ExamMetadata {
    public String examTitle;
    public Long scheduledAt; // millis
    public Integer durationMinutes;
    public String teacherName;
    public String courseName;
    public String specializationName;
    public String yearName;
    public String sectionName;
    public String courseDisplay;

    public ExamMetadata() {}
}