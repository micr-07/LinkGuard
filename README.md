<div align="center">

# LinkGuard · 链接守卫

**别的应用想打开链接时，先问你一句。**
*When another app wants to open a link, it asks you first.*

[![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Material 3](https://img.shields.io/badge/Material%203-6750A4?style=for-the-badge)](https://m3.material.io)
[![No Gradle](https://img.shields.io/badge/No%20Gradle-%E7%9B%B4%E9%A9%B1%20SDK-lightgrey?style=for-the-badge)](#%E6%9E%84%E5%BB%BA)
[![Bilingual](https://img.shields.io/badge/%E4%B8%AD%E8%8B%B1-EN%20%2F%20ZH-0B57D0?style=for-the-badge)](#%E8%AF%AD%E8%A8%80%E5%8C%85)

[**下载 APK**](../../releases/latest) · [官方网站](https://micger.top/) · [问题反馈](../../issues)

</div>

<div align="center">
  <img src="screenshots/1-主界面.png" width="30%" />
  <img src="screenshots/2-拦截弹窗.png" width="30%" />
  <img src="screenshots/3-更多.png" width="30%" />
</div>

---

> [!NOTE]
> 安卓上点一个链接，经常被直接甩进某个 App 的内置浏览器或广告页，你连跳去了哪儿都没看清。
> LinkGuard 注册成系统默认浏览器后，**所有 `http` / `https` 跳转都先经过它**，由你决定这一跳要不要放行。

> [!IMPORTANT]
> **本应用不收集任何数据。** 联网只用于拦截弹窗里的网页实时预览；黑名单、白名单、浏览记录、全部配置
> 都只存在本机 `SharedPreferences`，不上传、无统计、无账号。

---

## 拦截弹窗

被拉起时**不显示应用界面，只弹窗**：

```text
打开这个链接？                    (⋯)
来自「Shell」的请求

┌────────────────────────────────┐
│ 🌐  baidu.com                  │
│     https://www.baidu.com      │
│ ╭────────────────────────────╮ │  ← 实时预览
│ │   [真实渲染的网页缩略图]     │ │     只读，点不动
│ ╰────────────────────────────╯ │
└────────────────────────────────┘
                    拒绝   ( 允许 )
```

| 按钮 | 作用 |
| :--- | :--- |
| **拒绝** | 不打开，记一条日志 |
| **允许** | 用默认浏览器打开 |
| **⋯** | 展开「更多」面板 |

## 「更多」面板

顶部的小白条**可以上下拖**。收起档只露最常用的处置动作，展开档放出全部设置。

| 记住选择 | 这个链接 | 隐私 |
| :--- | :--- | :--- |
| ✓ 总是允许（白名单） | 📋 复制链接 | 隐藏网址详情 |
| ⊘ 总是拦截（黑名单） | | 剥离追踪参数 |
| 📵 屏蔽「某应用」（红） | | 预览不加载第三方资源 |
| | | 退出时清除记录 |
| | | 无痕模式 |

> [!TIP]
> 「总是允许」就是加白名单，「总是拦截」就是加黑名单 —— 所以没有单独的「加入黑名单 / 白名单」按钮。

底部单独一行：**打开应用主页面**

---

## 功能

| 功能 | 说明 |
| :--- | :--- |
| **黑名单** | 匹配的网址直接拦下，只留一条系统小字提示 |
| **白名单** | 匹配的网址直接放行，可指定用哪个浏览器 |
| **屏蔽应用** | 某个应用拉起的链接全部拦下，最强一级 |
| **浏览记录** | 放行 / 拦截 / 拒绝都有记录，可搜索 |
| **实时预览** | 打开前先看网页真容；WebView 渲染但完全不可操作 |
| **预览缩放** | 25–200% 任意输入，默认 60% |
| **7 套配色** | 默认蓝 / 紫罗兰 / 青碧 / 森林绿 / 暖橙 / 玫红 / 深色 |
| **中英双语** | 语言包独立，加语言只要丢一个 JSON |
| **隐私开关** | 隐藏完整 URL、剥离 utm_ 追踪参数、预览挡掉第三方资源、无痕模式 |

---

## 安装

从 [Releases](../../releases/latest) 下载 APK，安装后在首页点「设为系统默认浏览器」。

> [!WARNING]
> **Android 10+** — 直接弹出系统授权框，点「始终」即可。
>
> **Android 9 及以下** — 会跳到系统设置，需手动进入「默认应用 → 浏览器」选择 LinkGuard。

### 首次使用的便利

还没设过默认浏览器时，**第一次在弹窗里挑的浏览器会自动成为默认**，不用再跑一趟设置页。

---

## 语言包

文案不在 `res/values-xx` 里，而在 `assets/lang/<code>.json`：

```text
assets/lang/
├── zh.json   213 条
└── en.json   213 条
```

加一种语言 = **往这个目录丢一个 JSON**，不用改资源、不用碰代码。占位符用 `{0}` `{1}`。

<details>
<summary><b>怎么加一门新语言</b></summary>

1. 复制 `assets/lang/en.json`，改名成 `fr.json`
2. 把每个 value 翻成法语，**保留 `{0}` `{1}` 占位符与 `\n` 数量**
3. 在 `Lang.java` 的 `CODES` 数组里加上 `"fr"`
4. 在 `SELF_NAMES` 里加上 `"Français"`

</details>

---

## 设计

界面按 **Material Design 3** 手工实现（没有引入 AndroidX）：

- 形状标度 4 / 8 / 12 / 16 / 20 / 28 / 32 / 48 / full
- 圆角**同心**：外圈 28 → 详情卡 20 → 预览框 14，每层缝都是 8dp 左右，圆心在同一条角平分线上
- 颜色按 M3 中性色调阶梯生成：底色 → 卡片 → 嵌套容器，每级 7–9 个明度点
- 标题用 **Afacad Flux** 渲染成矢量图，随主题着色

---

## 构建

没有用 Gradle，直接驱动 SDK 工具链：

```powershell
.\build.ps1
```

<details>
<summary><b>需要什么 / 九步流程</b></summary>

**依赖**：Android SDK build-tools、JDK（`javac`/`jar`）、Python 3（PIL）

```text
[1/9] tools/make_icon.py    生成 5 档密度的启动图标
[2/9] aapt2 compile --dir res
[3/9] aapt2 link            链接成未签名 APK（-A assets 打包语言包）
[4/9] javac                 -source 8 -target 8 -bootclasspath android.jar
[5/9] jar cf -> d8          先打 jar 再 dex（避免命令行超长）
[6/9] tools/adddex.py       用 Python zipfile 注入 classes.dex
[7/9] zipalign
[8/9] apksigner
```

> 第 6 步是必需的：`aapt2 link` 的产物里没有 dex，要自己塞进去，
> 而且必须保持 `resources.arsc` **不压缩（STORED）**，否则系统读不到资源。

</details>

---

## 兼容性

| | |
| :--- | :--- |
| minSdk | **21**（Android 5.0） |
| targetSdk | 36 |
| 实测 | Android 15 (API 35) · Android 7.1.2 (API 25) |
| 依赖 | 无 AndroidX、无 Gradle、无第三方库 |

---

## 隐私

- 联网**只用于拦截弹窗里的网页实时预览**，除此之外没有任何网络行为
- 黑名单、白名单、浏览记录、全部配置都只存在本机 `SharedPreferences`
- 不上传任何数据，没有统计、没有账号

---

<div align="center">

**[⬆ 回到顶部](#linkguard--链接守卫)**

作者 [micr-07](https://github.com/micr-07) · DeepSeek · [dogoolge@126.com](mailto:dogoolge@126.com)

</div>
