export interface RouteWaypoint {
  lat: number
  lng: number
  alt: number
}

const DEFAULT_ALT = 80

export function parseWaypointsJson(raw: string | null | undefined): RouteWaypoint[] {
  if (!raw || !raw.trim()) {
    return []
  }
  try {
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed
      .map((item) => normalizeWaypoint(item))
      .filter((item): item is RouteWaypoint => item !== null)
  } catch {
    return []
  }
}

export function serializeWaypoints(waypoints: RouteWaypoint[]): string {
  return JSON.stringify(
    waypoints.map((point) => ({
      lat: round(point.lat, 6),
      lng: round(point.lng, 6),
      alt: round(point.alt, 1),
    })),
  )
}

export function normalizeWaypoint(value: unknown): RouteWaypoint | null {
  if (!value || typeof value !== 'object') {
    return null
  }
  const record = value as Record<string, unknown>
  const lat = toNumber(record.lat ?? record.latitude)
  const lng = toNumber(record.lng ?? record.longitude ?? record.lon)
  if (lat === null || lng === null) {
    return null
  }
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
    return null
  }
  const alt = toNumber(record.alt ?? record.altitude) ?? DEFAULT_ALT
  return { lat, lng, alt }
}

function toNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function round(value: number, digits: number): number {
  const factor = 10 ** digits
  return Math.round(value * factor) / factor
}
