package com.example.nextgen;

import java.util.HashMap;
import java.util.Map;

/**
 * AttendanceUtil - helper methods for computing attendance weighted scores and percentages.
 *
 * Place this file at: app/src/main/java/com/example/nextgen/AttendanceUtil.java
 *
 * Methods are null-safe and accept Number types (Integer/Long) which is convenient when reading
 * counts from Firebase (which often returns Long).
 */
public final class AttendanceUtil {

    private AttendanceUtil() { /* no-op */ }

    /**
     * Compute attendance percentage (0..100) using provided counts and weights.
     *
     * @param counts  Map with keys: "Present", "Late", "Excused", "Absent" (values can be Integer/Long)
     * @param totalDays Total scheduled class days in the period (must be > 0)
     * @param weights Map with same keys mapping to integer weight percentages (0..100).
     *                If null or missing keys, defaults are used.
     * @return attendance percentage (rounded) in range 0..100. Returns 0 when totalDays <= 0.
     */
    public static int computeAttendancePercentage(Map<String, ? extends Number> counts,
                                                  int totalDays,
                                                  Map<String, Integer> weights) {
        if (totalDays <= 0) return 0;

        int present = numberToInt(getNumberSafely(counts, "Present"));
        int late = numberToInt(getNumberSafely(counts, "Late"));
        int excused = numberToInt(getNumberSafely(counts, "Excused"));
        int absent = numberToInt(getNumberSafely(counts, "Absent"));

        Map<String, Integer> w = (weights == null) ? defaultWeights() : mergeWithDefaults(weights);

        long totalWeighted = (long) present * w.get("Present")
                + (long) late * w.get("Late")
                + (long) excused * w.get("Excused")
                + (long) absent * w.get("Absent");

        // maximum possible weight = totalDays * 100
        double percent = ((double) totalWeighted) / (totalDays * 100.0) * 100.0;
        int rounded = (int) Math.round(percent);

        if (rounded < 0) rounded = 0;
        if (rounded > 100) rounded = 100;
        return rounded;
    }

    /**
     * Convenience: compute attendance percentage using default weights:
     * Present=100, Late=90, Excused=100, Absent=0
     */
    public static int computeAttendancePercentage(Map<String, ? extends Number> counts, int totalDays) {
        return computeAttendancePercentage(counts, totalDays, null);
    }

    /**
     * Compute raw weighted score = sum(count * weight).
     * Useful if you want the absolute weighted points instead of percentage.
     *
     * @return weighted score (long)
     */
    public static long computeWeightedScore(Map<String, ? extends Number> counts,
                                            Map<String, Integer> weights) {
        int present = numberToInt(getNumberSafely(counts, "Present"));
        int late = numberToInt(getNumberSafely(counts, "Late"));
        int excused = numberToInt(getNumberSafely(counts, "Excused"));
        int absent = numberToInt(getNumberSafely(counts, "Absent"));

        Map<String, Integer> w = (weights == null) ? defaultWeights() : mergeWithDefaults(weights);

        return (long) present * w.get("Present")
                + (long) late * w.get("Late")
                + (long) excused * w.get("Excused")
                + (long) absent * w.get("Absent");
    }

    /**
     * Return default weights map.
     */
    public static Map<String, Integer> defaultWeights() {
        Map<String, Integer> w = new HashMap<>();
        w.put("Present", 100);
        w.put("Late", 90);
        w.put("Excused", 100);
        w.put("Absent", 0);
        return w;
    }

    /**
     * Merge a user-supplied weights map with defaults so missing keys are filled.
     */
    private static Map<String, Integer> mergeWithDefaults(Map<String, Integer> weights) {
        Map<String, Integer> merged = defaultWeights();
        if (weights == null) return merged;
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            String k = e.getKey();
            Integer v = e.getValue();
            if (k != null && v != null) merged.put(k, v);
        }
        return merged;
    }

    // safe extraction helpers
    private static Number getNumberSafely(Map<String, ? extends Number> counts, String key) {
        if (counts == null) return 0;
        Number n = counts.get(key);
        return n == null ? 0 : n;
    }

    private static int numberToInt(Number n) {
        if (n == null) return 0;
        return n.intValue();
    }
}