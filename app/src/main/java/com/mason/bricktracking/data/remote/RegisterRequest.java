package com.mason.bricktracking.data.remote;

public class RegisterRequest {
    private String username;
    private String password;
    private String mason_id;

    public RegisterRequest(String username, String password, String masonId) {
        this.username = username;
        this.password = password;
        this.mason_id = masonId;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getMasonId() { return mason_id; }
    public void setMasonId(String masonId) { this.mason_id = masonId; }
}
