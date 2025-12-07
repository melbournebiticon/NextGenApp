package com.finale.nextgen.teacher;

/**
 * Top-level SectionItem model so multiple classes (Activity, Adapter) can reference the same type.
 */
public class SectionItem {
    public String id;
    public String courseName;
    public String specializationName;
    public String yearName;
    public String sectionName;

    public SectionItem() {}

    public SectionItem(String id, String courseName, String specializationName, String yearName, String sectionName) {
        this.id = id;
        this.courseName = courseName;
        this.specializationName = specializationName;
        this.yearName = yearName;
        this.sectionName = sectionName;
    }

    public String getId() { return id; }
    public String getCourseName() { return courseName; }
    public String getSpecializationName() { return specializationName; }
    public String getYearName() { return yearName; }
    public String getSectionName() { return sectionName; }

    public String getDisplay() {
        return (courseName != null ? courseName : "Unknown Course")
                + " - " + (specializationName != null ? specializationName : "N/A")
                + " - " + (yearName != null ? yearName : "N/A")
                + " - " + (sectionName != null ? sectionName : "N/A");
    }

    public void setId(String key) {
    }
}