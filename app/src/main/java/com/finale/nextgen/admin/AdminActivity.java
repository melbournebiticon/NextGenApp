package com.finale.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import com.finale.nextgen.MainActivity;
import com.finale.nextgen.R;
import com.finale.nextgen.SessionManager;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import android.widget.PopupMenu;
import java.text.NumberFormat;
import java.util.Locale;

public class AdminActivity extends AppCompatActivity{

    private NavigationView navigationView;
    private TextView countExaminee, countTc, countCurriculum;
    private DatabaseReference coursesRef, studentsRef, teachersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        initializeFirebaseReferences();
        initUi();
        loadDashboardCounts();

        // PROFILE MENU SETUP
        ImageView ivProfile = findViewById(R.id.ivProfile);
        if (ivProfile != null) {
            ivProfile.setOnClickListener(view -> showProfileMenu(view));
        }

        // QUICK ACTIONS LISTENERS
        findViewById(R.id.cardCourses).setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, CourseActivity.class)));
        findViewById(R.id.cardSubjects).setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, SubjectActivity.class)));
        findViewById(R.id.cardSpecialization).setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, SpecializationsActivity.class)));
        findViewById(R.id.cardYear).setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, YearsActivity.class)));
        findViewById(R.id.cardSection).setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, SectionsActivity.class)));
        findViewById(R.id.cardManageTeachers).setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, TeacherActivity.class)));
        findViewById(R.id.cardManageStudents).setOnClickListener(v ->
                startActivity(new Intent(AdminActivity.this, StudentActivity.class)));
    }

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
        popupMenu.getMenuInflater().inflate(R.menu.admin_popup_logout, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
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

        findViewById(R.id.card_totalCourses).setClickable(false);
        findViewById(R.id.card_totalCourses).setOnClickListener(null);
        findViewById(R.id.card_totalExaminees).setClickable(false);
        findViewById(R.id.card_totalExaminees).setOnClickListener(null);
        findViewById(R.id.card_totalTeachers).setClickable(false);
        findViewById(R.id.card_totalTeachers).setOnClickListener(null);
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
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}