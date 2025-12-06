package com.example.nextgen.teacher;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "quiz_table")
public class Quiz {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String firebaseKey = ""; // Firebase unique key
    private String quizName = "";    // Quiz title
    private String subject = "";     // Subject name
    private String section = "";     // Course display like "BSIT - SD - 1 - A"
    private int durationMinutes = 15; // Default duration
    private long scheduledAt = 0L;    // Epoch time for scheduled date
    private boolean isActive = false; // Active status
    private String teacherId = "";     // Teacher who created the quiz

    // ===== Constructors =====
    public Quiz() {}

    // Constructor for creating new Quiz
    public Quiz(@NonNull String quizName, @NonNull String subject, int durationMinutes,
                long scheduledAt, @NonNull String section, @NonNull String teacherId) {
        this.quizName = quizName;
        this.subject = subject;
        this.durationMinutes = durationMinutes;
        this.scheduledAt = scheduledAt;
        this.section = section;
        this.teacherId = teacherId;
        this.isActive = false;   // default inactive
        this.firebaseKey = "";   // firebase key to be set when synced
    }

    // ===== Getters and Setters =====
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @NonNull
    public String getFirebaseKey() { return firebaseKey != null ? firebaseKey : ""; }
    public void setFirebaseKey(@NonNull String firebaseKey) { this.firebaseKey = firebaseKey; }

    @NonNull
    public String getQuizName() { return quizName != null ? quizName : ""; }
    public void setQuizName(@NonNull String quizName) { this.quizName = quizName; }

    @NonNull
    public String getSubject() { return subject != null ? subject : ""; }
    public void setSubject(@NonNull String subject) { this.subject = subject; }

    @NonNull
    public String getSection() { return section != null ? section : ""; }
    public void setSection(@NonNull String section) { this.section = section; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public long getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(long scheduledAt) { this.scheduledAt = scheduledAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    @NonNull
    public String getTeacherId() { return teacherId != null ? teacherId : ""; }
    public void setTeacherId(@NonNull String teacherId) { this.teacherId = teacherId; }
}
