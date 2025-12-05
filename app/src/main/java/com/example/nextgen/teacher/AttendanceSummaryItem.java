package com.example.nextgen.teacher;

import com.google.firebase.database.DataSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * AttendanceSummaryItem - simple POJO model representing attendance summary for a student.
 *
 * - This is a plain model (DOES NOT extend RecyclerView.Adapter).
 * - Includes helpers to convert to/from a Map (useful for Firebase writes/reads).
 */
public class AttendanceSummaryItem {

    private String studentId;
    private String studentName; // optional, may be null
    private int attendancePercentage;
    private int totalDays;
    private Map<String, Long> counts = new HashMap<>(); // Present, Late, Excused, Absent
    private long lastUpdated;

    // New: today's status (Present, Late, Excused, Absent) or null if not marked today
    private String todayStatus;

    // Required no-arg constructor for Firebase deserialization
    public AttendanceSummaryItem() {}

    public AttendanceSummaryItem(String studentId,
                                 String studentName,
                                 int attendancePercentage,
                                 int totalDays,
                                 Map<String, Long> counts,
                                 long lastUpdated) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.attendancePercentage = attendancePercentage;
        this.totalDays = totalDays;
        this.counts = counts == null ? new HashMap<>() : counts;
        this.lastUpdated = lastUpdated;
    }

    // --- getters / setters ---

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public int getAttendancePercentage() {
        return attendancePercentage;
    }

    public void setAttendancePercentage(int attendancePercentage) {
        this.attendancePercentage = attendancePercentage;
    }

    public int getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(int totalDays) {
        this.totalDays = totalDays;
    }

    public Map<String, Long> getCounts() {
        return counts;
    }

    public void setCounts(Map<String, Long> counts) {
        this.counts = counts == null ? new HashMap<>() : counts;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getTodayStatus() {
        return todayStatus;
    }

    public void setTodayStatus(String todayStatus) {
        this.todayStatus = todayStatus;
    }

    // --- helpers ---

    /**
     * Convert this model to a Map suitable for writing to Firebase.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> out = new HashMap<>();
        out.put("studentId", studentId);
        out.put("studentName", studentName);
        out.put("attendancePercentage", attendancePercentage);
        out.put("totalDays", totalDays);
        out.put("counts", counts);
        out.put("lastUpdated", lastUpdated);
        if (todayStatus != null) out.put("lastStatus", todayStatus);
        return out;
    }

    /**
     * Create an AttendanceSummaryItem from a Firebase DataSnapshot of AttendanceSummary/{sectionId}/{studentId}
     * Returns null if snapshot is null.
     */
    public static AttendanceSummaryItem fromSnapshot(DataSnapshot s) {
        if (s == null || !s.exists()) return null;

        String studentId = s.getKey();

        Integer pct = null;
        Object pctObj = s.child("attendancePercentage").getValue();
        if (pctObj instanceof Long) pct = ((Long) pctObj).intValue();
        else if (pctObj instanceof Integer) pct = (Integer) pctObj;
        else if (pctObj instanceof Double) pct = (int) Math.round((Double) pctObj);
        int attendancePercentage = pct != null ? pct : 0;

        Integer totalDays = null;
        Object tdObj = s.child("totalDays").getValue();
        if (tdObj instanceof Long) totalDays = ((Long) tdObj).intValue();
        else if (tdObj instanceof Integer) totalDays = (Integer) tdObj;
        else if (tdObj instanceof Double) totalDays = (int) Math.round((Double) tdObj);
        int td = totalDays != null ? totalDays : 0;

        String studentName = s.child("studentName").getValue(String.class);
        if (studentName == null) studentName = s.child("name").getValue(String.class);

        Map<String, Long> counts = new HashMap<>();
        DataSnapshot countsSnap = s.child("counts");
        if (countsSnap.exists()) {
            for (DataSnapshot cs : countsSnap.getChildren()) {
                Long v = cs.getValue(Long.class);
                counts.put(cs.getKey(), v != null ? v : 0L);
            }
        } else {
            counts.put("Present", 0L);
            counts.put("Late", 0L);
            counts.put("Excused", 0L);
            counts.put("Absent", 0L);
        }

        Long lastUpdated = s.child("lastUpdated").getValue(Long.class);
        long lu = lastUpdated != null ? lastUpdated : 0L;

        String lastStatus = s.child("lastStatus").getValue(String.class);

        AttendanceSummaryItem item = new AttendanceSummaryItem(studentId, studentName, attendancePercentage, td, counts, lu);
        item.setTodayStatus(lastStatus);
        return item;
    }
}