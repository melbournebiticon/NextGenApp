package com.example.nextgen.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


public class TeacherActivity extends AppCompatActivity {

    private EditText etFullName, etBirthday, etEmail;
    private RecyclerView recyclerCourseSelection, recyclerSubjects, recyclerTeachers;
    private Button btnAddTeacher;

    private List<SubjectModel> selectedCourseSubjects = new ArrayList<>();
    private List<CourseModel> courseOptionList = new ArrayList<>();
    private List<TeacherModel> teacherList = new ArrayList<>();

    private DatabaseReference teachersRef, coursesRef, subjectsRef, usersRef;
    private FirebaseAuth auth;

    private SubjectSelectionAdapter subjectAdapter;
    private TeacherAdapter teacherAdapter;
    private CourseSelectionAdapter courseSelectionAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher);

        // Only activity views
        recyclerTeachers = findViewById(R.id.recyclerTeachers);
        btnAddTeacher = findViewById(R.id.btnAddTeacher);

        recyclerTeachers.setLayoutManager(new LinearLayoutManager(this));

        // Firebase refs
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        auth = FirebaseAuth.getInstance();

        teacherAdapter = new TeacherAdapter(teacherList, new TeacherAdapter.OnTeacherActionListener() {
            @Override
            public void onUpdate(TeacherModel teacher) {
                showTeacherDialog(teacher);
            }

            @Override
            public void onDelete(TeacherModel teacher) {
                teachersRef.child(teacher.getId()).removeValue()
                        .addOnSuccessListener(aVoid -> Toast.makeText(TeacherActivity.this, "Teacher deleted", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(TeacherActivity.this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
        recyclerTeachers.setAdapter(teacherAdapter);

        teachersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                teacherList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    TeacherModel t = ds.getValue(TeacherModel.class);
                    if (t != null) teacherList.add(t);
                }
                teacherAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        loadCourses();

        btnAddTeacher.setOnClickListener(v -> showAddTeacherDialog());
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

    private void showAddTeacherDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_teacher, null);

        EditText etFullNameDialog = dialogView.findViewById(R.id.etFullName);
        EditText etBirthdayDialog = dialogView.findViewById(R.id.etBirthday);
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

        // Show dialog
        new AlertDialog.Builder(this)
                .setTitle("Add Teacher")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String fullName = etFullNameDialog.getText().toString().trim();
                    String birthday = etBirthdayDialog.getText().toString().trim();
                    String email = etEmailDialog.getText().toString().trim();

                    if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(birthday) || TextUtils.isEmpty(email)) {
                        Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<CourseModel> selectedCourses = courseAdapterDialog.getSelectedCourses();
                    if (selectedCourses.isEmpty()) {
                        Toast.makeText(this, "Select at least one course", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<String> assignedSubjects = new ArrayList<>();
                    for (SubjectModel s : subjectAdapterDialog.getSelectedSubjects()) {
                        assignedSubjects.add(s.getName());
                    }

                    // Generate teacher ID and create teacher
                    generateTeacherId(teacherId -> {
                        String password = birthday.replaceAll("[^0-9]", "");
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
                                assignedSubjects,
                                password
                        );

                        auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener(authTask -> {
                                    if (authTask.isSuccessful()) {
                                        FirebaseUser firebaseUser = authTask.getResult().getUser();
                                        usersRef.child(firebaseUser.getUid()).child("role").setValue("teacher");
                                        teachersRef.child(teacherId).setValue(teacher)
                                                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Teacher added", Toast.LENGTH_SHORT).show());
                                    } else {
                                        Toast.makeText(this, "Auth failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }



    private void updateSelectedSubjects() {
        List<CourseModel> selectedCourses = courseSelectionAdapter.getSelectedCourses();

        selectedCourseSubjects.clear(); // reset subjects
        if (selectedCourses.isEmpty()) {
            subjectAdapter.updateSubjects(selectedCourseSubjects);
            return;
        }

        final int[] loadedCount = {0};
        List<SubjectModel> subjects = new ArrayList<>();

        for (CourseModel c : selectedCourses) {
            subjectsRef.orderByChild("courseId").equalTo(c.getId())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                SubjectModel s = ds.getValue(SubjectModel.class);
                                if (s != null && !subjects.contains(s)) {
                                    subjects.add(s); // just add, do NOT auto-select
                                }
                            }
                            loadedCount[0]++;
                            if (loadedCount[0] == selectedCourses.size()) {
                                selectedCourseSubjects.addAll(subjects);
                                subjectAdapter.updateSubjects(selectedCourseSubjects);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            loadedCount[0]++;
                            if (loadedCount[0] == selectedCourses.size()) {
                                selectedCourseSubjects.addAll(subjects);
                                subjectAdapter.updateSubjects(selectedCourseSubjects);
                            }
                        }
                    });
        }
    }

    private void addTeacher() {
        String fullName = etFullName.getText().toString().trim();
        String birthday = etBirthday.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(birthday) || TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        List<CourseModel> selectedCourses = courseSelectionAdapter.getSelectedCourses();
        if (selectedCourses.isEmpty()) {
            Toast.makeText(this, "Select at least one course", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get only selected subjects
        List<String> assignedSubjects = new ArrayList<>();
        for (SubjectModel s : subjectAdapter.getSelectedSubjects()) {
            assignedSubjects.add(s.getName());
        }

        if (assignedSubjects.isEmpty()) {
            Toast.makeText(this, "Select at least one subject", Toast.LENGTH_SHORT).show();
            return;
        }

        generateTeacherId(teacherId -> {
            String password = birthday.replaceAll("[^0-9]", "");

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
                    assignedSubjects,
                    password
            );

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(authTask -> {
                        if (authTask.isSuccessful()) {
                            FirebaseUser firebaseUser = authTask.getResult().getUser();
                            usersRef.child(firebaseUser.getUid()).child("role").setValue("teacher");

                            teachersRef.child(teacherId).setValue(teacher)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this, "Teacher added successfully", Toast.LENGTH_SHORT).show();
                                        etFullName.setText("");
                                        etBirthday.setText("");
                                        etEmail.setText("");
                                        selectedCourseSubjects.clear();
                                        subjectAdapter.updateSubjects(selectedCourseSubjects);
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        } else {
                            Toast.makeText(this, "Auth creation failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }


    private void fetchSelectedSubjects(List<CourseModel> selectedCourses, OnSubjectsFetchedListener listener) {
        List<SubjectModel> subjects = new ArrayList<>();
        if (selectedCourses.isEmpty()) {
            listener.onFetched(new ArrayList<>());
            return;
        }

        final int[] loadedCount = {0};
        for (CourseModel c : selectedCourses) {
            subjectsRef.orderByChild("courseId").equalTo(c.getId())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                SubjectModel s = ds.getValue(SubjectModel.class);
                                if (s != null && !subjects.contains(s)) {
                                    // ❌ REMOVE this line: s.setSelected(true);
                                    subjects.add(s);
                                }
                            }
                            loadedCount[0]++;
                            if (loadedCount[0] == selectedCourses.size()) {
                                listener.onFetched(subjectsToNames(subjects));
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            loadedCount[0]++;
                            if (loadedCount[0] == selectedCourses.size()) {
                                listener.onFetched(subjectsToNames(subjects));
                            }
                        }
                    });
        }
    }


    private List<String> subjectsToNames(List<SubjectModel> subjects) {
        List<String> names = new ArrayList<>();
        for (SubjectModel s : subjects) {
            names.add(s.getName());
        }
        return names;
    }

    interface OnSubjectsFetchedListener {
        void onFetched(List<String> assignedSubjects);
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

    // Teacher edit dialog
    private void showTeacherDialog(TeacherModel teacher) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_teacher_edit, null);

        EditText etEditFullName = dialogView.findViewById(R.id.etEditFullName);
        EditText etEditBirthday = dialogView.findViewById(R.id.etEditBirthday);
        EditText etEditEmail = dialogView.findViewById(R.id.etEditEmail);
        RecyclerView recyclerEditCourses = dialogView.findViewById(R.id.recyclerEditCourses);
        RecyclerView recyclerEditSubjects = dialogView.findViewById(R.id.recyclerEditSubjects);

        etEditFullName.setText(teacher.getFullName());
        etEditBirthday.setText(teacher.getBirthday());
        etEditEmail.setText(teacher.getEmail());

        List<CourseModel> editCourseList = new ArrayList<>();
        CourseSelectionAdapter editCourseAdapter = new CourseSelectionAdapter(this, editCourseList);
        recyclerEditCourses.setLayoutManager(new LinearLayoutManager(this));
        recyclerEditCourses.setAdapter(editCourseAdapter);

        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                editCourseList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) editCourseList.add(c);
                }
                editCourseAdapter.notifyDataSetChanged();

                // Preselect teacher courses
                if (teacher.getCourseIds() != null) {
                    editCourseAdapter.setPreselectedCoursesById(teacher.getCourseIds());
                }

                // Subjects adapter
                SubjectSelectionAdapter editSubjectAdapter = new SubjectSelectionAdapter(new ArrayList<>());
                recyclerEditSubjects.setLayoutManager(new LinearLayoutManager(TeacherActivity.this));
                recyclerEditSubjects.setAdapter(editSubjectAdapter);

                // Preselect subjects
                editSubjectAdapter.setPreselectedSubjects(teacher.getAssignedSubjects());

                // Update subjects when courses change
                editCourseAdapter.setOnCourseSelectionChanged(() -> {
                    Map<String, List<SubjectModel>> selectedCoursesWithSubjects = editCourseAdapter.getSelectedCoursesWithSubjects();
                    List<SubjectModel> combinedSubjects = new ArrayList<>();
                    for (List<SubjectModel> subjects : selectedCoursesWithSubjects.values()) {
                        for (SubjectModel s : subjects) {
                            if (!combinedSubjects.contains(s)) combinedSubjects.add(s);
                        }
                    }
                    editSubjectAdapter.updateSubjects(combinedSubjects);
                });

                editCourseAdapter.notifySelectionChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        new AlertDialog.Builder(this)
                .setTitle("Update Teacher")
                .setView(dialogView)
                .setPositiveButton("Update", (dialog, which) -> {
                    String oldEmail = teacher.getEmail();
                    String oldBirthdayPassword = teacher.getBirthday().replaceAll("[^0-9]", "");

                    teacher.setFullName(etEditFullName.getText().toString().trim());
                    teacher.setBirthday(etEditBirthday.getText().toString().trim());
                    teacher.setEmail(etEditEmail.getText().toString().trim());
                    teacher.setDisplayName(getDisplayName(teacher.getFullName()));

                    // Courses
                    List<CourseModel> updatedCourses = editCourseAdapter.getSelectedCourses();
                    List<String> courseIds = new ArrayList<>();
                    List<String> courseDisplays = new ArrayList<>();
                    for (CourseModel c : updatedCourses) {
                        courseIds.add(c.getId());
                        courseDisplays.add(
                                c.getName() + " - " +
                                        c.getSpecializationName() + " - " +
                                        c.getYearName() + " - " +
                                        c.getSectionName()
                        );

                    }
                    teacher.setCourseIds(courseIds);
                    teacher.setCourseDisplays(courseDisplays);

                    // Subjects
                    List<String> updatedSubjects = new ArrayList<>();
                    RecyclerView.Adapter<?> adapter = recyclerEditSubjects.getAdapter();
                    if (adapter instanceof SubjectSelectionAdapter) {
                        SubjectSelectionAdapter editSubjectAdapter = (SubjectSelectionAdapter) adapter;
                        for (SubjectModel s : editSubjectAdapter.getSelectedSubjects()) {
                            updatedSubjects.add(s.getName());
                        }
                    }
                    teacher.setAssignedSubjects(updatedSubjects);

                    // Save to Firebase
                    teachersRef.child(teacher.getId()).setValue(teacher)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Teacher updated", Toast.LENGTH_SHORT).show();

                                // Update Auth email/password if changed
                                if (!oldEmail.equals(teacher.getEmail()) || !oldBirthdayPassword.equals(teacher.getBirthday().replaceAll("[^0-9]", ""))) {
                                    auth.signInWithEmailAndPassword(oldEmail, oldBirthdayPassword)
                                            .addOnCompleteListener(signInTask -> {
                                                if (signInTask.isSuccessful()) {
                                                    FirebaseUser user = auth.getCurrentUser();
                                                    if (user != null) {
                                                        if (!oldEmail.equals(teacher.getEmail()))
                                                            user.updateEmail(teacher.getEmail());
                                                        if (!oldBirthdayPassword.equals(teacher.getBirthday().replaceAll("[^0-9]", "")))
                                                            user.updatePassword(teacher.getBirthday().replaceAll("[^0-9]", ""));
                                                    }
                                                }
                                            });
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

