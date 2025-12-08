package com.finale.nextgen.offline;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * DAO for cached_quizzes.
 */
@Dao
public interface QuizDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<QuizEntity> quizzes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(QuizEntity quiz);

    @Query("SELECT * FROM cached_quizzes")
    List<QuizEntity> getAll();

    @Query("SELECT * FROM cached_quizzes WHERE quizId = :id LIMIT 1")
    QuizEntity getById(String id);

    @Query("DELETE FROM cached_quizzes")
    void deleteAll();
}