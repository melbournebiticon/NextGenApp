package com.finale.nextgen.offline;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * QuizPendingSubmission - pending quiz submission stored locally for offline sync.
 * Mirrors your existing PendingSubmission shape but named for quizzes (uses quizId).
 */
@Entity(tableName = "quiz_pending_submissions")
public class QuizPendingSubmission {

    @PrimaryKey
    @NonNull
    public String clientSubmissionId;

    @Nullable
    public String quizId; // was examId in exam pending rows

    @Nullable
    public String studentId;

    public int computedScore;
    public int maxScore;

    public long timestamp;

    @Nullable
    public String status; // "PENDING", "SYNCING", etc.

    // Optional fields to match PendingSubmission shape (deltas / metadata)
    @Nullable
    public String deductions; // serialized if you use it
    @Nullable
    public String answersJson; // optionally store answers

    public QuizPendingSubmission() {}

    public QuizPendingSubmission(@NonNull String clientSubmissionId, @Nullable String quizId,
                                 @Nullable String studentId, int computedScore, int maxScore,
                                 long timestamp, @Nullable String status) {
        this.clientSubmissionId = clientSubmissionId;
        this.quizId = quizId;
        this.studentId = studentId;
        this.computedScore = computedScore;
        this.maxScore = maxScore;
        this.timestamp = timestamp;
        this.status = status;
    }
}