# 语环 X1 · R03 统一版影片

![影片封面](assets/poster.jpg)

35 秒产品演示片，1920×1080、30 fps、16:9。当前影片、实时预览、HTML 汇报与结构方案统一采用 **R03 CAD 同源模型**，产品名称统一为 **语环 X1**。

- 主体 **110.80 × 84.84 × 9.80 mm**，含按键最高 **10.15 mm**。
- 模型包含实际外露圆头榫接、铝塑分区、螺丝、按键、接口和声孔。
- 手机镜头使用当前 Android Demo 的真实模拟器截图。截图中的短音频为测试数据。
- 手机 AI 整理、待办与提醒仍为功能规划；24h 为循环录音保留窗口，续航待实测。

当前为结构渲染和软件 Demo，尚未完成实板上电、样壳装配或真实无线链路验证。首件全部用树脂打印，画面中的铝质表现用于说明后续材质意向。

## 播放

优先播放 [R03 统一版 MP4](../../outputs/语环X1-R03统一版.mp4)。本地播放器：

```sh
python3 -m http.server 8765 --bind 127.0.0.1
```

浏览器访问 `http://127.0.0.1:8765/` 播放成片；加 `?live=1` 查看 Three.js 实时预览。空格暂停或继续，左右方向键跳段，F 全屏。图片、模型、配乐和依赖均在本地。

ZIP 解压后也可直接打开 `cinema.html` 播放成片。实时预览和重渲染需要通过上面的本地服务加载模块。

## 分镜

| 时间 | 画面与信息 |
|---|---|
| 00–05 | 灵感、对话与约定，没按录音就错过 |
| 05–08 | 回溯保存与 R03 机身亮相 |
| 08–10.5 | 24h 循环录音保留窗口 |
| 10.5–17 | 双按键微距；按一次保存前 1 小时，连按 N 次保存前 N 小时 |
| 17–21 | 保存之后仍需重听的使用场景 |
| 21–27 | 当前 Android Demo 界面；AI 整理与提醒的后续规划 |
| 27–29 | 9.80 mm 主体侧面，含按键最高 10.15 mm |
| 29–31 | 静时一麦、人声四麦工作模式示意 |
| 31–35 | 语环 X1 与 R03 机身收尾 |

## CAD 同源关系

`assets/r03-assembly.json` 是当前 R03 预览使用的同一套 CAD 三角网格，包含壳体、按键和内部装配参考。来源为 `enclosure/r03/source/build_print_r03.py`，未对几何进行减薄或美化重画。

影片统一坐标：`x=(CAD x−49.9999)/10`、`y=(CAD y−37.0205)/10`、`z=(CAD z−4.9)/10`，1 单位为 10 mm。两按键中心距 8.89 mm。实际开孔和接头直接来自网格。

`verification/r03-scene-check.json` 验证了全部网格坐标、主体尺寸、两键间距、连续相机朝向与打包贴图。`r03-media-check.json` 与 `r03-player-check.json` 记录当前成片和播放入口检查。旧的 PCB 对齐和连续运镜报告保留作历史依据，当前外观以 R03 检查为准。

## 模型与渲染

- `assets/Yuhuan-X1-R03-CG.blend`：当前成片场景，包含 1050 帧连续运镜、真实 R03 网格、灯光与已打包的 App 截图。
- `product.js`：Three.js 预览，加载同一份 `r03-assembly.json` 与 Android 截图。
- `motion.js`：覆盖整部影片的连续运动轨迹；字幕切换不会重置模型或相机。
- `film.js`：字幕、波形及画面合成；`window.film.render(seconds)` 可定位任意帧。
- `tools/render_r03_frames.py`：逐帧渲染 Blender 场景。
- `tools/render.mjs`：隔离浏览器合成字幕、配乐与 H.264 MP4。
- `tools/check_scene.py`：CAD 网格及相机检查。

成片使用 Blender EEVEE Next、32 采样，保留原有连续运镜。模型文件不依赖外部字体。手机截图是当前 Demo，不显示虚构的已完成 AI 结果。

从本目录重渲染：

```sh
blender -b assets/Yuhuan-X1-R03-CG.blend --python tools/render_r03_frames.py -- /absolute/path/cg
node tools/render.mjs --chrome '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' --cg-dir /absolute/path/cg --cg-start 1 --cg-fps 30 --fps 30 --out ../../outputs/语环X1-R03统一版.mp4
blender -b assets/Yuhuan-X1-R03-CG.blend --python tools/check_scene.py
```

Node 侧需要 Playwright，导出需要 FFmpeg。可用 `X1_NODE_MODULES` 指向统一依赖目录，`FFMPEG_PATH` 指定 FFmpeg。普通播放无需安装 npm 包。渲染帧保存在临时目录，不进入源码仓库。

## 更新模型

重新从 R03 构建脚本输出 CAD 预览网格后，替换 `assets/r03-assembly.json`，再运行 `blender -b assets/Voloop-X1-CG.blend --python tools/update_r03_scene.py --`。脚本自动定位本工程目录，复用历史场景的运镜和灯光，替换模型与手机贴图。历史 `assets/Voloop-X1-CG.blend` 保留作运镜模板，当前播放与交付不使用其中的旧概念壳。

## 历史与许可

旧版 Voloop / 6 mm 概念影片保留在 [历史下载](../../outputs/README.md#产品影片)，不代表当前制造结构。当前参数以 R03 为准，已实现功能以固件和 Android 文档为准。

音乐与按键音由项目代码合成，没有外部音乐采样；`vendor/` 保留 Three.js MIT 许可。未使用其他品牌标识、广告片或音乐。
