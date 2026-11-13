# 吾爱阅读 · 52pojie 安卓阅读增强客户端

一个专门解决「**在手机上看吾爱破解论坛不方便**」的开源 Android App。

它不重写论坛，而是用 WebView 承载原站（登录、签到、回复、点赞、附件全部照常可用），
再注入一段 CSS/JS 把 PC 风格的帖子重排成适合手机的阅读界面。完整调研见 [RESEARCH.md](RESEARCH.md)。

**仓库**：https://github.com/348766299/52pojie-reader
**直接装 APK**：https://github.com/348766299/52pojie-reader/releases/latest

> 代码推送后 `.github/workflows/build-apk.yml` 自动编译，已实测构建成功：
> 产物同时上传 Actions 与发布到 **Releases**，手机浏览器可直接下载安装。
> 本机没有 JDK / Android SDK 也没关系，云端构建不需要装任何开发工具。

---

## 为什么不是照抄 GitHub 上的 LCG

GitHub 上最成熟的是 `fabricezhang/lcg`（807★）和它的续作 `sjshb57/NewLcgR`（v2.3.0，仍在更新），
都是 Kotlin + MVVM + Jsoup 抓取后原生渲染。体验很原生，但代价是要自己维护登录态、
formhash 和 Discuz 页面解析 —— 续作的 README 里作者写着「坏了，有没有人提交代码诶，我不会修」。

本项目的取舍：**保住原站所有能力，只优化「看」这一件事**。站点改版时，改几个 CSS 选择器就能恢复；
最坏情况下它只是个带工具栏的浏览器，不会整体不可用。实测依据见 [RESEARCH.md 第四节](RESEARCH.md#四对目标站点的实测决定实现细节)。

---

## 功能

**阅读**
- **优化排版**：把 Discuz 左右两栏表格改成单栏，正文全宽、图片自适应、代码块可横向滚动
- **沉浸阅读**：一键只保留标题 + 各楼层作者/时间/正文，重排成卡片；可「只看楼主」
- **恢复双指缩放**：原站 viewport 写死 `user-scalable=no`，App 注入修正
- **夜间模式**：跟随系统 / 常开 / 常关
- **字号调节**：80% ~ 160%，持久化
- **隐藏图片**：省流量模式，原生层同时拦截图片请求

**效率**
- 一键提取正文里的网盘 / 附件下载链接（百度、蓝奏、123 盘、夸克、迅雷、城通、论坛附件），可单条或全部复制
- 点击图片进入全屏大图查看，支持双指缩放、左右翻页
- 收藏夹 + 阅读历史（长按删除），全部本地存储
- 站内搜索 / 百度站内搜索
- 首页、最新帖、论坛任务（签到入口）快捷导航
- 长按链接可复制 / 外部打开 / 收藏
- 从系统浏览器点开 52pojie 链接时可选择用本 App 打开

**其它**
- 完整保留登录态、回复、上传附件（`onShowFileChooser`）、文件下载、`target=_blank` 链接
- 零第三方依赖（不用 AndroidX、不用 OkHttp/Jsoup/Coil），不联网上报、不含统计 SDK
- `minSdk 26`（Android 8.0+），`targetSdk 34`

---

## 怎么拿到 APK

### 方式 A：GitHub Actions 云端打包（推荐，不用装开发环境）

代码推送到 `main` / `master` 分支后会自动触发「构建 Android APK」，约 3~6 分钟完成；
也可以到仓库 **Actions** 页手动点 **Run workflow** 重新打包。

构建成功后有两个下载入口：
- **Releases（推荐）**：打开仓库右侧 Releases，下载 `wuai-reader-v1.0.0-build.x.apk`，
  手机浏览器直接下载安装即可（需允许「安装未知来源应用」）；
- **Actions 产物**：点进该次运行，下载底部 `wuai-reader-apk`，解压得到 `app-debug.apk`。

自建仓库时：新建选 **Public**、**不要**勾选 README 初始化，把本目录所有文件推上去
（注意保留 `.github` 目录）。仓库以纯文本托管，`gradle-wrapper.jar` 这个二进制文件
由 CI 自动从 Gradle 官方仓库下载，本地若提示缺 jar，执行一次：

```bash
curl -fsSL -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar
```

> 每次 push 都会生成一个带构建号的 Release（`v1.0.0-build.<run_number>`），不会互相覆盖。

### 方式 B：本机用 Android Studio 编译

1. 安装 Android Studio（自带 JDK 17），SDK Manager 里勾选 **Android 14 (API 34)** 和 **Build-Tools 34.0.0**。
2. `File → Open` 选中本目录，等 Gradle Sync 完成。
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`，
   产物在 `app/build/outputs/apk/debug/app-debug.apk`。

命令行等价写法（需 JDK 17 + Android SDK，并设置 `ANDROID_HOME`）：
```bash
./gradlew assembleDebug      # Windows 用 gradlew.bat assembleDebug
```

> 如果提示 `Could not find or load main class org.gradle.wrapper.GradleWrapperMain`，
> 按方式 A 末尾的 curl 命令补齐 `gradle/wrapper/gradle-wrapper.jar` 即可。

### 方式 C：只想马上有得用

可以直接装现成开源客户端（**不是本项目**）：
- [sjshb57/NewLcgR Releases](https://github.com/sjshb57/NewLcgR/releases)（v2.3.0，仍在维护）
- [fabricezhang/lcg Releases](https://github.com/fabricezhang/lcg/releases)（V1.8.7）

---

## 项目结构

```
52pojie-reader/
├── app/src/main/
│   ├── assets/
│   │   ├── boot.js          注入引导（UTF-8 Base64 解码，绕开站点 GBK 编码）
│   │   ├── enhance.js       阅读模式、图片查看、链接提取、缩放修复
│   │   └── enhance.css      优化排版 / 精简 / 夜间 / 沉浸阅读 全部样式
│   ├── java/cn/read52/app/
│   │   ├── MainActivity.java  WebView、导航、设置、收藏、大图、下载
│   │   ├── Prefs.java         本地偏好设置
│   │   ├── Store.java         收藏与阅读历史（JSON）
│   │   ├── JsBridge.java      JS → Java 通道
│   │   └── Assets.java        资源读取与 Base64
│   ├── res/                   布局、颜色、主题、自适应图标
│   └── AndroidManifest.xml
├── .github/workflows/build-apk.yml   云端自动打包
├── RESEARCH.md                      GitHub 开源项目调研 + 站点实测
└── build.gradle / settings.gradle / gradle/wrapper/
```

## 站点改版了怎么办

绝大多数情况只需改 `app/src/main/assets/enhance.css` 里的选择器；
如果楼层结构变了，改 `enhance.js` 里的这几个函数即可：

- `postNodes()` —— 找楼层容器（当前 `div[id^="post_"]`）
- `buildReader()` —— 从楼层里取标题、作者、时间、正文（当前 `.authi` / `td.t_f`）
- `R.extractLinks()` —— 要识别的网盘域名关键字

改完不需要动任何 Java 代码，重新打包即可。

## 已知限制

- 已完成真实页面注入测试（22/22 通过）、Java 语法解析、XML 与资源引用校验；**Gradle 真编译已在 GitHub Actions 跑通**（run 43455281674，全 9 步成功，产出 47 KB 的 debug APK）。构建环境固定为 JDK 17 / Android SDK 34 / Build-Tools 34.0.0 / AGP 8.5.2 / Gradle 8.7。
- 云端构建有两个已修过的坑，改动 CI 时别退回去：① 不能用 `android-actions/setup-android@v3`，它会去装已下线的 `tools` 包导致 sdkmanager 退出码 1，直接用 runner 预装的 SDK；② `gradlew` 的 JVM 参数不能带引号字面量，否则 Java 会把 `"-Xmx64m"` 当主类名。
- 沉浸阅读是**只读视图**，回复 / 点赞 / 下载附件请切回「优化排版」或「原版」（底部「阅读」按钮）。
- 夜间模式的原站页面用整体反色实现，少数带复杂背景图的板块可能颜色不够准确；沉浸阅读模式使用独立配色，效果更好。
- 自动签到**只提供任务页快捷入口**，不做任何自动化脚本。
- 不提供免登录绕过、内容抓取缓存、批量下载等能力。

## 合规声明

本项目仅供个人阅读使用，不绕过登录、权限、验证或任何访问控制，不缓存/转载/分发论坛内容。
所有数据仅存于用户本机。请遵守论坛规则与内容版权要求。

## 许可

MIT（见 [LICENSE](LICENSE)）。参考的开源项目版权归各自作者所有。
