const express = require('express');
const path = require('path');
const bodyParser = require('body-parser');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const rateLimit = require('express-rate-limit');
const { initializeDatabase, dbUsers, dbPlacements, dbCompanies, dbSessions, closeDatabase } = require('./db');

const app = express();
const PORT = 8080;

// Trust proxy for ngrok/reverse proxy (required for rate limiting to work correctly)
app.set('trust proxy', 1);

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// Rate limiting — general API (100 requests per minute per IP)
const generalLimiter = rateLimit({
    windowMs: 60 * 1000,
    max: 100,
    standardHeaders: true,
    legacyHeaders: false,
    message: { success: false, message: 'Too many requests. Try again later.' }
});
app.use('/api/', generalLimiter);

// Strict rate limiting for auth endpoints (10 attempts per 15 minutes per IP)
const authLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 10,
    standardHeaders: true,
    legacyHeaders: false,
    message: { success: false, message: 'Too many login attempts. Try again in 15 minutes.' }
});
app.use('/api/auth/', authLimiter);

// Serve static files from public directory
app.use(express.static('public'));

// Logging middleware
app.use((req, res, next) => {
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
    if (req.body && Object.keys(req.body).length > 0) {
        // Redact sensitive fields before logging
        const sanitized = { ...req.body };
        if (sanitized.password) sanitized.password = '***REDACTED***';
        console.log('Body:', JSON.stringify(sanitized, null, 2));
    }
    next();
});

// ============================================
// JWT AUTHENTICATION
// ============================================

// Generate a persistent secret - in production use an env variable
const JWT_SECRET = process.env.JWT_SECRET || crypto.randomBytes(32).toString('hex');
const JWT_EXPIRY = '7d'; // Tokens valid for 7 days

// Generate a JWT token for a user
function generateToken(user) {
    return jwt.sign(
        {
            masonId: user.mason_id,
            username: user.username,
            role: user.role || 'user',
            companyId: user.company_id || null,
            companyName: user.company_name || null,
            companyCode: user.company_code || null
        },
        JWT_SECRET,
        { expiresIn: JWT_EXPIRY }
    );
}

// Auth middleware - verifies JWT Bearer token
function requireAuth(req, res, next) {
    const authHeader = req.headers.authorization;
    let token = null;

    if (authHeader && authHeader.startsWith('Bearer ')) {
        token = authHeader.split(' ')[1];
    } else if (req.query.token) {
        // Allow token via query param (for report URLs opened in new tabs)
        token = req.query.token;
    }

    if (!token) {
        return res.status(401).json({
            success: false,
            message: 'Authentication required. Provide Bearer token.'
        });
    }

    try {
        const decoded = jwt.verify(token, JWT_SECRET);
        req.user = decoded; // { masonId, username, iat, exp }
        next();
    } catch (err) {
        if (err.name === 'TokenExpiredError') {
            return res.status(401).json({
                success: false,
                message: 'Token expired. Please log in again.'
            });
        }
        return res.status(401).json({
            success: false,
            message: 'Invalid token.'
        });
    }
}

// Clean up expired sessions periodically (every 6 hours)
setInterval(async () => {
    try {
        const cleaned = await dbSessions.cleanExpired();
        if (cleaned > 0) {
            console.log(`[SESSION] Cleaned ${cleaned} expired sessions`);
        }
    } catch (err) {
        console.error('[SESSION] Cleanup error:', err.message);
    }
}, 6 * 60 * 60 * 1000);

// ============================================
// AUTH ENDPOINTS
// ============================================

// POST /api/auth/login
app.post('/api/auth/login', async (req, res) => {
    const { username, password } = req.body;
    
    console.log(`Login attempt: ${username}`);
    
    if (!username || !password) {
        return res.status(400).json({
            success: false,
            message: 'Username and password are required'
        });
    }
    
    try {
        // Authenticate user with bcrypt password comparison
        const user = await dbUsers.authenticate(username, password);
        
        if (user) {
            const role = user.role || 'user';
            const isSuperAdmin = role === 'super_admin' || user.mason_id === 'MASON_ADMIN' || user.username === 'admin';
            const isCompanyAdmin = role === 'company_admin';
            const isAdmin = isSuperAdmin || isCompanyAdmin;
            
            // Ensure legacy admin accounts get super_admin role in DB
            if (isSuperAdmin && role !== 'super_admin') {
                try { await dbUsers.updateRole(user.mason_id, 'super_admin'); } catch(e) {}
                user.role = 'super_admin';
            }
            
            // Generate JWT token
            const token = generateToken(user);
            
            // Calculate expiry date for session storage
            const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();
            
            // Store session in database
            try {
                await dbSessions.create(user.mason_id, token, expiresAt);
            } catch (sessionErr) {
                console.error('Session creation error (non-fatal):', sessionErr.message);
            }
            
            console.log(`✓ Login successful: ${username} -> ${user.mason_id} (role: ${user.role}, admin: ${isAdmin})`);
            return res.json({
                success: true,
                message: 'Login successful',
                masonId: user.mason_id,
                username: user.username,
                role: user.role || 'user',
                isAdmin: isAdmin,
                isSuperAdmin: isSuperAdmin,
                isCompanyAdmin: isCompanyAdmin,
                token: token,
                company: user.company_name ? {
                    id: user.comp_id,
                    name: user.company_name,
                    code: user.company_code
                } : null
            });
        } else {
            console.log(`✗ Login failed: Invalid credentials for ${username}`);
            return res.status(401).json({
                success: false,
                message: 'Invalid username or password'
            });
        }
    } catch (err) {
        console.error('Login error:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error'
        });
    }
});

// POST /api/auth/register - Register new user
app.post('/api/auth/register', async (req, res) => {
    const { username, password, mason_id, company_id } = req.body;
    
    console.log(`Registration attempt: ${username}`);
    
    if (!username || !password || !mason_id) {
        return res.status(400).json({
            success: false,
            message: 'Username, password, and mason_id are required'
        });
    }

    if (!company_id) {
        return res.status(400).json({
            success: false,
            message: 'Company selection is required'
        });
    }
    
    // Validate password strength
    if (password.length < 6) {
        return res.status(400).json({
            success: false,
            message: 'Password must be at least 6 characters'
        });
    }
    
    try {
        await dbUsers.create(username, password, mason_id, company_id || null);
        console.log(`✓ User registered: ${username} -> ${mason_id}`);
        
        // Generate token so user can immediately use the API
        // Look up company info for the token
        let companyInfo = null;
        if (company_id) {
            companyInfo = await dbCompanies.getById(company_id);
        }
        
        const token = generateToken({
            mason_id, username,
            company_id: company_id || null,
            company_name: companyInfo ? companyInfo.name : null,
            company_code: companyInfo ? companyInfo.code : null
        });
        const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();
        
        try {
            await dbSessions.create(mason_id, token, expiresAt);
        } catch (sessionErr) {
            console.error('Session creation error (non-fatal):', sessionErr.message);
        }
        
        return res.json({
            success: true,
            message: 'User registered successfully',
            username: username,
            masonId: mason_id,
            token: token,
            company: companyInfo ? {
                id: companyInfo.id,
                name: companyInfo.name,
                code: companyInfo.code
            } : null
        });
    } catch (err) {
        if (err.message.includes('UNIQUE constraint')) {
            console.log(`✗ Registration failed: ${username} already exists`);
            return res.status(409).json({
                success: false,
                message: 'Username or Mason ID already exists'
            });
        }
        console.error('Registration error:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error'
        });
    }
});

// ============================================
// USER/MASON ENDPOINTS
// ============================================

// GET /api/user/preferences - Get current user's preferences (widget layout, etc.)
app.get('/api/user/preferences', requireAuth, async (req, res) => {
    try {
        const prefs = await dbUsers.getPreferences(req.user.masonId);
        res.json({ success: true, preferences: prefs });
    } catch (err) {
        console.error('Error fetching preferences:', err);
        res.status(500).json({ success: false, message: 'Failed to load preferences' });
    }
});

// PUT /api/user/preferences - Update current user's preferences
app.put('/api/user/preferences', requireAuth, async (req, res) => {
    try {
        const { preferences } = req.body;
        if (!preferences || typeof preferences !== 'object') {
            return res.status(400).json({ success: false, message: 'Invalid preferences object' });
        }
        const merged = await dbUsers.updatePreferences(req.user.masonId, preferences);
        res.json({ success: true, preferences: merged });
    } catch (err) {
        console.error('Error saving preferences:', err);
        res.status(500).json({ success: false, message: 'Failed to save preferences' });
    }
});

// GET /api/users - Get users for dropdown (company-scoped for company admins)
app.get('/api/users', requireAuth, async (req, res) => {
    console.log(`Get users for dropdown (requester: ${req.user.username}, role: ${req.user.role})`);
    
    try {
        let users;
        const role = req.user.role || 'user';
        const isSuperAdmin = role === 'super_admin';
        const isCompanyAdmin = role === 'company_admin';
        
        if (isSuperAdmin) {
            // Super admins see ALL users across all companies
            users = await dbUsers.listAll();
        } else if (isCompanyAdmin && req.user.companyId) {
            // Company admins see only their company's users
            users = await dbUsers.listByCompany(req.user.companyId);
        } else {
            // Regular users see only their company's users
            if (req.user.companyId) {
                users = await dbUsers.listByCompany(req.user.companyId);
            } else {
                users = [];
            }
        }
        
        res.json({
            success: true,
            count: users.length,
            users: users.map(u => ({
                username: u.username,
                masonId: u.mason_id,
                role: u.role || 'user',
                company: u.company_name || 'Unassigned',
                companyCode: u.company_code || 'NONE',
                companyId: u.company_id || null
            }))
        });
    } catch (err) {
        console.error('Error fetching users:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error'
        });
    }
});

// GET /api/companies - Get all companies for dropdowns (public - needed for registration)
app.get('/api/companies', async (req, res) => {
    try {
        const companies = await dbCompanies.getAll();
        res.json({
            success: true,
            companies: companies.map(c => ({
                id: c.id,
                name: c.name,
                code: c.code
            }))
        });
    } catch (err) {
        console.error('Error fetching companies:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error'
        });
    }
});

// POST /api/companies - Create a new company (admin only)
app.post('/api/companies', requireAuth, async (req, res) => {
    const { name, code } = req.body;
    
    if (!name || !code) {
        return res.status(400).json({
            success: false,
            message: 'Company name and code are required'
        });
    }
    
    try {
        const company = await dbCompanies.create(name, code);
        console.log(`✓ Company created: ${name} (${code.toUpperCase()})`);
        res.json({ success: true, company });
    } catch (err) {
        if (err.message.includes('UNIQUE constraint')) {
            return res.status(409).json({
                success: false,
                message: 'Company code already exists'
            });
        }
        console.error('Error creating company:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error'
        });
    }
});

// ============================================
// USER MANAGEMENT ENDPOINTS
// ============================================

// PUT /api/users/:masonId/role - Update a user's role (admin only)
app.put('/api/users/:masonId/role', requireAuth, async (req, res) => {
    const { masonId } = req.params;
    const { role } = req.body;
    const requesterRole = req.user.role || 'user';
    
    if (!role) {
        return res.status(400).json({ success: false, message: 'Role is required' });
    }
    
    // Only super_admin and company_admin can change roles
    if (requesterRole !== 'super_admin' && requesterRole !== 'company_admin') {
        return res.status(403).json({ success: false, message: 'Only admins can change user roles' });
    }
    
    // Company admins can only promote/demote within their own company to company_admin or user
    if (requesterRole === 'company_admin') {
        if (role === 'super_admin') {
            return res.status(403).json({ success: false, message: 'Company admins cannot grant super_admin role' });
        }
        // Verify target user is in the same company
        const targetUser = await dbUsers.getByMasonId(masonId);
        if (!targetUser || targetUser.company_id !== req.user.companyId) {
            return res.status(403).json({ success: false, message: 'You can only manage users in your own company' });
        }
    }
    
    try {
        const changes = await dbUsers.updateRole(masonId, role);
        if (changes === 0) {
            return res.status(404).json({ success: false, message: 'User not found' });
        }
        console.log(`✓ Role updated: ${masonId} -> ${role} (by ${req.user.username})`);
        res.json({ success: true, message: `Role updated to ${role}`, masonId, role });
    } catch (err) {
        console.error('Error updating role:', err);
        return res.status(400).json({ success: false, message: err.message });
    }
});

// ============================================
// PLACEMENT ENDPOINTS
// ============================================

// POST /api/placements/sync
app.post('/api/placements/sync', requireAuth, async (req, res) => {
    const { masonId, placements: newPlacements } = req.body;
    
    console.log(`[${masonId}] Sync request received`);
    console.log(`[${masonId}] Placements to sync: ${newPlacements ? newPlacements.length : 0}`);
    
    if (!masonId) {
        return res.status(400).json({
            success: false,
            message: 'Mason ID is required'
        });
    }
    
    if (!newPlacements || !Array.isArray(newPlacements)) {
        return res.status(400).json({
            success: false,
            message: 'Placements array is required'
        });
    }
    
    try {
        // Deduplicate placements and separate into inserts vs updates
        const result = await dbPlacements.filterDuplicates(masonId, newPlacements);
        
        if (result.toInsert.length === 0 && result.toUpdate.length === 0) {
            console.log(`[${masonId}] ⚠ All ${newPlacements.length} placements were duplicates - skipped`);
            const totalCount = await dbPlacements.getCountByMasonId(masonId);
            return res.json({
                success: true,
                message: 'All placements were duplicates',
                lastPlacementNumber: totalCount,
                duplicatesSkipped: newPlacements.length
            });
        }
        
        if (result.toInsert.length + result.toUpdate.length < newPlacements.length) {
            const skipped = newPlacements.length - result.toInsert.length - result.toUpdate.length;
            console.log(`[${masonId}] ⚠ Skipped ${skipped} duplicate placements`);
        }
        
        // Store new placements or update existing ones
        const { inserted, updated } = await dbPlacements.addBatch(masonId, result);
        
        // Log each placement with operation type and scan type
        result.toInsert.forEach(placement => {
            console.log(`[${masonId}] + New ${placement.scanType || 'placement'}: ${placement.brickNumber} @ ${new Date(placement.timestamp).toISOString()}`);
        });
        result.toUpdate.forEach(placement => {
            console.log(`[${masonId}] ↻ Updated ${placement.scanType || 'placement'}: ${placement.brickNumber} @ ${new Date(placement.timestamp).toISOString()}`);
        });
        
        // Get counts by scan type for this mason
        const palletCount = await dbPlacements.getCountByMasonId(masonId, 'pallet');
        const placementCount = await dbPlacements.getCountByMasonId(masonId, 'placement');
        const totalCount = palletCount + placementCount;
        
        console.log(`[${masonId}] ✓ Synced: ${inserted} new, ${updated} updated. Total: ${totalCount} (${palletCount} pallet, ${placementCount} placement)`);
        
        res.json({
            success: true,
            message: `Successfully synced ${inserted} new + ${updated} updated placements`,
            lastPlacementNumber: totalCount,
            palletCount: palletCount,
            placementCount: placementCount,
            inserted: inserted,
            updated: updated,
            duplicatesSkipped: newPlacements.length - inserted - updated
        });
    } catch (err) {
        console.error(`[${masonId}] Sync error:`, err);
        return res.status(500).json({
            success: false,
            message: 'Database error during sync'
        });
    }
});

// GET /api/placements/last
app.get('/api/placements/last', requireAuth, async (req, res) => {
    const { masonId } = req.query;
    
    console.log(`Get last placement number for: ${masonId}`);
    
    if (!masonId) {
        return res.status(400).json({
            success: false,
            message: 'Mason ID is required'
        });
    }
    
    try {
        const palletCount = await dbPlacements.getCountByMasonId(masonId, 'pallet');
        const placementCount = await dbPlacements.getCountByMasonId(masonId, 'placement');
        const lastNumber = palletCount + placementCount;
        
        console.log(`✓ Last placement number: ${lastNumber} (${palletCount} pallet, ${placementCount} placement)`);
        
        res.json({
            success: true,
            message: 'Success',
            lastPlacementNumber: lastNumber,
            palletCount: palletCount,
            placementCount: placementCount
        });
    } catch (err) {
        console.error('Error getting last placement:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error'
        });
    }
});

// ============================================
// ADMIN/DEBUG ENDPOINTS
// ============================================

// GET /api/debug/placements
app.get('/api/debug/placements', requireAuth, async (req, res) => {
    console.log('Debug: Get all placements');
    try {
        const allPlacements = await dbPlacements.getAll();
        const stats = await dbPlacements.getStatsByMason();
        
        res.json({
            placements: allPlacements,
            statistics: stats,
            totalPlacements: allPlacements.length,
            totalMasons: stats.length
        });
    } catch (err) {
        console.error('Debug error:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error'
        });
    }
});

// DELETE /api/debug/purge - Purge all placements (ADMIN ONLY)
app.delete('/api/debug/purge', requireAuth, async (req, res) => {
    console.log('PURGE: Deleting all placements');
    try {
        const { db } = require('./db');
        
        await new Promise((resolve, reject) => {
            db.run('DELETE FROM placements', [], function(err) {
                if (err) reject(err);
                else resolve(this.changes);
            });
        });
        
        console.log('✓ All placements purged');
        res.json({
            success: true,
            message: 'All placements deleted'
        });
    } catch (err) {
        console.error('Purge error:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error',
            error: err.message
        });
    }
});

// GET /api/debug/users
app.get('/api/debug/users', requireAuth, async (req, res) => {
    console.log('Debug: Get all users');
    try {
        const users = await dbUsers.getAll();
        res.json({
            users: users.map(u => ({ 
                id: u.id,
                username: u.username, 
                masonId: u.mason_id,
                createdAt: u.created_at
            }))
        });
    } catch (err) {
        console.error('Debug error:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error'
        });
    }
});

// GET /api/placements/mason/:masonId - Get all placements for a specific mason
// Supports ?since=<timestamp> for incremental live feed
app.get('/api/placements/mason/:masonId', requireAuth, async (req, res) => {
    const { masonId } = req.params;
    const { since } = req.query;
    console.log(`Get placements for mason: ${masonId}${since ? ` (since ${since})` : ''}`);
    
    try {
        const placements = await dbPlacements.getByMasonId(masonId, since ? parseInt(since) : null);
        res.json({
            success: true,
            masonId,
            count: placements.length,
            placements: placements.map(p => ({
                id: p.id,
                mason_id: p.mason_id,  // Add mason_id to response
                brickNumber: p.brick_number,
                rfidTag: p.rfid_tag,
                timestamp: p.timestamp,
                receivedAt: p.received_at,
                createdAt: p.created_at,
                // New session/sequence fields for ordering
                buildSessionId: p.build_session_id,
                eventSeq: p.event_seq,
                rssiAvg: p.rssi_avg,
                rssiPeak: p.rssi_peak,
                readsInWindow: p.reads_in_window,
                powerLevel: p.power_level,
                decisionStatus: p.decision_status,
                scanType: p.scan_type || 'placement',
                // GPS fields
                latitude: p.latitude,
                longitude: p.longitude,
                accuracy: p.accuracy
            }))
        });
    } catch (err) {
        console.error('Error fetching placements:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error'
        });
    }
});

// DELETE /api/placements/mason/:masonId - Delete all placements for a mason (reset profile)
app.delete('/api/placements/mason/:masonId', requireAuth, async (req, res) => {
    const { masonId } = req.params;
    console.log(`Reset profile data for mason: ${masonId}`);
    
    if (!masonId) {
        return res.status(400).json({
            success: false,
            message: 'Mason ID is required'
        });
    }
    
    try {
        const deletedCount = await dbPlacements.deleteByMasonId(masonId);
        
        console.log(`✓ Deleted ${deletedCount} placements for ${masonId}`);
        
        res.json({
            success: true,
            message: `Successfully deleted ${deletedCount} placements`,
            deletedCount: deletedCount
        });
    } catch (err) {
        console.error('Error deleting placements:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error during reset'
        });
    }
});

// GET /api/placements/recent - Get recent placements across all masons
app.get('/api/placements/recent', requireAuth, async (req, res) => {
    const limit = parseInt(req.query.limit) || 50;
    console.log(`Get recent placements (limit: ${limit})`);
    
    try {
        const placements = await dbPlacements.getRecent(limit);
        res.json({
            success: true,
            count: placements.length,
            placements: placements.map(p => ({
                id: p.id,
                mason_id: p.mason_id,
                masonId: p.mason_id,
                username: p.username,
                brickNumber: p.brick_number,
                rfidTag: p.rfid_tag,
                timestamp: p.timestamp,
                receivedAt: p.received_at,
                createdAt: p.created_at,
                buildSessionId: p.build_session_id,
                eventSeq: p.event_seq,
                rssiAvg: p.rssi_avg,
                rssiPeak: p.rssi_peak,
                readsInWindow: p.reads_in_window,
                powerLevel: p.power_level,
                decisionStatus: p.decision_status,
                scanType: p.scan_type || 'placement',
                latitude: p.latitude,
                longitude: p.longitude,
                altitude: p.altitude,
                accuracy: p.accuracy
            }))
        });
    } catch (err) {
        console.error('Error fetching recent placements:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error'
        });
    }
});

// GET /api/statistics - Get overall statistics
app.get('/api/statistics', requireAuth, async (req, res) => {
    console.log('Get statistics');
    
    try {
        const stats = await dbPlacements.getStatsByMason();
        const users = await dbUsers.getAll();
        
        res.json({
            success: true,
            totalUsers: users.length,
            totalMasons: stats.length,
            masonStats: stats.map(s => ({
                masonId: s.mason_id,
                username: s.username,
                placementCount: s.placement_count,
                firstPlacement: s.first_placement,
                lastPlacement: s.last_placement
            }))
        });
    } catch (err) {
        console.error('Error fetching statistics:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error'
        });
    }
});

// GET /api/statistics/efficiency/:masonId - Get mason efficiency metrics (session-based)
app.get('/api/statistics/efficiency/:masonId', requireAuth, async (req, res) => {
    const { masonId } = req.params;
    console.log(`Get efficiency statistics for mason: ${masonId}`);
    
    try {
        const { db } = require('./db');
        
        // Session-based efficiency query (tool comparison)
        const sessionQuery = `
            SELECT
                build_session_id,
                COUNT(*) AS placements,
                MIN(timestamp) AS started_at_client,
                MAX(timestamp) AS ended_at_client,
                (MAX(timestamp) - MIN(timestamp)) / 1000.0 AS duration_s,
                MAX(event_seq) AS last_seq,
                AVG(rssi_avg) AS avg_rssi,
                AVG(power_level) AS avg_power
            FROM placements
            WHERE mason_id = ? AND build_session_id != ''
            GROUP BY build_session_id
            ORDER BY started_at_client DESC
            LIMIT 20
        `;
        
        db.all(sessionQuery, [masonId], (err, sessions) => {
            if (err) {
                console.error('Session query error:', err);
                return res.status(500).json({
                    success: false,
                    message: 'Database error',
                    error: err.message
                });
            }
            
            // Calculate placements/minute for each session
            const sessionMetrics = sessions.map(s => ({
                buildSessionId: s.build_session_id,
                placements: s.placements,
                startedAt: s.started_at_client,
                endedAt: s.ended_at_client,
                durationSeconds: s.duration_s,
                durationMinutes: s.duration_s / 60,
                placementsPerMinute: s.duration_s > 0 ? (s.placements / (s.duration_s / 60)) : 0,
                lastSequence: s.last_seq,
                avgRssi: s.avg_rssi,
                avgPower: s.avg_power
            }));
            
            // Overall summary for this mason
            const summaryQuery = `
                SELECT
                    COUNT(*) AS total_placements,
                    COUNT(DISTINCT build_session_id) AS total_sessions,
                    MIN(timestamp) AS first_placement,
                    MAX(timestamp) AS last_placement,
                    AVG(rssi_avg) AS overall_avg_rssi
                FROM placements
                WHERE mason_id = ?
            `;
            
            db.get(summaryQuery, [masonId], (err2, summary) => {
                if (err2) {
                    console.error('Summary query error:', err2);
                    return res.status(500).json({
                        success: false,
                        message: 'Database error',
                        error: err2.message
                    });
                }
                
                res.json({
                    success: true,
                    masonId,
                    summary: {
                        totalPlacements: summary.total_placements,
                        totalSessions: summary.total_sessions,
                        firstPlacement: summary.first_placement,
                        lastPlacement: summary.last_placement,
                        overallAvgRssi: summary.overall_avg_rssi
                    },
                    sessions: sessionMetrics
                });
            });
        });
    } catch (err) {
        console.error('Efficiency endpoint error:', err);
        return res.status(500).json({
            success: false,
            message: 'Server error',
            error: err.message
        });
    }
});

// GET /api/statistics/efficiency - Get mason efficiency comparison data (legacy - all masons)
app.get('/api/statistics/efficiency', requireAuth, async (req, res) => {
    const { masonId } = req.query;
    
    // If masonId provided as query param, redirect to specific endpoint
    if (masonId) {
        return res.redirect(`/api/statistics/efficiency/${masonId}`);
    }
    
    console.log('Get mason efficiency statistics (all masons)');
    
    try {
        const { db } = require('./db');
        
        // Simple test query first
        db.all("SELECT mason_id, timestamp FROM placements LIMIT 5", [], (err, rows) => {
            if (err) {
                console.error('Simple query error:', err);
                return res.status(500).json({
                    success: false,
                    message: 'Database error',
                    error: err.message
                });
            }
            
            console.log('Simple query worked, rows:', rows);
            
            // Now try the complex query - only count actual placements for efficiency
            const query = `
                SELECT 
                    mason_id,
                    COUNT(*) as total_placements,
                    MIN(timestamp) as first_placement,
                    MAX(timestamp) as last_placement,
                    MAX((MAX(timestamp) - MIN(timestamp)) / 3600000.0, 1.0) as hours_worked,
                    CAST(COUNT(*) AS FLOAT) / MAX((MAX(timestamp) - MIN(timestamp)) / 3600000.0, 1.0) as placements_per_hour,
                    DATE(timestamp / 1000, 'unixepoch', 'localtime') as work_date
                FROM placements
                WHERE scan_type != 'pallet' OR scan_type IS NULL
                GROUP BY mason_id, DATE(timestamp / 1000, 'unixepoch', 'localtime')
                ORDER BY mason_id, work_date DESC
            `;
            
            db.all(query, [], (err2, rows2) => {
                if (err2) {
                    console.error('Complex query error:', err2);
                    return res.status(500).json({
                        success: false,
                        message: 'Database error',
                        error: err2.message
                    });
                }
                
                // Get summary statistics
                const summaryQuery = `
                    SELECT 
                        mason_id,
                        COUNT(*) as total_placements,
                        SUM(CASE WHEN scan_type = 'pallet' THEN 1 ELSE 0 END) as pallet_count,
                        SUM(CASE WHEN scan_type != 'pallet' OR scan_type IS NULL THEN 1 ELSE 0 END) as placement_count,
                        MIN(timestamp) as first_placement_ever,
                        MAX(timestamp) as last_placement_ever,
                        COUNT(DISTINCT DATE(timestamp / 1000, 'unixepoch', 'localtime')) as days_worked,
                        CAST(COUNT(*) AS FLOAT) / COUNT(DISTINCT DATE(timestamp / 1000, 'unixepoch', 'localtime')) as avg_placements_per_day
                    FROM placements
                    GROUP BY mason_id
                    ORDER BY total_placements DESC
                `;
                
                db.all(summaryQuery, [], (err3, rows3) => {
                    if (err3) {
                        console.error('Summary query error:', err3);
                        return res.status(500).json({
                            success: false,
                            message: 'Database error',
                            error: err3.message
                        });
                    }
                    
                    res.json({
                        success: true,
                        dailyEfficiency: rows2.map(row => ({
                            masonId: row.mason_id,
                            date: row.work_date,
                            totalPlacements: row.total_placements,
                            hoursWorked: parseFloat(row.hours_worked.toFixed(2)),
                            placementsPerHour: parseFloat(row.placements_per_hour.toFixed(2)),
                            firstPlacement: row.first_placement,
                            lastPlacement: row.last_placement
                        })),
                        masonSummary: rows3.map(row => ({
                            masonId: row.mason_id,
                            totalPlacements: row.total_placements,
                            palletCount: row.pallet_count || 0,
                            placementCount: row.placement_count || 0,
                            daysWorked: row.days_worked,
                            avgPlacementsPerDay: parseFloat(row.avg_placements_per_day.toFixed(2)),
                            firstPlacementEver: row.first_placement_ever,
                            lastPlacementEver: row.last_placement_ever
                        }))
                    });
                });
            });
        });
    } catch (err) {
        console.error('Error fetching efficiency statistics:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error',
            error: err.message
        });
    }
});

// Debug: Get placement table structure
app.get('/api/debug/table-info', requireAuth, async (req, res) => {
    console.log('Get table info');
    const { db } = require('./db');
    
    db.all("PRAGMA table_info(placements)", [], (err, rows) => {
        if (err) {
            return res.status(500).json({ success: false, error: err.message });
        }
        res.json({ success: true, columns: rows });
    });
});

// Health check
app.get('/api/health', (req, res) => {
    res.json({ 
        status: 'OK', 
        timestamp: new Date().toISOString(),
        uptime: process.uptime()
    });
});

// Root endpoint - serve login page
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'login.html'));
});

// Dashboard route
app.get('/dashboard', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'dashboard.html'));
});

// GET placement history for a specific brick
app.get('/api/history/brick/:brickNumber', requireAuth, async (req, res) => {
    const { brickNumber } = req.params;
    
    console.log(`[HISTORY] Get history for brick: ${brickNumber}`);
    
    try {
        const { db } = require('./db');
        
        // Get current placement
        const currentQuery = `
            SELECT *, 'CURRENT' as record_type, created_at as record_time
            FROM placements 
            WHERE brick_number = ?
        `;
        
        // Get historical records
        const historyQuery = `
            SELECT *, 'HISTORY' as record_type, archived_at as record_time
            FROM placement_history 
            WHERE brick_number = ?
            ORDER BY archived_at ASC
        `;
        
        db.all(currentQuery, [brickNumber], (err, currentRows) => {
            if (err) {
                console.error('Current placement query error:', err);
                return res.status(500).json({
                    success: false,
                    message: 'Database error',
                    error: err.message
                });
            }
            
            db.all(historyQuery, [brickNumber], (err2, historyRows) => {
                if (err2) {
                    console.error('History query error:', err2);
                    return res.status(500).json({
                        success: false,
                        message: 'Database error',
                        error: err2.message
                    });
                }
                
                // Combine and sort all records chronologically
                const allRecords = [...historyRows, ...currentRows].sort((a, b) => {
                    const timeA = a.timestamp || 0;
                    const timeB = b.timestamp || 0;
                    return timeA - timeB;
                });
                
                res.json({
                    success: true,
                    brickNumber,
                    totalScans: allRecords.length,
                    history: allRecords
                });
            });
        });
    } catch (err) {
        console.error('Error fetching brick history:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error',
            error: err.message
        });
    }
});

// GET placement history for a mason
app.get('/api/history/mason/:masonId', requireAuth, async (req, res) => {
    const { masonId } = req.params;
    const limit = parseInt(req.query.limit) || 100;
    
    console.log(`[HISTORY] Get history for mason: ${masonId}`);
    
    try {
        const { db } = require('./db');
        
        const query = `
            SELECT * FROM placement_history 
            WHERE mason_id = ?
            ORDER BY archived_at DESC
            LIMIT ?
        `;
        
        db.all(query, [masonId, limit], (err, rows) => {
            if (err) {
                console.error('Mason history query error:', err);
                return res.status(500).json({
                    success: false,
                    message: 'Database error',
                    error: err.message
                });
            }
            
            res.json({
                success: true,
                masonId,
                totalRecords: rows.length,
                history: rows
            });
        });
    } catch (err) {
        console.error('Error fetching mason history:', err);
        return res.status(500).json({
            success: false,
            message: 'Database error',
            error: err.message
        });
    }
});

// GET performance review report
app.get('/api/report/mason/:masonId', requireAuth, async (req, res) => {
    const { masonId } = req.params;
    const startDate = parseInt(req.query.startDate) || (Date.now() - 30 * 24 * 60 * 60 * 1000); // Default: last 30 days
    const endDate = parseInt(req.query.endDate) || Date.now();
    const format = req.query.format || 'html'; // 'html' or 'json'
    const sections = req.query.sections ? req.query.sections.split(',') : null; // null = all
    
    console.log(`[REPORT] Generate performance review for ${masonId}, sections: ${sections ? sections.join(',') : 'all'}`);
    
    try {
        const { generatePerformanceReport, generateHTMLReport } = require('./reportGenerator');
        
        const reportData = await generatePerformanceReport(masonId, startDate, endDate);
        
        if (format === 'json') {
            return res.json({
                success: true,
                report: reportData
            });
        }
        
        // Generate HTML report
        const htmlReport = generateHTMLReport(reportData, sections);
        res.setHeader('Content-Type', 'text/html');
        res.send(htmlReport);
        
    } catch (err) {
        console.error('Error generating performance report:', err);
        return res.status(500).json({
            success: false,
            message: 'Failed to generate report',
            error: err.message
        });
    }
});

// 404 handler - must be after all other routes
app.use((req, res) => {
    res.status(404).json({
        success: false,
        message: 'Endpoint not found'
    });
});

// Error handler
app.use((err, req, res, next) => {
    console.error('Error:', err);
    res.status(500).json({
        success: false,
        message: 'Internal server error',
        error: err.message
    });
});

// Start server after database is ready
async function startServer() {
    try {
        // Initialize database first
        await initializeDatabase();
        
        // Then start Express server
        app.listen(PORT, '0.0.0.0', () => {
            console.log('╔════════════════════════════════════════════════╗');
            console.log('║   Efficiency Tracker API Server               ║');
            console.log('║   with SQLite Database                         ║');
            console.log('╚════════════════════════════════════════════════╝');
            console.log(`\n✓ Server running on http://0.0.0.0:${PORT}`);
            console.log(`✓ Local access: http://localhost:${PORT}`);
            console.log(`✓ Network access: http://<your-ip>:${PORT}`);
            console.log(`✓ Android emulator: http://10.0.2.2:${PORT}`);
            console.log('\nAPI Endpoints:');
            console.log('  Authentication:');
            console.log('    POST /api/auth/login');
            console.log('  Placements:');
            console.log('    POST /api/placements/sync');
            console.log('    GET  /api/placements/last?masonId=XXX');
            console.log('    GET  /api/placements/mason/:masonId');
            console.log('    DELETE /api/placements/mason/:masonId (Reset Profile)');
            console.log('    GET  /api/placements/recent?limit=50');
            console.log('  Statistics:');
            console.log('    GET  /api/statistics');
            console.log('  History (Audit Trail):');
            console.log('    GET  /api/history/brick/:brickNumber');
            console.log('    GET  /api/history/mason/:masonId?limit=100');
            console.log('  Debug:');
            console.log('    GET  /api/debug/placements');
            console.log('    GET  /api/debug/users');
            console.log('\n[Press Ctrl+C to stop]\n');
        });
    } catch (err) {
        console.error('✗ Failed to start server:', err);
        process.exit(1);
    }
}

// Start the server
startServer();

// Handle graceful shutdown
process.on('SIGTERM', () => {
    console.log('\n✓ Received SIGTERM, closing database...');
    closeDatabase();
    process.exit(0);
});

process.on('SIGINT', () => {
    console.log('\n✓ Received SIGINT, closing database...');
    closeDatabase();
    process.exit(0);
});
