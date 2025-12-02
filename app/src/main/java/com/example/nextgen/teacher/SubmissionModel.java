package com.example.nextgen.teacher;

import androidx.annotation.NonNull;

public class SubmissionModel {

    private String id;             // Firebase node key
    private String activityId;
    private String studentId;
    private String fileName;
    private String fileData;
    private String score;           // teacher-assigned score (always String internally)
    private String maxScore;        // maximum possible score
    private String submittedAt;
    private boolean viewed;
    private boolean resubmitRequested;

    // REQUIRED empty constructor for Firebase
    public SubmissionModel() {}

    // ============ GETTERS ============
    public String getId() { return id; }
    public String getSubmissionId() { return id; }
    public String getActivityId() { return activityId; }
    public String getStudentId() { return studentId; }
    public String getFileName() { return fileName; }
    public String getFileData() { return fileData; }
    public String getScore() { return score; }
    public String getMaxScore() { return maxScore; }
    public String getSubmittedAt() { return submittedAt; }
    public boolean isViewed() { return viewed; }
    public boolean isResubmitRequested() { return resubmitRequested; }

    // ============ SETTERS ============
    public void setId(String id) { this.id = id; }
    public void setSubmissionId(String submissionId) { this.id = submissionId; }
    public void setActivityId(String activityId) { this.activityId = activityId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileData(String fileData) { this.fileData = fileData; }

    // SINGLE setter to handle both Firebase numeric and string values
    public void setScore(@NonNull Object scoreObj) {
        if (scoreObj == null) {
            this.score = "Pending";
        } else if (scoreObj instanceof Number) {
            this.score = String.valueOf(((Number) scoreObj).intValue());
        } else {
            this.score = scoreObj.toString();
        }
    }

    // Do NOT add another setScore(String) — it breaks Firebase mapping

    public void setMaxScore(String maxScore) {
        this.maxScore = (maxScore == null || maxScore.isEmpty()) ? "100" : maxScore;
    }

    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }
    public void setViewed(boolean viewed) { this.viewed = viewed; }
    public void setResubmitRequested(boolean resubmitRequested) { this.resubmitRequested = resubmitRequested; }

    // ============ DISPLAY HELPERS ============
    public String getScoreDisplay() {
        if (score == null || score.isEmpty()) return "Pending";
        if (maxScore == null || maxScore.isEmpty()) return score;
        return score + " / " + maxScore;
    }

    // Helper to get score as int
    public int getScoreAsInt() {
        try {
            return Integer.parseInt(score);
        } catch (Exception e) {
            return 0;
        }
    }
}
