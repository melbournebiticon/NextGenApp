package com.example.nextgen.sync;

/**
 * Holder for both Exam and Quiz metadata.
 * Works for exam QR payloads and quiz QR payloads.
 */
public class ExamMetadata {

    // ---- EXISTING EXAM FIELDS (unchanged) ----
    public String examTitle;
    public Long scheduledAt; // millis
    public Integer durationMinutes;
    public String teacherName;
    public String courseName;
    public String specializationName;
    public String yearName;
    public String sectionName;
    public String courseDisplay;
    public String teacherId;

    // ---- ADDED QUIZ FIELDS ----
    public String quizName;       // from QR: quizName
    public String subjectName;    // from QR: subjectName
    public String quizSection;    // from QR: section (for quiz)
    public String quizTeacherName; // from QR: teacherName
    public String quizTeacherId;   // from QR: teacherId

    public ExamMetadata() {}
}
