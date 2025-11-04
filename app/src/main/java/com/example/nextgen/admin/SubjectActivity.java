package com.example.nextgen.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class SubjectActivity extends AppCompatActivity {

    private EditText etSubjectCode, etSubjectName;
    private Spinner spinnerCourses;
    private Button btnAddSubject;
    private RecyclerView recyclerSubjects;

    private SubjectAdapter adapter;
    private final List<SubjectModel> subjectList = new ArrayList<>();
    private final List<SubjectOption> subjectOptionList = new ArrayList<>();

    private DatabaseReference subjectsRef, coursesRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject);

        // UI
        etSubjectCode = findViewById(R.id.etSubjectCode);
        etSubjectName = findViewById(R.id.etSubjectName);
        spinnerCourses = findViewById(R.id.spinnerCourseOption);
        btnAddSubject = findViewById(R.id.btnAddSubject);
        recyclerSubjects = findViewById(R.id.recyclerSubjects);

        // RecyclerView setup
        recyclerSubjects.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectAdapter(subjectList, new SubjectAdapter.OnSubjectActionListener() {
            @Override
            public void onEdit(SubjectModel subject) {
                showEditDialog(subject);
            }

            @Override
            public void onDelete(SubjectModel subject) {
                new androidx.appcompat.app.AlertDialog.Builder(SubjectActivity.this)
                        .setTitle("Delete Subject")
                        .setMessage("Are you sure you want to delete " + subject.getName() + "?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            FirebaseDatabase.getInstance().getReference("Subjects")
                                    .child(subject.getId())
                                    .removeValue()
                                    .addOnSuccessListener(aVoid ->
                                            Toast.makeText(SubjectActivity.this, "Deleted successfully", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e ->
                                            Toast.makeText(SubjectActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });



        recyclerSubjects.setAdapter(adapter);

        // Firebase
        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");

        // Load spinner options and existing subjects
        loadSubjectOptions();
        loadSubjects();

        // Add subject
        btnAddSubject.setOnClickListener(v -> addSubject());
    }

    private void loadSubjectOptions() {
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                subjectOptionList.clear();
                List<String> displayNames = new ArrayList<>();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel course = ds.getValue(CourseModel.class);
                    if (course != null) {
                        SubjectOption option = new SubjectOption(
                                course.getId(),
                                course.getName(),
                                course.getSpecializationName(),
                                course.getYearName(),
                                course.getSectionName()
                        );
                        subjectOptionList.add(option);
                        displayNames.add(option.toString());
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(SubjectActivity.this,
                        android.R.layout.simple_spinner_item, displayNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCourses.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void addSubject() {
        String code = etSubjectCode.getText().toString().trim();
        String name = etSubjectName.getText().toString().trim();

        if (TextUtils.isEmpty(code)) {
            etSubjectCode.setError("Enter subject code");
            etSubjectCode.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(name)) {
            etSubjectName.setError("Enter subject name");
            etSubjectName.requestFocus();
            return;
        }

        int pos = spinnerCourses.getSelectedItemPosition();
        if (pos < 0) {
            Toast.makeText(this, "Select a course option", Toast.LENGTH_SHORT).show();
            return;
        }

        SubjectOption selectedOption = subjectOptionList.get(pos);

        String id = subjectsRef.push().getKey();
        if (id == null) {
            Toast.makeText(this, "Error generating ID", Toast.LENGTH_SHORT).show();
            return;
        }

        SubjectModel subject = new SubjectModel(
                id,
                code,
                name,
                selectedOption.getCourseId(),
                selectedOption.getCourseName(),
                selectedOption.getSpecializationName(),
                selectedOption.getYearName(),
                selectedOption.getSectionName()
        );

        subjectsRef.child(id).setValue(subject)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Subject added successfully", Toast.LENGTH_SHORT).show();
                    etSubjectCode.setText("");
                    etSubjectName.setText("");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void loadSubjects() {
        subjectsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                subjectList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    SubjectModel s = ds.getValue(SubjectModel.class);
                    if (s != null) subjectList.add(s);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }
    private void showEditDialog(SubjectModel subject) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Subject");

        // Create input fields
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_subject, null);
        EditText etEditCode = dialogView.findViewById(R.id.etEditSubjectCode);
        EditText etEditName = dialogView.findViewById(R.id.etEditSubjectName);

        etEditCode.setText(subject.getCode());
        etEditName.setText(subject.getName());

        builder.setView(dialogView);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String newCode = etEditCode.getText().toString().trim();
            String newName = etEditName.getText().toString().trim();

            if (newCode.isEmpty() || newName.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Subjects").child(subject.getId());
            ref.child("code").setValue(newCode);
            ref.child("name").setValue(newName)
                    .addOnSuccessListener(aVoid ->
                            Toast.makeText(this, "Subject updated successfully", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
