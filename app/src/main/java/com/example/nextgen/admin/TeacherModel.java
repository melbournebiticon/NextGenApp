package com.example.nextgen.admin;

import java.util.List;

public class TeacherModel {
    private String id;
    private String fullName;
    private String displayName;
    private String birthday;
    private String email;
    private List<String> courseIds;
    private List<String> courseDisplays;
    private List<String> assignedSubjects;
    private String password;

    // Empty constructor required for Firebase
    public TeacherModel() { }

    public TeacherModel(String id, String fullName, String displayName, String birthday,
                        String email, List<String> courseIds, List<String> courseDisplays,
                        List<String> assignedSubjects, String password) {
        this.id = id;
        this.fullName = fullName;
        this.displayName = displayName;
        this.birthday = birthday;
        this.email = email;
        this.courseIds = courseIds;
        this.courseDisplays = courseDisplays;
        this.assignedSubjects = assignedSubjects;
        this.password = password;
    }

    // ✅ Getters
    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getDisplayName() { return displayName; }
    public String getBirthday() { return birthday; }
    public String getEmail() { return email; }
    public List<String> getCourseIds() { return courseIds; }
    public List<String> getCourseDisplays() { return courseDisplays; }
    public List<String> getAssignedSubjects() { return assignedSubjects; }
    public String getPassword() { return password; }

    // ✅ Setters
    public void setId(String id) { this.id = id; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setBirthday(String birthday) { this.birthday = birthday; }
    public void setEmail(String email) { this.email = email; }
    public void setCourseIds(List<String> courseIds) { this.courseIds = courseIds; }
    public void setCourseDisplays(List<String> courseDisplays) { this.courseDisplays = courseDisplays; }
    public void setAssignedSubjects(List<String> assignedSubjects) { this.assignedSubjects = assignedSubjects; }
    public void setPassword(String password) { this.password = password; }
}
