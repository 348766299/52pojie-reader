# GitHub 开源项目调研：吾爱破解（52pojie）安卓客户端

> 调研时间：2026-11-13 ｜ 数据来源：GitHub REST API（search/repositories、repos）+ 网络检索 + 对 52pojie.cn 的真实 HTTP 请求

## 一、直接相关的项目

| 项目 | Star | 语言 / 许可 | 最近提交 | 技术路线 | 状态 |
|---|---|---|---|---|---|
| [fabricezhang/lcg](https://github.com/fabricezhang/lcg) | 807 | Kotlin / MIT | 2024-10 | Jsoup 抓取 + 原生 UI 重排版 + HtmlTextView，MVVM | 已停更，登录偶发失效 |
| [sjshb57/NewLcgR](https://github.com/sjshb57/NewLcgR) | 58 | Kotlin 72% / Java 27% / MIT | 2026-07 | LCG 重构续作，Coil 图片、重写 HtmlTextView、AppGuard 崩溃重启 | 活跃，但 README 作者自述「坏了，有没有人提交代码诶，我不会修」；已发 v2.3.0 |
| [MrLiQiu/lcg](https://github.com/MrLiQiu/lcg) | 0 | Java / MIT | — | 早期分支：Jsoup 重排版 + WebView 混合 | 归档性质 |
| [ganlvtech/down_52pojie_cn](https://github.com/ganlvtech/down_52pojie_cn) | 2828 | Vue / PHP / MIT | 2019 | 爱盘（down.52pojie.cn）单页文件浏览器 | 与客户端无关，但说明「移动端友好访问」需求真实存在 |
| [Mrzqd/52pojie_sign](https://github.com/Mrzqd/52pojie_sign) | 118 | Python | 2026-10 | 签到脚本 | 脚本类，非 App |

**LCG 系功能清单（值得借鉴的部分）**

- 浏览论坛各板块、Jsoup 重新排版文章
- 账号密码登录并保持登录态
- 评论 / 点赞 / 收藏 / 关注 / 阅读记录
- 自动签到
- 百度站内搜索
- **一键提取正文内所有百度网盘 / 蓝奏云下载链接并复制**
- 早期版本使用「移动网页方式浏览」作为兜底

## 二、通用论坛客户端参考

搜索 `android forum client jsoup / webview` 得到 25 个仓库，star 较高的有：

- `lincanbin/Android-Carbon-Forum`（248★，Java + Material）
- `Awful/Awful.apk`（113★，Something Awful 原生客户端）
- `ForumHFR/Redface` / `redface2`（HFR 论坛客户端，Kotlin 重写版）
- `Shirolin/s1er`（Flutter 跨平台 S1 客户端）

共同点：几乎全部走「抓 HTML → 解析 DOM → 原生渲染」路线，因此都高度依赖目标站点的 DOM 结构和登录接口。

## 三、两条技术路线的对比

| | A. 抓取 + 原生渲染（LCG 路线） | B. WebView + 注入增强（本项目） |
|---|---|---|
| 阅读体验 | 原生列表/排版，最流畅 | 网页排版 + CSS 优化，接近原生 |
| 登录 / 签到 / 回复 / 附件 | 需自行实现 Cookie、formhash、验证码，易失效 | 全部由原站提供，天然可用 |
| 抗改版能力 | 弱，站点一改版就要改解析器 | 较强，核心只依赖少量稳定选择器 |
| 开发 / 维护成本 | 高 | 低 |
| 失效后的表现 | 客户端直接不可用 | 退化为「就是一个浏览器」，仍可用 |

**结论**：用户的核心诉求是「方便观看」，不是「重写一个论坛」。因此选 B —— 用 WebView 保住原站全部能力，只把精力放在阅读体验上。LCG 作者自己那句「坏了，我不会修」正好印证了 A 路线的维护代价。

## 四、对目标站点的实测（决定实现细节）

用 Android Chrome UA 对 52pojie.cn 发起真实请求：

| 检查项 | 实测结果 | 对实现的影响 |
|---|---|---|
| HTTP 状态 | `200`，`Content-Type: text/html; charset=gbk` | 注入脚本必须走 UTF-8 Base64，不能依赖页面编码 |
| CSP 响应头 | **无 Content-Security-Policy** | 可以自由注入 `<style>` 与 `eval` 引导脚本 |
| 是否需要登录看帖 | **不需要**，`thread-1325357-1-1.html` 匿名返回完整内容 | 阅读模式可直接提取正文 |
| 页面模板 | Discuz! X，`mobile=2` 参数无效，统一返回同一套响应式 PC 模板 | 必须自己做移动端重排 |
| viewport | `width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no` | **站点禁止双指缩放**，是手机看代码/截图最难受的点，需注入修正 |
| 标题节点 | `#thread_subject` | 阅读模式标题来源 |
| 楼层容器 | `div#post_<纯数字>`，实测 15 个真实楼层 | 阅读模式遍历单位；必须按 ID 正则精确匹配 |
| 干扰节点 | 另有 15 个 `post_rate_*`（评分）和 `post_new` 等 `div[id^="post_"]` | 若只按前缀取会多出一倍假楼层，已用 `/^post_\d+$/` 过滤 |
| 正文节点 | 15 层中 14 层有 `td.t_f` / `#postmessage_xxx`，1 层内容不可见 | 阅读模式跳过无正文楼层（实测输出 14 层） |
| 作者 / 时间 | 每个楼层内 `.authi`、`.xw1`、`em` | 楼层头信息来源 |
| 其它 | `Set-Cookie` 正常下发，无 Cloudflare | WebView 可保持登录态 |

## 五、从开源项目里实际借鉴了什么

1. **MrLiQiu/lcg** —— 「一键提取百度/蓝奏下载链接」这个高频刚需，本项目的 `R52.extractLinks()` 做了强化（覆盖 123 盘、夸克、迅雷、城通、附件链接等）。
2. **LCG 系** —— 收藏、阅读记录、板块导航、签到入口的产品结构；但签到只做**快捷跳转**，不做自动化脚本。
3. **HtmlTextView 思路** —— 代码块、引用、图片在窄屏的处理，本项目用 CSS 实现（更轻、不依赖库）。
4. **反面经验** —— 不自建网络层、不解析 formhash、不硬编码登录接口，避免「站点一改版就整体失效」。

## 六、本次验证记录

不是「写完就说能用」，交付前做了这些实测：

| 验证项 | 方法 | 结果 |
|---|---|---|
| GitHub 项目数据 | GitHub REST API 实时查询 star / 语言 / 最近提交 / License | 已核对，见第一节 |
| 站点真实响应 | Android Chrome UA 发起真实 HTTP 请求（首页 + 真实帖子页） | 200 / GBK / 无 CSP / 匿名可读 / viewport 禁缩放 |
| 阅读模式提取 | **jsdom 加载真实抓取的帖子 HTML**，执行 boot.js + enhance.js + enhance.css | **22/22 断言通过**：提取 14 层正文、标题「开源吾爱破解App 1.8.7」、作者与时间、楼主标记、过滤 15 个 `post_rate_*` 干扰节点 |
| 注入编码 | UTF-8 Base64 解码后在 GBK 页面上重建 `<style>` | 中文内容无乱码 |
| 缩放修复 | 断言 viewport 被改写 | `user-scalable=yes, maximum-scale=5.0` |
| 下载链接提取 | 在真实帖子上执行 `R52.extractLinks()` | 成功提取蓝奏云链接与论坛附件链接 |
| 图片查看 | 模拟点击正文图片 | 成功回调原生层并带上图片列表 |
| 模式切换 | 优化排版 / 沉浸阅读 / 原版 / 只看楼主 互相切换 | 类名与 DOM 状态均正确 |
| Java 源码 | java-parser 解析全部 5 个 .java | 语法 5/5 通过 |
| Android 资源 | 扫描 7 个 XML + 47 处 `R.*` / `@type/name` 引用 | 全部可解析 |
| 打包工作流 | YAML 解析 | 语法正确，8 个 step |

**未做到的一项**：本机没有 JDK / Gradle / Android SDK，尝试临时下载 Android 命令行工具时网络过慢（10 分钟未完成）而中止，因此**没有在本机执行 Gradle 真编译**。
作为兜底，仓库里配置了 GitHub Actions 工作流（JDK 17 + API 34 + Build-Tools 34.0.0 + Gradle 8.7），push 后会自动产出可直接安装的 debug APK。

## 七、合规说明

- 本项目是**阅读增强客户端**，不绕过登录、权限、验证、付费或任何访问控制，不含自动签到/刷分/爬取脚本。
- 论坛内容版权归原作者及论坛所有；本项目不缓存、不转载、不分发任何帖子内容。
- 所有设置、收藏、历史仅存于用户本机，无统计、无上报、无第三方 SDK。
