package com.example.nextgen.teacher;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ExamDao {

    // ✅ Insert new exam (returns generated local ID)
    @Insert
    long insert(Exam exam);

    // ✅ Update an existing exam
    @Update
    void updateExam(Exam exam);

    // ✅ Get all exams sorted by latest schedule
    @Query("SELECT * FROM exams ORDER BY scheduledAt DESC")
    List<Exam> getAllExams();

    // ✅ Get specific exam by local Room ID
    @Query("SELECT * FROM exams WHERE id = :id LIMIT 1")
    Exam getExamById(int id);

    // ✅ 🔹 Get specific exam by Firebase key (for syncing or TakeExamActivity)
    @Query("SELECT * FROM exams WHERE firebaseKey = :firebaseKey LIMIT 1")
    Exam getExamByFirebaseKey(String firebaseKey);

    // ✅ Delete specific exam by local ID
    @Query("DELETE FROM exams WHERE id = :id")
    void deleteById(int id);

    // ✅ Delete all exams (reset table)
    @Query("DELETE FROM exams")
    void resetAllExams();

    // ✅ Update exam activation status
    @Query("UPDATE exams SET active = :isActive WHERE id = :id")
    void updateExamActivation(int id, boolean isActive);

    // ✅ Get all exams created by a specific teacher
    @Query("SELECT * FROM exams WHERE teacherId = :teacherId ORDER BY scheduledAt DESC")
    List<Exam> getExamsByTeacher(String teacherId);

    // ✅ Optional: Update exam duration manually
    @Query("UPDATE exams SET durationMinutes = :minutes WHERE id = :id")
    void updateExamDuration(int id, int minutes);

    // ✅ Optional: Update Firebase key (for sync after push)
    @Query("UPDATE exams SET firebaseKey = :firebaseKey WHERE id = :id")
    void updateFirebaseKey(int id, String firebaseKey);
}
