package com.mason.bricktracking.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mason.bricktracking.R;
import com.mason.bricktracking.data.remote.ApiClient;
import com.mason.bricktracking.data.remote.ApiService;
import com.mason.bricktracking.data.remote.RegisterRequest;
import com.mason.bricktracking.data.remote.RegisterResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText etUsername, etMasonId, etPassword, etConfirmPassword;
    private Button btnRegister;
    private TextView tvBackToLogin;
    private TextView tvStatusMessage;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupListeners();
    }

    private void initViews() {
        etUsername = findViewById(R.id.et_reg_username);
        etMasonId = findViewById(R.id.et_reg_mason_id);
        etPassword = findViewById(R.id.et_reg_password);
        etConfirmPassword = findViewById(R.id.et_reg_confirm_password);
        btnRegister = findViewById(R.id.btn_register);
        tvBackToLogin = findViewById(R.id.tv_back_to_login);
        tvStatusMessage = findViewById(R.id.tv_status_message);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void setupListeners() {
        btnRegister.setOnClickListener(v -> attemptRegister());
        tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String username = etUsername.getText().toString().trim();
        String masonId = etMasonId.getText().toString().trim().toUpperCase();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // --- Validation ---
        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Username is required");
            etUsername.requestFocus();
            return;
        }

        if (username.length() < 3) {
            etUsername.setError("Username must be at least 3 characters");
            etUsername.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(masonId)) {
            etMasonId.setError("Mason ID is required");
            etMasonId.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        // --- API call ---
        setLoading(true);

        ApiService apiService = ApiClient.getApiService();

        if (apiService == null) {
            setLoading(false);
            showError("Cannot connect to server. Check network settings.");
            return;
        }

        RegisterRequest request = new RegisterRequest(username, password, masonId);
        Call<RegisterResponse> call = apiService.register(request);

        call.enqueue(new Callback<RegisterResponse>() {
            @Override
            public void onResponse(Call<RegisterResponse> call, Response<RegisterResponse> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    RegisterResponse body = response.body();
                    if (body.isSuccess()) {
                        // Pass credentials back to LoginActivity for auto-fill
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("username", username);
                        resultIntent.putExtra("password", password);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                        return;
                    } else {
                        showError(body.getMessage() != null ? body.getMessage() : "Registration failed");
                    }
                } else if (response.code() == 409) {
                    showError("Username or Mason ID already exists. Choose a different one.");
                } else if (response.code() == 400) {
                    showError("Please fill in all fields correctly.");
                } else {
                    showError("Server error (" + response.code() + "). Try again later.");
                }
            }

            @Override
            public void onFailure(Call<RegisterResponse> call, Throwable t) {
                setLoading(false);
                showError("Cannot reach server. Check your network connection.");
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
        etUsername.setEnabled(!loading);
        etMasonId.setEnabled(!loading);
        etPassword.setEnabled(!loading);
        etConfirmPassword.setEnabled(!loading);
    }

    private void showError(String message) {
        tvStatusMessage.setText(message);
        tvStatusMessage.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        tvStatusMessage.setVisibility(View.VISIBLE);
    }

    private void showSuccess(String message) {
        tvStatusMessage.setText("\u2705 " + message);
        tvStatusMessage.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        tvStatusMessage.setVisibility(View.VISIBLE);
    }
}
