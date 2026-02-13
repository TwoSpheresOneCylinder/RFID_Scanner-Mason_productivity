// Performance Review Report Generator
const { db } = require('./db');

function generatePerformanceReport(masonId, startDate, endDate) {
    return new Promise(async (resolve, reject) => {
        try {
            const isAllMasons = !masonId || masonId === 'ALL';
            const displayName = isAllMasons ? 'All Users' : masonId;
            
            const reportData = {
                masonId: displayName,
                generatedAt: new Date().toISOString(),
                period: { start: startDate, end: endDate }
            };

            // Get summary statistics
            const summaryQuery = `
                SELECT 
                    COUNT(*) as total_placements,
                    SUM(CASE WHEN scan_type = 'pallet' THEN 1 ELSE 0 END) as total_indexed,
                    SUM(CASE WHEN scan_type != 'pallet' OR scan_type IS NULL THEN 1 ELSE 0 END) as placement_count,
                    MIN(timestamp) as first_placement,
                    MAX(timestamp) as last_placement,
                    COUNT(DISTINCT DATE(timestamp / 1000, 'unixepoch', 'localtime')) as days_worked,
                    COUNT(DISTINCT brick_number) as unique_bricks,
                    COUNT(DISTINCT mason_id) as active_masons
                FROM placements
                WHERE ${isAllMasons ? '1=1' : 'mason_id = ?'}
                    AND timestamp >= ?
                    AND timestamp <= ?
            `;

            const summaryParams = isAllMasons ? [startDate, endDate] : [masonId, startDate, endDate];
            const summaryResult = await new Promise((res, rej) => {
                db.get(summaryQuery, summaryParams, (err, row) => {
                    if (err) rej(err);
                    else res(row);
                });
            });

            reportData.summary = summaryResult;

            // Get daily breakdown
            const dailyQuery = `
                SELECT 
                    DATE(timestamp / 1000, 'unixepoch', 'localtime') as date,
                    ${isAllMasons ? 'mason_id,' : ''}
                    COUNT(*) as placements,
                    MIN(timestamp) as first_scan,
                    MAX(timestamp) as last_scan
                FROM placements
                WHERE ${isAllMasons ? '1=1' : 'mason_id = ?'}
                    AND timestamp >= ?
                    AND timestamp <= ?
                GROUP BY DATE(timestamp / 1000, 'unixepoch', 'localtime')${isAllMasons ? ', mason_id' : ''}
                ORDER BY date ASC${isAllMasons ? ', mason_id' : ''}
            `;

            const dailyParams = isAllMasons ? [startDate, endDate] : [masonId, startDate, endDate];
            const dailyData = await new Promise((res, rej) => {
                db.all(dailyQuery, dailyParams, (err, rows) => {
                    if (err) rej(err);
                    else res(rows);
                });
            });

            reportData.dailyBreakdown = dailyData;

            // Get rescanned bricks count (shows attention to quality)
            const improvementQuery = `
                SELECT 
                    COUNT(DISTINCT brick_number) as rescanned_bricks,
                    COUNT(*) as total_rescans
                FROM placement_history
                WHERE ${isAllMasons ? '1=1' : 'mason_id = ?'}
                    AND timestamp >= ?
                    AND timestamp <= ?
            `;

            const improvementParams = isAllMasons ? [startDate, endDate] : [masonId, startDate, endDate];
            const improvementData = await new Promise((res, rej) => {
                db.get(improvementQuery, improvementParams, (err, row) => {
                    if (err) rej(err);
                    else res(row);
                });
            });

            reportData.improvements = improvementData;

            // Get hourly productivity pattern
            const hourlyQuery = `
                SELECT 
                    CAST(strftime('%H', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) as hour,
                    COUNT(*) as placements
                FROM placements
                WHERE ${isAllMasons ? '1=1' : 'mason_id = ?'}
                    AND timestamp >= ?
                    AND timestamp <= ?
                GROUP BY hour
                ORDER BY hour
            `;

            const hourlyParams = isAllMasons ? [startDate, endDate] : [masonId, startDate, endDate];
            const hourlyData = await new Promise((res, rej) => {
                db.all(hourlyQuery, hourlyParams, (err, rows) => {
                    if (err) rej(err);
                    else res(rows);
                });
            });

            reportData.hourlyPattern = hourlyData;

            // Get daily efficiency data (placements per hour per day)
            const efficiencyQuery = `
                SELECT 
                    DATE(timestamp / 1000, 'unixepoch', 'localtime') as date,
                    COUNT(*) as day_placements,
                    MIN(timestamp) as day_start,
                    MAX(timestamp) as day_end,
                    MAX((MAX(timestamp) - MIN(timestamp)) / 3600000.0, 1.0) as hours_worked,
                    CAST(COUNT(*) AS FLOAT) / MAX((MAX(timestamp) - MIN(timestamp)) / 3600000.0, 1.0) as placements_per_hour
                FROM placements
                WHERE ${isAllMasons ? '1=1' : 'mason_id = ?'}
                    AND (scan_type != 'pallet' OR scan_type IS NULL)
                    AND timestamp >= ?
                    AND timestamp <= ?
                GROUP BY DATE(timestamp / 1000, 'unixepoch', 'localtime')
                ORDER BY date DESC
            `;

            const efficiencyParams = isAllMasons ? [startDate, endDate] : [masonId, startDate, endDate];
            const efficiencyData = await new Promise((res, rej) => {
                db.all(efficiencyQuery, efficiencyParams, (err, rows) => {
                    if (err) rej(err);
                    else res(rows || []);
                });
            });

            reportData.efficiencyData = efficiencyData;

            resolve(reportData);
        } catch (err) {
            reject(err);
        }
    });
}

function generateHTMLReport(reportData, sections) {
    const { masonId, generatedAt, period, summary, dailyBreakdown, improvements, hourlyPattern, efficiencyData } = reportData;
    
    // If no sections specified, show all (backwards-compatible)
    const ALL_SECTIONS = ['daysWorked','totalPlacements','totalIndexed','avgPerDay','peakHour','currentEfficiency','activeMasons','totalScans','avgPerHourAllTime','uniqueBricks','dailyChart','hourlyChart'];
    const show = new Set(sections && sections.length > 0 ? sections : ALL_SECTIONS);
    const showAnyKpi = ['daysWorked','totalPlacements','totalIndexed','avgPerDay','peakHour','currentEfficiency','activeMasons','totalScans','avgPerHourAllTime','uniqueBricks'].some(k => show.has(k));
    const showAnyChart = show.has('dailyChart') || show.has('hourlyChart');
    
    // XSS sanitization helper
    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
    
    const safeMasonId = escapeHtml(masonId);
    
    // Calculate period label
    const startDate = new Date(period.start);
    const endDate = new Date(period.end);
    const daysDiff = Math.ceil((endDate - startDate) / (1000 * 60 * 60 * 24));
    let periodLabel = '';
    if (daysDiff <= 1) periodLabel = 'Today';
    else if (daysDiff <= 7) periodLabel = 'Last 7 Days';
    else if (daysDiff <= 30) periodLabel = 'Last 30 Days';
    else if (daysDiff <= 90) periodLabel = 'Last 90 Days';
    else if (daysDiff <= 365) periodLabel = 'Last Year';
    else periodLabel = 'All Time';
    
    const avgPerDay = summary.days_worked > 0 ? (summary.total_placements / summary.days_worked).toFixed(1) : '0';
    
    // Compute extra KPI values
    const totalIndexed = summary.total_indexed || 0;
    const placementCount = summary.placement_count || summary.total_placements;
    const totalScans = summary.total_placements;
    const activeMasons = summary.active_masons || 1;
    const uniqueBricks = summary.unique_bricks || 0;
    
    // Current efficiency = most recent day's placements/hour
    const latestDay = efficiencyData && efficiencyData.length > 0 ? efficiencyData[0] : null;
    const currentEfficiency = latestDay ? latestDay.placements_per_hour.toFixed(1) : 'N/A';
    
    // Avg per hour all time
    const totalHours = efficiencyData ? efficiencyData.reduce((sum, d) => sum + (d.hours_worked || 0), 0) : 0;
    const totalPlacementsForRate = efficiencyData ? efficiencyData.reduce((sum, d) => sum + (d.day_placements || 0), 0) : 0;
    const avgPerHourAllTime = totalHours > 0 ? (totalPlacementsForRate / totalHours).toFixed(1) : 'N/A';
    
    // Helper for pluralization
    const plural = (count, singular, pluralForm) => count === 1 ? singular : pluralForm;

    // Prepare chart data
    let chartData;
    if (masonId === 'All Users') {
        const masonMap = {};
        dailyBreakdown.forEach(row => {
            if (!masonMap[row.mason_id]) {
                masonMap[row.mason_id] = [];
            }
            masonMap[row.mason_id].push({ date: row.date, placements: row.placements });
        });
        const allDates = [...new Set(dailyBreakdown.map(d => d.date))].sort();
        chartData = { allDates, masonMap };
    } else {
        const dailyDates = dailyBreakdown.map(d => d.date);
        const dailyPlacements = dailyBreakdown.map(d => d.placements);
        chartData = { dailyDates, dailyPlacements };
    }

    const hourlyHours = hourlyPattern.map(h => `${h.hour}:00`);
    const hourlyPlacements = hourlyPattern.map(h => h.placements);

    // Find peak hour
    const peakHour = hourlyPattern.length > 0 
        ? hourlyPattern.reduce((max, h) => h.placements > max.placements ? h : max, hourlyPattern[0])
        : null;

    return `
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Performance Review - ${safeMasonId}</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <link href="https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@400;600;700;800&display=swap" rel="stylesheet">
    <style>
        @media print {
            .no-print { display: none !important; }
            .page-break { page-break-before: always; }
            body { padding: 0; margin: 0; background: white !important; -webkit-print-color-adjust: exact; }
            .container { box-shadow: none !important; border-radius: 0; max-width: 100%; }
            .report-header { box-shadow: none !important; border-radius: 0; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
            .header-left { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
            .accent-bar { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
            .stat-card { box-shadow: none !important; border: 1px solid #ccc; break-inside: avoid; }
            .stat-card:hover { transform: none; }
            .chart-box { box-shadow: none !important; border: 1px solid #ccc; break-inside: avoid; padding: 15px; }
            .chart-wrapper { height: auto !important; }
            canvas { display: none !important; }
            .chart-print-img { display: block !important; max-width: 100%; height: auto; }
            .report-footer { border-top: 1px solid #ccc; }
            th { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
            a { text-decoration: none; color: inherit; }
        }
        
        * { margin: 0; padding: 0; box-sizing: border-box; }
        
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: #e8e8e8;
            min-height: 100vh;
            padding: 20px;
            color: #333;
        }
        
        .container {
            max-width: 1400px;
            margin: 0 auto;
            background: white;
            border-radius: 12px;
            padding: 0;
            box-shadow: 0 4px 16px rgba(0,0,0,0.1), 0 12px 40px rgba(0,0,0,0.15);
            overflow: hidden;
        }
        
        /* Header — matches dashboard */
        .report-header {
            display: flex;
            align-items: stretch;
            border-radius: 12px 12px 0 0;
            overflow: hidden;
            min-height: 100px;
            position: relative;
            box-shadow: 0 6px 16px rgba(0,0,0,0.25), 0 12px 32px rgba(0,0,0,0.15);
        }
        
        .header-left {
            background: #2D3436;
            color: white;
            flex: 0 0 70%;
            display: flex;
            flex-direction: column;
            justify-content: center;
            padding: 25px 60px 25px 30px;
            position: relative;
            z-index: 1;
        }
        
        .header-left::after {
            content: '';
            position: absolute;
            top: 0;
            right: -40px;
            width: 80px;
            height: 100%;
            background: #2D3436;
            transform: skewX(-17deg);
            z-index: 1;
        }
        
        .header-left h1 {
            font-family: 'Barlow Semi Condensed', sans-serif;
            font-size: 2.2rem;
            margin: 0 0 6px 0;
            font-weight: 700;
            letter-spacing: 0.5px;
            text-transform: uppercase;
            text-shadow: 0 1px 3px rgba(0,0,0,0.15);
        }
        
        .header-left p {
            font-size: 1.05rem;
            opacity: 0.7;
            margin: 0;
        }
        
        .header-right {
            background: white;
            flex: 0 0 30%;
            display: flex;
            align-items: center;
            justify-content: flex-end;
            padding: 20px 30px 20px 60px;
        }
        
        .header-right .report-badge {
            font-family: 'Barlow Semi Condensed', sans-serif;
            font-size: 0.85rem;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 1px;
            color: #CC0000;
            border: 2px solid #CC0000;
            padding: 6px 14px;
            border-radius: 6px;
        }
        
        /* Red accent bar */
        .accent-bar {
            background: #CC0000;
            padding: 14px 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            color: white;
            font-size: 0.9rem;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        }
        
        .accent-bar strong {
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        
        .accent-bar .period {
            opacity: 0.9;
        }
        
        /* Content area */
        .report-body { padding: 30px; }
        
        /* Section headings */
        .section { margin-bottom: 30px; }
        
        .section-title {
            font-family: 'Barlow Semi Condensed', sans-serif;
            color: #333;
            font-size: 1.1rem;
            font-weight: 700;
            letter-spacing: 0.5px;
            text-transform: uppercase;
            margin-bottom: 16px;
            padding-bottom: 8px;
            border-bottom: 2px solid #e0e0e0;
        }
        
        /* Stat cards — matches dashboard */
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        
        .stat-card {
            background: white;
            padding: 25px;
            border-radius: 10px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.08), 0 8px 24px rgba(0,0,0,0.12);
            text-align: center;
            transition: transform 0.3s;
        }
        
        .stat-card:hover {
            transform: translateY(-5px);
            box-shadow: 0 4px 12px rgba(0,0,0,0.1), 0 12px 32px rgba(0,0,0,0.18);
        }
        
        .stat-card .icon {
            margin-bottom: 10px;
        }
        
        .stat-card .icon svg {
            width: 28px;
            height: 28px;
        }
        
        .stat-card h3 {
            font-family: 'Barlow Semi Condensed', sans-serif;
            color: #555;
            font-size: 0.9rem;
            text-transform: uppercase;
            margin-bottom: 10px;
            letter-spacing: 1px;
            font-weight: 600;
        }
        
        .stat-card .value {
            font-family: 'Barlow Semi Condensed', sans-serif;
            font-size: 2.5rem;
            font-weight: 700;
            color: #333;
            margin-bottom: 5px;
        }
        
        .stat-card .sub {
            color: #888;
            font-size: 0.8rem;
        }
        
        /* Chart containers — matches dashboard */
        .chart-box {
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.08), 0 8px 24px rgba(0,0,0,0.12);
            margin-bottom: 20px;
        }
        
        .chart-box h2 {
            font-family: 'Barlow Semi Condensed', sans-serif;
            color: #333;
            margin-bottom: 20px;
            text-align: center;
            font-weight: 700;
            letter-spacing: 0.5px;
            text-transform: uppercase;
            font-size: 1rem;
        }
        
        .chart-wrapper {
            position: relative;
            height: 320px;
        }
        
        .chart-print-img {
            display: none;
            width: 100%;
        }
        
        /* Table — matches dashboard */
        .table-container {
            overflow-x: auto;
        }
        
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.95rem;
        }
        
        th {
            background: #2D3436;
            color: white;
            font-weight: 600;
            padding: 12px 16px;
            text-align: left;
            font-family: 'Barlow Semi Condensed', sans-serif;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            font-size: 0.85rem;
        }
        
        td {
            padding: 12px 16px;
            border-bottom: 1px solid #e8e8e8;
        }
        
        tr:hover td {
            background: #f5f5f5;
        }
        
        .rate-badge {
            display: inline-block;
            padding: 3px 10px;
            border-radius: 4px;
            font-size: 0.8rem;
            font-weight: 700;
        }
        
        .rate-good { background: #d1fae5; color: #065f46; }
        .rate-ok { background: #fef3c7; color: #92400e; }
        .rate-low { background: #fee2e2; color: #991b1b; }
        
        /* Action buttons */
        .actions {
            display: flex;
            gap: 10px;
            justify-content: center;
            padding: 20px 0;
        }
        
        .btn {
            padding: 10px 20px;
            border: none;
            border-radius: 5px;
            font-size: 0.9rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.3s;
            box-shadow: 0 1px 4px rgba(0,0,0,0.1);
        }
        
        .btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 8px rgba(0,0,0,0.2);
        }
        
        .btn-dark {
            background: #2D3436;
            color: white;
        }
        
        .btn-dark:hover { background: #444; }
        
        .btn-red {
            background: #CC0000;
            color: white;
        }
        
        .btn-red:hover { background: #a00; }
        
        /* Footer */
        .report-footer {
            text-align: center;
            padding: 20px 30px;
            border-top: 1px solid #e0e0e0;
            color: #888;
            font-size: 0.8rem;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="report-header">
            <div class="header-left">
                <h1>Performance Review</h1>
                <p>${safeMasonId}</p>
            </div>
            <div class="header-right">
                <span class="report-badge">Official Report</span>
            </div>
        </div>
        
        <div class="accent-bar">
            <div>
                <strong>${safeMasonId}</strong>
            </div>
            <div class="period">${periodLabel} &mdash; ${startDate.toLocaleDateString()} to ${endDate.toLocaleDateString()}</div>
        </div>
        
        <div class="report-body">
            <!-- Summary Stats (conditional) -->
            ${showAnyKpi ? `
            <div class="section">
                <div class="stats-grid">
                    ${show.has('daysWorked') ? `
                    <div class="stat-card">
                        <div class="icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#CC0000" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                        </div>
                        <h3>Days Worked</h3>
                        <div class="value">${summary.days_worked}</div>
                    </div>` : ''}
                    ${show.has('totalPlacements') ? `
                    <div class="stat-card">
                        <div class="icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#CC0000" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
                        </div>
                        <h3>Total Placements</h3>
                        <div class="value">${placementCount}</div>
                    </div>` : ''}
                    ${show.has('totalIndexed') ? `
                    <div class="stat-card">
                        <div class="icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#CC0000" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1"/></svg>
                        </div>
                        <h3>Blocks Indexed</h3>
                        <div class="value">${totalIndexed}</div>
                    </div>` : ''}
                    ${show.has('avgPerDay') ? `
                    <div class="stat-card">
                        <div class="icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#CC0000" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="20" x2="12" y2="10"/><line x1="18" y1="20" x2="18" y2="4"/><line x1="6" y1="20" x2="6" y2="16"/></svg>
                        </div>
                        <h3>Avg Per Day</h3>
                        <div class="value">${avgPerDay}</div>
                    </div>` : ''}
                    ${show.has('peakHour') ? `
                    <div class="stat-card">
                        <div class="icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#CC0000" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                        </div>
                        <h3>Peak Hour</h3>
                        <div class="value">${peakHour ? peakHour.hour + ':00' : 'N/A'}</div>
                        <div class="sub">${peakHour ? peakHour.placements + ' placements' : ''}</div>
                    </div>` : ''}
                    ${show.has('currentEfficiency') ? `
                    <div class="stat-card">
                        <div class="icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#CC0000" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
                        </div>
                        <h3>Placements / Hour</h3>
                        <div class="value">${currentEfficiency}</div>
                        <div class="sub">Most recent day</div>
                    </div>` : ''}
                    ${show.has('activeMasons') ? `
                    <div class="stat-card">
                        <div class="icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#CC0000" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                        </div>
                        <h3>Active Users</h3>
                        <div class="value">${activeMasons}</div>
                    </div>` : ''}
                    ${show.has('totalScans') ? `
                    <div class="stat-card">
                        <div class="icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#CC0000" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 16.1A5 5 0 0 1 5.9 20M2 12.05A9 9 0 0 1 9.95 20M2 8V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6"/><line x1="2" y1="20" x2="2.01" y2="20"/></svg>
                        </div>
                        <h3>Total Scans</h3>
                        <div class="value">${totalScans}</div>
                        <div class="sub">${placementCount} placed + ${totalIndexed} indexed</div>
                    </div>` : ''}
                    ${show.has('avgPerHourAllTime') ? `
                    <div class="stat-card">
                        <div class="icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#CC0000" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                        </div>
                        <h3>Avg / Hour (All Time)</h3>
                        <div class="value">${avgPerHourAllTime}</div>
                    </div>` : ''}
                    ${show.has('uniqueBricks') ? `
                    <div class="stat-card">
                        <div class="icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="#CC0000" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
                        </div>
                        <h3>Unique Bricks</h3>
                        <div class="value">${uniqueBricks}</div>
                    </div>` : ''}
                </div>
            </div>` : ''}
            
            ${show.has('dailyChart') ? `
            <!-- Daily Trend Chart -->
            <div class="section">
                <div class="chart-box">
                    <h2>Daily Productivity Trend</h2>
                    <div class="chart-wrapper">
                        <canvas id="dailyChart"></canvas>
                    </div>
                </div>
            </div>` : ''}
            
            ${show.has('hourlyChart') ? `
            <!-- Hourly Pattern Chart -->
            <div class="section page-break">
                <div class="chart-box">
                    <h2>Hourly Productivity Pattern</h2>
                    <div class="chart-wrapper">
                        <canvas id="hourlyChart"></canvas>
                    </div>
                </div>
            </div>` : ''}
            
            <!-- Actions -->
            <div class="actions no-print">
                <button class="btn btn-dark" onclick="printReport()">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align:middle;margin-right:6px;"><polyline points="6 9 6 2 18 2 18 9"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect x="6" y="14" width="12" height="8"/></svg>
                    Print / Save PDF
                </button>
                <button class="btn btn-red" onclick="downloadAsHTML()">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align:middle;margin-right:6px;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                    Download HTML
                </button>
            </div>
        </div>

        <div class="report-footer">
            <strong>Efficiency Tracker</strong> &mdash; Performance Review Report &mdash; Generated ${new Date(generatedAt).toLocaleString()}
        </div>
    </div>

    <script>
        const chartColors = ['#FF0000', '#0066FF', '#00CC00', '#FF6600', '#AA00FF', '#00CCCC', '#FF00AA', '#CCCC00', '#FF3399', '#00FF66', '#3366FF', '#FF9900'];
        const dashPatterns = [[], [10,5], [3,3], [15,5,3,5], [8,3,3,3], [5,10], [20,5], [3,8], [10,3,3,3,3,3], [15,3], [6,3,2,3], [2,6]];
        const dashNames   = ['Solid', 'Dashed', 'Dotted', 'Dash-Dot', 'Dash-Dot-Dot', 'Spaced', 'Long Dash', 'Short Space', 'Dash-Multi', 'Long-Short', 'Mixed', 'Micro'];
        const pointStyles = ['circle', 'rect', 'triangle', 'rectRot', 'cross', 'crossRot', 'star', 'line', 'dash', 'circle', 'rect', 'triangle'];
        const pointMarkers = ['\u25cf', '\u25a0', '\u25b2', '\u25c6', '\u2716', '\u2718', '\u2605', '\u2501', '\u2500', '\u25cf', '\u25a0', '\u25b2'];
        
        // Daily Productivity Chart
        ${show.has('dailyChart') ? (masonId === 'All Users' ? `
        const allDates = ${JSON.stringify(chartData.allDates)};
        const masonMap = ${JSON.stringify(chartData.masonMap)};
        const masonIds = Object.keys(masonMap).sort();
        
        const datasets = masonIds.map((mId, index) => {
            const masonData = masonMap[mId];
            const dataPoints = allDates.map(date => {
                const found = masonData.find(d => d.date === date);
                return found ? found.placements : null;
            });
            
            return {
                label: mId + '  ' + pointMarkers[index % pointMarkers.length] + ' ' + dashNames[index % dashNames.length],
                data: dataPoints,
                borderColor: chartColors[index % chartColors.length],
                backgroundColor: chartColors[index % chartColors.length] + '20',
                borderWidth: 3,
                borderDash: dashPatterns[index % dashPatterns.length],
                tension: 0.4,
                fill: false,
                spanGaps: false,
                pointStyle: pointStyles[index % pointStyles.length],
                pointRadius: 6,
                pointBorderWidth: 2,
                pointBackgroundColor: chartColors[index % chartColors.length]
            };
        });
        
        new Chart(document.getElementById('dailyChart'), {
            type: 'line',
            data: { labels: allDates, datasets: datasets },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: true,
                        position: 'top',
                        labels: {
                            font: { family: "'Barlow Semi Condensed', sans-serif", weight: '600', size: 13 },
                            usePointStyle: true,
                            pointStyleWidth: 24,
                            padding: 20
                        }
                    }
                },
                scales: {
                    y: { beginAtZero: true, title: { display: true, text: 'Placements', font: { family: "'Barlow Semi Condensed', sans-serif" } } }
                }
            }
        });
        ` : `
        new Chart(document.getElementById('dailyChart'), {
            type: 'bar',
            data: {
                labels: ${JSON.stringify(chartData.dailyDates)},
                datasets: [{
                    label: 'Placements',
                    data: ${JSON.stringify(chartData.dailyPlacements)},
                    backgroundColor: '#2D3436',
                    borderColor: '#2D3436',
                    borderWidth: 0,
                    borderRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                    y: { beginAtZero: true, title: { display: true, text: 'Placements', font: { family: "'Barlow Semi Condensed', sans-serif" } } }
                }
            }
        });
        `) : '// Daily chart not selected'}

        // Hourly Pattern Chart
        ${show.has('hourlyChart') ? `
        new Chart(document.getElementById('hourlyChart'), {
            type: 'bar',
            data: {
                labels: ${JSON.stringify(hourlyHours)},
                datasets: [{
                    label: 'Placements by Hour',
                    data: ${JSON.stringify(hourlyPlacements)},
                    backgroundColor: '#CC0000',
                    borderColor: '#CC0000',
                    borderWidth: 0,
                    borderRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                    y: { beginAtZero: true, title: { display: true, text: 'Placements', font: { family: "'Barlow Semi Condensed', sans-serif" } } },
                    x: { title: { display: true, text: 'Hour of Day', font: { family: "'Barlow Semi Condensed', sans-serif" } } }
                }
            }
        });
        ` : '// Hourly chart not selected'}

        function printReport() {
            // Convert all Chart.js canvases to images for print
            document.querySelectorAll('.chart-wrapper').forEach(wrapper => {
                const canvas = wrapper.querySelector('canvas');
                if (!canvas) return;
                // Remove any previous print image
                const old = wrapper.querySelector('.chart-print-img');
                if (old) old.remove();
                // Create image from canvas
                const img = document.createElement('img');
                img.className = 'chart-print-img';
                img.src = canvas.toDataURL('image/png', 1.0);
                wrapper.appendChild(img);
            });
            setTimeout(() => window.print(), 100);
        }

        function downloadAsHTML() {
            const htmlContent = document.documentElement.outerHTML;
            const blob = new Blob([htmlContent], { type: 'text/html' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'Performance_Review_${safeMasonId}_${new Date().toISOString().split('T')[0]}.html';
            a.click();
            URL.revokeObjectURL(url);
        }
    </script>
</body>
</html>
    `;
}

module.exports = {
    generatePerformanceReport,
    generateHTMLReport
};
