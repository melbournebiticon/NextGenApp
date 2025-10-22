package com.example.nextgen.admin;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class YearsActivity extends AppCompatActivity {

    Button addBtn;
    RecyclerView recyclerView;
    DatabaseReference dbRef;
    ArrayList<YearModel> yearList;
    YearsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_years);

        addBtn = findViewById(R.id.addYearBtn);
        recyclerView = findViewById(R.id.yearRecyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        yearList = new ArrayList<>();
        dbRef = FirebaseDatabase.getInstance().getReference("Years");

        addBtn.setOnClickListener(v -> showAddEditDialog(null, null));

        loadYears();
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
                adapter = new YearsAdapter(yearList, YearsActivity.this, dbRef);
                recyclerView.setAdapter(adapter);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(YearsActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Modal for Add/Edit Year
    public void showAddEditDialog(String id, String currentName) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_year);

        EditText etName = dialog.findViewById(R.id.etYearName);
        Button btnSave = dialog.findViewById(R.id.btnSaveYear);

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
            } else {
                // Edit existing year
                dbRef.child(id).child("name").setValue(name);
            }

            dialog.dismiss();
        });

        dialog.show();
    }
}
