package com.mason.bricktracking.data.remote;

public class RegisterResponse {
    private boolean success;
    private String message;
    private String username;
    private String masonId;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMasonId() { return masonId; }
    public void setMasonId(String masonId) { this.masonId = masonId; }
}
