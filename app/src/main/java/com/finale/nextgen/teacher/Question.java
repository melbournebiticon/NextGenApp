package com.finale.nextgen.teacher;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.List;
import java.util.ArrayList;


import androidx.room.TypeConverters;

@TypeConverters(Converters.class)
@Entity(tableName = "questions")
public class Question {

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

    // ===== NEW: store student's answer =====
    private String studentAnswer;

    // ===== NEW: store display number (for numbering per type) =====
    private int displayNumber;

    private List<String> matchingOptions;
    // ===== Empty constructor (needed for Room & imports) =====
    public Question() {}

    // ===== Full constructor =====
    public Question(String examId, String questionText, String questionType,
                    String optionA, String optionB, String optionC, String optionD,
                    String correctAnswer) {

        this.examId = examId;
        this.questionText = questionText;
        this.questionType = questionType;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctAnswer = correctAnswer;
    }

    // ===== Getters =====
    public int getId() { return id; }
    public String getExamId() { return examId; }
    public String getQuestionText() { return questionText; }
    public String getQuestionType() { return questionType; }
    public String getOptionA() { return optionA; }
    public String getOptionB() { return optionB; }
    public String getOptionC() { return optionC; }
    public String getOptionD() { return optionD; }
    public String getCorrectAnswer() { return correctAnswer; }
    public String getStudentAnswer() { return studentAnswer; } // NEW
    public int getDisplayNumber() { return displayNumber; } // ✅ NEW Getter

    // ===== Setters =====
    public void setExamId(String examId) { this.examId = examId; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public void setOptionA(String optionA) { this.optionA = optionA; }
    public void setOptionB(String optionB) { this.optionB = optionB; }
    public void setOptionC(String optionC) { this.optionC = optionC; }
    public void setOptionD(String optionD) { this.optionD = optionD; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; } // NEW
    public void setDisplayNumber(int displayNumber) { this.displayNumber = displayNumber; } // ✅ NEW Setter

    // ===== Helper Methods (for Editing Support) =====
    public void updateFrom(Question updated) {
        this.questionText = updated.getQuestionText();
        this.questionType = updated.getQuestionType();
        this.optionA = updated.getOptionA();
        this.optionB = updated.getOptionB();
        this.optionC = updated.getOptionC();
        this.optionD = updated.getOptionD();
        this.correctAnswer = updated.getCorrectAnswer();
    }

    public Question copy() {
        Question copy = new Question();
        copy.id = this.id;
        copy.examId = this.examId;
        copy.questionText = this.questionText;
        copy.questionType = this.questionType;
        copy.optionA = this.optionA;
        copy.optionB = this.optionB;
        copy.optionC = this.optionC;
        copy.optionD = this.optionD;
        copy.correctAnswer = this.correctAnswer;
        copy.studentAnswer = this.studentAnswer; // copy student answer too
        copy.displayNumber = this.displayNumber; // also copy display number
        if (this.matchingOptions != null) {
            copy.matchingOptions = new ArrayList<>(this.matchingOptions); // copy the list
        }
        return copy;
    }


    public void setFirebaseKey(String key) { this.firebaseKey = key; }
    public String getFirebaseKey() { return firebaseKey; }
    public List<String> getMatchingOptions() {
        return matchingOptions;
    }

    // Setter
    public void setMatchingOptions(List<String> matchingOptions) {
        this.matchingOptions = matchingOptions;
    }

    public Object getChoices() {
        return null;
    }
}
