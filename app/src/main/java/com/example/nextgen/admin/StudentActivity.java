package com.example.nextgen.admin;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;


public class StudentActivity extends AppCompatActivity {
    private Spinner spEditCourse;
    private Uri selectedImageUri;
    private ImageView currentEditProfileView;


    private EditText etFullName, etBirthday, etEmail, etContact;

    private RecyclerView recyclerStudents;
    private Button btnAddStudent;

    private List<CourseModel> courseOptionList = new ArrayList<>();
    private List<StudentModel> studentList = new ArrayList<>();

    private DatabaseReference studentsRef, coursesRef, usersRef;
    private FirebaseAuth auth;

    private StudentAdapter studentAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student);

        recyclerStudents = findViewById(R.id.recyclerStudents);
        btnAddStudent = findViewById(R.id.btnAddStudent);




        recyclerStudents.setLayoutManager(new LinearLayoutManager(this));

        // Firebase
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        auth = FirebaseAuth.getInstance();

        studentAdapter = new StudentAdapter(studentList, new StudentAdapter.OnStudentActionListener() {
            @Override
            public void onUpdate(StudentModel student) {
                showStudentDialog(student);
            }

            @Override
            public void onDelete(StudentModel student) {
                new AlertDialog.Builder(StudentActivity.this)
                        .setTitle("Delete Student")
                        .setMessage("Are you sure you want to delete " + student.getFullName() + "?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // First, delete from Users node
                            if (student.getUid() != null && !student.getUid().isEmpty()) {
                                usersRef.child(student.getUid()).removeValue()
                                        .addOnSuccessListener(aVoid -> {
                                            // Optional: Delete from Firebase Auth (only if admin has access)
                                            FirebaseUser currentUser = auth.getCurrentUser();
                                            if (currentUser != null && currentUser.getUid().equals(student.getUid())) {
                                                currentUser.delete()
                                                        .addOnSuccessListener(aVoid2 ->
                                                                Toast.makeText(StudentActivity.this, "Auth user deleted", Toast.LENGTH_SHORT).show())
                                                        .addOnFailureListener(e ->
                                                                Toast.makeText(StudentActivity.this, "Auth delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                            }
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(StudentActivity.this, "Failed to delete user record: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                            }

                            // Then, delete from Students node
                            studentsRef.child(student.getStudentId()).removeValue()
                                    .addOnSuccessListener(aVoid ->
                                            Toast.makeText(StudentActivity.this, "Student deleted", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e ->
                                            Toast.makeText(StudentActivity.this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }



        });

        recyclerStudents.setAdapter(studentAdapter);

        // Load students
        studentsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                studentList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StudentModel s = ds.getValue(StudentModel.class);
                    if (s != null) studentList.add(s);
                }
                studentAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });


        btnAddStudent.setOnClickListener(v -> addStudentDialog());

    }

    private void loadCourses() {
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                List<String> displayNames = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) {
                        courseOptionList.add(c);
                        displayNames.add(c.getName() + " - " +
                                c.getSpecializationName() + " - " +
                                c.getYearName() + " - " +
                                c.getSectionName());
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(StudentActivity.this,
                        android.R.layout.simple_spinner_item, displayNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void addStudentDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_student_add, null);

        EditText etFullName = dialogView.findViewById(R.id.etFullName);
        EditText etBirthday = dialogView.findViewById(R.id.etBirthday);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etContact = dialogView.findViewById(R.id.etContact);
        Spinner spCourse = dialogView.findViewById(R.id.spinnerCourses);
        ImageView ivProfile = dialogView.findViewById(R.id.ivProfile);

        // Birthday picker
        etBirthday.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog picker = new DatePickerDialog(this, (view, y, m, d) -> {
                etBirthday.setText(String.format("%04d-%02d-%02d", y, m+1, d));
            }, year, month, day);
            picker.show();
        });

        // Load courses dynamically from Firebase
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                List<String> courseNames = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) {
                        courseOptionList.add(c);
                        courseNames.add(c.getCourseName() + " - " +
                                c.getSpecializationName() + " - " +
                                c.getYearName() + " - " +
                                c.getSectionName());
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        StudentActivity.this,
                        android.R.layout.simple_spinner_item,
                        courseNames
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spCourse.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });


        // Create AlertDialog
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle("Add Student")
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .create();

        dialog.show();

        // Override positive button to prevent auto-dismiss
        Button btnAdd = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        btnAdd.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String birthday = etBirthday.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String contact = etContact.getText().toString().trim();
            int coursePos = spCourse.getSelectedItemPosition();

            if (fullName.isEmpty() || birthday.isEmpty() || email.isEmpty() || contact.isEmpty() || coursePos < 0) {
                Toast.makeText(this, "Complete all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            CourseModel selectedCourse = courseOptionList.get(coursePos);

            // Generate student ID and password
            generateStudentId(studentId -> {
                String[] parts = birthday.split("-");
                String password = parts.length == 3 ? parts[1] + parts[2] + parts[0] : "123456"; // MMDDYYYY fallback

                StudentModel student = new StudentModel(
                        studentId,
                        fullName,
                        birthday,
                        email,
                        contact,
                        selectedCourse.getId(),
                        selectedCourse.getCourseName(),
                        selectedCourse.getSpecializationName(),
                        selectedCourse.getYearName(),
                        selectedCourse.getSectionName(),
                        "", // profileImage
                        password,
                        ""  // uid
                );

                auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(authTask -> {
                            if (authTask.isSuccessful()) {
                                FirebaseUser firebaseUser = authTask.getResult().getUser();
                                String uid = firebaseUser.getUid();
                                student.setUid(uid);

                                usersRef.child(uid).child("role").setValue("student");
                                usersRef.child(uid).child("studentId").setValue(studentId);

                                studentsRef.child(studentId).setValue(student)
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "Student added", Toast.LENGTH_SHORT).show();
                                            studentList.add(student);
                                            studentAdapter.notifyItemInserted(studentList.size() - 1);
                                            dialog.dismiss();
                                        });
                            } else {
                                Toast.makeText(this, "Auth failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
            });
        });
    }


    private void generateStudentId(OnIdGeneratedListener listener) {
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Integer> numbers = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String id = ds.getKey();
                    if (id != null && id.startsWith("STD-")) {
                        try {
                            int num = Integer.parseInt(id.replace("STD-", ""));
                            numbers.add(num);
                        } catch (NumberFormatException ignored) {}
                    }
                }

                int newNum = 1;
                while (numbers.contains(newNum)) newNum++;
                String newId = String.format("STD-%04d", newNum);
                listener.onGenerated(newId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(StudentActivity.this, "Error generating ID", Toast.LENGTH_SHORT).show();
            }
        });
    }
    AlertDialog loadingDialog;

    private void showLoadingDialog(String message) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null);
        TextView tvLoading = view.findViewById(R.id.tvLoading);
        tvLoading.setText(message);

        loadingDialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create();
        loadingDialog.show();
    }

    private void hideLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }


    interface OnIdGeneratedListener {
        void onGenerated(String studentId);
    }

    private void showStudentDialog(StudentModel student) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_student_edit, null);

        EditText etEditFullName = dialogView.findViewById(R.id.etEditFullName);
        EditText etEditBirthday = dialogView.findViewById(R.id.etEditBirthday);
        etEditBirthday.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (!TextUtils.isEmpty(etEditBirthday.getText().toString())) {
                try {
                    String[] parts = etEditBirthday.getText().toString().split("-");
                    int y = Integer.parseInt(parts[0]);
                    int m = Integer.parseInt(parts[1]) - 1;
                    int d = Integer.parseInt(parts[2]);
                    calendar.set(y, m, d);
                } catch (Exception ignored) {}
            }
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePicker = new DatePickerDialog(StudentActivity.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formattedDate = String.format("%04d-%02d-%02d",
                                selectedYear, selectedMonth + 1, selectedDay);
                        etEditBirthday.setText(formattedDate);
                    }, year, month, day);
            datePicker.show();
        });


        EditText etEditEmail = dialogView.findViewById(R.id.etEditEmail);
        EditText etEditContact = dialogView.findViewById(R.id.etEditContact);
        Spinner spEditCourse = dialogView.findViewById(R.id.spinnerEditCourses);
        ImageView ivEditProfile = dialogView.findViewById(R.id.ivEditProfile);
        ProgressBar progressBar = dialogView.findViewById(R.id.progressBarUpload);
        TextView tvProgress = dialogView.findViewById(R.id.tvUploadProgress);

        // Prefill values
        etEditFullName.setText(student.getFullName());
        etEditBirthday.setText(student.getBirthday());
        etEditEmail.setText(student.getEmail());
        etEditContact.setText(student.getContact());

        if (student.getProfileImage() != null && !student.getProfileImage().isEmpty()) {
            byte[] decodedBytes = Base64.decode(student.getProfileImage(), Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            ivEditProfile.setImageBitmap(bitmap);
        } else {
            ivEditProfile.setImageResource(R.drawable.examinee_default);
        }


        currentEditProfileView = ivEditProfile;

        ivEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, 101);
        });

        // Load courses into spinner
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear(); // <-- important
                List<String> courseNames = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel c = ds.getValue(CourseModel.class);
                    if (c != null) {
                        courseOptionList.add(c); // <-- update main list
                        courseNames.add(c.getName() + " - " +
                                c.getSpecializationName() + " - " +
                                c.getYearName() + " - " +
                                c.getSectionName());
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        StudentActivity.this,
                        android.R.layout.simple_spinner_item,
                        courseNames
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spEditCourse.setAdapter(adapter);

                // Set selection based on student's current course
                String displayValue = student.getCourseName() + " - " +
                        student.getSpecializationName() + " - " +
                        student.getYearName() + " - " +
                        student.getSectionName();
                setSpinnerSelection(spEditCourse, displayValue);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });


        // Create dialog
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle("Edit Student")
                .setCancelable(false)
                .setPositiveButton("Update", null) // override later
                .setNegativeButton("Cancel", null) // just null here
                .create();

        dialog.show(); // show first

// Handle negative button manually if needed
        Button btnCancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        btnCancel.setOnClickListener(v -> dialog.dismiss()); // manually dismiss


        // Override positive button click
        Button btnUpdate = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        btnUpdate.setOnClickListener(v -> {
            student.setFullName(etEditFullName.getText().toString().trim());
            student.setBirthday(etEditBirthday.getText().toString().trim());
            student.setEmail(etEditEmail.getText().toString().trim());
            student.setContact(etEditContact.getText().toString().trim());

            int pos = spEditCourse.getSelectedItemPosition();
            if (pos >= 0 && pos < courseOptionList.size()) {
                CourseModel selectedCourse = courseOptionList.get(pos);
                student.setCourseName(selectedCourse.getCourseName());
                student.setSpecializationName(selectedCourse.getSpecializationName());
                student.setYearName(selectedCourse.getYearName());
                student.setSectionName(selectedCourse.getSectionName());
            }


            if (selectedImageUri != null) {
                String base64Image = convertImageToBase64(selectedImageUri);
                if (base64Image != null) {
                    student.setProfileImage(base64Image); // save Base64 string instead of URL
                    updateStudentData(student);
                    dialog.dismiss();
                } else {
                    Toast.makeText(this, "Failed to encode image", Toast.LENGTH_SHORT).show();
                }
            } else {
                updateStudentData(student);
                dialog.dismiss();
            }

        });
    }



    // Handle selected image
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            if (currentEditProfileView != null) {
                currentEditProfileView.setImageURI(selectedImageUri);
            }
        }
    }




    private void updateStudentData(StudentModel student) {
        studentsRef.child(student.getStudentId()).setValue(student)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Student updated", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
    private void setSpinnerSelection(Spinner spinner, String value) {
        if (value == null) return;
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(value)) {
                spinner.setSelection(i);
                break;
            }
        }
    }
    private String convertImageToBase64(Uri imageUri) {
        try {
            // Load bitmap from URI
            Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

            // Resize bitmap (max width or height = 400px)
            int maxSize = 400;
            int width = original.getWidth();
            int height = original.getHeight();
            float scale = Math.min((float) maxSize / width, (float) maxSize / height);
            int newWidth = Math.round(width * scale);
            int newHeight = Math.round(height * scale);
            Bitmap resized = Bitmap.createScaledBitmap(original, newWidth, newHeight, true);

            // Compress bitmap to JPEG with quality 60% for small size
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resized.compress(Bitmap.CompressFormat.JPEG, 60, baos);

            byte[] imageBytes = baos.toByteArray();
            return Base64.encodeToString(imageBytes, Base64.DEFAULT);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }





}
