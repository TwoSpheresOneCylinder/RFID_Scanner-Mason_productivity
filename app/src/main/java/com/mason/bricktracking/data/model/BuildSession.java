package com.mason.bricktracking.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "build_sessions")
public class BuildSession {
    @PrimaryKey
    @NonNull
    private String buildSessionId;  // UUID
    
    private String masonId;
    private String toolId;          // Tool identifier (e.g., "MR20")
    private String wallId;          // Wall/test run identifier
    private long startedAtClient;
    private long endedAtClient;
    private String notes;
    private boolean synced;
    
    public BuildSession() {
    }
    
    public BuildSession(String buildSessionId, String masonId, String toolId, String wallId, long startedAtClient) {
        this.buildSessionId = buildSessionId;
        this.masonId = masonId;
        this.toolId = toolId;
        this.wallId = wallId;
        this.startedAtClient = startedAtClient;
        this.endedAtClient = 0;
        this.notes = "";
        this.synced = false;
    }
    
    // Getters and Setters
    @NonNull
    public String getBuildSessionId() {
        return buildSessionId;
    }
    
    public void setBuildSessionId(@NonNull String buildSessionId) {
        this.buildSessionId = buildSessionId;
    }
    
    public String getMasonId() {
        return masonId;
    }
    
    public void setMasonId(String masonId) {
        this.masonId = masonId;
    }
    
    public String getToolId() {
        return toolId;
    }
    
    public void setToolId(String toolId) {
        this.toolId = toolId;
    }
    
    public String getWallId() {
        return wallId;
    }
    
    public void setWallId(String wallId) {
        this.wallId = wallId;
    }
    
    public long getStartedAtClient() {
        return startedAtClient;
    }
    
    public void setStartedAtClient(long startedAtClient) {
        this.startedAtClient = startedAtClient;
    }
    
    public long getEndedAtClient() {
        return endedAtClient;
    }
    
    public void setEndedAtClient(long endedAtClient) {
        this.endedAtClient = endedAtClient;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public boolean isSynced() {
        return synced;
    }
    
    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}
