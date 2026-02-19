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
            
            // Enable WAL mode for better concurrent read/write performance
            db.run('PRAGMA journal_mode=WAL', (walErr) => {
                if (walErr) {
                    console.error('Warning: Could not enable WAL mode:', walErr.message);
                } else {
                    console.log('✓ WAL journal mode enabled');
                }
                createTables(resolve, reject);
            });
        });
    });
    return dbReadyPromise;
}

// Create database tables
function createTables(resolve, reject) {
    let tablesCreated = 0;
    const totalTables = 5;
    
    const checkComplete = () => {
        tablesCreated++;
        if (tablesCreated === totalTables) {
            dbReady = true;
            console.log('✓ Database initialization complete');
            resolve();
        }
    };
    
    db.serialize(() => {
        // Companies table
        db.run(`
            CREATE TABLE IF NOT EXISTS companies (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                code TEXT UNIQUE NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        `, (err) => {
            if (err) {
                console.error('Error creating companies table:', err.message);
                reject(err);
            } else {
                console.log('✓ Companies table ready');
                seedDefaultCompanies();
                checkComplete();
            }
        });

        // Users table
        db.run(`
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                mason_id TEXT UNIQUE NOT NULL,
                company_id INTEGER,
                role TEXT NOT NULL DEFAULT 'user',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (company_id) REFERENCES companies(id)
            )
        `, (err) => {
            if (err) {
                console.error('Error creating users table:', err.message);
                reject(err);
            } else {
                console.log('✓ Users table ready');
                // Migrate: add company_id column if missing (existing DBs)
                db.run(`ALTER TABLE users ADD COLUMN company_id INTEGER REFERENCES companies(id)`, (alterErr) => {
                    // Ignore "duplicate column" error — it means column already exists
                    if (alterErr && !alterErr.message.includes('duplicate column')) {
                        console.error('Warning: Could not add company_id column:', alterErr.message);
                    } else if (!alterErr) {
                        console.log('✓ Migrated users table: added company_id column');
                    }
                });
                // Migrate: add role column if missing (existing DBs)
                db.run(`ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'user'`, (alterErr) => {
                    if (alterErr && !alterErr.message.includes('duplicate column')) {
                        console.error('Warning: Could not add role column:', alterErr.message);
                    } else if (!alterErr) {
                        console.log('✓ Migrated users table: added role column');
                        // Set existing admin user to super_admin role
                        db.run(`UPDATE users SET role = 'super_admin' WHERE mason_id = 'MASON_ADMIN' OR username = 'admin'`);
                    }
                });
                // Migrate: add preferences column if missing (stores JSON user preferences like widget layout)
                db.run(`ALTER TABLE users ADD COLUMN preferences TEXT DEFAULT '{}'`, (alterErr) => {
                    if (alterErr && !alterErr.message.includes('duplicate column')) {
                        console.error('Warning: Could not add preferences column:', alterErr.message);
                    } else if (!alterErr) {
                        console.log('✓ Migrated users table: added preferences column');
                    }
                });
                // Migrate: add email column if missing
                db.run(`ALTER TABLE users ADD COLUMN email TEXT DEFAULT ''`, (alterErr) => {
                    if (alterErr && !alterErr.message.includes('duplicate column')) {
                        console.error('Warning: Could not add email column:', alterErr.message);
                    } else if (!alterErr) {
                        console.log('✓ Migrated users table: added email column');
                    }
                });
                // Seed default super admin accounts
                seedDefaultSuperAdmins();
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
                
                // Migrate: add scan_type column if missing (existing DBs)
                db.run(`ALTER TABLE placements ADD COLUMN scan_type TEXT DEFAULT 'placement'`, (alterErr) => {
                    if (alterErr && !alterErr.message.includes('duplicate column')) {
                        console.error('Warning: Could not add scan_type column to placements:', alterErr.message);
                    } else if (!alterErr) {
                        console.log('✓ Migrated placements table: added scan_type column');
                    }
                });
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
                
                // Migrate: add scan_type column if missing (existing DBs)
                db.run(`ALTER TABLE placement_history ADD COLUMN scan_type TEXT DEFAULT 'placement'`, (alterErr) => {
                    if (alterErr && !alterErr.message.includes('duplicate column')) {
                        console.error('Warning: Could not add scan_type column to placement_history:', alterErr.message);
                    } else if (!alterErr) {
                        console.log('✓ Migrated placement_history table: added scan_type column');
                    }
                });
                checkComplete();
            }
        });

        // Note: redundant single-column indexes (idx_placements_mason_id, idx_placements_timestamp,
        // idx_placements_brick_number) removed — all covered by composite indexes above
    });
}

// Seed default companies
function seedDefaultCompanies() {
    const defaults = [
        { name: 'Construction Robotics', code: 'CR' }
    ];

    defaults.forEach(c => {
        db.run(
            `INSERT OR IGNORE INTO companies (name, code) VALUES (?, ?)`,
            [c.name, c.code],
            (err) => {
                if (err && !err.message.includes('UNIQUE constraint')) {
                    console.error(`Error seeding company ${c.name}:`, err.message);
                }
            }
        );
    });
}

// Seed default super admin accounts on startup
function seedDefaultSuperAdmins() {
    const defaultAdmins = [
        { username: 'cstreb', mason_id: 'CHUCK' },   // promote existing user only
        { username: 'developer', mason_id: 'DEV' }   // promote existing user only
    ];

    defaultAdmins.forEach(admin => {
        if (admin.password) {
            // Full seed: create if missing, set role to super_admin
            db.get(`SELECT id FROM users WHERE mason_id = ?`, [admin.mason_id], async (err, row) => {
                if (err) return;
                if (!row) {
                    // Look up company id from code
                    db.get(`SELECT id FROM companies WHERE code = ?`, [admin.company_code || 'CR'], async (err2, company) => {
                        if (err2) return;
                        const bcrypt = require('bcryptjs');
                        const hash = await bcrypt.hash(admin.password, 10);
                        db.run(
                            `INSERT OR IGNORE INTO users (username, password, mason_id, role, company_id) VALUES (?, ?, ?, 'super_admin', ?)`,
                            [admin.username, hash, admin.mason_id, company ? company.id : null],
                            (insertErr) => {
                                if (!insertErr) {
                                    console.log(`✓ Default super admin seeded: ${admin.username}`);
                                }
                            }
                        );
                    });
                } else {
                    // Already exists — ensure role is super_admin
                    db.run(`UPDATE users SET role = 'super_admin' WHERE mason_id = ?`, [admin.mason_id]);
                }
            });
        } else {
            // Promote-only: just set role if user exists
            db.run(`UPDATE users SET role = 'super_admin' WHERE mason_id = ?`, [admin.mason_id], function(err) {
                if (!err && this.changes > 0) {
                    console.log(`✓ Promoted existing user to super admin: ${admin.username}`);
                }
            });
        }
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
                `SELECT u.*, u.role, c.name as company_name, c.code as company_code, c.id as comp_id
                 FROM users u LEFT JOIN companies c ON u.company_id = c.id
                 WHERE u.username = ?`,
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
            db.all(
                `SELECT u.*, u.role, c.name as company_name, c.code as company_code
                 FROM users u LEFT JOIN companies c ON u.company_id = c.id`,
                (err, rows) => {
                    if (err) reject(err);
                    else resolve(rows);
                }
            );
        });
    },

    // Get all users for dropdown (username + mason_id + company + role)
    listAll: () => {
        return new Promise((resolve, reject) => {
            db.all(
                `SELECT u.username, u.mason_id, u.role, u.company_id, c.name as company_name, c.code as company_code
                 FROM users u LEFT JOIN companies c ON u.company_id = c.id
                 ORDER BY c.name ASC, u.username ASC`,
                (err, rows) => {
                    if (err) reject(err);
                    else resolve(rows || []);
                }
            );
        });
    },

    // Create new user with hashed password
    create: async (username, password, masonId, companyId, email) => {
        return new Promise(async (resolve, reject) => {
            // Hash password before storing
            const hashedPassword = await bcrypt.hash(password, 10);
            
            db.run(
                `INSERT INTO users (username, password, mason_id, company_id, email) VALUES (?, ?, ?, ?, ?)`,
                [username, hashedPassword, masonId, companyId || null, email || ''],
                function(err) {
                    if (err) reject(err);
                    else resolve({ id: this.lastID, username, masonId, companyId, email });
                }
            );
        });
    },

    // Update a user's company assignment
    updateCompany: async (masonId, companyId) => {
        return new Promise((resolve, reject) => {
            db.run(
                `UPDATE users SET company_id = ? WHERE mason_id = ?`,
                [companyId, masonId],
                function(err) {
                    if (err) reject(err);
                    else resolve(this.changes);
                }
            );
        });
    },

    // List users filtered by company (for company admins)
    listByCompany: (companyId) => {
        return new Promise((resolve, reject) => {
            db.all(
                `SELECT u.username, u.mason_id, u.role, u.company_id, c.name as company_name, c.code as company_code
                 FROM users u LEFT JOIN companies c ON u.company_id = c.id
                 WHERE u.company_id = ?
                 ORDER BY u.username ASC`,
                [companyId],
                (err, rows) => {
                    if (err) reject(err);
                    else resolve(rows || []);
                }
            );
        });
    },

    // Update a user's role (super_admin or company_admin can do this)
    updateRole: async (masonId, role) => {
        const validRoles = ['user', 'company_admin', 'super_admin'];
        if (!validRoles.includes(role)) {
            throw new Error(`Invalid role: ${role}. Must be one of: ${validRoles.join(', ')}`);
        }
        return new Promise((resolve, reject) => {
            db.run(
                `UPDATE users SET role = ? WHERE mason_id = ?`,
                [role, masonId],
                function(err) {
                    if (err) reject(err);
                    else resolve(this.changes);
                }
            );
        });
    },

    // Get a single user by mason_id
    getByMasonId: (masonId) => {
        return new Promise((resolve, reject) => {
            db.get(
                `SELECT u.username, u.mason_id, u.role, u.company_id, c.name as company_name, c.code as company_code
                 FROM users u LEFT JOIN companies c ON u.company_id = c.id
                 WHERE u.mason_id = ?`,
                [masonId],
                (err, row) => {
                    if (err) reject(err);
                    else resolve(row);
                }
            );
        });
    },

    // Get user preferences (JSON blob)
    getPreferences: (masonId) => {
        return new Promise((resolve, reject) => {
            db.get(
                `SELECT preferences FROM users WHERE mason_id = ?`,
                [masonId],
                (err, row) => {
                    if (err) reject(err);
                    else {
                        try {
                            resolve(row ? JSON.parse(row.preferences || '{}') : {});
                        } catch (e) {
                            resolve({});
                        }
                    }
                }
            );
        });
    },

    // Update user preferences (merge with existing)
    updatePreferences: (masonId, preferences) => {
        return new Promise(async (resolve, reject) => {
            try {
                // Get existing preferences first
                const existing = await dbUsers.getPreferences(masonId);
                const merged = { ...existing, ...preferences };
                const json = JSON.stringify(merged);
                db.run(
                    `UPDATE users SET preferences = ? WHERE mason_id = ?`,
                    [json, masonId],
                    function(err) {
                        if (err) reject(err);
                        else resolve(merged);
                    }
                );
            } catch (err) {
                reject(err);
            }
        });
    },

    // Delete user by mason_id
    deleteByMasonId: (masonId) => {
        return new Promise((resolve, reject) => {
            db.run(
                `DELETE FROM users WHERE mason_id = ?`,
                [masonId],
                function(err) {
                    if (err) reject(err);
                    else resolve(this.changes);
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
                        
                        // RSSI-based directional confidence scoring
                        // Higher RSSI = scanner was pointed more directly at tag = more accurate position
                        // MR20 UHF is directional (~60-70° beam), so RSSI strongly correlates with positioning accuracy
                        const RSSI_SIGNIFICANT_IMPROVEMENT = 3; // dB threshold for "significantly better" directional read
                        const rssiDiff = placement.rssiPeak - existingForMason.rssi_peak;
                        
                        // Calculate directional confidence (0-100 scale based on RSSI)
                        // Typical RSSI range: -70dB (weak/edge) to -30dB (strong/direct)
                        const calcConfidence = (rssi) => {
                            if (rssi === 0) return 0;
                            // Map -70 to -30 dB range to 0-100 confidence
                            const normalized = Math.max(0, Math.min(100, (rssi + 70) * 2.5));
                            return Math.round(normalized);
                        };
                        const newConfidence = calcConfidence(placement.rssiPeak);
                        const oldConfidence = calcConfidence(existingForMason.rssi_peak);
                        
                        // Validation checks - NEW scan must meet at least one criteria:
                        const isNewer = timeDiff > 30000; // At least 30 seconds newer
                        const hasBetterAccuracy = placement.accuracy > 0 && 
                                                 (existingForMason.accuracy === 0 || 
                                                  placement.accuracy < existingForMason.accuracy);
                        const hasStrongerSignal = rssiDiff > 0;
                        const hasSignificantlyStrongerSignal = rssiDiff >= RSSI_SIGNIFICANT_IMPROVEMENT;
                        const hasValidGPS = placement.latitude !== 0 && placement.longitude !== 0 &&
                                          (existingForMason.latitude === 0 || existingForMason.longitude === 0);
                        
                        // Directional positioning improvement: stronger RSSI with valid GPS = more accurate position
                        const hasBetterDirectionalPosition = hasSignificantlyStrongerSignal && 
                                                             placement.latitude !== 0 && 
                                                             placement.longitude !== 0;
                        
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
                        if (!hasBetterAccuracy && !hasStrongerSignal && !hasValidGPS && !hasBetterDirectionalPosition) {
                            console.log(`[${masonId}] ✗ Rejected: ${placement.brickNumber} - no improvement (accuracy: ${placement.accuracy}m vs ${existingForMason.accuracy}m, RSSI: ${placement.rssiPeak}dB vs ${existingForMason.rssi_peak}dB, confidence: ${newConfidence}% vs ${oldConfidence}%) - skipping`);
                            continue;
                        }
                        
                        // Valid UPDATE - log why it's being updated with directional confidence
                        let updateReasons = [];
                        if (isNewer) updateReasons.push(`${timeDiffSeconds}s newer`);
                        if (hasBetterAccuracy) updateReasons.push(`better GPS accuracy: ${placement.accuracy}m vs ${existingForMason.accuracy}m`);
                        if (hasSignificantlyStrongerSignal) {
                            updateReasons.push(`stronger directional read: ${placement.rssiPeak}dB vs ${existingForMason.rssi_peak}dB (+${rssiDiff}dB)`);
                        } else if (hasStrongerSignal) {
                            updateReasons.push(`slightly stronger signal: ${placement.rssiPeak}dB vs ${existingForMason.rssi_peak}dB`);
                        }
                        if (hasValidGPS) updateReasons.push(`added GPS coordinates`);
                        if (hasBetterDirectionalPosition) updateReasons.push(`better directional position (confidence: ${newConfidence}% vs ${oldConfidence}%)`);
                        
                        console.log(`[${masonId}] ↻ Valid update for ${placement.brickNumber}: ${updateReasons.join(', ')}`);
                        placement.existingId = existingForMason.id;
                        placement.directionalConfidence = newConfidence; // Store for potential future use
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
                            build_session_id, event_seq, rssi_avg, rssi_peak, reads_in_window, power_level, decision_status, event_id, scan_type
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                            placement.scanType || 'placement',
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
                            decision_status, event_id, action_type, scan_type
                        )
                        SELECT 
                            id, mason_id, brick_number, rfid_tag, timestamp, received_at,
                            latitude, longitude, altitude, accuracy,
                            build_session_id, event_seq, rssi_avg, rssi_peak, reads_in_window, power_level,
                            decision_status, event_id, 'UPDATE', scan_type
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
                            event_id = ?,
                            scan_type = ?
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
                            placement.scanType || 'placement',
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
    getCountByMasonId: (masonId, scanType = null) => {
        return new Promise((resolve, reject) => {
            let query = `SELECT COUNT(*) as count FROM placements WHERE mason_id = ?`;
            const params = [masonId];
            if (scanType) {
                query += ` AND scan_type = ?`;
                params.push(scanType);
            }
            db.get(query, params, (err, row) => {
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

    // Get recent placements for a single mason
    getRecentByMason: (masonId, limit = 50) => {
        return new Promise((resolve, reject) => {
            db.all(
                `SELECT p.*, u.username 
                 FROM placements p 
                 LEFT JOIN users u ON p.mason_id = u.mason_id 
                 WHERE p.mason_id = ?
                 ORDER BY p.timestamp DESC 
                 LIMIT ?`,
                [masonId, limit],
                (err, rows) => {
                    if (err) reject(err);
                    else resolve(rows || []);
                }
            );
        });
    },

    // Get recent placements for all masons in a company
    getRecentByCompany: (companyId, limit = 50) => {
        return new Promise((resolve, reject) => {
            db.all(
                `SELECT p.*, u.username 
                 FROM placements p 
                 LEFT JOIN users u ON p.mason_id = u.mason_id 
                 WHERE p.mason_id IN (SELECT mason_id FROM users WHERE company_id = ?)
                 ORDER BY p.timestamp DESC 
                 LIMIT ?`,
                [companyId, limit],
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

    // Get statistics for a single mason
    getStatsByMasonFiltered: (masonId) => {
        return new Promise((resolve, reject) => {
            db.all(
                `SELECT mason_id, COUNT(*) as count 
                 FROM placements 
                 WHERE mason_id = ?
                 GROUP BY mason_id 
                 ORDER BY count DESC`,
                [masonId],
                (err, rows) => {
                    if (err) reject(err);
                    else resolve(rows || []);
                }
            );
        });
    },

    // Get statistics for all masons in a company
    getStatsByCompany: (companyId) => {
        return new Promise((resolve, reject) => {
            db.all(
                `SELECT mason_id, COUNT(*) as count 
                 FROM placements 
                 WHERE mason_id IN (SELECT mason_id FROM users WHERE company_id = ?)
                 GROUP BY mason_id 
                 ORDER BY count DESC`,
                [companyId],
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

// Company functions
const dbCompanies = {
    // Get all companies
    getAll: () => {
        return new Promise((resolve, reject) => {
            db.all(
                `SELECT id, name, code, created_at FROM companies ORDER BY name ASC`,
                (err, rows) => {
                    if (err) reject(err);
                    else resolve(rows || []);
                }
            );
        });
    },

    // Get company by id
    getById: (id) => {
        return new Promise((resolve, reject) => {
            db.get(
                `SELECT * FROM companies WHERE id = ?`,
                [id],
                (err, row) => {
                    if (err) reject(err);
                    else resolve(row || null);
                }
            );
        });
    },

    // Get company by code
    getByCode: (code) => {
        return new Promise((resolve, reject) => {
            db.get(
                `SELECT * FROM companies WHERE code = ?`,
                [code],
                (err, row) => {
                    if (err) reject(err);
                    else resolve(row || null);
                }
            );
        });
    },

    // Create a new company
    create: (name, code) => {
        return new Promise((resolve, reject) => {
            db.run(
                `INSERT INTO companies (name, code) VALUES (?, ?)`,
                [name, code.toUpperCase()],
                function(err) {
                    if (err) reject(err);
                    else resolve({ id: this.lastID, name, code: code.toUpperCase() });
                }
            );
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
    dbCompanies,
    dbSessions,
    closeDatabase,
    get db() { return db; }
};
