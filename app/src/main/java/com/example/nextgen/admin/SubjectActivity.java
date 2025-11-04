package com.example.nextgen.admin;

import android.os.Bundle;
import android.text.TextUtils;
import androidx.appcompat.app.AlertDialog;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
        btnAddSubject.setOnClickListener(v -> showAddSubjectDialog());
    }

    private void loadSubjectOptions() {
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                subjectOptionList.clear();
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
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }


    private void showAddSubjectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Subject");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_subject, null);
        EditText etCode = dialogView.findViewById(R.id.etDialogSubjectCode);
        EditText etName = dialogView.findViewById(R.id.etDialogSubjectName);
        Spinner spinnerDialog = dialogView.findViewById(R.id.spinnerDialogCourses);

        // Populate spinner
        List<String> displayNames = new ArrayList<>();
        for (SubjectOption option : subjectOptionList) {
            displayNames.add(option.toString());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDialog.setAdapter(adapter);

        builder.setView(dialogView);

        builder.setPositiveButton("Add", (dialogInterface, i) -> {
            String code = etCode.getText().toString().trim();
            String name = etName.getText().toString().trim();

            if (code.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            int pos = spinnerDialog.getSelectedItemPosition();
            if (pos < 0) {
                Toast.makeText(this, "Select a course", Toast.LENGTH_SHORT).show();
                return;
            }

            SubjectOption selectedOption = subjectOptionList.get(pos);
            String id = subjectsRef.push().getKey();
            if (id == null) return;

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
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Subject added", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);  // Modal: can't dismiss with back button
        dialog.setCanceledOnTouchOutside(false); // Modal: can't tap outside
        dialog.show();
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

        // Inflate dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_subject, null);
        EditText etEditCode = dialogView.findViewById(R.id.etEditSubjectCode);
        EditText etEditName = dialogView.findViewById(R.id.etEditSubjectName);
        Spinner spinnerDialog = dialogView.findViewById(R.id.spinnerEditCourses); // Add spinner in layout

        etEditCode.setText(subject.getCode());
        etEditName.setText(subject.getName());

        // Populate spinner
        List<String> displayNames = new ArrayList<>();
        int selectedIndex = -1;
        for (int i = 0; i < subjectOptionList.size(); i++) {
            SubjectOption option = subjectOptionList.get(i);
            displayNames.add(option.toString());
            if (option.getCourseId().equals(subject.getCourseId())) {
                selectedIndex = i;
            }
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, displayNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDialog.setAdapter(spinnerAdapter);
        if (selectedIndex >= 0) spinnerDialog.setSelection(selectedIndex);

        builder.setView(dialogView);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String newCode = etEditCode.getText().toString().trim();
            String newName = etEditName.getText().toString().trim();

            if (newCode.isEmpty() || newName.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            int pos = spinnerDialog.getSelectedItemPosition();
            if (pos < 0) {
                Toast.makeText(this, "Select a course", Toast.LENGTH_SHORT).show();
                return;
            }
            SubjectOption selectedOption = subjectOptionList.get(pos);

            // Update Firebase
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Subjects").child(subject.getId());
            ref.setValue(new SubjectModel(
                    subject.getId(),
                    newCode,
                    newName,
                    selectedOption.getCourseId(),
                    selectedOption.getCourseName(),
                    selectedOption.getSpecializationName(),
                    selectedOption.getYearName(),
                    selectedOption.getSectionName()
            )).addOnSuccessListener(aVoid ->
                    Toast.makeText(this, "Subject updated successfully", Toast.LENGTH_SHORT).show()
            ).addOnFailureListener(e ->
                    Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
            );
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }


}
