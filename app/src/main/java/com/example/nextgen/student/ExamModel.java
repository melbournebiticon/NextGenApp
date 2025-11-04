package com.example.nextgen.student;

public class ExamModel {
    private String examId;
    private String examTitle;
    private String courseId;
    private String courseName;
    private String specializationName;
    private String yearName;
    private String sectionName;
    private String teacherName;
    private String createdAt;
    private boolean active;

    public ExamModel() {}

    public ExamModel(String examId, String examTitle, String courseId, String courseName,
                     String specializationName, String yearName, String sectionName,
                     String teacherName, String createdAt) {
        this.examId = examId;
        this.examTitle = examTitle;
        this.courseId = courseId;
        this.courseName = courseName;
        this.specializationName = specializationName;
        this.yearName = yearName;
        this.sectionName = sectionName;
        this.teacherName = teacherName;
        this.createdAt = createdAt;
    }

    public String getExamId() { return examId; }
    public String getExamTitle() { return examTitle; }
    public String getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public String getSpecializationName() { return specializationName; }
    public String getYearName() { return yearName; }
    public String getSectionName() { return sectionName; }
    public String getTeacherName() { return teacherName; }
    public String getCreatedAt() { return createdAt; }

    public void setExamId(String examId) { this.examId = examId; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public void setSpecializationName(String specializationName) { this.specializationName = specializationName; }
    public void setYearName(String yearName) { this.yearName = yearName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    // Add setter
    public void setActive(boolean active) { this.active = active; }

    public String getCourseDisplay() {
        StringBuilder display = new StringBuilder();

        if (courseName != null && !courseName.isEmpty()) {
            display.append(courseName);
        }

        if (specializationName != null && !specializationName.isEmpty()) {
            display.append(" - ").append(specializationName);
        }

        if (yearName != null && !yearName.isEmpty()) {
            display.append(" - ").append(yearName);
        }

        if (sectionName != null && !sectionName.isEmpty()) {
            display.append(" - ").append(sectionName);
        }

        return display.toString();
    }
    public boolean isActive() {
        return active;
    }

}
