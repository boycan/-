/**
 * 消息接入层：可替换 Adapter。
 * 不包含任何微信破解 / 注入实现。
 */
(function (global) {
  "use strict";
  const U = global.WAA.util;

  class SimulatorAdapter {
    constructor() {
      this.id = "simulator";
      this._onMessage = null;
      this._timer = null;
      this._i = 0;
      this.demos = [
        { conversationId: "c_zhangsan", conversationName: "张三", conversationType: "contact", content: "你好，在吗？" },
        { conversationId: "c_lisi", conversationName: "李四", conversationType: "contact", content: "明天会议几点开始？" },
        { conversationId: "g_project", conversationName: "项目沟通群", conversationType: "group", content: "报价大概多少钱？" }
      ];
      this.outbox = [];
    }
    async start(onMessage) {
      this._onMessage = onMessage;
      const self = this;
      this._timer = setInterval(function () {
        if (!self._onMessage) return;
        const d = self.demos[self._i % self.demos.length];
        self._i += 1;
        self._onMessage(self._wrap(d));
      }, 15000);
    }
    async stop() {
      if (this._timer) clearInterval(this._timer);
      this._timer = null;
      this._onMessage = null;
    }
    async send(payload) {
      this.outbox.push({ ts: U.now(), payload: payload });
      return { ok: true };
    }
    async health() { return { ok: true, detail: "模拟消息源就绪" }; }
    inject(partial) {
      if (!this._onMessage) return;
      this._onMessage(this._wrap(partial));
    }
    _wrap(d) {
      const msg = {
        id: U.uid("msg"),
        conversationId: d.conversationId || ("c_" + U.hash(d.conversationName || "unknown")),
        conversationName: d.conversationName || "未知",
        conversationType: d.conversationType || "contact",
        senderId: d.senderId || d.conversationId || "sender",
        senderName: d.senderName || d.conversationName || "对方",
        content: d.content || "",
        contentType: "text",
        timestamp: d.timestamp || U.now(),
        isSelf: !!d.isSelf,
        raw: d.raw || {}
      };
      msg.fingerprint = U.fingerprint(msg);
      return msg;
    }
  }

  class ManualAdapter {
    constructor() {
      this.id = "manual";
      this._onMessage = null;
    }
    async start(onMessage) { this._onMessage = onMessage; }
    async stop() { this._onMessage = null; }
    async send() { return { ok: true, skipped: true }; }
    async health() { return { ok: true, detail: "手动录入" }; }
    push(partial) {
      if (!this._onMessage) return;
      const msg = Object.assign({
        id: U.uid("msg"),
        conversationId: "c_manual",
        conversationName: "手动会话",
        conversationType: "contact",
        senderId: "manual",
        senderName: "手动",
        content: "",
        contentType: "text",
        timestamp: U.now(),
        isSelf: false
      }, partial || {});
      msg.fingerprint = U.fingerprint(msg);
      this._onMessage(msg);
    }
  }

  class FileWatchAdapter {
    constructor() {
      this.id = "filewatch";
      this._onMessage = null;
      this._timer = null;
      this._offset = 0;
    }
    async start(onMessage) {
      this._onMessage = onMessage;
      const self = this;
      this._timer = setInterval(async function () {
        if (!U.Native.available()) return;
        try {
          const res = await U.Native.call("readInbox", { offset: self._offset });
          if (!res || !res.lines) return;
          (res.lines || []).forEach(function (line) {
            try {
              const msg = JSON.parse(line);
              msg.fingerprint = msg.fingerprint || U.fingerprint(msg);
              self._onMessage && self._onMessage(msg);
            } catch (e) {}
          });
          if (typeof res.offset === "number") self._offset = res.offset;
        } catch (e) {}
      }, 1200);
    }
    async stop() {
      if (this._timer) clearInterval(this._timer);
      this._timer = null;
      this._onMessage = null;
    }
    async send(payload) {
      if (U.Native.available()) {
        await U.Native.call("appendOutbox", payload);
      }
      return { ok: true };
    }
    async health() {
      return { ok: true, detail: "FileWatch（需原生桥）" };
    }
  }

  class OfficialPlaceholderAdapter {
    constructor() { this.id = "wechat-official"; }
    async start() { throw new Error("未接入官方渠道：禁止用破解方式实现"); }
    async stop() {}
    async send() { return { ok: false, error: "未实现" }; }
    async health() { return { ok: false, detail: "预留官方接口，未实现" }; }
  }

  function createAdapter(type) {
    if (type === "manual") return new ManualAdapter();
    if (type === "filewatch") return new FileWatchAdapter();
    if (type === "wechat-official") return new OfficialPlaceholderAdapter();
    return new SimulatorAdapter();
  }

  global.WAA.Adapters = {
    SimulatorAdapter: SimulatorAdapter,
    ManualAdapter: ManualAdapter,
    FileWatchAdapter: FileWatchAdapter,
    OfficialPlaceholderAdapter: OfficialPlaceholderAdapter,
    createAdapter: createAdapter
  };
})(window);
