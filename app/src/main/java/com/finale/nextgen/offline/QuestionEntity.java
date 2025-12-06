package com.finale.nextgen.offline;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.util.List;

@Entity(tableName = "offline_questions")
@TypeConverters(Converters.class)
public class QuestionEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String firebaseKey;

    public String examId;

    public String questionText;
    public String questionType;

    public String optionA;
    public String optionB;
    public String optionC;
    public String optionD;

    public String correctAnswer;

    public String studentAnswer;

    public int displayNumber;

    public List<String> matchingOptions;
}
