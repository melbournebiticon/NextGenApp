package com.example.nextgen.student;

public class ActivityModel {
    private String id, title, description, dueDate, courseDisplay, subject, subjectId;
    private String teacherName; // <--- add this

    public ActivityModel() {} // required for Firebase

    // ===== Getters =====
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getDueDate() { return dueDate; }
    public String getCourseDisplay() { return courseDisplay; }
    public String getSubject() { return subject; }
    public String getSubjectId() { return subjectId; }
    public String getTeacherName() { return teacherName; } // <--- add this

    // ===== Setters =====
    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public void setCourseDisplay(String courseDisplay) { this.courseDisplay = courseDisplay; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; } // <--- add this
}
