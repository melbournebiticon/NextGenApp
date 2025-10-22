package com.example.nextgen.teacher;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "exams")
public class Exam {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String subject;
    private String examName;
    private int durationMinutes;
    private long scheduledAt; // epoch millis
    private String section;
    private boolean active; // ✅ for activation checkbox

    // ===== Constructor =====
    public Exam(String subject, String examName, int durationMinutes, long scheduledAt, String section) {
        this.subject = subject;
        this.examName = examName;
        this.durationMinutes = durationMinutes;
        this.scheduledAt = scheduledAt;
        this.section = section;
        this.active = false; // default inactive
    }

    // ===== Getters =====
    public int getId() { return id; }
    public String getSubject() { return subject; }
    public String getExamName() { return examName; }
    public int getDurationMinutes() { return durationMinutes; }
    public long getScheduledAt() { return scheduledAt; }
    public String getSection() { return section; }

    // ✅ New — readable formatted schedule
    public String getSchedule() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a");
        return sdf.format(new java.util.Date(scheduledAt));
    }

    // ✅ Activation (for checkbox)
    public boolean isActive() {
        return active;
    }

    // ===== Setters =====
    public void setId(int id) { this.id = id; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setExamName(String examName) { this.examName = examName; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public void setScheduledAt(long scheduledAt) { this.scheduledAt = scheduledAt; }
    public void setSection(String section) { this.section = section; }
    public void setActive(boolean active) { this.active = active; }
}
