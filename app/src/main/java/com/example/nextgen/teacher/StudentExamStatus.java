package com.example.nextgen.teacher;

public class StudentExamStatus {

    private String studentId;
    private String fullName;
    private boolean present;           // true if student joined the exam
    private boolean ongoing;           // true if student is currently taking the exam
    private int questionsAnswered;     // how many questions the student has answered

    // Empty constructor required for Firebase
    public StudentExamStatus() {}

    public StudentExamStatus(String studentId, String fullName, boolean present, boolean ongoing, int questionsAnswered) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.present = present;
        this.ongoing = ongoing;
        this.questionsAnswered = questionsAnswered;
    }

    // Getters & Setters
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public boolean isPresent() { return present; }
    public void setPresent(boolean present) { this.present = present; }

    public boolean isOngoing() { return ongoing; }
    public void setOngoing(boolean ongoing) { this.ongoing = ongoing; }

    public int getQuestionsAnswered() { return questionsAnswered; }
    public void setQuestionsAnswered(int questionsAnswered) { this.questionsAnswered = questionsAnswered; }
}
