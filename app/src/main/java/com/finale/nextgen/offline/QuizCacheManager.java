package com.finale.nextgen.offline;

import android.content.Context;
import android.util.Log;

import com.finale.nextgen.student.QuizModel;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Updated save/load: normalize timestamps (seconds -> ms), compute availableAt fallback,
 * parse section into course/spec/year/section, and store durationMinutes so offline availability checks work.
 */
public final class QuizCacheManager {
    private static final String TAG = "QuizCacheManager";

    private QuizCacheManager() {}

    private static long normalizeTimestampLong(Long ts) {
        if (ts == null) return 0L;
        // if looks like seconds (10 digits), convert to ms
        if (ts > 0 && ts < 1_000_000_000_000L) return ts * 1000L;
        return ts;
    }

    public static void saveSnapshot(Context ctx, DataSnapshot snapshot) {
        try {
            Log.d(TAG, "saveSnapshot called, snapshotExists=" + (snapshot != null && snapshot.exists()));
            final com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(ctx);
            final List<QuizEntity> list = new ArrayList<>();
            long now = System.currentTimeMillis();
            if (snapshot != null && snapshot.exists()) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    try {
                        QuizEntity q = new QuizEntity();
                        q.quizId = child.getKey();

                        // Raw string fields
                        String rawQuizName = child.child("quizName").getValue(String.class);
                        String rawTeacherName = child.child("teacherName").getValue(String.class);
                        String rawSubjectName = child.child("subjectName").getValue(String.class);
                        String courseNameRaw = child.child("courseName").getValue(String.class);
                        String courseDisplayRaw = child.child("courseDisplay").getValue(String.class);
                        String sectionValue = child.child("section").getValue(String.class);
                        String rawSpecialization = child.child("specializationName").getValue(String.class);
                        String rawYear = child.child("yearName").getValue(String.class);

                        // parse sectionValue into parts if present
                        String parsedCourse = "";
                        String parsedSpec = "";
                        String parsedYear = "";
                        String parsedSection = "";

                        if (sectionValue != null && !sectionValue.trim().isEmpty()) {
                            String s = sectionValue.trim();
                            if (s.contains(" - ")) {
                                String[] parts = s.split(" - ");
                                if (parts.length > 0) parsedCourse = parts[0].trim();
                                if (parts.length > 1) parsedSpec = parts[1].trim();
                                if (parts.length > 2) parsedYear = parts[2].trim();
                                if (parts.length > 3) parsedSection = parts[3].trim();
                            } else {
                                parsedSection = s;
                            }
                        }

                        // choose course fallback order: parsedCourse -> courseNameRaw -> courseDisplayRaw
                        String chosenCourse = (!parsedCourse.isEmpty()) ? parsedCourse
                                : (courseNameRaw != null && !courseNameRaw.trim().isEmpty() ? courseNameRaw.trim()
                                : (courseDisplayRaw != null ? courseDisplayRaw.trim() : null));

                        // assign parsed/normalized string fields to entity
                        q.quizName = (rawQuizName != null && !rawQuizName.trim().isEmpty()) ? rawQuizName.trim() : null;
                        q.teacherName = (rawTeacherName != null && !rawTeacherName.trim().isEmpty()) ? rawTeacherName.trim() : null;
                        q.subjectName = (rawSubjectName != null && !rawSubjectName.trim().isEmpty()) ? rawSubjectName.trim() : null;
                        q.courseName = (chosenCourse != null && !chosenCourse.trim().isEmpty()) ? chosenCourse : null;
                        q.sectionName = (!parsedSection.isEmpty()) ? parsedSection : (sectionValue != null ? sectionValue.trim() : null);
                        q.specializationName = (parsedSpec != null && !parsedSpec.isEmpty()) ? parsedSpec : (rawSpecialization != null ? rawSpecialization.trim() : null);
                        q.yearName = (parsedYear != null && !parsedYear.isEmpty()) ? parsedYear : (rawYear != null ? rawYear.trim() : null);

                        // raw times from DB
                        Long scheduledAtRaw = child.child("scheduledAt").getValue(Long.class);
                        Long availableAtRaw = child.child("availableAt").getValue(Long.class);
                        Integer availableAfterMinutes = child.child("availableAfterMinutes").getValue(Integer.class);
                        Integer duration = child.child("durationMinutes").getValue(Integer.class);

                        // normalize to milliseconds
                        long scheduledAt = normalizeTimestampLong(scheduledAtRaw);
                        long availableAt = normalizeTimestampLong(availableAtRaw);

                        // compute fallback availableAt using availableAfterMinutes if availableAt missing
                        if ((availableAt == 0L || availableAt < 1000L) && availableAfterMinutes != null && availableAfterMinutes > 0 && scheduledAt > 0L) {
                            availableAt = scheduledAt + (availableAfterMinutes * 60_000L);
                        }

                        // if still zero and scheduledAt present, treat scheduledAt as availableAt
                        if (availableAt == 0L && scheduledAt > 0L) {
                            availableAt = scheduledAt;
                        }

                        q.scheduledAt = scheduledAt > 0L ? scheduledAt : null;
                        q.availableAt = availableAt > 0L ? availableAt : null;
                        q.durationMinutes = duration;
                        Boolean active = child.child("active").getValue(Boolean.class);
                        q.active = active != null ? active : false;

                        // default presence = false; presence updates should be persisted separately when received
                        q.present = false;
                        q.cachedAt = now;
                        list.add(q);
                    } catch (Exception ex) {
                        Log.w(TAG, "parse child failed: " + ex.getMessage());
                    }
                }
            } else {
                Log.d(TAG, "saveSnapshot: snapshot null or empty, skipping");
            }

            if (!list.isEmpty()) {
                new Thread(() -> {
                    try {
                        db.quizDao().insertAll(list);
                        int total = 0;
                        try {
                            List<QuizEntity> all = db.quizDao().getAll();
                            total = (all != null) ? all.size() : 0;
                        } catch (Exception qex) {
                            Log.w(TAG, "Could not query cached_quizzes after insert: " + qex.getMessage());
                        }
                        Log.d(TAG, "Saved " + list.size() + " quizzes to cache; total cached rows=" + total);
                        // debug: print first row timestamp human readable
                        if (!list.isEmpty() && list.get(0).availableAt != null) {
                            Log.d(TAG, "Example cached availableAt (ms) = " + list.get(0).availableAt
                                    + " -> " + new Date(list.get(0).availableAt).toString());
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "saveSnapshot db insert failed: " + e.getMessage(), e);
                    }
                }).start();
            } else {
                Log.d(TAG, "saveSnapshot: no quizzes parsed from snapshot");
            }
        } catch (Exception e) {
            Log.w(TAG, "saveSnapshot failed: " + e.getMessage(), e);
        }
    }

    // Replace the existing loadCachedQuizzes(...) method with this updated version.
    public static List<QuizModel> loadCachedQuizzes(Context ctx) {
        List<QuizModel> out = new ArrayList<>();
        try {
            com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(ctx);
            List<QuizEntity> cached = null;
            try {
                cached = db.quizDao().getAll();
            } catch (Exception e) {
                Log.w(TAG, "Error reading cached_quizzes: " + e.getMessage(), e);
            }

            int loaded = (cached != null) ? cached.size() : 0;
            Log.d(TAG, "loadCachedQuizzes: rowsFound=" + loaded);

            if (cached != null) {
                for (QuizEntity qe : cached) {
                    try {
                        QuizModel qm = new QuizModel();
                        qm.setQuizId(qe.quizId);
                        qm.setQuizName(qe.quizName != null ? qe.quizName : "Quiz");
                        qm.setTeacherName(qe.teacherName != null ? qe.teacherName : "");
                        qm.setSubjectName(qe.subjectName != null ? qe.subjectName : "");
                        qm.setCourseName(qe.courseName != null ? qe.courseName : "");
                        qm.setSectionName(qe.sectionName != null ? qe.sectionName : "");

                        // ensure availableAt is normalized to ms
                        long avail = (qe.availableAt != null) ? qe.availableAt : 0L;
                        long sched = (qe.scheduledAt != null) ? qe.scheduledAt : 0L;
                        int dur = (qe.durationMinutes != null) ? qe.durationMinutes : 0;

                        if (avail == 0L && sched > 0L) {
                            avail = sched;
                        }

                        qm.setAvailableAt(avail);
                        qm.setDurationMinutes(dur);
                        qm.setActive(qe.active != null ? qe.active : false);

                        // IMPORTANT: map cached 'present' -> studentPresent (attendance),
                        // and avoid mapping it into QuizModel.present which adapter treats as 'taken'
                        boolean cachedPresent = qe.present != null && qe.present;
                        try { qm.setStudentPresent(cachedPresent); } catch (Exception ignored) {}
                        // Ensure QuizModel.present (meaning "taken" in adapter) is not set by cached attendance:
                        try { qm.setPresent(false); } catch (Exception ignored) {}

                        out.add(qm);
                    } catch (Exception ex) {
                        Log.w(TAG, "map cached row failed: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "loadCachedQuizzes failed: " + e.getMessage(), e);
        }
        return out;
    }
}