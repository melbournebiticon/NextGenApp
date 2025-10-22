package com.example.nextgen.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
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
import java.util.List;// === Optional: Destroy instance if needed ===

public class TeacherActivity extends AppCompatActivity {

    private EditText etFullName, etBirthday, etEmail;
    private Spinner spinnerCourses;
    private RecyclerView recyclerSubjects, recyclerTeachers;
    private Button btnAddTeacher;

    private List<SubjectModel> selectedCourseSubjects = new ArrayList<>();
    private List<CourseModel> courseOptionList = new ArrayList<>();
    private List<TeacherModel> teacherList = new ArrayList<>();

    private DatabaseReference teachersRef, coursesRef, subjectsRef, usersRef;
    private FirebaseAuth auth;

    private SubjectSelectionAdapter subjectAdapter; // ✅ use selection adapter
    private TeacherAdapter teacherAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher);

        // Initialize UI
        etFullName = findViewById(R.id.etFullName);
        etBirthday = findViewById(R.id.etBirthday);
        etEmail = findViewById(R.id.etEmail);
        spinnerCourses = findViewById(R.id.spinnerCourses);
        recyclerSubjects = findViewById(R.id.recyclerSubjects);
        btnAddTeacher = findViewById(R.id.btnAddTeacher);

        recyclerTeachers = findViewById(R.id.recyclerTeachers);
        recyclerTeachers.setLayoutManager(new LinearLayoutManager(this));

        // Firebase references
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        auth = FirebaseAuth.getInstance();

        // 🔹 TeacherAdapter with full action listener (Update + Delete)
        teacherAdapter = new TeacherAdapter(teacherList, new TeacherAdapter.OnTeacherActionListener() {
            @Override
            public void onUpdate(TeacherModel teacher) {
                showTeacherDialog(teacher); // opens dialog for editing
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

        // Load courses
        loadCourses();

        // Spinner listener → load subjects
        spinnerCourses.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CourseModel selectedCourse = courseOptionList.get(position);
                loadSubjectsByCourse(selectedCourse.getId());
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Add teacher button
        btnAddTeacher.setOnClickListener(v -> addTeacher());
    }

    private void loadCourses() {
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                List<String> displayNames = new ArrayList<>();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel course = ds.getValue(CourseModel.class);
                    if (course != null) {
                        courseOptionList.add(course);
                        displayNames.add(course.getName() + " - " +
                                course.getSpecializationName() + " - " +
                                course.getYearName() + " - " +
                                course.getSectionName());
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        TeacherActivity.this,
                        android.R.layout.simple_spinner_item,
                        displayNames
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCourses.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadSubjectsByCourse(String courseId) {
        subjectsRef.orderByChild("courseId").equalTo(courseId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        selectedCourseSubjects.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            SubjectModel s = ds.getValue(SubjectModel.class);
                            if (s != null) selectedCourseSubjects.add(s);
                        }

                        subjectAdapter = new SubjectSelectionAdapter(selectedCourseSubjects);
                        recyclerSubjects.setLayoutManager(new LinearLayoutManager(TeacherActivity.this));
                        recyclerSubjects.setAdapter(subjectAdapter);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void addTeacher() {
        String fullName = etFullName.getText().toString().trim();
        String birthday = etBirthday.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        int coursePos = spinnerCourses.getSelectedItemPosition();

        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(birthday) ||
                TextUtils.isEmpty(email) || coursePos < 0) {
            Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // You can later change this to allow multiple course selections
        CourseModel selectedCourse = courseOptionList.get(coursePos);
        List<String> courseIds = new ArrayList<>();
        List<String> courseDisplays = new ArrayList<>();

        courseIds.add(selectedCourse.getId());
        courseDisplays.add(selectedCourse.getName() + " - " +
                selectedCourse.getSpecializationName() + " - " +
                selectedCourse.getYearName() + " - " +
                selectedCourse.getSectionName());

        List<String> assignedSubjects = new ArrayList<>();
        if (subjectAdapter != null) {
            for (SubjectModel s : subjectAdapter.getSelectedSubjects()) {
                assignedSubjects.add(s.getName());
            }
        }

        generateTeacherId(teacherId -> {
            String password = birthday.replaceAll("[^0-9]", "");

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
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        } else {
                            Toast.makeText(this, "Auth creation failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
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
                    String id = ds.getKey(); // "TCHR-0001"
                    if (id != null && id.startsWith("TCHR-")) {
                        try {
                            int num = Integer.parseInt(id.replace("TCHR-", ""));
                            numbers.add(num);
                        } catch (NumberFormatException ignored) {}
                    }
                }

                int newNum = 1;
                while (numbers.contains(newNum)) {
                    newNum++;
                }

                String newId = String.format("TCHR-%04d", newNum);
                listener.onGenerated(newId);
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

    // 🔹 Update teacher dialog
    private void showTeacherDialog(TeacherModel teacher) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_teacher_edit, null);

        EditText etEditFullName = dialogView.findViewById(R.id.etEditFullName);
        EditText etEditBirthday = dialogView.findViewById(R.id.etEditBirthday);
        EditText etEditEmail = dialogView.findViewById(R.id.etEditEmail);
        Spinner spinnerEditCourses = dialogView.findViewById(R.id.spinnerEditCourses);
        RecyclerView recyclerEditSubjects = dialogView.findViewById(R.id.recyclerEditSubjects);

        etEditFullName.setText(teacher.getFullName());
        etEditBirthday.setText(teacher.getBirthday());
        etEditEmail.setText(teacher.getEmail());

        List<CourseModel> editCourseList = new ArrayList<>();
        ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEditCourses.setAdapter(courseAdapter);

        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                editCourseList.clear();
                List<String> displayNames = new ArrayList<>();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) {
                        editCourseList.add(c);
                        displayNames.add(c.getName() + " - " + c.getSpecializationName() + " - " +
                                c.getYearName() + " - " + c.getSectionName());
                    }
                }

                courseAdapter.clear();
                courseAdapter.addAll(displayNames);

                // ✅ Set spinner selection to one of the teacher’s assigned courses
                if (teacher.getCourseIds() != null) {
                    for (String courseId : teacher.getCourseIds()) {
                        for (int i = 0; i < editCourseList.size(); i++) {
                            if (editCourseList.get(i).getId().equals(courseId)) {
                                spinnerEditCourses.setSelection(i); // highlight matching course
                                break; // stop when matched
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });


        final SubjectSelectionAdapter[] editAdapter = new SubjectSelectionAdapter[1];
        spinnerEditCourses.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < editCourseList.size()) {
                    CourseModel selectedCourse = editCourseList.get(pos);
                    subjectsRef.orderByChild("courseId").equalTo(selectedCourse.getId())
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    List<SubjectModel> editSubjects = new ArrayList<>();
                                    for (DataSnapshot ds : snapshot.getChildren()) {
                                        SubjectModel s = ds.getValue(SubjectModel.class);
                                        if (s != null) editSubjects.add(s);
                                    }
                                    editAdapter[0] = new SubjectSelectionAdapter(editSubjects);
                                    recyclerEditSubjects.setLayoutManager(new LinearLayoutManager(TeacherActivity.this));
                                    recyclerEditSubjects.setAdapter(editAdapter[0]);
                                    editAdapter[0].setPreselectedSubjects(teacher.getAssignedSubjects());
                                }
                                @Override public void onCancelled(@NonNull DatabaseError error) {}
                            });
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
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

                    int coursePos = spinnerEditCourses.getSelectedItemPosition();
                    if (coursePos >= 0 && coursePos < editCourseList.size()) {
                        CourseModel selectedCourse = editCourseList.get(coursePos);
                        if (teacher.getCourseIds() == null) {
                            teacher.setCourseIds(new ArrayList<>());
                        }
                        if (!teacher.getCourseIds().contains(selectedCourse.getId())) {
                            teacher.getCourseIds().add(selectedCourse.getId());
                        }

                        if (teacher.getCourseDisplays() == null) {
                            teacher.setCourseDisplays(new ArrayList<>());
                        }

                        String displayName = selectedCourse.getName() + " - " +
                                selectedCourse.getSpecializationName() + " - " +
                                selectedCourse.getYearName() + " - " +
                                selectedCourse.getSectionName();

                        if (!teacher.getCourseDisplays().contains(displayName)) {
                            teacher.getCourseDisplays().add(displayName);
                        }

                    }

                    if (editAdapter[0] != null) {
                        List<String> updatedSubjects = new ArrayList<>();
                        for (SubjectModel s : editAdapter[0].getSelectedSubjects()) {
                            updatedSubjects.add(s.getName());
                        }
                        teacher.setAssignedSubjects(updatedSubjects);
                    }

                    teachersRef.child(teacher.getId()).setValue(teacher)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Teacher updated", Toast.LENGTH_SHORT).show();

                                // 🔹 Update Firebase Auth if email or birthday (password) changed
                                if (!oldEmail.equals(teacher.getEmail()) || !oldBirthdayPassword.equals(teacher.getBirthday().replaceAll("[^0-9]", ""))) {
                                    auth.signInWithEmailAndPassword(oldEmail, oldBirthdayPassword)
                                            .addOnCompleteListener(signInTask -> {
                                                if (signInTask.isSuccessful()) {
                                                    FirebaseUser user = auth.getCurrentUser();
                                                    if (user != null) {
                                                        if (!oldEmail.equals(teacher.getEmail())) {
                                                            user.updateEmail(teacher.getEmail());
                                                        }
                                                        if (!oldBirthdayPassword.equals(teacher.getBirthday().replaceAll("[^0-9]", ""))) {
                                                            user.updatePassword(teacher.getBirthday().replaceAll("[^0-9]", ""));
                                                        }
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