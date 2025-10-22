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

public class SpecializationsActivity extends AppCompatActivity {

    Button addBtn;
    RecyclerView recyclerView;
    DatabaseReference dbRef;
    ArrayList<SpecializationModel> specializationList;
    SpecializationAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_specializations);

        addBtn = findViewById(R.id.addSpecializationBtn);
        recyclerView = findViewById(R.id.specializationRecyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        specializationList = new ArrayList<>();
        dbRef = FirebaseDatabase.getInstance().getReference("Specializations");

        addBtn.setOnClickListener(v -> showAddEditDialog(null, null));

        loadSpecializations();
    }

    private void loadSpecializations() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                specializationList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String id = ds.getKey();
                    String name = ds.child("name").getValue(String.class);
                    specializationList.add(new SpecializationModel(id, name));
                }
                // Pass the activity itself, not a method reference
                adapter = new SpecializationAdapter(specializationList, SpecializationsActivity.this, dbRef);
                recyclerView.setAdapter(adapter);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(SpecializationsActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    // Modal for Add/Edit
    public void showAddEditDialog(String id, String currentName) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_specialization);

        EditText etName = dialog.findViewById(R.id.etSpecializationName);
        Button btnSave = dialog.findViewById(R.id.btnSaveSpecialization);

        if (currentName != null) etName.setText(currentName);

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError("Enter name");
                return;
            }

            if (id == null) {
                // Add new specialization
                String newId = dbRef.push().getKey();
                dbRef.child(newId).child("name").setValue(name);
            } else {
                // Edit existing specialization
                dbRef.child(id).child("name").setValue(name);
            }

            dialog.dismiss();
        });

        dialog.show();
    }
}
