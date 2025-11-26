package com.example.nextgen.offline;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ExamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertExam(ExamEntity exam);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertExams(List<ExamEntity> list);

    @Query("SELECT * FROM offline_exams")
    List<ExamEntity> getAllExams();

    // ✅ fetch exams for a specific student
    @Query("SELECT * FROM offline_exams WHERE studentUid = :studentUid")
    List<ExamEntity> getAllExamsForStudent(String studentUid);

    // ✅ fetch a single exam by id (used by TakeExamActivity to load cached metadata)
    @Query("SELECT * FROM offline_exams WHERE examId = :examId LIMIT 1")
    ExamEntity getExamById(String examId);
}