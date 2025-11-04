package com.example.nextgen.admin;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

public class CourseAdapter extends RecyclerView.Adapter<CourseAdapter.ViewHolder> {

    private final List<CourseModel> courseList;
    private final Context context;

    public CourseAdapter(Context context, List<CourseModel> courseList) {
        this.context = context;
        this.courseList = courseList;
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
        builder.setTitle("Edit Course Name");

        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(course.getCourseName());
        builder.setView(input);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(context, "Course name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Courses");
            ref.child(course.getId()).child("courseName").setValue(newName)
                    .addOnSuccessListener(aVoid -> {
                        course.setCourseName(newName);
                        notifyItemChanged(position);
                        Toast.makeText(context, "Course updated", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e ->
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
