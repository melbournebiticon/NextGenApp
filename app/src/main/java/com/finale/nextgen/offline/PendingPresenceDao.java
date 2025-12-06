package com.finale.nextgen.offline;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PendingPresenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PendingPresence p);

    @Query("SELECT * FROM pending_presences WHERE status = :status")
    List<PendingPresence> getByStatus(String status);

    @Query("DELETE FROM pending_presences WHERE id = :id")
    void deleteById(String id);

    @Query("UPDATE pending_presences SET status = :status WHERE id = :id")
    void updateStatus(String id, String status);

    // Convenience count used by dashboard to mark presence quickly
    @Query("SELECT COUNT(*) FROM pending_presences WHERE examId = :examId AND studentId = :studentId")
    int countByExamAndStudent(String examId, String studentId);
}