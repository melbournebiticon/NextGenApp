package com.finale.nextgen.teacher;

import android.text.TextUtils;

import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AttendanceSummaryItem - model + helpers for attendance summary.
 *
 * Aggregation helper updated to recursively find date nodes under Attendance/{sectionId}
 * so shapes like:
 *   Attendance/{section}/{group}/{teacherId}/{date}/{studentId}
 * will be discovered.
 *
 * NOTE: "TT" mapping has been removed from normalizeStatus per request.
 */
public class AttendanceSummaryItem {

    private String studentId;
    private String studentName; // optional
    private int attendancePercentage;
    private int totalDays;
    private Map<String, Long> counts = new HashMap<>(); // keys: Present, Late, Excused, Absent
    private long lastUpdated;

    // today's (last) status recorded (canonical)
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

    private static final Pattern DATE_KEY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final int MAX_RECURSIVE_DEPTH = 6;

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
     * Normalize a status string into one of the canonical labels:
     *   Present, Late, Excused, Absent
     * Returns empty string for unknown/empty.
     *
     * NOTE: "TT" mapping has been intentionally removed.
     */
    private static String normalizeStatus(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) return "";

        // common synonyms mapped to canonical labels (without "tt")
        if (s.equals("present") || s.equals("p") || s.equals("here") || s.equals("on time") || s.equals("ontime")) {
            return "Present";
        }
        if (s.equals("late") || s.equals("l") || s.equals("tardy")) {
            return "Late";
        }
        if (s.equals("excused") || s.equals("e")) {
            return "Excused";
        }
        if (s.equals("absent") || s.equals("a") || s.equals("abs")) {
            return "Absent";
        }

        // If it's already one of canonical labels but case differs
        String up = raw.trim();
        if ("Present".equalsIgnoreCase(up)) return "Present";
        if ("Late".equalsIgnoreCase(up)) return "Late";
        if ("Excused".equalsIgnoreCase(up)) return "Excused";
        if ("Absent".equalsIgnoreCase(up)) return "Absent";

        // unknown mapping -> treat as empty (not counted)
        return "";
    }

    /**
     * Apply a single-day status change.
     * - previousStatus may be null/empty (meaning previously not marked)
     * - newStatus may be null/empty (meaning unmark)
     * - lastUpdatedTimestamp is epoch millis (or Server timestamp equivalent)
     *
     * Normalizes statuses before applying counts.
     */
    public void applyStatusChange(String previousStatus, String newStatus, long lastUpdatedTimestamp) {
        ensureCountsKeys();

        String prevNorm = normalizeStatus(previousStatus);
        String newNorm = normalizeStatus(newStatus);

        boolean prevEmpty = prevNorm == null || prevNorm.trim().isEmpty();
        boolean newEmpty = newNorm == null || newNorm.trim().isEmpty();

        if (prevEmpty && !newEmpty) {
            // new marking => totalDays++
            totalDays = Math.max(0, totalDays) + 1;
            incrementByStatus(newNorm);
        } else if (!prevEmpty && newEmpty) {
            // removing a mark => totalDays--
            totalDays = Math.max(0, totalDays - 1);
            decrementByStatus(prevNorm);
        } else if (!prevEmpty && !newEmpty && !prevNorm.equals(newNorm)) {
            // changed status for same day
            decrementByStatus(prevNorm);
            incrementByStatus(newNorm);
        }
        // update todayStatus (store canonical) and lastUpdated
        this.todayStatus = newEmpty ? null : newNorm;
        this.lastUpdated = lastUpdatedTimestamp;

        computeWeightedAndPercentage();
    }

    private void incrementByStatus(String status) {
        if (status == null || status.isEmpty()) return;
        Long cur = counts.getOrDefault(status, 0L);
        counts.put(status, cur + 1L);
    }

    private void decrementByStatus(String status) {
        if (status == null || status.isEmpty()) return;
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

    /**
     * Aggregate per-student AttendanceSummaryItems from a DataSnapshot rooted at Attendance/{sectionId}.
     *
     * The returned map keys are studentId -> AttendanceSummaryItem.
     */
    public static Map<String, AttendanceSummaryItem> aggregateFromAttendanceRootSnapshot(DataSnapshot attendanceRoot) {
        Map<String, AttendanceSummaryItem> acc = new HashMap<>();
        if (attendanceRoot == null || !attendanceRoot.exists()) return acc;

        long now = System.currentTimeMillis();

        // recursively collect date nodes up to a limited depth
        List<DataSnapshot> dateNodes = new ArrayList<>();
        collectDateNodes(attendanceRoot, dateNodes, 0);

        for (DataSnapshot dateNode : dateNodes) {
            processDateNodeSnapshot(dateNode, acc, now);
        }

        return acc;
    }

    /**
     * New: Aggregate per-student AttendanceSummaryItems by scanning the entire top-level Attendance snapshot.
     * This will include "preview" date nodes stored under other section keys.
     *
     * Parameters:
     * - attendanceTop: DataSnapshot at root "Attendance" (Attendance/*)
     * - sectionId: display section name (e.g. "BSIT - BA - 1 - A") used for matching, may be null
     * - sectionFallbackKey: exact DB key used under Attendance (e.g. "fallback:bsit-ba-1-a"), may be null
     * - teacherId: teacher id to match teacher-scoped nodes (may be null)
     * - forceIncludeAll: when true, include all date nodes found under Attendance/* regardless of matching fields
     *
     * Returns map studentId -> AttendanceSummaryItem aggregated from matching date nodes.
     */
    public static Map<String, AttendanceSummaryItem> aggregateFromTopLevelAttendanceSnapshot(
            DataSnapshot attendanceTop,
            String sectionId,
            String sectionFallbackKey,
            String teacherId,
            boolean forceIncludeAll) {

        Map<String, AttendanceSummaryItem> acc = new HashMap<>();
        if (attendanceTop == null || !attendanceTop.exists()) return acc;

        long now = System.currentTimeMillis();

        // Iterate every top-level child under Attendance (each is a section key)
        for (DataSnapshot sectionNode : attendanceTop.getChildren()) {
            // collect date nodes under this sectionNode
            List<DataSnapshot> dateNodes = new ArrayList<>();
            collectDateNodes(sectionNode, dateNodes, 0);

            for (DataSnapshot dateNode : dateNodes) {
                // If not forcing inclusion, only process date nodes that contain at least one student record belonging to our section
                if (!forceIncludeAll && !dateNodeHasMatchingSection(dateNode, sectionId, sectionFallbackKey, teacherId)) {
                    continue;
                }
                // Merge this date node into accumulator
                processDateNodeSnapshot(dateNode, acc, now);
            }
        }

        return acc;
    }

    // recursively search for date-keyed nodes (yyyy-MM-dd) up to MAX_RECURSIVE_DEPTH
    private static void collectDateNodes(DataSnapshot node, List<DataSnapshot> out, int depth) {
        if (node == null || !node.exists() || depth > MAX_RECURSIVE_DEPTH) return;
        if (node.getKey() != null && DATE_KEY.matcher(node.getKey()).matches()) {
            out.add(node);
            return;
        }
        for (DataSnapshot ch : node.getChildren()) {
            collectDateNodes(ch, out, depth + 1);
        }
    }

    // process a single date node (which may be student -> record children OR teacher -> student -> record)
    private static void processDateNodeSnapshot(DataSnapshot dateNode, Map<String, AttendanceSummaryItem> acc, long now) {
        if (dateNode == null || !dateNode.exists()) return;

        // Build per-date dedupe map: studentId -> first seen {status, name}
        Map<String, SimpleDayRecord> perDate = new HashMap<>();

        // detect if dateNode children are student nodes (have status) or teacher nodes
        boolean looksLikeStudentDirect = false;
        for (DataSnapshot ch : dateNode.getChildren()) {
            try {
                if (ch.hasChild("status") || ch.hasChild("studentId")) {
                    looksLikeStudentDirect = true;
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (looksLikeStudentDirect) {
            for (DataSnapshot s : dateNode.getChildren()) {
                String sid = s.getKey();
                if (sid == null) continue;
                if (perDate.containsKey(sid)) continue;
                String status = safeString(s.child("status").getValue(String.class));
                String name = safeString(s.child("studentName").getValue(String.class));
                perDate.put(sid, new SimpleDayRecord(sid, name, status));
            }
        } else {
            // treat dateNode children as teacher nodes -> student children
            for (DataSnapshot teacherNode : dateNode.getChildren()) {
                for (DataSnapshot s : teacherNode.getChildren()) {
                    String sid = s.getKey();
                    if (sid == null) continue;
                    if (perDate.containsKey(sid)) continue;
                    String status = safeString(s.child("status").getValue(String.class));
                    String name = safeString(s.child("studentName").getValue(String.class));
                    perDate.put(sid, new SimpleDayRecord(sid, name, status));
                }
            }
        }

        // Merge perDate into acc: treat each non-empty status as a single day for that student
        for (SimpleDayRecord dr : perDate.values()) {
            AttendanceSummaryItem item = acc.get(dr.id);
            if (item == null) {
                item = new AttendanceSummaryItem();
                item.studentId = dr.id;
                item.studentName = (dr.name == null || dr.name.trim().isEmpty()) ? "(Unknown)" : dr.name;
                item.counts = new HashMap<>();
                item.ensureCountsKeys();
                item.totalDays = 0;
                item.lastUpdated = 0L;
                acc.put(dr.id, item);
            } else {
                // set name if missing
                if ((item.studentName == null || item.studentName.trim().isEmpty() || "(Unknown)".equals(item.studentName)) && dr.name != null && !dr.name.trim().isEmpty()) {
                    item.studentName = dr.name;
                }
            }

            String st = dr.status == null ? "" : dr.status.trim();
            // Use normalized status; since "TT" mapping removed, "TT" will be treated as unknown (not counted).
            String norm = normalizeStatus(st);
            if (!norm.isEmpty()) {
                // previousStatus is considered empty when building from scratch
                item.applyStatusChange("", norm, now);
            }
        }
    }

    /**
     * Return true if a dateNode contains at least one student record that indicates it belongs
     * to the target section by matching either sectionFallbackKey, section (display name), or teacherId.
     */
    private static boolean dateNodeHasMatchingSection(DataSnapshot dateNode, String sectionId, String sectionFallbackKey, String teacherId) {
        if (dateNode == null || !dateNode.exists()) return false;
        for (DataSnapshot child : dateNode.getChildren()) {
            // child might be a student record or a teacher node
            if (child.hasChild("status") || child.hasChild("studentId")) {
                String sfk = safeString(child.child("sectionFallbackKey").getValue(String.class));
                String sectionName = safeString(child.child("section").getValue(String.class));
                String tid = safeString(child.child("teacherId").getValue(String.class));
                if ((!TextUtils.isEmpty(sectionFallbackKey) && sectionFallbackKey.equals(sfk))
                        || (!TextUtils.isEmpty(sectionId) && sectionId.equalsIgnoreCase(sectionName))
                        || (!TextUtils.isEmpty(teacherId) && teacherId.equals(tid))) {
                    return true;
                }
            } else {
                // child likely teacher node; check its students
                for (DataSnapshot s : child.getChildren()) {
                    String sfk = safeString(s.child("sectionFallbackKey").getValue(String.class));
                    String sectionName = safeString(s.child("section").getValue(String.class));
                    String tid = safeString(s.child("teacherId").getValue(String.class));
                    if ((!TextUtils.isEmpty(sectionFallbackKey) && sectionFallbackKey.equals(sfk))
                            || (!TextUtils.isEmpty(sectionId) && sectionId.equalsIgnoreCase(sectionName))
                            || (!TextUtils.isEmpty(teacherId) && teacherId.equals(tid))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // small helper for per-date record
    private static class SimpleDayRecord {
        final String id;
        final String name;
        final String status;
        SimpleDayRecord(String id, String name, String status) { this.id = id; this.name = name; this.status = status; }
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

    private static String safeString(String s) { return s == null ? "" : s; }
}