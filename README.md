# Mason Brick Tracking

RFID-based brick placement tracking system for masonry construction sites. Tracks which bricks each mason places, where, and when — in real time.

## System Components

| Component | Tech | Purpose |
|-----------|------|---------|
| **Android App** | Java · Room · Retrofit2 | RFID scanning, GPS capture, local queue, sync |
| **Backend API** | Node.js · Express · SQLite | Data storage, UPSERT validation, analytics |
| **Web Dashboard** | HTML · Chart.js · Leaflet | Live monitoring, wall clustering, performance reports |
| **RFID Hardware** | Chainway MR20 UHF (BLE) | Physical tag scanning |

> 📐 Full architecture with diagrams → [ARCHITECTURE.md](ARCHITECTURE.md)

## Quick Start

### 1. Backend
```bash
cd backend
npm install
node server.js
# → Listening on port 8080
# → Default users: mason1/password123, mason2/password123, admin/admin123
```

### 2. Android App
1. Open project root in Android Studio
2. Update `ApiClient.java` → `BASE_URL` to your server IP
3. Build & install on device
4. Login → Connect RFID reader via Bluetooth → Press START

### 3. Dashboard
Open `http://<server-ip>:8080/dashboard.html` in any browser.

## How It Works

1. Mason presses physical button on MR20 scanner
2. App captures all RFID reads in a **350ms window**, selects best candidate by RSSI
3. GPS location attached, placement saved locally
4. **Immediate sync** to backend (threshold = 1 placement)
5. Backend validates via UPSERT logic (30s gap, improvement checks, cross-mason dedup)
6. Dashboard updates in real time (1-10s polling)

## Key Features

- **Continuous scanning** — press START once, then just scan bricks
- **RSSI-based winner selection** — 350ms capture window, best signal wins
- **Three-level duplicate prevention** — app cooldown → GPS distance → backend UPSERT
- **Offline resilient** — Room DB queues scans, auto-syncs on reconnect
- **Multi-mason concurrent** — per-mason isolation, cross-mason conflict detection
- **Placement history** — full audit trail, brick journey from pallet to wall
- **Wall clustering** — DBSCAN groups nearby bricks into wall polygons on map
- **Performance reports** — per-mason HTML reports with charts
- **Battery monitoring** — live battery icon with percentage overlay
- **Wake lock** — screen can turn off, scanning continues

## Project Structure

```
MasonBrickTracking/
├── app/                    # Android application
│   ├── libs/               # RFID SDK (.aar)
│   └── src/main/java/com/mason/bricktracking/
│       ├── ui/             # Activities (Main, Login, Connection)
│       ├── data/           # Room entities, Retrofit API, models
│       ├── sync/           # SyncManager
│       └── service/        # BatteryTestService
├── backend/                # Node.js server
│   ├── server.js           # Express API (957 lines)
│   ├── db.js               # SQLite schema + queries (791 lines)
│   ├── reportGenerator.js  # HTML report generation
│   └── public/dashboard.html  # Web dashboard (2001 lines)
├── ARCHITECTURE.md         # Full system documentation with Mermaid diagrams
└── README.md               # This file
```

## Requirements

- Android SDK 27+ (device with BLE)
- Chainway MR20 UHF RFID reader (or compatible)
- Node.js 14+
- Any modern browser for dashboard
Proprietary - For internal use only
