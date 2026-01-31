# Mason Brick Tracking API Server

Node.js/Express backend with SQLite database for the RFID brick tracking Android app.

## Features

- ✅ User authentication
- ✅ Persistent SQLite database storage
- ✅ Brick placement tracking
- ✅ Multi-mason support
- ✅ Statistics and reporting endpoints
- ✅ RESTful API design

## Quick Start

### 1. Install Dependencies
```bash
cd backend
npm install
```

This will install:
- `express` - Web framework
- `body-parser` - Parse JSON request bodies
- `cors` - Enable cross-origin requests
- `sqlite3` - SQLite database driver

### 2. Start Server
```bash
npm start
```

Or for development with auto-reload:
```bash
npm run dev
```

The server will:
- Start on **http://0.0.0.0:8080**
- Create `mason_tracking.db` database file automatically
- Initialize tables and seed default users

## Database Structure

The SQLite database includes three tables:

### users
- `id` - Primary key
- `username` - Unique username
- `password` - Password (should be hashed in production)
- `mason_id` - Unique mason identifier
- `created_at` - Account creation timestamp

### placements
- `id` - Primary key
- `mason_id` - Foreign key to users
- `brick_number` - Brick identifier
- `rfid_tag` - RFID tag data (optional)
- `timestamp` - When placement was made (from device)
- `received_at` - When server received the data
- `synced` - Sync status flag
- `created_at` - Database record creation time

### sessions
- `id` - Primary key
- `mason_id` - Foreign key to users
- `token` - Session token
- `created_at` - Session creation time
- `expires_at` - Session expiration time

## API Endpoints

### Authentication

#### POST /api/auth/login
Login with username and password.

**Request:**
```json
{
  "username": "mason1",
  "password": "password123"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Login successful",
  "masonId": "MASON_001"
}
```

### Placements

#### POST /api/placements/sync
Sync brick placements from device to server.

**Request:**
```json
{
  "masonId": "MASON_001",
  "placements": [
    {
      "brickNumber": "BRICK_12345",
      "rfidTag": "E2801170000020127E8B0E94",
      "timestamp": 1704844800000
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "message": "Successfully synced 1 placements",
  "lastPlacementNumber": 42
}
```

#### GET /api/placements/last?masonId=MASON_001
Get the last placement number for a mason.

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "lastPlacementNumber": 42
}
```

#### GET /api/placements/mason/:masonId
Get all placements for a specific mason.

**Response:**
```json
{
  "success": true,
  "masonId": "MASON_001",
  "count": 42,
  "placements": [
    {
      "id": 1,
      "brickNumber": "BRICK_12345",
      "rfidTag": "E2801170000020127E8B0E94",
      "timestamp": 1704844800000,
      "receivedAt": 1704844801234,
      "createdAt": "2026-01-10 14:30:00"
    }
  ]
}
```

#### GET /api/placements/recent?limit=50
Get recent placements across all masons.

**Query Parameters:**
- `limit` (optional) - Number of records to return (default: 50)

**Response:**
```json
{
  "success": true,
  "count": 50,
  "placements": [
    {
      "id": 150,
      "masonId": "MASON_001",
      "username": "mason1",
      "brickNumber": "BRICK_12345",
      "rfidTag": "E2801170000020127E8B0E94",
      "timestamp": 1704844800000,
      "receivedAt": 1704844801234
    }
  ]
}
```

### Statistics

#### GET /api/statistics
Get overall statistics and per-mason metrics.

**Response:**
```json
{
  "success": true,
  "totalUsers": 5,
  "totalMasons": 3,
  "masonStats": [
    {
      "masonId": "MASON_001",
      "username": "mason1",
      "placementCount": 156,
      "firstPlacement": 1704844800000,
      "lastPlacement": 1705449600000
    }
  ]
}
```

### Debug Endpoints

#### GET /api/debug/placements
View all stored placements with statistics (for debugging).

**Response includes:**
- All placements with user information
- Statistics per mason
- Total placement count
- Total mason count

#### GET /api/debug/users
View all registered users.

**Response:**
```json
{
  "users": [
    {
      "id": 1,
      "username": "mason1",
      "masonId": "MASON_001",
      "createdAt": "2026-01-10 12:00:00"
    }
  ]
}
```

#### GET /api/health
Server health check.

**Response:**
```json
{
  "status": "OK",
  "timestamp": "2026-01-16T10:30:00.000Z",
  "uptime": 3600
}
```

## Default Test Users

| Username | Password     | Mason ID      |
|----------|--------------|---------------|
| mason1   | password123  | MASON_001     |
| mason2   | password123  | MASON_002     |
| admin    | admin123     | MASON_ADMIN   |

**Note:** These users are automatically created when the server first starts.

## Development Mode

The server currently runs in **development mode**:
- Any username/password combination is accepted (creates new user automatically)
- Mason ID is auto-generated as `MASON_<USERNAME>`
- All data is persisted to SQLite database (`mason_tracking.db`)

## Data Persistence

All data is stored in `mason_tracking.db` SQLite file in the backend directory. The database:
- Is created automatically on first run
- Persists all users and placements
- Can be backed up by copying the `.db` file
- Can be reset by deleting the `.db` file

## Production Deployment

For production use:

1. **Secure Authentication:**
   - Uncomment authentication validation in `/api/auth/login`
   - Implement password hashing (bcrypt)
   - Add JWT token-based authentication
   - Use the sessions table for token management

2. **Database:**
   - Consider migrating to PostgreSQL or MySQL for larger scale
   - Implement database backups
   - Add database connection pooling

3. **Security:**
   - Enable HTTPS with SSL certificates
   - Add rate limiting (express-rate-limit)
   - Implement security headers (helmet.js)
   - Add input validation and sanitization
   - Use environment variables for configuration

4. **Monitoring:**
   - Add logging (winston, morgan)
   - Implement error tracking (Sentry)
   - Add performance monitoring
   - Set up health checks

## Accessing from Android App

### From Android Emulator
Use base URL: `http://10.0.2.2:8080/api/`

### From Physical Device (Same Network)
1. Find your computer's local IP address:
   ```bash
   # Windows
   ipconfig
   # Look for "IPv4 Address" under your network adapter
   
   # Mac/Linux
   ifconfig
   # Look for "inet" address (e.g., 192.168.1.100)
   ```

2. Update the app's BASE_URL to: `http://<YOUR_IP>:8080/api/`
   Example: `http://192.168.1.100:8080/api/`

3. Ensure:
   - Computer and device are on the same WiFi network
   - Port 8080 is allowed through your firewall
   - Server is running and accessible

### Windows Firewall Configuration
If the device can't connect, allow Node.js through Windows Firewall:
```bash
# Run as Administrator in PowerShell
New-NetFirewallRule -DisplayName "Node.js Server" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
```

## Testing the API

You can test the API using curl, Postman, or any HTTP client:

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"mason1","password":"password123"}'
```

### Sync Placements
```bash
curl -X POST http://localhost:8080/api/placements/sync \
  -H "Content-Type: application/json" \
  -d '{
    "masonId": "MASON_001",
    "placements": [
      {
        "brickNumber": "BRICK_001",
        "rfidTag": "E2801170000020127E8B0E94",
        "timestamp": 1704844800000
      }
    ]
  }'
```

### Get Last Placement Number
```bash
curl http://localhost:8080/api/placements/last?masonId=MASON_001
```

### Get All Placements for a Mason
```bash
curl http://localhost:8080/api/placements/mason/MASON_001
```

### Get Recent Placements
```bash
curl http://localhost:8080/api/placements/recent?limit=10
```

### Get Statistics
```bash
curl http://localhost:8080/api/statistics
```

### Debug - View All Data
```bash
# All placements
curl http://localhost:8080/api/debug/placements

# All users
curl http://localhost:8080/api/debug/users

# Health check
curl http://localhost:8080/api/health
```

## Troubleshooting

### Port Already in Use
If port 8080 is already in use:
1. Change PORT in `server.js` (line 7)
2. Update Android app BASE_URL accordingly

### Database Locked Error
If you get "database is locked" errors:
- Close any SQLite browser tools that have the database open
- Restart the server

### Cannot Connect from Device
1. Verify server is running: `curl http://localhost:8080/api/health`
2. Check your IP address is correct
3. Verify firewall settings
4. Ensure both devices are on the same network
5. Try accessing from browser on device first

### Resetting the Database
To start fresh:
```bash
# Stop the server (Ctrl+C)
# Delete the database file
rm mason_tracking.db  # Mac/Linux
del mason_tracking.db  # Windows

# Restart the server - database will be recreated
npm start
```

## File Structure

```
backend/
├── package.json          # Dependencies and scripts
├── server.js            # Main Express server
├── db.js                # Database initialization and queries
├── mason_tracking.db    # SQLite database (auto-created)
└── README.md            # This file
```

## Next Steps

1. **Install dependencies:** `npm install`
2. **Start server:** `npm start`
3. **Test with curl** to verify it works
4. **Update Android app** BASE_URL to point to your server
5. **Run the Android app** and test data sync

## License

ISC

