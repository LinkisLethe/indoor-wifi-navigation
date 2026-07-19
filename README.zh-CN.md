# 室内 Wi-Fi 定位与导航

[![Android](https://img.shields.io/badge/Android-API%2024%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Validation](https://img.shields.io/github/actions/workflow/status/LinkisLethe/BNBU_IndoorNavigation/validate.yml?branch=master&style=flat-square&label=validation)](https://github.com/LinkisLethe/BNBU_IndoorNavigation/actions/workflows/validate.yml)
[![License](https://img.shields.io/badge/license-MIT-2ea44f?style=flat-square)](LICENSE)

[English](README.md)

这是一个面向多栋建筑室内环境的 Android 定位与导航原型。系统使用 Wi-Fi 指纹完成房间级定位，并结合行人航位推算（PDR）、图搜索、楼层切换和路径吸附实现连续导航。

## 核心功能

- 管理模式在每个参考点采集 6 轮 Wi-Fi 数据，过滤低于 `-85 dBm` 的弱信号，删除偏差最大的 RSSI 样本，并将指纹保存为 JSON。
- 用户模式连续扫描 4 轮，以 `K=3` 的加权近邻方法融合欧氏距离与余弦相似度，对指纹位置进行排序。
- `MapData` 保存三层楼中的房间、门、走廊和楼梯节点；`PathFinder` 使用带跨层惩罚的 Dijkstra 算法计算路线。
- `PdrManager` 使用 Android 计步器和旋转矢量传感器。导航模块按 `0.79 m` 固定步长完成地图尺度换算、路径吸附、Wi-Fi 漂移校正、楼梯触发和到达判断。
- 活动模块读取最新活动信息，并将可识别的场地编号映射到园区总览图。

仓库中的地图、图节点坐标和 Wi-Fi 指纹库只针对一个实地环境完成标定，可用于展示完整实现，但不能直接作为通用室内定位基准。

## 构建与运行

项目使用 Java 11 源码兼容级别、Android Gradle Plugin `8.13.1`、Gradle
`8.13`、`minSdk 24`、`compileSdk 36` 和 `targetSdk 36`。运行 Gradle 需要
JDK 17。

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

在 Android Studio 中打开仓库根目录，完成 Gradle 同步后部署到具备 Wi-Fi、计步器和旋转矢量传感器的实体 Android 设备。首次运行需授予定位和活动识别权限；连续 Wi-Fi 扫描可能还需要在开发者选项中关闭扫描限流。

主要代码位于 `app/src/main/java/com/example/fingerprintlocation/`。本地单元测试覆盖同层路线、跨层路线和无效节点处理。

## 局限与许可证

定位精度受设备、Wi-Fi 环境、指纹覆盖、地图标定和手机姿态影响。PDR 参数针对已审查的实地部署固定设置，活动解析也依赖来源网页当前的 HTML 结构。

项目使用 [MIT License](LICENSE)。GitHub 保留原上游历史和 fork 关系。
