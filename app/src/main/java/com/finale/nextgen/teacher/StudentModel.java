package com.finale.nextgen.teacher;

import java.io.Serializable;

public class StudentModel implements Serializable {

    // ===== CONSTANTS FOR ATTENDANCE =====
    public static final String PRESENT = "Present";
    public static final String ABSENT = "Absent";
    public static final String LATE = "Late";
    public static final String EXCUSED = "Excused";

    // ===== BASIC INFO =====
    private String studentId;
    private String fullName;

    private String courseId;
    private String courseName;

    private String specializationName;
    private String yearName;
    private String sectionName;

    private String uid;    // Firebase Auth UID
    private String dbKey;  // Firebase Realtime DB key

    // ===== EXTRA FIELDS =====
    private String birthday;
    private String profileImage;
    private String password;
    private String contact;
    private String email;

    // ===== FOR SUBMISSIONS (OPTIONAL) =====
    private SubmissionModel submission;

    // ===== ATTENDANCE =====
    private String attendanceStatus; // NULL by default
    private String term; // Prelim, Midterm, Final

    // ===== CONSTRUCTOR (REQUIRED) =====
    public StudentModel() {}

    public StudentModel(String studentId, String fullName, String courseId,
                        String courseName, String sectionName) {

        this.studentId = studentId;
        this.fullName = fullName;
        this.courseId = courseId;
        this.courseName = courseName;
        this.sectionName = sectionName;
    }

    // ===== BASIC GETTERS & SETTERS =====

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getSpecializationName() {
        return specializationName;
    }

    public void setSpecializationName(String specializationName) {
        this.specializationName = specializationName;
    }

    public String getYearName() {
        return yearName;
    }

    public void setYearName(String yearName) {
        this.yearName = yearName;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getDbKey() {
        return dbKey;
    }

    public void setDbKey(String dbKey) {
        this.dbKey = dbKey;
    }

    // ===== SUBMISSION =====

    public SubmissionModel getSubmission() {
        return submission;
    }

    public void setSubmission(SubmissionModel submission) {
        this.submission = submission;
    }

    // ===== ATTENDANCE LOGIC =====

    // No default value
    public String getAttendanceStatus() {
        return attendanceStatus;
    }

    // Ito ang tatawagin kapag pinindot ang Present/Late/etc
    public void setAttendanceStatus(String attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    // Quick button methods (optional gamitin sa adapter)
    public void markPresent() {
        this.attendanceStatus = PRESENT;
    }

    public void markAbsent() {
        this.attendanceStatus = ABSENT;
    }

    public void markLate() {
        this.attendanceStatus = LATE;
    }

    public void markExcused() {
        this.attendanceStatus = EXCUSED;
    }

    // For checking if napindot na siya
    public boolean hasMarkedAttendance() {
        return attendanceStatus != null && !attendanceStatus.isEmpty();
    }

    public void clearAttendance() {
        this.attendanceStatus = null;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    // ID helpers
    public void setId(String key) {
        this.dbKey = key;
    }

    public String getId() {
        return dbKey;
    }

    // ===== EXTRA FIELDS GETTERS & SETTERS =====

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return "";
    }
}
