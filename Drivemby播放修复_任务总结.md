# CineIsle × Drivemby 百度网盘直连修复记录

## 一、任务目标

1. 在 Windows 本机完成 CineIsle Android APK 构建，不再依赖低内存云主机构建。
2. 清理 Redmi 真机上的全部旧 CineIsle 安装，只保留一个明确的新测试包。
3. 解决 Drivemby 百度网盘 HTTPS 直链进入播放器后长期显示“正在加载…”，以及可能自动退回播放页的问题。
4. 保留 Drivemby 的海报墙、刮削和百度网盘直连能力，不经过服务器中转视频流量。
5. 建立回归测试，构建 APK，并在 Android 17 真机上实际播放验证。

## 二、问题表现

- Drivemby 登录、海报墙和媒体信息均正常。
- 点击播放后进入播放器，但界面持续显示“正在加载…”。
- 部分情况下应用会退出播放器或重启。
- 真机日志中出现：

```text
fdsan: failed to exchange ownership of file descriptor
```

已知对照结果：同一媒体经过明文 HTTP 反向代理可以播放，直接使用百度 HTTPS 地址时容易失败。

## 三、根因定位

播放数据链如下：

```text
EmbyViewModel.launchSingle
  -> PlaybackInfo.MediaSources.Path / DirectStreamUrl
  -> RequiredHttpHeaders
  -> PlayerActivity / mpv native HTTPS
```

Drivemby 的 `PlaybackInfo` 返回百度网盘最终地址，典型特征为：

- 域名是 `d.pcs.baidu.com` 或 `*.baidupcs.com`；
- 或要求 `User-Agent: pan.baidu.com`；
- 请求不能自行添加 Referer，否则可能返回 HTTP 403（错误码 31326）。

原播放链把 HTTPS 百度直链交给 mpv/native 网络层。在 Redmi Android 17 / HyperOS 环境中，该路径会触发 native 文件描述符所有权异常，播放器无法完成初始化，最终表现为一直加载或应用被系统终止。

反向代理可以播放，是因为播放器面对的是普通 HTTP 地址，没有进入相同的 native TLS 路径。

## 四、解决方案

### 1. 新增百度直链路由策略

新增文件：

```text
app/src/main/java/app/marlboroadvance/mpvex/ui/browser/emby/EmbyDirectPlaybackRoute.kt
```

识别以下任一条件：

- `d.pcs.baidu.com`
- `*.baidupcs.com`
- 请求头 `User-Agent: pan.baidu.com`（不区分大小写）

识别成功后：

- 播放引擎强制切换为 `EngineKind.GSY`；
- GSY 内核强制使用 `GsyKernelKind.EXO`；
- 锁定内核，禁止失败后自动切回 IJK/native 路径；
- 播放请求头保留服务端要求的其它认证头，但删除 Referer、Range 和 Accept-Encoding，并规范为 `User-Agent: pan.baidu.com`。
- 播放列表起播前只解析首条：若首条是 Drivemby 百度直链，则安全降级为播放当前条目，不建立混合来源队列。原因是现有播放器列表只有一组全局请求头，强行连播会把 Cookie/UA 重放到其它主机；本版本优先保证当前视频稳定播放，暂不支持百度直链自动下一集。
- Emby `api_key` 只允许追加到与服务器同源的地址，第三方百度直链不再携带服务器令牌；诊断日志只记录请求头名称，不记录 Cookie、令牌或查询参数值。

普通 Emby 地址仍使用用户原本选择的播放引擎，不改变现有行为。

### 2. 修改播放 Intent 路由

修改：

```text
app/src/main/java/app/marlboroadvance/mpvex/ui/browser/emby/EmbyViewModel.kt
```

`launchSingle()` 获取最终播放 URL 后调用百度直链路由策略，并将以下参数传给 `GsyPlayerActivity`：

```text
gsy_force_kernel=EXO
gsy_lock_kernel=true
```

### 3. 禁止 Exo 自动回退到 IJK

修改：

```text
app/src/main/java/app/marlboroadvance/mpvex/ui/player/GsyPlayerActivity.kt
```

新增 `EXTRA_LOCK_KERNEL`。该值为 `true` 时，即使 Exo 返回播放错误，也不再自动切换到可能重新触发 native 崩溃的 IJK 内核；Activity 重建时继续保留锁定状态。用户手动切换到 MPV 以及 ISO 自动切换也会被拦截，避免重新进入 native HTTPS 崩溃路径。

### 4. 添加回归测试

新增：

```text
app/src/test/java/app/marlboroadvance/mpvex/ui/browser/emby/EmbyDirectPlaybackRouteTest.kt
```

覆盖以下场景：

1. 百度 PCS 域名强制走 GSY + Exo；
2. 域名变化但服务端要求 `pan.baidu.com` UA 时仍走 GSY + Exo；
3. 普通 Emby 地址保持原有 MPV 选择。

专项测试结果：

```text
BUILD SUCCESSFUL
```

## 五、构建步骤

### 构建环境

- 操作系统：Windows 11
- CPU：Ryzen 9 7945HX（16 核 32 线程）
- 内存：16 GB
- Android SDK：`D:\Software development\env\android-sdk`
- Android Platform：37.0
- Build Tools：37.0.0
- 目标 ABI：`arm64-v8a`

由于 Gradle 9 的单元测试 worker 在含中文的源码路径下可能报 `Could not execute test class`，测试及最终构建使用纯 ASCII 镜像目录：

```text
D:\ci-cineisle
```

构建任务：

```text
:app:assembleFdroidDebug -Pabi=arm64-v8a
```

结果：

```text
BUILD SUCCESSFUL in 1m 12s
44 actionable tasks
```

## 六、安装与真机验证

### 设备

- 型号：Redmi 25102RK69C
- Android：17
- API：37
- ABI：`arm64-v8a`

安装前已卸载手机上全部 CineIsle 包名变体；最终只安装：

```text
app.cineisle.player.localdebug
```

安装结果：

```text
Performing Streamed Install
Success
```

APK 信息：

```text
versionName = 1.6.7-fdroid-0
versionCode = 1402
签名方案 = APK Signature Scheme v2
ABI = arm64-v8a
```

### 播放验证

实际前台 Activity：

```text
app.marlboroadvance.mpvex.ui.player.GsyPlayerActivity
```

验证结果：

- 用户手动点击播放后可以正常观看；
- 播放器不再长期停留于“正在加载…”；
- 应用进程持续存活；
- 连续三次真机画面截图 SHA-256 均不同，证明视频画面持续推进；
- 播放观察期间日志未出现 `fdsan`、`Fatal signal` 或 `FATAL EXCEPTION`。

三帧验证哈希：

```text
b7bcfdb3195e6cef0e0c35fb63884d70f1fc2357cbd37015b74b2ddc2c7b1366
aed3ebda57f8f16a73be8d5edf7574cd8284ba7cbba893253926e6574fb52e64
cdf9f3081e364b04e7df3449a853c68b1bcfde9052aaa01dcf8ea3a67c49026e
```

## 七、最终产物

APK 文件：

```text
CineIsle-Drivemby-ExoFix-arm64-20261027.apk
```

文件大小：

```text
74,274,492 bytes
```

SHA-256：

```text
eab462c58a8868786c0c9725b01ab7af5d237cb38da374a0ee10bb961c2c5ab4
```

本地路径：

```text
D:\Software development\New Project\drivemby直连开发\CineIsle-Drivemby-ExoFix-arm64-20261027.apk
```

## 八、测试说明

- 新增的 Drivemby 百度直链专项回归测试全部通过。
- APK 构建、V2 签名校验、ADB 安装和真机播放验收全部通过。
- 项目完整单元测试共执行 379 项，其中原有 `MediaSourcePreferenceTest` 有 4 项失败；本次没有修改其相关实现，专项测试及真机验收不受影响。

## 九、最终结果

任务已完成：CineIsle 可以直接登录 Drivemby，在 Redmi Android 17 真机上使用百度网盘 HTTPS 直链正常播放；视频流量仍由手机直连百度网盘，不经过自建服务器中转。
