package com.example.nextgen.admin;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Spinner;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;
import java.util.ArrayList;



public class CourseAdapter extends RecyclerView.Adapter<CourseAdapter.ViewHolder> {

    private final List<CourseModel> courseList;
    private final Context context;

    private final List<CourseOption> courseOptionList;

    public CourseAdapter(Context context, List<CourseModel> courseList, List<CourseOption> courseOptionList) {
        this.context = context;
        this.courseList = courseList;
        this.courseOptionList = courseOptionList;
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
        String display = course.getCourseName() + " - "
                + course.getSpecializationName() + " - "
                + course.getYearName() + " - "
                + course.getSectionName();

        holder.tvCourseName.setText(display);

        // Edit button click
        holder.btnEdit.setOnClickListener(v -> showEditDialog(course, position));

        // Delete button click
        holder.btnDelete.setOnClickListener(v -> showDeleteDialog(course, position));
    }

    private void showEditDialog(CourseModel course, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit Course");

        // Inflate a custom view similar to the add dialog
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_course, null);
        EditText etCourseName = dialogView.findViewById(R.id.etCourseName);
        Spinner spinnerSections = dialogView.findViewById(R.id.spinnerSection);

        // Prefill course name
        etCourseName.setText(course.getCourseName());

        // Populate spinner options (you need to pass courseOptionList from Activity or make it accessible)
        List<String> spinnerNames = new ArrayList<>();
        int selectedIndex = -1;
        for (int i = 0; i < courseOptionList.size(); i++) {
            CourseOption option = courseOptionList.get(i);
            spinnerNames.add(option.getSpecializationName() + " - " +
                    option.getSectionName() + " - " +
                    option.getYearName());

            // Preselect current course option
            if (option.getSectionId().equals(course.getSectionId()) &&
                    option.getYearId().equals(course.getYearId()) &&
                    option.getSpecializationId().equals(course.getSpecializationId())) {
                selectedIndex = i;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, spinnerNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSections.setAdapter(adapter);
        if (selectedIndex >= 0) spinnerSections.setSelection(selectedIndex);

        builder.setView(dialogView);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String newName = etCourseName.getText().toString().trim();
            int selectedPos = spinnerSections.getSelectedItemPosition();

            if (newName.isEmpty()) {
                Toast.makeText(context, "Course name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedPos < 0) {
                Toast.makeText(context, "Select a course option", Toast.LENGTH_SHORT).show();
                return;
            }

            CourseOption selectedOption = this.courseOptionList
                    .get(selectedPos);

            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Courses");
            ref.child(course.getId()).setValue(new CourseModel(
                    course.getId(),
                    newName,
                    selectedOption.getSpecializationId(),
                    selectedOption.getSpecializationName(),
                    selectedOption.getYearId(),
                    selectedOption.getYearName(),
                    selectedOption.getSectionId(),
                    selectedOption.getSectionName()
            )).addOnSuccessListener(aVoid -> {
                course.setCourseName(newName);
                course.setSpecializationId(selectedOption.getSpecializationId());
                course.setSpecializationName(selectedOption.getSpecializationName());
                course.setYearId(selectedOption.getYearId());
                course.setYearName(selectedOption.getYearName());
                course.setSectionId(selectedOption.getSectionId());
                course.setSectionName(selectedOption.getSectionName());

                notifyItemChanged(position);
                Toast.makeText(context, "Course updated successfully", Toast.LENGTH_SHORT).show();
            }).addOnFailureListener(e ->
                    Toast.makeText(context, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
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
        if (courseId == null) return;

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Courses");
        ref.child(courseId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    // Safely remove only if still valid and not already updated elsewhere
                    if (position >= 0 && position < courseList.size()) {
                        courseList.remove(position);
                        notifyItemRemoved(position);
                    } else {
                        // Fallback: refresh entire list if index invalid
                        notifyDataSetChanged();
                    }

                    Toast.makeText(context, "Course deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }


    @Override
    public int getItemCount() {
        return courseList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCourseName;
        ImageButton btnEdit, btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCourseName = itemView.findViewById(R.id.tvCourseName);
            btnEdit = itemView.findViewById(R.id.btnEditCourse);
            btnDelete = itemView.findViewById(R.id.btnDeleteCourse);
        }
    }
}
