package com.example.nextgen.admin;

public class CourseModel {
    public String id;
    public String courseName;
    public String specializationId;
    public String specializationName;
    public String yearId;
    public String yearName;
    public String sectionId;
    public String sectionName;

    // Default constructor required for Firebase
    public CourseModel() {}

    public CourseModel(String id, String name,
                       String specializationId, String specializationName,
                       String yearId, String yearName,
                       String sectionId, String sectionName) {
        this.id = id;
        this.courseName = name;
        this.specializationId = specializationId;
        this.specializationName = specializationName;
        this.yearId = yearId;
        this.yearName = yearName;
        this.sectionId = sectionId;
        this.sectionName = sectionName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getSpecializationId() { return specializationId; }
    public void setSpecializationId(String specializationId) { this.specializationId = specializationId; }

    public String getSpecializationName() { return specializationName; }
    public void setSpecializationName(String specializationName) { this.specializationName = specializationName; }

    public String getYearId() { return yearId; }
    public void setYearId(String yearId) { this.yearId = yearId; }

<<<<<<< HEAD
    public String getYearName() { return yearName; }
    public void setYearName(String yearName) { this.yearName = yearName; }
=======
>>>>>>> origin/pushnyodito4

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }

<<<<<<< HEAD
    public char[] getName() {
        return new char[0];
    }
}
=======
    public void setYearName(String yearName) { this.yearName = yearName; }
    public void setSpecializationName(String specializationName) { this.specializationName = specializationName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }

    // Optional: add getters/setters if needed
}
>>>>>>> origin/pushnyodito4
