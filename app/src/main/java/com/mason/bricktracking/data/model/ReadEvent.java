package com.mason.bricktracking.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "read_events")
public class ReadEvent {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String buildSessionId;
    private long clientTimestamp;
    private String epc;
    private int rssi;               // RSSI in dBm
    private int readCount;          // Number of reads in window
    private boolean accepted;
    private String reasonCode;      // ACCEPTED, COOLDOWN, DUPLICATE_GPS, AMBIGUOUS, NO_GPS, etc.
    private boolean synced;
    
    public ReadEvent() {
    }
    
    public ReadEvent(String buildSessionId, long clientTimestamp, String epc, int rssi, int readCount, boolean accepted, String reasonCode) {
        this.buildSessionId = buildSessionId;
        this.clientTimestamp = clientTimestamp;
        this.epc = epc;
        this.rssi = rssi;
        this.readCount = readCount;
        this.accepted = accepted;
        this.reasonCode = reasonCode;
        this.synced = false;
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getBuildSessionId() {
        return buildSessionId;
    }
    
    public void setBuildSessionId(String buildSessionId) {
        this.buildSessionId = buildSessionId;
    }
    
    public long getClientTimestamp() {
        return clientTimestamp;
    }
    
    public void setClientTimestamp(long clientTimestamp) {
        this.clientTimestamp = clientTimestamp;
    }
    
    public String getEpc() {
        return epc;
    }
    
    public void setEpc(String epc) {
        this.epc = epc;
    }
    
    public int getRssi() {
        return rssi;
    }
    
    public void setRssi(int rssi) {
        this.rssi = rssi;
    }
    
    public int getReadCount() {
        return readCount;
    }
    
    public void setReadCount(int readCount) {
        this.readCount = readCount;
    }
    
    public boolean isAccepted() {
        return accepted;
    }
    
    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }
    
    public String getReasonCode() {
        return reasonCode;
    }
    
    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }
    
    public boolean isSynced() {
        return synced;
    }
    
    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}
