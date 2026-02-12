package com.mason.bricktracking.data.remote;

public class SyncResponse {
    private boolean success;
    private String message;
    private int lastPlacementNumber;
    private int palletCount;
    private int placementCount;
    
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
    
    public int getPalletCount() {
        return palletCount;
    }
    
    public void setPalletCount(int palletCount) {
        this.palletCount = palletCount;
    }
    
    public int getPlacementCount() {
        return placementCount;
    }
    
    public void setPlacementCount(int placementCount) {
        this.placementCount = placementCount;
    }
}
