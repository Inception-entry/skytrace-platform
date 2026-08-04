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
import { ref } from 'vue'

const cesiumLoaded = ref(false)
const loaded = (): void => {
  cesiumLoaded.value = true
}
</script>

<style lang="scss" scoped>
.h-screen.map-shell {
  position: relative;
}
</style>
