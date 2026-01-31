# Mason Brick Tracking System - Architecture Documentation

## A) System Architecture and Data Flow

### Exact Data Path
```
MR20 RFID Scanner → Bluetooth BLE → Android OS → Mason App → REST API (Node.js/Express) → SQLite DB → Web Dashboard
```

**Detailed Flow:**
1. MR20 sends tag data via BLE GATT characteristics
2. Android app receives via RFIDWithUHFBLE SDK (vendor SDK)
3. App processes, adds GPS, stores locally in Room DB
4. App syncs to backend via HTTP POST
5. Backend stores in SQLite
6. Web dashboard polls backend API for real-time display

### Platform
- **Mobile App:** Android only (API 27-34, minSdk 27, targetSdk 34)
- **Device:** Handheld Android phone/tablet with BLE
- **Scanner:** MR20 UHF RFID reader (Bluetooth connection)

### MR20 Integration Method
- **Vendor SDK:** `RFIDWithUHFBLE.getInstance()` from `com.rscja.deviceapi`
- **Connection:** Bluetooth Low Energy (BLE)
- **Library:** `DeviceAPI_ver20250209_release.aar` in `app/libs/`
- **NOT using:** GATT directly, SPP serial, or keyboard wedge

---

## B) What the Reader Sends

### Fields Received from MR20
When a tag is read via callback, we receive:
```java
UHFTAGInfo tag = {
    EPC: String,           // Electronic Product Code (96-bit typically)
    RSSI: (not used),      // Signal strength (available but not captured)
    TID: (not used),       // Tag Identifier (not captured)
    Timestamp: (none)      // No reader timestamp - we generate client-side
}
```

**Currently captured:**
- ✅ EPC only (24-character hex string)
- ❌ RSSI available but not logged
- ❌ TID not requested
- ❌ Reader timestamp not provided
- ❌ Antenna/power info not captured

### Raw Sample Payload (from Android log)
```
SCAN_CALLBACK: ✓ NEW TAG - EPC: E28011B0A50500768C7FB6D2 | Session count: 1
```

**Processing in code (MainActivity.java line 176):**
```java
@Override
public void callback(UHFTAGInfo tag) {
    if (tag != null && tag.getEPC() != null) {
        String epc = tag.getEPC().trim().toUpperCase();
        // Only EPC is extracted, RSSI ignored
    }
}
```

### Streaming Behavior
- **Continuous callback listening:** Yes, inventory callback is always active
- **Tag reads:** Only occur when physical scanner button is pressed
- **No automatic streaming:** Scanner requires button press per read attempt

---

## C) Scan Control and User Interaction Model

### Current Scanning Mechanism (as of latest update)

**How scanning starts:**
- ✅ **Physical MR20 button** (primary method)
- ❌ App UI button (disabled - shows "Use Scanner Button")
- ❌ Always-on scanning (not implemented)

**Implementation:**
```java
// MainActivity.java line 392
private void setupListeners() {
    // Scanning is controlled by physical RFID scanner button
    btnStart.setEnabled(false);
    btnStart.setText("Use Scanner Button");
    btnStop.setEnabled(false);
    btnStop.setVisibility(View.GONE);
}
```

### Trigger Event Detection

**Critical answer to your key question:**
```
WE DO NOT RECEIVE A CLEAN "TRIGGER PRESSED" EVENT
```

**What we actually receive:**
- Only tag read events via `IUHFInventoryCallback.callback(UHFTAGInfo tag)`
- No separate "scan button down/up" events
- No "trigger pressed" signal from SDK

**Current implementation uses scan gating:**
```java
// We emulate "one press = one tag" using:
1. Session-based tag tracking (HashSet<String> scannedTagsInSession)
2. Per-tag cooldown (500ms) to prevent rapid re-reads
3. GPS-based duplicate detection (time + distance thresholds)
```

### Desired Behavior
**User expectation:** "Press physical button once → capture one brick"

**Current reality:** Physical button starts scan, callback fires on tag detection, app handles deduplication

---

## D) Filtering / "Correct Tag" Selection Logic

### Multi-Tag Handling
When multiple EPCs are received in short period:

**Current behavior:**
```java
// MainActivity.java line 195-210
synchronized (scannedTagsInSession) {
    if (!scannedTagsInSession.contains(epc)) {
        scannedTagsInSession.add(epc);
        onBrickScanned(epc);  // ✓ Logs first new tag
    } else {
        // ✗ Already scanned - ignored
    }
}
```

**Policy:**
- ✅ **Logs first unique tag** in session
- ❌ **Ignores duplicates** (same EPC already in session)
- ❌ Does NOT log all tags
- ❌ Does NOT use RSSI for strongest selection
- ❌ Does NOT prompt user

### RSSI Access
```java
// Available via SDK but NOT captured:
tag.getRssi()  // Returns int, typically -70 to -30 dBm range
```

**Status:**
- ✅ RSSI accessible in code
- ❌ NOT currently logged or used
- ❌ NOT sent to backend
- Could be added for proximity-based filtering if needed

### EPC Format Control
- ❌ **Do NOT program tags** - EPCs are pre-programmed by brick manufacturer
- ✅ **EPCs are random** 96-bit hex strings (e.g., `E28011B0A50500768C7FB6D2`)
- No pattern enforcement or validation

---

## E) Ordering and Timestamps

### Timestamp Generation
**Client-side (Android phone):**
```java
// MainActivity.java line 505
long scanTimestamp = System.currentTimeMillis();  // Unix epoch ms
BrickPlacement placement = new BrickPlacement(
    masonId, 
    epc, 
    scanTimestamp,  // ← Generated on phone
    latitude, 
    longitude, 
    accuracy
);
```

**Server-side (Node.js backend):**
```javascript
// server.js line 127
received_at: Date.now()  // Separate server receipt timestamp
```

**Both timestamps stored:**
- `timestamp` (client) - actual scan moment
- `received_at` (server) - when API received it

### Sequence Ordering
- ❌ **No monotonic sequence number** (`event_seq`)
- ✅ **Relies on timestamps** for ordering
- ⚠️ **Risk:** Clock skew if phone time is wrong

**Room DB schema:**
```java
@PrimaryKey(autoGenerate = true)
private int id;  // Auto-increment, not exposed to API
```

### Offline Behavior
**Queue locally and sync later:**
```java
// SyncManager.java line 26
private static final int SYNC_THRESHOLD = 1;  // Sync immediately

// But if offline:
public void addPlacement(BrickPlacement placement) {
    executorService.execute(() -> {
        getDao().insert(placement);  // Stored locally
        unsyncedCount = getDao().getUnsyncedCount();
        
        if (unsyncedCount >= SYNC_THRESHOLD) {
            attemptSync();  // Tries to sync, fails gracefully if offline
        }
    });
}
```

**Offline strategy:**
- ✅ Scans continue normally
- ✅ Data queued in Room DB (`synced=false`)
- ✅ Auto-retry when network returns
- ❌ No blocking - user can keep scanning

---

## F) Backend / DB Schema and Integrity Constraints

### Database: SQLite (`mason_tracking.db`)

### Table: `placements`
```sql
CREATE TABLE placements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mason_id TEXT NOT NULL,                    -- User identifier
    brick_number TEXT NOT NULL,                -- EPC from RFID tag
    rfid_tag TEXT,                             -- Duplicate of brick_number
    timestamp BIGINT NOT NULL,                 -- Client scan time (Unix ms)
    received_at BIGINT NOT NULL,               -- Server receipt time (Unix ms)
    synced INTEGER DEFAULT 1,                  -- Always 1 on server
    latitude REAL DEFAULT 0.0,                 -- GPS latitude
    longitude REAL DEFAULT 0.0,                -- GPS longitude
    accuracy REAL DEFAULT 0.0,                 -- GPS accuracy (meters)
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (mason_id) REFERENCES users(mason_id)
);
```

### Uniqueness Constraints
**Current enforcement:**
- ❌ **No DB-level unique constraint** on `(mason_id, brick_number)`
- ✅ **Application-level deduplication** via session tracking
- ✅ **Allows duplicates** in DB (same EPC can be rescanned across sessions)

**Rationale:** Admin users need ability to re-scan same brick in testing

### Session Management
**No formal sessions in DB:**
- ❌ No `build_session_id` field
- ❌ No `tool_id` or `wall_id`
- ✅ Sessions exist only in client app memory (`HashSet<String>`)
- ⚠️ Session clears on app restart or admin stop button

### Audit Trail
**Raw reads vs final chosen tag:**
- ❌ **No separate audit log** of rejected reads
- ❌ **No RSSI logging**
- ✅ **Only accepted tags** stored in DB
- Logs exist in Android logcat only (not persisted)

### Table: `users`
```sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    mason_id TEXT UNIQUE NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

---

## Key Artifacts

### 1. Component Diagram (Text)
```
┌─────────────────────────────────────────────────────────────────┐
│ HARDWARE LAYER                                                  │
├─────────────────────────────────────────────────────────────────┤
│ MR20 UHF RFID Scanner                                          │
│   - Physical scan button                                        │
│   - BLE GATT connection                                         │
│   - Reads EPC tags in range                                     │
└────────────────┬────────────────────────────────────────────────┘
                 │ Bluetooth LE
                 ↓
┌─────────────────────────────────────────────────────────────────┐
│ ANDROID APP (minSdk 27, targetSdk 34)                         │
├─────────────────────────────────────────────────────────────────┤
│ MainActivity.java (637 lines)                                  │
│   - RFIDWithUHFBLE SDK integration                             │
│   - IUHFInventoryCallback.callback(UHFTAGInfo tag)            │
│   - Session deduplication (HashSet<String>)                    │
│   - GPS location capture (FusedLocationProviderClient)         │
│   - 500ms per-tag cooldown                                      │
│                                                                 │
│ Room Database (SQLite on device)                               │
│   - brick_placements table                                      │
│   - Local queue for offline scans                              │
│                                                                 │
│ SyncManager.java                                               │
│   - Auto-sync on each scan (SYNC_THRESHOLD=1)                  │
│   - Retrofit HTTP client                                        │
│   - Offline queue handling                                      │
└────────────────┬────────────────────────────────────────────────┘
                 │ HTTP POST /api/placements/sync
                 │ JSON: { masonId, placements: [{ brickNumber, timestamp, lat, lon, accuracy }] }
                 ↓
┌─────────────────────────────────────────────────────────────────┐
│ BACKEND API (Node.js + Express)                                │
├─────────────────────────────────────────────────────────────────┤
│ server.js (565 lines)                                          │
│   - POST /api/placements/sync                                  │
│   - GET  /api/placements/mason/:masonId                        │
│   - GET  /api/statistics/efficiency                            │
│   - DELETE /api/debug/purge (admin)                            │
│                                                                 │
│ SQLite Database (mason_tracking.db)                            │
│   - placements table (28 columns)                              │
│   - users table (5 columns)                                     │
└────────────────┬────────────────────────────────────────────────┘
                 │ HTTP GET /api/statistics/efficiency
                 │ HTTP GET /api/placements/mason/:id
                 ↓
┌─────────────────────────────────────────────────────────────────┐
│ WEB DASHBOARD (dashboard.html)                                │
├─────────────────────────────────────────────────────────────────┤
│   - Real-time placement log (1s refresh)                       │
│   - Live placement timeline chart (Chart.js)                   │
│   - Daily totals bar chart                                     │
│   - Efficiency metrics cards                                    │
│   - Auto-refresh every 10s                                      │
└─────────────────────────────────────────────────────────────────┘
```

### 2. Raw Tag Read Event (Android Logcat)
```
2026-01-22T20:31:11.044Z
SCAN_CALLBACK: ⏸ COOLDOWN - EPC: E28011B0A50500768C7FB6D2 | Wait 234ms
SCAN_CALLBACK: ✓ NEW TAG - EPC: E28011B0A50500768C7FB642 | Session count: 1
GPS: Location: 43.002773, -77.470458 | Accuracy: ±13.7m
ADMIN_RFID: Scanned: E28011B0A50500768C7FB642 | Count: 1 | Time: 1769113871044 | GPS: 43.002773, -77.470458 ±13.7m
```

### 3. Scan Handler Code
```java
// MainActivity.java lines 173-210
uhf.setInventoryCallback(new IUHFInventoryCallback() {
    @Override
    public void callback(UHFTAGInfo tag) {
        if (tag != null && tag.getEPC() != null) {
            // Physical button was pressed and tag detected
            String epc = tag.getEPC().trim().toUpperCase();
            
            if (epc.isEmpty()) return;
            
            // GPS check
            if (lastKnownLocation == null) return;
            
            // Cooldown check (500ms per tag)
            long currentTime = System.currentTimeMillis();
            synchronized (tagCooldowns) {
                Long lastScanTime = tagCooldowns.get(epc);
                if (lastScanTime != null && 
                    (currentTime - lastScanTime < SCAN_COOLDOWN_MS)) {
                    return; // Too soon, ignore
                }
                tagCooldowns.put(epc, currentTime);
            }
            
            // Session deduplication
            synchronized (scannedTagsInSession) {
                if (!scannedTagsInSession.contains(epc)) {
                    scannedTagsInSession.add(epc);
                    onBrickScanned(epc); // ← Process this tag
                } else {
                    // Already scanned in this session, ignore
                }
            }
        }
    }
});
```

### 4. API Endpoint Contract

**Request: POST /api/placements/sync**
```json
{
  "masonId": "MASON_ADMIN",
  "placements": [
    {
      "brickNumber": "E28011B0A50500768C7FB6D2",
      "timestamp": 1769113871044,
      "latitude": 43.002773,
      "longitude": -77.470458,
      "accuracy": 13.679
    }
  ]
}
```

**Response: 200 OK**
```json
{
  "success": true,
  "message": "Synced 1 placements",
  "lastPlacementNumber": 27,
  "syncedCount": 1
}
```

**Response: 500 Error**
```json
{
  "success": false,
  "message": "Database error"
}
```

### 5. Additional Duplicate Prevention Layer
```java
// MainActivity.java lines 515-545
// GPS-based duplicate detection (beyond session tracking)
private static final long DUPLICATE_TIME_THRESHOLD = 5000; // 5 seconds
private static final double BASE_DISTANCE_THRESHOLD = 2.0; // 2 meters

synchronized (recentPlacements) {
    PlacementRecord recent = recentPlacements.get(epc);
    if (recent != null) {
        long timeDiff = scanTimestamp - recent.timestamp;
        double distance = calculateDistance(
            recent.latitude, recent.longitude, 
            latitude, longitude
        );
        
        // Adjust threshold based on GPS accuracy
        double adjustedThreshold = BASE_DISTANCE_THRESHOLD + 
            Math.max(accuracy, recent.accuracy);
        
        if (timeDiff < DUPLICATE_TIME_THRESHOLD && 
            distance < adjustedThreshold) {
            // Duplicate detected - discard
            return;
        }
    }
    
    // Not a duplicate - store this placement
    recentPlacements.put(epc, new PlacementRecord(...));
}
```

---

## Answer to Your Key Decision Question

### Does the app receive a clean "trigger pressed" event?

**NO - We only receive tag reads**

**Implications:**
1. Cannot distinguish "button pressed but no tag in range" vs "button not pressed"
2. Cannot count failed scan attempts
3. Must emulate "one press = one block" using:
   - Session-based tag tracking
   - Cooldown windows (500ms per tag)
   - GPS-based duplicate detection
   - Application state management

**Current Implementation:**
```java
// State machine is implicit:
// 1. Callback fires only when physical button pressed AND tag detected
// 2. App checks if tag already in session (scannedTagsInSession)
// 3. App checks if tag scanned recently at this location (recentPlacements)
// 4. If both checks pass → onBrickScanned() → save to DB
```

**Reliability:**
- ✅ Works well for normal scanning workflow
- ✅ Prevents duplicates effectively (session + GPS + cooldown)
- ⚠️ Admin users can clear session to rescan same brick
- ❌ No way to detect "missed scans" (button pressed, no tag found)
- ❌ No scan attempt metrics

---

## Recommended Improvements for Production

1. **Add RSSI logging** for signal strength analysis
2. **Add sequence numbers** (`event_seq`) for guaranteed ordering
3. **Add session table** to track scan sessions formally
4. **Add audit table** to log all read attempts (accepted + rejected)
5. **Request reader timestamp** if SDK supports it
6. **Add DB unique constraint** option for strict deduplication mode
7. **Expose "scan attempt" metric** if SDK provides callback on button press without tag

---

## Current Limitations

1. **No trigger-down/trigger-up events** - rely on tag detection only
2. **No RSSI-based selection** - first unique tag wins
3. **No multi-antenna support** - single antenna assumed
4. **No reader-side timestamps** - all timestamps client-generated
5. **Session tracking in memory only** - lost on app restart
6. **No formal session IDs** - cannot group scans by work session
7. **No TID capture** - EPC only, no tag manufacturer data
8. **Clock skew risk** - ordering breaks if phone time is wrong

---

## Files Modified in Recent Updates

### Android App:
- `MainActivity.java` - Removed app button control, enabled physical button mode
- UI now shows "Use Scanner Button" (disabled state)
- Callback always listening for physical button presses

### Backend:
- `server.js` - Added `/api/debug/purge` endpoint to clear all placements
- `server.js` - Fixed efficiency statistics query (added masonSummary)

### Dashboard:
- `dashboard.html` - Filters live placements to show only today's data
- Charts update in place without destroying (smooth refresh)
- GPS-based timestamps in placement log

---

**Last Updated:** 2026-01-22  
**System Version:** Post-physical-button-control update
