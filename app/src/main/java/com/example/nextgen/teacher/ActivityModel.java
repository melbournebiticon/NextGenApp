package com.example.nextgen.teacher;

public class ActivityModel {
    private String id, teacherId, title, description, subject, subjectCode, subjectId, courseDisplay;

    public ActivityModel() {} // required for Firebase

    // getters and setters
    public String getId() { return id; }
    public String getTeacherId() { return teacherId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSubject() { return subject; }
    public String getSubjectCode() { return subjectCode; }
    public String getSubjectId() { return subjectId; }
    public String getCourseDisplay() { return courseDisplay; }
}

