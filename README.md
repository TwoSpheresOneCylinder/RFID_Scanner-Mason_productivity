# Mason Brick Tracking

Android application for tracking mason brick placements using RFID technology.

## Features

- **User Authentication**: Mason login with unique Mason ID generation
- **RFID Integration**: Connects to UHF RFID readers via Bluetooth
- **Real-time Tracking**: Scans and tracks brick placements in real-time
- **Local Caching**: Stores placements locally when offline
- **Auto Sync**: Automatically syncs with backend every 10 placements
- **Counter Management**: Syncs placement counter with backend database

## Setup

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 27 or higher
- UHF RFID Reader (Bluetooth-enabled)

### Installation

1. Clone or copy this project to your workspace
2. Open the project in Android Studio
3. Update the backend URL in `ApiClient.java`:
   ```java
   private static final String BASE_URL = "http://YOUR_BACKEND_URL_HERE/";
   ```
4. Build and run the application

### Backend API Requirements

The app expects the following endpoints:

1. **POST /api/auth/login**
   - Request: `{ "username": "string", "password": "string" }`
   - Response: `{ "success": boolean, "message": "string", "masonId": "string" }`

2. **POST /api/placements/sync**
   - Request: `{ "masonId": "string", "placements": [{ "brickNumber": "string", "timestamp": long }] }`
   - Response: `{ "success": boolean, "message": "string", "lastPlacementNumber": int }`

3. **GET /api/placements/last?masonId=string**
   - Response: `{ "success": boolean, "lastPlacementNumber": int }`

## Usage

1. **Login**: Enter username and password to generate Mason ID
2. **Connect RFID**: Search and connect to your RFID reader via Bluetooth
3. **Start Tracking**: Press START to begin scanning brick placements
4. **Auto Sync**: Data automatically syncs every 10 placements
5. **Manual Sync**: Use manual sync button if needed

## Dependencies

- AndroidX AppCompat, Material Design
- Room Database for local storage
- Retrofit for REST API communication
- RFID Device API (DeviceAPI_ver20250209_release.aar)

## License

Proprietary - For internal use only
