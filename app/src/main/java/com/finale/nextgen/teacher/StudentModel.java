package com.finale.nextgen.teacher;

public class StudentModel {
    private String studentId;
    private String fullName;
    private String courseId;
    private String courseName;
    private String sectionName;
    private String yearName;
    private String specializationName;
    private String uid;

    // 🔑 ADDED: Field to store the Firebase Database Key (ds.getKey())
    private String dbKey;

    // Add this to store the submission for this student
    private SubmissionModel submission;

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

    // ===== Getters and Setters for existing fields =====
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

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    // 🔑 ADDED: Getter and setter for dbKey
    public String getDbKey() {
        return dbKey;
    }

    public void setDbKey(String dbKey) {
        this.dbKey = dbKey;
    }

    // Getter and setter for submission
    public SubmissionModel getSubmission() { return submission; }
    public void setSubmission(SubmissionModel submission) { this.submission = submission; }
}