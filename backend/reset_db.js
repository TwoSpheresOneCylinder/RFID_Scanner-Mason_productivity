/**
 * Database Reset Utility
 * 
 * Usage:
 *   node reset_db.js              - Interactive menu
 *   node reset_db.js placements   - Clear all placement data
 *   node reset_db.js users        - Clear users (re-seeds defaults)
 *   node reset_db.js all          - Nuke everything and re-seed
 */

const sqlite3 = require('sqlite3').verbose();
const bcrypt = require('bcryptjs');
const path = require('path');
const readline = require('readline');

const DB_PATH = path.join(__dirname, 'mason_tracking.db');

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
});

function ask(question) {
    return new Promise(resolve => rl.question(question, resolve));
}

function openDb() {
    return new Promise((resolve, reject) => {
        const db = new sqlite3.Database(DB_PATH, (err) => {
            if (err) reject(err);
            else resolve(db);
        });
    });
}

function run(db, sql) {
    return new Promise((resolve, reject) => {
        db.run(sql, function(err) {
            if (err) reject(err);
            else resolve(this.changes);
        });
    });
}

function get(db, sql) {
    return new Promise((resolve, reject) => {
        db.get(sql, (err, row) => {
            if (err) reject(err);
            else resolve(row);
        });
    });
}

async function getCounts(db) {
    const users = await get(db, 'SELECT COUNT(*) as count FROM users');
    const placements = await get(db, 'SELECT COUNT(*) as count FROM placements');
    const history = await get(db, 'SELECT COUNT(*) as count FROM placement_history');
    const sessions = await get(db, 'SELECT COUNT(*) as count FROM sessions');
    return {
        users: users.count,
        placements: placements.count,
        history: history.count,
        sessions: sessions.count
    };
}

async function seedDefaults(db) {
    const defaultUsers = [
        { username: 'mason1', password: 'password123', mason_id: 'MASON_001' },
        { username: 'mason2', password: 'password123', mason_id: 'MASON_002' },
        { username: 'admin', password: 'password123', mason_id: 'MASON_ADMIN' }
    ];

    for (const user of defaultUsers) {
        const hash = bcrypt.hashSync(user.password, 10);
        await run(db, `INSERT OR IGNORE INTO users (username, password, mason_id) VALUES ('${user.username}', '${hash}', '${user.mason_id}')`);
    }
    console.log('  ✓ Default users re-seeded (mason1, mason2, admin)');
}

async function clearPlacements(db) {
    const deleted = await run(db, 'DELETE FROM placement_history');
    console.log(`  ✓ Cleared placement_history (${deleted} rows)`);
    const deleted2 = await run(db, 'DELETE FROM placements');
    console.log(`  ✓ Cleared placements (${deleted2} rows)`);
}

async function clearUsers(db) {
    const deleted = await run(db, 'DELETE FROM sessions');
    console.log(`  ✓ Cleared sessions (${deleted} rows)`);
    const deleted2 = await run(db, 'DELETE FROM users');
    console.log(`  ✓ Cleared users (${deleted2} rows)`);
    await seedDefaults(db);
}

async function clearAll(db) {
    await clearPlacements(db);
    await clearUsers(db);
}

async function main() {
    const db = await openDb();
    const counts = await getCounts(db);
    const action = process.argv[2];

    console.log('\n=== Mason Brick Tracking - Database Reset ===');
    console.log(`  Users:             ${counts.users}`);
    console.log(`  Placements:        ${counts.placements}`);
    console.log(`  Placement History: ${counts.history}`);
    console.log(`  Sessions:          ${counts.sessions}`);
    console.log('');

    if (action) {
        // CLI mode
        const confirm = await ask(`⚠️  Clear "${action}" data? Type YES to confirm: `);
        if (confirm !== 'YES') {
            console.log('Cancelled.');
            rl.close();
            db.close();
            return;
        }

        if (action === 'placements') await clearPlacements(db);
        else if (action === 'users') await clearUsers(db);
        else if (action === 'all') await clearAll(db);
        else console.log(`Unknown action: ${action}. Use: placements, users, or all`);

    } else {
        // Interactive menu
        console.log('What would you like to clear?');
        console.log('  1) Placements + history (keeps users)');
        console.log('  2) Users + sessions (re-seeds defaults)');
        console.log('  3) Everything (full reset, re-seeds defaults)');
        console.log('  4) Cancel');
        console.log('');

        const choice = await ask('Choose [1-4]: ');

        if (choice === '4' || !['1','2','3'].includes(choice)) {
            console.log('Cancelled.');
            rl.close();
            db.close();
            return;
        }

        const confirm = await ask('⚠️  This cannot be undone. Type YES to confirm: ');
        if (confirm !== 'YES') {
            console.log('Cancelled.');
            rl.close();
            db.close();
            return;
        }

        if (choice === '1') await clearPlacements(db);
        else if (choice === '2') await clearUsers(db);
        else if (choice === '3') await clearAll(db);
    }

    const after = await getCounts(db);
    console.log('\n--- After reset ---');
    console.log(`  Users:             ${after.users}`);
    console.log(`  Placements:        ${after.placements}`);
    console.log(`  Placement History: ${after.history}`);
    console.log(`  Sessions:          ${after.sessions}`);
    console.log('');

    rl.close();
    db.close();
}

main().catch(err => {
    console.error('Error:', err);
    rl.close();
    process.exit(1);
});
