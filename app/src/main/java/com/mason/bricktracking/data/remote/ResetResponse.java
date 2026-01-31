package com.mason.bricktracking.data.remote;

public class ResetResponse {
    private boolean success;
    private String message;
    private int deletedCount;
    
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
    
    public int getDeletedCount() {
        return deletedCount;
    }
    
    public void setDeletedCount(int deletedCount) {
        this.deletedCount = deletedCount;
    }
}
