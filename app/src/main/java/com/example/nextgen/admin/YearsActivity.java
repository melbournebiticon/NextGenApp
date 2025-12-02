package com.example.nextgen.admin;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.widget.ImageView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

public class YearsActivity extends AppCompatActivity {




    // ORIGINAL COMPONENTS
    Button addBtn;
    RecyclerView recyclerView;
    DatabaseReference dbRef;
    ArrayList<YearModel> yearList;
    YearsAdapter adapter;

    // NEW UI COMPONENTS
    private LinearLayout emptyState;
    private TextView tvYearCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_years);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(YearsActivity.this, AdminActivity.class);
            startActivity(intent);
            finish();
        });


        FloatingActionButton addYearFab = findViewById(R.id.addYearFab);
        addYearFab.setOnClickListener(v -> {
            // Show the Add/Edit Year dialog
            showAddEditDialog(null, null);
        });



        // ORIGINAL CODE
        addBtn = findViewById(R.id.addYearBtn);
        recyclerView = findViewById(R.id.yearRecyclerView);

        // NEW UI COMPONENTS INITIALIZATION
        emptyState = findViewById(R.id.emptyState);
        tvYearCount = findViewById(R.id.tvYearCount);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        yearList = new ArrayList<>();
        dbRef = FirebaseDatabase.getInstance().getReference("Years");

        addBtn.setOnClickListener(v -> showAddEditDialog(null, null));

        loadYears();
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Navigate back to AdminActivity
            Intent intent = new Intent(YearsActivity.this, AdminActivity.class);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }




    @Override
    public void onBackPressed() {
        Intent intent = new Intent(YearsActivity.this, AdminActivity.class);
        startActivity(intent);
        finish();
    }



    private void loadYears() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                yearList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String id = ds.getKey();
                    String name = ds.child("name").getValue(String.class);
                    yearList.add(new YearModel(id, name));
                }

                // NEW: Update UI with count and empty state
                updateUI();

                adapter = new YearsAdapter(yearList, YearsActivity.this, dbRef);
                recyclerView.setAdapter(adapter);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(YearsActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // NEW METHOD: Update UI based on data
    private void updateUI() {
        int count = yearList.size();
        tvYearCount.setText(count + " year" + (count != 1 ? "s" : ""));

        if (yearList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    public void showAddEditDialog(String id, String currentName) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_year);

        EditText etName = dialog.findViewById(R.id.etYearName);
        Button btnSave = dialog.findViewById(R.id.btnSaveYear);

        // NEW: Set dialog title based on action
        TextView tvDialogTitle = dialog.findViewById(R.id.tvDialogTitle);
        if (id != null) {
            tvDialogTitle.setText("Edit Year");
        } else {
            tvDialogTitle.setText("Add Year");
        }

        if (currentName != null) etName.setText(currentName);

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError("Enter name");
                return;
            }

            if (id == null) {
                // Add new year
                String newId = dbRef.push().getKey();
                dbRef.child(newId).child("name").setValue(name);
                Toast.makeText(this, "Year added successfully", Toast.LENGTH_SHORT).show();
            } else {
                // Edit existing year
                dbRef.child(id).child("name").setValue(name);
                Toast.makeText(this, "Year updated successfully", Toast.LENGTH_SHORT).show();
            }

            dialog.dismiss();
        });

        // NEW: Add cancel button functionality
        Button btnCancel = dialog.findViewById(R.id.btnCancelYear);
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}