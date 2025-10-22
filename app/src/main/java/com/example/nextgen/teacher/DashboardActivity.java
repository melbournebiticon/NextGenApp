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


import org.json.JSONException;
import org.json.JSONObject;
import com.example.nextgen.R;


public class DashboardActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;

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
            // ✅ Go to ProfileActivity to show saved user info
            Intent profileIntent = new Intent(this, ProfileActivity.class);
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
