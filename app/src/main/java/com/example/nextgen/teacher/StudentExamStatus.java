package com.example.nextgen.teacher;

public class StudentExamStatus {

    private String studentId;
    private String fullName;
    private boolean present;           // true if student joined the exam
    private boolean ongoing;           // true if student is currently taking the exam
    private int questionsAnswered;     // how many questions the student has answered

    private String course;
    private String specialization;
    private String year;
    private String section;

    // Empty constructor required for Firebase
    public StudentExamStatus() {}

    public StudentExamStatus(String studentId, String fullName, boolean present, boolean ongoing, int questionsAnswered,
                             String course, String specialization, String year, String section) {
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

    // Getters & setters for new fields
    public String getCourse() { return course; }
    public void setCourse(String course) { this.course = course; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
}
