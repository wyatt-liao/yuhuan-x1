# 语环 X1 HTML 汇报

14 页中文产品研发汇报，16:9。建议主体约 8 分钟，最后一页用于参数答疑。

- [离线 HTML](../../outputs/yuhuan-x1-report/语环X1-产品研发汇报.html)：下载文件后，用 Chrome、Edge 或 Safari 打开。所有图片内嵌，不需要网络。
- [PDF 备用版](../../outputs/yuhuan-x1-report/语环X1-产品研发汇报.pdf)
- [逐页讲稿](../../outputs/yuhuan-x1-report/汇报讲稿.md)

方向键或空格翻页；F 全屏，O 目录，N 讲稿，B 黑屏，Home / End 跳到首尾页。第 4 页可点击演示回溯 1、3、6、24 小时。

讲稿弹窗会显示在当前屏幕，投屏前请关闭。也可把 Markdown 讲稿放在手机上查看。支持浏览器打印为 PDF，推荐同时打开背景图形。

## 模型与产品口径

汇报和当前影片统一使用 R03 结构模型：主体 110.80 × 84.84 × 9.80 mm，含按键最高 10.15 mm。封面来自影片的同源 CAD 网格，结构页来自 R03 预览。手机画面为 Android 模拟器截图。

24 小时指循环保留窗口；续航尚未实测。AI 识别、摘要与提醒为后续规划。实板上电、样壳装配与真实无线链路仍待验证。

## 编辑与重建

`build.mjs` 包含逐页文字、讲稿和资料来源；`deck.css` 为排版与打印样式；`deck.js` 为放映控制。交付 HTML 内也保留可编辑的文字、样式与脚本。

在仓库根目录安装依赖并构建：

```sh
npm --prefix media/report install
npm --prefix media/report run build
```

构建将生成 `outputs/yuhuan-x1-report/` 下的 HTML 和 Markdown 讲稿。PDF 可从浏览器打印导出。
