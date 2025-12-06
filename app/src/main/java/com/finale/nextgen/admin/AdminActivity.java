package com.finale.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.MainActivity;
import com.finale.nextgen.R;
import com.finale.nextgen.SessionManager;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.widget.PopupMenu;
import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;


public class AdminActivity extends AppCompatActivity{

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private TextView countExaminee, countTc, countCurriculum;

    private DatabaseReference coursesRef, studentsRef, teachersRef;

    private RecyclerView rvStudents, rvTc;
    private SingleRecentAdapter studentsAdapter;
    private SingleRecentAdapter tcAdapter;

    private ValueEventListener studentsValueListener;
    private ValueEventListener tcValueListener;

    private RecentAccount latestStudent;
    private RecentAccount latestTeacher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        initializeFirebaseReferences();
        initUi();
        loadDashboardCounts();
        attachRealtimeRecentListeners();

        // PROFILE MENU SETUP (FIXED)
        ImageView ivProfile = findViewById(R.id.ivProfile);
        if (ivProfile != null) {
            ivProfile.setOnClickListener(view -> showProfileMenu(view));
        }

        // QUICK ACTIONS LISTENERS
        findViewById(R.id.cardCourses).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(AdminActivity.this, CourseActivity.class));
            }
        });

        // CARD: Subjects
        findViewById(R.id.cardSubjects).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(AdminActivity.this, SubjectActivity.class));
            }
        });

        // CARD: Specialization
        findViewById(R.id.cardSpecialization).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(AdminActivity.this, SpecializationsActivity.class));
            }
        });

        // CARD: Year
        findViewById(R.id.cardYear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(AdminActivity.this, YearsActivity.class));
            }
        });

        // CARD: Section
        findViewById(R.id.cardSection).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(AdminActivity.this, SectionsActivity.class));
            }
        });

        // CARD: Manage Teachers
        findViewById(R.id.cardManageTeachers).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(AdminActivity.this, TeacherActivity.class));
            }
        });

        // CARD: Manage Students
        findViewById(R.id.cardManageStudents).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(AdminActivity.this, StudentActivity.class));
            }
        });
    }

    // --- REMOVED: onCreateOptionsMenu and onOptionsItemSelected ---

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> logoutAdmin())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showProfileMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);

        // CORRECT: Use the menu file that contains the "Logout" item.
        popupMenu.getMenuInflater().inflate(R.menu.admin_popup_logout, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            // CORRECT: Check for the R.id.action_logout item.
            if (id == R.id.action_logout) {
                showLogoutConfirmation();
                return true;
            }

            return false;
        });

        popupMenu.show();
    }


    private void logoutAdmin() {
        SessionManager session = new SessionManager(this);
        session.clearSession();


        Intent intent = new Intent(AdminActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }



    private void initializeFirebaseReferences() {
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
    }



    private void initUi() {
        countExaminee = findViewById(R.id.totalExaminees);
        countTc = findViewById(R.id.totalTeachers);
        countCurriculum = findViewById(R.id.totalCourses);

        setCountText(countExaminee, 0);
        setCountText(countTc, 0);
        setCountText(countCurriculum, 0);

        View cardCurr = findViewById(R.id.card_totalCourses);
        View cardExm = findViewById(R.id.card_totalExaminees);
        View cardTc = findViewById(R.id.card_totalTeachers);

        // 🛑 REMOVING CLICK FUNCTIONALITY FROM DASHBOARD COUNTER CARDS
        if (cardCurr != null) {
            cardCurr.setClickable(false);
            cardCurr.setOnClickListener(null);
        }
        if (cardExm != null) {
            cardExm.setClickable(false);
            cardExm.setOnClickListener(null);
        }
        if (cardTc != null) {
            cardTc.setClickable(false);
            cardTc.setOnClickListener(null);
        }
        // ---------------------------------------------

        rvStudents = findViewById(R.id.rv_recent_students);
        rvTc = findViewById(R.id.rv_recent_tc);

        studentsAdapter = new SingleRecentAdapter(acc ->
                Toast.makeText(this, "Open " + acc.name, Toast.LENGTH_SHORT).show());

        tcAdapter = new SingleRecentAdapter(acc ->
                Toast.makeText(this, "Open " + acc.name, Toast.LENGTH_SHORT).show());

        if (rvStudents != null) {
            rvStudents.setLayoutManager(new LinearLayoutManager(this));
            rvStudents.setAdapter(studentsAdapter);
            rvStudents.setNestedScrollingEnabled(false);
        }

        if (rvTc != null) {
            rvTc.setLayoutManager(new LinearLayoutManager(this));
            rvTc.setAdapter(tcAdapter);
            rvTc.setNestedScrollingEnabled(false);
        }
    }

    private void attachRealtimeRecentListeners() {
        studentsValueListener = studentsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot latest = findHighestIdChild(snapshot, "STD");
                if (latest != null) {
                    RecentAccount acc = mapSnapshot(latest, "Student");
                    acc.id = latest.getKey();
                    latestStudent = acc;
                } else {
                    latestStudent = null;
                }
                updateCombinedRecent();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AdminActivity.this, "Failed to load students", Toast.LENGTH_SHORT).show();
            }
        });

        tcValueListener = teachersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot latest = findHighestIdChild(snapshot, "TCHR");
                if (latest != null) {
                    RecentAccount acc = mapSnapshot(latest, "TC");
                    acc.id = latest.getKey();
                    latestTeacher = acc;
                } else {
                    latestTeacher = null;
                }
                updateCombinedRecent();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AdminActivity.this, "Failed to load teachers", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Shows the latest student in the student adapter and the latest teacher in the TC adapter.
     */
    private void updateCombinedRecent() {
        if (studentsAdapter != null) {
            studentsAdapter.setSingle(latestStudent);
        }

        if (tcAdapter != null) {
            tcAdapter.setSingle(latestTeacher);
        }
    }

    private long extractIdNumber(String id) {
        if (id == null) return 0;
        Pattern p = Pattern.compile("^(STD|TCHR)-?(\\d+)$", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(id.trim());
        if (m.matches()) {
            try {
                return Long.parseLong(m.group(2));
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private DataSnapshot findHighestIdChild(DataSnapshot parentSnapshot, String prefix) {
        if (parentSnapshot == null || !parentSnapshot.exists()) return null;

        Pattern p = Pattern.compile("^" + Pattern.quote(prefix) + "-?(\\d+)$", Pattern.CASE_INSENSITIVE);
        long max = Long.MIN_VALUE;
        DataSnapshot best = null;

        for (DataSnapshot child : parentSnapshot.getChildren()) {
            String key = child.getKey();
            if (key == null) continue;

            Matcher m = p.matcher(key.trim());
            if (m.matches()) {
                try {
                    long num = Long.parseLong(m.group(1));
                    if (num > max) {
                        max = num;
                        best = child;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (best == null) {
            for (DataSnapshot child : parentSnapshot.getChildren()) {
                if (best == null) best = child;
                else if (child.getKey() != null && child.getKey().compareTo(best.getKey()) > 0)
                    best = child;
            }
        }

        return best;
    }

    /**
     * Maps the DataSnapshot fields to a RecentAccount object, including detailed teacher lists.
     */
    private RecentAccount mapSnapshot(DataSnapshot s, String defaultRole) {
        RecentAccount a = new RecentAccount();

        a.id = s.getKey();
        a.email = s.child("email").getValue(String.class);
        a.role = defaultRole;

        // --- 1. HANDLE NAME (Full Name or F. Last Name) ---
        String fullName = s.child("fullName").getValue(String.class);
        if ("TC".equals(defaultRole) && !TextUtils.isEmpty(fullName)) {
            // Implement "First Name Initial + Last Name" logic for Teachers
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length >= 2) {
                String firstNameInitial = parts[0].substring(0, 1).toUpperCase(Locale.getDefault()) + ".";
                String lastName = parts[parts.length - 1];
                a.name = firstNameInitial + " " + lastName;
            } else {
                a.name = fullName; // Fallback to full name if only one part
            }
        } else {
            a.name = fullName;
        }

        // CRITICAL FIX: If name is still null or empty, use the unique ID (key)
        if (TextUtils.isEmpty(a.name)) {
            a.name = s.getKey();
        }

        // --- 2. LOGIC SPLIT BASED ON ROLE ---
        // (This logic is kept as is to correctly populate the RecentAccount object from Firebase,
        // but the display logic in the Adapter will ignore 'program' and 'status'.)

        if ("Student".equals(defaultRole)) {
            // 🔹 STUDENT-SPECIFIC FIELDS
            a.program = s.child("courseName").getValue(String.class);
            a.status = s.child("sectionName").getValue(String.class);
        } else if ("TC".equals(defaultRole)) {

            // 🔹 TEACHER-SPECIFIC FIELD: BIRTHDAY
            String birthday = s.child("birthday").getValue(String.class);
            a.status = birthday != null ? "Bday: " + birthday : "Bday: N/A"; // Use status for birthday

            // 🔹 TEACHER-SPECIFIC FIELDS: Subjects (The required list)
            DataSnapshot subjectsSnapshot = s.child("assignedSubjects");
            if (subjectsSnapshot.exists() && subjectsSnapshot.hasChildren()) {
                List<String> subjectList = new ArrayList<>();
                for (DataSnapshot subjectChild : subjectsSnapshot.getChildren()) {
                    String subjectName = subjectChild.getValue(String.class);
                    if (TextUtils.isEmpty(subjectName)) {
                        subjectName = subjectChild.getKey();
                    }
                    if (!TextUtils.isEmpty(subjectName)) {
                        subjectList.add(subjectName);
                    }
                }
                a.subjects = subjectList;

                // Optional: Use program field to show the count of subjects
                a.program = "Subjects: " + subjectList.size();
            }

            // 🔹 TEACHER-SPECIFIC FIELDS: Courses (Optional, for completeness)
            DataSnapshot coursesSnapshot = s.child("courses");
            if (!coursesSnapshot.exists()) {
                coursesSnapshot = s.child("courseDisplays");
            }
            if (coursesSnapshot.exists() && coursesSnapshot.hasChildren()) {
                List<String> courseList = new ArrayList<>();
                for (DataSnapshot courseChild : coursesSnapshot.getChildren()) {
                    String courseName = courseChild.getValue(String.class);
                    if (!TextUtils.isEmpty(courseName)) {
                        courseList.add(courseName);
                    }
                }
                a.courses = courseList;
            }
        }

        return a;
    }

    private void setCountText(TextView t, long value) {
        if (t != null)
            t.setText(NumberFormat.getInstance(Locale.getDefault()).format(value));
    }

    private void loadDashboardCounts() {
        loadCountFromFirebase(coursesRef, countCurriculum);
        loadCountFromFirebase(studentsRef, countExaminee);
        loadCountFromFirebase(teachersRef, countTc);
    }

    private void loadCountFromFirebase(DatabaseReference ref, TextView target) {
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                setCountText(target, snapshot.getChildrenCount());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (studentsRef != null && studentsValueListener != null)
            studentsRef.removeEventListener(studentsValueListener);
        if (teachersRef != null && tcValueListener != null)
            teachersRef.removeEventListener(tcValueListener);
    }

    // 🔹 UPDATED: Added Lists for detailed teacher data
    public static class RecentAccount {
        public String id, name, email, role, program, status;
        public List<String> courses;
        public List<String> subjects;

        public RecentAccount() {
        }
    }

    // FINAL CLEANED-UP SingleRecentAdapter CLASS
    private static class SingleRecentAdapter extends RecyclerView.Adapter<SingleRecentAdapter.VH> {

        interface OnItemClick {
            void onClick(RecentAccount acc);
        }

        private final List<RecentAccount> items = new ArrayList<>();
        private final OnItemClick listener;

        SingleRecentAdapter(OnItemClick listener) {
            this.listener = listener;
        }

        void setSingle(RecentAccount acc) {
            items.clear();
            if (acc != null) items.add(acc);
            notifyDataSetChanged();
        }

        // 1. 🖼️ Single and Correct VH Class Definition
        static class VH extends RecyclerView.ViewHolder {
            FrameLayout avatarContainer;
            TextView tvInitials, tvName, tvEmail, tvExtra, tvRole;
            ImageView overflow;
            ImageView ivDefaultAvatar;

            // 🛠️ CORRECT VH CONSTRUCTOR - FIX: Initialize 'overflow'
            VH(View v) {
                super(v);

                // --- 1. AVATAR ELEMENTS ---
                avatarContainer = v.findViewById(R.id.avatar_container);
                tvInitials = v.findViewById(R.id.tv_avatar_initials);
                ivDefaultAvatar = v.findViewById(R.id.iv_default_avatar);

                // --- 2. TEXT DETAILS ---
                tvName = v.findViewById(R.id.tv_name);
                tvEmail = v.findViewById(R.id.tv_email);
                tvExtra = v.findViewById(R.id.tv_extra);
                tvRole = v.findViewById(R.id.tv_role);
            }

            // 2. 🔗 BIND METHOD - UPDATED to only show Name, Email, and Role
            void bind(RecentAccount a, OnItemClick click) {
                // 1. Basic Text Setting (Name, Email, Role)
                if (tvName != null) tvName.setText(a.name != null ? a.name : "—");
                if (tvEmail != null) tvEmail.setText(a.email != null ? a.email : "");
                if (tvRole != null) tvRole.setText(a.role != null ? a.role : "");
                if (tvInitials != null) tvInitials.setText(getInitials(a.name));

                // 2. 🛑 ITAGO ANG TVPHONE AT TVEXTRA
                if (tvExtra != null) {
                    tvExtra.setText(""); // Clear the text
                    tvExtra.setVisibility(View.GONE); // Hide the view
                }

                // 3. 🖼️ AVATAR/INITIALS LOGIC
                int defaultIconRes;
                if ("Student".equals(a.role)) {
                    defaultIconRes = R.drawable.examinee_default;
                } else if ("TC".equals(a.role)) {
                    defaultIconRes = R.drawable.tc_profile;
                } else {
                    defaultIconRes = R.drawable.ic_clear;
                }

                String initials = getInitials(a.name);

                // Logic for showing Icon vs Initials (robust check)
                boolean shouldShowDefaultIcon = TextUtils.isEmpty(initials) ||
                        a.id.equals(a.name) ||
                        (a.name != null && a.name.equals("—"));

                if (ivDefaultAvatar != null) {
                    if (shouldShowDefaultIcon) {
                        ivDefaultAvatar.setImageResource(defaultIconRes);
                        ivDefaultAvatar.setVisibility(View.VISIBLE);
                        if (tvInitials != null) tvInitials.setVisibility(View.GONE);
                    } else {
                        ivDefaultAvatar.setVisibility(View.GONE);
                        if (tvInitials != null) tvInitials.setVisibility(View.VISIBLE);
                    }
                }

                // 4. Click Listeners
                itemView.setOnClickListener(v -> click.onClick(a));
                if (overflow != null) {
                    overflow.setOnClickListener(v -> Toast.makeText(v.getContext(), "More for " + a.name, Toast.LENGTH_SHORT).show());
                }
            }

            // 3. 🔡 getInitials METHOD
            private String getInitials(String n) {
                if (n == null || n.trim().isEmpty()) return "";

                if (n.matches("^(STD|TCHR)-?(\\d+)$")) {
                    return n.substring(0, 1);
                }

                String[] p = n.trim().split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < p.length && i < 2; i++)
                    sb.append(Character.toUpperCase(p[i].charAt(0)));
                return sb.toString();
            }
        }

        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recent_account, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(items.get(position), listener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}