package com.example.nextgen.teacher;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;

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

    // ===== Empty constructor (needed for Room) =====
    public Question() {}

    // ===== Full constructor - Mark with @Ignore =====
    @Ignore
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
    public String getStudentAnswer() { return studentAnswer; }
    public int getDisplayNumber() { return displayNumber; }
    public String getFirebaseKey() { return firebaseKey; }

    // ===== Setters =====
    public void setId(int id) { this.id = id; }
    public void setExamId(String examId) { this.examId = examId; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public void setOptionA(String optionA) { this.optionA = optionA; }
    public void setOptionB(String optionB) { this.optionB = optionB; }
    public void setOptionC(String optionC) { this.optionC = optionC; }
    public void setOptionD(String optionD) { this.optionD = optionD; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }
    public void setDisplayNumber(int displayNumber) { this.displayNumber = displayNumber; }
    public void setFirebaseKey(String firebaseKey) { this.firebaseKey = firebaseKey; }

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

    // ===== Copy method - Mark with @Ignore since it's not a constructor =====
    @Ignore
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
        copy.studentAnswer = this.studentAnswer;
        copy.displayNumber = this.displayNumber;
        copy.firebaseKey = this.firebaseKey;
        return copy;
    }
}