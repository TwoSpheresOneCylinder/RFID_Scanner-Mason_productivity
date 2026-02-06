package com.mason.bricktracking.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mason.bricktracking.MasonApp;
import com.mason.bricktracking.R;
import com.mason.bricktracking.data.remote.ApiClient;
import com.mason.bricktracking.data.remote.ApiService;
import com.mason.bricktracking.data.remote.Company;
import com.mason.bricktracking.data.remote.LoginRequest;
import com.mason.bricktracking.data.remote.LoginResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    
    private EditText etUsername, etPassword;
    private Button btnLogin;
    private ProgressBar progressBar;
    private TextView tvStatusMessage;

    private final ActivityResultLauncher<Intent> registerLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                String username = data.getStringExtra("username");
                String password = data.getStringExtra("password");
                if (username != null) etUsername.setText(username);
                if (password != null) etPassword.setText(password);
                showSuccess("Account created! You can now log in.");
            }
        });
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        initViews();
        setupListeners();
        
        // Pre-fill saved credentials if available
        MasonApp app = MasonApp.getInstance();
        if (app != null && app.hasSavedCredentials()) {
            String savedUsername = app.getSavedUsername();
            String savedPassword = app.getSavedPassword();
            etUsername.setText(savedUsername);
            etPassword.setText(savedPassword);
            // User must still press Login button
        }
    }
    
    private void initViews() {
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        progressBar = findViewById(R.id.progress_bar);
        tvStatusMessage = findViewById(R.id.tv_status_message);
    }
    
    private void setupListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());
        
        findViewById(R.id.tv_create_account).setOnClickListener(v -> {
            tvStatusMessage.setVisibility(View.GONE);
            registerLauncher.launch(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }
    
    private void attemptLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        
        // Validate inputs
        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Username is required");
            etUsername.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }
        
        // Show loading
        setLoading(true);
        
        // Create Mason ID from username
        String masonId = "MASON_" + username.toUpperCase();
        
        // Make login API call
        LoginRequest request = new LoginRequest(username, password);
        ApiService apiService = ApiClient.getApiService();
        
        // Handle case where API service couldn't be initialized
        if (apiService == null) {
            setLoading(false);
            showError("Cannot connect to server. Check network settings.");
            return;
        }
        
        Call<LoginResponse> call = apiService.login(request);
        
        call.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                setLoading(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();
                    
                    if (loginResponse.isSuccess()) {
                        // Save mason ID and admin status
                        String finalMasonId = loginResponse.getMasonId() != null ? 
                            loginResponse.getMasonId() : masonId;
                        
                        MasonApp.getInstance().saveMasonId(finalMasonId, loginResponse.isAdmin());
                        
                        // Save JWT auth token
                        if (loginResponse.getToken() != null) {
                            MasonApp.getInstance().saveAuthToken(loginResponse.getToken());
                        }

                        // Save company info
                        Company company = loginResponse.getCompany();
                        if (company != null) {
                            MasonApp.getInstance().saveCompany(
                                company.getId(),
                                company.getName(),
                                company.getCode()
                            );
                        }
                        
                        // Save credentials if enabled
                        MasonApp.getInstance().saveCredentials(username, password);
                        
                        navigateToConnection();
                    } else {
                        // Server returned success:false with a message
                        String msg = loginResponse.getMessage();
                        showError(msg != null ? msg : "Login failed");
                    }
                } else if (response.code() == 401) {
                    showError("Invalid username or password");
                } else if (response.code() == 400) {
                    showError("Please enter both username and password");
                } else {
                    showError("Server error (" + response.code() + "). Try again later.");
                }
            }
            
            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                setLoading(false);
                showError("Cannot reach server. Check your network connection.");
            }
        });
    }
    
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        etUsername.setEnabled(!loading);
        etPassword.setEnabled(!loading);
    }
    
    private void navigateToConnection() {
        Intent intent = new Intent(LoginActivity.this, ConnectionActivity.class);
        startActivity(intent);
        finish();
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
