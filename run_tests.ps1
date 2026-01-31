# Mason Brick Tracking - Quick Test Runner
# This script runs basic validation tests on the system

Write-Host "=== MASON BRICK TRACKING - SYSTEM TEST SUITE ===" -ForegroundColor Cyan
Write-Host ""

$backendPath = "c:\Users\cange\Downloads\Block RFID\Vendor Code\MasonBrickTracking\backend"

# Check if backend is running
Write-Host "[1/6] Checking backend status..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/placements/recent" -Method GET -TimeoutSec 3 -UseBasicParsing
    Write-Host "✅ Backend is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Backend not running. Start with: cd backend; node server.js" -ForegroundColor Red
    exit 1
}

# Test authentication
Write-Host "`n[2/6] Testing authentication..." -ForegroundColor Yellow
try {
    $loginBody = @{
        username = "mason1"
        password = "password123"
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $loginBody -ContentType "application/json"
    
    if ($response.success -eq $true) {
        Write-Host "✅ Authentication working - mason1 login successful" -ForegroundColor Green
        Write-Host "   Mason ID: $($response.masonId)" -ForegroundColor Gray
    } else {
        Write-Host "❌ Authentication failed" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "❌ Authentication error: $_" -ForegroundColor Red
    exit 1
}

# Test invalid login
Write-Host "`n[3/6] Testing invalid credentials..." -ForegroundColor Yellow
try {
    $invalidBody = @{
        username = "mason1"
        password = "wrongpassword"
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $invalidBody -ContentType "application/json" -ErrorAction Stop
    
    if ($response.success -eq $false) {
        Write-Host "✅ Invalid credentials correctly rejected" -ForegroundColor Green
    } else {
        Write-Host "❌ Security issue: Invalid password accepted!" -ForegroundColor Red
        exit 1
    }
} catch {
    # 401 or other error is expected
    Write-Host "✅ Invalid credentials correctly rejected" -ForegroundColor Green
}

# Test sync endpoint with new placement
Write-Host "`n[4/6] Testing sync endpoint (new placement)..." -ForegroundColor Yellow
try {
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    
    $syncBody = @{
        masonId = "MASON_001"
        placements = @(
            @{
                brickNumber = "QUICKTEST-$timestamp"
                timestamp = $timestamp
                latitude = 40.7128
                longitude = -74.0060
                altitude = 10.5
                accuracy = 5.0
                buildSessionId = "QUICKTEST-SESSION"
                eventSeq = 1
                rssiAvg = -50
                rssiPeak = -45
                readsInWindow = 10
                powerLevel = 26
                decisionStatus = "CONFIRMED"
            }
        )
    } | ConvertTo-Json -Depth 5

    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/placements/sync" -Method POST -Body $syncBody -ContentType "application/json"
    
    if ($response.success -eq $true -and $response.inserted -eq 1) {
        Write-Host "✅ Sync successful - 1 new placement added" -ForegroundColor Green
        Write-Host "   Last placement number: $($response.lastPlacementNumber)" -ForegroundColor Gray
    } else {
        Write-Host "❌ Sync failed or unexpected result" -ForegroundColor Red
        Write-Host "   Response: $($response | ConvertTo-Json)" -ForegroundColor Gray
        exit 1
    }
} catch {
    Write-Host "❌ Sync error: $_" -ForegroundColor Red
    exit 1
}

# Run data integrity test
Write-Host "`n[5/6] Running comprehensive data integrity test..." -ForegroundColor Yellow
try {
    Push-Location $backendPath
    $output = node test_data_integrity.js 2>&1
    $exitCode = $LASTEXITCODE
    Pop-Location
    
    if ($exitCode -eq 0) {
        Write-Host "✅ Data integrity test PASSED" -ForegroundColor Green
        # Show last few lines with result
        $output | Select-Object -Last 5 | ForEach-Object { Write-Host "   $_" -ForegroundColor Gray }
    } else {
        Write-Host "❌ Data integrity test FAILED" -ForegroundColor Red
        Write-Host $output
        exit 1
    }
} catch {
    Write-Host "❌ Data integrity test error: $_" -ForegroundColor Red
    exit 1
}

# Check database schema
Write-Host "`n[6/6] Verifying database schema..." -ForegroundColor Yellow
try {
    Push-Location $backendPath
    $schemaCheck = node -e "const sqlite3 = require('sqlite3').verbose(); const db = new sqlite3.Database('./mason_tracking.db'); db.all('PRAGMA table_info(placements)', (err, rows) => { if (err) { console.error(err); process.exit(1); } console.log(rows.length); db.close(); });" 2>&1 | Select-Object -Last 1
    Pop-Location
    
    $fieldCount = [int]$schemaCheck
    if ($fieldCount -ge 17) {
        Write-Host "✅ Database schema valid ($fieldCount fields)" -ForegroundColor Green
    } else {
        Write-Host "❌ Database schema incomplete (only $fieldCount fields, expected 17+)" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "❌ Schema check error: $_" -ForegroundColor Red
    exit 1
}

# Summary
Write-Host "`n" + ("=" * 60) -ForegroundColor Cyan
Write-Host "=== TEST SUITE SUMMARY ===" -ForegroundColor Cyan
Write-Host ("=" * 60) -ForegroundColor Cyan
Write-Host "✅ Backend connectivity" -ForegroundColor Green
Write-Host "✅ Authentication & security" -ForegroundColor Green
Write-Host "✅ Sync endpoint functionality" -ForegroundColor Green
Write-Host "✅ Data integrity (field-by-field)" -ForegroundColor Green
Write-Host "✅ Database schema" -ForegroundColor Green
Write-Host ("=" * 60) -ForegroundColor Cyan
Write-Host "`n✅ ALL TESTS PASSED - System is operational" -ForegroundColor Green
Write-Host "`nNext steps:" -ForegroundColor Yellow
Write-Host "1. Rebuild Android APK: cd MasonBrickTracking; .\gradlew assembleDebug" -ForegroundColor Gray
Write-Host "2. Install on device: adb install -r app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Gray
Write-Host "3. Clear app data: adb shell pm clear com.mason.bricktracking" -ForegroundColor Gray
Write-Host "4. Test end-to-end with RFID scanner" -ForegroundColor Gray
Write-Host "`nFor full test suite, see: TESTING_GUIDE.md`n" -ForegroundColor Cyan
