package com.mason.bricktracking.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "brick_placements")
public class BrickPlacement {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String masonId;
    private String brickNumber;  // EPC from RFID tag
    private long timestamp;
    private boolean synced;
    private double latitude;
    private double longitude;
    private double altitude;
    private double accuracy;
    
    // Session and sequence tracking
    private String buildSessionId;
    private int eventSeq;
    
    // RSSI and read quality metrics
    private int rssiAvg;
    private int rssiPeak;
    private int readsInWindow;
    
    // Reader power level used for this scan
    private int powerLevel;
    
    // Decision status
    private String decisionStatus;  // ACCEPTED, AMBIGUOUS, REJECTED_NO_GPS, ACCEPTED_NO_GPS, etc.
    
    // Scan type: "pallet" for inventory scans, "placement" for placement scans
    private String scanType;
    
    public BrickPlacement() {
    }
    
    public BrickPlacement(String masonId, String brickNumber, long timestamp) {
        this.masonId = masonId;
        this.brickNumber = brickNumber;
        this.timestamp = timestamp;
        this.synced = false;
        this.latitude = 0.0;
        this.longitude = 0.0;
        this.altitude = 0.0;
        this.accuracy = 0.0;
        this.scanType = "placement";
    }
    
    public BrickPlacement(String masonId, String brickNumber, long timestamp, double latitude, double longitude) {
        this.masonId = masonId;
        this.brickNumber = brickNumber;
        this.timestamp = timestamp;
        this.synced = false;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = 0.0;
        this.scanType = "placement";
    }
    
    public BrickPlacement(String masonId, String brickNumber, long timestamp, double latitude, double longitude, double altitude, double accuracy) {
        this.masonId = masonId;
        this.brickNumber = brickNumber;
        this.timestamp = timestamp;
        this.synced = false;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.accuracy = accuracy;
        this.buildSessionId = "";
        this.eventSeq = 0;
        this.rssiAvg = 0;
        this.rssiPeak = 0;
        this.readsInWindow = 0;
        this.decisionStatus = "ACCEPTED";
        this.scanType = "placement";
    }
    
    // Full constructor with all fields
    public BrickPlacement(String masonId, String brickNumber, long timestamp, double latitude, double longitude, double altitude, double accuracy,
                          String buildSessionId, int eventSeq, int rssiAvg, int rssiPeak, int readsInWindow, String decisionStatus) {
        this.masonId = masonId;
        this.brickNumber = brickNumber;
        this.timestamp = timestamp;
        this.synced = false;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.accuracy = accuracy;
        this.buildSessionId = buildSessionId;
        this.eventSeq = eventSeq;
        this.rssiAvg = rssiAvg;
        this.rssiPeak = rssiPeak;
        this.readsInWindow = readsInWindow;
        this.powerLevel = 0; // Will be set separately
        this.decisionStatus = decisionStatus;
        this.scanType = "placement"; // Will be set separately
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getMasonId() {
        return masonId;
    }
    
    public void setMasonId(String masonId) {
        this.masonId = masonId;
    }
    
    public String getBrickNumber() {
        return brickNumber;
    }
    
    public void setBrickNumber(String brickNumber) {
        this.brickNumber = brickNumber;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isSynced() {
        return synced;
    }
    
    public void setSynced(boolean synced) {
        this.synced = synced;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    
    public double getAltitude() {
        return altitude;
    }
    
    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }
    
    public double getAccuracy() {
        return accuracy;
    }
    
    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }
    
    public String getBuildSessionId() {
        return buildSessionId;
    }
    
    public void setBuildSessionId(String buildSessionId) {
        this.buildSessionId = buildSessionId;
    }
    
    public int getEventSeq() {
        return eventSeq;
    }
    
    public void setEventSeq(int eventSeq) {
        this.eventSeq = eventSeq;
    }
    
    public int getRssiAvg() {
        return rssiAvg;
    }
    
    public void setRssiAvg(int rssiAvg) {
        this.rssiAvg = rssiAvg;
    }
    
    public int getRssiPeak() {
        return rssiPeak;
    }
    
    public void setRssiPeak(int rssiPeak) {
        this.rssiPeak = rssiPeak;
    }
    
    public int getReadsInWindow() {
        return readsInWindow;
    }
    
    public void setReadsInWindow(int readsInWindow) {
        this.readsInWindow = readsInWindow;
    }
    
    public int getPowerLevel() {
        return powerLevel;
    }
    
    public void setPowerLevel(int powerLevel) {
        this.powerLevel = powerLevel;
    }
    
    public String getDecisionStatus() {
        return decisionStatus;
    }
    
    public void setDecisionStatus(String decisionStatus) {
        this.decisionStatus = decisionStatus;
    }
    
    public String getScanType() {
        return scanType;
    }
    
    public void setScanType(String scanType) {
        this.scanType = scanType;
    }
}
