# 微信 AI 智能自动回复助手（Android）

真正可安装到 Android 手机的 AI 智能回复助手工程。  
**不破解微信、不注入、不窃取凭证**；优先使用系统通知监听 / 辅助功能等用户主动授权能力。

## 当前能力

- 首页：运行状态、模型状态、今日统计、启动/暂停/停止
- 模拟消息源：完整跑通 检测 → 规则 → 上下文 → AI → 审核/发送
- 通知监听 Adapter：读取微信通知（需授权）
- 辅助功能发送 Adapter：尽力填入并发送（受微信 UI 版本影响，可能失败）
- 默认 **辅助回复（人工确认）**，避免误发
- 免费精选云端模型：DeepSeek（默认）/ Gemini / 硅基流动 / OpenRouter，一键切换
- 可选 Ollama 本地大模型、自定义 OpenAI Compatible
- 无 Key / 无网时自动离线模板兜底
- 规则：全局/联系人/群聊/关键词/工作时间/黑白名单
- 防护：去重、冷却、每分钟/每日上限、异常自动暂停、跳过自己的消息
- 前台服务 + 通知栏状态 + 开机恢复 + 电池优化引导

## 不装 Android Studio：云端打包

推荐走仓库根目录的 GitHub Actions，说明见：

[`../docs/CLOUD_BUILD.md`](../docs/CLOUD_BUILD.md)

推送后到 GitHub → Actions → 下载 `WeChatAIAssistant-debug-apk`。

## 打开工程（可选，本地编译）

1. 安装 [Android Studio](https://developer.android.com/studio)（带 JDK 17）
2. `Open` 本目录：

```text
wechat-ai-assistant/android
```

3. 等待 Gradle Sync
4. 连接手机或启动模拟器，Run `app`

## 配置免费模型（重要）

1. 打开 App → **设置**
2. 选择精选模型（默认 DeepSeek）
3. 点击「打开免费申请页」获取 API Key
4. 将 Key 填入（仅保存在本机，源码不含任何密钥）
5. 首页启动后发送模拟消息验证

| 精选 | 默认模型 | 申请 |
|---|---|---|
| DeepSeek | `deepseek-chat` | https://platform.deepseek.com/ |
| Gemini | `gemini-2.0-flash` | https://aistudio.google.com/apikey |
| 硅基流动 | `Qwen/Qwen2.5-7B-Instruct` | https://cloud.siliconflow.cn/ |
| OpenRouter | `deepseek/...:free` | https://openrouter.ai/ |

## 推荐验证路径（不碰微信）

1. 首次启动阅读权限说明 → 继续
2. 设置中填入 DeepSeek Key
3. 首页点 **启动**
4. 点「模拟：你好，在吗？」
5. 到 **审核** 页查看 AI 回复 → 可编辑 / 重新生成 / 发送 / 忽略
6. 在 **规则** 中切换「自动回复 / 辅助回复 / 关闭」

## 微信接入（合法边界）

| Adapter | 作用 | 说明 |
|---|---|---|
| `simulator` | 模拟消息 | 默认，开发演示 |
| `notification` | NotificationListener | 用户授权后读取微信通知 |
| `accessibility_send` | AccessibilityService | 用户授权后辅助填入发送 |

自动发送在真机上受系统与微信界面层级影响，**不能保证 100% 成功**。  
产品默认应使用「辅助回复」。若发送失败，任务会回到审核队列。

## 架构

```text
App UI (Jetpack Compose)
  ↓
MessageEngine
  ↓
RulesEngine / ContextManager / Guard
  ↓
AiRouter (DeepSeek | Gemini | SiliconFlow | OpenRouter | Ollama | Offline)
  ↓
WeChatAdapter (Simulator | Notification | Accessibility)
  ↓
Room + DataStore + ForegroundService + Logger
```

## 合规

- 所有敏感能力需用户主动授权
- API Key 仅本地保存，应用不上传、源码不含 Key
- 不读取未授权应用数据
- 不绕过微信安全机制
- 仅处理用户本人有权访问的会话内容
