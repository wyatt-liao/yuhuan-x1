# 语环 X1 本地同步协议 v1

所有 ID 和样本时间轴为 **16 位小写十六进制字符串**，避免手机 JSON 浮点数精度限制。每次只串行处理一个请求。录音片段封口后才进入 list，活动片段最多约一分钟后出现。客户端连接后自动 time → status → 分页 list → 下载 → 持久化 → ACK，再读取 marks。

## BLE

服务 UUID `8d4a0001-5c61-4d3d-a34b-e271e6fa1001`。

- 控制写入：`8d4a0002-5c61-4d3d-a34b-e271e6fa1001`，Write With Response，要求加密。
- 数据通知：`8d4a0003-5c61-4d3d-a34b-e271e6fa1001`，先订阅。发送通知时固件再次检查链路加密。
- JSON 命令以换行 `\n` 结尾，JSON 本身最多 512 字节，可分多次 GATT write。客户端按 ATT MTU−3 分片，即使 MTU23 也可使用。UTF-8 的字符串换行需 JSON 转义。
- `req` 为 1–65535 的请求号。重试使用新请求号，丢弃旧请求通知。

通知头为 12 字节、小端 `<BBHII>`：kind(u8)、flags(u8)、req(u16)、offset(u32)、payload CRC32(u32)，后接 payload。CRC 是标准 IEEE CRC32，只覆盖 payload。flags bit0=本次响应最后一帧，bit1=错误；kind1=JSON、kind2=文件字节。JSON offset 从0开始；文件 offset 是文件的绝对偏移。每帧总长不超过 ATT MTU−3。

read 每次最多4096字节，客户端校验 CRC 和连续 offset 后再接下一块。最后标记只表示本次 read 响应结束，不表示整个文件下载完成。错误使用 kind1 JSON，offset0；客户端不得把它当音频。

固件最多租用一个 BLE 下载片段；切换文件、ACK、断开连接或120s无 read 释放租用。连接状态每秒检查，租用防止该片段在传输期间被循环删除。HTTP 则在一次完整文件请求期间租用。断线后已经超出循环窗口且未保护的片段可能过期，客户端应接受 unavailable 并重新获取 list。

## 命令

每条命令可带 `req`，BLE 使用它关联通知，HTTP 忽略它。通常返回 `{"ok":true,...}`，失败返回 `{"ok":false,"error":"..."}`。

| JSON 示例 | 含义 |
|---|---|
| `{"cmd":"status"}` | 状态、录音错误计数、电池毫伏、Wi-Fi IP/token、时间轴 |
| `{"cmd":"time","epoch_ms":1792310400000}` | 设置当前 Unix 毫秒时间 |
| `{"cmd":"list","after":"0000000000000000","limit":16}` | ID 升序分页，最多16片段；空列表表示当前结束 |
| `{"cmd":"marks"}` | 返回保护区间、分组和种类 |
| `{"cmd":"read","id":"0000000000000001","offset":0,"length":4096}` | BLE 读取片段字节 |
| `{"cmd":"ack","id":"0000000000000001","bytes":487484}` | 仅当 bytes 与该片段完全相等时标记已同步 |
| `{"cmd":"protect","hours":3}` | 立即保护此前3小时已有录音，范围1–24 |
| `{"cmd":"unprotect","id":"0000000000000001"}` | 解除一个保护标记；不会立即主动删文件 |
| `{"cmd":"manual","action":"start"}` | 手动录音：start/stop/pause/resume；检查当前状态 |
| `{"cmd":"button1"}` / `{"cmd":"button2"}` | 与实体按钮相同，第二键会聚合500ms连击 |
| `{"cmd":"wifi","ssid":"SSID","password":"PASSWORD","enabled":true}` | 配置/启用 Wi-Fi；省略凭据可只开关 |

list 返回 `segments`，每项含 id、start、samples、epoch_ms、bytes、protected、synced。start 是样本时间轴，不是 Unix 时间；samples/16000 是时长。marks 的 start/end 是半开样本区间 `[start,end)`；kind1=回溯保护，kind2=手动录音。手动录音开始时 end 为 `ffffffffffffffff`，结束后为具体样本位置。暂停后的恢复使用相同 group。录音边界可能落在一分钟文件内部，App 按样本区间裁切播放/拼接，不要把整个保护重叠文件都当成手动录音内容。

## 局域网 HTTP

每次请求带 `Authorization: Bearer TOKEN`，token 仅经已配对、加密 BLE 获取。设备监听80端口，只在 Wi-Fi 开启窗口可达。

- `GET /v1/status`
- `GET /v1/segments?after=0000000000000000`
- `GET /v1/audio?id=0000000000000001`，可带 `Range: bytes=4096-`，返回206续传；暂不支持范围终点和多范围。
- `POST /v1/control`，body 为上表 JSON。HTTP 下载用 /audio，read 命令仅适用于 BLE。

接收方应下载全部仍可用片段并保存 marks；按 synced 跳过设备文件可能遗漏“已 ACK 但本地库丢失”的内容，因此参考客户端根据本地文件存在与长度决定跳过。ACK 表示接收方已持久化，不表示 AI 已处理。此协议没有外部云、AI 调用或提醒排期。
