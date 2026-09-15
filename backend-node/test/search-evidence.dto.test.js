require('reflect-metadata');

const assert = require('node:assert/strict');
const { test } = require('node:test');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { plainToInstance } = require('class-transformer');
const { validate } = require('class-validator');
const {
  SearchEvidenceDto,
} = require('../dist/evidence/dto/search-evidence.dto.js');

test('includeDeleted=false stays false', async () => {
  const request = plainToInstance(SearchEvidenceDto, {
    includeDeleted: 'false',
  });

  assert.equal(request.includeDeleted, false);
  assert.deepEqual(await validate(request), []);
});

test('includeDeleted=true stays true', async () => {
  const request = plainToInstance(SearchEvidenceDto, {
    includeDeleted: 'true',
  });

  assert.equal(request.includeDeleted, true);
  assert.deepEqual(await validate(request), []);
});

test('omitted includeDeleted stays undefined', async () => {
  const request = plainToInstance(SearchEvidenceDto, {});

  assert.equal(request.includeDeleted, undefined);
  assert.deepEqual(await validate(request), []);
});

test('includeDeleted=0 is rejected instead of becoming false', async () => {
  const request = plainToInstance(SearchEvidenceDto, {
    includeDeleted: '0',
  });
  const errors = await validate(request);

  assert.equal(request.includeDeleted, '0');
  assert.equal(errors.some((error) => error.property === 'includeDeleted'), true);
});

test('includeDeleted=yes is rejected instead of becoming true', async () => {
  const request = plainToInstance(SearchEvidenceDto, {
    includeDeleted: 'yes',
  });
  const errors = await validate(request);

  assert.equal(request.includeDeleted, 'yes');
  assert.equal(errors.some((error) => error.property === 'includeDeleted'), true);
});

test('repeated includeDeleted query values are rejected', async () => {
  const request = plainToInstance(SearchEvidenceDto, {
    includeDeleted: ['false', 'true'],
  });
  const errors = await validate(request);

  assert.deepEqual(request.includeDeleted, ['false', 'true']);
  assert.equal(errors.some((error) => error.property === 'includeDeleted'), true);
});

test('search DTO does not use Boolean() coercion', () => {
  const source = readFileSync(
    join(__dirname, '../src/evidence/dto/search-evidence.dto.ts'),
    'utf8',
  );
  assert.equal(source.includes('@Type(() => Boolean)'), false);
});
