<template>
  <div class="telemetry-replay">
    <div v-if="loading" class="state-text">{{ loadingLabel }}</div>
    <div v-else-if="!points.length" class="state-text">{{ emptyLabel }}</div>
    <template v-else>
      <div ref="mapEl" class="map-host" />
      <div class="controls">
        <button class="tool-btn" type="button" @click="togglePlay">
          {{ playing ? pauseLabel : playLabel }}
        </button>
        <input
          class="scrubber"
          type="range"
          min="0"
          :max="points.length - 1"
          step="1"
          v-model.number="currentIndex"
          @input="onScrub"
        />
        <span class="frame-meta">{{ frameLabel }}</span>
      </div>
      <p class="meta">
        {{ formatTime(currentPoint?.recordedAt) }} ·
        {{ altitudeLabel }} {{ formatNumber(currentPoint?.altitude) }} m ·
        {{ headingLabel }} {{ formatNumber(currentPoint?.heading) }}°
      </p>
    </template>
  </div>
</template>

<script setup lang="ts">
import * as Cesium from 'cesium'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import droneModelUrl from '@/assets/model/CesiumDrone.glb?url'
import type { TaskTelemetryPoint } from '@/api/inspection-task'

const props = withDefaults(
  defineProps<{
    points: TaskTelemetryPoint[]
    loading?: boolean
    loadingLabel?: string
    emptyLabel?: string
    playLabel?: string
    pauseLabel?: string
    altitudeLabel?: string
    headingLabel?: string
    frameTemplate?: string
    playbackFps?: number
  }>(),
  {
    loading: false,
    loadingLabel: '加载轨迹中…',
    emptyLabel: '该任务暂无遥测轨迹',
    playLabel: '播放',
    pauseLabel: '暂停',
    altitudeLabel: '高度',
    headingLabel: '航向',
    frameTemplate: '{{current}} / {{total}}',
    playbackFps: 6,
  },
)

const mapEl = ref<HTMLElement | null>(null)
const currentIndex = ref(0)
const playing = ref(false)

let viewer: Cesium.Viewer | null = null
let polylineCollection: Cesium.PolylineCollection | null = null
let flownPolyline: Cesium.Polyline | null = null
let droneEntity: Cesium.Entity | null = null
let timer: ReturnType<typeof setInterval> | null = null

const LINE_FLOWN = Cesium.Color.fromCssColorString('#4ade80').withAlpha(0.9)
const LINE_REMAINING = Cesium.Color.fromCssColorString('#38bdf8').withAlpha(0.5)

const currentPoint = computed(() => props.points[currentIndex.value])
const frameLabel = computed(() =>
  props.frameTemplate
    .replace('{{current}}', String(props.points.length ? currentIndex.value + 1 : 0))
    .replace('{{total}}', String(props.points.length)),
)

function formatTime(value: string | undefined): string {
  if (!value) {
    return '—'
  }
  return new Date(value).toLocaleString()
}

function formatNumber(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : value.toFixed(1)
}

function ensureViewer() {
  if (viewer || !mapEl.value) {
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
  polylineCollection = viewer.scene.primitives.add(new Cesium.PolylineCollection())
  buildScene()
}

function positions(): Cesium.Cartesian3[] {
  return props.points.map((point) =>
    Cesium.Cartesian3.fromDegrees(
      point.longitude,
      point.latitude,
      point.altitude ?? 100,
    ),
  )
}

function buildScene() {
  if (!viewer || !polylineCollection || !props.points.length) {
    return
  }
  polylineCollection.removeAll()
  if (droneEntity) {
    viewer.entities.remove(droneEntity)
    droneEntity = null
  }

  const allPositions = positions()
  polylineCollection.add({
    positions: allPositions,
    width: 3,
    material: Cesium.Material.fromType('Color', { color: LINE_REMAINING }),
  })
  flownPolyline = polylineCollection.add({
    positions: [allPositions[0]],
    width: 4,
    material: Cesium.Material.fromType('Color', { color: LINE_FLOWN }),
  })

  droneEntity = viewer.entities.add({
    position: allPositions[0],
    model: {
      uri: droneModelUrl,
      minimumPixelSize: 48,
      maximumScale: 20000,
      scale: 1.5,
    },
  })

  const rectangle = Cesium.Rectangle.fromDegrees(
    Math.min(...props.points.map((point) => point.longitude)) - 0.01,
    Math.min(...props.points.map((point) => point.latitude)) - 0.01,
    Math.max(...props.points.map((point) => point.longitude)) + 0.01,
    Math.max(...props.points.map((point) => point.latitude)) + 0.01,
  )
  viewer.camera.flyTo({ destination: rectangle, duration: 0.6 })

  currentIndex.value = 0
  updateFrame()
}

function updateFrame() {
  if (!flownPolyline || !droneEntity || !props.points.length) {
    return
  }
  const allPositions = positions()
  const upTo = allPositions.slice(0, currentIndex.value + 1)
  flownPolyline.positions = upTo.length ? upTo : [allPositions[0]]

  const point = props.points[currentIndex.value]
  const position = Cesium.Cartesian3.fromDegrees(
    point.longitude,
    point.latitude,
    point.altitude ?? 100,
  )
  droneEntity.position = new Cesium.ConstantPositionProperty(position)

  const heading = point.heading ?? 0
  droneEntity.orientation = new Cesium.ConstantProperty(
    Cesium.Transforms.headingPitchRollQuaternion(
      position,
      new Cesium.HeadingPitchRoll(Cesium.Math.toRadians(heading), 0, 0),
    ),
  )
}

function onScrub() {
  stopPlayback()
  updateFrame()
}

function togglePlay() {
  if (playing.value) {
    stopPlayback()
    return
  }
  if (currentIndex.value >= props.points.length - 1) {
    currentIndex.value = 0
  }
  playing.value = true
  const intervalMs = Math.max(1000 / props.playbackFps, 50)
  timer = setInterval(() => {
    if (currentIndex.value >= props.points.length - 1) {
      stopPlayback()
      return
    }
    currentIndex.value += 1
    updateFrame()
  }, intervalMs)
}

function stopPlayback() {
  playing.value = false
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(
  () => props.points,
  () => {
    stopPlayback()
    if (mapEl.value) {
      ensureViewer()
      buildScene()
    }
  },
)

watch(mapEl, () => {
  ensureViewer()
})

onBeforeUnmount(() => {
  stopPlayback()
  if (viewer && !viewer.isDestroyed()) {
    viewer.destroy()
  }
  viewer = null
})
</script>

<style scoped>
.telemetry-replay {
  display: grid;
  gap: 8px;
}

.state-text {
  padding: 24px;
  text-align: center;
  color: var(--st-text-muted);
}

.map-host {
  height: 360px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--st-border);
}

.controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tool-btn {
  border: 1px solid var(--st-border);
  border-radius: 8px;
  background: var(--st-bg-elevated);
  color: var(--st-text);
  padding: 6px 12px;
  cursor: pointer;
}

.scrubber {
  flex: 1;
}

.frame-meta {
  font-size: 12px;
  color: var(--st-text-muted);
  white-space: nowrap;
}

.meta {
  margin: 0;
  font-size: 12px;
  color: var(--st-text-muted);
}
</style>
