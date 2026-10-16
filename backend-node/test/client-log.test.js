const assert = require('node:assert/strict');
const { test } = require('node:test');
const { formatClientLog, ClientLogService } = require('../dist/client-log/client-log.service.js');

test('client log redacts secrets and strips query strings', () => {
  const line = formatClientLog({
    level: 'error',
    scope: 'http',
    message: 'password=secret failed',
    path: '/tasks',
    detail: {
      status: 500,
      method: 'post',
      url: '/api/tasks?access_token=abc',
      code: 'UPSTREAM',
      serverMessage: 'bad credential',
    },
  });
  assert.match(line, /\[skytrace-web\]/);
  assert.match(line, /\[redacted\]=\[redacted\]/);
  assert.match(line, /url=\/api\/tasks/);
  assert.equal(line.includes('abc'), false);
  assert.match(line, /serverMessage=bad \[redacted\]/);
});

test('same address stops after 30 client logs in one minute', () => {
  const service = new ClientLogService();
  const lines = [];
  const original = console.warn;
  console.warn = (line) => {
    lines.push(String(line));
  };
  try {
    for (let i = 0; i < 31; i += 1) {
      service.accept(
        { level: 'warn', scope: 'tasks.list', message: '任务列表失败' },
        '10.0.0.8',
        1000 + i,
      );
    }
  } finally {
    console.warn = original;
  }
  assert.equal(lines.length, 30);
});
