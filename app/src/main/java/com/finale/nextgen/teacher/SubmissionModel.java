package com.finale.nextgen.teacher;

import androidx.annotation.Nullable;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

import java.io.Serializable;

/**
 * SubmissionModel - robust Firebase mapping
 *
 * Changes:
 * - Added @IgnoreExtraProperties to tolerate extra fields in the DB
 * - Use @PropertyName("...") annotated setters that accept Object so Firebase won't fail
 *   when the DB stores a number vs string (common cause of DatabaseException Long->String)
 * - Keep a simple String-based getter API for the rest of the app
 *
 * Important: keep only ONE method annotated for "score" so Firebase mapping is deterministic.
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
     *
     * This avoids DatabaseException when the DB contains a Long/Integer where previously
     * the model expected a String.
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
     * Same approach for maxScore: accept number or string from DB.
     */
    @PropertyName("maxScore")
    public void setMaxScoreFromFirebase(@Nullable Object maxScoreObj) {
        if (maxScoreObj == null) {
            this.maxScore = "100";
        } else if (maxScoreObj instanceof Number) {
            this.maxScore = String.valueOf(((Number) maxScoreObj).intValue());
        } else {
            String s = maxScoreObj.toString();
            this.maxScore = s.isEmpty() ? "100" : s;
        }
    }

    @PropertyName("submittedAt")
    public void setSubmittedAtFromFirebase(@Nullable Object submittedAtObj) {
        if (submittedAtObj == null) {
            this.submittedAt = null;
        } else if (submittedAtObj instanceof Number) {
            this.submittedAt = String.valueOf(((Number) submittedAtObj).longValue());
        } else {
            this.submittedAt = submittedAtObj.toString();
        }
    }

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

    public void setMaxScore(String s) {
    }

    public void setScore(String score) {
    }
}