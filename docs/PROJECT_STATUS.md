# 项目状态

更新：2026-10-19（Asia/Shanghai）。

当前配套组合为独立麦克风与双按键 PCB、固件 Demo 0.1.0、Android Demo 0.1.0，以及 R03 树脂打印外壳。

| 模块 | 已有交付和验证 | 下一步 |
|---|---|---|
| 硬件 | EasyEDA 原生工程、改线说明与检查证据 | 实板上电、电源测量、逐麦声道确认 |
| 固件 | ESP-IDF 构建、主机存储 / 编解码 / 按键测试、同步协议测试；记录位于 `firmware/release/` | 烧录、真实 SD 与无线链路、断电恢复、长时间并发录音同步 |
| Android | APK、协议单元测试、Lint、模拟器界面检查；记录位于 `android/release/` | 实机 BLE 配对、Wi-Fi 下载、锁屏与后台兼容性 |
| R03 外壳 | STEP / STL、CAD 源码、实体与间隙检查、可剪按键拼版 | 测量成品电池、供应商审图、首件打印装配 |
| AI 与提醒 | 产品需求和界面占位 | 接入真实 ASR、信息提取、摘要与提醒排程 |
| 续航与声学 | 软件功耗策略、四麦切换方案 | 电池侧电流 / 续航实测、多路延迟增益测量及阵列算法 |

## 版本关系

- 固件与 App 对应当前四麦独立时钟 / 数据、双按键方案，协议版本见各模块文档。
- R03 为当前首件打印入口，主体 9.80 mm、按键最高处 10.15 mm。R02 的 7.60 mm 和 R01 留作历史设计记录。
- 当前影片与 HTML 汇报均已切换到 R03 CAD 同源模型与 9.80 mm 主体厚度，产品名称统一为语环 X1。AI 信息整理仍为后续规划；旧的 6 mm 概念影片留作历史。
- 24 小时是按采样时间计算的循环录音保留窗口。此前 2000 mAh 电池续航估算约 13–22 小时，尚无测量结果。
- 电池名义规格为 504060、3.7 V、2000 mAh、自带保护板；封边、保护板和引线后的最大外形尚未测量。

## 目录迁移

初次上传将项目放在 `outputs/yuhuan-x1-*`。本次整理将源码按用途移到仓库根目录，历史提交保留完整记录：

| 原目录 / 文件 | 当前目录 |
|---|---|
| `outputs/yuhuan-x1-firmware/` | [`firmware/`](../firmware/) |
| `outputs/yuhuan-x1-android/` | [`android/`](../android/) |
| `outputs/yuhuan-x1-enclosure-r01/` | [`enclosure/r01/`](../enclosure/r01/) |
| `outputs/yuhuan-x1-enclosure-r02/` | [`enclosure/r02/`](../enclosure/r02/) |
| `outputs/yuhuan-x1-enclosure-r03/` | [`enclosure/r03/`](../enclosure/r03/) |
| `outputs/yuhuan-x1-film/` | [`media/film/`](../media/film/) |
| PCB 原生文件 | [`hardware/`](../hardware/) |
| PCB 审查、改线说明与图片 | [`hardware/reviews/`](../hardware/reviews/) |

`outputs/` 保留可直接下载的交付包、影片、截图与校验文件。历史 ZIP 保留原文件内容和内部目录；它们不随源码目录调整重新打包。当前目录继续开发，历史 ZIP 用于复核当时交付。
