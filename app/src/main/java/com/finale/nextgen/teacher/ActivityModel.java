package com.finale.nextgen.teacher;

public class ActivityModel {
    private String id;
    private String teacherId;
    private String teacherName; // Added
    private String title;
    private String description;
    private String subject;
    private String subjectCode;
    private String subjectId;
    private String courseDisplay;
    private String dueDate; // Added
    private String mainTerm; // Added
    private String subTerm; // Added
    private String maxScore; // Added
    private long createdAt; // Added, usually stored as long/timestamp

    public ActivityModel() {} // required for Firebase

    // --- GETTERS ---

    public String getId() { return id; }
    public String getTeacherId() { return teacherId; }
    public String getTeacherName() { return teacherName; } // Getter for teacherName
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSubject() { return subject; }
    public String getSubjectCode() { return subjectCode; }
    public String getSubjectId() { return subjectId; }
    public String getCourseDisplay() { return courseDisplay; }
    public String getDueDate() { return dueDate; } // Getter for dueDate
    public String getMainTerm() { return mainTerm; } // Getter for mainTerm
    public String getSubTerm() { return subTerm; } // Getter for subTerm
    public String getMaxScore() { return maxScore; } // Getter for maxScore
    public long getCreatedAt() { return createdAt; } // Getter for createdAt

    // --- SETTERS (Omitted for brevity, but Firebase reflection may not strictly need them if all data is read) ---
    // Kung kailangan ng manual data assignment, idagdag ang setters:

    public void setId(String id) { this.id = id; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public void setCourseDisplay(String courseDisplay) { this.courseDisplay = courseDisplay; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public void setMainTerm(String mainTerm) { this.mainTerm = mainTerm; }
    public void setSubTerm(String subTerm) { this.subTerm = subTerm; }
    public void setMaxScore(String maxScore) { this.maxScore = maxScore; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}