package com.example.nextgen.offline;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface StudentAnswerDao {

    @Insert
    void insertAnswer(StudentAnswerEntity answer);

    @Query("SELECT * FROM offline_answers WHERE synced = 0")
    List<StudentAnswerEntity> getUnsynced();

    @Query("UPDATE offline_answers SET synced = 1 WHERE id = :id")
    void markSynced(int id);
}
