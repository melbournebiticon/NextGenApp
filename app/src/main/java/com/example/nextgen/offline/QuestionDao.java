package com.example.nextgen.offline;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface QuestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(QuestionEntity question);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<QuestionEntity> questions);

    @Query("SELECT * FROM offline_questions WHERE examId = :examId ORDER BY displayNumber ASC")
    List<QuestionEntity> getQuestionsByExamId(String examId);

    @Query("DELETE FROM offline_questions WHERE examId = :examId")
    void deleteByExamId(String examId);
    @Query("SELECT COUNT(*) FROM offline_questions WHERE examId = :examId")
    int countByExamId(String examId);

    @Query("DELETE FROM offline_questions")
    void clearAll();
}
