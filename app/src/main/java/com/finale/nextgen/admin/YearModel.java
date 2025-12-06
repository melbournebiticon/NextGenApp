package com.finale.nextgen.admin;

public class YearModel {
    private String id;
    private String name;

    public YearModel() {} // Required for Firebase

    public YearModel(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
}
