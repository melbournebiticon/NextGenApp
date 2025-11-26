package com.example.nextgen.offline;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PendingSubmissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PendingSubmission p);

    @Update
    void update(PendingSubmission p);

    @Delete
    void delete(PendingSubmission p);

    @Query("DELETE FROM pending_submissions WHERE clientSubmissionId = :id")
    void deleteById(String id);

    @Query("SELECT * FROM pending_submissions WHERE status = :status")
    List<PendingSubmission> getByStatus(String status);

    @Query("SELECT * FROM pending_submissions")
    List<PendingSubmission> getAll();

    @Query("SELECT * FROM pending_submissions WHERE examId = :examId AND studentId = :studentId LIMIT 1")
    PendingSubmission findPendingByExamAndStudent(String examId, String studentId);
}