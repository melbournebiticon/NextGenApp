package com.example.nextgen.admin;

public class SubjectModel {
    private String id, code, name, courseId, courseName, specializationName, yearName, sectionName;

    // 🔹 Add this field
    private boolean selected = false;

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
        this.selected = false; // default
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public String getSpecializationName() { return specializationName; }
    public String getYearName() { return yearName; }
    public String getSectionName() { return sectionName; }

    // 🔹 Add these for selection
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}
