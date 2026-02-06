module.exports = {
  apps: [{
    name: 'mason-brick-api',
    script: 'server.js',
    cwd: __dirname,
    instances: 1,
    autorestart: true,
    watch: false,
    max_memory_restart: '200M',
    env: {
      NODE_ENV: 'production',
      PORT: 8080
    },
    env_development: {
      NODE_ENV: 'development',
      PORT: 8080
    },
    // Log settings
    error_file: './logs/error.log',
    out_file: './logs/output.log',
    merge_logs: true,
    log_date_format: 'YYYY-MM-DD HH:mm:ss Z'
  }]
};
