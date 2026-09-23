require('reflect-metadata');

const assert = require('node:assert/strict');
const { test } = require('node:test');
const { BadRequestException } = require('@nestjs/common');
const {
  requireOffsetEventTime,
  toJavaLocalDateTime,
} = require('../dist/common/java-local-date-time.js');

test('keeps an offset eventTime for schema v2', () => {
  assert.equal(
    requireOffsetEventTime('2026-08-24T02:00:00Z'),
    '2026-08-24T02:00:00Z',
  );
});

test('converts UTC Z to Shanghai wall clock', () => {
  assert.equal(
    toJavaLocalDateTime('2026-08-24T02:00:00Z'),
    '2026-08-24T10:00:00.000',
  );
});

test('converts +08:00 to the same Shanghai wall clock', () => {
  assert.equal(
    toJavaLocalDateTime('2026-08-24T10:00:00+08:00'),
    '2026-08-24T10:00:00.000',
  );
});

test('UTC evening becomes next Shanghai calendar day', () => {
  assert.equal(
    toJavaLocalDateTime('2026-08-24T16:30:00Z'),
    '2026-08-25T00:30:00.000',
  );
});

test('rejects naive local datetime without offset', () => {
  assert.throws(
    () => toJavaLocalDateTime('2026-08-24T10:00:00'),
    BadRequestException,
  );
});
