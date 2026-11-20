# 签名证书

`release.jks.b64` 是 base64 编码的 JKS 密钥库（RSA 4096，SHA384withRSA，有效期至 2056-11-12）。
CI 在构建前解码成 `release.jks` 参与签名，`release.jks` 本身被 `.gitignore` 排除。

- Keystore 密码 / Key 密码：`exifstudio2026`
- Alias：`exifstudio`

SHA-256 指纹：

```
CF:CF:5C:8D:AD:A0:3D:BD:5E:04:B3:5F:63:BB:68:6B:73:75:9D:11:FF:6D:1B:79:B2:32:7D:0D:B4:5C:D7:AD
```

## 为什么把私钥放进公开仓库

只为了一个目的：**保证每一次构建用同一把证书签名**，这样用户升级 App 时可以直接覆盖安装，不会被系统当成不同应用而要求先卸载。

它不承担任何身份认证作用。要上架应用商店或正式对外分发，请自行生成新证书，并把本目录的 b64 从仓库移除、改用 GitHub Secrets 注入。

## 换证书的后果

一旦已发布过用当前证书签名的 APK，换证书就意味着老用户必须卸载重装（数据会丢）。确定要换时，同时把 `applicationId` 改掉更干净。
