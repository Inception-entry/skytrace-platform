require('reflect-metadata');

const assert = require('node:assert/strict');
const { test } = require('node:test');
const { plainToInstance } = require('class-transformer');
const { validate } = require('class-validator');
const {
  EvidenceCleanupDto,
} = require('../dist/admin/dto/evidence-maintenance.dto.js');

test('cleanup dryRun defaults to true', async () => {
  const request = plainToInstance(EvidenceCleanupDto, {});

  assert.equal(request.dryRun, true);
  assert.deepEqual(await validate(request), []);
});

test('cleanup accepts an explicit false boolean', async () => {
  const request = plainToInstance(EvidenceCleanupDto, {
    dryRun: 'false',
    confirmation: 'PURGE_ARCHIVED_EVIDENCE',
  });

  assert.equal(request.dryRun, false);
  assert.deepEqual(await validate(request), []);
});

test('cleanup rejects a misspelled boolean instead of treating it as false', async () => {
  const request = plainToInstance(EvidenceCleanupDto, {
    dryRun: 'flase',
    confirmation: 'PURGE_ARCHIVED_EVIDENCE',
  });
  const errors = await validate(request);

  assert.equal(request.dryRun, 'flase');
  assert.equal(errors.some((error) => error.property === 'dryRun'), true);
});
