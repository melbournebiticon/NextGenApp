package com.example.nextgen.admin;

public class SubjectOption {
    private String courseId;
    private String courseName;
    private String specializationName;
    private String yearName;
    private String sectionName;

    public SubjectOption() { }

    public SubjectOption(String courseId, String courseName, String specializationName,
                         String yearName, String sectionName) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.specializationName = specializationName;
        this.yearName = yearName;
        this.sectionName = sectionName;
    }

    public SubjectOption(String id, char[] name, String specializationName, String yearName, String sectionName) {
    }

    public String getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public String getSpecializationName() { return specializationName; }
    public String getYearName() { return yearName; }
    public String getSectionName() { return sectionName; }

    @Override
    public String toString() {
        return courseName + " - " + specializationName + " - " + yearName + " - " + sectionName;
    }
}
