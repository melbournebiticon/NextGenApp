package com.example.nextgen.student;

import java.io.Serializable;
import java.util.Objects;

public class QuizModel implements Serializable {
    private static final long serialVersionUID = 1L;

    // ===== BASE IDENTIFIERS =====
    private String quizId;
    private String quizName;

    // ===== COURSE / CLASS DETAILS =====
    private String courseId;
    private String courseName;
    private String specializationName;
    private String yearName;
    private String sectionName;
    private String teacherName;
    private String subjectName;
    private String createdAt;

    // ===== SCHEDULING / TIMING =====
    private Long scheduledAt;
    private Long availableAt;
    private Long endAt;
    private Integer durationMinutes;

    // ===== QUIZ-SPECIFIC =====
    private Integer questionCount;
    private Boolean randomizeQuestions;
    private String instructions;
    private String accessPin;
    private Integer passMark;

    // ===== FLAGS / STATUS =====
    private Boolean active;
    private Boolean available;
    private Boolean present;
    private Boolean studentPresent;
    private String status;

    public QuizModel() {
        this.scheduledAt = 0L;
        this.availableAt = 0L;
        this.endAt = 0L;
        this.durationMinutes = 0;
        this.questionCount = 0;
        this.randomizeQuestions = false;
        this.active = false;
        this.available = false;
        this.present = false;
        this.studentPresent = false;
        this.passMark = 0;
        this.status = "";
    }

    // ======================
    //       GETTERS
    // ======================
    public String getQuizId() { return quizId; }
    public String getQuizName() { return quizName; }
    public String getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public String getSpecializationName() { return specializationName; }
    public String getYearName() { return yearName; }
    public String getSectionName() { return sectionName; }
    public String getTeacherName() { return teacherName; }
    public String getSubjectName() { return subjectName; }
    public String getCreatedAt() { return createdAt; }
    public Long getScheduledAt() { return scheduledAt; }
    public Long getAvailableAt() { return availableAt; }
    public Long getEndAt() { return endAt; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public Integer getQuestionCount() { return questionCount; }
    public Boolean getRandomizeQuestions() { return randomizeQuestions; }
    public String getInstructions() { return instructions; }
    public String getAccessPin() { return accessPin; }
    public Integer getPassMark() { return passMark; }
    public Boolean getActive() { return active; }
    public Boolean getAvailable() { return available; }
    public Boolean getPresent() { return present; }
    public Boolean getStudentPresent() { return studentPresent; }
    public String getStatus() { return status; }

    public boolean isActive() { return active != null && active; }
    public boolean isAvailable() { return available != null && available; }
    public boolean isPresent() { return present != null && present; }
    public boolean isStudentPresent() { return studentPresent != null && studentPresent; }

    // ======================
    //       SETTERS
    // ======================
    public void setQuizId(String quizId) { this.quizId = quizId; }
    public void setQuizName(String quizName) { this.quizName = quizName != null ? quizName : ""; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public void setSpecializationName(String specializationName) { this.specializationName = specializationName; }
    public void setYearName(String yearName) { this.yearName = yearName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public void setScheduledAt(Long scheduledAt) {
        this.scheduledAt = scheduledAt != null ? scheduledAt : 0L;
        computeEndTime();
    }

    public void setAvailableAt(Long availableAt) { this.availableAt = availableAt != null ? availableAt : 0L; }
    public void setEndAt(Long endAt) { this.endAt = endAt != null ? endAt : 0L; }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes != null ? durationMinutes : 0;
        computeEndTime();
    }

    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount != null ? questionCount : 0; }
    public void setRandomizeQuestions(Boolean randomizeQuestions) { this.randomizeQuestions = randomizeQuestions != null && randomizeQuestions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public void setAccessPin(String accessPin) { this.accessPin = accessPin; }
    public void setPassMark(Integer passMark) { this.passMark = passMark != null ? passMark : 0; }
    public void setActive(Boolean active) { this.active = active != null && active; }
    public void setAvailable(Boolean available) { this.available = available != null && available; }
    public void setPresent(Boolean present) { this.present = present != null && present; }
    public void setStudentPresent(Boolean studentPresent) { this.studentPresent = studentPresent != null && studentPresent; }
    public void setStatus(String status) { this.status = status != null ? status : ""; }

    private void computeEndTime() {
        long sched = scheduledAt != null ? scheduledAt : 0L;
        int dur = durationMinutes != null ? durationMinutes : 0;
        this.endAt = (sched > 0 && dur > 0) ? sched + dur * 60000L : this.endAt != null ? this.endAt : 0L;
    }

    public String getCourseDisplay() {
        StringBuilder sb = new StringBuilder();
        if (courseName != null && !courseName.isEmpty()) sb.append(courseName);
        if (specializationName != null && !specializationName.isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(specializationName);
        }
        if (yearName != null && !yearName.isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(yearName);
        }
        if (sectionName != null && !sectionName.isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(sectionName);
        }
        return sb.toString();
    }

    public void markStudentPresent() { this.studentPresent = true; }
    public void clearStudentPresent() { this.studentPresent = false; }

    @Override
    public String toString() {
        return "QuizModel{" +
                "quizId='" + quizId + '\'' +
                ", quizName='" + quizName + '\'' +
                ", courseName='" + courseName + '\'' +
                ", specializationName='" + specializationName + '\'' +
                ", yearName='" + yearName + '\'' +
                ", sectionName='" + sectionName + '\'' +
                ", scheduledAt=" + scheduledAt +
                ", availableAt=" + availableAt +
                ", endAt=" + endAt +
                ", durationMinutes=" + durationMinutes +
                ", active=" + active +
                ", present=" + present +
                ", studentPresent=" + studentPresent +
                '}';
    }

    // ===== FIXED EQUALITY: Only quizId matters for duplicate prevention =====
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuizModel)) return false;
        QuizModel quizModel = (QuizModel) o;
        return Objects.equals(quizId, quizModel.quizId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(quizId);
    }
}
