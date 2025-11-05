package com.example.nextgen.teacher;

public class StudentModel {
    private String studentId;
    private String fullName;
    private String courseId;
    private String courseName;
    private String sectionName;   // ADD THIS
    private String yearName;      // optional, if you want
    private String specializationName; // optional

    public StudentModel() {
        // Firebase needs empty constructor
    }

    public StudentModel(String studentId, String fullName, String courseId, String courseName, String sectionName) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.courseId = courseId;
        this.courseName = courseName;
        this.sectionName = sectionName;
    }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }

    public String getYearName() { return yearName; }
    public void setYearName(String yearName) { this.yearName = yearName; }

    public String getSpecializationName() { return specializationName; }
    public void setSpecializationName(String specializationName) { this.specializationName = specializationName; }
}
