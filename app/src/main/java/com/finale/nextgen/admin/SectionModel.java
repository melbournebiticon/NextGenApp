package com.finale.nextgen.admin;

public class SectionModel {
    public String id;
    public String name;
    public String specializationId;
    public String yearId;
    public String specializationName; // NEW
    public String yearName;           // NEW

    public SectionModel() {
        // Default constructor required for Firebase
    }

    public SectionModel(String id, String name, String specializationId, String yearId,
                        String specializationName, String yearName) {
        this.id = id;
        this.name = name;
        this.specializationId = specializationId;
        this.yearId = yearId;
        this.specializationName = specializationName;
        this.yearName = yearName;
    }
    public String getId() { return id; }
    public String getName() { return name; }
}
