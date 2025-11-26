package com.example.nextgen.teacher;

public class SubmissionModel {
    private String id;
    private String activityId;
    private String studentId;
    private String fileName;
    private String fileData;
    private String score;       // teacher-assigned score
    private String maxScore;    // maximum possible score
    private String submittedAt;
    private boolean viewed;
    private boolean resubmitRequested;

    public SubmissionModel() {} // Firebase needs empty constructor

    // Getters
    public String getId() { return id; }
    public String getActivityId() { return activityId; }
    public String getStudentId() { return studentId; }
    public String getFileName() { return fileName; }
    public String getFileData() { return fileData; }
    public String getScore() { return score; }
    public String getMaxScore() { return maxScore; }
    public String getSubmittedAt() { return submittedAt; }
    public boolean isViewed() { return viewed; }
    public boolean isResubmitRequested() { return resubmitRequested; }

    // Aliases for compatibility
    public String getSubmissionId() { return id; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setActivityId(String activityId) { this.activityId = activityId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileData(String fileData) { this.fileData = fileData; }
    public void setScore(String score) { this.score = score; }
    public void setMaxScore(String maxScore) { this.maxScore = maxScore; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }
    public void setViewed(boolean viewed) { this.viewed = viewed; }
    public void setResubmitRequested(boolean resubmitRequested) { this.resubmitRequested = resubmitRequested; }

    // Optional: convenience method to display as "score/maxScore"
    public String getScoreDisplay() {
        if (score == null) return "Pending";
        if (maxScore == null) return score;
        return score + "/" + maxScore;
    }
}
