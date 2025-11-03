package com.example.nextgen.teacher;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * Represents an Exam entity for the teacher side.
 * Supports both local Room storage and Firebase sync.
 */
@Entity(tableName = "exams")
public class Exam {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String subject;
    private String examName;
    private int durationMinutes;  // ✅ duration now safely handled by Room & Firebase
    private long scheduledAt;     // ✅ timestamp in milliseconds
    private String section;       // ✅ used as courseDisplay (e.g., BSIT - 3A)
    private boolean active;
    private String firebaseKey;
    private String teacherId;

    // ===== Constructors =====
    public Exam() {
        // Required empty constructor for Firebase and Room
    }

    public Exam(String subject, String examName, int durationMinutes, long scheduledAt, String section) {
        this.subject = subject;
        this.examName = examName;
        this.durationMinutes = durationMinutes;
        this.scheduledAt = scheduledAt;
        this.section = section;
        this.active = false;
    }

    // ===== Getters =====
    public int getId() { return id; }
    public String getSubject() { return subject; }
    public String getExamName() { return examName; }
    public int getDurationMinutes() { return durationMinutes; }
    public long getScheduledAt() { return scheduledAt; }
    public String getSection() { return section; }
    public boolean isActive() { return active; }
    public String getFirebaseKey() { return firebaseKey; }
    public String getTeacherId() { return teacherId; }

    // ===== Setters =====
    public void setId(int id) { this.id = id; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setExamName(String examName) { this.examName = examName; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public void setScheduledAt(long scheduledAt) { this.scheduledAt = scheduledAt; }
    public void setSection(String section) { this.section = section; }
    public void setActive(boolean active) { this.active = active; }
    public void setFirebaseKey(String firebaseKey) { this.firebaseKey = firebaseKey; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }

    // ===== Utility / Display Methods =====
    public String getFormattedSchedule() {
        if (scheduledAt <= 0) return "Not scheduled";
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        return sdf.format(new Date(scheduledAt));
    }

    // Label for RecyclerView or logs
    public String getDisplayName() {
        return examName + " (" + subject + ")";
    }

    // ===== Firebase Mapping =====
    public HashMap<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("subject", subject);
        map.put("examName", examName);
        map.put("durationMinutes", durationMinutes);
        map.put("scheduledAt", scheduledAt);
        map.put("section", section);
        map.put("active", active);
        map.put("firebaseKey", firebaseKey);
        map.put("teacherId", teacherId);
        return map;
    }
}
