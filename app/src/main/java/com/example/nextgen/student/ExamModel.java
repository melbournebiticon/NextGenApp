package com.example.nextgen.student;

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

    // 🏆 NEW FIELDS FOR SCHEDULING AND STATUS
    private int durationMinutes = 0;
    private long scheduledAt = 0;
    private String scheduledDateDisplay;
    private String status;
    private boolean isAvailable;

    // ✅ NEW FIELD: student presence
    private boolean present;

    public ExamModel() {}

    public ExamModel(
            String examId,
            String examTitle,
            String courseId,
            String courseName,
            String specializationName,
            String yearName,
            String sectionName,
            String teacherName,
            String createdAt,
            int durationMinutes,
            long scheduledAt,
            String scheduledDateDisplay,
            boolean active
    ) {
        this.examId = examId;
        this.examTitle = examTitle;
        this.courseId = courseId;
        this.courseName = courseName;
        this.specializationName = specializationName;
        this.yearName = yearName;
        this.sectionName = sectionName;
        this.teacherName = teacherName;
        this.createdAt = createdAt;
        this.active = active;
        this.durationMinutes = durationMinutes;
        this.scheduledAt = scheduledAt;
        this.scheduledDateDisplay = scheduledDateDisplay;
    }

    // ===== Existing Getters and Setters =====
    public String getExamId() { return examId; }
    public String getExamTitle() { return examTitle; }
    public String getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public String getSpecializationName() { return specializationName; }
    public String getYearName() { return yearName; }
    public String getSectionName() { return sectionName; }
    public String getTeacherName() { return teacherName; }
    public String getCreatedAt() { return createdAt; }
    public boolean isActive() { return active; }

    public void setExamId(String examId) { this.examId = examId; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public void setSpecializationName(String specializationName) { this.specializationName = specializationName; }
    public void setYearName(String yearName) { this.yearName = yearName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setActive(boolean active) { this.active = active; }

    // 🏆 NEW Getters and Setters for scheduling & status
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public long getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(long scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getScheduledDateDisplay() { return scheduledDateDisplay; }
    public void setScheduledDateDisplay(String scheduledDateDisplay) { this.scheduledDateDisplay = scheduledDateDisplay; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isAvailable() { return isAvailable; }
    public void setAvailable(boolean available) { isAvailable = available; }

    // ✅ NEW: presence getter/setter
    public boolean isPresent() { return present; }
    public void setPresent(boolean present) { this.present = present; }

    // ===== getCourseDisplay() method =====
    public String getCourseDisplay() {
        StringBuilder display = new StringBuilder();

        if (courseName != null && !courseName.isEmpty()) {
            display.append(courseName);
        }

        if (specializationName != null && !specializationName.isEmpty()) {
            display.append(" - ").append(specializationName);
        }

        if (yearName != null && !yearName.isEmpty()) {
            display.append(" - ").append(yearName);
        }

        if (sectionName != null && !sectionName.isEmpty()) {
            display.append(" - ").append(sectionName);
        }

        return display.toString();
    }
}
