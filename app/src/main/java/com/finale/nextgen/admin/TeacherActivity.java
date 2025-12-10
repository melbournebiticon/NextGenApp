package com.finale.nextgen.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.ImageView;
import android.content.Intent;


import androidx.annotation.Nullable;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;   // ← add this
import java.io.ByteArrayOutputStream;
import android.graphics.Bitmap;
import android.util.Base64;
import android.app.DatePickerDialog;
import java.util.Calendar;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.finale.nextgen.utils.InputValidator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.finale.nextgen.utils.EmailSender;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


public class TeacherActivity extends AppCompatActivity {

    private EditText etFullName, etBirthday, etEmail;
    private RecyclerView recyclerCourseSelection, recyclerSubjects, recyclerTeachers;
    private ImageView btnBack, addTeacherFab;


    private List<SubjectModel> selectedCourseSubjects = new ArrayList<>();
    private List<CourseModel> courseOptionList = new ArrayList<>();
    private List<TeacherModel> teacherList = new ArrayList<>();

    // NEW: cache of all subjects for id->name mapping
    private List<SubjectModel> allSubjects = new ArrayList<>();

    private DatabaseReference teachersRef, coursesRef, subjectsRef, usersRef;
    private FirebaseAuth auth;

    private boolean sortAscending = true;

    private SubjectSelectionAdapter subjectAdapter;
    private TeacherAdapter teacherAdapter;
    private CourseSelectionAdapter courseSelectionAdapter;

    private Uri selectedImageUri;
    private ImageView currentEditProfileView;
    private TextView tvTeacherCount;
    private LinearLayout emptyState;

    private EditText etSearchTeacher;
    private List<TeacherModel> teacherListFull; // keep original full list




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher);

        // Views
        recyclerTeachers = findViewById(R.id.recyclerTeachers);
        btnBack = findViewById(R.id.btnBack);
        addTeacherFab = findViewById(R.id.addTeacherFab);

        recyclerTeachers.setLayoutManager(new LinearLayoutManager(this));

        btnBack.setOnClickListener(v -> finish());
        addTeacherFab.setOnClickListener(v -> showAddTeacherDialog());

        // Firebase references
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        auth = FirebaseAuth.getInstance();

        tvTeacherCount = findViewById(R.id.tvTeacherCount);
        emptyState = findViewById(R.id.emptyState);

        Button btnSort = findViewById(R.id.btnSort);
        btnSort.setOnClickListener(v -> sortTeachersByName());

        teacherListFull = new ArrayList<>(teacherList); // initial copy

        etSearchTeacher = findViewById(R.id.etSearchTeacher); // make sure you have this EditText in your XML
        etSearchTeacher.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTeachers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });





        teacherAdapter = new TeacherAdapter(teacherList, new TeacherAdapter.OnTeacherActionListener() {
            @Override
            public void onUpdate(TeacherModel teacher) {
                showTeacherDialog(teacher);
            }

            @Override
            public void onDelete(TeacherModel teacher) {
                // Use centralized confirm + delete flow
                confirmAndDeleteTeacher(teacher);
            }



        });
        recyclerTeachers.setAdapter(teacherAdapter);

        // IMPORTANT: load subject mapping so the adapter can show names instead of raw IDs
        loadSubjectsForMapping();

        teachersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                teacherList.clear();
                teacherListFull.clear(); // reset full list
                for (DataSnapshot ds : snapshot.getChildren()) {
                    TeacherModel t = ds.getValue(TeacherModel.class);
                    if (t != null) {
                        teacherList.add(t);
                        teacherListFull.add(t);
                    }
                }
                teacherAdapter.notifyDataSetChanged();
                updateTeacherUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeacherActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });


        loadCourses();

        addTeacherFab.setOnClickListener(v -> showAddTeacherDialog());

    }

    private void filterTeachers(String query) {
        teacherList.clear();
        if (query.isEmpty()) {
            teacherList.addAll(teacherListFull); // restore full list
        } else {
            String lowerQuery = query.toLowerCase();
            for (TeacherModel t : teacherListFull) {
                if (t.getFullName().toLowerCase().contains(lowerQuery)) {
                    teacherList.add(t);
                }
            }
        }
        teacherAdapter.notifyDataSetChanged();
        updateTeacherUI(); // optional: update empty state
    }


    private void updateTeacherUI() {
        int count = teacherList.size();
        tvTeacherCount.setText(count + " teacher" + (count != 1 ? "s" : ""));

        if (teacherList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerTeachers.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerTeachers.setVisibility(View.VISIBLE);
        }
    }


    private void loadCourses() {
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel course = ds.getValue(CourseModel.class);
                    if (course != null) courseOptionList.add(course);
                }
                // No need to notify anything here in activity
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // NEW: load subjects once and provide id->name mapping to TeacherAdapter
    private void loadSubjectsForMapping() {
        subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allSubjects.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    SubjectModel s = ds.getValue(SubjectModel.class);
                    if (s != null) allSubjects.add(s);
                }
                // Provide to adapter so it shows names instead of IDs
                teacherAdapter.setSubjectsListForMapping(allSubjects);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // If load fails, adapter will simply fallback to IDs (no mapping available)
                Log.w("TeacherActivity", "Failed to load subjects for mapping: " + error.getMessage());
            }
        });

        // Optional: also listen for future changes to Subjects and update mapping in real-time
        // If you prefer real-time updates uncomment below:
        /*
        subjectsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allSubjects.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    SubjectModel s = ds.getValue(SubjectModel.class);
                    if (s != null) allSubjects.add(s);
                }
                teacherAdapter.setSubjectsListForMapping(allSubjects);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
        */
    }

    private void sortTeachersByName() {
        if (sortAscending) {
            teacherList.sort((t1, t2) -> t1.getFullName().compareToIgnoreCase(t2.getFullName()));
        } else {
            teacherList.sort((t1, t2) -> t2.getFullName().compareToIgnoreCase(t1.getFullName()));
        }
        sortAscending = !sortAscending; // toggle for next click
        teacherAdapter.notifyDataSetChanged();
    }
    private void showAddTeacherDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_teacher, null);

        EditText etFullNameDialog = dialogView.findViewById(R.id.etFullName);
        EditText etBirthdayDialog = dialogView.findViewById(R.id.etBirthday);
        etBirthdayDialog.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePicker = new DatePickerDialog(TeacherActivity.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formattedDate = String.format("%04d-%02d-%02d",
                                selectedYear, selectedMonth + 1, selectedDay);
                        etBirthdayDialog.setText(formattedDate);
                    }, year, month, day);

            datePicker.show();
        });
        EditText etEmailDialog = dialogView.findViewById(R.id.etEmail);
        RecyclerView recyclerCourseDialog = dialogView.findViewById(R.id.recyclerCourseSelection);
        RecyclerView recyclerSubjectsDialog = dialogView.findViewById(R.id.recyclerSubjects);

        // Setup RecyclerViews
        recyclerCourseDialog.setLayoutManager(new LinearLayoutManager(this));
        recyclerSubjectsDialog.setLayoutManager(new LinearLayoutManager(this));

        // Clone the course list
        List<CourseModel> dialogCourses = new ArrayList<>(courseOptionList);
        CourseSelectionAdapter courseAdapterDialog = new CourseSelectionAdapter(this, dialogCourses);
        recyclerCourseDialog.setAdapter(courseAdapterDialog);

        // Subject adapter starts empty
        List<SubjectModel> dialogSubjects = new ArrayList<>();
        SubjectSelectionAdapter subjectAdapterDialog = new SubjectSelectionAdapter(dialogSubjects);
        recyclerSubjectsDialog.setAdapter(subjectAdapterDialog);

        // Map to store subjects per selected course
        Map<String, List<SubjectModel>> courseSubjectsMap = new HashMap<>();

        // Update subjects when course selection changes
        courseAdapterDialog.setOnCourseSelectionChanged(() -> {
            List<CourseModel> selectedCourses = courseAdapterDialog.getSelectedCourses();
            List<SubjectModel> combinedSubjects = new ArrayList<>();

            if (selectedCourses.isEmpty()) {
                subjectAdapterDialog.updateSubjects(combinedSubjects);
                return;
            }

            final int[] loadedCount = {0};
            for (CourseModel course : selectedCourses) {
                // If subjects already loaded, use cache
                if (courseSubjectsMap.containsKey(course.getId())) {
                    combinedSubjects.addAll(courseSubjectsMap.get(course.getId()));
                    loadedCount[0]++;
                    if (loadedCount[0] == selectedCourses.size()) {
                        subjectAdapterDialog.updateSubjects(combinedSubjects);
                    }
                    continue;
                }

                subjectsRef.orderByChild("courseId").equalTo(course.getId())
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                List<SubjectModel> courseSubjects = new ArrayList<>();
                                for (DataSnapshot ds : snapshot.getChildren()) {
                                    SubjectModel s = ds.getValue(SubjectModel.class);
                                    if (s != null) courseSubjects.add(s);
                                }
                                courseSubjectsMap.put(course.getId(), courseSubjects);
                                combinedSubjects.addAll(courseSubjects);

                                loadedCount[0]++;
                                if (loadedCount[0] == selectedCourses.size()) {
                                    subjectAdapterDialog.updateSubjects(combinedSubjects);
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                loadedCount[0]++;
                                if (loadedCount[0] == selectedCourses.size()) {
                                    subjectAdapterDialog.updateSubjects(combinedSubjects);
                                }
                            }
                        });
            }
        });
        // May nabago
        // Show dialog
        new AlertDialog.Builder(this)
                .setTitle("Add Teacher")
                .setView(dialogView)

                .setPositiveButton("Add", (dialog, which) -> {
                    String fullNameRaw = etFullNameDialog.getText().toString().trim();
                    String error;
                    error = InputValidator.validateFullName(fullNameRaw);
                    if (error != null) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); return; }

                    String fullName = InputValidator.formatFullName(fullNameRaw);

                    String birthday = etBirthdayDialog.getText().toString().trim();
                    String email = etEmailDialog.getText().toString().trim();

                    List<CourseModel> selectedCourses = courseAdapterDialog.getSelectedCourses();
                    List<SubjectModel> selectedSubjects = subjectAdapterDialog.getSelectedSubjects();

                    // ---------- VALIDATION ----------


                    error = InputValidator.validateFullName(fullName);
                    if (error != null) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); return; }

                    error = InputValidator.validateBirthday(birthday, 18);
                    if (error != null) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); return; }

                    error = InputValidator.validateEmail(email);
                    if (error != null) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); return; }

                    error = InputValidator.validateCourses(selectedCourses);
                    if (error != null) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); return; }

                    error = InputValidator.validateSubjects(selectedSubjects);
                    if (error != null) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); return; }



                    // Get selected subjects → store IDs instead of names
                    List<String> assignedSubjectIds = new ArrayList<>();
                    for (SubjectModel s : subjectAdapterDialog.getSelectedSubjects()) {
                        assignedSubjectIds.add(s.getId());  // ✅ store ID
                    }

// Generate teacher ID and create teacher
                    generateTeacherId(teacherId -> {
                        String[] parts = birthday.split("-"); // [YYYY, MM, DD]
                        String year = parts[0];
                        String month = parts[1];
                        String day = parts[2];
                        String password = month + day + year; // MMDDYY format

                        List<String> courseIds = new ArrayList<>();
                        List<String> courseDisplays = new ArrayList<>();
                        for (CourseModel c : selectedCourses) {
                            courseIds.add(c.getId());
                            courseDisplays.add(
                                    c.getName() + " - " +
                                            c.getSpecializationName() + " - " +
                                            c.getYearName() + " - " +
                                            c.getSectionName()
                            );
                        }

                        TeacherModel teacher = new TeacherModel(
                                teacherId,
                                fullName,
                                getDisplayName(fullName),
                                birthday,
                                email,
                                courseIds,
                                courseDisplays,
                                assignedSubjectIds, // ✅ use IDs
                                password,
                                null
                        );

                        auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener(authTask -> {
                                    if (authTask.isSuccessful()) {
                                        FirebaseUser firebaseUser = authTask.getResult().getUser();
                                        String uid = firebaseUser.getUid();
                                        teacher.setUid(uid);

                                        // Save role
                                        usersRef.child(uid).child("role").setValue("teacher");

                                        // Save full teacher record
                                        teachersRef.child(teacherId).setValue(teacher)
                                                .addOnSuccessListener(aVoid -> {
                                                    Toast.makeText(this, "Teacher added successfully", Toast.LENGTH_SHORT).show();

                                                    // Prepare email message
                                                    String subject = "Your Teacher Account Details";
                                                    String body = "Hello " + fullName + ",\n\n" +
                                                            "Your teacher account has been created.\n\n" +
                                                            "Teacher ID: " + teacherId + "\n" +
                                                            "Email: " + email + "\n" +
                                                            "Password: " + password + "\n\n" +
                                                            "Please log in and change your password.\n\n" +
                                                            "Thank you.";

                                                    // Send email
                                                    EmailSender.send(TeacherActivity.this, email, subject, body);
                                                })

                                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save teacher: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                    } else {
                                        Toast.makeText(this, "Auth failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    });

                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private String getDisplayName(String fullName) {
        String[] parts = fullName.split(" ");
        if (parts.length >= 2) {
            return parts[0].charAt(0) + "." + parts[parts.length - 1];
        }
        return fullName;
    }

    private void generateTeacherId(OnIdGeneratedListener listener) {
        teachersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Integer> numbers = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String id = ds.getKey();
                    if (id != null && id.startsWith("TCHR-")) {
                        try {
                            int num = Integer.parseInt(id.replace("TCHR-", ""));
                            numbers.add(num);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                int newNum = 1;
                while (numbers.contains(newNum)) newNum++;
                listener.onGenerated(String.format("TCHR-%04d", newNum));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeacherActivity.this, "Error generating ID", Toast.LENGTH_SHORT).show();
            }
        });
    }

    interface OnIdGeneratedListener {
        void onGenerated(String teacherId);
    }


    private void showTeacherDialog(TeacherModel teacher) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_teacher_edit, null);

        EditText etEditFullName = dialogView.findViewById(R.id.etEditFullName);
        EditText etEditBirthday = dialogView.findViewById(R.id.etEditBirthday);
        etEditBirthday.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();

            // Preload current birthday if available
            if (!TextUtils.isEmpty(etEditBirthday.getText().toString())) {
                try {
                    String[] parts = etEditBirthday.getText().toString().split("-");
                    int y = Integer.parseInt(parts[0]);
                    int m = Integer.parseInt(parts[1]) - 1; // months start at 0
                    int d = Integer.parseInt(parts[2]);
                    calendar.set(y, m, d);
                } catch (Exception ignored) {}
            }

            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePicker = new DatePickerDialog(TeacherActivity.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formattedDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                        etEditBirthday.setText(formattedDate);
                    }, year, month, day);

            datePicker.show();
        });

        EditText etEditEmail = dialogView.findViewById(R.id.etEditEmail);
        RecyclerView recyclerEditCourses = dialogView.findViewById(R.id.recyclerEditCourses);
        RecyclerView recyclerEditSubjects = dialogView.findViewById(R.id.recyclerEditSubjects);
        ImageView ivEditProfile = dialogView.findViewById(R.id.ivEditProfile);
        ProgressBar progressBar = dialogView.findViewById(R.id.progressBarUpload);
        TextView tvProgress = dialogView.findViewById(R.id.tvUploadProgress);

        etEditFullName.setText(teacher.getFullName());
        etEditBirthday.setText(teacher.getBirthday());
        etEditEmail.setText(teacher.getEmail());

        // Load profile image
        if (teacher.getProfileImage() != null && !teacher.getProfileImage().isEmpty()) {
            byte[] decodedBytes = Base64.decode(teacher.getProfileImage(), Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            ivEditProfile.setImageBitmap(bitmap);
        } else {
            ivEditProfile.setImageResource(R.drawable.examinee_default);
        }

        currentEditProfileView = ivEditProfile;

        ivEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, 202);
        });

        // Setup recycler views
        List<CourseModel> editCourseList = new ArrayList<>();
        CourseSelectionAdapter editCourseAdapter = new CourseSelectionAdapter(this, editCourseList);
        recyclerEditCourses.setLayoutManager(new LinearLayoutManager(this));
        recyclerEditCourses.setAdapter(editCourseAdapter);

        SubjectSelectionAdapter editSubjectAdapter = new SubjectSelectionAdapter(new ArrayList<>());
        recyclerEditSubjects.setLayoutManager(new LinearLayoutManager(this));
        recyclerEditSubjects.setAdapter(editSubjectAdapter);

        // Load all courses first
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                editCourseList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) editCourseList.add(c);
                }
                editCourseAdapter.notifyDataSetChanged();

                if (teacher.getCourseIds() != null)
                    editCourseAdapter.setPreselectedCoursesById(teacher.getCourseIds());
                // May nabago
                // Load subjects of existing courses
                if (teacher.getCourseIds() != null && !teacher.getCourseIds().isEmpty()) {
                    final List<SubjectModel> loadedSubjects = new ArrayList<>();
                    final int[] loadedCount = {0};

                    for (String courseId : teacher.getCourseIds()) {
                        subjectsRef.orderByChild("courseId").equalTo(courseId)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        for (DataSnapshot ds : snapshot.getChildren()) {
                                            SubjectModel s = ds.getValue(SubjectModel.class);
                                            if (s != null && !loadedSubjects.contains(s))
                                                loadedSubjects.add(s);
                                        }
                                        loadedCount[0]++;
                                        if (loadedCount[0] == teacher.getCourseIds().size()) {
                                            // Map IDs → names for pre-selection
                                            List<String> preselectedNames = new ArrayList<>();
                                            for (SubjectModel s : loadedSubjects) {
                                                if (teacher.getAssignedSubjects().contains(s.getId())) {
                                                    preselectedNames.add(s.getName());
                                                }
                                            }

                                            editSubjectAdapter.updateSubjects(loadedSubjects);
                                            editSubjectAdapter.setPreselectedSubjects(preselectedNames);
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {}
                                });
                    }
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // 🔹 Course selection listener (dynamically update subjects)
        editCourseAdapter.setOnCourseSelectionChanged(() -> {
            List<CourseModel> selectedCourses = editCourseAdapter.getSelectedCourses();
            List<SubjectModel> combinedSubjects = new ArrayList<>();
            final int[] loadedCount = {0};

            if (selectedCourses.isEmpty()) {
                editSubjectAdapter.updateSubjects(new ArrayList<>());
                return;
            }

            for (CourseModel c : selectedCourses) {
                subjectsRef.orderByChild("courseId").equalTo(c.getId())
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                for (DataSnapshot ds : snapshot.getChildren()) {
                                    SubjectModel s = ds.getValue(SubjectModel.class);
                                    if (s != null && !combinedSubjects.contains(s))
                                        combinedSubjects.add(s);
                                }
                                loadedCount[0]++;
                                if (loadedCount[0] == selectedCourses.size()) {
                                    editSubjectAdapter.updateSubjects(combinedSubjects);
                                    editSubjectAdapter.setPreselectedSubjects(teacher.getAssignedSubjects());
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
            }
        });

        // ✅ Show the actual dialog here
        new AlertDialog.Builder(this)
                .setTitle("Update Teacher")
                .setView(dialogView)
                .setPositiveButton("Update", (dialog, which) -> {
                    updateTeacherData(teacher, etEditFullName, etEditBirthday, etEditEmail, editCourseAdapter, editSubjectAdapter);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // May nabago
    private void updateTeacherData(
            TeacherModel teacher,
            EditText etEditFullName,
            EditText etEditBirthday,
            EditText etEditEmail,
            CourseSelectionAdapter editCourseAdapter,
            SubjectSelectionAdapter editSubjectAdapter
    ) {
        String fullNameRaw = etEditFullName.getText().toString().trim();

        String error = InputValidator.validateFullName(fullNameRaw);
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            return;
        }

        String fullName = InputValidator.formatFullName(fullNameRaw);

        String birthday = etEditBirthday.getText().toString().trim();
        String email = etEditEmail.getText().toString().trim();
        List<CourseModel> selectedCourses = editCourseAdapter.getSelectedCourses();
        List<SubjectModel> selectedSubjects = editSubjectAdapter.getSelectedSubjects();

        // ---------- VALIDATION ----------

        error = InputValidator.validateFullName(fullName);
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            return;
        }

        error = InputValidator.validateBirthday(birthday,5);
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            return;
        }

        error = InputValidator.validateEmail(email);
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            return;
        }

        error = InputValidator.validateCourses(selectedCourses);
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            return;
        }

        error = InputValidator.validateSubjects(selectedSubjects);
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            return;
        }

        // ---------- UPDATE TEACHER ----------
        teacher.setFullName(fullName);
        teacher.setBirthday(birthday);
        teacher.setEmail(email);
        teacher.setDisplayName(getDisplayName(fullName));

        // Update profile image if changed
        if (selectedImageUri != null) {
            String base64Image = convertImageToBase64(selectedImageUri);
            if (base64Image != null)
                teacher.setProfileImage(base64Image);
        }

        // Courses
        List<String> courseIds = new ArrayList<>();
        List<String> courseDisplays = new ArrayList<>();
        for (CourseModel c : selectedCourses) {
            courseIds.add(c.getId());
            courseDisplays.add(c.getName() + " - " + c.getSpecializationName() + " - " + c.getYearName() + " - " + c.getSectionName());
        }
        teacher.setCourseIds(courseIds);
        teacher.setCourseDisplays(courseDisplays);

        // Subjects → store IDs
        List<String> updatedSubjectIds = new ArrayList<>();
        for (SubjectModel s : selectedSubjects) {
            updatedSubjectIds.add(s.getId());
        }
        teacher.setAssignedSubjects(updatedSubjectIds);

        // Save to Firebase
        teachersRef.child(teacher.getId())
                .setValue(teacher)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Teacher updated", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 202 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            if (currentEditProfileView != null) {
                currentEditProfileView.setImageURI(selectedImageUri);
            }
        }
    }
    private String convertImageToBase64(Uri imageUri) {
        try {
            Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            int maxSize = 400;
            int width = original.getWidth();
            int height = original.getHeight();
            float scale = Math.min((float) maxSize / width, (float) maxSize / height);
            int newWidth = Math.round(width * scale);
            int newHeight = Math.round(height * scale);
            Bitmap resized = Bitmap.createScaledBitmap(original, newWidth, newHeight, true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resized.compress(Bitmap.CompressFormat.JPEG, 60, baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.encodeToString(imageBytes, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // New: confirm dialog before deletion
    private void confirmAndDeleteTeacher(TeacherModel teacher) {
        if (teacher == null) return;
        new AlertDialog.Builder(TeacherActivity.this)
                .setTitle("Delete Teacher")
                .setMessage("Are you sure you want to delete " + teacher.getFullName() + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    deleteTeacherRecordAndUser(teacher);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // New: robust delete flow - deletes Teachers record (by id if available, otherwise queries by uid),
    // and also attempts to remove Users/{uid} independently.
    private void deleteTeacherRecordAndUser(TeacherModel teacher) {
        if (teacher == null) return;

        String teacherId = teacher.getId();
        String uid = teacher.getUid();

        // 1) Delete Teachers record - prefer using teacherId if present
        if (teacherId != null && !teacherId.isEmpty()) {
            teachersRef.child(teacherId).removeValue()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(TeacherActivity.this, "Teacher record deleted", Toast.LENGTH_SHORT).show();
                        // update local lists / UI immediately
                        teacherList.remove(teacher);
                        teacherListFull.remove(teacher);
                        teacherAdapter.notifyDataSetChanged();
                        updateTeacherUI();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(TeacherActivity.this, "Failed to delete teacher record: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e("TeacherDelete", "teachersRef.removeValue() failed", e);
                    });
        } else if (uid != null && !uid.isEmpty()) {
            // 1b) teacherId missing => find the teacher node by uid and delete by the actual key
            teachersRef.orderByChild("uid").equalTo(uid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (!snapshot.exists()) {
                                Toast.makeText(TeacherActivity.this, "Teachers record not found for uid", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            for (DataSnapshot ds : snapshot.getChildren()) { // usually just one
                                ds.getRef().removeValue()
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(TeacherActivity.this, "Teacher record deleted", Toast.LENGTH_SHORT).show();
                                            teacherList.remove(teacher);
                                            teacherListFull.remove(teacher);
                                            teacherAdapter.notifyDataSetChanged();
                                            updateTeacherUI();
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(TeacherActivity.this, "Failed to delete teacher record: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            Log.e("TeacherDelete", "removeValue by key failed", e);
                                        });
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(TeacherActivity.this, "Error finding teacher record: " + error.getMessage(), Toast.LENGTH_LONG).show();
                            Log.e("TeacherDelete", "query cancelled", error.toException());
                        }
                    });
        } else {
            Toast.makeText(TeacherActivity.this, "Cannot delete: missing teacher id and uid", Toast.LENGTH_LONG).show();
            return;
        }

        // 2) Delete Users node (if exists) — do this independently (don't block Teachers deletion)
        if (uid != null && !uid.isEmpty()) {
            usersRef.child(uid).removeValue()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(TeacherActivity.this, "User record removed", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(TeacherActivity.this, "Failed to remove user record: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e("TeacherDelete", "usersRef.removeValue() failed", e);
                    });
        }
    }

}