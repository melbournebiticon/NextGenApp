package com.example.nextgen.teacher;

public class SubmissionModel {
    private String id;
    private String activityId;
    private String studentId;
    private String fileName;
    private String fileData;
    private String score;
    private String submittedAt;
    private boolean viewed;

    public SubmissionModel() {} // Firebase needs empty constructor

    public String getId() { return id; }
    public String getActivityId() { return activityId; }
    public String getStudentId() { return studentId; }
    public String getFileName() { return fileName; }
    public String getFileData() { return fileData; }
    public String getScore() { return score; }
    public String getSubmittedAt() { return submittedAt; }
    public boolean isViewed() { return viewed; }
}
