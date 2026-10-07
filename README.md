HuaweiSwitcher
==============

HuaweiSwitcher 是一个开源的安卓应用，用来**直连华为 / 荣耀手表与手环**，无需安装臃肿的华为运动健康。

- 平台：Android（minSdk 24）
- 包名：`com.huaweiswitcher`
- 界面：Jetpack Compose + Material 3 Expressive
- 许可：AGPL-3.0（见文末）

## 功能

- **连接华为 / 荣耀手表、手环**（Band 系列、Watch 数字系列、GT 系列等）。
- 读取设备信息：型号、固件、蓝牙地址、电量。
- 活动数据同步：步数、睡眠、心率等（进入"已初始化"后自动同步一次，也可手动同步）。
- 通知同步、音乐控制、天气、查找设备、解绑设备等。
- **华为账号 ID 助手**：华为手表绑定华为账号后，第三方应用直接连接会被要求恢复出厂。本应用可在**首次引导 / 设置页**内获取并保存华为账号 ID，配对时自动套用，从而免恢复出厂连接。

## 首次使用：设置华为账号 ID

华为手表在配对时会把**华为账号 ID（17 位数字）**写入手表。若手表此前用华为运动健康配对过，本应用需要同一个账号 ID 才能连接，否则会被要求恢复出厂。

获取方式（应用内任选其一）：

- **应用内登录华为云自动获取**：登录 `https://cloud.huawei.com/` 后，应用读取 `userId` cookie 得到账号 ID（推荐）。
- **手动获取**：
  - Root：`grep old_user_id /data/data/com.huawei.health/shared_prefs/login_data.xml`
  - 无 Root：`adb logcat | grep huid=`

在应用内（首启引导，或 设置 → 华为账号 ID）填入并保存后，配对手表时会自动套用。

## 下载 / 安装

预编译的 debug APK 由 GitHub Actions 构建：

1. 打开本仓库的 **Actions** 页面，进入最新一次成功的运行。
2. 在页面底部下载 `huaweiswitcher-debug` 工件（zip，解压得到 `app-mainline-debug.apk`）。

## 从源码构建

```bash
./gradlew assembleMainlineDebug
```

产物位于 `app/build/outputs/apk/mainline/debug/`。

**环境要求**：JDK 21、Android SDK（`compileSdk 37`，即 `platforms;android-37.0` 与 `build-tools;37.0.0`）。

自动化出包配置见 [.github/workflows/build-apk.yml](.github/workflows/build-apk.yml)。

## 已知限制

- **设备级深层设置**与**全局设置**目前仍是旧版界面（可在设备详情 / 设置页通过"全部设置"进入），尚未全部重做。
- 部分新固件（HarmonyOS 6.1+）无法安装第三方表盘。
- "连接成功但读不到数据"等情况属于协议层问题，需结合日志排查。

### 抓取日志

设置 →「经典界面」中导出日志；或使用 `adb logcat`。

## 许可与致谢

本项目以 **AGPL-3.0** 发布。它是一个 AGPL-3.0 开源穿戴设备协议项目的衍生作品：设备协议逆向与蓝牙通信层的全部工作由原项目社区完成，本仓库在其基础上重做了界面并补充了华为账号等易用性功能。在此致谢原项目及其贡献者。

第三方代码许可说明：

* 本项目采用 [AGPLv3](LICENSE)。
* `app/src/main/java/net/osmand/` 与 `app/src/main/aidl/net/osmand/` 取自 [OsmAnd](https://osmand.net/) 项目，GPLv3，版权归 OsmAnd BV。
* `app/src/main/java/org/bouncycastle` 取自 [Bouncy Castle](https://www.bouncycastle.org/java.html) 项目，MIT，版权归 The Legion of the Bouncy Castle Inc.
* `app/src/main/java/com/android/nQuant` 取自 [nQuant.android](https://github.com/mcychan/nQuant.android/)，Apache，版权归 Miller Cy Chan。
* `app/src/main/java/lineageos/` 取自 [LineageOS](https://lineageos.org/) 平台（原 CyanogenMod），Apache，版权归 The CyanogenMod Project 与 LineageOS 贡献者。
* `app/src/main/java/org/concentus` 取自 [Concentus](https://github.com/lostromb/concentus) 项目，BSD-3。
* `GBDaoGenerator/src/de/greenrobot` 取自 [greenDAO](https://codeberg.org/Freeyourgadget/greenDAO) 项目，GPLv3，版权归 Markus Junginger / greenrobot。
* `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/util/SearchPreferenceHighlighter.java` 取自 [SearchPreference](https://github.com/ByteHamster/SearchPreference)，MIT，版权归 ByteHamster。
