const fs = require('node:fs')
const path = require('node:path')

// MinIO 8.0.x still imports the old stream-json path and CommonJS query-string
// expects decode-uri-component to return a function. Our audited overrides
// install newer ESM releases, so bridge those two imports after npm ci.
const queryStringPath = require.resolve('query-string')
const original = "const decodeComponent = require('decode-uri-component');"
const replacement = "const decodeComponent = require('decode-uri-component').default;"
const queryString = fs.readFileSync(queryStringPath, 'utf8')
if (queryString.includes(original)) {
  fs.writeFileSync(queryStringPath, queryString.replace(original, replacement))
} else if (!queryString.includes(replacement)) {
  throw new Error('query-string changed; review the MinIO compatibility patch')
}

const streamJsonRoot = path.dirname(require.resolve('stream-json'))
const parserPath = path.join(streamJsonRoot, 'jsonl', 'Parser.js')
const parserShim = [
  "import parser from './parser.js'",
  '',
  'export const make = (options) => parser.asStream(options)',
  '',
].join('\n')
if (!fs.existsSync(parserPath)) {
  fs.writeFileSync(parserPath, parserShim)
} else if (fs.readFileSync(parserPath, 'utf8') !== parserShim) {
  throw new Error('stream-json changed; review the MinIO compatibility patch')
}
