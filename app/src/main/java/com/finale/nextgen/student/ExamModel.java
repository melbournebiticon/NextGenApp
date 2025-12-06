package com.finale.nextgen.student;

public class ExamModel {
    private String examId;
    private String examTitle;
    private String courseId;
    private String courseName;
    private String specializationName;
    private String yearName;
    private String sectionName;
    private String teacherName;
    private String createdAt;
    private boolean active;

    // Scheduling / status
    private Integer durationMinutes = 0;
    private Long scheduledAt = 0L;
    private String scheduledDateDisplay;
    private String status;
    private boolean isAvailable;

    // presence
    private boolean present;

    // --- NEW fields to match DB keys that produced warnings ---
    private String subjectName;    // maps to "subjectName"
    private String teacherId;      // maps to "teacherId"
    private String courseDisplay;  // maps to "courseDisplay"

    public ExamModel() {}

    // (Keep or add any constructors you need)

    // ===== Getters / Setters =====
    public String getExamId() { return examId; }
    public void setExamId(String examId) { this.examId = examId; }

    public String getExamTitle() { return examTitle; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getSpecializationName() { return specializationName; }
    public void setSpecializationName(String specializationName) { this.specializationName = specializationName; }

    public String getYearName() { return yearName; }
    public void setYearName(String yearName) { this.yearName = yearName; }

    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public Long getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Long scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getScheduledDateDisplay() { return scheduledDateDisplay; }
    public void setScheduledDateDisplay(String scheduledDateDisplay) { this.scheduledDateDisplay = scheduledDateDisplay; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isAvailable() { return isAvailable; }
    public void setAvailable(boolean available) { isAvailable = available; }

    public boolean isPresent() { return present; }
    public void setPresent(boolean present) { this.present = present; }

    // --- New getters/setters to stop ClassMapper warnings ---
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getTeacherId() { return teacherId; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }

    public String getCourseDisplay() { return courseDisplay; }
    public void setCourseDisplay(String courseDisplay) { this.courseDisplay = courseDisplay; }

    // Convenience display builder (keeps previous logic)
    public String getCourseDisplayFallback() {
        StringBuilder display = new StringBuilder();
        if (courseName != null && !courseName.isEmpty()) display.append(courseName);
        if (specializationName != null && !specializationName.isEmpty()) display.append(" - ").append(specializationName);
        if (yearName != null && !yearName.isEmpty()) display.append(" - ").append(yearName);
        if (sectionName != null && !sectionName.isEmpty()) display.append(" - ").append(sectionName);
        return display.toString();
    }

    public String getSection() {
        return "";
    }
}