package com.finale.nextgen.teacher;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface QuizDao {

    // Insert a new quiz
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Quiz quiz);

    // Insert multiple quizzes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Quiz> quizzes);

    // Update a quiz
    @Update
    void updateQuiz(Quiz quiz);

    // Delete a quiz
    @Delete
    void delete(Quiz quiz);

    // Delete by ID
    @Query("DELETE FROM quiz_table WHERE id = :id")
    void deleteById(int id);

    // Get all quizzes
    @Query("SELECT * FROM quiz_table ORDER BY scheduledAt ASC")
    List<Quiz> getAllQuizzes();

    // Get active quizzes only
    @Query("SELECT * FROM quiz_table WHERE isActive = 1 ORDER BY scheduledAt ASC")
    List<Quiz> getActiveQuizzes();

    // Get quizzes by teacher ID
    @Query("SELECT * FROM quiz_table WHERE teacherId = :teacherId ORDER BY scheduledAt ASC")
    List<Quiz> getQuizzesByTeacher(String teacherId);

    // Get quiz by Firebase key
    @Query("SELECT * FROM quiz_table WHERE firebaseKey = :firebaseKey LIMIT 1")
    Quiz getQuizByFirebaseKey(String firebaseKey);
}
