package com.example.nextgen.offline;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.OnConflictStrategy;

import java.util.List;

/**
 * DAO for quiz_pending_submissions.
 */
@Dao
public interface QuizPendingSubmissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(QuizPendingSubmission item);

    @Update
    void update(QuizPendingSubmission item);

    @Delete
    void delete(QuizPendingSubmission item);

    @Query("DELETE FROM quiz_pending_submissions WHERE clientSubmissionId = :id")
    void deleteById(String id);

    @Query("SELECT * FROM quiz_pending_submissions WHERE status = :status")
    List<QuizPendingSubmission> getByStatus(String status);

    @Query("SELECT * FROM quiz_pending_submissions WHERE quizId = :quizId AND studentId = :studentId LIMIT 1")
    QuizPendingSubmission findPendingByQuizAndStudent(String quizId, String studentId);
}