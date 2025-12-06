package com.example.nextgen.student;

import android.graphics.Color;
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

import com.example.nextgen.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * QuizListAdapter - cleaned and fixed
 *
 * - Removed duplicated/misordered blocks and rebuilt onBindViewHolder to be single, coherent flow.
 * - Keeps optimistic placeholder/merge behavior: markOptimisticPresent, setStudentPresent, updateData.
 * - Ensures optimistic entries force the "Take Quiz" button to appear immediately.
 * - Uses normalizeKey(...) consistently to avoid case/prefix mismatches.
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

        // ===== OPTIMISTIC FLAG =====
        boolean isOptimistic = optimisticPresent.contains(normalizeKey(q.getQuizId()));

        // ===== BUTTON LOGIC (NO openNow) =====
        boolean showButton = (active && !alreadyTaken && attendancePresent && inWindowWithTolerance) || isOptimistic;
        boolean enableButton = (active && !alreadyTaken && attendancePresent && inWindowStrict) || isOptimistic;

        if (showButton) {
            holder.btnTakeQuiz.setVisibility(View.VISIBLE);
            holder.btnTakeQuiz.setEnabled(enableButton);

            if (!enableButton && beforeStart) {
                long startsInMs = Math.max(0, availableAt - now);
                long secs = TimeUnit.MILLISECONDS.toSeconds(startsInMs);
                holder.btnTakeQuiz.setText("Starts in " + secs + "s");
            } else {
                holder.btnTakeQuiz.setText("Take Quiz");
            }

            holder.tvTaken.setVisibility(View.GONE);

        } else {
            holder.btnTakeQuiz.setVisibility(View.GONE);
            holder.tvTaken.setVisibility(View.VISIBLE);

            if (alreadyTaken && !isPending) {
                holder.tvTaken.setText("Taken");
            } else if (alreadyTaken && isPending) {
                holder.tvTaken.setText("Taken (Pending)");
            } else if (!attendancePresent) {
                holder.tvTaken.setText("Waiting for teacher to mark you present");
            } else if (attendancePresent && !inWindowWithTolerance) {
                if (beforeStart) holder.tvTaken.setText("Available at: " + SDF.format(new Date(availableAt)));
                else if (afterEnd) holder.tvTaken.setText("No longer available");
                else holder.tvTaken.setVisibility(View.GONE);
            } else {
                holder.tvTaken.setVisibility(View.GONE);
            }
        }

        // ===== ON CLICK =====
        holder.btnTakeQuiz.setOnClickListener(v -> {
            if (!enableButton) return;
            if (listener != null) listener.onQuizClick(q);
        });

        boolean finalAttendancePresent = attendancePresent;
        holder.cardRoot.setOnClickListener(v -> {
            if ((active && !alreadyTaken && finalAttendancePresent && inWindowStrict) || isOptimistic) {
                if (listener != null) listener.onQuizClick(q);
            }
        });

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

    public void setStudentPresent(@NonNull String quizId, boolean present) {
        if (quizId == null) return;
        String key = normalizeKey(quizId);
        int pos = getPositionForQuizId(quizId);
        if (pos >= 0) {
            synchronized (list) {
                QuizModel qm = list.get(pos);
                if (qm != null) qm.setStudentPresent(present);
                if (present) optimisticPresent.add(key); else optimisticPresent.remove(key);
            }
            try { notifyItemChanged(pos); } catch (Exception e) { notifyDataSetChanged(); }
        } else {
            if (present) {
                // add placeholder at top so UI shows immediately
                QuizModel placeholder = new QuizModel();
                placeholder.setQuizId(quizId);
                placeholder.setQuizName("Quiz");
                placeholder.setStatus("QUIZ");
                placeholder.setAvailable(true);
                placeholder.setActive(true);
                placeholder.setStudentPresent(true);
                synchronized (list) {
                    list.add(0, placeholder);
                    optimisticPresent.add(key);
                }
                try { notifyItemInserted(0); } catch (Exception e) { notifyDataSetChanged(); }
            } else {
                synchronized (optimisticPresent) { optimisticPresent.remove(key); }
                Log.d(TAG, "setStudentPresent: quizId not in list and present=false (no-op): " + quizId);
            }
        }
    }

    /**
     * Mark optimistic present only (keeps separate semantics)
     * If item not present, insert placeholder so button appears immediately.
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
            placeholder.setStudentPresent(true);  // force present

            synchronized (list) {
                list.add(0, placeholder);
            }
            try { notifyItemInserted(0); } catch (Exception e) { notifyDataSetChanged(); }
        }
    }

    /**
     * Replace or add a quiz model and notify adapter appropriately.
     * Useful if you want to update metadata (quizName, schedule) without rebuilding the entire list.
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
                try { notifyItemChanged(pos); } catch (Exception e) { notifyDataSetChanged(); }
            } else {
                boolean opt = optimisticPresent.contains(normalizeKey(id));
                if (opt) quiz.setStudentPresent(true);
                list.add(0, quiz);
                try { notifyItemInserted(0); } catch (Exception e) { notifyDataSetChanged(); }
            }
        }
    }

    /**
     * Remove a quiz by id (if needed).
     */
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

    /**
     * Merge-aware update:
     * - Keep optimistic placeholders (entries whose ids are in optimisticPresent) if they are not in the fresh list.
     * - For incoming items that match optimistic keys, ensure studentPresent=true is applied.
     */
    public void updateData(List<QuizModel> newList) {
        synchronized (list) {
            // Build a normalized set of incoming IDs
            Set<String> incomingKeys = new HashSet<>();
            if (newList != null) {
                for (QuizModel qm : newList) {
                    if (qm != null && qm.getQuizId() != null) incomingKeys.add(normalizeKey(qm.getQuizId()));
                }
            }

            // Collect placeholders from existing list that are optimistic and not present in newList
            List<QuizModel> placeholdersToKeep = new ArrayList<>();
            for (QuizModel old : list) {
                if (old == null) continue;
                String k = normalizeKey(old.getQuizId());
                if (optimisticPresent.contains(k) && !incomingKeys.contains(k)) {
                    placeholdersToKeep.add(old);
                }
            }

            // Replace list with newList, but prepend placeholders
            list.clear();
            if (!placeholdersToKeep.isEmpty()) {
                // keep the most recent optimistic first
                for (int i = placeholdersToKeep.size() - 1; i >= 0; i--) {
                    list.add(placeholdersToKeep.get(i));
                }
            }
            if (newList != null) list.addAll(newList);

            // ALWAYS REAPPLY optimistic presence no matter what DB sends
            for (QuizModel qm : list) {
                if (qm == null) continue;
                String k = normalizeKey(qm.getQuizId());
                if (optimisticPresent.contains(k)) {
                    qm.setStudentPresent(true);
                    qm.setActive(true); // make sure take quiz shows immediately
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setHighlightQuizId(@Nullable String quizId) {
        this.highlightQuizId = quizId;
        notifyDataSetChanged();
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