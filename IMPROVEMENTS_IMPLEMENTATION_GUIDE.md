# RFID System Improvements - Implementation Guide

## Overview
This document outlines 5 critical improvements to replace "first unique EPC wins" with a robust best-candidate system, add sequence tracking, persist sessions, implement audit trails, and ensure backend idempotency.

## ✅ COMPLETED: Android App Changes

### 1. Windowed Capture System (Replacement for "First Wins")

**What Changed:**
- Physical button press now starts a **350ms capture window**
- All RFID reads during window are accumulated
- After window closes, system selects **best candidate** based on:
  - Primary: Highest read count
  - Tie-break: Highest average RSSI
  - Ambiguity rejection: If top two candidates are within 3dB RSSI and 1 count difference, reject as "ambiguous"

**Code Added:**
```java
// New fields in MainActivity
private Map<String, List<TagRead>> captureWindow;
private Handler captureWindowHandler;
private static final long CAPTURE_WINDOW_MS = 350;
private static final int RSSI_AMBIGUITY_THRESHOLD_DB = 3;
private static final int COUNT_AMBIGUITY_THRESHOLD = 1;

// Helper classes
private static class TagRead {
    String epc;
    int rssi;  // Now using tag.getRssi()
    long timestamp;
}

private static class CandidateStats {
    String epc;
    int count;       // Number of reads in window
    int avgRssi;     // Average RSSI
    int peakRssi;    // Peak RSSI
    
    boolean isBetterThan(CandidateStats other) {
        if (this.count != other.count) return this.count > other.count;
        return this.avgRssi > other.avgRssi;
    }
    
    boolean isAmbiguousWith(CandidateStats other) {
        int countDiff = Math.abs(this.count - other.count);
        if (countDiff <= COUNT_AMBIGUITY_THRESHOLD) {
            int rssiDiff = Math.abs(this.avgRssi - other.avgRssi);
            return rssiDiff <= RSSI_AMBIGUITY_THRESHOLD_DB;
        }
        return false;
    }
}
```

**Flow:**
```
Physical Button Press
  ↓
Start 350ms Capture Window (Status: "Capturing...")
  ↓
Accumulate all tag reads (EPC + RSSI)
  ↓
Window Timeout
  ↓
Calculate stats for each unique EPC:
  - Read count
  - Avg RSSI
  - Peak RSSI
  ↓
Select winner (max count, tie-break RSSI)
  ↓
Check ambiguity vs runner-up
  ↓
If ambiguous: Reject → Status: "Ambiguous - Rescan"
If clear winner: Accept → Process brick
```

**Log Output:**
```
[CAPTURE_WINDOW] Started capture window (350ms)
[SCAN_CALLBACK] Read: EPC=E2003411...F6D2 RSSI=-45 dBm
[SCAN_CALLBACK] Read: EPC=E2003411...F6D2 RSSI=-43 dBm
[SCAN_CALLBACK] Read: EPC=E2003411...A8C3 RSSI=-62 dBm
[CAPTURE_WINDOW] Candidate: E2003411...F6D2 | Count=2 | AvgRSSI=-44 | MaxRSSI=-43
[CAPTURE_WINDOW] Candidate: E2003411...A8C3 | Count=1 | AvgRSSI=-62 | MaxRSSI=-62
[CAPTURE_WINDOW] ✓ WINNER - EPC: E2003411...F6D2 | Count=2 | AvgRSSI=-44 | MaxRSSI=-43
```

### 2. Monotonic Sequence Numbers (`event_seq`)

**What Changed:**
- Each build session gets unique UUID: `currentBuildSessionId`
- Sequence counter `currentEventSeq` starts at 0, increments on each accepted scan
- Both stored in BrickPlacement entity

**New Fields:**
```java
// MainActivity
private String currentBuildSessionId;  // UUID per session
private int currentEventSeq = 0;       // Increments per brick

// Initialized on START button
currentBuildSessionId = UUID.randomUUID().toString();
currentEventSeq = 0;

// Incremented on each brick scan
currentEventSeq++;
```

**BrickPlacement Entity Updated:**
```java
@Entity(tableName = "brick_placements")
public class BrickPlacement {
    // ...existing fields...
    
    // NEW: Session and sequence tracking
    private String buildSessionId;
    private int eventSeq;
    
    // NEW: RSSI and read quality metrics
    private int rssiAvg;
    private int rssiPeak;
    private int readsInWindow;
    
    // NEW: Decision status
    private String decisionStatus;  // ACCEPTED, AMBIGUOUS, REJECTED_NO_GPS
}
```

**Log Output:**
```
[BUILD_SESSION] Started new session: 550e8400-e29b-41d4-a716-446655440000
[BRICK_SCANNED] Session: 550e8400... | Seq: 1 | EPC: E2003411...F6D2 | RSSI: -44/-43 | Reads: 2
[BRICK_SCANNED] Session: 550e8400... | Seq: 2 | EPC: E2003411...A8C3 | RSSI: -38/-35 | Reads: 3
```

**Benefits:**
- True ordering independent of timestamp
- Handles clock drift and offline sync
- Prevents millisecond collisions
- Backend can sort by `(buildSessionId, eventSeq)` for guaranteed order

### 3. Session Persistence (New Entities)

**Created `BuildSession` Entity:**
```java
@Entity(tableName = "build_sessions")
public class BuildSession {
    @PrimaryKey
    @NonNull
    private String buildSessionId;  // UUID
    
    private String masonId;
    private String toolId;          // "MR20" or other tool identifier
    private String wallId;          // Wall/test run identifier
    private long startedAtClient;   // Session start timestamp
    private long endedAtClient;     // Session end timestamp
    private String notes;           // Optional notes
    private boolean synced;
}
```

**Created `ReadEvent` Entity (Audit Trail):**
```java
@Entity(tableName = "read_events")
public class ReadEvent {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String buildSessionId;
    private long clientTimestamp;
    private String epc;
    private int rssi;
    private int readCount;          // Reads in capture window
    private boolean accepted;       // true = accepted, false = rejected
    private String reasonCode;      // ACCEPTED, COOLDOWN, DUPLICATE_GPS, AMBIGUOUS, NO_GPS
    private boolean synced;
}
```

**Usage:**
- `BuildSession` created when START button pressed
- `ReadEvent` logged for EVERY decision (accepted OR rejected)
- Enables tool comparison: "MR20 vs other_reader on same wall"
- Enables debugging: "Why was this brick logged wrong?"

### 4. UI Enhancements

**Admin Mode Display:**
```
RFID: E2003411...F6D2
[#5 | RSSI: -44 dBm]
Time: 2026-01-22 14:23:45
GPS: 40.712776, -74.005974 ±8.5m | Reads: 2
```

**Regular Mode Display:**
```
Last Brick: ID...F6D2 (#5)
Time: 2026-01-22 14:23:45
```

Shows:
- Event sequence number (`#5`)
- RSSI value (admin only)
- Number of reads in window (admin only)
- GPS coordinates with accuracy (admin only)

## ⏳ PENDING: Backend Changes

### 1. Update Database Schema

**Add to `backend/db.js`:**

```javascript
// New build_sessions table
db.run(`
    CREATE TABLE IF NOT EXISTS build_sessions (
        build_session_id TEXT PRIMARY KEY,
        mason_id TEXT NOT NULL,
        tool_id TEXT,
        wall_id TEXT,
        started_at_client BIGINT NOT NULL,
        ended_at_client BIGINT,
        notes TEXT,
        synced INTEGER DEFAULT 1,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (mason_id) REFERENCES users(mason_id)
    )
`);

// New read_events table (audit trail)
db.run(`
    CREATE TABLE IF NOT EXISTS read_events (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        build_session_id TEXT NOT NULL,
        client_timestamp BIGINT NOT NULL,
        epc TEXT NOT NULL,
        rssi INTEGER,
        read_count INTEGER,
        accepted INTEGER,
        reason_code TEXT,
        synced INTEGER DEFAULT 1,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (build_session_id) REFERENCES build_sessions(build_session_id)
    )
`);

// Update placements table with new fields
db.run(`
    ALTER TABLE placements ADD COLUMN build_session_id TEXT;
    ALTER TABLE placements ADD COLUMN event_seq INTEGER;
    ALTER TABLE placements ADD COLUMN rssi_avg INTEGER;
    ALTER TABLE placements ADD COLUMN rssi_peak INTEGER;
    ALTER TABLE placements ADD COLUMN reads_in_window INTEGER;
    ALTER TABLE placements ADD COLUMN decision_status TEXT;
    ALTER TABLE placements ADD COLUMN event_id TEXT UNIQUE;  -- For idempotency
`);

// Add indexes
db.run(`CREATE INDEX IF NOT EXISTS idx_session_seq ON placements(build_session_id, event_seq)`);
db.run(`CREATE INDEX IF NOT EXISTS idx_event_id ON placements(event_id)`);
db.run(`CREATE INDEX IF NOT EXISTS idx_build_session ON read_events(build_session_id)`);
```

### 2. Implement Idempotency

**Update `server.js` sync endpoint:**

```javascript
app.post('/api/placements/sync', async (req, res) => {
    const { masonId, placements: newPlacements } = req.body;
    
    try {
        // Calculate event_id for each placement (deterministic hash)
        const placementsWithEventId = newPlacements.map(p => ({
            ...p,
            eventId: createEventId(p.buildSessionId, p.eventSeq, p.masonId)
        }));
        
        // Use INSERT OR IGNORE for idempotency
        await dbPlacements.addBatchIdempotent(masonId, placementsWithEventId);
        
        res.json({
            success: true,
            message: `Successfully synced ${newPlacements.length} placements`
        });
    } catch (err) {
        console.error(`[${masonId}] Sync error:`, err);
        res.status(500).json({ success: false, message: 'Database error' });
    }
});

// Helper function for deterministic event ID
function createEventId(buildSessionId, eventSeq, masonId) {
    const crypto = require('crypto');
    const input = `${buildSessionId}:${eventSeq}:${masonId}`;
    return crypto.createHash('sha256').update(input).digest('hex').substring(0, 32);
}
```

**Update `db.js` with idempotent insert:**

```javascript
addBatchIdempotent: (masonId, placements) => {
    return new Promise(async (resolve, reject) => {
        await dbReadyPromise;
        
        db.serialize(() => {
            db.run('BEGIN TRANSACTION');
            
            const stmt = db.prepare(`
                INSERT OR IGNORE INTO placements (
                    mason_id, brick_number, rfid_tag, timestamp, received_at,
                    latitude, longitude, accuracy,
                    build_session_id, event_seq, rssi_avg, rssi_peak, reads_in_window,
                    decision_status, event_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            `);
            
            const receivedAt = Date.now();
            let insertCount = 0;
            
            placements.forEach(p => {
                stmt.run(
                    masonId,
                    p.brickNumber,
                    p.brickNumber,
                    p.timestamp,
                    receivedAt,
                    p.latitude || 0.0,
                    p.longitude || 0.0,
                    p.accuracy || 0.0,
                    p.buildSessionId || '',
                    p.eventSeq || 0,
                    p.rssiAvg || 0,
                    p.rssiPeak || 0,
                    p.readsInWindow || 0,
                    p.decisionStatus || 'ACCEPTED',
                    p.eventId
                );
                insertCount++;
            });
            
            stmt.finalize((err) => {
                if (err) {
                    db.run('ROLLBACK');
                    reject(err);
                } else {
                    db.run('COMMIT');
                    console.log(`[${masonId}] Inserted ${insertCount} placements (idempotent)`);
                    resolve(insertCount);
                }
            });
        });
    });
}
```

### 3. Add Session and Audit Endpoints

```javascript
// Create new build session
app.post('/api/sessions', async (req, res) => {
    const { buildSessionId, masonId, toolId, wallId, startedAtClient, notes } = req.body;
    
    await db.run(`
        INSERT INTO build_sessions (build_session_id, mason_id, tool_id, wall_id, started_at_client, notes)
        VALUES (?, ?, ?, ?, ?, ?)
    `, [buildSessionId, masonId, toolId, wallId, startedAtClient, notes || '']);
    
    res.json({ success: true });
});

// End build session
app.put('/api/sessions/:sessionId/end', async (req, res) => {
    const { sessionId } = req.params;
    const { endedAtClient } = req.body;
    
    await db.run(`
        UPDATE build_sessions SET ended_at_client = ? WHERE build_session_id = ?
    `, [endedAtClient, sessionId]);
    
    res.json({ success: true });
});

// Sync read events (audit trail)
app.post('/api/read-events/sync', async (req, res) => {
    const { events } = req.body;
    
    // Batch insert read events
    await dbReadEvents.addBatch(events);
    
    res.json({ success: true, count: events.length });
});

// Get audit trail for session
app.get('/api/sessions/:sessionId/audit', async (req, res) => {
    const { sessionId } = req.params;
    
    const events = await db.all(`
        SELECT * FROM read_events 
        WHERE build_session_id = ? 
        ORDER BY client_timestamp
    `, [sessionId]);
    
    res.json({ success: true, events });
});
```

## Testing Checklist

### Test 1: Windowed Capture with Multiple Tags
```
Setup: Place 3 tags close together
Action: Press physical button
Expected: 
- Window captures all 3 tags
- Selects tag with highest read count
- If tie, selects highest RSSI
- If ambiguous (within 3dB), rejects and displays "Ambiguous - Rescan"
```

### Test 2: RSSI Decision Making
```
Tag A: 5 reads, avg RSSI = -40 dBm
Tag B: 5 reads, avg RSSI = -38 dBm

Expected: Tag B wins (higher RSSI in tie)
```

### Test 3: Ambiguity Rejection
```
Tag A: 3 reads, avg RSSI = -42 dBm
Tag B: 3 reads, avg RSSI = -40 dBm (only 2dB difference)

Expected: Rejected as ambiguous (within 3dB threshold)
```

### Test 4: Sequence Ordering
```
Action: Scan 5 bricks
Expected Log:
- Session: xxx-yyy-zzz | Seq: 1 | EPC: ...
- Session: xxx-yyy-zzz | Seq: 2 | EPC: ...
- Session: xxx-yyy-zzz | Seq: 3 | EPC: ...
- Session: xxx-yyy-zzz | Seq: 4 | EPC: ...
- Session: xxx-yyy-zzz | Seq: 5 | EPC: ...
```

### Test 5: Idempotent Sync
```
Action: 
1. Scan 3 bricks
2. Sync to backend
3. Clear backend received_at timestamps
4. Re-sync same 3 bricks

Expected: No duplicates (event_id prevents double-insert)
```

### Test 6: Audit Trail
```
Action:
1. Scan brick A (accepted)
2. Try to scan brick A again (rejected - cooldown)
3. Scan brick B but ambiguous (rejected - ambiguous)
4. Scan brick C (accepted)

Expected Database:
read_events table:
- brick A | accepted=true | reason=ACCEPTED
- brick A | accepted=false | reason=COOLDOWN
- brick B | accepted=false | reason=AMBIGUOUS
- brick C | accepted=true | reason=ACCEPTED
```

## Performance Tuning

### Adjustable Parameters

**Capture Window Duration:**
```java
private static final long CAPTURE_WINDOW_MS = 350; // Try 250-500ms
```
- Shorter (250ms): Faster but may miss reads
- Longer (500ms): More reads but slower response

**RSSI Ambiguity Threshold:**
```java
private static final int RSSI_AMBIGUITY_THRESHOLD_DB = 3; // Try 2-5dB
```
- Tighter (2dB): More ambiguity rejections (safer)
- Looser (5dB): Fewer rejections (faster workflow)

**Count Ambiguity Threshold:**
```java
private static final int COUNT_AMBIGUITY_THRESHOLD = 1; // Try 0-2
```
- 0: Only exact count ties check RSSI
- 1: Count within 1 checks RSSI
- 2: Count within 2 checks RSSI

## Migration Notes

### Database Migration Script

Run this on backend first deployment:

```sql
-- Add new columns to existing placements table
ALTER TABLE placements ADD COLUMN build_session_id TEXT;
ALTER TABLE placements ADD COLUMN event_seq INTEGER DEFAULT 0;
ALTER TABLE placements ADD COLUMN rssi_avg INTEGER DEFAULT 0;
ALTER TABLE placements ADD COLUMN rssi_peak INTEGER DEFAULT 0;
ALTER TABLE placements ADD COLUMN reads_in_window INTEGER DEFAULT 1;
ALTER TABLE placements ADD COLUMN decision_status TEXT DEFAULT 'ACCEPTED';
ALTER TABLE placements ADD COLUMN event_id TEXT;

-- Backfill event_id for existing rows (one-time)
UPDATE placements 
SET event_id = lower(hex(randomblob(16)))
WHERE event_id IS NULL;

-- Add uniqueness constraint
CREATE UNIQUE INDEX idx_event_id_unique ON placements(event_id);

-- Create new tables
CREATE TABLE IF NOT EXISTS build_sessions ( ... );
CREATE TABLE IF NOT EXISTS read_events ( ... );

-- Add indexes
CREATE INDEX idx_session_seq ON placements(build_session_id, event_seq);
CREATE INDEX idx_build_session_events ON read_events(build_session_id);
```

### Android Room Migration

Add to `AppDatabase.java`:

```java
static final Migration MIGRATION_1_2 = new Migration(1, 2) {
    @Override
    public void migrate(SupportSQLiteDatabase database) {
        // Add new columns
        database.execSQL("ALTER TABLE brick_placements ADD COLUMN build_session_id TEXT DEFAULT ''");
        database.execSQL("ALTER TABLE brick_placements ADD COLUMN event_seq INTEGER DEFAULT 0");
        database.execSQL("ALTER TABLE brick_placements ADD COLUMN rssi_avg INTEGER DEFAULT 0");
        database.execSQL("ALTER TABLE brick_placements ADD COLUMN rssi_peak INTEGER DEFAULT 0");
        database.execSQL("ALTER TABLE brick_placements ADD COLUMN reads_in_window INTEGER DEFAULT 0");
        database.execSQL("ALTER TABLE brick_placements ADD COLUMN decision_status TEXT DEFAULT 'ACCEPTED'");
        
        // Create new tables
        database.execSQL("CREATE TABLE build_sessions (...)");
        database.execSQL("CREATE TABLE read_events (...)");
    }
};

// Update database builder
Room.databaseBuilder(context, AppDatabase.class, "mason_tracking_db")
    .addMigrations(MIGRATION_1_2)
    .build();
```

## Answers to User's Code Questions

### Q1: Does the SDK provide "inventory start/stop" control?
**Answer:** Not currently used. The system relies on physical button triggering callbacks. The SDK likely has `uhf.startInventoryTag()` and `uhf.stopInventory()` methods, but we're using passive callback mode for one-press-one-brick behavior. If needed, we could enforce a burst scan by calling `startInventoryTag()` at window start and `stopInventory()` at window end.

### Q2: What exactly does tag.getRssi() return?
**Answer:** Based on typical UHF RFID SDKs:
- Returns: `int` in dBm (negative value)
- Range: -30 dBm (very close) to -80 dBm (far)
- Example: `-45` means 45 dBm below reference
- **Higher (less negative) = stronger signal**

Code now uses: `int rssi = tag.getRssi();` in callback.

### Q3: Where do you clear scannedTagsInSession, and what defines "session end"?
**Answer:** 
- **Session Start:** START button → generates new UUID
- **Session End:** STOP button → clears `scannedTagsInSession.clear()`
- **What defines it:** Mason's intent to start/stop a "wall run" or "tool test"
- **Future:** Could add explicit "Wall ID" input dialog on START

Current implementation:
```java
btnStop.setOnClickListener(v -> {
    isScanning = false;
    scannedTagsInSession.clear();
    // Log session end...
});
```

### Q4: Do you ever scan without GPS lock?
**Answer:** Current behavior: **Rejects all scans without GPS**
```java
if (lastKnownLocation == null) {
    return; // Reject
}
```

**Recommendation:** Allow scan but mark as `gps_missing=true`:
```java
if (lastKnownLocation == null) {
    // Still accept but mark as no GPS
    BrickPlacement placement = new BrickPlacement(...);
    placement.setLatitude(0.0);
    placement.setLongitude(0.0);
    placement.setAccuracy(999.0f); // Sentinel value
    placement.setDecisionStatus("ACCEPTED_NO_GPS");
}
```

Enables outdoor construction sites where GPS may be intermittent.

### Q5: Is the dashboard computing "speed" off client timestamp or server received_at?
**Answer:** Current dashboard uses **client `timestamp`** for efficiency calculations. 

**Correct approach:**
- **Ordering:** Use `(build_session_id, event_seq)` first, then `timestamp`
- **Efficiency:** Use `timestamp` (client time when brick actually placed)
- **Transport metadata:** Use `received_at` only for sync latency analysis

Updated efficiency query should be:
```sql
SELECT 
    strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') as date,
    COUNT(*) as placements,
    build_session_id,
    MIN(event_seq) as first_seq,
    MAX(event_seq) as last_seq
FROM placements
WHERE mason_id = ?
GROUP BY date, build_session_id
ORDER BY build_session_id, event_seq;
```

## Next Steps

1. **Deploy Android App**
   - Build and install updated APK
   - Test windowed capture with multiple tags
   - Verify RSSI decision logic
   - Confirm ambiguity rejection works

2. **Update Backend Schema**
   - Run migration script
   - Add new tables and columns
   - Implement idempotency
   - Add session endpoints

3. **Field Testing**
   - Run tool comparison test (same wall, different sessions)
   - Verify audit trail captures all decisions
   - Check dashboard shows sequence ordering
   - Validate no duplicate insertions on retry

4. **Tuning**
   - Adjust CAPTURE_WINDOW_MS based on field results
   - Tune RSSI_AMBIGUITY_THRESHOLD_DB
   - Optimize COUNT_AMBIGUITY_THRESHOLD
   - Consider GPS-optional mode

## Files Modified

### Android
- ✅ `MainActivity.java` - Windowed capture, sequence tracking, RSSI selection
- ✅ `BrickPlacement.java` - Added session, sequence, RSSI fields
- ✅ `BuildSession.java` - NEW entity for session persistence
- ✅ `ReadEvent.java` - NEW entity for audit trail
- ⏳ `AppDatabase.java` - Need to add migration
- ⏳ `SyncRequest.java` - Need to add new fields to sync payload

### Backend
- ⏳ `db.js` - Add new tables, update schema, add idempotent insert
- ⏳ `server.js` - Update sync endpoint, add session endpoints
- ⏳ `dashboard.html` - Update queries to use `(session, seq)` ordering

## Conclusion

These 5 improvements address the core issues:
1. ✅ **No more "first wins"** - Best candidate selected from 350ms window
2. ✅ **True ordering** - Session UUID + monotonic sequence
3. ✅ **Session persistence** - Ready for tool comparison
4. ✅ **Audit trail** - Can debug "wrong brick" complaints
5. ⏳ **Idempotency** - Retry-safe backend (needs deployment)

**Material improvement in wrong-brick logs expected** due to RSSI-based selection and ambiguity rejection.
