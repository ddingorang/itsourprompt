const { spawnSync } = require('node:child_process');

const testResult = spawnSync('npm', ['run', 'test:e2e'], {
  stdio: 'inherit',
  shell: true,
});

const uploadResult = spawnSync('npm', ['run', 'testrail:upload'], {
  stdio: 'inherit',
  shell: true,
});

if (uploadResult.status !== 0) {
  process.exit(uploadResult.status ?? 1);
}

process.exit(testResult.status ?? 1);