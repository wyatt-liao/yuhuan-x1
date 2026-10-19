# 语环 X1 Android Demo 0.1.0 验证记录

本包对应用户的 Stitch 四页设计和独立四麦、双按键固件 `0.1.0 / independent-mic-20261018`，采用 Kotlin / Jetpack Compose。

## 已完成验证

| 项目 | 结果 | 记录 |
|---|---|---|
| APK 与测试 APK 编译 | 通过 | `reports/package-build.log` |
| 核心 JVM 测试 | 6 项通过，0 失败 | `reports/TEST-com.yuhuan.x1.ProtocolTest.xml` |
| Android Lint | 0 错误，24 警告 | `reports/lint-results-debug.txt` |
| 常规竖屏 | 1080×2340、字体 1.0，流程通过 | `reports/ui-normal.log` |
| 小屏大字体 | 984×2132、字体 2.0，流程通过 | `reports/ui-small-largefont.log` |
| 横屏 | 2132×984、字体 1.0，流程通过 | `reports/ui-landscape.log` |
| APK 签名 | 开发证书、v2 签名验证通过 | `reports/apk-signature.txt` |

UI 测试在 Android 16 / API 36 模拟器上执行，三组屏幕密度均为 420dpi。每个配置运行一次离线录音库、过滤、播放详情、AI 待接入状态、设备页及连接弹层流程。截图中的短测试音频由单独的 instrumentation 测试 APK 注入；交付给用户的主 APK 不预装假录音。

核心测试覆盖 BLE 请求号、CRC、偏移与错误帧、无符号 64 位 ID、重叠保护区间、暂停区间裁切、断点续传及落盘后 ACK。固件 ADPCM fixture 与独立 FFmpeg 解码结果比较通过；LAN 地址限制也有测试。

Lint 警告主要涉及固定依赖版本、API 属性提示、局域网 HTTP 配置、存储空间访问、KTX 和 Compose 参数样式建议。完整警告已保留，不等同于所有问题均被排除。

## 尚未验证与当前边界

- 尚无实物录音卡测试。BLE 配对、Wi-Fi 传输、录音控制及音频内容仍需与实板联调；模拟器测试只证明本报告所列流程。
- 尚未接入语音识别、LLM、AI 提醒或系统日历。相关页面明确显示尚未分析，不含虚构结果。
- APK 使用开发签名，是可安装的 Demo，不是应用商店生产发行包。最低 Android 8.0 / API 26；最低版本与各品牌手机尚未逐一实测。
- 此次未做长时间后台、真实断电、低存储容量、射频吞吐或续航测试。设备当前协议的 HTTP、设备身份和目录代际限制详见根目录 README。

## 文件一致性

`build.json` 记录最终 APK 的 SHA256 与版本。项目根目录 `SHA256SUMS.txt` 覆盖交付工程内全部文件（该清单自身除外）。工程 ZIP 已做 CRC 检查，且其中 APK 与单独交付 APK 的 SHA256 一致。
