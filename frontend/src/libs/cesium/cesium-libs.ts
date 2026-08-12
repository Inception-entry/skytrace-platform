import * as Cesium from 'cesium'
import droneModelUrl from '@/assets/model/CesiumDrone.glb?url'

const MAX_TRAIL_POINTS = 200
const ENTITY_ID_PREFIX = 'skytrace-device-'
const TRAIL_ID_PREFIX = 'skytrace-trail-'

export interface DeviceTelemetryPoint {
  deviceCode: string
  latitude: number
  longitude: number
  altitude?: number | null
  heading?: number | null
}

interface TrackState {
  positions: Cesium.Cartesian3[]
  entity: Cesium.Entity
  trail: Cesium.Entity
}

// cesium 工具类
class CesiumLibs {
  protected viewer: Cesium.Viewer
  private readonly tracks = new Map<string, TrackState>()

  constructor(viewer: Cesium.Viewer) {
    this.viewer = viewer
  }

  upsertDeviceTelemetry(point: DeviceTelemetryPoint): void {
    const code = point.deviceCode?.trim()
    if (!code || !Number.isFinite(point.latitude) || !Number.isFinite(point.longitude)) {
      return
    }

    const altitude = Number.isFinite(point.altitude as number)
      ? Number(point.altitude)
      : 120
    const position = Cesium.Cartesian3.fromDegrees(
      point.longitude,
      point.latitude,
      altitude,
    )
    const headingRad = Number.isFinite(point.heading as number)
      ? Cesium.Math.toRadians(Number(point.heading))
      : 0
    const orientation = Cesium.Transforms.headingPitchRollQuaternion(
      position,
      new Cesium.HeadingPitchRoll(headingRad, 0, 0),
    )

    let state = this.tracks.get(code)
    if (!state) {
      const positions: Cesium.Cartesian3[] = [position]
      const entity = this.viewer.entities.add({
        id: ENTITY_ID_PREFIX + code,
        name: code,
        position,
        orientation,
        model: {
          uri: droneModelUrl,
          minimumPixelSize: 48,
          maximumScale: 20000,
          scale: 1.5,
        },
        label: {
          text: code,
          font: '14px sans-serif',
          fillColor: Cesium.Color.WHITE,
          outlineColor: Cesium.Color.BLACK,
          outlineWidth: 2,
          style: Cesium.LabelStyle.FILL_AND_OUTLINE,
          verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
          pixelOffset: new Cesium.Cartesian2(0, -28),
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
        },
      })
      const trail = this.viewer.entities.add({
        id: TRAIL_ID_PREFIX + code,
        polyline: {
          positions: new Cesium.CallbackProperty(() => {
            const current = this.tracks.get(code)
            return current ? current.positions.slice() : []
          }, false),
          width: 3,
          material: Cesium.Color.CYAN.withAlpha(0.85),
          clampToGround: false,
        },
      })
      state = { positions, entity, trail }
      this.tracks.set(code, state)
      this.flyToIfFirst(position)
      return
    }

    state.positions.push(position)
    if (state.positions.length > MAX_TRAIL_POINTS) {
      state.positions.splice(0, state.positions.length - MAX_TRAIL_POINTS)
    }
    state.entity.position = new Cesium.ConstantPositionProperty(position)
    state.entity.orientation = new Cesium.ConstantProperty(orientation)
  }

  removeDeviceTelemetry(deviceCode: string): void {
    const code = deviceCode?.trim()
    if (!code) {
      return
    }
    const state = this.tracks.get(code)
    if (!state) {
      return
    }
    this.viewer.entities.remove(state.entity)
    this.viewer.entities.remove(state.trail)
    this.tracks.delete(code)
  }

  clearAllDeviceTelemetry(): void {
    const codes: string[] = []
    this.tracks.forEach((_state, code) => {
      codes.push(code)
    })
    for (const code of codes) {
      this.removeDeviceTelemetry(code)
    }
  }

  private flyToIfFirst(position: Cesium.Cartesian3): void {
    if (this.tracks.size !== 1) {
      return
    }
    this.viewer.camera.flyTo({
      destination: Cesium.Cartesian3.fromDegrees(
        Cesium.Cartographic.fromCartesian(position).longitude * Cesium.Math.DEGREES_PER_RADIAN,
        Cesium.Cartographic.fromCartesian(position).latitude * Cesium.Math.DEGREES_PER_RADIAN,
        2500,
      ),
      duration: 1.2,
    })
  }
}

export default CesiumLibs
