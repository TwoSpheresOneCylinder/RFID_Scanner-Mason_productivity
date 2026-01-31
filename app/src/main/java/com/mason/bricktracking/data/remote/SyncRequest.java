package com.mason.bricktracking.data.remote;

public class SyncRequest {
    private String masonId;
    private PlacementData[] placements;
    
    public SyncRequest(String masonId, PlacementData[] placements) {
        this.masonId = masonId;
        this.placements = placements;
    }
    
    public String getMasonId() {
        return masonId;
    }
    
    public void setMasonId(String masonId) {
        this.masonId = masonId;
    }
    
    public PlacementData[] getPlacements() {
        return placements;
    }
    
    public void setPlacements(PlacementData[] placements) {
        this.placements = placements;
    }
    
    public static class PlacementData {
        private String brickNumber;
        private long timestamp;
        private double latitude;
        private double longitude;
        private double altitude;
        private double accuracy;
        private String buildSessionId;
        private int eventSeq;
        private int rssiAvg;
        private int rssiPeak;
        private int readsInWindow;
        private int powerLevel;
        private String decisionStatus;
        
        public PlacementData(String brickNumber, long timestamp, double latitude, double longitude, double altitude, double accuracy,
                           String buildSessionId, int eventSeq, int rssiAvg, int rssiPeak, int readsInWindow,
                           int powerLevel, String decisionStatus) {
            this.brickNumber = brickNumber;
            this.timestamp = timestamp;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.accuracy = accuracy;
            this.buildSessionId = buildSessionId;
            this.eventSeq = eventSeq;
            this.rssiAvg = rssiAvg;
            this.rssiPeak = rssiPeak;
            this.readsInWindow = readsInWindow;
            this.powerLevel = powerLevel;
            this.decisionStatus = decisionStatus;
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
    }
}
