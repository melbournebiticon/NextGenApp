package com.finale.nextgen.admin;

public class CourseOption {
    private String yearId;
    private String yearName;
    private String sectionId;
    private String sectionName;
    private String specializationId;
    private String specializationName;

    public CourseOption() {}

    public CourseOption(String yearId, String yearName,
                        String sectionId, String sectionName,
                        String specializationId, String specializationName) {
        this.yearId = yearId;
        this.yearName = yearName;
        this.sectionId = sectionId;
        this.sectionName = sectionName;
        this.specializationId = specializationId;
        this.specializationName = specializationName;
    }

    public String getYearId() { return yearId; }
    public String getYearName() { return yearName; }
    public String getSectionId() { return sectionId; }
    public String getSectionName() { return sectionName; }
    public String getSpecializationId() { return specializationId; }
    public String getSpecializationName() { return specializationName; }

    public String getDisplayName() {
        return specializationName + " - " + yearName + sectionName;
    }
}
