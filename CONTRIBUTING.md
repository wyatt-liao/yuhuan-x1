# 参与修改

提交 Issue 时注明所用 PCB、固件、App 与外壳版本，并提供可重现的操作、预期行为和实际结果。日志请去除 Wi-Fi 密码、设备配对信息和个人录音内容。

## 开发入口

- 固件：从 `firmware/README.md` 开始，使用 ESP-IDF v5.5.5。纯逻辑修改可运行 `python tools/test_host.py` 和 `python -m unittest discover -s tests -p 'test_*.py' -v`；驱动修改另需 IDF 编译与实物验证。
- Android：从 `android/README.md` 开始，使用 JDK 17 和工程内 Gradle Wrapper。按修改范围运行单元测试、Lint 与界面检查。
- 结构：修改 `enclosure/` 下对应版本的参数和 CadQuery 源码，另行导出新版本。交付前检查实体有效性、干涉、间隙和 STL 闭合状态。
- 硬件：保留原生 EasyEDA 工程，记录原理图 / PCB 的连接、DRC 和实际修改；同步检查固件 GPIO 和外壳孔位。

说明验证结果时区分主机测试、模拟器、几何检查和实物测试。协议变更同时更新 `firmware/PROTOCOL.md` 与 `android/docs/DEVICE_PROTOCOL.md`。

## 文件约定

源码放入各模块目录，独立可下载交付包放入 `outputs/` 并更新其索引。历史快照以版本区分，避免覆盖。工具链、SDK、编译缓存、个人配置、签名私钥和录音数据不提交。

原创内容的使用授权尚未指定统一开源许可证，提交或复用内容前请先阅读 [NOTICE.md](NOTICE.md)。第三方许可证与署名应随文件保留。
