package com.finale.nextgen.teacher;

import androidx.annotation.Nullable;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

import java.io.Serializable;

/**
 * SubmissionModel - robust Firebase mapping
 *
 * - Tolerates extra DB fields with @IgnoreExtraProperties
 * - Accepts both Number and String for score/maxScore/submittedAt via @PropertyName setters
 * - Provides plain String setters/getters for app use
 */
@IgnoreExtraProperties
public class SubmissionModel implements Serializable {

    private String id;             // Firebase node key
    private String activityId;
    private String studentId;
    private String fileName;
    private String fileData;
    private String score;           // teacher-assigned score (displayed/stored as String)
    private String maxScore;        // maximum possible score (displayed/stored as String)
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

    // Keep getter simple/string-based for app usage
    @PropertyName("score")
    public String getScore() { return score; }

    @PropertyName("maxScore")
    public String getMaxScore() { return maxScore; }

    @PropertyName("submittedAt")
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

    /**
     * Single, annotated setter to accept whatever the database stored for "score".
     * It safely normalizes numbers and strings into our internal String representation.
     */
    @PropertyName("score")
    public void setScoreFromFirebase(@Nullable Object scoreObj) {
        if (scoreObj == null) {
            this.score = null;
        } else if (scoreObj instanceof Number) {
            // use integer representation for display
            this.score = String.valueOf(((Number) scoreObj).intValue());
        } else {
            this.score = scoreObj.toString();
        }
    }

    /**
     * Accept both number and string for maxScore.
     * IMPORTANT: do NOT default to a concrete number here — leave null if DB has no value
     * so callers can correctly fall back to activity-level max when desired.
     */
    @PropertyName("maxScore")
    public void setMaxScoreFromFirebase(@Nullable Object maxScoreObj) {
        if (maxScoreObj == null) {
            this.maxScore = null;
        } else if (maxScoreObj instanceof Number) {
            this.maxScore = String.valueOf(((Number) maxScoreObj).intValue());
        } else {
            String s = maxScoreObj.toString().trim();
            this.maxScore = s.isEmpty() ? null : s;
        }
    }

    @PropertyName("submittedAt")
    public void setSubmittedAtFromFirebase(@Nullable Object submittedAtObj) {
        if (submittedAtObj == null) {
            this.submittedAt = null;
        } else if (submittedAtObj instanceof Number) {
            this.submittedAt = String.valueOf(((Number) submittedAtObj).longValue());
        } else {
            String s = submittedAtObj.toString().trim();
            this.submittedAt = s.isEmpty() ? null : s;
        }
    }

    public void setViewed(boolean viewed) { this.viewed = viewed; }
    public void setResubmitRequested(boolean resubmitRequested) { this.resubmitRequested = resubmitRequested; }

    // Plain setters used by app logic (not annotated) --------------------------------
    // These allow code to set values before pushing to Firebase (the Firebase SDK will
    // still write these fields by their property names).
    public void setScore(String score) { this.score = (score == null || score.trim().isEmpty()) ? null : score.trim(); }
    public void setMaxScore(String maxScore) { this.maxScore = (maxScore == null || maxScore.trim().isEmpty()) ? null : maxScore.trim(); }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = (submittedAt == null || submittedAt.trim().isEmpty()) ? null : submittedAt.trim(); }

    // ============ DISPLAY HELPERS ============
    public String getScoreDisplay() {
        if (score == null || score.isEmpty()) return "Pending";
        if (maxScore == null || maxScore.isEmpty()) return score;
        return score + " / " + maxScore;
    }

    // Helper to get score as int (safe)
    public int getScoreAsInt() {
        try {
            return Integer.parseInt(score);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public String toString() {
        return "SubmissionModel{" +
                "id='" + id + '\'' +
                ", activityId='" + activityId + '\'' +
                ", studentId='" + studentId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", score='" + score + '\'' +
                ", maxScore='" + maxScore + '\'' +
                ", submittedAt='" + submittedAt + '\'' +
                ", viewed=" + viewed +
                ", resubmitRequested=" + resubmitRequested +
                '}';
    }
}