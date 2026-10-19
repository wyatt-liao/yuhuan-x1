# 下载与交付文件

源码已按用途整理到仓库根目录。这里保留可直接下载的交付包和历史快照，继续开发请从[工程导航](../README.md#工程导航)进入。

## 当前下载

| 用途 | 文件 | 使用说明 |
|---|---|---|
| Android Demo 0.1.0 | [APK](语环X1-Android-Demo-0.1.0.apk) | [安装与使用](语环X1-安卓Demo使用说明.md)，开发签名 |
| Android 原始交付工程 | [源码 ZIP](语环X1-安卓工程-0.1.0.zip) | 当前源码见 [android/](../android/README.md) |
| 固件 Demo 0.1.0 | [固件 ZIP](语环X1-固件Demo-0.1.0.zip) | 当前源码与烧录步骤见 [firmware/](../firmware/README.md) |
| R03 树脂打印样壳 | [完整 ZIP](语环X1-嘉立创打印优化与按键拼版-R03.zip) · [SHA-256](语环X1-嘉立创打印优化与按键拼版-R03.zip.sha256) | [打印说明](../enclosure/r03/README-打印与拼版说明.md) |
| 最新 CG 产品影片 | [运镜修正版 MP4](Voloop-X1-CG版-运镜修正.mp4) | [影片说明](../media/film/README.md) |
| 最新 CG 原始交付工程 | [源码 ZIP](Voloop-X1-CG动画源码-运镜修正.zip) | 当前源码见 [media/film/](../media/film/) |

## 外壳打印包

- **R03：** [树脂打印优化与按键拼版 ZIP](语环X1-嘉立创打印优化与按键拼版-R03.zip)，主体 9.80 mm，首件优先使用。
- **仅取 R03 按键连桥：** [STL](../enclosure/r03/stl_optional/x1_buttons_on_sprue_CUT_AFTER_PRINT.stl)、[STEP](../enclosure/r03/step_optional/x1_buttons_on_sprue_CUT_AFTER_PRINT.step)。与单独按键二选一，打印后剪除连接条。
- **R02 历史薄版：** [铝塑融合样壳 ZIP](语环X1-铝塑融合打印样壳-R02.zip) · [SHA-256](语环X1-铝塑融合打印样壳-R02.zip.sha256)，主体 7.60 mm。
- **版本比较和源码：** [enclosure/](../enclosure/README.md)。装配参考文件与概念模型不作为整机一体打印文件。

## 硬件与证据

| 文件 | 说明 |
|---|---|
| [EasyEDA Pro 原生工程](../hardware/语环X1-独立麦克风与双按键.epro2) | 当前四麦与双按键设计 |
| [改线说明](../hardware/reviews/语环X1-本次改线说明.md) | 变更内容与检查边界 |
| [改线后 PCB 图](../hardware/reviews/语环X1-改线后PCB.png) | 当前设计预览 |
| [本次改线证据 ZIP](语环X1-本次改线证据.zip) | 改线时的检查快照 |
| [初始 PCB 检查证据 ZIP](PCB1-检查证据.zip) | 初始板子审查快照，结合[历史审查说明](../hardware/reviews/PCB1-投产前审查.md)阅读 |

## 产品影片

| 版本 | 视频 | 原始交付源码 |
|---|---|---|
| CG 运镜修正版，当前版本 | [MP4](Voloop-X1-CG版-运镜修正.mp4) | [ZIP](Voloop-X1-CG动画源码-运镜修正.zip) |
| CG 连续运镜历史版 | [MP4](Voloop-X1-连续运镜-CG版.mp4) | [ZIP](Voloop-X1-CG动画源码.zip) |
| Voloop 发布会快闪历史版 | [MP4](Voloop-X1-发布会快闪片.mp4) | [ZIP](Voloop-X1-影片源码.zip) |
| 语环 X1 早期概念片 | [MP4](语环X1-产品概念片.mp4) | [ZIP](语环X1-影片源码.zip) |

影片展示的 AI 和 6 mm 外观目标属于产品愿景，当前软件与结构状态见[项目状态](../docs/PROJECT_STATUS.md)。

## 校验与历史记录

R03、R02 压缩包旁有独立 SHA-256 文件；其他原交付校验记录保留在本目录的 `*SHA256*.txt`。模块内的 `SHA256SUMS.txt` 或 `manifest-sha256.txt` 用于核对各自交付内容。

早期快闪片的封面校验值与已提交 PNG 不一致，本次按当前文件更新；历史值保留在 Git 记录中。影片与封面文件内容未改动。

早期校验记录中迁走的 PCB 和说明文件路径已调整为当前相对路径，文件内容保持原交付；硬件初始审查中的图片链接另已改为可跨电脑使用的相对链接。ZIP 本身保持原始字节内容，内部目录和文档可能仍使用整理前的工程名称。

公开仓库入口与本次整理说明见 [GitHub-项目上传说明.md](GitHub-项目上传说明.md)。
