package com.mason.bricktracking.data.remote;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    
    @POST("placements/sync")
    Call<SyncResponse> syncPlacements(@Body SyncRequest request);
    
    @GET("placements/last")
    Call<SyncResponse> getLastPlacementNumber(@Query("masonId") String masonId);
    
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);
    
    @POST("auth/register")
    Call<RegisterResponse> register(@Body RegisterRequest request);

    @DELETE("placements/mason/{masonId}")
    Call<ResetResponse> resetProfileData(@Path("masonId") String masonId);
}
