package com.example.nextgen.admin;

import android.app.AlertDialog;
import android.content.Context;
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

public class CourseAdapter extends RecyclerView.Adapter<CourseAdapter.ViewHolder> {

    private final List<CourseModel> courseList;
    private final Context context;
    private List<CourseOption> courseOptionList = new ArrayList<>();

    public CourseAdapter(Context context, List<CourseModel> courseList) {
        this.context = context;
        this.courseList = courseList;
        loadCourseOptions();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_course, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CourseModel course = courseList.get(position);

        // Display course info
        holder.tvCourseName.setText(course.getCourseName());

        // Display course details
        String details = course.getSpecializationName() + " - " +
                course.getYearName() + " - " +
                course.getSectionName();
        holder.tvCourseDetails.setText(details);

        // Edit button click
        holder.btnEditCourse.setOnClickListener(v -> showEditDialog(course, position));

        // Delete button click
        holder.btnDeleteCourse.setOnClickListener(v -> showDeleteDialog(course, position));
    }

    private void loadCourseOptions() {
        DatabaseReference courseOptionsRef = FirebaseDatabase.getInstance().getReference("CourseOptions");
        courseOptionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseOption option = ds.getValue(CourseOption.class);
                    if (option != null) {
                        courseOptionList.add(option);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(context, "Failed to load course options", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditDialog(CourseModel course, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_edit_course, null);
        builder.setView(dialogView);

        EditText etCourseName = dialogView.findViewById(R.id.etCourseName);
        Spinner spinnerSections = dialogView.findViewById(R.id.spinnerSection);
        Button btnUpdate = dialogView.findViewById(R.id.btnUpdateCourse);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelCourse);

        // Pre-fill current values
        etCourseName.setText(course.getCourseName());

        // Load course options for spinner
        loadCourseOptionsForDialog(spinnerSections, course);

        AlertDialog dialog = builder.create();
        dialog.show();

        btnUpdate.setOnClickListener(v -> {
            String newName = etCourseName.getText().toString().trim();
            if (newName.isEmpty()) {
                etCourseName.setError("Enter course name");
                return;
            }

            int selectedPos = spinnerSections.getSelectedItemPosition();
            if (selectedPos < 0 || courseOptionList.isEmpty()) {
                Toast.makeText(context, "Select a course option", Toast.LENGTH_SHORT).show();
                return;
            }

            CourseOption selectedOption = courseOptionList.get(selectedPos);
            updateCourse(course, newName, selectedOption, position);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
    }

    private void loadCourseOptionsForDialog(Spinner spinner, CourseModel currentCourse) {
        List<String> names = new ArrayList<>();
        int selectedPosition = 0;

        for (int i = 0; i < courseOptionList.size(); i++) {
            CourseOption option = courseOptionList.get(i);
            String displayName = option.getSpecializationName() + " - " +
                    option.getSectionName() + " - " +
                    option.getYearName();
            names.add(displayName);

            // Pre-select the current course option
            if (option.getSectionId().equals(currentCourse.getSectionId()) &&
                    option.getSpecializationId().equals(currentCourse.getSpecializationId()) &&
                    option.getYearId().equals(currentCourse.getYearId())) {
                selectedPosition = i;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selectedPosition);
    }

    private void updateCourse(CourseModel course, String newName, CourseOption selectedOption, int position) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Courses");

        // Update all fields
        ref.child(course.getId()).child("courseName").setValue(newName);
        ref.child(course.getId()).child("specializationId").setValue(selectedOption.getSpecializationId());
        ref.child(course.getId()).child("specializationName").setValue(selectedOption.getSpecializationName());
        ref.child(course.getId()).child("yearId").setValue(selectedOption.getYearId());
        ref.child(course.getId()).child("yearName").setValue(selectedOption.getYearName());
        ref.child(course.getId()).child("sectionId").setValue(selectedOption.getSectionId());
        ref.child(course.getId()).child("sectionName").setValue(selectedOption.getSectionName())
                .addOnSuccessListener(aVoid -> {
                    // Update local data
                    course.setCourseName(newName);
                    course.setSpecializationId(selectedOption.getSpecializationId());
                    course.setSpecializationName(selectedOption.getSpecializationName());
                    course.setYearId(selectedOption.getYearId());
                    course.setYearName(selectedOption.getYearName());
                    course.setSectionId(selectedOption.getSectionId());
                    course.setSectionName(selectedOption.getSectionName());

                    notifyItemChanged(position);
                    Toast.makeText(context, "Course updated successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showDeleteDialog(CourseModel course, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Course")
                .setMessage("Are you sure you want to delete " + course.getCourseName() + "?")
                .setPositiveButton("Yes", (dialog, which) -> deleteCourse(course.getId(), position))
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteCourse(String courseId, int position) {
        if (courseId == null || position < 0 || position >= courseList.size()) return;

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Courses");
        ref.child(courseId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    courseList.remove(position);
                    notifyItemRemoved(position);
                    Toast.makeText(context, "Course deleted successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public int getItemCount() {
        return courseList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCourseName, tvCourseDetails;
        ImageButton btnEditCourse, btnDeleteCourse;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCourseName = itemView.findViewById(R.id.tvCourseName);
            tvCourseDetails = itemView.findViewById(R.id.tvCourseDetails);
            btnEditCourse = itemView.findViewById(R.id.btnEditCourse);
            btnDeleteCourse = itemView.findViewById(R.id.btnDeleteCourse);
        }
    }
}