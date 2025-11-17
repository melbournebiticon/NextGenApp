package com.example.nextgen.student;

public class ActivityModel {
    private String id;
    private String activityId;   // ✅ for passing unique activity reference
    private String title;
    private String description;
    private String dueDate;
    private String courseDisplay;
    private String subject;
    private String subjectId;
    private String subjectCode;  // ✅ for passing subject code
    private String teacherName;

    private String mainTerm; // e.g., "1st Term", "2nd Term"
    private String subTerm;  // e.g., "Prelim", "Midterm", "Finals"


    public ActivityModel() {} // required for Firebase

    // ===== Getters =====
    public String getId() { return id; }
    public String getActivityId() { return activityId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getDueDate() { return dueDate; }
    public String getCourseDisplay() { return courseDisplay; }
    public String getSubject() { return subject; }
    public String getSubjectId() { return subjectId; }
    public String getSubjectCode() { return subjectCode; }
    public String getTeacherName() { return teacherName; }

    // ===== Setters =====
    public void setId(String id) { this.id = id; }
    public void setActivityId(String activityId) { this.activityId = activityId; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public void setCourseDisplay(String courseDisplay) { this.courseDisplay = courseDisplay; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getMainTerm() { return mainTerm; }
    public void setMainTerm(String mainTerm) { this.mainTerm = mainTerm; }

    public String getSubTerm() { return subTerm; }
    public void setSubTerm(String subTerm) { this.subTerm = subTerm; }

}
