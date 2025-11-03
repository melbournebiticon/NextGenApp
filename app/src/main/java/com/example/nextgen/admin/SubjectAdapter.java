package com.example.nextgen.admin;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.ViewHolder> {

    private final List<SubjectModel> subjectList;
    private final Context context;
    private List<SubjectOption> subjectOptionList = new ArrayList<>();

    // REMOVED THE CONSTRUCTOR WITHOUT CONTEXT
    // public SubjectAdapter(List<SubjectModel> subjectList) {
    //     this.subjectList = subjectList;
    //     this.context = null;
    // }

    public SubjectAdapter(Context context, List<SubjectModel> subjectList) {
        this.context = context;
        this.subjectList = subjectList;
        loadCourseOptions(); // Load course options when adapter is created
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SubjectModel subject = subjectList.get(position);

        // Display subject info
        holder.tvSubjectCode.setText(subject.getCode());
        holder.tvSubjectName.setText(subject.getName());

        // Display course details
        String details = subject.getCourseName() + " - " +
                subject.getSpecializationName() + " - " +
                subject.getYearName() + " - " +
                subject.getSectionName();
        holder.tvCourseDetails.setText(details);

        // Edit button click
        holder.btnEditSubject.setOnClickListener(v -> {
            showEditDialog(subject, position);
        });

        // Delete button click
        holder.btnDeleteSubject.setOnClickListener(v -> {
            showDeleteDialog(subject, position);
        });
    }

    private void loadCourseOptions() {
        DatabaseReference coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                subjectOptionList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel course = ds.getValue(CourseModel.class);
                    if (course != null) {
                        SubjectOption option = new SubjectOption(
                                course.getId(),
                                course.getCourseName(),
                                course.getSpecializationName(),
                                course.getYearName(),
                                course.getSectionName()
                        );
                        subjectOptionList.add(option);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(context, "Failed to load courses", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditDialog(SubjectModel subject, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_edit_subject, null);
        builder.setView(dialogView);

        EditText etSubjectCode = dialogView.findViewById(R.id.etSubjectCode);
        EditText etSubjectName = dialogView.findViewById(R.id.etSubjectName);
        Spinner spinnerCourses = dialogView.findViewById(R.id.spinnerCourseOption);
        Button btnUpdate = dialogView.findViewById(R.id.btnUpdateSubject);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelSubject);

        // Pre-fill current values
        etSubjectCode.setText(subject.getCode());
        etSubjectName.setText(subject.getName());

        // Load course options for spinner
        loadCourseOptionsForDialog(spinnerCourses, subject);

        AlertDialog dialog = builder.create();
        dialog.show();

        btnUpdate.setOnClickListener(v -> {
            String newCode = etSubjectCode.getText().toString().trim();
            String newName = etSubjectName.getText().toString().trim();

            if (TextUtils.isEmpty(newCode)) {
                etSubjectCode.setError("Enter subject code");
                return;
            }
            if (TextUtils.isEmpty(newName)) {
                etSubjectName.setError("Enter subject name");
                return;
            }

            int selectedPos = spinnerCourses.getSelectedItemPosition();
            if (selectedPos < 0 || subjectOptionList.isEmpty()) {
                Toast.makeText(context, "Select a course option", Toast.LENGTH_SHORT).show();
                return;
            }

            SubjectOption selectedOption = subjectOptionList.get(selectedPos);
            updateSubject(subject, newCode, newName, selectedOption, position);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
    }

    private void loadCourseOptionsForDialog(Spinner spinner, SubjectModel currentSubject) {
        List<String> names = new ArrayList<>();
        int selectedPosition = 0;

        for (int i = 0; i < subjectOptionList.size(); i++) {
            SubjectOption option = subjectOptionList.get(i);
            String displayName = option.toString();
            names.add(displayName);

            // Pre-select the current course option
            if (option.getCourseId().equals(currentSubject.getCourseId())) {
                selectedPosition = i;
            }
        }

        if (names.isEmpty()) {
            names.add("No courses available");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        if (!names.isEmpty() && selectedPosition < names.size()) {
            spinner.setSelection(selectedPosition);
        }
    }

    private void updateSubject(SubjectModel subject, String newCode, String newName, SubjectOption selectedOption, int position) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Subjects");

        // Update all fields
        ref.child(subject.getId()).child("code").setValue(newCode);
        ref.child(subject.getId()).child("name").setValue(newName);
        ref.child(subject.getId()).child("courseId").setValue(selectedOption.getCourseId());
        ref.child(subject.getId()).child("courseName").setValue(selectedOption.getCourseName());
        ref.child(subject.getId()).child("specializationName").setValue(selectedOption.getSpecializationName());
        ref.child(subject.getId()).child("yearName").setValue(selectedOption.getYearName());
        ref.child(subject.getId()).child("sectionName").setValue(selectedOption.getSectionName())
                .addOnSuccessListener(aVoid -> {
                    // Update local data
                    subject.setCode(newCode);
                    subject.setName(newName);
                    subject.setCourseId(selectedOption.getCourseId());
                    subject.setCourseName(selectedOption.getCourseName());
                    subject.setSpecializationName(selectedOption.getSpecializationName());
                    subject.setYearName(selectedOption.getYearName());
                    subject.setSectionName(selectedOption.getSectionName());

                    notifyItemChanged(position);
                    Toast.makeText(context, "Subject updated successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showDeleteDialog(SubjectModel subject, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Subject")
                .setMessage("Are you sure you want to delete " + subject.getName() + " (" + subject.getCode() + ")?")
                .setPositiveButton("Yes", (dialog, which) -> deleteSubject(subject.getId(), position))
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteSubject(String subjectId, int position) {
        if (subjectId == null || position < 0 || position >= subjectList.size()) return;

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Subjects");
        ref.child(subjectId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    subjectList.remove(position);
                    notifyItemRemoved(position);
                    Toast.makeText(context, "Subject deleted successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public int getItemCount() {
        return subjectList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSubjectCode, tvSubjectName, tvCourseDetails;
        ImageButton btnEditSubject, btnDeleteSubject;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSubjectCode = itemView.findViewById(R.id.tvSubjectCode);
            tvSubjectName = itemView.findViewById(R.id.tvSubjectName);
            tvCourseDetails = itemView.findViewById(R.id.tvCourseDetails);
            btnEditSubject = itemView.findViewById(R.id.btnEditSubject);
            btnDeleteSubject = itemView.findViewById(R.id.btnDeleteSubject);
        }
    }
}