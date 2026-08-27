# 微信消息接入 Adapter 规范

消息处理引擎 **不依赖** 任何特定 IM 客户端。接入层实现 `WeChatAdapter` 接口即可替换。

## 接口

```js
class WeChatAdapter {
  async start(onMessage) {}   // 开始监听，新消息回调
  async stop() {}             // 停止监听
  async send(payload) {}      // 发送回复 { conversationId, text, to }
  async health() {}           // { ok, detail }
  get id() {}                 // 适配器标识
}
```

## 入站消息标准结构

```json
{
  "id": "msg_20260827_001",
  "conversationId": "c_zhangsan",
  "conversationName": "张三",
  "conversationType": "contact",
  "senderId": "zhangsan",
  "senderName": "张三",
  "content": "你好，在吗？",
  "contentType": "text",
  "timestamp": 1777286400000,
  "isSelf": false,
  "raw": {}
}
```

`conversationType`: `contact` | `group`  
`isSelf`: 自己发出的消息必须为 `true`，引擎会跳过自动回复。

## 内置适配器

### simulator

软件内置模拟会话（同事、客户、群聊）。用于功能验证，不接触微信。

### manual

设置页 / 模拟器中手动录入一条消息，等价于「用户明确授权处理的内容」。

### filewatch

监视目录（默认 `~/Library/Application Support/WeChatAIAssistant/inbox.jsonl`）。

每一行一个 JSON 消息。由 **用户授权的、合法的** 上游工具写入，例如：

- 官方开放平台回调服务把消息转写到该文件
- 用户自己用快捷指令 / 邮件规则导出
- 企业微信/已授权机器人的 webhook 落盘

本仓库 **不包含** 对微信客户端的注入、hook、内存扫描或协议破解。

发送结果写入 `outbox.jsonl`，便于上游合法通道真正发出。

### 预留 wechat-official

`adapters.js` / Android `WeChatAdapter` 中有空实现与接入注释。仅在获得官方 API / 用户明确授权后填充，禁止用破解方式实现。

### Android 通知监听 / 辅助功能

| Adapter | 类 | 说明 |
|---|---|---|
| `simulator` | `SimulatorAdapter` | 默认演示 |
| `notification` | `NotificationAdapter` + `WeChatNotificationListener` | 用户授权通知读取 |
| `accessibility_send` | `WeChatAccessibilityService` | 用户授权后辅助填入发送 |

自动发送受微信 UI 影响，失败时应回退到人工审核。

## 指纹去重

引擎使用：

```
hash(conversationId + '|' + senderId + '|' + timestamp + '|' + content)
```

同一条消息只入队一次。
