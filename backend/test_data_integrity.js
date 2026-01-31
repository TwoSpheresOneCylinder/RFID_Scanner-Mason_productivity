const sqlite3 = require('sqlite3').verbose();
const http = require('http');

// Test data with all fields populated to maximum precision
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

console.log("=== MASON BRICK TRACKING - DATA INTEGRITY TEST ===\n");
console.log("=== SENDING TEST DATA TO API ===");
console.log(JSON.stringify(testData, null, 2));

// Send to API
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
        try {
            const response = JSON.parse(data);
            console.log(JSON.stringify(response, null, 2));
            
            if (!response.success) {
                console.error("❌ API ERROR:", response.message);
                process.exit(1);
            }
        } catch (e) {
            console.error("❌ Invalid JSON response:", data);
            process.exit(1);
        }
        
        // Wait for database write, then verify
        setTimeout(() => {
            const db = new sqlite3.Database('./mason_tracking.db');
            
            db.get(
                'SELECT * FROM placements WHERE brick_number = ? ORDER BY timestamp DESC LIMIT 1',
                [testData.brickNumber],
                (err, row) => {
                    if (err) {
                        console.error("❌ Database error:", err);
                        db.close();
                        process.exit(1);
                    }
                    
                    if (!row) {
                        console.error("❌ No record found in database for brick:", testData.brickNumber);
                        db.close();
                        process.exit(1);
                    }
                    
                    console.log("\n=== DATABASE RECORD ===");
                    console.log(JSON.stringify(row, null, 2));
                    
                    console.log("\n=== FIELD-BY-FIELD VALIDATION ===");
                    
                    const validations = [
                        {
                            field: 'brick_number',
                            sent: testData.brickNumber,
                            stored: row.brick_number,
                            type: 'string'
                        },
                        {
                            field: 'timestamp',
                            sent: testData.timestamp,
                            stored: row.timestamp,
                            type: 'number'
                        },
                        {
                            field: 'latitude',
                            sent: testData.latitude,
                            stored: row.latitude,
                            type: 'float',
                            tolerance: 0.000001
                        },
                        {
                            field: 'longitude',
                            sent: testData.longitude,
                            stored: row.longitude,
                            type: 'float',
                            tolerance: 0.000001
                        },
                        {
                            field: 'altitude',
                            sent: testData.altitude,
                            stored: row.altitude,
                            type: 'float',
                            tolerance: 0.001
                        },
                        {
                            field: 'accuracy',
                            sent: testData.accuracy,
                            stored: row.accuracy,
                            type: 'float',
                            tolerance: 0.001
                        },
                        {
                            field: 'build_session_id',
                            sent: testData.buildSessionId,
                            stored: row.build_session_id,
                            type: 'string'
                        },
                        {
                            field: 'event_seq',
                            sent: testData.eventSeq,
                            stored: row.event_seq,
                            type: 'number'
                        },
                        {
                            field: 'rssi_avg',
                            sent: testData.rssiAvg,
                            stored: row.rssi_avg,
                            type: 'number'
                        },
                        {
                            field: 'rssi_peak',
                            sent: testData.rssiPeak,
                            stored: row.rssi_peak,
                            type: 'number'
                        },
                        {
                            field: 'reads_in_window',
                            sent: testData.readsInWindow,
                            stored: row.reads_in_window,
                            type: 'number'
                        },
                        {
                            field: 'power_level',
                            sent: testData.powerLevel,
                            stored: row.power_level,
                            type: 'number'
                        },
                        {
                            field: 'decision_status',
                            sent: testData.decisionStatus,
                            stored: row.decision_status,
                            type: 'string'
                        }
                    ];
                    
                    let allValid = true;
                    let passCount = 0;
                    let failCount = 0;
                    
                    validations.forEach(v => {
                        let matches = false;
                        let details = '';
                        
                        if (v.type === 'float' && v.tolerance) {
                            // For floating point, check within tolerance
                            const diff = Math.abs(v.sent - v.stored);
                            matches = diff <= v.tolerance;
                            details = `(diff: ${diff.toFixed(6)}, tolerance: ${v.tolerance})`;
                        } else if (v.type === 'number') {
                            matches = v.sent === v.stored;
                        } else {
                            // String comparison
                            matches = String(v.sent) === String(v.stored);
                        }
                        
                        const symbol = matches ? '✅' : '❌';
                        const arrow = matches ? '→' : '≠';
                        
                        console.log(`${symbol} ${v.field.padEnd(20)} ${v.sent} ${arrow} ${v.stored} ${details}`);
                        
                        if (matches) {
                            passCount++;
                        } else {
                            failCount++;
                            allValid = false;
                        }
                    });
                    
                    // Check computed fields exist
                    console.log("\n=== COMPUTED FIELDS ===");
                    const computedChecks = [
                        {field: 'event_id', value: row.event_id, expected: `${testData.buildSessionId}-${testData.eventSeq}`},
                        {field: 'received_at', value: row.received_at},
                        {field: 'created_at', value: row.created_at},
                        {field: 'mason_id', value: row.mason_id, expected: 'MASON_001'}
                    ];
                    
                    computedChecks.forEach(c => {
                        const hasValue = c.value !== null && c.value !== undefined;
                        const matchesExpected = c.expected ? c.value === c.expected : true;
                        const symbol = (hasValue && matchesExpected) ? '✅' : '❌';
                        console.log(`${symbol} ${c.field.padEnd(20)} ${c.value} ${c.expected ? `(expected: ${c.expected})` : ''}`);
                        if (!hasValue || !matchesExpected) {
                            allValid = false;
                            failCount++;
                        } else {
                            passCount++;
                        }
                    });
                    
                    console.log("\n" + "=".repeat(60));
                    console.log("=== TEST RESULT ===");
                    console.log(`Passed: ${passCount}`);
                    console.log(`Failed: ${failCount}`);
                    console.log("=".repeat(60));
                    
                    if (allValid) {
                        console.log("\n✅ ALL FIELDS VALIDATED SUCCESSFULLY");
                        console.log("✅ NO TRANSLATION ERRORS DETECTED");
                        console.log("✅ DATA INTEGRITY: 100%\n");
                        db.close();
                        process.exit(0);
                    } else {
                        console.log("\n❌ FIELD MISMATCH DETECTED");
                        console.log("❌ TRANSLATION ERROR - INVESTIGATE DATA FLOW");
                        console.log("❌ DATA INTEGRITY: FAILED\n");
                        db.close();
                        process.exit(1);
                    }
                }
            );
        }, 500);
    });
});

req.on('error', (e) => {
    console.error("❌ Request error:", e.message);
    process.exit(1);
});

req.write(postData);
req.end();
