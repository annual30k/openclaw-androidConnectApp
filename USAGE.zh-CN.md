# ClawLink Android 使用说明

本文面向 Android 版 ClawLink 用户，说明如何安装 App、登录 Relay、绑定电脑端 ClawConnect Agent，并在手机上使用远程对话、文件传输、模型、技能、任务和网关维护能力。

## 1. 产品定位

ClawLink Android 是 OpenClaw / PocketClaw 的移动控制端。它本身不直接运行模型，也不直接连接本机 OpenClaw Gateway，而是通过以下链路访问你的电脑：

```text
Android App -> ClawLink Relay Server -> ClawConnect Agent -> 本机 OpenClaw Gateway
```

其中：

- Android App：负责登录、配对、聊天、上传附件、管理模型/技能/任务、查看状态和发起维护操作。
- Relay Server：负责账号、配对、WebSocket 转发、状态同步、文件中转和权限校验。
- ClawConnect Agent：运行在电脑上，把本机 OpenClaw Gateway 接入 Relay。
- OpenClaw Gateway：运行实际的 OpenClaw 能力，例如聊天、模型、技能、任务和本机维护。

## 2. 准备工作

### 2.1 Android 设备

- Android 8.0 或更高版本。项目当前 `minSdk` 为 26。
- 可访问 Relay Server 的网络。
- 如果要扫码配对，需要允许相机权限。
- 如果要语音输入，需要允许麦克风权限。

### 2.2 电脑端

电脑上需要准备：

- Node.js 和 npm。
- 可用的 OpenClaw Gateway。
- 全局安装 `clawconnect-agent`：

```bash
npm install -g clawconnect-agent
```

安装后确认命令可用：

```bash
clawconnect --help
```

### 2.3 Relay Server

默认托管 Relay 地址是：

```text
https://clawlinks.cn
```

如果使用 Android 模拟器进行本地开发，App 会默认使用：

```text
http://10.0.2.2:8080
```

如果使用私有化 Relay，请确保手机可以访问你的 Relay 地址。登录页的“更多设置 / 私有化部署”可以填写自定义 Relay 地址。

## 3. 安装 Android App

### 3.1 直接安装 APK

仓库根目录已有调试包：

```text
clawlink-debug.apk
```

可以通过 adb 安装：

```bash
adb install -r ../clawlink-debug.apk
```

如果手机提示禁止安装未知来源应用，请在系统设置里允许当前安装来源。

### 3.2 从源码构建

在 Android 项目目录执行：

```bash
./gradlew assembleDebug
```

构建产物通常在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4. 首次使用流程

### 4.1 打开 App 并登录

1. 打开 ClawLink Android。
2. 首次进入会显示欢迎页，点击“开始使用”。
3. 在登录页输入邮箱和密码。
4. 如果还没有账号，切换到“注册”。
5. 托管 Relay 注册需要邮箱验证码；私有化 Relay 可按你的部署配置使用。

登录成功后，App 会进入主聊天页。如果当前账号还没有绑定主机，聊天页会提示先查看使用说明或添加网关。

### 4.2 在电脑上生成配对码

在电脑终端执行：

```bash
clawconnect pair
```

该命令会向 Relay 注册或刷新当前电脑网关，并输出二维码和一次性访问码。

如果终端二维码显示异常，改用纯配对码：

```bash
clawconnect pair --code-only
```

常用参数：

```bash
clawconnect pair --name "我的 MacBook"
clawconnect pair --server https://your-relay.example.com
```

说明：

- `--name` 控制这台主机在手机端显示的名称。
- `--server` 指定私有化 Relay 地址。
- 访问码是一次性使用的，过期或使用后需要重新生成。

### 4.3 在 Android App 绑定主机

在 App 中进入：

```text
设置 -> 网关 -> 添加 / 绑定
```

推荐方式：

1. 点击“开始扫码”。
2. 允许相机权限。
3. 扫描 `clawconnect pair` 输出的二维码。
4. App 会自动读取 Relay 地址、网关 ID 和配对码，并完成绑定。

备选方式：

1. 在“手动输入”区域填写配对码。
2. 如果使用私有化 Relay，确认 Relay 地址正确。
3. 点击“完成绑定”。

绑定完成后，App 会刷新网关列表并回到主界面。

### 4.4 启动电脑端 Agent

调试时可以前台运行：

```bash
clawconnect run
```

正式使用建议安装为后台服务：

```bash
clawconnect install
```

后台服务行为：

- macOS：使用 `launchd` 用户服务。
- Linux：优先使用 `systemd --user`，不支持时回退到 `nohup`。
- Windows：使用 Task Scheduler，登录时后台启动。

查看当前状态：

```bash
clawconnect status
```

重启后台服务：

```bash
clawconnect restart
```

停止后台服务：

```bash
clawconnect stop
```

重置本机配对：

```bash
clawconnect reset
```

## 5. 主界面与常用功能

### 5.1 对话

绑定并保持链路在线后，主界面就是聊天页。

可用操作：

- 输入文字消息并发送。
- 选择模型后继续对话。
- 使用斜杠命令或技能扩展。
- 切换会话或新建会话。
- 查看助手回复、工具调用过程和流式状态。

如果底部输入框提示“当前还没有绑定主机”或“当前链路未全通”，需要先检查 Relay、Agent 和 OpenClaw Gateway 的连接状态。

### 5.2 附件和文件

聊天输入区支持添加：

- 相册图片。
- 拍照图片。
- 本地文件。

附件会先上传到 Relay，再进入当前聊天会话。图片会尽量以预览形式显示，其他文件以文件卡片显示。

电脑端也可以主动发送文件到已配对会话：

```bash
clawconnect send-file ~/Pictures/demo.jpg
```

### 5.3 语音输入和语音回复

Android 端支持语音输入：

1. 在聊天输入区切换到语音模式。
2. 按住说话。
3. 松开发送，滑出后松开可取消。

语音回复需要电脑端 Agent 和技能配置支持。电脑端可通过环境变量启用默认 TTS：

```bash
OPENCLAW_TTS_ENABLED=1 clawconnect run
```

App 中可进入：

```text
设置 -> 语音设置
```

配置语音回复相关选项。

### 5.4 网关管理

进入：

```text
设置 -> 网关
```

可以：

- 查看所有已绑定主机。
- 切换当前使用的网关。
- 刷新网关列表。
- 添加新的主机绑定。
- 查看当前网关的链路状态。

状态含义：

- 已连接：Android App、Relay、电脑端 Agent、OpenClaw Gateway 链路可用。
- 连接中 / 部分可用：链路中有部分节点正在恢复或不可用。
- 离线：Relay、Agent 或 Gateway 不可达。
- 待绑定：账号下存在未完成绑定的网关信息。

### 5.5 模型

进入：

```text
设置 -> 模型
```

可以：

- 查看当前网关上报的模型列表。
- 按供应商分组浏览模型。
- 搜索模型。
- 设置 OpenClaw 全局默认模型。

设置默认模型时，主机可能会短暂重启并自动恢复。操作期间模型页会锁定，避免重复提交。

### 5.6 技能

进入：

```text
设置 -> 技能
```

可以查看当前网关上报的技能列表，并按技能能力进行启用、配置或查看命令。具体可用能力取决于电脑端 OpenClaw Gateway 上报的数据。

### 5.7 定时任务

进入：

```text
设置 -> 定时任务
```

可以：

- 查看活跃和暂停的任务。
- 创建新任务。
- 编辑任务标题、提示词和调度时间。
- 暂停或恢复任务。
- 删除任务。
- 查看下一次执行时间和上次结果。

任务能力依赖当前网关在线且聊天链路可用。

### 5.8 会话管理

进入：

```text
设置 -> 会话
```

可以管理当前网关的聊天会话，例如切换、清理或回到最近工作上下文。聊天页顶部也可切换当前会话。

### 5.9 像素办公室

进入：

```text
设置 -> 像素办公室
```

用于以可视化场景查看主机、员工和任务状态。它是状态展示入口，不替代网关列表和高级维护页。

## 6. 高级设置与维护

进入：

```text
设置 -> 高级设置
```

可用功能包括：

- 重启 Gateway：当前 Gateway 在线时使用，会短暂中断连接。
- 远程重启：当 Relay 和 Agent 在线但 OpenClaw Gateway 异常时，可尝试恢复主机侧 Gateway。
- Doctor Fix：执行主机侧修复流程，用于恢复常见网关问题。
- 查看日志：读取远端日志并搜索关键字。
- 备份管理：管理本地 `openclaw.json` 备份，最多保留多份恢复点。
- 显示 tools 调用过程：关闭后聊天页会隐藏部分工具过程，只保留更简化的结果视图。

维护类操作会触发短暂断连，App 会继续追踪恢复状态。

## 7. 私有化 Relay 使用

如果不用默认 `https://clawlinks.cn`，请保持 App、Agent 和 Relay 地址一致。

电脑端配对：

```bash
clawconnect pair --server https://your-relay.example.com
```

Android 登录页：

```text
更多设置 -> 私有化部署 -> Relay 地址
```

Android 配对页：

- 如果账号登录时使用的是私有化 Relay，配对页允许手动修改 Relay 地址。
- 如果账号使用默认托管 Relay，配对页会锁定默认地址，避免把主机绑到错误 Relay。

本地开发常见地址：

- Android 模拟器访问宿主机：`http://10.0.2.2:8080`
- 真机访问局域网 Relay：`http://电脑局域网 IP:8080`

## 8. 常见问题

### 8.1 登录失败

检查：

- 邮箱和密码是否正确。
- 密码至少 8 位。
- Relay 地址是否可访问。
- 私有化 Relay 的 `JWT_SECRET`、数据库和服务是否正常。
- 是否触发了登录频率限制，稍后再试。

### 8.2 扫码失败

检查：

- App 是否获得相机权限。
- 二维码是否来自当前要绑定的 `clawconnect pair` 输出。
- 二维码是否过期。
- 终端二维码显示是否完整。

如果仍失败，使用：

```bash
clawconnect pair --code-only
```

然后在 App 中手动输入配对码。

### 8.3 绑定失败

检查：

- 配对码是否过期或已被使用。
- App 登录账号和电脑端配对使用的 Relay 是否一致。
- 私有化 Relay 地址是否一致。
- 电脑端 `clawconnect pair --server <url>` 是否指向同一个 Relay。

必要时在电脑端重置后重新配对：

```bash
clawconnect reset
clawconnect pair
```

### 8.4 网关一直离线

按顺序检查：

1. Relay Server 是否启动并可访问。
2. 电脑端 Agent 是否运行：

```bash
clawconnect status
```

3. 如果服务未运行，启动或重启：

```bash
clawconnect install
clawconnect restart
```

4. 本机 OpenClaw Gateway 是否启动。
5. 如果 Gateway 需要 token 鉴权，执行：

```bash
clawconnect set-token
```

6. 回到 App 的“设置 -> 网关”刷新状态。

### 8.5 模型列表为空

可能原因：

- 当前链路未全通。
- OpenClaw Gateway 尚未完成启动。
- 主机模型供应商未配置。
- Agent 连接的是错误的 Gateway 地址。

处理方式：

- 在 App 模型页点击刷新。
- 在电脑端执行 `clawconnect status`。
- 检查 Agent 环境变量 `CLAWCONNECT_GATEWAY_URL`。
- 查看 App 的“高级设置 -> 查看日志”。

### 8.6 附件发送失败

检查：

- Relay 文件存储是否配置正常。
- 私有化 Relay 的对象存储或磁盘存储是否可写。
- 文件大小和网络是否正常。
- App 是否有读取相册、相机或文件的权限。

### 8.7 语音输入不可用

检查：

- App 是否获得麦克风权限。
- 当前设备是否支持录音。
- 当前会话是否正在处理回复；处理期间不能继续提交语音。

### 8.8 Android 模拟器无法访问本地 Relay

模拟器不能直接用 `localhost` 访问电脑本机服务。请使用：

```text
http://10.0.2.2:8080
```

App 在模拟器环境会自动把常见本地地址改写为 `10.0.2.2`。

## 9. 安全说明

- 配对码是一次性凭证，用完或过期后应重新生成。
- App 保存 access token 和 Relay 地址，用于恢复会话。
- 不要把配对码、access token 或私有 Relay 地址发给不可信的人。
- 私有化部署时请使用 HTTPS、稳定的 `JWT_SECRET` 和可靠的数据库备份。
- 远程重启、Doctor Fix、模型默认值修改等操作会影响电脑端 OpenClaw Gateway，请确认当前没有重要运行中任务后再执行。

## 10. 常用命令速查

```bash
# 安装电脑端 Agent
npm install -g clawconnect-agent

# 生成二维码和一次性配对码
clawconnect pair

# 只输出配对码
clawconnect pair --code-only

# 指定主机显示名
clawconnect pair --name "我的 MacBook"

# 指定私有化 Relay
clawconnect pair --server https://your-relay.example.com

# 前台运行，适合调试
clawconnect run

# 安装后台服务
clawconnect install

# 查看状态
clawconnect status

# 重启后台服务
clawconnect restart

# 停止后台服务
clawconnect stop

# 保存 Gateway token
clawconnect set-token

# 发送本地文件到手机会话
clawconnect send-file ~/Pictures/demo.jpg

# 升级 Agent
clawconnect update

# 清除本机配对并重新开始
clawconnect reset
```

