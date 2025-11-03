package com.example.nextgen.admin;

public class SubjectModel {
    public String id;
    public String code;
    public String name;
    public String courseId;
    public String courseName;
    public String specializationName;
    public String yearName;
    public String sectionName;

    public SubjectModel() {}

    public SubjectModel(String id, String code, String name, String courseId, String courseName,
                        String specializationName, String yearName, String sectionName) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.courseId = courseId;
        this.courseName = courseName;
        this.specializationName = specializationName;
        this.yearName = yearName;
        this.sectionName = sectionName;
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public String getSpecializationName() { return specializationName; }
    public String getYearName() { return yearName; }
    public String getSectionName() { return sectionName; }

    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public void setSpecializationName(String specializationName) { this.specializationName = specializationName; }
    public void setYearName(String yearName) { this.yearName = yearName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }
}