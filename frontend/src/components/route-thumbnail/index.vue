<template>
  <div class="route-thumb" :title="title">
    <svg
      v-if="points.length >= 1"
      viewBox="0 0 120 72"
      preserveAspectRatio="xMidYMid meet"
      aria-hidden="true"
    >
      <polyline
        v-if="points.length >= 2"
        class="trail"
        fill="none"
        stroke-width="2.5"
        stroke-linecap="round"
        stroke-linejoin="round"
        :points="polylinePoints"
      />
      <circle
        v-for="(point, index) in points"
        :key="index"
        :cx="point.x"
        :cy="point.y"
        :r="index === 0 || index === points.length - 1 ? 3.2 : 2.2"
        class="dot"
        :class="{ start: index === 0, end: index === points.length - 1 }"
      />
    </svg>
    <span v-else class="empty">{{ emptyLabel }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { parseWaypointsJson } from '@/libs/route/waypoints'

const props = withDefaults(
  defineProps<{
    waypointsJson?: string | null
    title?: string
    emptyLabel?: string
  }>(),
  {
    waypointsJson: null,
    title: '',
    emptyLabel: '—',
  },
)

const points = computed(() => {
  const waypoints = parseWaypointsJson(props.waypointsJson)
  if (!waypoints.length) {
    return [] as Array<{ x: number; y: number }>
  }
  const lats = waypoints.map((point) => point.lat)
  const lngs = waypoints.map((point) => point.lng)
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  const minLng = Math.min(...lngs)
  const maxLng = Math.max(...lngs)
  const pad = 10
  const width = 120 - pad * 2
  const height = 72 - pad * 2
  const spanLng = Math.max(maxLng - minLng, 1e-6)
  const spanLat = Math.max(maxLat - minLat, 1e-6)
  // Keep roughly square geographic aspect in the thumbnail box.
  const scale = Math.min(width / spanLng, height / spanLat)
  const usedW = spanLng * scale
  const usedH = spanLat * scale
  const offsetX = pad + (width - usedW) / 2
  const offsetY = pad + (height - usedH) / 2
  return waypoints.map((point) => ({
    x: offsetX + (point.lng - minLng) * scale,
    y: offsetY + (maxLat - point.lat) * scale,
  }))
})

const polylinePoints = computed(() =>
  points.value.map((point) => `${point.x},${point.y}`).join(' '),
)
</script>

<style scoped>
.route-thumb {
  width: 120px;
  height: 72px;
  border-radius: 8px;
  border: 1px solid var(--st-border);
  background:
    linear-gradient(
      160deg,
      color-mix(in srgb, var(--st-color-primary) 10%, transparent),
      color-mix(in srgb, var(--st-bg-elevated) 80%, #0b1220)
    );
  display: grid;
  place-items: center;
  overflow: hidden;
}

.route-thumb svg {
  width: 100%;
  height: 100%;
}

.trail {
  stroke: color-mix(in srgb, var(--st-color-primary) 80%, #67e8f9);
}

.dot {
  fill: #e2e8f0;
}

.dot.start {
  fill: #4ade80;
}

.dot.end {
  fill: #f97316;
}

.empty {
  font-size: 12px;
  color: var(--st-text-muted);
}
</style>
