package com.finale.nextgen.teacher;


import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;


import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;


import com.finale.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * AttendanceAdapter - improved robustness for loading students.
 *
 * Changes:
 * - Uses SectionItem getters (safer).
 * - Calls OnLoadListener.onLoadStarted/onLoadFinished/onLoadFailed reliably.
 * - Performs a case-insensitive fallback full-scan if the indexed query returns nothing (helps with case/format mismatches).
 * - Keeps the existing UI behavior and attendance-change callback.
 *
 * Updated behavior:
 * - Clicking a status button triggers the OnAttendanceChangedListener so the confirmation flow runs when the teacher
 *   explicitly selects a status.
 * - Removed pre-selection: students without an attendanceStatus are left blank ("" / null) so no button is selected by default.
 *   Teachers must actively choose a status to trigger confirmation/save.
 */
public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.ViewHolder> {


    private final ArrayList<StudentModel> students;
    private OnAttendanceChangedListener listener;
    private final Context context;


    public AttendanceAdapter(@NonNull Context context, ArrayList<StudentModel> students) {
        this.context = context;
        this.students = students != null ? students : new ArrayList<>();
        setHasStableIds(true);
    }


    // Public API
    public void setOnAttendanceChangedListener(OnAttendanceChangedListener l) {
        this.listener = l;
    }


    /**
     * Load students for the given section from the provided studentsRef (Firebase DatabaseReference).
     * The adapter will query orderByChild("sectionName").equalTo(section.getSectionName()) and then filter
     * by courseName, specializationName, yearName and sectionName to ensure close matches.
     *
     * The OnLoadListener callbacks are optional (can pass null).
     */
    public void loadStudentsForSection(SectionItem section, DatabaseReference studentsRef, OnLoadListener loadListener) {
        if (loadListener != null) loadListener.onLoadStarted();


        if (section == null || studentsRef == null) {
            setStudents(new ArrayList<>());
            if (loadListener != null) loadListener.onLoadFinished(0);
            return;
        }


        final String secName = section.getSectionName();
        final String courseName = section.getCourseName();
        final String specName = section.getSpecializationName();
        final String yearName = section.getYearName();


        // Primary indexed query by sectionName (fast)
        studentsRef.orderByChild("sectionName").equalTo(secName)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<StudentModel> found = new ArrayList<>();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            StudentModel student = ds.getValue(StudentModel.class);
                            if (student == null) continue;


                            if (matchesSection(section, student)) {
                                safeSetDbKey(student, ds.getKey());
                                ensureDefaultStatus(student);
                                found.add(student);
                            }
                        }


                        if (!found.isEmpty()) {
                            setStudents(found);
                            if (loadListener != null) loadListener.onLoadFinished(found.size());
                        } else {
                            // Fallback full-scan (case-insensitive) to catch data where sectionName case/format differs
                            studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot fullSnapshot) {
                                    List<StudentModel> fallback = new ArrayList<>();
                                    for (DataSnapshot ds2 : fullSnapshot.getChildren()) {
                                        StudentModel st = ds2.getValue(StudentModel.class);
                                        if (st == null) continue;
                                        if (matchesSectionCaseInsensitive(section, st)) {
                                            safeSetDbKey(st, ds2.getKey());
                                            ensureDefaultStatus(st);
                                            fallback.add(st);
                                        }
                                    }
                                    setStudents(fallback);
                                    if (loadListener != null) loadListener.onLoadFinished(fallback.size());
                                }


                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    if (loadListener != null) loadListener.onLoadFailed(error.getMessage());
                                }
                            });
                        }
                    }


                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (loadListener != null) loadListener.onLoadFailed(error.getMessage());
                    }
                });
    }


    private void safeSetDbKey(StudentModel student, String key) {
        try { student.setId(key); } catch (Exception ignore) {}
    }


    /**
     * Previously this method defaulted to "Present" which pre-selected that button.
     * To require the teacher to actively choose a status, we leave the attendanceStatus empty
     * when it's not already set.
     */
    private void ensureDefaultStatus(StudentModel s) {
        if (s.getAttendanceStatus() == null || s.getAttendanceStatus().trim().isEmpty()) {
            // leave empty so no pre-selection; teacher must explicitly select a status
            s.setAttendanceStatus("");
        }
    }


    // Exact match using adapter of SectionItem fields (case-sensitive)
    private boolean matchesSection(SectionItem sec, StudentModel s) {
        if (s == null || sec == null) return false;
        if (!equalsSafe(sec.getSectionName(), s.getSectionName())) return false;
        if (sec.getCourseName() != null && !sec.getCourseName().trim().isEmpty()
                && !equalsSafe(sec.getCourseName(), s.getCourseName())) return false;
        if (sec.getSpecializationName() != null && !sec.getSpecializationName().trim().isEmpty()
                && !equalsSafe(sec.getSpecializationName(), s.getSpecializationName())) return false;
        if (sec.getYearName() != null && !sec.getYearName().trim().isEmpty()
                && !equalsSafe(sec.getYearName(), s.getYearName())) return false;
        return true;
    }


    // Case-insensitive, trimmed fallback matching
    private boolean matchesSectionCaseInsensitive(SectionItem sec, StudentModel s) {
        if (s == null || sec == null) return false;
        if (!equalsIgnoreCaseTrim(sec.getSectionName(), s.getSectionName())) return false;
        if (sec.getCourseName() != null && !sec.getCourseName().trim().isEmpty()
                && !equalsIgnoreCaseTrim(sec.getCourseName(), s.getCourseName())) return false;
        if (sec.getSpecializationName() != null && !sec.getSpecializationName().trim().isEmpty()
                && !equalsIgnoreCaseTrim(sec.getSpecializationName(), s.getSpecializationName())) return false;
        if (sec.getYearName() != null && !sec.getYearName().trim().isEmpty()
                && !equalsIgnoreCaseTrim(sec.getYearName(), s.getYearName())) return false;
        return true;
    }


    private boolean equalsIgnoreCaseTrim(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }


    public void setStudents(List<StudentModel> newStudents) {
        students.clear();
        if (newStudents != null) students.addAll(newStudents);
        notifyDataSetChanged();
    }


    public Map<String, String> getAttendanceMap() {
        Map<String, String> map = new HashMap<>();
        for (StudentModel s : students) {
            String sid = s.getStudentId();
            String status = s.getAttendanceStatus();
            if (sid != null && status != null && !status.trim().isEmpty()) {
                map.put(sid, status);
            }
        }
        return map;
    }


    @Override
    public long getItemId(int position) {
        StudentModel s = students.get(position);
        if (s != null && s.getStudentId() != null) {
            return s.getStudentId().hashCode();
        }
        return position;
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_attendance, parent, false);
        return new ViewHolder(view);
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudentModel student = students.get(position);
        holder.bind(student, position);
    }


    @Override
    public int getItemCount() {
        return students.size();
    }


    public interface OnAttendanceChangedListener {
        void onAttendanceChanged(StudentModel student, int position, String previousStatus);
    }


    public interface OnLoadListener {
        void onLoadStarted();
        void onLoadFinished(int count);
        void onLoadFailed(String errorMessage);
    }


    class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameTxt, studentIdTxt, sectionTxt;
        Button presentBtn, absentBtn, excusedBtn, lateBtn;


        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTxt = itemView.findViewById(R.id.studentNameTxt);
            studentIdTxt = itemView.findViewById(R.id.studentIdTxt);
            sectionTxt = itemView.findViewById(R.id.sectionTxt);


            presentBtn = itemView.findViewById(R.id.presentBtn);
            absentBtn = itemView.findViewById(R.id.absentBtn);
            excusedBtn = itemView.findViewById(R.id.excusedBtn);
            lateBtn = itemView.findViewById(R.id.lateBtn);
        }


        void bind(StudentModel student, int position) {
            if (student == null) return;


            nameTxt.setText(student.getFullName() != null ? student.getFullName() : "No name");
            studentIdTxt.setText(student.getStudentId() != null ? student.getStudentId() : "");
            sectionTxt.setText(
                    safeString(student.getCourseName()) + " - " +
                            safeString(student.getSpecializationName()) + " - " +
                            safeString(student.getYearName()) + " - " +
                            safeString(student.getSectionName())
            );


            applyButtonStates(student.getAttendanceStatus());


            presentBtn.setOnClickListener(v -> updateStatusForPosition("Present", position));
            absentBtn.setOnClickListener(v -> updateStatusForPosition("Absent", position));
            excusedBtn.setOnClickListener(v -> updateStatusForPosition("Excused", position));
            lateBtn.setOnClickListener(v -> updateStatusForPosition("Late", position));
        }


        private String safeString(String s) {
            return s == null ? "N/A" : s;
        }


        private void updateStatusForPosition(String newStatus, int position) {
            if (position < 0 || position >= students.size()) return;
            StudentModel s = students.get(position);
            if (s == null) return;


            String previous = s.getAttendanceStatus();
            // Always proceed to set and notify listener so clicks on a status button
            // will trigger confirmation even if previous was empty or different.
            s.setAttendanceStatus(newStatus);
            notifyItemChanged(position);


            if (listener != null) listener.onAttendanceChanged(s, position, previous);
        }


        private void applyButtonStates(String status) {
            int colorPresent, colorAbsent, colorExcused, colorLate;
            int colorPresentSel, colorAbsentSel, colorExcusedSel, colorLateSel;


            try {
                colorPresent = ContextCompat.getColor(context, R.color.att_present_light);
                colorAbsent = ContextCompat.getColor(context, R.color.att_absent_light);
                colorExcused = ContextCompat.getColor(context, R.color.att_excused_light);
                colorLate = ContextCompat.getColor(context, R.color.att_late_light);


                colorPresentSel = ContextCompat.getColor(context, R.color.att_present);
                colorAbsentSel = ContextCompat.getColor(context, R.color.att_absent);
                colorExcusedSel = ContextCompat.getColor(context, R.color.att_excused);
                colorLateSel = ContextCompat.getColor(context, R.color.att_late);
            } catch (Exception e) {
                colorPresent = Color.parseColor("#A5D6A7");
                colorAbsent = Color.parseColor("#FFCDD2");
                colorExcused = Color.parseColor("#BBDEFB");
                colorLate = Color.parseColor("#FFE0B2");


                colorPresentSel = Color.parseColor("#388E3C");
                colorAbsentSel = Color.parseColor("#D32F2F");
                colorExcusedSel = Color.parseColor("#1976D2");
                colorLateSel = Color.parseColor("#F57C00");
            }


            presentBtn.setBackgroundColor(colorPresent);
            absentBtn.setBackgroundColor(colorAbsent);
            excusedBtn.setBackgroundColor(colorExcused);
            lateBtn.setBackgroundColor(colorLate);


            presentBtn.setTextColor(Color.WHITE);
            absentBtn.setTextColor(Color.WHITE);
            excusedBtn.setTextColor(Color.WHITE);
            lateBtn.setTextColor(Color.WHITE);


            if (status == null || status.isEmpty()) return;
            switch (status) {
                case "Present":
                    presentBtn.setBackgroundColor(colorPresentSel);
                    break;
                case "Absent":
                    absentBtn.setBackgroundColor(colorAbsentSel);
                    break;
                case "Excused":
                    excusedBtn.setBackgroundColor(colorExcusedSel);
                    break;
                case "Late":
                    lateBtn.setBackgroundColor(colorLateSel);
                    break;
            }
        }
    }


    // small helper
    private boolean equalsSafe(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}

