# 交接 · 影迹 ExifStudio

最后更新：2026-11-22 05:0x UTC · 由 build-38 之后的会话补录。上一版交接停在 build-24 / 1.3.0，均已过期。

## 当前发布状态

| 项 | 值 |
| --- | --- |
| 最新构建 | `build-38`（Release id 454198402，2026-11-22T04:22:35Z，github-actions[bot]） |
| 对应提交 | `545b9287437de6cbdc63b822cc889ffab4bb28f5` |
| versionCode / versionName | `8` / `1.3.3`（未 bump，build-38 与 build-36 同 versionCode） |
| 签名 | 固定证书，subject `C=CN…OU=LUCIFER,CN=ExifStudio`，sha256 `CF:CF:5C:8D:AD:A0:3D:BD:5E:04:B3:5F:63:BB:68:6B:73:75:9D:11:FF:6D:1B:79:B2:32:7D:0D:B4:5C:D7:AD`，notAfter 2056-11-12 |
| 产物 | `ExifStudio.apk`，3,207,336 B，SHA-256 `1a5c2d8c27499e446821f151e7bb30bf22a07ba7e848df5948bfe05e58bea4aa` |

build-36 与 build-38 的证书逐字节相同（签名段不同、证书相同），覆盖安装成立。

## 怎么提交（重要）

沙箱里 `git push` 会报 `could not read Username`，`gh` 也未登录，环境里没有可用的 token 变量。**commit 走 GitHub MCP 工具写文件**（`create_or_update_file` / `push_files`），写完后用 MCP 读回校验 blob sha，本地仓库再跟随远端。

- 远端：`https://github.com/shibry88-netizen/ExifStudio`（`shibry88-netizen`）
- 本地工作区：`/tmp/exif`（会话级，可能被回收）

## 流水线

`.github/workflows/android.yml`：`push` 到 `main` 或 `v*` tag 触发 → `assembleRelease` → `apksigner verify --print-certs` → 上传 artifact → 建 `build-<run_number>` Release。失败时把 `e:` / `error:` / `FAILURE:` 行写进 `ci-report.txt` 并 `[skip ci]` 回推。

注意：**推任何东西到 `main` 都会发一个新 Release**，纯文档提交也会。

## 已修：开屏 `nextContour` 编译错误

`app/src/main/java/com/lucifer/exifstudio/ui/SplashScreen.kt`

- `:29-30` 新增 `asAndroidPath` / `asComposePath`
- `:54` 改为 `android.graphics.PathMeasure(path.asAndroidPath(), false)`，段落写进 `android.graphics.Path` 后 `asComposePath()`
- `:68` `if (!pm.nextContour()) break` 保留

两处 `nextContour` / `getSegment` 都落在平台类，语义与原意图一致，路径常量、`InkStroke`、时序、布局一字未动。dex 核对：`nextContour` 1、`PathMeasure` 1。

## 素材库 `assets/devices.json` —— 之前的判断是错的

曾记录「仓库里是 31567 B 旧版，工作区 86529 B / 51 款没提交」。复核结果：**仓库、本地工作区、build-38 APK 内是同一个 blob**（git blob sha `9a8771281490ca86df959f3bbabb98159fec851e`，31567 B，337 行；APK 内文件 sha256 `705ca7c6…` 与本地一致）。86529 B 那份是另一种格式化版本的记忆，已不在盘上。

当前覆盖：51 款 / 186 镜头 —— apple 9、vivo 9、oppo 9、redmi 9、xiaomi 8、iqoo 7。

待确认：**没有 huawei / samsung / honor / oneplus / meizu**，是有意收缩还是漏了，需产品口径。

## 代号残留

`Text4` 不是残留占位名，是 `Theme.kt:44` 的合法 token（`Color(0x5CFFFFFF)`），全仓仅 `HomeScreen.kt:322` 一处引用。早先列的 `HomeScreen:170`、`Components:219`、`Sheets:146/250`、`EditorScreen:288` 现已全部没有 `Text4`。

## 待办

- **P0 视觉回归（唯一没验的）**：装上 build-38 看开屏 —— 王冠描边起点须在右下、辉光/主线粗细比 7:2.4（辉光 α = 0.16 × glow）、`padding(bottom = 72.dp)` 构图、字标 22 条子路径左→右书写。参考帧 `/tmp/splash_paths.png`、`/tmp/splash_frames.png`（会话级）。
- **P1** `splash` 状态升 `rememberSaveable`，或进 Editor 后不重放。
- **P1** `HomeScreen.kt:201` 预览卡渐变末 `Bg0 α0.85` 是否压字。
- **P1** 版本号：build-38 与 build-36 同为 1.3.3 / 8，下次实质改动一并 bump。
- **P2** 升 lifecycle 后恢复 `collectAsStateWithLifecycle()`。
- **P2** 真机实测：真实相机图写入、SOS 前后熵段搬运、`exiftool -validate -warning`（素材 `/tmp/ref.jpg`、`picture.zip`）。

## 构建 / 验签

```bash
gradle :app:assembleRelease --no-daemon --stacktrace
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
"$BT/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
curl -L -o exif.apk https://github.com/shibry88-netizen/ExifStudio/releases/download/build-38/ExifStudio.apk
```

工具链固定：AGP 8.5.2 / Kotlin 1.9.24 / kotlinCompilerExtensionVersion 1.5.14；namespace 与 applicationId 均为 `com.lucifer.exifstudio`；compileSdk 36 / minSdk 29 / targetSdk 36。
