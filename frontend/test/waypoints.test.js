import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const waypointsSource = readFileSync(
  join(root, 'src/libs/route/waypoints.ts'),
  'utf8',
)
const routeViewSource = readFileSync(
  join(root, 'src/views/RouteView.vue'),
  'utf8',
)
const droneViewSource = readFileSync(
  join(root, 'src/views/DroneView.vue'),
  'utf8',
)
const replaySource = readFileSync(
  join(root, 'src/components/telemetry-replay/index.vue'),
  'utf8',
)
const inspectionApiSource = readFileSync(
  join(root, 'src/api/inspection-task.ts'),
  'utf8',
)

test('waypoints helper exposes parse/serialize for map editor sync', () => {
  assert.match(waypointsSource, /export function parseWaypointsJson/)
  assert.match(waypointsSource, /export function serializeWaypoints/)
  assert.match(waypointsSource, /latitude/)
  assert.match(waypointsSource, /longitude/)
})

test('route page wires map editor and thumbnail instead of textarea-only UX', () => {
  assert.match(routeViewSource, /RouteWaypointEditor/)
  assert.match(routeViewSource, /RouteThumbnail/)
  assert.match(routeViewSource, /waypointsJson/)
})

test('task page exposes route thumbnail, live map link, and replay panel', () => {
  assert.match(droneViewSource, /RouteThumbnail/)
  assert.match(droneViewSource, /TelemetryReplay/)
  assert.match(droneViewSource, /selectReplayTask/)
  assert.match(droneViewSource, /viewLiveMap|实时地图|tasks\.viewLiveMap/)
  assert.match(droneViewSource, /tasks\.replay/)
})

test('inspection API and replay component support task telemetry track', () => {
  assert.match(inspectionApiSource, /getTaskTelemetryTrack/)
  assert.match(inspectionApiSource, /\/telemetry/)
  assert.match(replaySource, /PolylineCollection/)
  assert.match(replaySource, /togglePlay/)
})
