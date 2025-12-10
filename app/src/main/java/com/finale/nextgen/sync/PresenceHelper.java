package com.finale.nextgen.sync;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager; // <-- add this import

import com.finale.nextgen.offline.AppDatabase;
import com.finale.nextgen.offline.ExamEntity;
import com.finale.nextgen.offline.PendingPresence;

import java.util.UUID;

/**
 * Presence helper: save pending presence, merge metadata when available, broadcast update.
 */
public class PresenceHelper {
    private static final String TAG = "PresenceHelper";
    public static final String ACTION_PRESENCE_SAVED = "com.finale.nextgen.action.PRESENCE_SAVED";

    public static void savePresenceLocallyAndEnqueue(Context ctx, String examId, String studentId, ExamMetadata meta) {
        final Context appCtx = ctx.getApplicationContext();

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appCtx);

                // 1) Insert pending presence row
                PendingPresence p = new PendingPresence();
                p.id = UUID.randomUUID().toString();
                p.examId = examId;
                p.studentId = studentId;
                p.timestamp = System.currentTimeMillis();
                p.status = "PENDING";
                db.pendingPresenceDao().insert(p);
                Log.d(TAG, "Saved pending presence locally: " + p.id);

                // 2) Merge or create a fuller ExamEntity...
                try {
                    ExamEntity examEntity = db.examDao().getExamById(examId);
                    if (examEntity == null) {
                        examEntity = new ExamEntity();
                        examEntity.examId = examId;
                        if (meta != null) {
                            examEntity.examTitle = nonNull(meta.examTitle, "Untitled Exam");
                            examEntity.scheduledAt = meta.scheduledAt != null ? meta.scheduledAt : 0L;
                            examEntity.durationMinutes = meta.durationMinutes != null ? meta.durationMinutes : 0;
                            examEntity.teacherName = nonNull(meta.teacherName, "Unknown");
                            examEntity.courseName = nonNull(meta.courseName, "");
                            examEntity.specializationName = nonNull(meta.specializationName, "");
                            examEntity.yearName = nonNull(meta.yearName, "");
                            examEntity.sectionName = nonNull(meta.sectionName, "");
                        } else {
                            examEntity.examTitle = "Untitled Exam";
                            examEntity.scheduledAt = 0L;
                            examEntity.durationMinutes = 0;
                            examEntity.teacherName = "Unknown";
                            examEntity.courseName = "";
                            examEntity.specializationName = "";
                            examEntity.yearName = "";
                            examEntity.sectionName = "";
                        }
                        examEntity.present = true;
                        examEntity.isAvailable = examEntity.scheduledAt != null && examEntity.scheduledAt > 0;
                        db.examDao().insertExam(examEntity);
                        Log.d(TAG, "Inserted new local ExamEntity for examId=" + examId);
                    } else {
                        if (meta != null) {
                            if (isEmpty(examEntity.examTitle)) examEntity.examTitle = nonNull(meta.examTitle, examEntity.examTitle);
                            if (examEntity.scheduledAt == null || examEntity.scheduledAt == 0L) examEntity.scheduledAt = meta.scheduledAt != null ? meta.scheduledAt : examEntity.scheduledAt;
                            if (examEntity.durationMinutes == null || examEntity.durationMinutes == 0) examEntity.durationMinutes = meta.durationMinutes != null ? meta.durationMinutes : examEntity.durationMinutes;
                            if (isEmpty(examEntity.teacherName)) examEntity.teacherName = nonNull(meta.teacherName, examEntity.teacherName);
                            if (isEmpty(examEntity.courseName)) examEntity.courseName = nonNull(meta.courseName, examEntity.courseName);
                            if (isEmpty(examEntity.specializationName)) examEntity.specializationName = nonNull(meta.specializationName, examEntity.specializationName);
                            if (isEmpty(examEntity.yearName)) examEntity.yearName = nonNull(meta.yearName, examEntity.yearName);
                            if (isEmpty(examEntity.sectionName)) examEntity.sectionName = nonNull(meta.sectionName, examEntity.sectionName);
                        }
                        examEntity.present = true;
                        examEntity.isAvailable = examEntity.scheduledAt != null && examEntity.scheduledAt > 0;
                        db.examDao().insertExam(examEntity);
                        Log.d(TAG, "Merged local ExamEntity.present=true for examId=" + examId);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to update/merge cached ExamEntity: " + e.getMessage());
                }

                // 3) Enqueue WorkManager sync
                try {
                    SyncManager.enqueueImmediatePresenceSync(appCtx);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to enqueue presence sync: " + e.getMessage());
                }

                // 4) Broadcast so UI can refresh immediately (use LocalBroadcastManager)
                try {
                    Intent i = new Intent(ACTION_PRESENCE_SAVED);
                    i.putExtra("examId", examId);
                    i.putExtra("studentId", studentId);
                    LocalBroadcastManager.getInstance(appCtx).sendBroadcast(i); // <-- changed
                } catch (Exception e) {
                    Log.w(TAG, "Failed to broadcast presence saved: " + e.getMessage());
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to save pending presence: " + e.getMessage(), e);
            }
        }).start();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nonNull(String s, String fallback) {
        return (s == null || s.trim().isEmpty()) ? fallback : s;
    }
}