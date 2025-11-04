package com.example.nextgen.teacher;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface QuestionDao {

    // ===== Insert Operations =====
    @Insert
    void insert(Question question);

    @Insert
    void insertAll(List<Question> questions);

    // ===== Query Operations =====
    @Query("SELECT * FROM questions WHERE examId = :examId")
    List<Question> getQuestionsByExamId(String examId);

    // ===== Delete Operations =====
    @Query("DELETE FROM questions WHERE examId = :examId")
    void deleteByExamId(String examId);  // Delete all questions for a specific exam

    @Query("DELETE FROM questions WHERE id = :id")
    void deleteById(int id);  // Delete a single question by its ID

    @Delete
    void delete(Question question);  // Delete by object

    // ===== Update Operations =====
    @Update
    void updateQuestion(Question question);  // Update a question
}
