package com.finale.nextgen.student;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.text.SimpleDateFormat;

/**
 * QuizListAdapter - updated: separate "attendance/present" vs "taken" methods.
 *
 * Important change:
 * - setStudentPresent(...) now updates only the attendance/studentPresent flag (teacher scanned / optimistic).
 *   It does NOT mark the quiz as TAKEN.
 * - setQuizTaken(...) is a new method that marks the quiz as completed (present=true, available=false, status="TAKEN")
 *   and removes optimistic flags. This should be called when a submission is confirmed (broadcast or DB listener).
 *
 * This prevents the adapter from showing TAKEN immediately when the student is merely marked present.
 */
public class QuizListAdapter extends RecyclerView.Adapter<QuizListAdapter.VH> {

    public interface OnQuizClickListener {
        void onQuizClick(QuizModel quiz);
    }

    private static final String TAG = "QUIZ_ADAPTER";
    private static final SimpleDateFormat SDF = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
    private static final long START_TOLERANCE_MS = 5_000L;

    private final List<QuizModel> list = new ArrayList<>();
    private final OnQuizClickListener listener;

    // track optimistic present state for items that were scanned locally but DB confirmation pending
    private final Set<String> optimisticPresent = new HashSet<>();

    // executor for background checks (Room)
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String highlightQuizId = null;

    public QuizListAdapter(List<QuizModel> list, OnQuizClickListener listener) {
        if (list != null) this.list.addAll(list);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quiz_list, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        if (position < 0) {
            holder.bindEmpty();
            return;
        }

        final QuizModel q;
        synchronized (list) {
            if (position >= list.size()) {
                holder.bindEmpty();
                return;
            }
            q = list.get(position);
        }
        if (q == null) {
            holder.bindEmpty();
            return;
        }

        holder.itemView.setVisibility(View.VISIBLE);
        if (holder.cardRoot != null) holder.cardRoot.setVisibility(View.VISIBLE);

        // ===== TITLE =====
        String title = q.getQuizName() != null ? q.getQuizName() : "Untitled Quiz";
        holder.tvTitle.setText(title);

        // ===== SUBJECT / TEACHER / SECTION =====
        String subject = safeTrim(q.getSubjectName());
        if (!subject.isEmpty()) {
            holder.tvSubject.setVisibility(View.VISIBLE);
            holder.tvSubject.setText(subject);
        } else {
            holder.tvSubject.setVisibility(View.GONE);
        }

        String teacher = safeTrim(q.getTeacherName());
        if (!teacher.isEmpty()) {
            holder.tvTeacher.setVisibility(View.VISIBLE);
            holder.tvTeacher.setText(teacher);
        } else {
            holder.tvTeacher.setVisibility(View.GONE);
        }

        String sectionDisplay = q.getCourseDisplay();
        if (sectionDisplay != null && !sectionDisplay.isEmpty()) {
            holder.tvSection.setVisibility(View.VISIBLE);
            holder.tvSection.setText("Section: " + sectionDisplay);
        } else {
            holder.tvSection.setVisibility(View.GONE);
        }

        // ===== DURATION =====
        int duration = safeInt(q.getDurationMinutes());
        if (duration > 0) {
            holder.tvDuration.setVisibility(View.VISIBLE);
            holder.tvDuration.setText("Duration: " + duration + " mins");
        } else {
            holder.tvDuration.setVisibility(View.GONE);
        }

        // ===== TIME LOGIC =====
        long now = System.currentTimeMillis();
        long scheduledAt = safeLong(q.getScheduledAt());
        long availableAt = safeLong(q.getAvailableAt());
        Long endAtObj = q.getEndAt();
        long endAt = endAtObj != null ? endAtObj : 0;
        if (availableAt <= 0 && scheduledAt > 0) availableAt = scheduledAt;
        if (endAt <= 0 && duration > 0 && availableAt > 0) endAt = availableAt + duration * 60_000L;

        boolean active = safeBool(q.getActive(), true);
        boolean alreadyTaken = safeBool(q.getPresent(), false);

        // teacher marked attendance (student present) - includes optimistic flag
        boolean attendancePresent;
        try {
            attendancePresent = q.getStudentPresent() || optimisticPresent.contains(normalizeKey(q.getQuizId()));
        } catch (Exception e) {
            attendancePresent = optimisticPresent.contains(normalizeKey(q.getQuizId()));
        }

        boolean beforeStart = availableAt > 0 && now < availableAt;
        boolean afterEnd = duration > 0 && now > endAt;

        boolean inWindowStrict = (availableAt <= 0 || now >= availableAt) && (duration <= 0 || now <= endAt);
        boolean inWindowWithTolerance = (availableAt <= 0 || now + START_TOLERANCE_MS >= availableAt) && (duration <= 0 || now <= endAt + START_TOLERANCE_MS);

        // ===== SCHEDULE LABEL =====
        String scheduleText = "";
        if (availableAt > 0) {
            if (now < availableAt) scheduleText = "Available at: " + SDF.format(new Date(availableAt));
            else if (inWindowStrict && duration > 0) {
                long remainingMs = Math.max(0L, endAt - now);
                long mins = TimeUnit.MILLISECONDS.toMinutes(remainingMs);
                long secs = TimeUnit.MILLISECONDS.toSeconds(remainingMs) - TimeUnit.MINUTES.toSeconds(mins);
                scheduleText = "Time left: " + mins + "m " + secs + "s";
            } else if (afterEnd) scheduleText = "Ended: " + SDF.format(new Date(endAt));
        } else if (scheduledAt > 0) {
            scheduleText = "Scheduled: " + SDF.format(new Date(scheduledAt));
        }
        holder.tvSchedule.setText(scheduleText);

        // ===== DEBUG LOG =====
        Log.d(TAG, "QUIZ DEBUG: quizId=" + q.getQuizId()
                + " now=" + now
                + " availableAt=" + availableAt
                + " endAt=" + endAt
                + " active=" + active
                + " taken=" + alreadyTaken
                + " attendancePresent=" + attendancePresent
                + " inWindowStrict=" + inWindowStrict
                + " inWindowWithTolerance=" + inWindowWithTolerance);

        // ===== STATUS / ATTENDANCE =====
        String status = safeTrim(q.getStatus()).toLowerCase();
        boolean isPending = status.contains("pending");
        boolean isOptimistic = optimisticPresent.contains(normalizeKey(q.getQuizId()));

        holder.tvTaken.setVisibility(View.VISIBLE);
        holder.btnTakeQuiz.setVisibility(View.GONE);
        holder.btnTakeQuiz.setEnabled(false);
        holder.itemView.setClickable(true);
        holder.itemView.setOnClickListener(v -> { /* default no-op */ });

        if (afterEnd) {
            holder.tvTaken.setText("EXPIRED");
            holder.tvTaken.setBackgroundColor(ContextCompat.getColor(holder.tvTaken.getContext(), R.color.error));
            holder.tvTaken.setTextColor(Color.WHITE);
            holder.btnTakeQuiz.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v ->
                    showToast(holder, "This quiz has expired.")
            );
        } else if (!attendancePresent) {
            holder.tvTaken.setText("ABSENT");
            holder.tvTaken.setBackgroundColor(ContextCompat.getColor(holder.tvTaken.getContext(), R.color.error));
            holder.tvTaken.setTextColor(Color.WHITE);
            holder.btnTakeQuiz.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v ->
                    showToast(holder, "You are marked ABSENT for this quiz.")
            );
        } else if (isPending) {
            holder.tvTaken.setText("SUBMITTED (Pending)");
            holder.tvTaken.setBackgroundColor(ContextCompat.getColor(holder.tvTaken.getContext(), R.color.md_theme_primary));
            holder.tvTaken.setTextColor(Color.WHITE);
            holder.btnTakeQuiz.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v ->
                    showToast(holder, "You have already submitted this quiz (pending sync).")
            );
        } else if ((active && !alreadyTaken && attendancePresent && inWindowWithTolerance && !afterEnd) || (isOptimistic && !afterEnd)) {
            holder.tvTaken.setVisibility(View.GONE);
            holder.btnTakeQuiz.setVisibility(View.VISIBLE);
            holder.btnTakeQuiz.setEnabled((active && !alreadyTaken && attendancePresent && inWindowStrict) || isOptimistic);
            if (!holder.btnTakeQuiz.isEnabled() && beforeStart) {
                long startsInMs = Math.max(0, availableAt - now);
                long secs = TimeUnit.MILLISECONDS.toSeconds(startsInMs);
                holder.btnTakeQuiz.setText("Starts in " + secs + "s");
            } else {
                holder.btnTakeQuiz.setText("Take Quiz");
            }

            holder.btnTakeQuiz.setOnClickListener(v -> {
                if (!holder.btnTakeQuiz.isEnabled()) return;

                // 1) Fast in-memory guard
                boolean modelTaken = false;
                try {
                    modelTaken = q.getPresent() != null && q.getPresent();
                } catch (Exception ignored) { modelTaken = false; }
                if (modelTaken || "TAKEN".equalsIgnoreCase(q.getStatus())) {
                    showToast(holder, "You have already taken this quiz.");
                    holder.btnTakeQuiz.setEnabled(false);
                    // Ensure UI shows TAKEN state
                    runOnUiThread(() -> setQuizTaken(q.getQuizId()));
                    return;
                }

                // disable button while validating
                holder.btnTakeQuiz.setEnabled(false);

                // 2) Background checks (Room local pending + optional server check)
                bgExecutor.execute(() -> {
                    final String ctxStudentId = com.finale.nextgen.SessionManager.getStudentId(holder.btnTakeQuiz.getContext());
                    if (ctxStudentId == null || ctxStudentId.isEmpty()) {
                        runOnUiThread(() -> {
                            showToast(holder, "Student ID not found.");
                            holder.btnTakeQuiz.setEnabled(true);
                        });
                        return;
                    }

                    // 2a) Local pending submission check (Room)
                    com.finale.nextgen.offline.PendingSubmission pending = null;
                    try {
                        com.finale.nextgen.offline.AppDatabase db = com.finale.nextgen.offline.AppDatabase.getInstance(holder.btnTakeQuiz.getContext().getApplicationContext());
                        pending = db.pendingSubmissionDao().findPendingByExamAndStudent(q.getQuizId(), ctxStudentId);
                    } catch (Exception e) {
                        Log.e(TAG, "Room check failed: " + e.getMessage(), e);
                    }

                    if (pending != null) {
                        // Mark taken locally and inform user
                        runOnUiThread(() -> {
                            showToast(holder, "You already submitted this quiz (pending upload).");
                            setQuizTaken(q.getQuizId());
                            holder.btnTakeQuiz.setEnabled(false);
                        });
                        return;
                    }

                    // 2b) Optional last-moment server check to avoid race (recommended)
                    if (isNetworkAvailable(holder.btnTakeQuiz.getContext())) {
                        DatabaseReference scoreRef = FirebaseDatabase.getInstance()
                                .getReference("QuizScores")
                                .child(ctxStudentId)
                                .child(q.getQuizId());
                        scoreRef.get().addOnCompleteListener(task -> {
                            boolean exists = false;
                            if (task.isSuccessful() && task.getResult() != null) {
                                exists = task.getResult().exists();
                            }

                            if (exists) {
                                // Server already has a score -> mark taken and block start
                                runOnUiThread(() -> {
                                    showToast(holder, "You have already completed this quiz.");
                                    setQuizTaken(q.getQuizId());
                                    holder.btnTakeQuiz.setEnabled(false);
                                });
                                return;
                            }

                            // Not taken on server -> proceed to set ongoing and start quiz
                            runOnUiThread(() -> {
                                DatabaseReference ref = FirebaseDatabase.getInstance()
                                        .getReference("QuizStudents")
                                        .child(q.getQuizId())
                                        .child(ctxStudentId);

                                ref.child("ongoing").setValue(true)
                                        .addOnSuccessListener(aVoid -> {
                                            // clearer startup text
                                            showToast(holder, "Starting quiz...");
                                            if (listener != null) listener.onQuizClick(q);
                                            holder.btnTakeQuiz.setEnabled(true);
                                        })
                                        .addOnFailureListener(e -> {
                                            showToast(holder, "Failed to update ongoing, starting offline.");
                                            if (listener != null) listener.onQuizClick(q);
                                            holder.btnTakeQuiz.setEnabled(true);
                                        });
                            });
                        });
                    } else {
                        // offline: launch immediately
                        runOnUiThread(() -> {
                            if (listener != null) listener.onQuizClick(q);
                            holder.btnTakeQuiz.setEnabled(true);
                        });
                    }
                });
            });

            holder.itemView.setClickable(false);
            holder.itemView.setOnClickListener(null);
        } else if (alreadyTaken) {
            holder.tvTaken.setText("TAKEN");
            holder.tvTaken.setBackgroundColor(ContextCompat.getColor(holder.tvTaken.getContext(), R.color.md_theme_primary));
            holder.tvTaken.setTextColor(Color.WHITE);
            holder.btnTakeQuiz.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v ->
                    showToast(holder, "You have already taken this quiz.")
            );
        } else {
            holder.tvTaken.setText("SCHEDULED");
            holder.tvTaken.setBackgroundColor(ContextCompat.getColor(holder.tvTaken.getContext(), R.color.blue_500));
            holder.tvTaken.setTextColor(Color.WHITE);
            holder.btnTakeQuiz.setVisibility(View.GONE);
            final String scheduleCopy = scheduleText;
            holder.itemView.setOnClickListener(v ->
                    showToast(holder, "Scheduled: " + scheduleCopy)
            );
        }

        // ===== HIGHLIGHT =====
        if (highlightQuizId != null && highlightQuizId.equals(q.getQuizId())) {
            holder.cardRoot.setCardBackgroundColor(Color.parseColor("#FFF9C4"));
            holder.cardRoot.setCardElevation(8f);
        } else {
            holder.cardRoot.setCardBackgroundColor(
                    ContextCompat.getColor(holder.cardRoot.getContext(), android.R.color.white)
            );
            holder.cardRoot.setCardElevation(2f);
        }
    }

    private void showToast(VH holder, String msg) {
        if (holder.itemView != null) {
            holder.itemView.post(() -> android.widget.Toast.makeText(holder.itemView.getContext(), msg, android.widget.Toast.LENGTH_LONG).show());
        }
    }

    private void runOnUiThread(Runnable r) {
        mainHandler.post(r);
    }

    private boolean isNetworkAvailable(android.content.Context context) {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    private String safeTrim(String s) { return s == null ? "" : s.trim(); }
    private long safeLong(Long v) { return v == null ? 0 : v; }
    private int safeInt(Integer v) { return v == null ? 0 : v; }
    private boolean safeBool(Boolean v, boolean def) { return v == null ? def : v; }

    public int getPositionForQuizId(@Nullable String quizId) {
        if (quizId == null) return -1;
        synchronized (list) {
            String key = normalizeKey(quizId);
            for (int i = 0; i < list.size(); i++) {
                QuizModel qm = list.get(i);
                if (qm != null && key.equals(normalizeKey(qm.getQuizId()))) return i;
            }
        }
        return -1;
    }

    private String normalizeKey(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    /**
     * Attendance-only update: marks studentPresent (teacher marked / scanned).
     * This does NOT mark the quiz as TAKEN.
     * Use setQuizTaken(...) to mark completion.
     */
    public void setStudentPresent(@NonNull String quizId, boolean present) {
        if (quizId == null) return;
        String key = normalizeKey(quizId);

        int pos = getPositionForQuizId(quizId);
        if (pos >= 0) {
            // Update existing item
            synchronized (list) {
                QuizModel qm = list.get(pos);
                if (qm != null) {
                    qm.setStudentPresent(present); // Only attendance, not quiz-taken status

                    if (present) {
                        optimisticPresent.add(key);
                    } else {
                        optimisticPresent.remove(key);
                    }
                }
            }
            try {
                notifyItemChanged(pos);
            } catch (Exception e) {
                notifyDataSetChanged();
            }
        } else {
            // DO NOT CREATE PLACEHOLDERS ANYMORE
            if (present) {
                optimisticPresent.add(key); // Keep optimistic attendance only
            } else {
                optimisticPresent.remove(key);
            }

            Log.d(TAG, "setStudentPresent: quiz not in list (ignored): " + quizId);
        }
    }


    /**
     * Mark the quiz as completed/taken. This sets present=true (taken), available=false, status="TAKEN".
     * Call this when submission is confirmed (DB child or QUIZ_SUBMITTED broadcast).
     */
    public void setQuizTaken(@NonNull String quizId) {
        if (quizId == null) return;
        String key = normalizeKey(quizId);
        int pos = getPositionForQuizId(quizId);
        if (pos >= 0) {
            synchronized (list) {
                QuizModel qm = list.get(pos);
                if (qm != null) {
                    qm.setStudentPresent(true);
                    try { qm.setPresent(true); } catch (Exception ignored) {}
                    try { qm.setAvailable(false); } catch (Exception ignored) {}
                    try { qm.setStatus("TAKEN"); } catch (Exception ignored) {}
                    synchronized (optimisticPresent) { optimisticPresent.remove(key); }
                }
            }
            try { notifyItemChanged(pos); } catch (Exception e) { notifyDataSetChanged(); }
        } else {
            // insert a TAKEN placeholder
            QuizModel placeholder = new QuizModel();
            placeholder.setQuizId(quizId);
            placeholder.setQuizName("Quiz");
            placeholder.setStatus("TAKEN");
            placeholder.setAvailable(false);
            placeholder.setActive(false);
            placeholder.setStudentPresent(true);
            try { placeholder.setPresent(true); } catch (Exception ignored) {}

            synchronized (list) {
                list.add(0, placeholder);
                optimisticPresent.remove(key);
            }
            try { notifyItemInserted(0); } catch (Exception e) { notifyDataSetChanged(); }
        }
    }

    /**
     * Mark optimistic present only (keeps separate semantics)
     * If item not present, insert placeholder so button appears immediately.
     * This should not mark the quiz as 'taken' (present==true) — only attendance for showing Take button.
     */
    public void markOptimisticPresent(@NonNull String quizId) {
        if (quizId == null) return;
        String key = normalizeKey(quizId);

        // Keep optimistic flag
        synchronized (optimisticPresent) { optimisticPresent.add(key); }

        int pos = getPositionForQuizId(quizId);
        if (pos >= 0) {
            synchronized (list) {
                QuizModel qm = list.get(pos);
                if (qm != null) {
                    // Do NOT set qm.setPresent(true) here. We want optimistic attendance only.
                    qm.setStudentPresent(true);
                    qm.setActive(true);          // ensure button shows
                }
            }
            try { notifyItemChanged(pos); } catch (Exception e) { notifyDataSetChanged(); }
        } else {
            QuizModel placeholder = new QuizModel();
            placeholder.setQuizId(quizId);
            placeholder.setQuizName("Quiz");
            placeholder.setStatus("QUIZ");
            placeholder.setAvailable(true);
            placeholder.setActive(true);          // ensure button shows
            placeholder.setStudentPresent(true);  // force attendance so button shows
            // leave present=false so it is NOT considered 'TAKEN' until DB confirms

            synchronized (list) {
                list.add(0, placeholder);
            }
            try { notifyItemInserted(0); } catch (Exception e) { notifyDataSetChanged(); }
        }
    }

    /**
     * Replace or add a quiz model and notify adapter appropriately.
     */
    public void updateOrAddQuiz(@NonNull QuizModel quiz) {
        String id = quiz.getQuizId();
        if (id == null) return;
        int pos = getPositionForQuizId(id);
        synchronized (list) {
            if (pos >= 0) {
                boolean opt = optimisticPresent.contains(normalizeKey(id));
                list.set(pos, quiz);
                if (opt) quiz.setStudentPresent(true);
                if (quiz.getPresent() != null && quiz.getPresent()) {
                    optimisticPresent.remove(normalizeKey(id));
                    quiz.setStudentPresent(true);
                }
                try { notifyItemChanged(pos); } catch (Exception e) { notifyDataSetChanged(); }
            } else {
                boolean opt = optimisticPresent.contains(normalizeKey(id));
                if (opt) quiz.setStudentPresent(true);
                list.add(0, quiz);
                try { notifyItemInserted(0); } catch (Exception e) { notifyDataSetChanged(); }
            }
        }
    }

    public void removeQuizById(@Nullable String quizId) {
        if (quizId == null) return;
        int pos = getPositionForQuizId(quizId);
        if (pos >= 0) {
            synchronized (list) {
                list.remove(pos);
            }
            try { notifyItemRemoved(pos); } catch (Exception e) { notifyDataSetChanged(); }
        }
    }

    @Override
    public int getItemCount() { return list.size(); }

    public void updateData(List<QuizModel> newList) {
        synchronized (list) {
            Set<String> incomingKeys = new HashSet<>();
            if (newList != null) {
                for (QuizModel qm : newList) {
                    if (qm != null && qm.getQuizId() != null) incomingKeys.add(normalizeKey(qm.getQuizId()));
                }
            }

            List<QuizModel> placeholdersToKeep = new ArrayList<>();
            for (QuizModel old : list) {
                if (old == null) continue;
                String k = normalizeKey(old.getQuizId());
                if (optimisticPresent.contains(k) && !incomingKeys.contains(k)) {
                    placeholdersToKeep.add(old);
                }
            }

            list.clear();
            if (!placeholdersToKeep.isEmpty()) {
                for (int i = placeholdersToKeep.size() - 1; i >= 0; i--) {
                    list.add(placeholdersToKeep.get(i));
                }
            }
            if (newList != null) list.addAll(newList);

            for (QuizModel qm : list) {
                if (qm == null) continue;
                String k = normalizeKey(qm.getQuizId());
                if (optimisticPresent.contains(k)) {
                    qm.setStudentPresent(true);
                    qm.setActive(true);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setHighlightQuizId(@Nullable String quizId) {
        this.highlightQuizId = quizId;
        notifyDataSetChanged();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        try {
            bgExecutor.shutdownNow();
        } catch (Exception ignored) {}
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSubject, tvSection, tvTeacher, tvSchedule, tvDuration, tvTaken;
        CardView cardRoot;
        Button btnTakeQuiz;

        VH(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardQuizRoot);
            tvTitle = itemView.findViewById(R.id.tvQuizTitle);
            tvSection = itemView.findViewById(R.id.tvQuizSection);
            tvSubject = itemView.findViewById(R.id.tvQuizSubject);
            tvTeacher = itemView.findViewById(R.id.tvQuizTeacher);
            tvSchedule = itemView.findViewById(R.id.tvQuizSchedule);
            tvDuration = itemView.findViewById(R.id.tvQuizDuration);
            btnTakeQuiz = itemView.findViewById(R.id.btnTakeQuiz);
            tvTaken = itemView.findViewById(R.id.tvQuizTaken);
        }

        void bindEmpty() {
            cardRoot.setVisibility(View.GONE);
            tvTitle.setText("");
            tvSection.setText("");
            tvSubject.setText("");
            tvTeacher.setText("");
            tvSchedule.setText("");
            tvDuration.setText("");
            btnTakeQuiz.setVisibility(View.GONE);
            tvTaken.setVisibility(View.GONE);
        }
    }
}