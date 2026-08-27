# 微信 AI 智能自动回复助手

面向 **Android 手机** 的 AI 智能回复助手（同时保留可在浏览器演示的 Web 原型）。

> **合规**：不破解微信、不注入客户端、不窃取凭证。  
> 微信接入为独立 Adapter；默认用模拟消息源跑通全流程，真机可通过用户授权的通知监听读取消息。  
> 默认回复模式为 **辅助回复（人工确认）**。  
> 默认 AI 为 **DeepSeek 等免费精选云端模型**（需用户自填免费 API Key，源码不含密钥）。

## 目录

```text
wechat-ai-assistant/
  android/          ← 可安装的 Android 工程（Kotlin + Compose）【主目标】
  src/              ← Web/桌面演示原型（浏览器可打开）
  docs/ADAPTER.md   ← 消息接入规范
  README.md
```

## Android（推荐）

### 不装 Android Studio：云端打包 APK（推荐）

已配置 GitHub Actions。推送到 GitHub 后可直接下载 APK 安装到手机。

完整步骤见：**[`docs/CLOUD_BUILD.md`](docs/CLOUD_BUILD.md)**

### 或用 Android Studio 本地编译

```bash
# 用 Android Studio 打开
open wechat-ai-assistant/android
# 或 File → Open → 选择 android 目录
```

详细说明见 [`android/README.md`](android/README.md)。

### 免费精选模型

| 模型 | 说明 |
|---|---|
| **DeepSeek**（默认） | 中文强，免费额度/高性价比 |
| Gemini | Google 免费额度 |
| 硅基流动 | 国内可达，Qwen 等开源模型 |
| OpenRouter | 可选 `:free` 免费路由 |
| Ollama | 本机本地大模型 |
| 离线兜底 | 无 Key/无网时的模板回复 |

验证路径：

1. 安装到手机 / 模拟器  
2. 设置中选择 DeepSeek，填入免费 API Key  
3. 首页 **启动** → 点模拟消息  
4. **审核** 页确认 AI 回复  

## Web 演示原型

浏览器打开：

```bash
open wechat-ai-assistant/src/index.html
```

可完整体验：模拟消息 → 规则 → 免费精选 AI → 人工审核。

## 架构

```text
App UI
  → 消息接入 Adapter（simulator / notification / accessibility）
  → MessageEngine（去重 / 合并 / 防护）
  → RulesEngine
  → ContextManager
  → AiRouter（deepseek / gemini / siliconflow / openrouter / ollama / offline）
  → 回复执行（自动 / 辅助确认）
  → 日志
```

## 重要限制（请知悉）

- 微信没有向普通第三方 App 提供私聊自动发送官方 API。  
- 通知监听只能拿到通知里暴露的文本，不等于完整聊天记录。  
- 辅助功能自动点击发送依赖微信当前 UI 结构，**不能保证稳定成功**。  
- 云端免费模型需要你自己申请 API Key；应用不会内置或上传密钥。  
- 因此本项目把「接入层」做成 Adapter，并优先保证：  
  **消息模拟 → AI 分析 → 回复生成 → 回复审核 → 发送接口** 全链路可用。
