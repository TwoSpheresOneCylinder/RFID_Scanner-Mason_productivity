package com.mason.bricktracking.data.remote;

public class SyncResponse {
    private boolean success;
    private String message;
    private int lastPlacementNumber;
    
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
    
    public int getLastPlacementNumber() {
        return lastPlacementNumber;
    }
    
    public void setLastPlacementNumber(int lastPlacementNumber) {
        this.lastPlacementNumber = lastPlacementNumber;
    }
}
