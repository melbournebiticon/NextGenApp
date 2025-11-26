package com.example.nextgen.offline;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "offline_answers")
public class StudentAnswerEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String examId;
    public String questionId;

    public String answer;

    public boolean synced = false;
}
