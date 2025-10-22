package com.example.nextgen.admin;

public class SpecializationModel {
    String id;
    String name;

    public SpecializationModel() {}

    public SpecializationModel(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
}
