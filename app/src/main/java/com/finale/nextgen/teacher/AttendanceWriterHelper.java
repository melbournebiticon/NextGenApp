package com.finale.nextgen.teacher;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.DatabaseError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AttendanceWriterHelper
 *
 * Improvements:
 * - Writes per-day attendance to both date-first and teacher-first shapes for primary and fallback keys to
 *   cover common DB shapes found in the project.
 * - Reads existing per-day record by trying all likely candidate paths (date-first / teacher-first, primary/fallback).
 * - Writes optional marks/remark when provided.
 * - Keeps summary transaction behavior (AttendanceSummary/{sectionId}/{teacherId?}/{studentId}).
 */
public final class AttendanceWriterHelper {
    private static final String TAG = "AttendanceWriterHelper";

    public interface SimpleCallback {
        void onComplete(boolean success, String message);
    }

    /**
     * Backwards-compatible signature (no marks).
     */
    public static void saveAttendanceRecord(
            @NonNull final String sectionId,
            final String teacherId,
            @NonNull final String dateKey,
            @NonNull final String studentId,
            @NonNull final String studentName,
            @NonNull final String newStatus,
            final SimpleCallback cb) {
        saveAttendanceRecord(sectionId, null, teacherId, dateKey, studentId, studentName, newStatus, null, cb);
    }

    /**
     * Overload that accepts a sectionFallbackKey (existing) but no marks.
     */
    public static void saveAttendanceRecord(
            @NonNull final String sectionId,
            final String sectionFallbackKey,
            final String teacherId,
            @NonNull final String dateKey,
            @NonNull final String studentId,
            @NonNull final String studentName,
            @NonNull final String newStatus,
            final SimpleCallback cb) {
        saveAttendanceRecord(sectionId, sectionFallbackKey, teacherId, dateKey, studentId, studentName, newStatus, null, cb);
    }

    /**
     * New overload that accepts optional marks (or remark). If marks is non-null it will be written to per-day nodes.
     *
     * @param marks optional free-text remark/marks to save with the attendance node (may be null)
     */
    @SuppressLint("RestrictedApi")
    public static void saveAttendanceRecord(
            @NonNull final String sectionId,
            final String sectionFallbackKey,
            final String teacherId,
            @NonNull final String dateKey,
            @NonNull final String studentId,
            @NonNull final String studentName,
            @NonNull final String newStatus,
            final String marks,
            final SimpleCallback cb) {

        final DatabaseReference root = FirebaseDatabase.getInstance().getReference();

        // We'll construct the set of per-day refs we want to write to. To be robust we include both
        // "date-first" and "teacher-first" shapes:
        // - date-first:   Attendance/{sectionKey}/{dateKey}/{teacherId?}/{studentId}
        // - teacher-first: Attendance/{sectionKey}/{teacherId?}/{dateKey}/{studentId}

        final List<DatabaseReference> writeRefs = new ArrayList<>();
        final List<DatabaseReference> readCandidates = new ArrayList<>();

        // Helper to add date-first and teacher-first refs for a given sectionKey (if not null)
        java.util.function.Consumer<String> addRefsForSection = sectionKey -> {
            if (sectionKey == null || sectionKey.trim().isEmpty()) return;
            // date-first
            String dateFirstPath = (teacherId != null && !teacherId.isEmpty())
                    ? String.format(Locale.US, "Attendance/%s/%s/%s/%s", sectionKey, dateKey, teacherId, studentId)
                    : String.format(Locale.US, "Attendance/%s/%s/%s", sectionKey, dateKey, studentId);
            writeRefs.add(root.child(dateFirstPath));
            readCandidates.add(root.child(dateFirstPath));

            // teacher-first
            String teacherFirstPath = (teacherId != null && !teacherId.isEmpty())
                    ? String.format(Locale.US, "Attendance/%s/%s/%s/%s", sectionKey, teacherId, dateKey, studentId)
                    : String.format(Locale.US, "Attendance/%s/%s/%s", sectionKey, dateKey, studentId);
            // If teacher-first equals date-first (no teacher) don't add twice
            DatabaseReference tfRef = root.child(teacherFirstPath);
            boolean dup = false;
            for (DatabaseReference r : writeRefs) if (r.getPath().toString().equals(tfRef.getPath().toString())) { dup = true; break; }
            if (!dup) {
                writeRefs.add(tfRef);
                readCandidates.add(tfRef);
            }
        };

        // Primary sectionKey
        addRefsForSection.accept(sectionId);

        // Fallback section key (if provided and different)
        if (sectionFallbackKey != null && !sectionFallbackKey.trim().isEmpty() && !sectionFallbackKey.equals(sectionId)) {
            addRefsForSection.accept(sectionFallbackKey);
        }

        // Deduplicate readCandidates and writeRefs by path
        List<DatabaseReference> uniqueReadCandidates = new ArrayList<>();
        for (DatabaseReference r : readCandidates) {
            boolean exists = false;
            for (DatabaseReference u : uniqueReadCandidates) {
                if (u.getPath().toString().equals(r.getPath().toString())) { exists = true; break; }
            }
            if (!exists) uniqueReadCandidates.add(r);
        }

        List<DatabaseReference> uniqueWriteRefs = new ArrayList<>();
        for (DatabaseReference r : writeRefs) {
            boolean exists = false;
            for (DatabaseReference u : uniqueWriteRefs) {
                if (u.getPath().toString().equals(r.getPath().toString())) { exists = true; break; }
            }
            if (!exists) uniqueWriteRefs.add(r);
        }

        // Summary path: AttendanceSummary/{sectionId}/{teacherId?}/{studentId}
        final String summaryPath = (teacherId != null && !teacherId.isEmpty())
                ? String.format(Locale.US, "AttendanceSummary/%s/%s/%s", sectionId, teacherId, studentId)
                : String.format(Locale.US, "AttendanceSummary/%s/%s", sectionId, studentId);
        final DatabaseReference summaryRef = root.child(summaryPath);

        // Step 1: Read existing per-day attendance to get oldStatus.
        attemptReadSequence(uniqueReadCandidates, 0, (foundRef, foundStatus) -> {
            final String oldStatus = foundStatus == null ? "" : foundStatus;

            // Build per-day record to write
            Map<String, Object> dayRecord = new HashMap<>();
            dayRecord.put("status", newStatus == null ? "" : newStatus);
            dayRecord.put("studentId", studentId);
            dayRecord.put("studentName", studentName);
            dayRecord.put("lastUpdated", System.currentTimeMillis());
            dayRecord.put("section", sectionId);
            if (sectionFallbackKey != null && !sectionFallbackKey.isEmpty())
                dayRecord.put("sectionFallbackKey", sectionFallbackKey);
            if (teacherId != null && !teacherId.isEmpty())
                dayRecord.put("teacherId", teacherId);
            if (marks != null) dayRecord.put("marks", marks);

            // Write to all uniqueWriteRefs and wait for completion
            final AtomicInteger completed = new AtomicInteger(0);
            final AtomicInteger successes = new AtomicInteger(0);
            final int total = uniqueWriteRefs.size();

            for (DatabaseReference wr : uniqueWriteRefs) {
                wr.setValue(dayRecord).addOnCompleteListener(writeTask -> {
                    completed.incrementAndGet();
                    if (writeTask.isSuccessful()) successes.incrementAndGet();
                    if (completed.get() == total) {
                        if (successes.get() != total) {
                            Log.w(TAG, "One or more attendance writes failed");
                            if (cb != null) cb.onComplete(false, "Failed to write attendance to all paths");
                            return;
                        }
                        // All writes succeeded -> run summary transaction (against summaryRef only)
                        runSummaryTransaction(summaryRef, oldStatus, newStatus, studentName, cb);
                    }
                });
            }
        });
    }

    // Attempt to read each candidate ref in order until an existing snapshot is found or the list ends.
    private static void attemptReadSequence(final List<DatabaseReference> candidates, final int index, final ReadCallback callback) {
        if (index >= candidates.size()) {
            callback.onResult(null, "");
            return;
        }
        final DatabaseReference ref = candidates.get(index);
        ref.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                // try next
                attemptReadSequence(candidates, index + 1, callback);
                return;
            }
            DataSnapshot snap = task.getResult();
            if (snap != null && snap.exists()) {
                String status = safeString(snap.child("status").getValue(String.class));
                callback.onResult(ref, status);
            } else {
                attemptReadSequence(candidates, index + 1, callback);
            }
        }).addOnFailureListener(e -> attemptReadSequence(candidates, index + 1, callback));
    }

    private interface ReadCallback { void onResult(DatabaseReference foundRef, String status); }

    // Run transaction on summaryRef to apply delta between oldStatus and newStatus
    private static void runSummaryTransaction(
            @NonNull final DatabaseReference summaryRef,
            final String oldStatus,
            final String newStatus,
            final String studentNameIfProvided,
            final SimpleCallback cb) {

        summaryRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Object raw = currentData.getValue();
                Map<String, Object> summary = (raw instanceof Map) ? (Map<String, Object>) raw : new HashMap<>();

                Map<String, Object> counts = (summary.containsKey("counts") && summary.get("counts") instanceof Map)
                        ? (Map<String, Object>) summary.get("counts")
                        : new HashMap<>();

                long present = safeLong(counts.get("Present"));
                long late = safeLong(counts.get("Late"));
                long excused = safeLong(counts.get("Excused"));
                long absent = safeLong(counts.get("Absent"));
                int totalDays = safeInt(summary.get("totalDays"));

                // decrement oldStatus count if present and oldStatus non-empty
                if (oldStatus != null && !oldStatus.isEmpty()) {
                    if ("Present".equalsIgnoreCase(oldStatus) && present > 0) present--;
                    else if ("Late".equalsIgnoreCase(oldStatus) && late > 0) late--;
                    else if ("Excused".equalsIgnoreCase(oldStatus) && excused > 0) excused--;
                    else if ("Absent".equalsIgnoreCase(oldStatus) && absent > 0) absent--;
                    // totalDays unchanged because old status accounted for that day
                }

                // increment newStatus
                if (newStatus != null && !newStatus.isEmpty()) {
                    if ("Present".equalsIgnoreCase(newStatus)) present++;
                    else if ("Late".equalsIgnoreCase(newStatus)) late++;
                    else if ("Excused".equalsIgnoreCase(newStatus)) excused++;
                    else if ("Absent".equalsIgnoreCase(newStatus)) absent++;
                }

                // Adjust totalDays:
                if ((oldStatus == null || oldStatus.isEmpty()) && (newStatus != null && !newStatus.isEmpty())) {
                    totalDays += 1;
                } else if ((oldStatus != null && !oldStatus.isEmpty()) && (newStatus == null || newStatus.isEmpty())) {
                    totalDays = Math.max(0, totalDays - 1);
                }
                // else: status changed (non-empty -> non-empty) => totalDays unchanged

                Map<String, Object> newCounts = new HashMap<>();
                newCounts.put("Present", present);
                newCounts.put("Late", late);
                newCounts.put("Excused", excused);
                newCounts.put("Absent", absent);

                summary.put("counts", newCounts);
                summary.put("totalDays", totalDays);
                if (studentNameIfProvided != null) summary.put("studentName", studentNameIfProvided);
                summary.put("lastUpdated", System.currentTimeMillis());

                currentData.setValue(summary);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) {
                    Log.w(TAG, "Summary transaction failed", error.toException());
                    if (cb != null) cb.onComplete(false, "Failed to update summary");
                } else {
                    if (cb != null) cb.onComplete(true, "Attendance saved and summary updated");
                }
            }
        });
    }

    // helpers
    private static int safeInt(Object o) {
        if (o == null) return 0;
        try {
            if (o instanceof Number) return ((Number) o).intValue();
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) { return 0; }
    }

    private static long safeLong(Object o) {
        if (o == null) return 0L;
        try {
            if (o instanceof Number) return ((Number) o).longValue();
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) { return 0L; }
    }

    private static String safeString(String s) { return s == null ? "" : s; }
}