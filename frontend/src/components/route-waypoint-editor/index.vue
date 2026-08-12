<template>
  <div class="waypoint-editor">
    <div class="toolbar">
      <p class="hint">{{ hint }}</p>
      <div class="actions">
        <button class="tool-btn" type="button" :disabled="!waypoints.length" @click="undoLast">
          {{ undoLabel }}
        </button>
        <button class="tool-btn" type="button" :disabled="!waypoints.length" @click="clearAll">
          {{ clearLabel }}
        </button>
      </div>
    </div>
    <div ref="mapEl" class="map-host" />
    <p class="meta">{{ countLabel }}</p>
  </div>
</template>

<script setup lang="ts">
import * as Cesium from 'cesium'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  parseWaypointsJson,
  serializeWaypoints,
  type RouteWaypoint,
} from '@/libs/route/waypoints'

const props = withDefaults(
  defineProps<{
    modelValue: string
    defaultAltitude?: number
    hint?: string
    undoLabel?: string
    clearLabel?: string
    countTemplate?: string
  }>(),
  {
    defaultAltitude: 80,
    hint: '左键点击地图添加航点，拖拽航点调整位置；右键删除航点。',
    undoLabel: '撤销末点',
    clearLabel: '清空航点',
    countTemplate: '共 {{count}} 个航点',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const mapEl = ref<HTMLElement | null>(null)
const waypoints = ref<RouteWaypoint[]>(parseWaypointsJson(props.modelValue))
const countLabel = computed(() =>
  props.countTemplate.replace('{{count}}', String(waypoints.value.length)),
)

interface WaypointPickId {
  kind: 'route-wp'
  index: number
}

let viewer: Cesium.Viewer | null = null
let pointCollection: Cesium.PointPrimitiveCollection | null = null
let labelCollection: Cesium.LabelCollection | null = null
let polylineCollection: Cesium.PolylineCollection | null = null
let routePolyline: Cesium.Polyline | null = null
let draggingIndex: number | null = null
let didDrag = false
let suppressCamera = false
let applyingExternal = false

const LINE_COLOR = Cesium.Color.fromCssColorString('#38bdf8').withAlpha(0.9)
const COLOR_START = Cesium.Color.fromCssColorString('#4ade80')
const COLOR_END = Cesium.Color.fromCssColorString('#fb923c')
const COLOR_MID = Cesium.Color.WHITE

onMounted(() => {
  if (!mapEl.value) {
    return
  }
  viewer = new Cesium.Viewer(mapEl.value, {
    animation: false,
    baseLayerPicker: false,
    fullscreenButton: false,
    geocoder: false,
    homeButton: false,
    infoBox: false,
    sceneModePicker: false,
    selectionIndicator: false,
    timeline: false,
    navigationHelpButton: false,
    scene3DOnly: true,
    shouldAnimate: false,
  })
  ;(viewer.cesiumWidget.creditContainer as HTMLElement).style.display = 'none'
  viewer.scene.globe.depthTestAgainstTerrain = false
  viewer.scene.screenSpaceCameraController.tiltEventTypes = [
    Cesium.CameraEventType.PINCH,
    Cesium.CameraEventType.RIGHT_DRAG,
  ]
  mapEl.value.addEventListener('contextmenu', (event) => event.preventDefault())

  pointCollection = viewer.scene.primitives.add(
    new Cesium.PointPrimitiveCollection(),
  )
  labelCollection = viewer.scene.primitives.add(new Cesium.LabelCollection())
  polylineCollection = viewer.scene.primitives.add(new Cesium.PolylineCollection())

  const handler = viewer.screenSpaceEventHandler
  handler.setInputAction(onLeftClick, Cesium.ScreenSpaceEventType.LEFT_CLICK)
  handler.setInputAction(onLeftDown, Cesium.ScreenSpaceEventType.LEFT_DOWN)
  handler.setInputAction(onLeftUp, Cesium.ScreenSpaceEventType.LEFT_UP)
  handler.setInputAction(onMouseMove, Cesium.ScreenSpaceEventType.MOUSE_MOVE)
  handler.setInputAction(onRightClick, Cesium.ScreenSpaceEventType.RIGHT_CLICK)

  rebuildPrimitives()
  flyToWaypoints()
})

onBeforeUnmount(() => {
  routePolyline = null
  pointCollection = null
  labelCollection = null
  polylineCollection = null
  if (viewer && !viewer.isDestroyed()) {
    viewer.destroy()
  }
  viewer = null
})

watch(
  () => props.modelValue,
  (next) => {
    if (serializeWaypoints(waypoints.value) === (next || '[]')
      || (waypoints.value.length === 0 && !next)) {
      return
    }
    applyingExternal = true
    waypoints.value = parseWaypointsJson(next)
    rebuildPrimitives()
    flyToWaypoints()
    applyingExternal = false
  },
)

function commit() {
  if (applyingExternal) {
    return
  }
  emit('update:modelValue', serializeWaypoints(waypoints.value))
}

function undoLast() {
  waypoints.value = waypoints.value.slice(0, -1)
  rebuildPrimitives()
  commit()
}

function clearAll() {
  waypoints.value = []
  rebuildPrimitives()
  commit()
}

function onLeftClick(event: { position: Cesium.Cartesian2 }) {
  if (!viewer || draggingIndex !== null) {
    return
  }
  if (didDrag) {
    didDrag = false
    return
  }
  if (pickWaypointIndex(event.position) >= 0) {
    return
  }
  const cartographic = pickCartographic(event.position)
  if (!cartographic) {
    return
  }
  waypoints.value = [
    ...waypoints.value,
    {
      lat: Cesium.Math.toDegrees(cartographic.latitude),
      lng: Cesium.Math.toDegrees(cartographic.longitude),
      alt: props.defaultAltitude,
    },
  ]
  rebuildPrimitives()
  commit()
}

function onRightClick(event: { position: Cesium.Cartesian2 }) {
  const index = pickWaypointIndex(event.position)
  if (index < 0) {
    return
  }
  waypoints.value = waypoints.value.filter((_, i) => i !== index)
  rebuildPrimitives()
  commit()
}

function onLeftDown(event: { position: Cesium.Cartesian2 }) {
  if (!viewer) {
    return
  }
  didDrag = false
  const index = pickWaypointIndex(event.position)
  if (index < 0) {
    return
  }
  draggingIndex = index
  suppressCamera = true
  viewer.scene.screenSpaceCameraController.enableRotate = false
  viewer.scene.screenSpaceCameraController.enableTranslate = false
}

function onLeftUp() {
  if (!viewer) {
    return
  }
  if (draggingIndex !== null) {
    draggingIndex = null
    commit()
  }
  if (suppressCamera) {
    suppressCamera = false
    viewer.scene.screenSpaceCameraController.enableRotate = true
    viewer.scene.screenSpaceCameraController.enableTranslate = true
  }
}

function onMouseMove(event: { endPosition: Cesium.Cartesian2 }) {
  if (!viewer || draggingIndex === null) {
    return
  }
  const cartographic = pickCartographic(event.endPosition)
  if (!cartographic) {
    return
  }
  const next = [...waypoints.value]
  const current = next[draggingIndex]
  next[draggingIndex] = {
    lat: Cesium.Math.toDegrees(cartographic.latitude),
    lng: Cesium.Math.toDegrees(cartographic.longitude),
    alt: current?.alt ?? props.defaultAltitude,
  }
  waypoints.value = next
  didDrag = true
  // 拖拽只改坐标，不重建 Primitive 集合
  updatePrimitivePositions()
}

function pickCartographic(position: Cesium.Cartesian2): Cesium.Cartographic | null {
  if (!viewer) {
    return null
  }
  const ray = viewer.camera.getPickRay(position)
  if (!ray) {
    return null
  }
  const cartesian = viewer.scene.globe.pick(ray, viewer.scene)
  if (!cartesian) {
    return null
  }
  return Cesium.Cartographic.fromCartesian(cartesian)
}

function pickWaypointIndex(position: Cesium.Cartesian2): number {
  if (!viewer) {
    return -1
  }
  const picked = viewer.scene.pick(position)
  if (!Cesium.defined(picked)) {
    return -1
  }
  const id = (picked as { id?: unknown }).id
  if (!id || typeof id !== 'object') {
    return -1
  }
  const marker = id as WaypointPickId
  if (marker.kind !== 'route-wp' || !Number.isInteger(marker.index)) {
    return -1
  }
  return marker.index
}

function toCartesian(point: RouteWaypoint): Cesium.Cartesian3 {
  return Cesium.Cartesian3.fromDegrees(point.lng, point.lat, point.alt)
}

function pointColor(index: number, total: number): Cesium.Color {
  if (index === 0) {
    return COLOR_START
  }
  if (index === total - 1) {
    return COLOR_END
  }
  return COLOR_MID
}

function rebuildPrimitives() {
  if (!pointCollection || !labelCollection || !polylineCollection) {
    return
  }

  pointCollection.removeAll()
  labelCollection.removeAll()
  polylineCollection.removeAll()
  routePolyline = null

  const list = waypoints.value
  const positions = list.map((point) => toCartesian(point))

  if (positions.length >= 2) {
    routePolyline = polylineCollection.add({
      positions,
      width: 3,
      material: Cesium.Material.fromType('Color', { color: LINE_COLOR }),
    })
  }

  for (let index = 0; index < list.length; index += 1) {
    const position = positions[index]
    const pickId: WaypointPickId = { kind: 'route-wp', index }
    pointCollection!.add({
      position,
      pixelSize: index === 0 || index === list.length - 1 ? 14 : 11,
      color: pointColor(index, list.length),
      outlineColor: Cesium.Color.BLACK,
      outlineWidth: 1,
      disableDepthTestDistance: Number.POSITIVE_INFINITY,
      id: pickId,
    })
    labelCollection!.add({
      position,
      text: String(index + 1),
      font: '12px sans-serif',
      fillColor: Cesium.Color.WHITE,
      outlineColor: Cesium.Color.BLACK,
      outlineWidth: 2,
      style: Cesium.LabelStyle.FILL_AND_OUTLINE,
      pixelOffset: new Cesium.Cartesian2(0, -18),
      disableDepthTestDistance: Number.POSITIVE_INFINITY,
      id: pickId,
    })
  }
}

function updatePrimitivePositions() {
  if (!pointCollection || !labelCollection) {
    return
  }
  const list = waypoints.value
  const positions = list.map((point) => toCartesian(point))

  for (let index = 0; index < list.length; index += 1) {
    const point = pointCollection.get(index)
    const label = labelCollection.get(index)
    const position = positions[index]
    if (point) {
      point.position = position
      point.color = pointColor(index, list.length)
      point.pixelSize = index === 0 || index === list.length - 1 ? 14 : 11
    }
    if (label) {
      label.position = position
    }
  }

  if (positions.length >= 2) {
    if (routePolyline) {
      routePolyline.positions = positions
    } else if (polylineCollection) {
      routePolyline = polylineCollection.add({
        positions,
        width: 3,
        material: Cesium.Material.fromType('Color', { color: LINE_COLOR }),
      })
    }
  } else if (routePolyline && polylineCollection) {
    polylineCollection.remove(routePolyline)
    routePolyline = null
  }
}

function flyToWaypoints() {
  if (!viewer) {
    return
  }
  if (!waypoints.value.length) {
    viewer.camera.setView({
      destination: Cesium.Cartesian3.fromDegrees(121.4737, 31.2304, 8000),
    })
    return
  }
  if (waypoints.value.length === 1) {
    const only = waypoints.value[0]
    viewer.camera.flyTo({
      destination: Cesium.Cartesian3.fromDegrees(only.lng, only.lat, 2500),
      duration: 0.6,
    })
    return
  }
  const rectangle = Cesium.Rectangle.fromDegrees(
    Math.min(...waypoints.value.map((point) => point.lng)) - 0.01,
    Math.min(...waypoints.value.map((point) => point.lat)) - 0.01,
    Math.max(...waypoints.value.map((point) => point.lng)) + 0.01,
    Math.max(...waypoints.value.map((point) => point.lat)) + 0.01,
  )
  viewer.camera.flyTo({
    destination: rectangle,
    duration: 0.6,
  })
}
</script>

<style scoped>
.waypoint-editor {
  display: grid;
  gap: 8px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.hint {
  margin: 0;
  color: var(--st-text-muted);
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 8px;
}

.tool-btn {
  border: 1px solid var(--st-border);
  border-radius: 8px;
  background: var(--st-bg-elevated);
  color: var(--st-text);
  padding: 6px 10px;
  cursor: pointer;
}

.tool-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.map-host {
  height: 360px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--st-border);
}

.meta {
  margin: 0;
  font-size: 12px;
  color: var(--st-text-muted);
}
</style>
