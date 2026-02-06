package com.mason.bricktracking.data.remote;

public class LoginResponse {
    private boolean success;
    private String message;
    private String masonId;
    private boolean isAdmin;
    private String token;
    private Company company;
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getMasonId() {
        return masonId;
    }
    
    public void setMasonId(String masonId) {
        this.masonId = masonId;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }
}
