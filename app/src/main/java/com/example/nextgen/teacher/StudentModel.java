package com.example.nextgen.teacher;

public class StudentModel {
    private String id;
    private String fullName;
    private String courseName;
    private String courseId;

    public StudentModel() {
        // Firebase needs empty constructor
    }

    public StudentModel(String id, String fullName, String courseName, String courseId) {
        this.id = id;
        this.fullName = fullName;
        this.courseName = courseName;
        this.courseId = courseId;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getCourseId() {
        return courseId;
    }
}
