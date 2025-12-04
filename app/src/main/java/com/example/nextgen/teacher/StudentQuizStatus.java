package com.example.nextgen.teacher;

import java.io.Serializable;

public class StudentQuizStatus implements Serializable {
    private String studentId;
    private String fullName;
    private boolean present;
    private boolean ongoing; // restored: teacher toggled "ongoing" state
    private int questionsAnswered;
    private String course;
    private String specialization;
    private String year;
    private String section;

    public StudentQuizStatus() {}

    /**
     * Convenience constructor matching usage in activity/adapter:
     * (studentId, fullName, present, ongoing, questionsAnswered, course, specialization, year, section)
     */
    public StudentQuizStatus(String studentId, String fullName, boolean present,
                             boolean ongoing, int questionsAnswered, String course,
                             String specialization, String year, String section) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.present = present;
        this.ongoing = ongoing;
        this.questionsAnswered = questionsAnswered;
        this.course = course;
        this.specialization = specialization;
        this.year = year;
        this.section = section;
    }

    // ========================
    // Getters / Setters
    // ========================
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

    public String getCourse() { return course; }
    public void setCourse(String course) { this.course = course; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    // ========================
    // Quiz Permission Logic
    // ========================

    /**
     * Student is allowed to take the quiz ONLY IF:
     * - Marked Present
     * - Quiz is marked as Ongoing
     */
    public boolean canTakeQuiz() {
        return present && ongoing;
    }

    /**
     * True if student is blocked from quiz
     */
    public boolean isBlockedFromQuiz() {
        return !canTakeQuiz();
    }

    @Override
    public String toString() {
        return "StudentQuizStatus{" +
                "studentId='" + studentId + '\'' +
                ", fullName='" + fullName + '\'' +
                ", present=" + present +
                ", ongoing=" + ongoing +
                ", questionsAnswered=" + questionsAnswered +
                ", course='" + course + '\'' +
                ", specialization='" + specialization + '\'' +
                ", year='" + year + '\'' +
                ", section='" + section + '\'' +
                '}';
    }
}
