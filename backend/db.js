const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const bcrypt = require('bcryptjs');

// Database file location
const DB_PATH = path.join(__dirname, 'mason_tracking.db');

let db;
let dbReady = false;
let dbReadyPromise;

// Initialize database connection and tables
function initializeDatabase() {
    dbReadyPromise = new Promise((resolve, reject) => {
        // Create and connect to database
        db = new sqlite3.Database(DB_PATH, (err) => {
            if (err) {
                console.error('Error opening database:', err.message);
                reject(err);
                return;
            }
            console.log('✓ Connected to SQLite database at:', DB_PATH);
            createTables(resolve, reject);
        });
    });
    return dbReadyPromise;
}

// Create database tables
function createTables(resolve, reject) {
    let tablesCreated = 0;
    const totalTables = 4;
    
    const checkComplete = () => {
        tablesCreated++;
        if (tablesCreated === totalTables) {
            dbReady = true;
            console.log('✓ Database initialization complete');
            resolve();
        }
    };
    
    db.serialize(() => {
        // Users table
        db.run(`
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                mason_id TEXT UNIQUE NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        `, (err) => {
            if (err) {
                console.error('Error creating users table:', err.message);
                reject(err);
            } else {
                console.log('✓ Users table ready');
                seedDefaultUsers();
                checkComplete();
            }
        });

        // Placements table with GPS fields
        db.run(`
            CREATE TABLE IF NOT EXISTS placements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                mason_id TEXT NOT NULL,
                brick_number TEXT NOT NULL,
                rfid_tag TEXT NOT NULL,
                timestamp BIGINT NOT NULL,
                received_at BIGINT NOT NULL,
                synced INTEGER DEFAULT 1,
                latitude REAL DEFAULT 0.0,
                longitude REAL DEFAULT 0.0,
                altitude REAL DEFAULT 0.0,
                accuracy REAL DEFAULT 0.0,
                build_session_id TEXT DEFAULT '',
                event_seq INTEGER DEFAULT 0,
                rssi_avg INTEGER DEFAULT 0,
                rssi_peak INTEGER DEFAULT 0,
                reads_in_window INTEGER DEFAULT 0,
                power_level INTEGER DEFAULT 0,
                decision_status TEXT DEFAULT 'ACCEPTED',
                event_id TEXT UNIQUE,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (mason_id) REFERENCES users(mason_id)
            )
        `, (err) => {
            if (err) {
                console.error('Error creating placements table:', err.message);
                reject(err);
            } else {
                console.log('✓ Placements table ready');
                // === Placement indexes ===
                // Composite: mason + timestamp (covers WHERE mason_id=? queries, ORDER BY timestamp, live feed since=)
                db.run(`CREATE INDEX IF NOT EXISTS idx_mason_timestamp ON placements(mason_id, timestamp DESC)`, (err) => {
                    if (err) console.error('Error creating mason_timestamp index:', err.message);
                    else console.log('✓ Index idx_mason_timestamp created');
                });
                // Composite: mason + brick + timestamp (covers the critical dedup lookup during sync)
                db.run(`CREATE INDEX IF NOT EXISTS idx_mason_brick ON placements(mason_id, brick_number, timestamp DESC)`, (err) => {
                    if (err) console.error('Error creating mason_brick index:', err.message);
                    else console.log('✓ Index idx_mason_brick created');
                });
                // Composite: brick + timestamp (covers cross-mason duplicate check)
                db.run(`CREATE INDEX IF NOT EXISTS idx_brick_timestamp ON placements(brick_number, timestamp DESC)`, (err) => {
                    if (err) console.error('Error creating brick_timestamp index:', err.message);
                    else console.log('✓ Index idx_brick_timestamp created');
                });
                // Global timestamp ordering (covers ORDER BY timestamp DESC for getAll, getRecent)
                db.run(`CREATE INDEX IF NOT EXISTS idx_timestamp ON placements(timestamp DESC)`, (err) => {
                    if (err) console.error('Error creating timestamp index:', err.message);
                    else console.log('✓ Index idx_timestamp created');
                });
                // Session + sequence (covers ORDER BY build_session_id, event_seq)
                db.run(`CREATE INDEX IF NOT EXISTS idx_session_seq ON placements(build_session_id, event_seq)`, (err) => {
                    if (err) console.error('Error creating session_seq index:', err.message);
                    else console.log('✓ Index idx_session_seq created');
                });
                // Note: event_id UNIQUE constraint already creates an implicit index
                // Note: idx_mason_id, idx_mason_time, idx_event_id removed (redundant)
                checkComplete();
            }
        });

        // Sessions table (for future token-based auth)
        db.run(`
            CREATE TABLE IF NOT EXISTS sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                mason_id TEXT NOT NULL,
                token TEXT UNIQUE NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                expires_at DATETIME,
                FOREIGN KEY (mason_id) REFERENCES users(mason_id)
            )
        `, (err) => {
            if (err) {
                console.error('Error creating sessions table:', err.message);
                reject(err);
            } else {
                console.log('✓ Sessions table ready');
                checkComplete();
            }
        });

        // Placement history table - stores every scan for audit trail
        db.run(`
            CREATE TABLE IF NOT EXISTS placement_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                placement_id INTEGER,
                mason_id TEXT NOT NULL,
                brick_number TEXT NOT NULL,
                rfid_tag TEXT,
                timestamp BIGINT NOT NULL,
                received_at BIGINT DEFAULT 0,
                latitude REAL DEFAULT 0.0,
                longitude REAL DEFAULT 0.0,
                altitude REAL DEFAULT 0.0,
                accuracy REAL DEFAULT 0.0,
                build_session_id TEXT DEFAULT '',
                event_seq INTEGER DEFAULT 0,
                rssi_avg INTEGER DEFAULT 0,
                rssi_peak INTEGER DEFAULT 0,
                reads_in_window INTEGER DEFAULT 0,
                power_level INTEGER DEFAULT 0,
                decision_status TEXT DEFAULT 'ACCEPTED',
                event_id TEXT,
                action_type TEXT DEFAULT 'INSERT',
                archived_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (mason_id) REFERENCES users(mason_id)
            )
        `, (err) => {
            if (err) {
                console.error('Error creating placement_history table:', err.message);
                reject(err);
            } else {
                console.log('✓ Placement history table ready');
                // Create indexes for historical queries
                db.run(`CREATE INDEX IF NOT EXISTS idx_history_placement_id ON placement_history(placement_id)`, (err) => {
                    if (err) console.error('Error creating history placement_id index:', err.message);
                    else console.log('✓ Index idx_history_placement_id created');
                });
                db.run(`CREATE INDEX IF NOT EXISTS idx_history_brick ON placement_history(brick_number, timestamp)`, (err) => {
                    if (err) console.error('Error creating history brick index:', err.message);
                    else console.log('✓ Index idx_history_brick created');
                });
                db.run(`CREATE INDEX IF NOT EXISTS idx_history_mason ON placement_history(mason_id, timestamp)`, (err) => {
                    if (err) console.error('Error creating history mason index:', err.message);
                    else console.log('✓ Index idx_history_mason created');
                });
                checkComplete();
            }
        });

        // Note: redundant single-column indexes (idx_placements_mason_id, idx_placements_timestamp,
        // idx_placements_brick_number) removed — all covered by composite indexes above
    });
}

// Seed default users with hashed passwords
function seedDefaultUsers() {
    const defaultUsers = [
        { username: 'mason1', password: 'password123', mason_id: 'MASON_001' },
        { username: 'mason2', password: 'password123', mason_id: 'MASON_002' },
        { username: 'admin', password: 'password123', mason_id: 'MASON_ADMIN' }
    ];

    defaultUsers.forEach(user => {
        // Hash password before storing
        const hashedPassword = bcrypt.hashSync(user.password, 10);
        
        db.run(
            `INSERT OR IGNORE INTO users (username, password, mason_id) VALUES (?, ?, ?)`,
            [user.username, hashedPassword, user.mason_id],
            (err) => {
                if (err && !err.message.includes('UNIQUE constraint')) {
                    console.error(`Error seeding user ${user.username}:`, err.message);
                }
            }
        );
    });
}

// ============================================
// DATABASE QUERY FUNCTIONS
// ============================================

// User functions
const dbUsers = {
    // Find user by username and verify password with bcrypt
    authenticate: async (username, password) => {
        return new Promise((resolve, reject) => {
            db.get(
                `SELECT * FROM users WHERE username = ?`,
                [username],
                async (err, row) => {
                    if (err) {
                        reject(err);
                    } else if (!row) {
                        resolve(null); // User not found
                    } else {
                        // Compare provided password with hashed password
                        const match = await bcrypt.compare(password, row.password);
                        if (match) {
                            resolve(row);
                        } else {
                            resolve(null); // Password doesn't match
                        }
                    }
                }
            );
        });
    },

    // Get all users
    getAll: () => {
        return new Promise((resolve, reject) => {
            db.all(`SELECT * FROM users`, (err, rows) => {
                if (err) reject(err);
                else resolve(rows);
            });
        });
    },

    // Get all users for dropdown (username + mason_id only)
    listAll: () => {
        return new Promise((resolve, reject) => {
            db.all(
                `SELECT username, mason_id FROM users ORDER BY username ASC`,
                (err, rows) => {
                    if (err) reject(err);
                    else resolve(rows || []);
                }
            );
        });
    },

    // Create new user with hashed password
    create: async (username, password, masonId) => {
        return new Promise(async (resolve, reject) => {
            // Hash password before storing
            const hashedPassword = await bcrypt.hash(password, 10);
            
            db.run(
                `INSERT INTO users (username, password, mason_id) VALUES (?, ?, ?)`,
                [username, hashedPassword, masonId],
                function(err) {
                    if (err) reject(err);
                    else resolve({ id: this.lastID, username, masonId });
                }
            );
        });
    }
};

// Placement functions
const dbPlacements = {
    // Filter out duplicate placements and mark existing ones for UPDATE
    // Returns: { toInsert: [], toUpdate: [] }
    filterDuplicates: async (masonId, placements) => {
        return new Promise(async (resolve, reject) => {
            await dbReadyPromise;
            
            try {
                const toInsert = [];
                const toUpdate = [];
                const DUPLICATE_WINDOW_MS = 5 * 60 * 1000; // 5 minutes for cross-mason check only
                
                for (const placement of placements) {
                    // Generate event_id for this placement
                    const eventId = `${placement.buildSessionId}-${placement.eventSeq}`;
                    placement.eventId = eventId;
                    
                    // Check if this brick already exists for this mason (any timestamp)
                    const existingForMason = await new Promise((res, rej) => {
                        db.get(
                            `SELECT id, timestamp, latitude, longitude, accuracy, rssi_avg, rssi_peak 
                             FROM placements 
                             WHERE mason_id = ? AND brick_number = ?
                             ORDER BY timestamp DESC LIMIT 1`,
                            [masonId, placement.brickNumber],
                            (err, row) => {
                                if (err) rej(err);
                                else res(row);
                            }
                        );
                    });
                    
                    if (existingForMason) {
                        // Brick exists - validate if this scan qualifies for UPDATE
                        const timeDiff = placement.timestamp - existingForMason.timestamp;
                        const timeDiffSeconds = Math.round(timeDiff / 1000);
                        
                        // Validation checks - NEW scan must meet at least one criteria:
                        const isNewer = timeDiff > 30000; // At least 30 seconds newer
                        const hasBetterAccuracy = placement.accuracy > 0 && 
                                                 (existingForMason.accuracy === 0 || 
                                                  placement.accuracy < existingForMason.accuracy);
                        const hasStrongerSignal = placement.rssiPeak > existingForMason.rssi_peak;
                        const hasValidGPS = placement.latitude !== 0 && placement.longitude !== 0 &&
                                          (existingForMason.latitude === 0 || existingForMason.longitude === 0);
                        
                        // Reject if scan is older or too recent
                        if (timeDiff < 0) {
                            console.log(`[${masonId}] ✗ Rejected: ${placement.brickNumber} - older scan (${Math.abs(timeDiffSeconds)}s older) - skipping`);
                            continue;
                        }
                        
                        if (timeDiff < 30000) {
                            console.log(`[${masonId}] ✗ Rejected: ${placement.brickNumber} - too recent (${timeDiffSeconds}s) - minimum 30s gap required`);
                            continue;
                        }
                        
                        // Check if new scan is actually better
                        if (!hasBetterAccuracy && !hasStrongerSignal && !hasValidGPS) {
                            console.log(`[${masonId}] ✗ Rejected: ${placement.brickNumber} - no improvement (accuracy: ${placement.accuracy}m vs ${existingForMason.accuracy}m, RSSI: ${placement.rssiPeak} vs ${existingForMason.rssi_peak}) - skipping`);
                            continue;
                        }
                        
                        // Valid UPDATE - log why it's being updated
                        let updateReasons = [];
                        if (isNewer) updateReasons.push(`${timeDiffSeconds}s newer`);
                        if (hasBetterAccuracy) updateReasons.push(`better accuracy: ${placement.accuracy}m vs ${existingForMason.accuracy}m`);
                        if (hasStrongerSignal) updateReasons.push(`stronger signal: ${placement.rssiPeak}dB vs ${existingForMason.rssi_peak}dB`);
                        if (hasValidGPS) updateReasons.push(`added GPS`);
                        
                        console.log(`[${masonId}] ↻ Valid update for ${placement.brickNumber}: ${updateReasons.join(', ')}`);
                        placement.existingId = existingForMason.id;
                        toUpdate.push(placement);
                        continue;
                    }
                    
                    // Check for exact duplicate by event_id (same session + sequence)
                    const eventExists = await new Promise((res, rej) => {
                        db.get(
                            `SELECT id FROM placements WHERE event_id = ?`,
                            [eventId],
                            (err, row) => {
                                if (err) rej(err);
                                else res(!!row);
                            }
                        );
                    });
                    
                    if (eventExists) {
                        console.log(`[${masonId}] ⚠ Duplicate event_id: ${eventId} - skipping`);
                        continue;
                    }
                    
                    // Check for cross-mason duplicate (same brick scanned by another mason recently)
                    const crossMasonDuplicate = await new Promise((res, rej) => {
                        db.get(
                            `SELECT mason_id, timestamp FROM placements 
                             WHERE brick_number = ? 
                             AND mason_id != ?
                             AND ABS(timestamp - ?) < ?
                             ORDER BY timestamp DESC LIMIT 1`,
                            [placement.brickNumber, masonId, placement.timestamp, DUPLICATE_WINDOW_MS],
                            (err, row) => {
                                if (err) rej(err);
                                else res(row);
                            }
                        );
                    });
                    
                    if (crossMasonDuplicate) {
                        const timeDiff = Math.abs(placement.timestamp - crossMasonDuplicate.timestamp);
                        console.log(`[${masonId}] ⚠ Cross-mason conflict: ${placement.brickNumber} scanned by ${crossMasonDuplicate.mason_id} ${Math.round(timeDiff/1000)}s ago - skipping`);
                        continue;
                    }
                    
                    // New brick for this mason - INSERT
                    toInsert.push(placement);
                }
                
                resolve({ toInsert, toUpdate });
            } catch (err) {
                reject(err);
            }
        });
    },

    // Add or update multiple placements (batch upsert with GPS data)
    addBatch: (masonId, result) => {
        return new Promise(async (resolve, reject) => {
            await dbReadyPromise; // Ensure DB is ready
            
            const { toInsert, toUpdate } = result;
            const totalOperations = toInsert.length + toUpdate.length;
            
            if (totalOperations === 0) {
                return resolve({ inserted: 0, updated: 0 });
            }
            
            // Use transaction for atomicity (multi-mason safe)
            db.serialize(() => {
                db.run('BEGIN TRANSACTION', (err) => {
                    if (err) {
                        console.error(`[${masonId}] Error starting transaction:`, err);
                        return reject(err);
                    }
                });

                let insertCount = 0;
                let updateCount = 0;
                let hasError = false;
                const receivedAt = Date.now();

                // Handle INSERTs
                if (toInsert.length > 0) {
                    const insertStmt = db.prepare(`
                        INSERT INTO placements (
                            mason_id, brick_number, rfid_tag, timestamp, received_at, 
                            latitude, longitude, altitude, accuracy,
                            build_session_id, event_seq, rssi_avg, rssi_peak, reads_in_window, power_level, decision_status, event_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    `);

                    toInsert.forEach(placement => {
                        const eventId = placement.eventId || `${placement.buildSessionId}-${placement.eventSeq}`;
                        
                        insertStmt.run(
                            masonId,
                            placement.brickNumber,
                            placement.brickNumber,
                            placement.timestamp,
                            receivedAt,
                            placement.latitude || 0.0,
                            placement.longitude || 0.0,
                            placement.altitude || 0.0,
                            placement.accuracy || 0.0,
                            placement.buildSessionId || '',
                            placement.eventSeq || 0,
                            placement.rssiAvg || 0,
                            placement.rssiPeak || 0,
                            placement.readsInWindow || 0,
                            placement.powerLevel || 0,
                            placement.decisionStatus || 'ACCEPTED',
                            eventId,
                            (err) => {
                                if (err) {
                                    console.error(`[${masonId}] Insert error:`, err);
                                    hasError = true;
                                } else {
                                    insertCount++;
                                }
                            }
                        );
                    });

                    insertStmt.finalize();
                }

                // Handle UPDATEs - archive old data to history first
                if (toUpdate.length > 0) {
                    // First, archive the current state to history
                    const archiveStmt = db.prepare(`
                        INSERT INTO placement_history (
                            placement_id, mason_id, brick_number, rfid_tag, timestamp, received_at,
                            latitude, longitude, altitude, accuracy,
                            build_session_id, event_seq, rssi_avg, rssi_peak, reads_in_window, power_level, 
                            decision_status, event_id, action_type
                        )
                        SELECT 
                            id, mason_id, brick_number, rfid_tag, timestamp, received_at,
                            latitude, longitude, altitude, accuracy,
                            build_session_id, event_seq, rssi_avg, rssi_peak, reads_in_window, power_level,
                            decision_status, event_id, 'UPDATE'
                        FROM placements
                        WHERE id = ?
                    `);

                    toUpdate.forEach(placement => {
                        archiveStmt.run(placement.existingId, (err) => {
                            if (err) {
                                console.error(`[${masonId}] Archive error for placement ${placement.existingId}:`, err);
                                hasError = true;
                            }
                        });
                    });

                    archiveStmt.finalize();

                    // Then update the current record
                    const updateStmt = db.prepare(`
                        UPDATE placements SET
                            timestamp = ?,
                            received_at = ?,
                            latitude = ?,
                            longitude = ?,
                            altitude = ?,
                            accuracy = ?,
                            build_session_id = ?,
                            event_seq = ?,
                            rssi_avg = ?,
                            rssi_peak = ?,
                            reads_in_window = ?,
                            power_level = ?,
                            decision_status = ?,
                            event_id = ?
                        WHERE id = ?
                    `);

                    toUpdate.forEach(placement => {
                        const eventId = placement.eventId || `${placement.buildSessionId}-${placement.eventSeq}`;
                        
                        updateStmt.run(
                            placement.timestamp,
                            receivedAt,
                            placement.latitude || 0.0,
                            placement.longitude || 0.0,
                            placement.altitude || 0.0,
                            placement.accuracy || 0.0,
                            placement.buildSessionId || '',
                            placement.eventSeq || 0,
                            placement.rssiAvg || 0,
                            placement.rssiPeak || 0,
                            placement.readsInWindow || 0,
                            placement.powerLevel || 0,
                            placement.decisionStatus || 'ACCEPTED',
                            eventId,
                            placement.existingId,
                            (err) => {
                                if (err) {
                                    console.error(`[${masonId}] Update error:`, err);
                                    hasError = true;
                                } else {
                                    updateCount++;
                                }
                            }
                        );
                    });

                    updateStmt.finalize();
                }

                // Commit or rollback transaction
                if (hasError) {
                    db.run('ROLLBACK', () => {
                        console.error(`[${masonId}] Transaction rolled back`);
                        reject(new Error('Insert/Update failed'));
                    });
                } else {
                    db.run('COMMIT', (commitErr) => {
                        if (commitErr) {
                            console.error(`[${masonId}] Commit error:`, commitErr);
                            reject(commitErr);
                        } else {
                            console.log(`[${masonId}] Transaction committed: ${insertCount} inserted, ${updateCount} updated`);
                            resolve({ inserted: insertCount, updated: updateCount });
                        }
                    });
                }
            });
        });
    },

    // Get total count for a mason
    getCountByMasonId: (masonId) => {
        return new Promise((resolve, reject) => {
            db.get(
                `SELECT COUNT(*) as count FROM placements WHERE mason_id = ?`,
                [masonId],
                (err, row) => {
                    if (err) reject(err);
                    else resolve(row ? row.count : 0);
                }
            );
        });
    },    
    deleteByMasonId: (masonId) => {
        return new Promise((resolve, reject) => {
            db.run(
                'DELETE FROM placements WHERE mason_id = ?',
                [masonId],
                function(err) {
                    if (err) reject(err);
                    else resolve(this.changes); // Returns number of deleted rows
                }
            );
        });
    },
    // Get all placements for a mason (ordered by session + sequence for true placement order)
    getByMasonId: (masonId, since = null) => {
        return new Promise((resolve, reject) => {
            let query = `SELECT * FROM placements WHERE mason_id = ?`;
            const params = [masonId];
            
            // Optional incremental filter for live feed efficiency
            if (since) {
                query += ` AND timestamp > ?`;
                params.push(since);
            }
            
            // Order by session (newest first), then sequence (ascending within session)
            query += ` ORDER BY build_session_id DESC, event_seq ASC, timestamp ASC`;
            
            db.all(query, params, (err, rows) => {
                if (err) reject(err);
                else resolve(rows || []);
            });
        });
    },

    // Get all placements
    getAll: () => {
        return new Promise((resolve, reject) => {
            db.all(
                `SELECT * FROM placements ORDER BY timestamp DESC`,
                (err, rows) => {
                    if (err) reject(err);
                    else resolve(rows || []);
                }
            );
        });
    },

    // Get recent placements (limit)
    getRecent: (limit = 50) => {
        return new Promise((resolve, reject) => {
            db.all(
                `SELECT p.*, u.username 
                 FROM placements p 
                 LEFT JOIN users u ON p.mason_id = u.mason_id 
                 ORDER BY p.timestamp DESC 
                 LIMIT ?`,
                [limit],
                (err, rows) => {
                    if (err) reject(err);
                    else resolve(rows || []);
                }
            );
        });
    },

    // Get statistics by mason
    getStatsByMason: () => {
        return new Promise((resolve, reject) => {
            db.all(
                `SELECT mason_id, COUNT(*) as count 
                 FROM placements 
                 GROUP BY mason_id 
                 ORDER BY count DESC`,
                (err, rows) => {
                    if (err) reject(err);
                    else resolve(rows || []);
                }
            );
        });
    },
    
    // Get efficiency data with hourly breakdown
    getEfficiencyData: async () => {
        await dbReadyPromise;
        return new Promise((resolve, reject) => {
            const query = `
                SELECT 
                    mason_id,
                    COUNT(*) as total_placements,
                    MIN(timestamp) as first_placement,
                    MAX(timestamp) as last_placement,
                    (MAX(timestamp) - MIN(timestamp)) / 3600000.0 as hours_worked,
                    CAST(COUNT(*) AS FLOAT) / NULLIF((MAX(timestamp) - MIN(timestamp)) / 3600000.0, 0) as placements_per_hour,
                    DATE(timestamp / 1000, 'unixepoch', 'localtime') as work_date
                FROM placements
                GROUP BY mason_id, work_date
                HAVING hours_worked > 0
                ORDER BY mason_id, work_date DESC
            `;
            db.all(query, [], (err, rows) => {
                if (err) reject(err);
                else resolve((rows || []).map(row => ({
                    masonId: row.mason_id,
                    date: row.work_date,
                    totalPlacements: row.total_placements,
                    hoursWorked: parseFloat(row.hours_worked.toFixed(2)),
                    placementsPerHour: parseFloat(row.placements_per_hour.toFixed(2)),
                    firstPlacement: row.first_placement,
                    lastPlacement: row.last_placement
                })));
            });
        });
    },
    
    // Get overall mason summaries
    getMasonSummaries: async () => {
        await dbReadyPromise;
        return new Promise((resolve, reject) => {
            const query = `
                SELECT 
                    mason_id,
                    COUNT(*) as total_placements,
                    MIN(timestamp) as first_placement_ever,
                    MAX(timestamp) as last_placement_ever,
                    COUNT(DISTINCT DATE(timestamp / 1000, 'unixepoch', 'localtime')) as days_worked,
                    AVG(daily_count) as avg_placements_per_day
                FROM (
                    SELECT 
                        mason_id,
                        DATE(timestamp / 1000, 'unixepoch', 'localtime') as work_date,
                        COUNT(*) as daily_count
                    FROM placements
                    GROUP BY mason_id, work_date
                )
                GROUP BY mason_id
                ORDER BY total_placements DESC
            `;
            db.all(query, [], (err, rows) => {
                if (err) reject(err);
                else resolve((rows || []).map(row => ({
                    masonId: row.mason_id,
                    totalPlacements: row.total_placements,
                    daysWorked: row.days_worked,
                    avgPlacementsPerDay: parseFloat(row.avg_placements_per_day.toFixed(2)),
                    firstPlacementEver: row.first_placement_ever,
                    lastPlacementEver: row.last_placement_ever
                })));
            });
        });
    }
};

// Session functions (JWT token tracking)
const dbSessions = {
    // Create a new session
    create: async (masonId, token, expiresAt) => {
        return new Promise((resolve, reject) => {
            db.run(
                `INSERT INTO sessions (mason_id, token, expires_at) VALUES (?, ?, ?)`,
                [masonId, token, expiresAt],
                function(err) {
                    if (err) reject(err);
                    else resolve({ id: this.lastID, masonId, token });
                }
            );
        });
    },

    // Find a valid (non-expired) session by token
    findByToken: async (token) => {
        return new Promise((resolve, reject) => {
            db.get(
                `SELECT * FROM sessions WHERE token = ? AND expires_at > datetime('now')`,
                [token],
                (err, row) => {
                    if (err) reject(err);
                    else resolve(row || null);
                }
            );
        });
    },

    // Delete a session (logout)
    deleteByToken: async (token) => {
        return new Promise((resolve, reject) => {
            db.run(
                `DELETE FROM sessions WHERE token = ?`,
                [token],
                function(err) {
                    if (err) reject(err);
                    else resolve(this.changes);
                }
            );
        });
    },

    // Delete all sessions for a mason (force logout everywhere)
    deleteByMasonId: async (masonId) => {
        return new Promise((resolve, reject) => {
            db.run(
                `DELETE FROM sessions WHERE mason_id = ?`,
                [masonId],
                function(err) {
                    if (err) reject(err);
                    else resolve(this.changes);
                }
            );
        });
    },

    // Clean up expired sessions
    cleanExpired: async () => {
        return new Promise((resolve, reject) => {
            db.run(
                `DELETE FROM sessions WHERE expires_at <= datetime('now')`,
                function(err) {
                    if (err) reject(err);
                    else resolve(this.changes);
                }
            );
        });
    }
};

// Close database connection
function closeDatabase() {
    return new Promise((resolve, reject) => {
        if (db) {
            db.close((err) => {
                if (err) {
                    console.error('Error closing database:', err.message);
                    reject(err);
                } else {
                    console.log('✓ Database connection closed');
                    resolve();
                }
            });
        } else {
            resolve();
        }
    });
}

// Export functions
module.exports = {
    initializeDatabase,
    dbUsers,
    dbPlacements,
    dbSessions,
    closeDatabase,
    get db() { return db; }
};
