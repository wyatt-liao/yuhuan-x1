# 语环 X1 固件 Demo 0.1.0

本工程对应 **2026-10-18 已修改的独立麦克风、双功能按键 PCB**，主控为 ESP32-S3-WROOM-1-N16R8。固件已完成 ESP-IDF v5.5.5 编译和主机测试；尚未在实板烧录、上电或录音验证。不要把编译通过理解为硬件验证通过。

## 现在实现的行为

上电并成功挂载 SD 卡后持续录音，分为一分钟音频片段，按录音样本时间保留最近 24 小时。16kHz、单声道、IMA ADPCM WAV，连续 24 小时约 701MB，不包含文件系统和标记开销。采用已经格式化的 FAT32 SD 卡；挂载失败不会自动格式化。

| 状态 | 按键 1：SW1 / GPIO0 | 按键 2：SW2 / GPIO15 |
|---|---|---|
| 没有手动录音 | 开始手动录音并保护后续内容 | 连按 N 次，保存前 N 小时 |
| 手动录音中 | 结束手动录音 | 暂停手动录音 |
| 手动录音暂停 | 结束本次手动录音 | 恢复手动录音 |

按键去抖 30ms，在释放时识别一次点击。第二键连续点击间隔小于 500ms，停止点击 500ms 后执行，最多 24 小时；例如连续三下保存前三小时。若设备尚未录满相应时长，只保护已有录音。按键 1 会取消尚未提交的回溯点击计数。

后台循环录音始终继续；“暂停”表示该段时间不归入本次手动录音，它仍存在于后台环中。保存通过持久化保护标记实现，不在设备上复制数小时音频。手动录音暂停前后的标记使用同一 group，手机可据此拼接。保护内容不会被循环删除，ACK 也不会删除音频。卡被保护内容占满时停止写入并报错；需要显式解除保护、清理或换卡后重启，不会覆盖已保存内容。

## 麦克风与省电

四麦供电仍共用 3V3；每只麦克风独立时钟、独立数据。平时只给 MIC1 时钟，另外三只 CLOCK 保持低电平。MIC1 上的 libfvad 检测连续约 90ms 人声后，GPIO Matrix 将 **同一个 I2S0 PDM RX 硬件时钟** 接到 GPIO16/17/18。固件继续使用 MIC1 约 300ms，给新麦克风、滤波器及缓冲留出稳定时间；连续无声约 3s 后回到一麦。

四路 PCM DMA 保持运行，避免重建 I2S 通道打断 MIC1。此版本在语音阶段比较四路能量与估计噪声，保持选择至少约 300ms，并用约 30ms 交叉淡化降低切换突变。**输出是单声道，未实现波束成形、回声消除、自动立体声校准或 AI 降噪。** PCB 已保留四路独立采集条件，后续可增加校准与阵列算法。

CPU 动态频率 80–160MHz、LED 平时熄灭、BLE 较慢广播、Wi-Fi 无应用流量 120s 自动关闭。连续录音期间不进入 light/deep sleep；只有低电量时收尾并进入 deep sleep。停掉三只麦克风不能让整机功耗变成四分之一，ESP32、SD 与无线仍耗电。2000mAh 电池此前估算约 13–22 小时只是预算，不是实测；24 小时环形保留不等于已经达到 24 小时续航。

| 信号 | GPIO / 说明 |
|---|---|
| MIC1 CLOCK / DATA | 4 / 6，SELECT 接地 |
| MIC2 CLOCK / DATA | 16 / 5，SELECT 接 3V3 |
| MIC3 CLOCK / DATA | 17 / 7，SELECT 接地 |
| MIC4 CLOCK / DATA | 18 / 8，SELECT 接 3V3 |
| SD CS / MOSI / CLK / MISO | 10 / 11 / 12 / 13，SPI 默认 10MHz |
| USB D− / D+ | 19 / 20 |
| 按键 1 / 按键 2 | 0 / 15，低有效 |
| WS2812 LED | 48 |
| 电池 ADC | 1，100k/100k 分压，读数乘 2 |

此固件的麦克风接法与改前 PCB 不同。原板即使禁用第二键，也不能直接使用本版四路采集配置。

## 编译与烧录

使用 ESP-IDF **v5.5.5**，已验证提交 `b774170ff46c393eeb5e495ea37936038d3f4f4f`。安装 ESP32-S3 工具链，激活 IDF 环境后，在本目录运行：

```sh
idf.py set-target esp32s3
idf.py build
idf.py -p PORT flash monitor
```

默认 16MB Flash、8MB Octal PSRAM，4MB factory 应用分区；本 Demo 没有 OTA。USB Serial/JTAG 输出日志。GPIO0 同时是启动绑带脚，首次上电、复位时不要按住按键 1，除非有意进入下载模式。

`release/` 含本次编译的 app、bootloader、分区表和 SHA-256 清单。在该目录使用已安装的 esptool 烧录：

```sh
python -m esptool --chip esp32s3 --port PORT --baud 460800 write_flash --flash_mode dio --flash_size 16MB --flash_freq 80m 0x0 bootloader.bin 0x8000 partition-table.bin 0x10000 yuhuan_x1.bin
```

首次烧录须写入三份文件，只有 app 文件不足以初始化一块空白板。烧录工具和 USB 自动复位行为也需要实板验证。

## 手机作为本地后端

固件提供 BLE 与局域网 HTTP。配套 Android Demo 已放在相邻的 `yuhuan-x1-android/` 工程，具体接入能力与测试边界见其 README；`tools/phone_demo.py` 仍可在电脑运行，作为“手机后端模拟器”完成接收、续传、落盘和 ACK，没有外部服务器、云账号或 AI 接口。

安装 Python 3.9+；BLE 路径另需 `pip install bleak`。默认连接后立即同步所有仍在设备上的片段，包括旧的保护录音；`--watch` 在此程序运行期间每 30s 重新连接同步。接入正式 App 时复用同一协议，让 App 在连接事件触发同步即可。

```sh
python tools/phone_demo.py ble --out recordings
python tools/phone_demo.py ble --command '{"cmd":"status"}'
python tools/phone_demo.py ble --command '{"cmd":"wifi","ssid":"YOUR_SSID","password":"YOUR_PASSWORD","enabled":true}'
```

先经配对、加密 BLE 下发 Wi-Fi 凭据。稍后再次查询 status，得到 `wifi_ip` 和 `wifi_token`，然后用同一局域网进行大批量传输：

```sh
python tools/phone_demo.py http --url http://DEVICE_IP --token TOKEN --out recordings --watch
```

Wi-Fi 配置保存在 NVS；有配置时启动一次同步窗口，此后可由 BLE 再开启。仅连接路由器不能自动将音频交给手机，手机端还必须运行接收程序并能访问设备。完整 24 小时内容应优先用 Wi-Fi；BLE 用于控制、配置和备用传输，速度尚未实测。

客户端使用 `.part` 断点续传，精确校验长度和 WAV 头，fsync 后改为 `.wav`，再发送相同字节数 ACK；失败保留待续传文件。BLE 每帧另有 CRC32 和 offset 检查。同步成功不等于识别成功，此版本没有转写、信息提取、提醒生成或手机通知。手机后端可在 ACK 后将这些 WAV 解码为 PCM，交给本地语音识别与后续 AI 流程。

## 数据与故障边界

- 时间轴使用采样计数。断电期间不计入 24 小时；没有 RTC 的初始绝对时间为 0，连接手机后由 time 命令校时。旧片段不会凭空获得可信时间。
- 每约一秒刷新活动片段头并同步；重启会截去未完成的 ADPCM 块、修复 `.prt` 并封口。真实断电、SD 内部缓存和 FAT 损坏仍需测试，不能承诺零丢失。
- 保护元数据损坏时禁止自动删除，保留音频并上报错误。保留策略只删除超期、未保护、未被传输租用的片段。
- 软件队列丢帧计数并补静音维持该段时间；DMA 溢出、I/O 故障会被计数，不代表仍然完整录音。首次样机的必要目标是长时间压力测试中所有丢帧/写错误计数为 0。
- 无卡、文件系统挂载失败、空间低于 64MiB 或写入错误时会报错并停止写入。写入错误和空间停止后需处理问题并重启本 Demo。
- 电池 ADC 是电压，不是电量百分比或电流计。默认连续三次 30s 检查低于 3300mV 时停机，需按实际电池和 ADC 读数校准。
- BLE 使用无显示屏的 Just Works 配对与链路加密；HTTP 使用随机 Bearer token，尚未提供 TLS、正式账号、撤销绑定界面或量产安全方案。

## 已完成验证与样机验证

主机测试：`python tools/test_host.py`，包含 ASan/UBSan、按键状态、回溯保护、手动暂停分组、循环删除、租用防删除、重启恢复、ACK、损坏保护信息时保留音频。另用 FFmpeg 独立解码，440Hz 测试音 SNR 约 36.6dB，这是软件编解码测试，不是麦克风实测信噪比。

同步协议测试：`python -m unittest discover -s tests -p 'test_*.py' -v`，覆盖 BLE CRC/offset/过期响应、下载续传、成功后 ACK、失败不 ACK。已通过 ESP-IDF 编译；未连接实物或真实 BLE/HTTP 链路进行测试。

样机依次验证：3V3 和启动日志；SD 挂载；四只麦克风逐只轻敲/近讲确认声道顺序；待机和人声时 GPIO4/16/17/18 的示波器波形；反复唤醒后的多路固定延迟/增益；双键行为；同步续传；断电恢复；24 小时连续录制、同步并发、SD 延迟和电池侧电流。立体声或阵列算法须在拿到同步原始多路音频后单独校准，当前版本不应宣传已完成。

目录：`main/` 设备驱动、控制和传输；`components/yh_core/` 可主机测试的存储/编解码/按键核心；`components/fvad/` 原版 libfvad 与许可证；`PROTOCOL.md` App 接口；`release/` 烧录产物。
