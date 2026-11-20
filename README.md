# 影迹 · ExifStudio

> 把照片的「出生证明」拆开重写的 Android 应用：机型、镜头、光圈快门、ISO、白平衡、拍摄时间、GPS 位置，全部可编辑，全部在本机完成。

<p align="left">
  <img alt="platform" src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square" />
  <img alt="kotlin" src="https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?style=flat-square" />
  <img alt="compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?style=flat-square" />
  <img alt="license" src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" />
</p>

---

## 功能

### 1. EXIF 全字段编辑

按「设备 → 镜头」两级选择，一次性把一整套**自洽**的参数写进照片：

| 分组 | 字段 |
| --- | --- |
| 设备 | `Make`、`Model`、`Software` |
| 镜头 | `LensModel`、`FocalLength`、`FocalLengthIn35mmFilm` |
| 曝光 | `FNumber`、`ExposureTime`、`PhotographicSensitivity`(ISO)、`WhiteBalance`、`LightSource` |
| 时间 | `DateTimeOriginal`、`DateTime`、`DateTimeDigitized` |
| 位置 | `GPSLatitude/Longitude/Altitude`、`GPSProcessingMethod`、`GPSAreaInformation` |

关键点：**物理焦距与等效焦距是联动计算的**。选 iPhone 17 Pro 主摄（等效 24mm），写入的 `FocalLength` 是 6.77mm 而不是 24mm —— 两者对不上，一眼就能看出是改过的。

### 2. 机型参数库（51 款 / 160+ 镜头）

覆盖 **Apple / vivo / OPPO / iQOO / 小米 / 红米** 近两年主流机型，每台机器都含**后置多摄 + 前置**：

- iPhone 16 / 16 Plus / 16 Pro / 16 Pro Max / 16e / 17 / 17 Pro / 17 Pro Max / iPhone Air
- vivo X200 / X200 Pro / X200 Pro mini / X200 Ultra / X300 / X300 Pro / S20 / S30 / Y300 Pro
- OPPO Find X8 / X8 Pro / X8 Ultra / X9 / X9 Pro / Reno13 / Reno14 / A5 Pro / K13 Turbo
- iQOO 13 / 15 / Neo10 / Neo10 Pro / Neo11 / Z9 Turbo+ / Z10 Turbo+
- Xiaomi 15 / 15 Pro / 15 Ultra / 17 / 17 Pro / 17 Pro Max / Civi 5 Pro / MIX Flip 2
- Redmi K80 / K80 Pro / K90 / K90 Pro Max / Turbo 4 / Turbo 4 Pro / Note 14 Pro / Note 15 Pro / 15C

每个镜头都带：等效焦距、光圈、传感器型号、推荐 ISO、白平衡、典型快门。所有参数可在界面里手动改写 —— 写的是「参考答案」，不是死数据。

数据是纯 JSON（`app/src/main/assets/devices.json`），加机型只要按 schema 追加一行。

### 3. 地图选点

- 全屏地图，**长按任意位置落下标记**，自动反查地址
- 顶部搜索框，**命中后地图自动跳到该地点**
- 底部面板显示 `WGS-84` 经纬度，也可手动输入坐标
- 三种瓦片源可切换：**高德**（免密钥、中文标注最全）/ **OSM** / **自建 XYZ 瓦片服务**（支持离线部署）

坐标系是最容易翻车的地方：EXIF 的 GPS 必须是国际标准 **WGS-84**，而高德瓦片是 **GCJ-02** 火星坐标。App 内部做了双向转换（迭代反解，误差 < 1 m），所以「地图上看着对」和「EXIF 里写着对」是同一件事。

### 4. 一键清除 EXIF

重编码一份干净副本，抹掉包括 GPS 在内的全部元数据 —— 发图前除掉位置隐私用。

### 5. 其它

- 相册「分享 → 影迹」可直接进入编辑
- 原图永不修改，结果另存到 `Pictures/影迹`
- 照片不出本机，只有地图瓦片与地名检索需要联网

---

## 界面

暗夜液态玻璃：深空黑渐变底 + 流动光斑 + 半透明玻璃面板（内高光 / 外细边 / 大而柔的投影），文案克制，EXIF 数值一律等宽字体。

---

## 构建

```bash
# 需要 JDK 17 + Android SDK 34
echo "sdk.dir=/你的/Android/Sdk" > local.properties
gradle :app:assembleRelease
```

推送 `main` 分支或打 `v*` 标签，GitHub Actions 会自动出包；打标签时会发布到 Releases。

> 国内网络拉不到 Google 仓库时，`settings.gradle.kts` 已优先配置阿里云镜像，境外环境会自动回落到 `google()` / `mavenCentral()`。

---

## 签名与升级（重要）

仓库里的 `keystore/release.jks.b64` 是**固定的发布证书**（RSA 4096，有效期到 2056 年），CI 构建前会解码成 `release.jks` 参与签名。

因此：**后续版本可以直接覆盖安装，不需要卸载重装。**

> ⚠️ 公开仓库意味着这把私钥是公开的。它只用于保证签名一致，不代表任何身份认证。若要上架应用商店，请换成自己的私钥，并改用 GitHub Secrets 注入。

SHA-256 指纹：

```
CF:CF:5C:8D:AD:A0:3D:BD:5E:04:B3:5F:63:BB:68:6B:73:75:9D:11:FF:6D:1B:79:B2:32:7D:0D:B4:5C:D7:AD
```

---

## 项目结构

```
app/src/main/java/com/lucifer/exifstudio/
├── MainActivity.kt           # 入口 + 极简导航（Home / Editor / Map）
├── data/
│   ├── Models.kt             # Camera / Device / Brand / ExifProfile
│   ├── DeviceRepository.kt   # 机型库解析 + 按 Model 反查机型
│   ├── ExifEngine.kt         # EXIF 读写引擎（字节级复制，不重编码）
│   ├── Geo.kt                # WGS84/GCJ02 转换、Nominatim 检索、瓦片源
│   └── PhotoStore.kt         # MediaStore 落盘
├── ui/
│   ├── Theme.kt              # 设计 Token
│   ├── Components.kt         # 玻璃面板 / 按钮 / 行式键值对
│   ├── HomeScreen.kt
│   ├── EditorScreen.kt
│   ├── Sheets.kt             # 机型 / 镜头抽屉
│   └── MapScreen.kt          # osmdroid 地图选点
└── vm/StudioViewModel.kt     # 状态与业务
```

## 技术栈

Kotlin · Jetpack Compose (Material3) · Coil · osmdroid · androidx.exifinterface · 不依赖任何需要 API Key 的地图 SDK

## 已知限制

- HEIC / ProRAW 的 EXIF 原地写入受系统限制，建议用 JPEG
- 机型参数来自公开规格整理，个别地区版本可能有差异，界面内可直接改
- 地名检索默认走 OSM Nominatim（免密钥），国内偶有延迟，可在代码里替换为自建 Photon

## License

MIT
