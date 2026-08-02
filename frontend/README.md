# SkyTrace（天巡智控）

基于 Vue 3 + Cesium + TypeScript 的无人机智能巡检业务端。

## 功能特性
- 根据系统是深色模式或浅色模式，来切换主题色（也可关闭此功能）
- 同时支持用户自主切换主题色

- 🌏 多种底图切换
  - 高德地图(含纠偏)
  - 腾讯地图(含纠偏)
  - 天地图
  - 影像图/矢量图切换
  
- 📏 测量工具
  - 距离测量
  - 面积测量 
  - 高程测量
  - 角度测量
  - 实时动态显示测量结果
  - 支持清除测量

- 🔍 地名地址搜索
  - 支持POI关键字搜索
  - 支持经纬度坐标定位
  - 支持地理编码和逆地理编码
  - 坐标系自动转换(WGS84/GCJ02)

- 🎯 场景控制
  - 全屏切换
  - 二三维场景切换
  - 场景状态保存与恢复
  - 相机视角保存与恢复
  - 键盘控制相机移动
  - 抗锯齿处理

- 🌐 地图优化
  - 支持高德/腾讯地图纠偏
  - 地图瓦片自动加载
  - 3DTiles模型加载
  - 性能优化

## 技术栈

- Vue 3.5.13
- Vite 6.3.5
- pinia 3.0.3
- sass 1.89.1
- Cesium 1.116.0
- typescript 5.8.3
- i18next 25.3.0

## 快速开始

### 环境要求

- Node.js >= 22.11.0
- npm >= 10.9.0


## 📁 项目结构

```
├── .eslintrc.js          # ESLint配置文件
├── .gitignore            # Git忽略文件配置
├── .vscode/              # VSCode配置目录
│   └── extensions.json
├── LICENSE               # 许可证文件
├── README.md             # 项目说明文档
├── index.html            # 入口HTML文件
├── package.json          # 项目依赖配置
├── public/               # 公共资源目录
│   └── vite.svg
├── src/                  # 源代码目录
│   ├── @types/           # TypeScript类型定义目录
│   ├── App.vue           # 根组件
│   ├── assets/           # 静态资源目录
│   │   └── icons/        # 图标资源
│   ├── components/       # 公共组件目录
│   │   └── st-cesium-vue/
│   ├── config/           # 配置文件目录
│   ├── main.ts           # 入口文件
│   ├── router/           # 路由配置
│   │   └── index.ts
│   ├── store/            # 状态管理
│   │   └── index.ts
│   ├── style/            # 样式文件目录
│   │   ├── common.scss   # 公共样式
│   │   └── variables.scss # 样式变量
│   ├── utils/            # 工具函数目录
│   ├── views/            # 页面组件目录
│   │   ├── DroneView.vue
│   │   └── Home.vue
│   └── vite-env.d.ts     # Vite环境声明文件
├── tsconfig.app.json     # TypeScript应用配置
├── tsconfig.json         # TypeScript主配置
├── tsconfig.node.json    # TypeScript Node配置
└── vite.config.ts        # Vite配置文件
```

## 🎮 操作说明

### 地图操作
- 左键拖动: 平移视角
- 右键拖动: 旋转视角
- 滚轮: 缩放视角

### 无人机控制
- 开始飞行: 点击开始按钮
- 暂停飞行: 点击暂停按钮
- 重置飞行: 点击重置按钮
- 调整参数: 通过参数面板实时调整

## 🔧 自定义配置

### Vite 配置
项目使用 Vite 作为构建工具，配置文件位于 `vite.config.js`。

### Cesium 配置
Cesium 相关配置位于 `src/utils/cesiumUtils.js`，包括:
- 地图初始化参数
- 场景配置
- 相机参数

## 📝 开发指南

### 添加新的地图功能
1. 在 `src/components/cesium` 下创建新组件
2. 在 `Map.vue` 中引入并使用
3. 在 `cesiumUtils.js` 中添加相关工具方法

## <img class="emoji" title=":octocat:" alt=":octocat:" src="https://github.githubassets.com/images/icons/emoji/octocat.png" height="38" width="38" align="absmiddle"> 代码提交规范

```
git <type>: <subject>
git commit -m “feat: 项目初始化”
```

### type 参考:

```
feat      ✨ 增加新功能（feature）
fix       🐛 Bug修复
docs      📖 文档书写改动（documentation）
style     💎 style修改，代码风格相关无影响运行结果的
refactor  📦 重构(既不增加新功能, 也不修改bug的代码改动)
perf      🚀 性能相关优化
test      🚨 测试相关
build     👷 影响构建系统或外部依赖的更改（例如：vite，webpack，broccoli，npm）
ci        🔖 持续集成的配置文件和脚本的变动（例如：Travis，Circle，BrowserStack，SauceLabs）
chore     🎫 依赖更新/脚手架配置修改等
revert    🔙 代码撤销修改
init      🎉 初始化提交
release   🔖 发布版本
wip       🚧 正在进行中, 且有可能出现不稳定运行的提交
config    🔧 修改配置文件
merge     🔀 合并分支
```

## 📄 许可证

[MIT](LICENSE)

## 🔗 相关链接

- [Vue 3 文档](https://v3.vuejs.org/)
- [Cesium 文档](https://cesium.com/docs/)
- [typescript](https://www.typescriptlang.org/)
- [Turf.js 文档](https://turfjs.fenxianglu.cn/)

## 📧 联系方式

如有问题或建议，欢迎提issue或PR。
_注意: 不要使用 1.81.0 - 1.82.1 版本的 cesium, 它包含一个已知的[bug](https://github.com/CesiumGS/cesium/issues/9590)._