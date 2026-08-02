<template>
  <div class="h-screen bg-green-200">
    <overlay v-if="cesiumLoaded">
      <router-view></router-view>
    </overlay>
    <st-cesium-vue @loaded="loaded" :depthTestAgainstTerrain="true" />
    <HelloWorld msg="Vite + Vue" />
    <st-tool-header />
    <st-menu-aside />
  </div>
</template>

<script setup lang="ts">
import overlay from '@/components/st-overlay/index.vue'
import stCesiumVue from '@/components/st-cesium-vue/index.vue'
import HelloWorld from '@/components/HelloWorld.vue'
import StToolHeader from '@/components/st-tool-header/index.vue'
import StMenuAside from '@/components/st-menu-aside/index.vue'
import { ref } from 'vue'

const cesiumLoaded = ref<boolean>(false)
const loaded = (): void => {
  cesiumLoaded.value = true
}
</script>
<style lang="scss" scoped>
.h-screen.bg-green-200 {
  position: relative;
  // width: 100%;
  // height: 100vh;
}
</style>
