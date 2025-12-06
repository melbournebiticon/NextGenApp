package com.example.nextgen.teacher;

import com.google.firebase.database.DataSnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * AttendanceSummaryItem - POJO representing attendance summary for a student.
 *
 * Improvements in this version:
 * - Robust parsing from DataSnapshot (handles Long/Integer/Double/String).
 * - Ensures counts keys exist.
 * - computeWeightedAndPercentage uses counts and totalDays (falls back to sum(counts) if totalDays missing).
 * - applyStatusChange(previousStatus, newStatus, lastUpdatedTimestamp) updates counts/totalDays and recomputes.
 * - mergeWith(other) merges another AttendanceSummaryItem into this (useful to aggregate teacher summaries).
 * - toMap() produces a writeable map for Firebase.
 */
public class AttendanceSummaryItem {

    private String studentId;
    private String studentName; // optional
    private int attendancePercentage;
    private int totalDays;
    private Map<String, Long> counts = new HashMap<>(); // keys: Present, Late, Excused, Absent
    private long lastUpdated;

    // today's (last) status recorded
    private String todayStatus;

    // cached weighted score (0.. totalDays*100)
    private long weightedScore;

    // weights for scoring
    private static final Map<String, Integer> WEIGHTS;
    static {
        Map<String, Integer> w = new HashMap<>();
        w.put("Present", 100);
        w.put("Late", 90);
        w.put("Excused", 100);
        w.put("Absent", 0);
        WEIGHTS = Collections.unmodifiableMap(w);
    }

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
        this.counts = counts == null ? new HashMap<>() : new HashMap<>(counts);
        ensureCountsKeys();
        this.lastUpdated = lastUpdated;
        computeWeightedAndPercentage();
    }

    // --- getters / setters ---

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public int getAttendancePercentage() { return attendancePercentage; }
    public void setAttendancePercentage(int attendancePercentage) { this.attendancePercentage = attendancePercentage; }

    public int getTotalDays() { return totalDays; }
    public void setTotalDays(int totalDays) { this.totalDays = totalDays; }

    public Map<String, Long> getCounts() { return counts; }
    public void setCounts(Map<String, Long> counts) { this.counts = counts == null ? new HashMap<>() : new HashMap<>(counts); ensureCountsKeys(); }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public String getTodayStatus() { return todayStatus; }
    public void setTodayStatus(String todayStatus) { this.todayStatus = todayStatus; }

    public long getWeightedScore() { return weightedScore; }

    // --- helpers ---

    private void ensureCountsKeys() {
        if (counts == null) counts = new HashMap<>();
        if (!counts.containsKey("Present")) counts.put("Present", 0L);
        if (!counts.containsKey("Late")) counts.put("Late", 0L);
        if (!counts.containsKey("Excused")) counts.put("Excused", 0L);
        if (!counts.containsKey("Absent")) counts.put("Absent", 0L);
    }

    /**
     * Convert to Map for writing to Firebase.
     */
    public Map<String, Object> toMap() {
        ensureCountsKeys();
        Map<String, Object> out = new HashMap<>();
        out.put("studentId", studentId);
        out.put("studentName", studentName);
        out.put("attendancePercentage", attendancePercentage);
        out.put("totalDays", totalDays);
        out.put("counts", new HashMap<>(counts));
        out.put("weightedScore", weightedScore);
        out.put("lastUpdated", lastUpdated);
        if (todayStatus != null) out.put("lastStatus", todayStatus);
        return out;
    }

    /**
     * Apply a single-day status change.
     * - previousStatus may be null/empty (meaning previously not marked)
     * - newStatus may be null/empty (meaning unmark)
     * - lastUpdatedTimestamp is epoch millis (or Server timestamp equivalent)
     */
    public void applyStatusChange(String previousStatus, String newStatus, long lastUpdatedTimestamp) {
        ensureCountsKeys();

        boolean prevEmpty = previousStatus == null || previousStatus.trim().isEmpty();
        boolean newEmpty = newStatus == null || newStatus.trim().isEmpty();

        if (prevEmpty && !newEmpty) {
            // new marking => totalDays++
            totalDays = Math.max(0, totalDays) + 1;
            incrementByStatus(newStatus);
        } else if (!prevEmpty && newEmpty) {
            // removing a mark => totalDays--
            totalDays = Math.max(0, totalDays - 1);
            decrementByStatus(previousStatus);
        } else if (!prevEmpty && !newEmpty && !previousStatus.equals(newStatus)) {
            // changed status for same day
            decrementByStatus(previousStatus);
            incrementByStatus(newStatus);
        }
        // update todayStatus and lastUpdated
        this.todayStatus = newEmpty ? null : newStatus;
        this.lastUpdated = lastUpdatedTimestamp;

        computeWeightedAndPercentage();
    }

    private void incrementByStatus(String status) {
        if (status == null) return;
        Long cur = counts.getOrDefault(status, 0L);
        counts.put(status, cur + 1L);
    }

    private void decrementByStatus(String status) {
        if (status == null) return;
        Long cur = counts.getOrDefault(status, 0L);
        counts.put(status, Math.max(0L, cur - 1L));
    }

    /**
     * Compute weightedScore and attendancePercentage from current counts and totalDays.
     * If totalDays is zero, it will be derived from sum(counts).
     */
    public void computeWeightedAndPercentage() {
        ensureCountsKeys();
        long p = counts.getOrDefault("Present", 0L);
        long l = counts.getOrDefault("Late", 0L);
        long e = counts.getOrDefault("Excused", 0L);
        long a = counts.getOrDefault("Absent", 0L);

        long sumCounts = p + l + e + a;
        int derivedTotalDays = this.totalDays;
        if (derivedTotalDays <= 0) {
            derivedTotalDays = (int) Math.min(Integer.MAX_VALUE, sumCounts);
            this.totalDays = derivedTotalDays;
        }

        weightedScore = p * WEIGHTS.get("Present")
                + l * WEIGHTS.get("Late")
                + e * WEIGHTS.get("Excused")
                + a * WEIGHTS.get("Absent");

        if (derivedTotalDays > 0) {
            // weightedScore range 0 .. derivedTotalDays * 100
            double fraction = (double) weightedScore / ((double) derivedTotalDays * 100.0); // 0..1
            int pct = (int) Math.round(fraction * 100.0); // 0..100
            pct = Math.max(0, Math.min(100, pct));
            this.attendancePercentage = pct;
        } else {
            this.attendancePercentage = 0;
        }
    }

    /**
     * Merge another AttendanceSummaryItem into this one by summing counts and days.
     * - studentName will be set to this.studentName if present, otherwise to other.studentName if present.
     * - lastUpdated will be max(this.lastUpdated, other.lastUpdated).
     */
    public void mergeWith(AttendanceSummaryItem other) {
        if (other == null) return;
        ensureCountsKeys();
        other.ensureCountsKeys();

        // merge counts
        counts.put("Present", counts.getOrDefault("Present", 0L) + other.counts.getOrDefault("Present", 0L));
        counts.put("Late", counts.getOrDefault("Late", 0L) + other.counts.getOrDefault("Late", 0L));
        counts.put("Excused", counts.getOrDefault("Excused", 0L) + other.counts.getOrDefault("Excused", 0L));
        counts.put("Absent", counts.getOrDefault("Absent", 0L) + other.counts.getOrDefault("Absent", 0L));

        // merge totalDays: if either has a meaningful totalDays, sum them; otherwise derive later
        int td1 = this.totalDays;
        int td2 = other.totalDays;
        if (td1 <= 0 && td2 <= 0) {
            // keep 0 for now; computeWeightedAndPercentage will derive from counts
            this.totalDays = 0;
        } else {
            long sum = Math.max(0, td1) + Math.max(0, td2);
            this.totalDays = (int) Math.min(Integer.MAX_VALUE, sum);
        }

        // pick a name if missing
        if ((this.studentName == null || this.studentName.trim().isEmpty()) && other.studentName != null && !other.studentName.trim().isEmpty()) {
            this.studentName = other.studentName;
        }

        // lastUpdated = max
        this.lastUpdated = Math.max(this.lastUpdated, other.lastUpdated);

        // recompute
        computeWeightedAndPercentage();
    }

    /**
     * Create AttendanceSummaryItem from DataSnapshot (AttendanceSummary/{section}/{(teacherId)}/{studentId}).
     * Robustly parses numeric types.
     */
    public static AttendanceSummaryItem fromSnapshot(DataSnapshot s) {
        if (s == null || !s.exists()) return null;

        String studentId = s.getKey();

        int attendancePercentage = 0;
        Object pctObj = s.child("attendancePercentage").getValue();
        if (pctObj != null) attendancePercentage = parseIntSafe(pctObj);

        int td = 0;
        Object tdObj = s.child("totalDays").getValue();
        if (tdObj != null) td = parseIntSafe(tdObj);

        String studentName = s.child("studentName").getValue(String.class);
        if (studentName == null) studentName = s.child("name").getValue(String.class);

        Map<String, Long> counts = new HashMap<>();
        DataSnapshot countsSnap = s.child("counts");
        if (countsSnap != null && countsSnap.exists()) {
            for (DataSnapshot cs : countsSnap.getChildren()) {
                Object val = cs.getValue();
                long v = parseLongSafe(val);
                counts.put(cs.getKey(), v);
            }
        }
        // ensure keys
        if (!counts.containsKey("Present")) counts.put("Present", 0L);
        if (!counts.containsKey("Late")) counts.put("Late", 0L);
        if (!counts.containsKey("Excused")) counts.put("Excused", 0L);
        if (!counts.containsKey("Absent")) counts.put("Absent", 0L);

        // if totalDays missing or zero, derive from counts sum
        long sumCounts = counts.get("Present") + counts.get("Late") + counts.get("Excused") + counts.get("Absent");
        if (td <= 0 && sumCounts > 0) td = (int) Math.min(Integer.MAX_VALUE, sumCounts);

        long lu = 0L;
        Object luObj = s.child("lastUpdated").getValue();
        if (luObj != null) lu = parseLongSafe(luObj);

        String lastStatus = s.child("lastStatus").getValue(String.class);

        AttendanceSummaryItem item = new AttendanceSummaryItem(studentId, studentName, attendancePercentage, td, counts, lu);
        item.setTodayStatus(lastStatus);
        item.computeWeightedAndPercentage();
        return item;
    }

    // -------------------------
    // Parsing utilities
    // -------------------------
    private static long parseLongSafe(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception ignored) {
            try {
                double d = Double.parseDouble(String.valueOf(o));
                return (long) d;
            } catch (Exception ex) {
                return 0L;
            }
        }
    }

    private static int parseIntSafe(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception ignored) {
            try {
                double d = Double.parseDouble(String.valueOf(o));
                return (int) Math.round(d);
            } catch (Exception ex) {
                return 0;
            }
        }
    }
}