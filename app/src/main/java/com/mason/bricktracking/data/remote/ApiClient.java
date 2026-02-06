package com.mason.bricktracking.data.remote;

import android.util.Log;

import com.mason.bricktracking.MasonApp;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

public class ApiClient {
    private static final String TAG = "ApiClient";
    // IMPORTANT: Update this BASE_URL based on your setup:
    // - Android Emulator: use "http://10.0.2.2:8080/api/"
    // - Physical Device: use your computer's IP (run "ipconfig" and find IPv4 Address)
    //   Example: "http://192.168.1.100:8080/api/"
    // 
    // Current setup: Physical device connected to same WiFi network
    private static final String BASE_URL = "http://192.168.10.124:8080/api/";
    private static Retrofit retrofit;
    private static ApiService apiService;
    
    public static synchronized ApiService getApiService() {
        if (apiService == null) {
            try {
                Log.d(TAG, "Initializing API client with BASE_URL: " + BASE_URL);
                retrofit = createRetrofit();
                apiService = retrofit.create(ApiService.class);
                Log.d(TAG, "API client initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize Retrofit", e);
                // Return a dummy implementation or handle it in the caller
            }
        }
        return apiService;
    }
    
    /**
     * Reset the API client (e.g., after logout so the auth interceptor
     * picks up the new token on next login).
     */
    public static synchronized void reset() {
        retrofit = null;
        apiService = null;
        Log.d(TAG, "API client reset");
    }
    
    private static Retrofit createRetrofit() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        
        OkHttpClient client = new OkHttpClient.Builder()
            // Auth interceptor — attaches Bearer token from MasonApp
            .addInterceptor(chain -> {
                Request original = chain.request();
                
                // Skip auth header for public endpoints
                String path = original.url().encodedPath();
                if (path.contains("/auth/login") || path.contains("/auth/register")
                        || path.endsWith("/companies")) {
                    return chain.proceed(original);
                }
                
                MasonApp app = MasonApp.getInstance();
                String token = (app != null) ? app.getAuthToken() : null;
                
                if (token != null) {
                    Request authenticated = original.newBuilder()
                        .header("Authorization", "Bearer " + token)
                        .build();
                    return chain.proceed(authenticated);
                }
                
                return chain.proceed(original);
            })
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        
        return new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
    }
}
