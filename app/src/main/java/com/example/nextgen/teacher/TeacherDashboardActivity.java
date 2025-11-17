package com.example.nextgen.teacher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;


import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import android.widget.ImageView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;


import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.FirebaseUser;


import android.view.View;                   // For inflating the dialog layout
import android.widget.EditText;             // For EditText fields in dialog
import android.widget.Toast;                // For showing Toast messages
import androidx.appcompat.app.AlertDialog;  // For AlertDialog
import androidx.annotation.NonNull;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;




import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TeacherDashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    // Profile info (Hidden Text Views)
    TextView tvTeacherId, tvFullName, tvEmail, tvBirthday, tvCourse, tvSubjects;

    SessionManager sessionManager;
    DatabaseReference teachersRef, examsRef;

    DrawerLayout drawerLayout;
    NavigationView navigationView;
    Toolbar toolbar;

    // Header views for Side Navigation
    TextView navHeaderUsername, navHeaderEmail;
    ImageView navHeaderImage;

    // Dashboard summary
    TextView tvTeacherNameDisplay, tvTeacherIdDisplay, tvActiveExamsCount, tvRecentExamTitle;

    // Dashboard cards (Quick Actions)
    CardView cardManageExam, cardManageExaminees, cardCreateActivity, cardViewProfile;

    // NEW: Variable para hawakan ang reference ng Profile icon
    private MenuItem profileMenuItem;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_dashboard);

        // Toolbar + Drawer setup
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);

        // Access header layout
        View headerView = navigationView.getHeaderView(0);
        navHeaderUsername = headerView.findViewById(R.id.nav_header_username);
        navHeaderEmail = headerView.findViewById(R.id.nav_header_email);
        navHeaderImage = headerView.findViewById(R.id.nav_header_image);

        // Firebase + Session
        sessionManager = new SessionManager(this);
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
        examsRef = FirebaseDatabase.getInstance().getReference("Exams");

        // Initialize views
        tvTeacherId = findViewById(R.id.tvTeacherId);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvBirthday = findViewById(R.id.tvBirthday);
        tvCourse = findViewById(R.id.tvCourse);
        tvSubjects = findViewById(R.id.tvSubjects);

        // Dashboard summary
        tvTeacherNameDisplay = findViewById(R.id.tvTeacherNameDisplay);
        tvTeacherIdDisplay = findViewById(R.id.tvTeacherIdDisplay);
        tvRecentExamTitle = findViewById(R.id.tvRecentExamTitle);
        tvActiveExamsCount = findViewById(R.id.tvActiveExamsCount);


        // Cards
        cardManageExam = findViewById(R.id.cardManageExam);
        cardCreateActivity = findViewById(R.id.cardCreateActivity);
        cardManageExaminees = findViewById(R.id.cardManageExaminees);
        cardViewProfile = findViewById(R.id.cardViewProfile);

        // Card actions
        cardManageExam.setOnClickListener(v -> startActivity(new Intent(this, ManageExamActivity.class)));

        cardCreateActivity.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateActivityActivity.class);
            startActivity(intent);
        });

        cardManageExaminees.setOnClickListener(v -> startActivity(new Intent(this, ViewStudentsActivity.class)));
        cardViewProfile.setOnClickListener(v -> openProfile()); // Quick action card also opens profile

        // Get teacher ID from session
        String teacherId = sessionManager.getUserId();
        if (teacherId != null) {
            loadTeacherInfo(teacherId);
            loadExamData(teacherId);
            loadActiveExamsCount(sessionManager.getUserId());
            loadMyClassesMenu(teacherId);

        } else {
            Toast.makeText(this, "Teacher ID not found in session!", Toast.LENGTH_SHORT).show();
        }
    }


    // =================================================================
    // START: TOOLBAR PROFILE/LOGOUT MENU HANDLING (FINAL FIXED VERSION)
    // =================================================================

    /**
     * Step 4: Handles the Toolbar Profile icon and Popup Menu.
     * This version fixes the wrong ID reference and guarantees that
     * the popup appears when the profile icon is tapped.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the toolbar_action_menu.xml (contains only the profile icon)
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.toolbar_action_menu, menu);
        profileMenuItem = menu.findItem(R.id.action_profile);

        loadToolbarProfileIcon();

        return true;
    }
    private void loadToolbarProfileIcon() {
        String teacherId = sessionManager.getUserId(); // ✅ Use session, not Intent
        if (teacherId == null || teacherId.isEmpty()) {
            profileMenuItem.setIcon(R.drawable.tc_profile);
            return;
        }

        DatabaseReference teacherRef = FirebaseDatabase.getInstance()
                .getReference("Teachers")
                .child(teacherId);

        teacherRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String base64Image = snapshot.child("profileImage").getValue(String.class);

                if (base64Image != null && !base64Image.isEmpty()) {
                    try {
                        // Clean up possible Base64 prefix
                        String pureBase64 = base64Image.replaceAll("^data:image/.*;base64,", "").trim();
                        pureBase64 = pureBase64.replaceAll("\\s+", "");
                        byte[] decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                        if (bitmap != null && profileMenuItem != null) {
                            Bitmap circularBitmap = getCircularBitmap(bitmap);
                            Drawable drawable = new BitmapDrawable(getResources(), circularBitmap);
                            profileMenuItem.setIcon(drawable);

                        } else {
                            profileMenuItem.setIcon(R.drawable.tc_profile);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        profileMenuItem.setIcon(R.drawable.tc_profile);
                    }
                } else {
                    profileMenuItem.setIcon(R.drawable.tc_profile);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                profileMenuItem.setIcon(R.drawable.tc_profile);
            }
        });
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        // ✅ Corrected: use R.id.action_profile (NOT R.id.id.action_profile)
        if (itemId == R.id.action_profile) {
            // Try to locate the actual view for the icon
            View anchorView = findViewById(R.id.action_profile);

            // If we can’t find it, use the toolbar itself as fallback
            if (anchorView == null) {
                anchorView = toolbar;
            }

            // Show the popup menu anchored to the icon (or toolbar)
            showProfilePopup(anchorView);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Displays the PopupMenu under the Profile icon with View Profile and Logout.
     */
    private void showProfilePopup(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);

        // Inflate popup menu layout (profile_popup_menu.xml)
        popup.getMenuInflater().inflate(R.menu.profile_popup_menu, popup.getMenu());

        // Handle popup menu item clicks
        popup.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();

            if (itemId == R.id.action_view_profile) {
                openProfile();
                return true;
            } else if (itemId == R.id.action_logout) {
                logout();
                return true;
            } else if (itemId == R.id.action_change_password) {
                showChangePasswordDialog(); // call the dialog
                return true;
            }
            return false;
        });


        popup.show();
    }

    // =================================================================
    // END: TOOLBAR PROFILE/LOGOUT MENU HANDLING (FINAL FIXED VERSION)
    // =================================================================


    private void loadTeacherInfo(String teacherId) {
        teachersRef.child(teacherId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String id = snapshot.child("id").getValue(String.class);
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    String birthday = snapshot.child("birthday").getValue(String.class);

                    // Base64 profile image
                    String base64Image = snapshot.child("profileImage").getValue(String.class);
                    if (base64Image != null && !base64Image.isEmpty()) {
                        try {
                            byte[] decodedBytes = Base64.decode(base64Image, Base64.NO_WRAP);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                            // Make the bitmap circular
                            Bitmap circularBitmap = getCircularBitmap(bitmap);

                            // Apply to both header and top card
                            navHeaderImage.setImageBitmap(circularBitmap);
                            ImageView imgProfile = findViewById(R.id.imgProfilePicture);
                            imgProfile.setImageBitmap(circularBitmap);

                        } catch (Exception e) {
                            e.printStackTrace();
                            navHeaderImage.setImageResource(R.drawable.tc_profile);
                        }
                    } else {
                        navHeaderImage.setImageResource(R.drawable.tc_profile);
                    }

                    // Update text info
                    tvTeacherId.setText(id != null ? id : teacherId);
                    tvFullName.setText(fullName != null ? fullName : "No Name");
                    tvEmail.setText(email != null ? email : "No Email");
                    tvBirthday.setText(birthday != null ? birthday : "No Birthday");

                    navHeaderUsername.setText(fullName != null ? fullName : "Teacher");
                    navHeaderEmail.setText(email != null ? email : "No Email");
                    tvTeacherNameDisplay.setText("Welcome, " + (fullName != null ? fullName : "Teacher Name"));
                    tvTeacherIdDisplay.setText("ID: " + (id != null ? id : teacherId));

                } else {
                    Toast.makeText(TeacherDashboardActivity.this, "Teacher info not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeacherDashboardActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }



    private void loadExamData(String teacherId) {
        DatabaseReference examsRef = FirebaseDatabase.getInstance()
                .getReference("Exams");

        examsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DataSnapshot> teacherExams = new ArrayList<>();

                if (snapshot.hasChild(teacherId)) {
                    for (DataSnapshot examSnap : snapshot.child(teacherId).getChildren()) {
                        teacherExams.add(examSnap);
                    }
                } else {
                    for (DataSnapshot examSnap : snapshot.getChildren()) {
                        String tid = examSnap.child("teacherId").getValue(String.class);
                        if (tid != null && tid.equals(teacherId)) {
                            teacherExams.add(examSnap);
                        }
                    }
                }

                if (!teacherExams.isEmpty()) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    teacherExams.sort((a, b) -> {
                        try {
                            Date dateA = sdf.parse(a.child("createdAt").getValue(String.class));
                            Date dateB = sdf.parse(b.child("createdAt").getValue(String.class));
                            return dateB.compareTo(dateA);
                        } catch (ParseException e) {
                            return 0;
                        }
                    });

                    DataSnapshot latestExam = teacherExams.get(0);
                    String examTitle = latestExam.child("examTitle").getValue(String.class);
                    String sectionName = latestExam.child("sectionName").getValue(String.class);
                    String createdAt = latestExam.child("createdAt").getValue(String.class);

                    tvRecentExamTitle.setText(
                            (examTitle != null ? examTitle : "No Title")
                                    + (sectionName != null ? " - Section " + sectionName : "")
                                    + "\nCreated at: " + (createdAt != null ? createdAt : "No date")
                    );
                } else {
                    tvRecentExamTitle.setText("No exams created yet");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeacherDashboardActivity.this,
                        "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openProfile() {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("teacherId", tvTeacherId.getText().toString());
        intent.putExtra("fullName", tvFullName.getText().toString());
        intent.putExtra("email", tvEmail.getText().toString());
        intent.putExtra("birthday", tvBirthday.getText().toString());
        intent.putExtra("course", tvCourse.getText().toString());
        intent.putExtra("subjects", tvSubjects.getText().toString());
        startActivity(intent);
    }

    // ===== SIDE NAVIGATION LOGIC =====
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_manage_exam) {
            startActivity(new Intent(this, ManageExamActivity.class));
        } else if (id == R.id.nav_view_results) {
            startActivity(new Intent(this, ViewStudentsActivity.class));
        } else if (id == R.id.nav_view_profile) {
            openProfile();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void logout() {
        SharedPreferences prefs = getSharedPreferences("user_data", MODE_PRIVATE);
        prefs.edit().remove("logged_user").apply();

        FirebaseAuth.getInstance().signOut();
        sessionManager.clearSession();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
    private void showChangePasswordDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_change_password, null);

        EditText etOldPassword = view.findViewById(R.id.etOldPassword);
        EditText etNewPassword = view.findViewById(R.id.etNewPassword);
        EditText etConfirmPassword = view.findViewById(R.id.etConfirmPassword);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Password")
                .setView(view)
                .setPositiveButton("Change", null)
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String oldPass = etOldPassword.getText().toString().trim();
                String newPass = etNewPassword.getText().toString().trim();
                String confirmPass = etConfirmPassword.getText().toString().trim();

                if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!newPass.equals(confirmPass)) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }

                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null || user.getEmail() == null) {
                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Re-authenticate
                com.google.firebase.auth.AuthCredential credential =
                        com.google.firebase.auth.EmailAuthProvider.getCredential(user.getEmail(), oldPass);

                user.reauthenticate(credential)
                        .addOnSuccessListener(aVoid -> {
                            // Update Firebase Auth password
                            user.updatePassword(newPass)
                                    .addOnSuccessListener(aVoid1 -> {
                                        // ALSO update Realtime Database password field as hash
                                        String teacherId = tvTeacherId.getText().toString();
                                        String hashedNewPass = hashPassword(newPass);

                                        teachersRef.child(teacherId).child("password")
                                                .setValue(hashedNewPass)
                                                .addOnSuccessListener(aVoid2 -> {
                                                    Toast.makeText(TeacherDashboardActivity.this,
                                                            "Password updated in Auth and Database (hashed)!", Toast.LENGTH_SHORT).show();
                                                    dialog.dismiss();
                                                })
                                                .addOnFailureListener(e ->
                                                        Toast.makeText(TeacherDashboardActivity.this,
                                                                "Auth updated but failed in DB: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                                );
                                    })

                                    .addOnFailureListener(e ->
                                            Toast.makeText(TeacherDashboardActivity.this,
                                                    "Failed to update password in Auth: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                    );
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(TeacherDashboardActivity.this,
                                        "Old password is incorrect", Toast.LENGTH_SHORT).show()
                        );
            });
        });

        dialog.show();
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void loadActiveExamsCount(String teacherId) {
        DatabaseReference examsRef = FirebaseDatabase.getInstance().getReference("Exams").child(teacherId);

        examsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int activeCount = 0;

                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean isActive = child.child("active").getValue(Boolean.class);
                    Long scheduledAt = child.child("scheduledAt").getValue(Long.class);
                    Integer durationMinutes = child.child("durationMinutes").getValue(Integer.class);

                    long examEndTime = scheduledAt + (durationMinutes * 60 * 1000);

                    Log.d("ActiveExamDebug", "ExamID: " + child.getKey()
                            + ", active: " + isActive
                            + ", scheduledAt: " + scheduledAt
                            + ", durationMinutes: " + durationMinutes
                            + ", examEndTime: " + examEndTime
                            + ", now: " + System.currentTimeMillis());

                    if (isActive != null && isActive && System.currentTimeMillis() <= examEndTime) {
                        activeCount++;
                    }
                }


                if (tvActiveExamsCount != null)
                    tvActiveExamsCount.setText(String.valueOf(activeCount));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeacherDashboardActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    private Bitmap getCircularBitmap(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(output);
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, size, size);
        final RectF rectF = new RectF(rect);

        float radius = size / 2f;
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(Color.BLACK);
        canvas.drawOval(rectF, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, null, rect, paint);

        return output;
    }
    private void loadMyClassesMenu(String teacherId) {
        DatabaseReference teacherRef = FirebaseDatabase.getInstance().getReference("Teachers").child(teacherId);
        DatabaseReference subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");

        teacherRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot teacherSnapshot) {
                if (!teacherSnapshot.exists()) return;

                List<String> subjectIds = new ArrayList<>();
                for (DataSnapshot snap : teacherSnapshot.child("assignedSubjects").getChildren()) {
                    String subjectId = snap.getValue(String.class);
                    if (subjectId != null) subjectIds.add(subjectId);
                }

                // Clear old My Classes submenu
                Menu menu = navigationView.getMenu();
                MenuItem myClassesItem = menu.findItem(R.id.nav_my_classes);
                final SubMenu subMenu; // must be final for inner use
                if (myClassesItem.getSubMenu() != null) {
                    subMenu = myClassesItem.getSubMenu();
                    subMenu.clear();
                } else {
                    subMenu = menu.addSubMenu("My Classes");
                }

                // For each subject assigned to this teacher
                for (String subjectId : subjectIds) {
                    final String finalSubjectId = subjectId; // must be final for listener

                    subjectsRef.child(finalSubjectId).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot subjectSnap) {
                            if (!subjectSnap.exists()) return;

                            String code = subjectSnap.child("code").getValue(String.class);
                            String name = subjectSnap.child("name").getValue(String.class);
                            String courseName = subjectSnap.child("courseName").getValue(String.class);
                            String specialization = subjectSnap.child("specializationName").getValue(String.class);
                            String year = subjectSnap.child("yearName").getValue(String.class);
                            String section = subjectSnap.child("sectionName").getValue(String.class);

                            String displayText = (code != null ? code : "Unknown Code") + " - " +
                                    (name != null ? name : "Unknown Name");
                            String courseDisplay = (courseName != null ? courseName : "Unknown Course") + " - " +
                                    (specialization != null ? specialization : "N/A") + " - " +
                                    (year != null ? year : "N/A") + " - " +
                                    (section != null ? section : "N/A");

                            // ✅ Create clickable submenu item
                            MenuItem item = subMenu.add(displayText);
                            item.setOnMenuItemClickListener(menuItem -> {
                                Intent intent = new Intent(TeacherDashboardActivity.this, TeacherActivitiesActivity.class);
                                intent.putExtra("subjectId", finalSubjectId);
                                intent.putExtra("subjectCode", code);
                                intent.putExtra("subjectName", name);
                                intent.putExtra("courseDisplay", courseDisplay);
                                startActivity(intent);
                                drawerLayout.closeDrawer(GravityCompat.START);
                                return true;
                            });
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) { }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

}




