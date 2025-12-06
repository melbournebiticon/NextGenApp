package com.finale.nextgen.teacher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.finale.nextgen.R;

import org.json.JSONException;
import org.json.JSONObject;

public class CreateAccountActivity extends AppCompatActivity {

    private EditText newUsernameEditText, newPasswordEditText;
    private EditText etBirthday, etAge, etEmail, etPhone, etLocation;
    private Button registerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_account);

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // === Bind Views ===
        newUsernameEditText = findViewById(R.id.newUsernameEditText);
        newPasswordEditText = findViewById(R.id.newPasswordEditText);
        etBirthday = findViewById(R.id.etBirthday);
        etAge = findViewById(R.id.etAge);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etLocation = findViewById(R.id.etLocation);
        registerButton = findViewById(R.id.createaccountButton);

        // === Ensure "accounts" JSON exists and migrate old data if needed ===
        final SharedPreferences sharedPref = getSharedPreferences("user_data", Context.MODE_PRIVATE);
        migrateOldSingleUserIfNeeded(sharedPref);

        // === Register Button Logic ===
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String username = newUsernameEditText.getText().toString().trim();
                String password = newPasswordEditText.getText().toString().trim();
                String birthday = etBirthday.getText().toString().trim();
                String age = etAge.getText().toString().trim();
                String email = etEmail.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();
                String location = etLocation.getText().toString().trim();

                // Basic Validation
                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(CreateAccountActivity.this, "Username & Password are required", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (email.isEmpty()) {
                    Toast.makeText(CreateAccountActivity.this, "Please enter your email", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Load accounts JSON
                String accountsStr = sharedPref.getString("accounts", "{}");
                try {
                    JSONObject accounts = new JSONObject(accountsStr);

                    // Check duplicate username
                    if (accounts.has(username)) {
                        Toast.makeText(CreateAccountActivity.this, "Username already exists. Choose another.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Create user object
                    JSONObject userObj = new JSONObject();
                    userObj.put("password", password);
                    userObj.put("birthday", birthday);
                    userObj.put("age", age);
                    userObj.put("email", email);
                    userObj.put("phone", phone);
                    userObj.put("location", location);

                    // Add to accounts list
                    accounts.put(username, userObj);

                    // Save permanently
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("accounts", accounts.toString());
                    editor.commit(); // <-- changed from apply() to commit()

                    Toast.makeText(CreateAccountActivity.this,
                            "Account created successfully!", Toast.LENGTH_SHORT).show();

                    // Return to Login screen
                    finish();

                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(CreateAccountActivity.this, "Error saving account.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void migrateOldSingleUserIfNeeded(SharedPreferences sharedPref) {
        String accountsStr = sharedPref.getString("accounts", null);
        if (accountsStr != null) return; // already using new structure

        String oldUsername = sharedPref.getString("username", null);
        if (oldUsername == null) return;

        try {
            JSONObject accounts = new JSONObject();
            JSONObject oldUser = new JSONObject();
            oldUser.put("password", sharedPref.getString("password", ""));
            oldUser.put("birthday", sharedPref.getString("birthday", ""));
            oldUser.put("age", sharedPref.getString("age", ""));
            oldUser.put("email", sharedPref.getString("email", ""));
            oldUser.put("phone", sharedPref.getString("phone", ""));
            oldUser.put("location", sharedPref.getString("location", ""));
            accounts.put(oldUsername, oldUser);

            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("accounts", accounts.toString());
            editor.remove("username");
            editor.remove("password");
            editor.remove("birthday");
            editor.remove("age");
            editor.remove("email");
            editor.remove("phone");
            editor.remove("location");
            editor.commit(); // <-- changed from apply() to commit()

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
