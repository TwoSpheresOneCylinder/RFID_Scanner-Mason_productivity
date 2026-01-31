# Comprehensive System Testing Guide
## Mason Brick Tracking - End-to-End Validation

---

## 🎯 Testing Objectives

1. **Data Integrity**: All 17 fields preserved from scan → backend → dashboard
2. **Translation Accuracy**: JSON serialization/deserialization correct
3. **UPSERT Logic**: Validation rules work correctly (30s gap, improvement checks)
4. **Counter Accuracy**: App UI reflects server's authoritative count
5. **Duplicate Prevention**: 3-level detection prevents duplicate data
6. **Security**: Authentication and authorization work correctly

---

## 📋 Pre-Test Setup

### 1. Clean Environment
```powershell
# Stop all running servers
Get-Process -Name node -ErrorAction SilentlyContinue | Stop-Process -Force

# Backend: Purge database
cd "c:\Users\cange\Downloads\Block RFID\Vendor Code\MasonBrickTracking\backend"
Remove-Item mason_tracking.db -Force -ErrorAction SilentlyContinue

# Rebuild and install app
cd "c:\Users\cange\Downloads\Block RFID\Vendor Code\MasonBrickTracking"
.\gradlew clean assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Clear app data on device
adb shell pm clear com.mason.bricktracking

# Start backend
cd backend
Start-Process powershell -ArgumentList "-NoExit", "-Command", "node server.js"
```

### 2. Verify Backend Startup
```powershell
# Should see:
# ✓ Users table ready
# ✓ Placements table ready
# Default users: mason1, mason2, admin
```

---

## 🧪 Test Suite

### Phase 1: Backend API Testing (No App Required)

#### Test 1.1: Authentication
```powershell
# Test login
curl -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"mason1","password":"password123"}'

# Expected: {"success":true,"token":"...","user":{...},"masonId":"MASON_001"}

# Test invalid password
curl -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"mason1","password":"wrong"}'

# Expected: {"success":false,"message":"Invalid credentials"}
```

**✅ Pass Criteria**: Login works with correct credentials, fails with wrong password

---

#### Test 1.2: Sync Endpoint - New Placement
```powershell
# Create test placement
$placement = @{
    masonId = "MASON_001"
    placements = @(
        @{
            brickNumber = "TEST-BRICK-001"
            timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            latitude = 40.7128
            longitude = -74.0060
            altitude = 10.5
            accuracy = 5.0
            buildSessionId = "TEST-SESSION-001"
            eventSeq = 1
            rssiAvg = -50
            rssiPeak = -45
            readsInWindow = 10
            powerLevel = 26
            decisionStatus = "CONFIRMED"
        }
    )
} | ConvertTo-Json -Depth 5

curl -X POST http://localhost:8080/api/placements/sync `
  -H "Content-Type: application/json" `
  -d $placement

# Expected: {"success":true,"lastPlacementNumber":1,"inserted":1,"updated":0}
```

**✅ Pass Criteria**: 
- Response has `lastPlacementNumber: 1`
- Backend logs show: `[MASON_001] + New: TEST-BRICK-001`
- `inserted: 1, updated: 0`

---

#### Test 1.3: UPSERT - Valid Update (30s+ gap)
```powershell
Start-Sleep -Seconds 31

# Send same brick with better GPS accuracy
$updatePlacement = @{
    masonId = "MASON_001"
    placements = @(
        @{
            brickNumber = "TEST-BRICK-001"
            timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            latitude = 40.7130  # Slightly different location
            longitude = -74.0058
            altitude = 10.8
            accuracy = 2.0      # Better accuracy: 2m vs 5m
            buildSessionId = "TEST-SESSION-001"
            eventSeq = 2
            rssiAvg = -52
            rssiPeak = -47
            readsInWindow = 12
            powerLevel = 26
            decisionStatus = "CONFIRMED"
        }
    )
} | ConvertTo-Json -Depth 5

curl -X POST http://localhost:8080/api/placements/sync `
  -H "Content-Type: application/json" `
  -d $updatePlacement

# Expected: {"success":true,"lastPlacementNumber":1,"inserted":0,"updated":1}
```

**✅ Pass Criteria**:
- Response has `lastPlacementNumber: 1` (still 1, not 2!)
- Backend logs show: `[MASON_001] ↻ Updated: TEST-BRICK-001`
- `inserted: 0, updated: 1`

---

#### Test 1.4: UPSERT - Rejected (Too Recent)
```powershell
# Send immediately (within 30s) - should be rejected
$rejectPlacement = @{
    masonId = "MASON_001"
    placements = @(
        @{
            brickNumber = "TEST-BRICK-001"
            timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            latitude = 40.7132
            longitude = -74.0055
            altitude = 11.0
            accuracy = 1.0      # Even better accuracy
            buildSessionId = "TEST-SESSION-001"
            eventSeq = 3
            rssiAvg = -48
            rssiPeak = -43
            readsInWindow = 15
            powerLevel = 26
            decisionStatus = "CONFIRMED"
        }
    )
} | ConvertTo-Json -Depth 5

curl -X POST http://localhost:8080/api/placements/sync `
  -H "Content-Type: application/json" `
  -d $rejectPlacement

# Expected: {"success":true,"lastPlacementNumber":1,"duplicatesSkipped":1}
```

**✅ Pass Criteria**:
- Backend logs show: `✗ Rejected: too recent (Xs < 30s)`
- Counter still 1 (not incremented)
- `duplicatesSkipped: 1`

---

#### Test 1.5: UPSERT - Rejected (No Improvement)
```powershell
Start-Sleep -Seconds 31

# Send with worse/same data
$noImprovePlacement = @{
    masonId = "MASON_001"
    placements = @(
        @{
            brickNumber = "TEST-BRICK-001"
            timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            latitude = 40.7128
            longitude = -74.0060
            altitude = 10.0
            accuracy = 5.0      # Same as original, not better
            buildSessionId = "TEST-SESSION-001"
            eventSeq = 4
            rssiAvg = -55       # Weaker signal
            rssiPeak = -50
            readsInWindow = 8
            powerLevel = 26
            decisionStatus = "CONFIRMED"
        }
    )
} | ConvertTo-Json -Depth 5

curl -X POST http://localhost:8080/api/placements/sync `
  -H "Content-Type: application/json" `
  -d $noImprovePlacement

# Expected: Rejected due to no improvement
```

**✅ Pass Criteria**:
- Backend logs show: `✗ Rejected: no improvement`
- Counter still 1

---

#### Test 1.6: Cross-Mason Duplicate Detection
```powershell
# Mason 2 tries to scan the same brick within 5 minutes
$crossMasonPlacement = @{
    masonId = "MASON_002"
    placements = @(
        @{
            brickNumber = "TEST-BRICK-001"  # Same brick
            timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            latitude = 40.7140
            longitude = -74.0070
            altitude = 12.0
            accuracy = 3.0
            buildSessionId = "TEST-SESSION-002"
            eventSeq = 1
            rssiAvg = -48
            rssiPeak = -43
            readsInWindow = 12
            powerLevel = 26
            decisionStatus = "CONFIRMED"
        }
    )
} | ConvertTo-Json -Depth 5

curl -X POST http://localhost:8080/api/placements/sync `
  -H "Content-Type: application/json" `
  -d $crossMasonPlacement

# Expected: Rejected due to cross-mason conflict
```

**✅ Pass Criteria**:
- Backend logs show: `⚠ Cross-mason conflict: TEST-BRICK-001 scanned by MASON_001 within 5min`
- Counter for MASON_002 is 0

---

### Phase 2: Data Integrity Verification

#### Test 2.1: Verify All Fields Stored Correctly
```powershell
cd backend
node -e "
const sqlite3 = require('sqlite3').verbose();
const db = new sqlite3.Database('./mason_tracking.db');

db.all('SELECT * FROM placements WHERE mason_id = ?', ['MASON_001'], (err, rows) => {
    if (err) {
        console.error(err);
    } else {
        console.log('=== MASON_001 PLACEMENT DATA ===');
        console.log(JSON.stringify(rows, null, 2));
        
        // Verify field count
        if (rows.length > 0) {
            const fields = Object.keys(rows[0]);
            console.log('\nTotal fields stored:', fields.length);
            console.log('Fields:', fields.join(', '));
            
            // Check critical fields
            const row = rows[0];
            console.log('\n=== FIELD VALIDATION ===');
            console.log('brick_number:', row.brick_number);
            console.log('latitude:', row.latitude);
            console.log('longitude:', row.longitude);
            console.log('accuracy:', row.accuracy);
            console.log('rssi_avg:', row.rssi_avg);
            console.log('event_id:', row.event_id);
            console.log('timestamp:', new Date(row.timestamp).toISOString());
        }
    }
    db.close();
});
"
```

**✅ Pass Criteria**:
- Total fields: 17+ (id, mason_id, brick_number, timestamp, latitude, longitude, altitude, accuracy, build_session_id, event_seq, rssi_avg, rssi_peak, reads_in_window, power_level, decision_status, event_id, received_at, created_at, synced)
- All values match what was sent in request
- No NULL values for required fields
- Timestamps are valid Unix milliseconds

---

#### Test 2.2: Verify Dashboard Data Format
```powershell
# Get recent placements (dashboard endpoint)
curl http://localhost:8080/api/placements/recent

# Expected: Array of placement objects with all fields
```

**✅ Pass Criteria**:
- Response is valid JSON array
- Each object has all 17 fields
- GPS coordinates match database
- RSSI values preserved
- Session IDs correct

---

### Phase 3: Android App Testing

#### Test 3.1: Clean Install and Login
**Steps:**
1. Open app on device
2. Login with `mason1` / `password123`
3. Verify counter shows `0`

**✅ Pass**: Login successful, counter initialized

---

#### Test 3.2: First Scan and Sync
**Steps:**
1. Scan RFID tag
2. Wait for sync (watch logs)
3. Check counter updates to `1`
4. Check dashboard shows 1 placement

**Backend Logs to Watch:**
```
[MASON_001] Sync request received
[MASON_001] Placements to sync: 1
[MASON_001] + New: E28011B0A50500768C7FB6E2 @ 2026-01-30T...
[MASON_001] ✓ Synced: 1 new, 0 updated. Total: 1
```

**✅ Pass Criteria**:
- App counter shows `1`
- Dashboard shows `1` placement for mason1
- Backend logs show `+ New`
- GPS coordinates appear on map

---

#### Test 3.3: Rescan Same Tag (Valid Update - 30s+ gap)
**Steps:**
1. Wait 31+ seconds
2. Move to different location (or just wait)
3. Scan same RFID tag again
4. Watch counter - should STAY at `1` (not increment to 2)

**Backend Logs to Watch:**
```
[MASON_001] ↻ Updated: E28011B0A50500768C7FB6E2 @ 2026-01-30T...
[MASON_001] ✓ Synced: 0 new, 1 updated. Total: 1
```

**✅ Pass Criteria**:
- App counter STAYS at `1` (not 2)
- Dashboard STAYS at `1` placement
- Backend logs show `↻ Updated`
- Map shows updated GPS location

---

#### Test 3.4: Rescan Too Quickly (Should Be Rejected)
**Steps:**
1. Immediately scan same tag again (within 30s)
2. Watch counter - should STAY at `1`

**Backend Logs to Watch:**
```
[MASON_001] ✗ Rejected: too recent (14s < 30s)
[MASON_001] ⚠ Skipped 1 duplicate placements
```

**✅ Pass Criteria**:
- App counter STAYS at `1`
- Backend logs show rejection reason
- Dashboard unchanged

---

#### Test 3.5: Scan Different Brick
**Steps:**
1. Scan a different RFID tag
2. Counter should increment to `2`

**✅ Pass Criteria**:
- App counter shows `2`
- Dashboard shows `2` placements
- Both bricks visible on map

---

#### Test 3.6: Offline Mode and Later Sync
**Steps:**
1. Enable airplane mode on device
2. Scan 3 different bricks
3. App should cache locally
4. Disable airplane mode
5. Wait for auto-sync (or trigger manually)
6. Counter should jump to `5` (2 + 3 new)

**✅ Pass Criteria**:
- App caches scans while offline
- Auto-syncs when network returns
- Counter updates to server's count
- All 5 placements in dashboard

---

### Phase 4: Field-by-Field Data Translation Test

#### Test 4.1: Create Comprehensive Field Validator
```powershell
# Create test script
cd backend
```

Create `test_data_integrity.js`:

```javascript
const sqlite3 = require('sqlite3').verbose();
const db = new sqlite3.Database('./mason_tracking.db');

// Test data with all fields populated
const testData = {
    brickNumber: "VALIDATION-TEST-001",
    timestamp: Date.now(),
    latitude: 40.712800,
    longitude: -74.006000,
    altitude: 10.555,
    accuracy: 3.142,
    buildSessionId: "VALID-SESSION-12345",
    eventSeq: 42,
    rssiAvg: -55,
    rssiPeak: -48,
    readsInWindow: 25,
    powerLevel: 26,
    decisionStatus: "CONFIRMED_TEST"
};

console.log("=== SENDING TEST DATA ===");
console.log(JSON.stringify(testData, null, 2));

// Send to API
const http = require('http');
const postData = JSON.stringify({
    masonId: "MASON_001",
    placements: [testData]
});

const options = {
    hostname: 'localhost',
    port: 8080,
    path: '/api/placements/sync',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
    }
};

const req = http.request(options, (res) => {
    let data = '';
    res.on('data', (chunk) => { data += chunk; });
    res.on('end', () => {
        console.log("\n=== API RESPONSE ===");
        console.log(data);
        
        // Wait a bit then verify database
        setTimeout(() => {
            db.get(
                'SELECT * FROM placements WHERE brick_number = ?',
                [testData.brickNumber],
                (err, row) => {
                    if (err) {
                        console.error("Database error:", err);
                        return;
                    }
                    
                    console.log("\n=== DATABASE RECORD ===");
                    console.log(JSON.stringify(row, null, 2));
                    
                    console.log("\n=== FIELD VALIDATION ===");
                    const validations = [
                        {field: 'brick_number', sent: testData.brickNumber, stored: row.brick_number},
                        {field: 'timestamp', sent: testData.timestamp, stored: row.timestamp},
                        {field: 'latitude', sent: testData.latitude, stored: row.latitude},
                        {field: 'longitude', sent: testData.longitude, stored: row.longitude},
                        {field: 'altitude', sent: testData.altitude, stored: row.altitude},
                        {field: 'accuracy', sent: testData.accuracy, stored: row.accuracy},
                        {field: 'build_session_id', sent: testData.buildSessionId, stored: row.build_session_id},
                        {field: 'event_seq', sent: testData.eventSeq, stored: row.event_seq},
                        {field: 'rssi_avg', sent: testData.rssiAvg, stored: row.rssi_avg},
                        {field: 'rssi_peak', sent: testData.rssiPeak, stored: row.rssi_peak},
                        {field: 'reads_in_window', sent: testData.readsInWindow, stored: row.reads_in_window},
                        {field: 'power_level', sent: testData.powerLevel, stored: row.power_level},
                        {field: 'decision_status', sent: testData.decisionStatus, stored: row.decision_status}
                    ];
                    
                    let allValid = true;
                    validations.forEach(v => {
                        const matches = v.sent === v.stored;
                        const symbol = matches ? '✓' : '✗';
                        console.log(`${symbol} ${v.field}: ${v.sent} → ${v.stored}`);
                        if (!matches) allValid = false;
                    });
                    
                    console.log("\n=== RESULT ===");
                    if (allValid) {
                        console.log("✅ ALL FIELDS VALIDATED - NO TRANSLATION ERRORS");
                    } else {
                        console.log("❌ FIELD MISMATCH DETECTED - TRANSLATION ERROR");
                    }
                    
                    db.close();
                }
            );
        }, 500);
    });
});

req.on('error', (e) => {
    console.error("Request error:", e);
});

req.write(postData);
req.end();
```

**Run:**
```powershell
node test_data_integrity.js
```

**✅ Pass Criteria**: All 13 fields show `✓` with exact matches

---

### Phase 5: Edge Cases and Stress Testing

#### Test 5.1: Rapid Multi-Scan (Batch)
**Steps:**
1. Scan 5 different bricks rapidly (within 10 seconds)
2. All should sync successfully
3. Counter should show `5` (or existing + 5)

**✅ Pass**: All bricks stored, no duplicates

---

#### Test 5.2: Invalid GPS Data
**Steps:**
1. Scan with GPS disabled (0,0 coordinates or NaN)
2. Should still be accepted (GPS optional)

**✅ Pass**: Placement stored with invalid/zero GPS

---

#### Test 5.3: Extreme RSSI Values
**Steps:**
1. Test with RSSI: -100 (very weak)
2. Test with RSSI: -20 (very strong)
3. Both should be accepted

**✅ Pass**: All RSSI ranges accepted

---

#### Test 5.4: Unicode in Session IDs
**Steps:**
1. Test with session ID containing special chars: `SESSION-测试-001`
2. Should be stored and retrieved correctly

**✅ Pass**: Unicode preserved

---

### Phase 6: Dashboard Visualization Testing

#### Test 6.1: Mason Filter
**Steps:**
1. Create placements for mason1 and mason2
2. Select mason1 in dashboard dropdown
3. Only mason1's placements should appear on map
4. Counter should show only mason1's count

**✅ Pass**: Filter works, no cross-contamination

---

#### Test 6.2: Wall Clustering
**Steps:**
1. Scan 10+ bricks in same location (within 10m)
2. Dashboard should show polygon wall
3. Individual dots should be inside polygon

**✅ Pass**: Walls appear with correct clustering

---

#### Test 6.3: Real-Time Updates
**Steps:**
1. Open dashboard
2. Scan brick on app
3. Dashboard should update within 5 seconds (auto-refresh)

**✅ Pass**: Dashboard reflects new placements without manual refresh

---

## 📊 Test Results Summary Template

```
=== MASON BRICK TRACKING TEST RESULTS ===
Date: [DATE]
Tester: [NAME]

Phase 1: Backend API Testing
  ✓ Authentication
  ✓ Sync - New Placement
  ✓ UPSERT - Valid Update
  ✓ UPSERT - Rejected (Too Recent)
  ✓ UPSERT - Rejected (No Improvement)
  ✓ Cross-Mason Duplicate Detection

Phase 2: Data Integrity
  ✓ All 17 fields stored
  ✓ Dashboard data format

Phase 3: Android App
  ✓ Login
  ✓ First scan and sync
  ✓ Rescan (UPSERT)
  ✓ Rescan rejected
  ✓ Different brick
  ✓ Offline mode

Phase 4: Field Translation
  ✓ All 13 fields validated

Phase 5: Edge Cases
  ✓ Rapid multi-scan
  ✓ Invalid GPS
  ✓ Extreme RSSI
  ✓ Unicode handling

Phase 6: Dashboard
  ✓ Mason filter
  ✓ Wall clustering
  ✓ Real-time updates

OVERALL: PASS / FAIL
Notes: [Any issues found]
```

---

## 🐛 Common Issues and Debugging

### Issue: Counter Mismatch Between App and Dashboard
**Debug:**
```powershell
# Check backend count
curl http://localhost:8080/api/placements/count?masonId=MASON_001

# Check app's Room DB
adb shell "run-as com.mason.bricktracking sqlite3 /data/data/com.mason.bricktracking/databases/mason_tracking_local.db 'SELECT COUNT(*) FROM brick_placements WHERE synced=1'"
```

### Issue: UPSERT Not Working
**Debug:**
```powershell
# Check backend logs for rejection reasons
# Look for: "✗ Rejected: too recent" or "✗ Rejected: no improvement"

# Verify time difference
node -e "
const db = require('sqlite3').verbose();
const conn = new db.Database('./mason_tracking.db');
conn.get('SELECT timestamp FROM placements WHERE brick_number = ? ORDER BY timestamp DESC LIMIT 1', ['YOUR-BRICK'], (err, row) => {
    if (row) {
        const diff = Date.now() - row.timestamp;
        console.log('Time since last scan:', Math.floor(diff/1000), 'seconds');
    }
});
"
```

### Issue: Fields Missing or Wrong Type
**Debug:**
```powershell
# Check database schema
node -e "
const db = require('sqlite3').verbose();
const conn = new db.Database('./mason_tracking.db');
conn.all('PRAGMA table_info(placements)', (err, rows) => {
    console.log('Database Schema:');
    rows.forEach(r => console.log(r.name, r.type));
});
"
```

---

## ✅ Production Readiness Checklist

- [ ] All Phase 1 tests pass (Backend API)
- [ ] All Phase 2 tests pass (Data Integrity)
- [ ] All Phase 3 tests pass (Android App)
- [ ] All Phase 4 tests pass (Field Translation)
- [ ] All Phase 5 tests pass (Edge Cases)
- [ ] All Phase 6 tests pass (Dashboard)
- [ ] No console errors in backend logs
- [ ] No crashes in Android app
- [ ] Counter accuracy: App matches dashboard 100%
- [ ] UPSERT validation working correctly
- [ ] Cross-mason duplicate detection working
- [ ] Offline mode syncs correctly
- [ ] Dashboard real-time updates working
- [ ] All 17 fields preserved end-to-end

---

## 🚀 Automated Test Suite (Future Enhancement)

For continuous testing, consider:
- **Backend**: Jest/Mocha unit tests for db.js functions
- **Android**: Espresso UI tests for scan workflow
- **API**: Postman collection with automated runs
- **E2E**: Selenium tests for dashboard

This current manual test suite ensures comprehensive validation of the entire pipeline.
