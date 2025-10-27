package com.example.nextgen.admin;

public class StudentModel {
    private String studentId;
    private String fullName;
    private String birthday;
    private String email;
    private String contact;
    private String courseId;
    private String courseName;
    private String specializationName;
    private String yearName;
    private String profileImage; // URL or path
    private String password;     // auto from birthday

    private String sectionName;

    private String uid;
    public StudentModel() {}

    public StudentModel(String studentId, String fullName, String birthday, String email,
                        String contact, String courseId, String courseName,
                        String specializationName, String yearName,
                        String sectionName, String profileImage, String password,
                        String uid) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.birthday = birthday;
        this.email = email;
        this.contact = contact;
        this.courseId = courseId;
        this.courseName = courseName;
        this.specializationName = specializationName;
        this.yearName = yearName;
        this.sectionName = sectionName;
        this.profileImage = profileImage;
        this.password = password;
        this.uid = uid;
    }



    // Getters & Setters
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getSpecializationName() { return specializationName; }
    public void setSpecializationName(String specializationName) { this.specializationName = specializationName; }

    public String getYearName() { return yearName; }
    public void setYearName(String yearName) { this.yearName = yearName; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }


    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

}
