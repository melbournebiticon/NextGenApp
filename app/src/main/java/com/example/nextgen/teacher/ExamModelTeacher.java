package com.example.nextgen.teacher;

public class ExamModelTeacher {
    private String examId;
    private String examTitle;
    private String subjectName;
    private String courseId;
    private String courseName;
    private String specializationName;
    private String yearName;
    private String sectionName;
    private String teacherId;
    private String teacherName;
    private int durationMinutes;
    private long scheduledAt;
    private boolean active;
    private String createdAt;

    private String courseDisplay;

    // ===== Default constructor (required for Firebase) =====
    public ExamModelTeacher() {}

    // ===== Constructor for creating new exams =====
    public ExamModelTeacher(String examId,
                     String examTitle,
                     String subjectName,
                     String courseId,
                     String courseName,
                     String specializationName,
                     String yearName,
                     String sectionName,
                     String teacherId,
                     String teacherName,
                     int durationMinutes,
                     long scheduledAt,
                     boolean active,
                     String createdAt, String courseDisplay) {
        this.examId = examId;
        this.examTitle = examTitle;
        this.subjectName = subjectName;
        this.courseId = courseId;
        this.courseName = courseName;
        this.specializationName = specializationName;
        this.yearName = yearName;
        this.sectionName = sectionName;
        this.teacherId = teacherId;
        this.teacherName = teacherName;
        this.durationMinutes = durationMinutes;
        this.scheduledAt = scheduledAt;
        this.active = active;
        this.createdAt = createdAt;
        this.courseDisplay = courseDisplay;
    }

    // ===== Getters and Setters =====
    public String getExamId() { return examId; }
    public void setExamId(String examId) { this.examId = examId; }

    public String getExamTitle() { return examTitle; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getSpecializationName() { return specializationName; }
    public void setSpecializationName(String specializationName) { this.specializationName = specializationName; }

    public String getYearName() { return yearName; }
    public void setYearName(String yearName) { this.yearName = yearName; }

    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }

    public String getTeacherId() { return teacherId; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public long getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(long scheduledAt) { this.scheduledAt = scheduledAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getCourseDisplay() {
        return courseDisplay;
    }

    public void setCourseDisplay(String courseDisplay) {
        this.courseDisplay = courseDisplay;
    }

}
