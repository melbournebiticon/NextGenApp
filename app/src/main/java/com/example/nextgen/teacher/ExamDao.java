package com.example.nextgen.teacher;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ExamDao {

    // ✅ Add new exam
    @Insert
    long insert(Exam exam);

    // ✅ Update existing exam
    @Update
    void updateExam(Exam exam);

    // ✅ Get all exams, sorted by most recent schedule
    @Query("SELECT * FROM exams ORDER BY scheduledAt DESC")
    List<Exam> getAllExams();

    // ✅ Get a specific exam by ID
    @Query("SELECT * FROM exams WHERE id = :id LIMIT 1")
    Exam getExamById(int id);

    // ✅ Delete specific exam
    @Query("DELETE FROM exams WHERE id = :id")
    void deleteById(int id);

    // ✅ Reset all exams (optional button function)
    @Query("DELETE FROM exams")
    void resetAllExams();

    // ✅ Activate / Deactivate exam
    @Query("UPDATE exams SET active = :isActive WHERE id = :id")
    void updateExamActivation(int id, boolean isActive);

    @Query("SELECT * FROM exams WHERE teacherId = :teacherId ORDER BY scheduledAt DESC")
    List<Exam> getExamsByTeacher(String teacherId);

}
