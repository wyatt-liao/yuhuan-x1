import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const out=path.join(root,'outputs/yuhuan-x1-report');
await fs.mkdir(out,{recursive:true});
const images={};
async function asset(key,file,{width=1500,extract}={}) {
 let source=sharp(path.join(root,file));
 if(extract)source=source.extract(extract);
 const bytes=await source.resize({width,withoutEnlargement:true}).jpeg({quality:90,mozjpeg:true}).toBuffer();
 images[key]='data:image/jpeg;base64,'+bytes.toString('base64');
}
await Promise.all([
 asset('hero','media/film/assets/r03-hero.jpg',{width:1920}),
 asset('front','enclosure/r03/preview/01-front.png'),
 asset('buttons','enclosure/r03/preview/06-top.png',{width:1100,extract:{left:740,top:120,width:720,height:680}}),
 asset('pcb','hardware/reviews/语环X1-改线后PCB.png',{width:1000,extract:{left:680,top:0,width:1050,height:1422}}),
 asset('exploded','enclosure/r03/preview/03-exploded.png'),
 asset('inside','enclosure/r03/preview/04-inside.png'),
 asset('sprue','enclosure/r03/preview/08-buttons-sprue.png',{width:850}),
 asset('appToday','android/release/screenshots/normal/01-today.png',{width:680}),
 asset('appDetail','android/release/screenshots/normal/03-detail.png',{width:680}),
 asset('back','enclosure/r03/preview/02-back.png')
]);
const sourceRoot='https://github.com/wyatt-liao/yuhuan-x1/blob/c01c0e15f273b57459ff1d528d4bf85e318d61fd/';
const image=(name,alt,klass='',style='')=>`<img class="${klass}" style="${style}" src="${images[name]}" alt="${alt}" draggable="false">`;
const slides=[];
function slide(title,classes,html,notes,sources=[]) {slides.push({title,classes,html,notes,sources:sources.map(([title,file])=>({title,url:(file==='media/film/README.md'?'https://github.com/wyatt-liao/yuhuan-x1/blob/main/':sourceRoot)+encodeURI(file)}))});}
slide('语环 X1','cover dark',`
 ${image('hero','语环 X1 当前 R03 CAD 同源外观渲染','cover-image')}
 <div class="cover-copy"><p class="eyebrow">产品研发汇报</p><h1>语环 <span>X1</span></h1><p class="cover-tagline">重要的，回头还在。</p><p class="cover-desc">可回溯的随身录音卡</p></div>
 <div class="cover-meta">2026.10.19<span>硬件与软件原型阶段</span></div><p class="image-caption">R03 CAD 同源渲染，实物待验证</p>`,
 '大家好，今天汇报的是语环 X1，一款可以回溯保存的随身录音卡。我们希望解决一个很具体的问题：当用户意识到一句话很重要时，这句话往往已经说完了。语环先在设备上保留最近的录音，用户再通过一个简单的按键动作，把重要内容留下。当前硬件、固件、安卓端与结构文件已经形成配套工程，下一步进入实物验证。',
 [['项目总览','README.md'],['影片模型与结构的统一说明','media/film/README.md']]);
slide('值得记录的时刻，常常先于录音动作','paper',`
 <h2>值得记录的时刻，<br>常常先于录音动作</h2>
 <div class="problem-quote"><span class="quote-mark">“</span>刚才那句话，<br><em>能再说一遍吗？</em></div>
 <div class="problem-side"><div><h3>开始得太晚</h3><p>谈话里突然出现关键内容，<br>再掏出手机，已经错过。</p></div><div><h3>找起来太慢</h3><p>即使录下整场对话，<br>事后还要逐段重听。</p></div></div>
 <p class="bottom-note">目标场景：获得录音授权的课堂、会议与个人口述记录。使用需求仍需用户测试验证。</p>`,
 '这页描述的是我们准备验证的使用场景，不是已经完成的市场调查。一个问题是录音启动晚于重要内容，另一个问题是录下之后很难再找。传统手动录音要求用户提前判断什么值得记录，而我们的设计把保存的决定放到事情发生之后。第一阶段先验证回溯保存是否真的方便，后续再用语音识别和信息整理降低重听成本。');
slide('设备负责记录，手机负责接收与整理','paper',`
 <h2>设备负责记录<br>手机负责接收与整理</h2>
 ${image('front','当前 R03 外壳的正面 CAD 渲染','product-right')}
 <div class="product-story"><div><span class="step-label">设备端</span><h3>持续保留最近录音</h3><p>手动录音照常使用，后台循环记录持续运行。</p></div><div><span class="step-label">用户操作</span><h3>按键保存已经发生的内容</h3><p>单击回溯 1 小时，连按 N 次回溯 N 小时。</p></div><div><span class="step-label">手机端</span><h3>本地接收与播放</h3><p>Android Demo 已实现。AI 整理列入后续开发。</p></div></div>
 <p class="bottom-note">当前 Demo 以手机作为本地后端，不依赖自建云服务器。</p>`,
 '整体分工是，录音卡负责连续采集和本地保存，手机负责接收、播放和后续整理。当前 Demo 不建设云端服务，先利用手机作为本地后端。这样可以把设备采集、回溯保存和手机接收先联通，再接入真正的识别与 AI。这里展示的是 R03 的结构渲染，还不是已经完成实物装配的产品照片。',
 [['固件行为','firmware/README.md'],['Android 功能','android/README.md']]);
slide('按一下，留下前一小时','forest',`
 <h2>按一下，留下前一小时</h2><p class="subtitle">保存决定可以发生在谈话之后</p>
 <div class="recall-display"><div class="recall-number"><span id="recall-hours">1</span><small>小时</small></div><div class="recall-summary"><h3 id="recall-title">最近 1 小时获得保护</h3><p>保护标记防止这些片段被循环清理。<br>超期且未保护的片段才会进入清理范围。</p></div></div>
 <div class="recall-chart" aria-label="最近24小时与回溯保护范围示意"><div class="time-bar" id="time-bar">${Array.from({length:24},(_,i)=>`<span class="time-segment${i===23?' protected':''}" data-hour="${i}"></span>`).join('')}</div><div class="time-labels"><span>24 小时前</span><span>现在</span></div></div>
 <div class="recall-actions" role="group" aria-label="选择回溯演示时长"><button data-recall="1" aria-pressed="true">单击 1 次</button><button data-recall="3" aria-pressed="false">连按 3 次</button><button data-recall="6" aria-pressed="false">连按 6 次</button><button data-recall="24" aria-pressed="false">回溯 24 小时</button></div>
 <p class="bottom-note">机制演示，未连接实际录音。最多回溯已有的 24 小时内容。24 小时是保留窗口，续航仍待实测。</p>`,
 '这一页可以现场点击演示。默认单击一次，最后一小时会被标记保护。换成连续三次，就保护前三小时。设备不需要现场复制几小时的文件，而是保存持久化保护标记，避免这些内容被循环清理。回溯最多二十四小时，设备没录满时只能保留已有内容。需要特别区分：二十四小时描述的是录音保留窗口，电池续航还没有实测结果。',
 [['回溯保护与存储边界','firmware/README.md'],['按键与存储核心','firmware/components/yh_core/include/yh/buttons.hpp']]);
slide('两个实体按键','paper',`
 <h2>两个实体按键</h2><p class="subtitle">常规录音与回溯保存，共用一套简单操作</p>
 ${image('buttons','R03 正面录音键与暂停回溯键的局部渲染','buttons-image')}
 <div class="key-copy"><div class="key-row"><span class="key-index">01</span><div><h3>录音键</h3><p>单击开始手动录音<br>录音中再次单击，结束本次录音</p></div></div><div class="key-row"><span class="key-index">02</span><div><h3>暂停 / 回溯键</h3><p>手动录音中：暂停，再按继续<br>无手动录音：连按 N 次保存前 N 小时</p></div></div></div>
 <p class="bottom-note">手动暂停只影响本次手动录音的保存区间，后台循环记录继续。</p>`,
 '实体交互保留两个按键。第一个按键负责一段手动录音的开始和结束。第二个按键在手动录音过程中用于暂停和继续，在没有手动录音时用于回溯保存。暂停时，后台环形录音仍然继续，所以不会因为用户暂停了一个手动任务，就失去整个回溯窗口。连续点击由固件统一判断，停止点击五百毫秒之后执行。',
 [['按键状态表','firmware/README.md']]);
slide('硬件与手机的系统分工','paper',`
 <h2>硬件与手机的系统分工</h2>
 <figure class="pcb-figure">${image('pcb','当前独立四麦与双按键 PCB 的原生布局截图')}<figcaption>当前 PCB 工程截图，尚未完成实板上电</figcaption></figure>
 <div class="architecture"><div><span>采集</span><h3>四麦独立时钟与数据</h3><p>ESP32-S3 负责采集、状态控制与录音编码。</p></div><div><span>保存</span><h3>SD 卡循环存储</h3><p>一分钟一个片段，保存标记与音频分开管理。</p></div><div><span>同步</span><h3>BLE 控制，Wi-Fi 批量接收</h3><p>手机校时并接收文件，校验落盘后回传确认。</p></div></div>
 <p class="bottom-note">当前音频格式：16 kHz 单声道 IMA ADPCM WAV。完整 24 小时约 701 MB，不含文件系统和标记开销。</p>`,
 '硬件采用 ESP32-S3，四只麦克风独立引出时钟和数据，录音按一分钟分片存到 SD 卡。当前保存格式是十六千赫兹单声道 IMA ADPCM WAV，二十四小时音频大约七百零一兆字节。BLE 主要负责配对、控制和网络配置，大批量历史内容优先走同一局域网内的 Wi-Fi。手机在文件完整校验并落盘后才确认成功，真实吞吐和兼容性还需要实板联调。',
 [['硬件工程说明','hardware/README.md'],['设备同步协议','firmware/PROTOCOL.md']]);
slide('按人声切换麦克风工作模式','forest',`
 <h2>按人声切换麦克风工作模式</h2>
 <div class="power-modes"><div><p class="step-label">平时</p><div class="mode-numeral">1<small>麦</small></div><h3>MIC1 持续检测人声</h3><p>另外三路时钟保持关闭。</p></div><div><p class="step-label">检测到人声</p><div class="mode-numeral">4<small>麦</small></div><h3>开启其余三路采集时钟</h3><p>稳定后比较声道，当前输出单声道。</p></div></div>
 <div class="power-detail"><p><strong>动态功耗策略</strong>CPU 调频、LED 平时熄灭、无流量时关闭 Wi-Fi。</p><p><strong>连续录音策略</strong>录音期间保持运行，续航以电池侧电流实测为准。</p></div>
 <p class="bottom-note">四麦共用供电，当前通过时钟控制工作状态。已实现模式切换，波束成形与自动立体声校准尚未实现。</p>`,
 '功耗设计的重点是让采集资源随需求变化。平时只有第一只麦克风获得工作时钟，同时检测人声。确认有人声后，打开其他三路时钟，并留出稳定时间。当前算法进行声道选择和交叉淡化，输出仍然是单声道，没有把它包装成已实现的波束成形。四麦供电仍然共用，ESP32、SD 卡和无线也在耗电，因此不能用一麦来推算整机只消耗四分之一电量。',
 [['麦克风与功耗策略','firmware/README.md'],['采集实现','firmware/main/audio.cpp']]);
slide('薄型结构的首件方案','paper',`
 <h2>薄型结构的首件方案</h2>
 ${image('exploded','R03 铝塑分区样壳、电池槽及 PCB 的装配爆炸图','exploded-image')}
 <div class="structure-copy"><div class="dimension"><strong>9.80</strong><span>mm 主体厚度</span></div><p>电池置于 PCB 槽内<br>引线直焊，降低连接高度</p><p>外露圆头榫接<br>保留铝件与塑料天线区的分件</p><p>先用树脂样壳验证装配<br>未来铝件另做 CNC 工艺评审</p></div>
 <p class="bottom-note">R03 长 110.80 × 宽 84.84 mm，含按键最大厚度 10.15 mm。当前为树脂打印首件方案，铝件另做工艺评审。</p>`,
 '轻薄是产品方向，影片、汇报和制造文件现在统一采用 R03 结构。首件为了适应树脂打印，主面与侧壁调整到两毫米，主体总厚度九点八毫米。电池放在 PCB 槽中，而不是叠在电路板上，连接改为引线直焊。结构保留外露圆头榫接以及未来铝塑分区，先完成样壳装配，再讨论 CNC 方案和进一步减薄。',
 [['R03 打印与装配','enclosure/r03/README-打印与拼版说明.md'],['版本状态','docs/PROJECT_STATUS.md']]);
slide('Android Demo 已形成可安装工程','paper',`
 <h2>Android Demo<br>已形成可安装工程</h2>
 <div class="app-copy"><h3>本地录音库</h3><p>连接设备、接收文件<br>播放、备注与分享</p><h3>设备控制与同步</h3><p>手动录音、回溯保存<br>BLE 与局域网断点续传</p><p class="app-boundary">当前 AI 页面明确显示“尚未分析”。<br>真实设备联调仍待完成。</p></div>
 <figure class="phone phone-one">${image('appToday','Android Demo 今天页真实模拟器截图')}<figcaption>今天</figcaption></figure>
 <figure class="phone phone-two">${image('appDetail','Android Demo 播放详情页真实模拟器截图')}<figcaption>录音详情</figcaption></figure>
 <p class="bottom-note">截图来自 Android 模拟器，播放页使用测试 APK 注入的短音频。交付主 APK 不预装测试录音。</p>`,
 '安卓端已经形成可安装的原生 Demo，使用 Kotlin 和 Jetpack Compose。现在已经有设备连接、录音控制、文件接收、本地播放、备注和分享，也实现了断点续传与下载校验。画面是模拟器真实截图，里面的短音频来自测试 APK。当前软件不伪造转写或摘要，AI 页面会明确显示尚未分析。真实板子的配对、网络速度和后台稳定性还需要联调。',
 [['Android 实现与边界','android/README.md'],['Android 验证记录','android/release/verification.md']]);
slide('下一阶段的 AI 生活助手','forest',`
 <h2>下一阶段的 AI 生活助手</h2><p class="subtitle">计划先在手机端处理已同步录音</p>
 <div class="ai-input"><p class="step-label">对话输入示例</p><blockquote>周五下午三点，<br>记得把方案发给我。</blockquote></div>
 <div class="ai-output"><p class="step-label">预期整理结果</p><dl><div><dt>事项</dt><dd>发送方案</dd></div><div><dt>时间</dt><dd>周五 15:00</dd></div><div><dt>用户操作</dt><dd>核对内容并确认提醒</dd></div></dl></div>
 <div class="ai-road"><span>语音转写</span><span>信息提取与摘要</span><span>用户确认与提醒</span></div>
 <p class="bottom-note">功能规划示例，尚未接入 ASR、LLM 或提醒排程。时间与任务需要结合对话上下文核对。</p>`,
 '这是下一阶段的功能规划。设备把声音交给手机之后，我们希望手机先做转写，再从里面提取任务、时间和摘要。比如用户说周五下午三点发送方案，系统应识别出事项和候选时间，再让用户核对并确认提醒。它需要结合上下文处理日期和指代，所以不能只靠一个示例就宣称已经完成。目前 ASR、模型调用和提醒排程都还没有接入。',
 [['AI 当前状态','android/README.md'],['研发进度','docs/PROJECT_STATUS.md']]);
slide('当前交付与验证进度','paper',`
 <h2>当前交付与验证进度</h2>
 <table class="status-table"><thead><tr><th>模块</th><th>已有成果</th><th>下一项实物验证</th></tr></thead><tbody>
 <tr><th>硬件</th><td>原生 PCB、独立四麦与双按键改线</td><td>电源、启动、各麦声道</td></tr>
 <tr><th>固件</th><td>IDF 编译、存储与协议主机测试</td><td>实板烧录、连续录音、断电恢复</td></tr>
 <tr><th>Android</th><td>可安装 APK、6 项 JVM 测试<br>Lint 0 错误、模拟器界面检查</td><td>BLE / Wi-Fi 与真实录音卡联调</td></tr>
 <tr><th>外壳</th><td>R03 STEP / STL、几何与间隙检查<br>小按键可剪连桥拼版</td><td>电池实测、工厂审图、首件装配</td></tr>
 <tr><th>AI</th><td>产品流程与界面占位</td><td>接入真实识别与提醒流程</td></tr></tbody></table>
 <p class="bottom-note">软件测试、模拟器和 CAD 检查已形成记录，量产可靠性仍需样机与实测支撑。</p>`,
 '当前成果已经形成完整工程链：硬件有原生 PCB 和改线记录，固件有编译产物与主机测试，安卓有安装包、六项核心 JVM 测试和模拟器界面检查，结构有 STEP、STL 和几何报告。这里不使用一个笼统的完成百分比，而是把成果和下一项实物验证并排列出。最关键的缺口仍是第一块板子的上电和连续录音联调。',
 [['完整项目状态','docs/PROJECT_STATUS.md'],['Android 测试依据','android/release/verification.md'],['结构检查','enclosure/r03/verification/geometry-check.json']]);
slide('下一步以样机验证推进','paper',`
 <h2>下一步以样机验证推进</h2><p class="subtitle">每一步都有可检查的结果，再进入下一阶段</p>
 <div class="milestones"><div><span>01</span><h3>板子能稳定录音</h3><p>上电、SD 卡与四麦确认<br>查清丢帧和写入错误</p></div><div><span>02</span><h3>设备与手机联通</h3><p>连续录音与同步并发<br>续传、锁屏和断链恢复</p></div><div><span>03</span><h3>结构与续航实测</h3><p>样壳装配与孔位检查<br>电流、温升和实际续航</p></div><div><span>04</span><h3>接入手机 AI</h3><p>真实录音转写与摘要<br>用户确认后生成提醒</p></div></div>
 <div class="next-decision"><p>近期投入重点</p><h3>PCB 样机、树脂样壳与实物联调</h3></div>
 <p class="bottom-note">以上为阶段顺序，具体周期待首件结果确认。当前尚未具备可直接承诺量产的测试证据。</p>`,
 '接下来的重点很明确。先把板子做出来，确认每一个麦克风与存储链路都工作，再验证连续录音和手机同步能同时运行。结构和电流需要真实测量，才能讨论进一步减薄和续航。完成这些之后，再接入手机侧 AI，并用真实录音检验转写与待办提取效果。阶段以验证结果推进，这里不虚构交付日期或预算。',
 [['实物验证清单','firmware/README.md'],['项目后续步骤','docs/PROJECT_STATUS.md']]);
slide('重要的，回头还在','closing paper',`
 <div class="closing-copy"><p class="eyebrow">语环 X1</p><h2>重要的，<br><em>回头还在。</em></h2><p class="closing-next">下一步：完成第一台可联调样机</p><a class="repo-link" href="https://github.com/wyatt-liao/yuhuan-x1" target="_blank" rel="noopener noreferrer">github.com/wyatt-liao/yuhuan-x1</a><p class="repo-caption">公开工程与交付文件</p></div>
 ${image('back','R03 外壳背面与外露圆头榫接 CAD 渲染','closing-image')}`,
 '语环 X1 的核心是，把保存重要内容的决定，放到内容发生之后。当前已经完成配套工程与 Demo，近期的目标是让第一台样机完成连续录音、回溯保存和手机同步。项目资料已经公开，欢迎围绕硬件、结构和使用体验提出问题。谢谢。',
 [['公开仓库','README.md']]);
slide('附录：参数与版本口径','paper appendix',`
 <h2>附录：参数与版本口径</h2>
 <table class="spec-table"><tbody>
 <tr><th>主控</th><td>ESP32-S3-WROOM-1-N16R8</td><th>音频</th><td>16 kHz，单声道，IMA ADPCM WAV</td></tr>
 <tr><th>存储</th><td>FAT32 SD 卡，一分钟一个片段</td><th>保留窗口</th><td>最近 24 小时采样时间</td></tr>
 <tr><th>回溯</th><td>整小时 1–24 小时，最多保护已有内容</td><th>同步</th><td>BLE 控制与传输，局域网 HTTP 批量接收</td></tr>
 <tr><th>电池</th><td>504060，3.7 V，2000 mAh，自带保护板</td><th>续航</th><td>尚未实测</td></tr>
 <tr><th>R03 主体</th><td>110.80 × 84.84 × 9.80 mm</td><th>最高处</th><td>含按键 10.15 mm</td></tr>
 <tr><th>首件工艺</th><td>全树脂 SLA 分件打印，螺丝装配</td><th>后续材质</th><td>铝合金与塑料天线区，待工艺评审</td></tr>
 <tr><th>固件</th><td>Demo 0.1.0，ESP-IDF v5.5.5</td><th>Android</th><td>Demo 0.1.0，最低 Android 8.0</td></tr>
 <tr><th>AI</th><td>ASR、摘要、提醒尚未接入</td><th>实物状态</th><td>PCB 未上电，样壳未打印装配</td></tr>
 </tbody></table>
 <p class="bottom-note">依据截至 2026-10-19 的工程记录。电池成品最大外形尚未测量，制造尺寸以当前 CAD 和首件复核为准。</p>`,
 '这页用于答疑。涉及产品参数时以这里和工程 README 为准。影片和汇报现在统一使用九点八毫米主体的 R03 首件结构，最高处含按键为十点一五毫米。二十四小时是保留窗口，续航未实测。四路采集也不等于已经实现立体声、阵列算法或 AI。',
 [['固件参数','firmware/README.md'],['Android 参数','android/README.md'],['R03 参数','enclosure/r03/README-打印与拼版说明.md']]);

const css=await fs.readFile(path.join(root,'media/report/deck.css'),'utf8');
const js=await fs.readFile(path.join(root,'media/report/deck.js'),'utf8');
const html=`<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="color-scheme" content="light dark"><title>语环 X1 · 产品研发汇报</title><style>${css}</style></head>
<body><main id="frame" aria-label="语环 X1 产品研发汇报"><div id="stage">${slides.map((s,i)=>`<section class="slide ${s.classes}${i===0?' active':''}" data-title="${s.title}" aria-label="第 ${i+1} 页：${s.title}" aria-hidden="${i!==0}" ${i!==0?'inert':''}>
${i===0||i===12?'':'<div class="running">语环 X1</div>'}${s.html}<footer class="slide-footer"><span>${i===13?'参考参数':'产品研发汇报'}</span><span>${String(i+1).padStart(2,'0')} / ${slides.length}</span></footer></section>`).join('\n')}</div></main>
<nav id="toolbar" aria-label="演示控制"><button id="prev" title="上一页，左方向键" aria-label="上一页">‹</button><button id="counter" title="打开目录，O 键">01 / ${slides.length}</button><button id="next" title="下一页，右方向键" aria-label="下一页">›</button><span class="toolbar-separator"></span><button id="overview-button" title="目录，O 键">目录</button><button id="notes-button" title="讲稿，N 键">讲稿</button><button id="fullscreen" title="全屏，F 键">全屏</button><button id="help-button" title="使用说明，问号键" aria-label="打开使用说明">?</button></nav>
<div id="progress" aria-hidden="true"></div><p id="status" class="sr-only" role="status" aria-live="polite"></p><div id="blackout" hidden><span>按 B 或 Esc 返回演示</span></div>
<dialog id="overview"><div class="dialog-header"><h2>演示目录</h2><button class="close-dialog" aria-label="关闭目录">关闭</button></div><div class="overview-list">${slides.map((s,i)=>`<button data-slide="${i}"><span>${String(i+1).padStart(2,'0')}</span>${s.title}</button>`).join('')}</div></dialog>
<dialog id="notes"><div class="dialog-header"><h2 id="notes-title">讲稿</h2><button class="close-dialog" aria-label="关闭讲稿">关闭</button></div><div class="notes-meta"><span id="elapsed">00:00</span><button id="timer-toggle">开始计时</button><button id="timer-reset">重置</button></div><p id="notes-body"></p><div id="notes-sources"></div><p class="dialog-hint">讲稿会显示在当前屏幕。正式投屏时请关闭，或提前使用配套 Markdown 讲稿。</p></dialog>
<dialog id="help"><div class="dialog-header"><h2>演示说明</h2><button class="close-dialog" aria-label="关闭使用说明">关闭</button></div><dl class="help-keys"><div><dt>左 / 右方向键、空格</dt><dd>翻页</dd></div><div><dt>F</dt><dd>切换全屏</dd></div><div><dt>O</dt><dd>打开目录</dd></div><div><dt>N</dt><dd>查看当前页讲稿和资料来源</dd></div><div><dt>B</dt><dd>暂时黑屏</dd></div><div><dt>Home / End</dt><dd>首页 / 最后一页</dd></div><div><dt>Ctrl / Cmd + P</dt><dd>打印或另存为 PDF</dd></div></dl><p>单文件包含全部图片，不调用外部字体或服务。支持触屏左右滑动。目录中可直接跳页。</p><p class="dialog-hint">第 4 页含回溯范围交互演示。示意内容不访问实际设备。全屏请使用 Chrome、Edge 或 Safari。</p></dialog>
<script id="speaker-data" type="application/json">${JSON.stringify(slides.map(({title,notes,sources})=>({title,notes,sources}))).replace(/</g,'\\u003c')}</script><script>${js}</script></body></html>`;
await fs.writeFile(path.join(out,'语环X1-产品研发汇报.html'),html);
const notes=`# 语环 X1 产品研发汇报讲稿\n\n日期：2026-10-19。建议主体约 8 分钟，第 14 页为答疑参数。\n\n`+slides.map((s,i)=>`## ${String(i+1).padStart(2,'0')} ${s.title}\n\n${s.notes}\n\n${s.sources.length?'资料：'+s.sources.map(x=>`[${x.title}](${x.url})`).join('、')+'\n':''}`).join('\n');
await fs.writeFile(path.join(out,'汇报讲稿.md'),notes);

console.log(JSON.stringify({slides:slides.length,htmlBytes:Buffer.byteLength(html),output:out}));
