<template>
  <div class="h-screen map-shell">
    <overlay v-if="cesiumLoaded">
      <router-view></router-view>
    </overlay>
    <st-cesium-vue @loaded="loaded" :depthTestAgainstTerrain="true" />
    <st-tool-header />
  </div>
</template>

<script setup lang="ts">
import overlay from '@/components/st-overlay/index.vue'
import stCesiumVue from '@/components/st-cesium-vue/index.vue'
import StToolHeader from '@/components/st-tool-header/index.vue'
import {
  onDeviceTelemetry,
  type DeviceTelemetryPayload,
} from '@/realtime/socket'
import type CesiumLibs from '@/libs/cesium/cesium-libs'
import { onBeforeUnmount, ref } from 'vue'
import type { Viewer } from 'cesium'

const cesiumLoaded = ref(false)
let unsubscribeTelemetry: (() => void) | null = null
let pe: CesiumLibs | null = null

const loaded = (payload?: { viewer?: Viewer }): void => {
  cesiumLoaded.value = true
  const viewer = payload?.viewer ?? (window as Window & { viewer?: Viewer }).viewer
  pe = (viewer as Viewer & { pe?: CesiumLibs })?.pe ?? null
  if (!pe) {
    return
  }

  unsubscribeTelemetry?.()
  unsubscribeTelemetry = onDeviceTelemetry((telemetry: DeviceTelemetryPayload) => {
    pe?.upsertDeviceTelemetry({
      deviceCode: telemetry.deviceCode,
      latitude: Number(telemetry.latitude),
      longitude: Number(telemetry.longitude),
      altitude: telemetry.altitude,
      heading: telemetry.heading,
    })
  })
}

onBeforeUnmount(() => {
  unsubscribeTelemetry?.()
  unsubscribeTelemetry = null
  pe?.clearAllDeviceTelemetry()
  pe = null
})
</script>

<style lang="scss" scoped>
.h-screen.map-shell {
  position: relative;
}
</style>
