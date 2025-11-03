package com.example.nextgen.teacher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.example.nextgen.MainActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.example.nextgen.R;

public class DashboardActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;

    // Add class-level variables for data to use in onNavigationItemSelected
    private String fullName = "Teacher Name";
    private String teacherId = "N/A";
    private String email = "No email";
    private String birthday = "N/A";
    private String course = "N/A";
    private String subjects = "N/A";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // ===== Toolbar Setup =====
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // ===== Drawer & Navigation =====
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // ✅ Get Header View of Navigation Drawer
        View headerView = navigationView.getHeaderView(0);
        TextView tvUserName = headerView.findViewById(R.id.nav_header_username);
        TextView tvUserEmail = headerView.findViewById(R.id.nav_header_email);

        // ✅ Fetch current logged-in user info from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("user_data", MODE_PRIVATE);
        String currentUser = prefs.getString("logged_user", null); // changed to match MainActivity
        String accountsStr = prefs.getString("accounts", "{}");

        String usernameDisplay = "User Name";
        String emailDisplay = "No email";

        try {
            JSONObject accounts = new JSONObject(accountsStr);
            if (currentUser != null && accounts.has(currentUser)) {
                JSONObject userObj = accounts.getJSONObject(currentUser);
                usernameDisplay = currentUser;
                emailDisplay = userObj.optString("email", "No email");

                // Add new data fetching for dashboard
                fullName = userObj.optString("fullName", currentUser);  // Use fullName if available, else username
                teacherId = userObj.optString("teacherId", "N/A");
                email = emailDisplay;  // Already fetched
                birthday = userObj.optString("birthday", "N/A");
                course = userObj.optString("course", "N/A");
                subjects = userObj.optString("subjects", "N/A");

                // Handle exams array for total and recent
                int totalExams = 0;
                String recentExam = "No recent exam";
                JSONArray examsArray = userObj.optJSONArray("exams");
                if (examsArray != null) {
                    totalExams = examsArray.length();
                    if (totalExams > 0) {
                        // Get the most recent exam (last in array)
                        JSONObject recentExamObj = examsArray.getJSONObject(totalExams - 1);
                        recentExam = recentExamObj.optString("name", "Unknown Exam");
                    }
                }

                // Bind to XML views
                // Hidden TextViews (for data binding or future use)
                TextView tvTeacherIdHidden = findViewById(R.id.tvTeacherId);
                TextView tvFullNameHidden = findViewById(R.id.tvFullName);
                TextView tvEmailHidden = findViewById(R.id.tvEmail);
                TextView tvBirthdayHidden = findViewById(R.id.tvBirthday);
                TextView tvCourseHidden = findViewById(R.id.tvCourse);
                TextView tvSubjectsHidden = findViewById(R.id.tvSubjects);

                tvTeacherIdHidden.setText(teacherId);
                tvFullNameHidden.setText(fullName);
                tvEmailHidden.setText(email);
                tvBirthdayHidden.setText(birthday);
                tvCourseHidden.setText(course);
                tvSubjectsHidden.setText(subjects);

                // Visible Header Views (Name and ID)
                TextView tvTeacherNameDisplay = findViewById(R.id.tvTeacherNameDisplay);
                TextView tvTeacherIdDisplay = findViewById(R.id.tvTeacherIdDisplay);

                tvTeacherNameDisplay.setText(fullName);
                tvTeacherIdDisplay.setText("ID: " + teacherId);



                // Recent Activity (Recent Exam)
                TextView tvRecentExamTitle = findViewById(R.id.tvRecentExamTitle);
                tvRecentExamTitle.setText(recentExam);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // ✅ Display fetched user data
        tvUserName.setText(usernameDisplay);
        tvUserEmail.setText(emailDisplay);

        // ===== Drawer Toggle (Hamburger) =====
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // ===== Back Press Handling =====
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    DashboardActivity.super.onBackPressed();
                }
            }
        });
    }

    // ===== Navigation Item Clicks =====
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_manage_exam) {
            // ✅ Open ManageExamActivity
            Intent intent = new Intent(this, ManageExamActivity.class);
            startActivity(intent);

        } else if (id == R.id.nav_view_results) {
            // Placeholder for future results activity

        } else if (id == R.id.nav_view_profile) {
            Intent profileIntent = new Intent(this, ProfileActivity.class);

            // Pass the fetched data
            profileIntent.putExtra("teacherId", teacherId);
            profileIntent.putExtra("fullName", fullName);
            profileIntent.putExtra("email", email);
            profileIntent.putExtra("birthday", birthday);
            profileIntent.putExtra("course", course);
            profileIntent.putExtra("subjects", subjects);
            profileIntent.putExtra("address", "");  // Add if available in JSON
            profileIntent.putExtra("phone", "");    // Add if available in JSON
            profileIntent.putExtra("profileImage", "");  // Add if available

            startActivity(profileIntent);

        } else if (id == R.id.nav_logout) {
            // ✅ Logout — remove only logged_user
            SharedPreferences prefs = getSharedPreferences("user_data", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("logged_user");
            editor.apply();

            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
}
