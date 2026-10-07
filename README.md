HuaweiSwitcher
==============

HuaweiSwitcher 是一个开源的安卓应用，用来**直连华为 / 荣耀手表与手环**，无需安装臃肿的华为运动健康。

它基于开源项目 [Gadgetbridge](https://gadgetbridge.org)（AGPL-3.0）的协议栈构建，在其之上提供全新的 **Material 3 Expressive** 界面，并针对华为设备的连接做了易用性改进。

- 平台：Android（minSdk 24）
- 包名：`com.huaweiswitcher`
- 许可：AGPL-3.0（见文末）

## 功能

- **连接华为 / 荣耀手表、手环**（Band 系列、Watch 数字系列、GT 系列等，协议来自 Gadgetbridge）。
- 读取设备信息：型号、固件、蓝牙地址、电量。
- 活动数据同步：步数、睡眠、心率等（进入"已初始化"后自动同步一次，也可手动同步）。
- 通知同步、音乐控制、天气等 Gadgetbridge 提供的设备功能。
- 查找设备、解绑设备。
- **华为账号 ID 助手**：华为手表绑定华为账号后，第三方应用直接连接会被要求恢复出厂。本应用可在**首次引导 / 设置页**内获取并保存华为账号 ID，配对时自动套用，从而免恢复出厂连接。

## 首次使用：设置华为账号 ID

华为手表在配对时会把**华为账号 ID（17 位数字）**写入手表。若手表此前用华为运动健康配对过，本应用需要同一个账号 ID 才能连接，否则会被要求恢复出厂。

获取方式（在应用内选择其一）：

- **应用内登录华为云自动获取**：登录 `https://cloud.huawei.com/` 后，应用读取 `userId` cookie 得到账号 ID。
- **手动获取**：参见官方说明 <https://gadgetbridge.org/basics/pairing/huawei-honor-pairing/>。
  - Root：`grep old_user_id /data/data/com.huawei.health/shared_prefs/login_data.xml`
  - 无 Root：`adb logcat | grep huid=`

在应用内（首启引导或 设置 → 华为账号 ID）填入并保存后，配对手表时会自动套用。

## 下载 / 安装

预编译的 debug APK 由 GitHub Actions 构建：

1. 打开本仓库的 **Actions** 页面，进入最新一次成功的运行。
2. 在页面底部下载 `huaweiswitcher-debug` 工件（zip，解压得到 `app-mainline-debug.apk`）。

> 也可用 Android Studio 直接构建（见下）。

## 从源码构建

命令行：

```bash
./gradlew assembleMainlineDebug
```

产物位于 `app/build/outputs/apk/mainline/debug/`。

**环境要求**：JDK 21、Android SDK（`compileSdk 37`，即 `platforms;android-37.0` 与 `build-tools;37.0.0`）。

本项目使用 GitHub Actions 自动出包，配置见 [.github/workflows/build-apk.yml](.github/workflows/build-apk.yml)。

## 已知限制

- 应用的**设备级深层设置**与**全局设置**目前仍复用 Gadgetbridge 原有的界面（可在设备详情 / 设置页通过"全部设置"进入）。
- 部分新固件（HarmonyOS 6.1+）无法安装第三方表盘。
- "连接成功但读不到数据"等情况属于协议层问题，需结合日志排查。

### 抓取日志

设置 →「经典界面」打开 Gadgetbridge 控制中心，在其中导出日志；或使用 `adb logcat`。

## 许可与致谢

HuaweiSwitcher 是 [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge)（AGPL-3.0）的衍生作品，**以 AGPL-3.0 发布**。华为 / 荣耀设备协议的全部逆向工作均由 Gadgetbridge 社区完成，在此致谢。

完整的第三方代码许可说明（仅在 AGPL 要求范围内保留）：

* HuaweiSwitcher / Gadgetbridge 采用 [AGPLv3](LICENSE)。
* `app/src/main/java/net/osmand/` 与 `app/src/main/aidl/net/osmand/` 取自 [OsmAnd](https://osmand.net/) 项目，GPLv3，版权归 OsmAnd BV。
* `app/src/main/java/org/bouncycastle` 取自 [Bouncy Castle](https://www.bouncycastle.org/java.html) 项目，MIT，版权归 The Legion of the Bouncy Castle Inc.
* `app/src/main/java/com/android/nQuant` 取自 [nQuant.android](https://github.com/mcychan/nQuant.android/)，Apache，版权归 Miller Cy Chan。
* `app/src/main/java/lineageos/` 取自 [LineageOS](https://lineageos.org/) 平台（原 CyanogenMod），Apache，版权归 The CyanogenMod Project 与 LineageOS 贡献者。
* `app/src/main/java/org/concentus` 取自 [Concentus](https://github.com/lostromb/concentus) 项目，BSD-3。
* `GBDaoGenerator/src/de/greenrobot` 取自 [greenDAO](https://codeberg.org/Freeyourgadget/greenDAO) 项目（Gadgetbridge 的 fork），GPLv3，版权归 Markus Junginger / greenrobot。
* `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/util/SearchPreferenceHighlighter.java` 取自 [SearchPreference](https://github.com/ByteHamster/SearchPreference)，MIT，版权归 ByteHamster。
