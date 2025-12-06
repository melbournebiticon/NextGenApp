package com.finale.nextgen.teacher;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface QuestionDao {

    // ===== Insert Operations =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Question question);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Question> questions);

    // ===== Query Operations =====
    @Query("SELECT * FROM questions WHERE examId = :examId ORDER BY displayNumber ASC")
    List<Question> getQuestionsByExamId(String examId);

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    Question getQuestionById(int id);

    @Query("SELECT COUNT(*) FROM questions WHERE examId = :examId")
    int getQuestionCountByExam(String examId);

    // ===== Update Operations =====
    @Update
    void updateQuestion(Question question);

    @Query("UPDATE questions SET studentAnswer = :answer WHERE id = :id")
    void updateStudentAnswer(int id, String answer);

    @Query("UPDATE questions SET correctAnswer = :correctAnswer WHERE id = :id")
    void updateCorrectAnswer(int id, String correctAnswer);

    // ===== Delete Operations =====
    @Query("DELETE FROM questions WHERE examId = :examId")
    void deleteByExamId(String examId);

    @Query("DELETE FROM questions WHERE id = :id")
    void deleteById(int id);

    @Delete
    void delete(Question question);

    @Query("DELETE FROM questions")
    void clearAll();
}
