package com.example.nextgen.offline;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.OnConflictStrategy;

import java.util.List;

/**
 * DAO for quiz_pending_presences table.
 */
@Dao
public interface QuizPendingPresenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(QuizPendingPresence p);

    @Update
    void update(QuizPendingPresence p);

    @Delete
    void delete(QuizPendingPresence p);

    @Query("DELETE FROM quiz_pending_presences WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT * FROM quiz_pending_presences WHERE status = :status")
    List<QuizPendingPresence> getByStatus(String status);

    @Query("UPDATE quiz_pending_presences SET status = :status WHERE id = :id")
    void updateStatus(String id, String status);

    @Query("SELECT * FROM quiz_pending_presences WHERE quizId = :quizId AND studentId = :studentId LIMIT 1")
    QuizPendingPresence findPendingByQuizAndStudent(String quizId, String studentId);
}