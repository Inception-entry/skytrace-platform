import { type App } from 'vue'

export type CesiumRef = import('@/@types/shims-cesium-ref').CesiumRef

export const CESIUM_REF_KEY = Symbol('cesiumRef')

declare module '@vue/runtime-core' {
  interface ComponentCustomProperties {
    $cesiumRef: CesiumRef
    cesiumRef: CesiumRef
  }
}

// cesium 挂载到 vue 实例上
export default {
  install: function (app: App<Element>): void {
    const cr: CesiumRef = {
      viewer: undefined,
      viewerContainer: undefined,
    }
    app.config.globalProperties.$cesiumRef = cr
    app.provide<CesiumRef>(CESIUM_REF_KEY, cr)
  },
}
