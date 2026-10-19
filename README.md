# 语环 X1 / Yuhuan X1

语环 X1 录音卡的硬件、固件、Android Demo、外壳 CAD 与产品演示资料。这个仓库保存当前工程及此前交付版本，主分支中的外壳建议先使用 **R03 SLA 打印优化版**。

![R03 打印与拼版预览](outputs/yuhuan-x1-enclosure-r03/preview/00-overview.png)

## 工程入口

| 内容 | 文件 / 目录 | 状态 |
|---|---|---|
| EasyEDA Pro PCB | [独立麦克风与双按键工程](outputs/语环X1-独立麦克风与双按键.epro2) | 原生工程，需实板验证 |
| 电子板审查与改线 | [投产前审查](outputs/PCB1-投产前审查.md)、[改线说明](outputs/语环X1-本次改线说明.md) | 保存检查证据与已知边界 |
| ESP32-S3 固件 | [yuhuan-x1-firmware](outputs/yuhuan-x1-firmware/README.md) | Demo 0.1.0，含编译产物与主机测试 |
| Android App | [yuhuan-x1-android](outputs/yuhuan-x1-android/README.md) | Demo 0.1.0，含 APK 和测试记录 |
| 推荐树脂打印样壳 | [R03 打印与拼版说明](outputs/yuhuan-x1-enclosure-r03/README-打印与拼版说明.md) | 9.80 mm 主体，含可剪按键拼版 |
| 薄版铝塑设计记录 | [R02 装配说明](outputs/yuhuan-x1-enclosure-r02/README-先读我.md) | 7.60 mm 主体，保留供对比 |
| 产品演示工程 | [yuhuan-x1-film](outputs/yuhuan-x1-film/README.md) | 影片、Blender 源文件与验证资料 |
| 已打包交付文件 | [outputs](outputs/) | APK、固件 ZIP、安卓源码 ZIP、外壳 ZIP、影片等 |

## 当前产品行为与边界

固件实现 24 小时环形保留、手动录音与双按键回溯标记、一麦检测人声后启用四路时钟，以及 BLE / 局域网同步。Android Demo 可连接设备、接收、播放与分享本地录音。**ASR、AI 摘要和提醒尚未接入。** 24 小时录音保留窗口不是已经实测的 24 小时电池续航。

PCB 未进行真实上电、录音与长时间压力验证；样壳未实际打印装配。器件高度、板厚及带保护板电池外形的部分尺寸仍为建模假设。构建、仿真和几何检查均不能代替实物测试，各工程 README 记录具体边界。

## 外壳版本

R03 对照用户提供的[嘉立创打印设计规范](https://www.jlc-3dp.cn/technicalColumnsDetails/27832.html)优化：主面与侧壁 2 mm、活动按键单边间隙 0.5 mm、静态胶缝 0.4 mm；两个小按钮另有 1.7 mm 可剪连接杆的可选拼版。普通 STL 每文件一个零件，拼版 STL 在 `stl_optional/`。M2 螺母口袋的局部残壁和小孔仍需厂家审图；该版本尚未获工厂认可或实物验证。

未来铝件使用相应 STEP 做 CNC 工艺评审，先用树脂验证装配。R01、R02 与历史 ZIP 保存原交付记录，不代表同时推荐投产。复制解压得到且内容完全相同的 `R02 2` 目录已排除重复上传。

## 构建与许可证

固件使用已验证的 ESP-IDF v5.5.5；安卓使用工程内 Gradle Wrapper；CAD 使用 CadQuery 2.5.2，可选 Blender 生成预览。各自 README 给出完整命令。

SDK、工具链、模拟器、Gradle / 编译缓存、本地连接状态和本机路径配置不入库。第三方库保留自己的许可证；本仓库未为用户原创设计额外授予开源许可证。
