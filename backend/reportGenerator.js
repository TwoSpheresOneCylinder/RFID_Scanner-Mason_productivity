// Performance Review Report Generator
const { db } = require('./db');

function generatePerformanceReport(masonId, startDate, endDate) {
    return new Promise(async (resolve, reject) => {
        try {
            const isAllMasons = !masonId || masonId === 'ALL';
            const displayName = isAllMasons ? 'All Masons' : masonId;
            
            const reportData = {
                masonId: displayName,
                generatedAt: new Date().toISOString(),
                period: { start: startDate, end: endDate }
            };

            // Get summary statistics
            const summaryQuery = `
                SELECT 
                    COUNT(*) as total_placements,
                    MIN(timestamp) as first_placement,
                    MAX(timestamp) as last_placement,
                    COUNT(DISTINCT DATE(timestamp / 1000, 'unixepoch', 'localtime')) as days_worked,
                    COUNT(DISTINCT brick_number) as unique_bricks
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

            resolve(reportData);
        } catch (err) {
            reject(err);
        }
    });
}

function generateHTMLReport(reportData) {
    const { masonId, generatedAt, period, summary, dailyBreakdown, improvements, hourlyPattern } = reportData;
    
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
    
    // Helper for pluralization
    const plural = (count, singular, plural) => count === 1 ? singular : plural;

    // Prepare chart data
    let chartData;
    if (masonId === 'All Masons') {
        // Multi-mason: group by mason_id for line chart
        const masonMap = {};
        dailyBreakdown.forEach(row => {
            if (!masonMap[row.mason_id]) {
                masonMap[row.mason_id] = [];
            }
            masonMap[row.mason_id].push({ date: row.date, placements: row.placements });
        });
        
        // Get all unique dates
        const allDates = [...new Set(dailyBreakdown.map(d => d.date))].sort();
        chartData = { allDates, masonMap };
    } else {
        // Single mason: use bar chart
        const dailyDates = dailyBreakdown.map(d => d.date);
        const dailyPlacements = dailyBreakdown.map(d => d.placements);
        chartData = { dailyDates, dailyPlacements };
    }

    const hourlyHours = hourlyPattern.map(h => `${h.hour}:00`);
    const hourlyPlacements = hourlyPattern.map(h => h.placements);

    return `
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Performance Review - ${masonId}</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <style>
        @media print {
            .no-print { display: none; }
            .page-break { page-break-after: always; }
        }
        
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: #f5f7fa;
            padding: 20px;
            color: #333;
        }
        
        .container {
            max-width: 1200px;
            margin: 0 auto;
            background: white;
            padding: 40px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        
        .header {
            text-align: center;
            padding-bottom: 30px;
            border-bottom: 3px solid #667eea;
            margin-bottom: 30px;
        }
        
        .header h1 {
            font-size: 2.5rem;
            color: #667eea;
            margin-bottom: 10px;
        }
        
        .header .meta {
            color: #666;
            font-size: 0.9rem;
        }
        
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin-bottom: 40px;
        }
        
        .stat-card {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            padding: 20px;
            border-radius: 8px;
            color: white;
            text-align: center;
        }
        
        .stat-card .value {
            font-size: 2.5rem;
            font-weight: bold;
            margin-bottom: 5px;
        }
        
        .stat-card .label {
            font-size: 0.9rem;
            opacity: 0.9;
        }
        
        .section {
            margin-bottom: 40px;
        }
        
        .section h2 {
            font-size: 1.8rem;
            color: #667eea;
            margin-bottom: 20px;
            padding-bottom: 10px;
            border-bottom: 2px solid #e0e0e0;
        }
        
        .chart-container {
            position: relative;
            height: 300px;
            margin-bottom: 30px;
        }
        
        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 20px;
        }
        
        th, td {
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid #e0e0e0;
        }
        
        th {
            background: #667eea;
            color: white;
            font-weight: 600;
        }
        
        tr:hover {
            background: #f5f7fa;
        }
        
        .improvement-badge {
            background: #4ade80;
            color: white;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 0.85rem;
            font-weight: bold;
        }
        
        .download-btn {
            background: #667eea;
            color: white;
            padding: 12px 24px;
            border: none;
            border-radius: 6px;
            font-size: 1rem;
            cursor: pointer;
            margin: 10px 5px;
        }
        
        .download-btn:hover {
            background: #5568d3;
        }
        
        .footer {
            text-align: center;
            margin-top: 50px;
            padding-top: 20px;
            border-top: 2px solid #e0e0e0;
            color: #666;
            font-size: 0.9rem;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Performance Review</h1>
            <div class="meta">
                <strong>${masonId}</strong><br>
                <strong>Period:</strong> ${periodLabel} (${startDate.toLocaleDateString()} - ${endDate.toLocaleDateString()})<br>
                Generated: ${new Date(generatedAt).toLocaleString()}
            </div>
            <div class="no-print" style="margin-top: 20px;">
                <button class="download-btn" onclick="window.print()">Print / Save as PDF</button>
                <button class="download-btn" onclick="downloadAsHTML()">Download HTML</button>
            </div>
        </div>

        <div class="section">
            <h2>Summary Statistics</h2>
            <div class="stats-grid">
                <div class="stat-card">
                    <div class="value">${summary.days_worked}</div>
                    <div class="label">${plural(summary.days_worked, 'Day Worked', 'Days Worked')}</div>
                </div>
                <div class="stat-card">
                    <div class="value">${summary.total_placements}</div>
                    <div class="label">Total ${plural(summary.total_placements, 'Placement', 'Placements')}</div>
                </div>
                <div class="stat-card">
                    <div class="value">${avgPerDay}</div>
                    <div class="label">Avg Per Day</div>
                </div>
            </div>
        </div>

        <div class="section page-break">
            <h2>Daily Productivity Trend</h2>
            <div class="chart-container">
                <canvas id="dailyChart"></canvas>
            </div>
        </div>

        <div class="section page-break">
            <h2>Hourly Productivity Pattern</h2>
            <div class="chart-container">
                <canvas id="hourlyChart"></canvas>
            </div>
        </div>

        <div class="section">
            <h2>Daily Performance Details</h2>
            <table>
                <thead>
                    <tr>
                        <th>Date</th>
                        <th>Placements</th>
                        <th>Hours Worked</th>
                        <th>Rate (per hour)</th>
                    </tr>
                </thead>
                <tbody>
                    ${dailyBreakdown.map(day => {
                        const hoursWorked = ((day.last_scan - day.first_scan) / 3600000).toFixed(1);
                        const rate = hoursWorked > 0 ? (day.placements / hoursWorked).toFixed(1) : 'N/A';
                        return `
                        <tr>
                            <td>${day.date}</td>
                            <td><strong>${day.placements}</strong></td>
                            <td>${hoursWorked}h</td>
                            <td>${rate} bricks/hr</td>
                        </tr>
                        `;
                    }).join('')}
                </tbody>
            </table>
        </div>

        <div class="footer">
            <p><strong>Mason Brick Tracking System</strong></p>
            <p>Performance Review Report - ${new Date().getFullYear()}</p>
        </div>
    </div>

    <script>
        const masonColors = ['#667eea', '#764ba2', '#f093fb', '#4facfe', '#43e97b', '#fa709a', '#fee140', '#30cfd0', '#a8edea', '#fed6e3', '#c471f5', '#12c2e9'];
        
        // Daily Productivity Chart
        ${masonId === 'All Masons' ? `
        // Multi-mason line chart
        const allDates = ${JSON.stringify(chartData.allDates)};
        const masonMap = ${JSON.stringify(chartData.masonMap)};
        const masonIds = Object.keys(masonMap).sort();
        
        const datasets = masonIds.map((mId, index) => {
            const masonData = masonMap[mId];
            const dataPoints = allDates.map(date => {
                const found = masonData.find(d => d.date === date);
                return found ? found.placements : null; // null creates gaps in line chart
            });
            
            return {
                label: mId,
                data: dataPoints,
                borderColor: masonColors[index % masonColors.length],
                backgroundColor: masonColors[index % masonColors.length] + '20',
                borderWidth: 3,
                tension: 0.4,
                fill: false,
                spanGaps: false // Don't connect lines across gaps
            };
        });
        
        new Chart(document.getElementById('dailyChart'), {
            type: 'line',
            data: {
                labels: allDates,
                datasets: datasets
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: true, position: 'top' }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        title: { display: true, text: 'Placements' }
                    }
                }
            }
        });
        ` : `
        // Single mason bar chart
        new Chart(document.getElementById('dailyChart'), {
            type: 'bar',
            data: {
                labels: ${JSON.stringify(chartData.dailyDates)},
                datasets: [{
                    label: 'Placements',
                    data: ${JSON.stringify(chartData.dailyPlacements)},
                    backgroundColor: '#667eea',
                    borderColor: '#667eea',
                    borderWidth: 2,
                    borderRadius: 5
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        title: { display: true, text: 'Placements' }
                    }
                }
            }
        });
        `}

        // Hourly Pattern Chart
        new Chart(document.getElementById('hourlyChart'), {
            type: 'bar',
            data: {
                labels: ${JSON.stringify(hourlyHours)},
                datasets: [{
                    label: 'Placements by Hour',
                    data: ${JSON.stringify(hourlyPlacements)},
                    backgroundColor: '#764ba2',
                    borderColor: '#764ba2',
                    borderWidth: 2,
                    borderRadius: 5
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        title: { display: true, text: 'Placements' }
                    },
                    x: {
                        title: { display: true, text: 'Hour of Day' }
                    }
                }
            }
        });

        function downloadAsHTML() {
            const htmlContent = document.documentElement.outerHTML;
            const blob = new Blob([htmlContent], { type: 'text/html' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'Performance_Review_${masonId}_${new Date().toISOString().split('T')[0]}.html';
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
