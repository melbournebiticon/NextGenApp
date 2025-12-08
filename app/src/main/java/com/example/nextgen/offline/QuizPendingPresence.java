package com.example.nextgen.offline;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * QuizPendingPresence - local pending presence row specific to quizzes.
 * Kept separate from PendingPresence (exam) to avoid mixing exam/quiz workflows.
 */
@Entity(tableName = "quiz_pending_presences")
public class QuizPendingPresence {
    @PrimaryKey
    @NonNull
    public String id;

    @Nullable
    public String quizId;

    @Nullable
    public String studentId;

    public long timestamp;

    @Nullable
    public String status; // PENDING, SYNCING, FAILED

    public QuizPendingPresence() {}
}