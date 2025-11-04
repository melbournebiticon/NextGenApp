package com.example.nextgen.model;

public class YearModel {
    private String id;
    private String name;

    public YearModel() {
        // Default constructor required for Firebase
    }

    public YearModel(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}