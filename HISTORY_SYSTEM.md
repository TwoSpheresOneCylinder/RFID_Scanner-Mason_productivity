# Placement History System - Audit Trail

## Overview

The system now maintains a **complete audit trail** of every brick scan, preserving historical data even when placements are updated (UPSERT).

## Architecture

### Two-Table Design

**`placements` table** - Current/Latest State
- Shows the most recent location and data for each brick
- Fast queries for dashboard, map, and statistics
- Updated when brick is rescanned with improvements

**`placement_history` table** - Complete Audit Trail
- Archives every scan before updates
- Preserves full journey: pallet → intermediate → final wall position
- Never deleted, append-only log
- Includes `action_type` and `archived_at` timestamps

## Data Flow

```
1. New Brick Scan → INSERT into placements → Counter increments
2. Rescan Same Brick → 
   a. Archive current state to placement_history
   b. UPDATE placements with new data
   c. Counter stays the same (UPSERT)
```

## History Table Schema

```sql
CREATE TABLE placement_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    placement_id INTEGER,              -- References placements.id
    mason_id TEXT NOT NULL,
    brick_number TEXT NOT NULL,
    rfid_tag TEXT,
    timestamp BIGINT NOT NULL,
    received_at BIGINT,
    latitude REAL,
    longitude REAL,
    altitude REAL,
    accuracy REAL,
    build_session_id TEXT,
    event_seq INTEGER,
    rssi_avg INTEGER,
    rssi_peak INTEGER,
    reads_in_window INTEGER,
    power_level INTEGER,
    decision_status TEXT,
    event_id TEXT,
    action_type TEXT,                  -- 'INSERT' or 'UPDATE'
    archived_at DATETIME DEFAULT CURRENT_TIMESTAMP
)
```

## API Endpoints

### Get Complete History for a Brick
```bash
GET /api/history/brick/:brickNumber

Response:
{
  "success": true,
  "brickNumber": "E28011B0A50500768C7FB6E2",
  "totalScans": 3,
  "history": [
    {
      "timestamp": 1769786978907,
      "latitude": 40.7128,
      "longitude": -74.0060,
      "accuracy": 15.0,
      "record_type": "HISTORY",
      "archived_at": "2026-01-30T10:15:00.000Z"
    },
    {
      "timestamp": 1769787458104,
      "latitude": 40.7130,
      "longitude": -74.0058,
      "accuracy": 5.0,
      "record_type": "HISTORY",
      "archived_at": "2026-01-30T10:20:00.000Z"
    },
    {
      "timestamp": 1769787890234,
      "latitude": 40.7132,
      "longitude": -74.0055,
      "accuracy": 2.0,
      "record_type": "CURRENT"
    }
  ]
}
```

**Use Cases:**
- Track brick's journey from pallet to wall
- See GPS accuracy improvements over time
- Verify RSSI quality progression
- Audit mason's placement history

### Get Mason's Archived Scans
```bash
GET /api/history/mason/:masonId?limit=100

Response:
{
  "success": true,
  "masonId": "MASON_001",
  "totalRecords": 45,
  "history": [...]
}
```

**Use Cases:**
- Review all updated/corrected placements
- Analyze GPS accuracy trends
- Quality control auditing

## Real-World Examples

### Example 1: Pallet → Wall Journey
```
Scan 1 (Pallet):
  - GPS: (40.7128, -74.0060) accuracy: 15m
  - RSSI: -65 dBm
  - Action: INSERT into placements

Scan 2 (Moved to wall):
  - GPS: (40.7132, -74.0055) accuracy: 3m
  - RSSI: -55 dBm
  - Action: 
    1. Archive Scan 1 to placement_history
    2. UPDATE placements with Scan 2 data
  - Counter: Still 1 (not 2)
```

### Example 2: GPS Fix Improvement
```
Scan 1: GPS acquiring, low accuracy (50m)
Scan 2: GPS locked, better accuracy (5m)
Scan 3: Final position, best accuracy (2m)

History preserves all 3 states, but placements shows only Scan 3.
```

## Dashboard Integration (Future Enhancement)

You can add a "History" button next to each placement in the dashboard:

```javascript
// Example: Fetch and display brick history
async function showBrickHistory(brickNumber) {
    const response = await fetch(`${API_BASE}/api/history/brick/${brickNumber}`);
    const data = await response.json();
    
    if (data.success) {
        console.log(`${data.brickNumber} was scanned ${data.totalScans} times:`);
        data.history.forEach((scan, idx) => {
            console.log(`${idx + 1}. ${new Date(scan.timestamp).toLocaleString()}`);
            console.log(`   GPS: (${scan.latitude}, ${scan.longitude}) ±${scan.accuracy}m`);
            console.log(`   RSSI: ${scan.rssi_avg} dBm`);
            console.log(`   Type: ${scan.record_type}`);
        });
    }
}
```

## Benefits

✅ **Never lose data** - Every scan is preserved
✅ **GPS improvement tracking** - See accuracy progression
✅ **Audit compliance** - Complete trail for verification
✅ **Quality analysis** - Identify patterns in rescans
✅ **Fast queries** - Current state in `placements`, history separate
✅ **UPSERT validation** - History proves why updates were accepted

## Query Examples

### Get all scans for a brick with location changes
```sql
SELECT 
    brick_number,
    timestamp,
    latitude,
    longitude,
    accuracy,
    record_type
FROM (
    SELECT *, 'CURRENT' as record_type FROM placements WHERE brick_number = ?
    UNION ALL
    SELECT *, 'HISTORY' as record_type FROM placement_history WHERE brick_number = ?
)
ORDER BY timestamp ASC;
```

### Count how many times bricks were rescanned
```sql
SELECT 
    brick_number,
    COUNT(*) as total_scans,
    COUNT(*) - 1 as rescans
FROM placement_history
GROUP BY brick_number
HAVING COUNT(*) > 1
ORDER BY rescans DESC;
```

### GPS accuracy improvement analysis
```sql
SELECT 
    brick_number,
    MIN(accuracy) as best_accuracy,
    MAX(accuracy) as worst_accuracy,
    MAX(accuracy) - MIN(accuracy) as improvement_meters
FROM (
    SELECT brick_number, accuracy FROM placements
    UNION ALL
    SELECT brick_number, accuracy FROM placement_history
)
GROUP BY brick_number
HAVING improvement_meters > 5
ORDER BY improvement_meters DESC;
```

## Storage Considerations

- **History grows over time** but disk space is cheap
- Typical scan: ~200 bytes
- 1 million scans ≈ 200 MB
- Consider archiving old history (>1 year) to separate database if needed
- Current implementation: no automatic cleanup (audit trail)

## Testing the History System

1. **Scan a brick** → Check `placements` table (1 record)
2. **Wait 31+ seconds, rescan same brick** → Check `placement_history` (1 archived record), `placements` (1 updated record)
3. **Query history API**: `GET /api/history/brick/YOUR-BRICK-NUMBER`
4. **Verify both scans appear** in chronological order

---

**Summary:** Every scan is now preserved forever. Current state queries remain fast, while complete audit trail is available on demand.
