package com.finale.nextgen.teacher;

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
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import android.widget.ImageView;

import com.finale.nextgen.MainActivity;
import com.finale.nextgen.R;
import com.finale.nextgen.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.FirebaseUser;

import androidx.appcompat.app.AlertDialog;
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

public class TeacherDashboardActivity extends AppCompatActivity {

    // Profile info (Hidden Text Views)
    TextView tvTeacherId, tvFullName, tvEmail, tvBirthday, tvCourse, tvSubjects;

    SessionManager sessionManager;
    DatabaseReference teachersRef, examsRef;

    Toolbar toolbar;

    private MenuItem profileMenuItem;



    // Dashboard summary
    TextView tvTeacherNameDisplay, tvTeacherIdDisplay, tvActiveExamsCount, tvRecentExamTitle, tvActiveQuizCount;

    // Dashboard cards (Quick Actions)
    CardView cardManageExam, cardManageQuiz, cardManageExaminees, cardCreateActivity, cardStudentAttendance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_dashboard);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Firebase + Session setup
        sessionManager = new SessionManager(this);
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
        examsRef = FirebaseDatabase.getInstance().getReference("Exams");

        // View initializations
        tvTeacherId = findViewById(R.id.tvTeacherId);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvBirthday = findViewById(R.id.tvBirthday);
        tvCourse = findViewById(R.id.tvCourse);
        tvSubjects = findViewById(R.id.tvSubjects);

        tvTeacherNameDisplay = findViewById(R.id.tvTeacherNameDisplay);
        tvTeacherIdDisplay = findViewById(R.id.tvTeacherIdDisplay);
        tvRecentExamTitle = findViewById(R.id.tvRecentExamTitle);
        tvActiveExamsCount = findViewById(R.id.tvActiveExamsCount);
        tvActiveQuizCount = findViewById(R.id.tvActiveQuizCount);

        CardView cardHeaderProfile = findViewById(R.id.cardHeaderProfile);


        // Cards (match XML IDs)
        cardManageExam = findViewById(R.id.cardManageExam);
        cardManageQuiz = findViewById(R.id.cardManageQuiz);
        cardCreateActivity = findViewById(R.id.cardCreateActivity);
        cardManageExaminees = findViewById(R.id.cardViewExaminee);
        cardStudentAttendance = findViewById(R.id.cardStudentAttendance);

        if (cardHeaderProfile != null) {
            cardHeaderProfile.setOnClickListener(v -> openProfile());
        }

        // Safe set click listeners
        if(cardManageExam != null) {
            cardManageExam.setOnClickListener(v -> startActivity(new Intent(this, ManageExamActivity.class)));
        }
        if(cardManageQuiz != null) {
            cardManageQuiz.setOnClickListener(v -> startActivity(new Intent(this, ManageQuizActivity.class)));
        }
        if(cardCreateActivity != null) {
            cardCreateActivity.setOnClickListener(v -> startActivity(new Intent(this, TeacherActivitiesActivity.class)));
        }
        if(cardManageExaminees != null) {
            cardManageExaminees.setOnClickListener(v -> startActivity(new Intent(this, ViewStudentsActivity.class)));
        }
        if(cardStudentAttendance != null) {
            cardStudentAttendance.setOnClickListener(v -> startActivity(new Intent(this, StudentAttendanceActivity.class)));
        }

        loadActiveQuizCount();

        // Get teacher ID from session
        String teacherId = sessionManager.getUserId();
        if (teacherId != null) {
            loadTeacherInfo(teacherId);
            loadExamData(teacherId);
            loadActiveExamsCount(sessionManager.getUserId());

        } else {
            Toast.makeText(this, "Teacher ID not found in session!", Toast.LENGTH_SHORT).show();
        }
    }

    // Toolbar menu for profile actions
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.toolbar_action_menu, menu); // see XML section below
        profileMenuItem = menu.findItem(R.id.action_profile);
        loadToolbarProfileIcon();
        return true;
    }

    private void loadActiveQuizCount() {
        DatabaseReference quizRef = FirebaseDatabase.getInstance().getReference("Quizzes");
        String teacherId = sessionManager.getUserId();
        quizRef.orderByChild("teacherId").equalTo(teacherId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int activeQuizCount = 0;
                        for (DataSnapshot quizSnap : snapshot.getChildren()) {
                            Boolean isActive = quizSnap.child("active").getValue(Boolean.class);
                            if (isActive != null && isActive) {
                                activeQuizCount++;
                            }
                        }
                        TextView tvActiveQuizCount = findViewById(R.id.tvActiveQuizCount);
                        if (tvActiveQuizCount != null)
                            tvActiveQuizCount.setText(String.valueOf(activeQuizCount));
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(TeacherDashboardActivity.this,
                                "Failed to load active quizzes: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Dynamically load/draw the user's profile photo in toolbar
    private void loadToolbarProfileIcon() {
        String teacherId = sessionManager.getUserId();
        if (teacherId == null || teacherId.isEmpty()) {
            profileMenuItem.setIcon(R.drawable.tc_profile);
            return;
        }
        DatabaseReference teacherRef = FirebaseDatabase.getInstance().getReference("Teachers").child(teacherId);
        teacherRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String base64Image = snapshot.child("profileImage").getValue(String.class);
                if (base64Image != null && !base64Image.isEmpty()) {
                    try {
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

    // Handle toolbar profile icon actions
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_profile) {
            showProfilePopup(toolbar);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showProfilePopup(View anchorView) {
        // Use Gravity.END or anchor to toolbar with gravity
        PopupMenu popup = new PopupMenu(this, toolbar, Gravity.END);
        popup.getMenuInflater().inflate(R.menu.profile_popup_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.action_view_profile) {
                openProfile();
                return true;
            } else if (itemId == R.id.action_logout) {
                logout();
                return true;
            } else if (itemId == R.id.action_change_password) {
                showChangePasswordDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

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
                    ImageView imgProfile = findViewById(R.id.imgProfilePicture);
                    if (base64Image != null && !base64Image.isEmpty()) {
                        try {
                            byte[] decodedBytes = Base64.decode(base64Image, Base64.NO_WRAP);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                            Bitmap circularBitmap = getCircularBitmap(bitmap);
                            if (imgProfile != null) imgProfile.setImageBitmap(circularBitmap);

                        } catch (Exception e) {
                            e.printStackTrace();
                            if (imgProfile != null) imgProfile.setImageResource(R.drawable.tc_profile);
                        }
                    } else {
                        if (imgProfile != null) imgProfile.setImageResource(R.drawable.tc_profile);
                    }

                    tvTeacherId.setText(id != null ? id : teacherId);
                    tvFullName.setText(fullName != null ? fullName : "No Name");
                    tvEmail.setText(email != null ? email : "No Email");
                    tvBirthday.setText(birthday != null ? birthday : "No Birthday");
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
        DatabaseReference examsRef = FirebaseDatabase.getInstance().getReference("Exams");
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

    private void logout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, which) -> {
                    SharedPreferences prefs = getSharedPreferences("user_data", MODE_PRIVATE);
                    prefs.edit().remove("logged_user").apply();
                    FirebaseAuth.getInstance().signOut();
                    sessionManager.clearSession();
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showChangePasswordDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_change_password, null);

        android.widget.EditText etOldPassword = view.findViewById(R.id.etOldPassword);
        android.widget.EditText etNewPassword = view.findViewById(R.id.etNewPassword);
        android.widget.EditText etConfirmPassword = view.findViewById(R.id.etConfirmPassword);

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

                com.google.firebase.auth.AuthCredential credential =
                        com.google.firebase.auth.EmailAuthProvider.getCredential(user.getEmail(), oldPass);

                user.reauthenticate(credential)
                        .addOnSuccessListener(aVoid -> {
                            user.updatePassword(newPass)
                                    .addOnSuccessListener(aVoid1 -> {
                                        String teacherId = tvTeacherId.getText().toString();
                                        String hashedNewPass = hashPassword(newPass);

                                        teachersRef.child(teacherId).child("password")
                                                .setValue(hashedNewPass)
                                                .addOnSuccessListener(aVoid2 -> {
                                                    Toast.makeText(TeacherDashboardActivity.this,
                                                            "Password updated", Toast.LENGTH_SHORT).show();
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
}