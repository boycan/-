/**
 * 核心层：工具、事件总线、日志、IndexedDB、配置。
 * 不依赖微信客户端，也不包含任何密钥。
 */
(function (global) {
  "use strict";

  const APP_ID = "wechat-ai-assistant";
  const DB_NAME = "WeChatAIAssistant";
  const DB_VERSION = 1;

  const DEFAULT_SETTINGS = {
    version: 1,
    runtime: {
      status: "stopped",
      lastStartedAt: null,
      lastStoppedAt: null,
      error: null
    },
    ai: {
      provider: "deepseek",
      currentModel: "deepseek-chat",
      modelPath: "https://api.deepseek.com/v1",
      modelStatus: "need_key",
      style: "natural",
      maxReplyLength: 80,
      contextSize: 8,
      temperature: 0.7,
      systemPrompt: "你是微信聊天助手，用自然口语回复，像真人一样简短、礼貌、有温度，不要机械套话，不要暴露自己是 AI。",
      offlineFallbackEnabled: true,
      presets: {
        deepseek: { baseUrl: "https://api.deepseek.com/v1", model: "deepseek-chat", apiKey: "" },
        gemini: { baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai", model: "gemini-2.0-flash", apiKey: "" },
        siliconflow: { baseUrl: "https://api.siliconflow.cn/v1", model: "Qwen/Qwen2.5-7B-Instruct", apiKey: "" },
        openrouter: { baseUrl: "https://openrouter.ai/api/v1", model: "deepseek/deepseek-chat-v3-0324:free", apiKey: "" }
      },
      ollama: {
        baseUrl: "http://127.0.0.1:11434",
        model: "qwen2.5:7b",
        timeoutMs: 20000
      },
      openai: {
        baseUrl: "https://api.deepseek.com/v1",
        apiKey: "",
        model: "deepseek-chat",
        timeoutMs: 45000
      }
    },
    reply: {
      enabled: true,
      mode: "review",
      cooldownSeconds: 20,
      maxPerMinute: 8,
      maxPerDay: 200,
      mergeWindowSeconds: 8,
      workHoursEnabled: false,
      workStart: "09:00",
      workEnd: "18:00",
      workHoursPolicy: "work",
      offHoursPolicy: "off",
      globalEnabled: true,
      useContext: true,
      autoPauseOnError: true,
      errorPauseThreshold: 3
    },
    lists: {
      blacklist: [],
      whitelist: [],
      whitelistOnly: false,
      contacts: [],
      groups: [],
      keywords: [
        { keyword: "在吗", enabled: true },
        { keyword: "你好", enabled: true }
      ]
    },
    adapter: {
      type: "simulator",
      filewatchPath: "",
      pollMs: 1200
    }
  };

  function now() { return Date.now(); }
  function uid(prefix) {
    return (prefix || "id") + "_" + now().toString(36) + "_" + Math.random().toString(36).slice(2, 8);
  }
  function clone(v) { return JSON.parse(JSON.stringify(v)); }
  function pad(n) { return String(n).padStart(2, "0"); }
  function formatTime(ts) {
    if (!ts) return "-";
    const d = new Date(ts);
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) + " " +
      pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
  }
  function formatClock(ts) {
    const d = new Date(ts || Date.now());
    return pad(d.getHours()) + ":" + pad(d.getMinutes());
  }
  function todayKey(ts) {
    const d = new Date(ts || Date.now());
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
  }
  function escapeHtml(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function hash(str) {
    let h = 5381;
    const s = String(str);
    for (let i = 0; i < s.length; i++) h = ((h << 5) + h) ^ s.charCodeAt(i);
    return (h >>> 0).toString(16);
  }
  function fingerprint(msg) {
    return hash([msg.conversationId, msg.senderId, msg.timestamp, msg.content].join("|"));
  }
  function inWorkHours(settings, ts) {
    const d = new Date(ts || Date.now());
    const cur = pad(d.getHours()) + ":" + pad(d.getMinutes());
    const start = settings.reply.workStart || "09:00";
    const end = settings.reply.workEnd || "18:00";
    if (start <= end) return cur >= start && cur <= end;
    return cur >= start || cur <= end;
  }
  function truncate(s, n) {
    s = String(s || "");
    return s.length <= n ? s : s.slice(0, n);
  }
  function parseList(text) {
    return String(text || "").split(/[\n,，;；]/).map(function (x) { return x.trim(); }).filter(Boolean);
  }

  class EventBus {
    constructor() { this.map = {}; }
    on(ev, fn) { (this.map[ev] || (this.map[ev] = [])).push(fn); return function () { this.off(ev, fn); }.bind(this); }
    off(ev, fn) { this.map[ev] = (this.map[ev] || []).filter(function (x) { return x !== fn; }); }
    emit(ev, payload) {
      (this.map[ev] || []).slice().forEach(function (fn) {
        try { fn(payload); } catch (e) { console.error("event handler", ev, e); }
      });
    }
  }

  class Store {
    constructor() {
      this.db = null;
    }
    open() {
      const self = this;
      return new Promise(function (resolve, reject) {
        const req = indexedDB.open(DB_NAME, DB_VERSION);
        req.onupgradeneeded = function (e) {
          const db = e.target.result;
          if (!db.objectStoreNames.contains("conversations")) {
            const c = db.createObjectStore("conversations", { keyPath: "id" });
            c.createIndex("updatedAt", "updatedAt");
          }
          if (!db.objectStoreNames.contains("messages")) {
            const m = db.createObjectStore("messages", { keyPath: "id" });
            m.createIndex("conversationId", "conversationId");
            m.createIndex("timestamp", "timestamp");
            m.createIndex("fingerprint", "fingerprint", { unique: false });
          }
          if (!db.objectStoreNames.contains("jobs")) {
            const j = db.createObjectStore("jobs", { keyPath: "id" });
            j.createIndex("status", "status");
            j.createIndex("createdAt", "createdAt");
          }
          if (!db.objectStoreNames.contains("logs")) {
            const l = db.createObjectStore("logs", { keyPath: "id" });
            l.createIndex("ts", "ts");
            l.createIndex("level", "level");
            l.createIndex("category", "category");
          }
          if (!db.objectStoreNames.contains("kv")) {
            db.createObjectStore("kv", { keyPath: "key" });
          }
          if (!db.objectStoreNames.contains("counters")) {
            db.createObjectStore("counters", { keyPath: "key" });
          }
        };
        req.onsuccess = function () { self.db = req.result; resolve(self.db); };
        req.onerror = function () { reject(req.error); };
      });
    }
    tx(name, mode) { return this.db.transaction(name, mode || "readonly").objectStore(name); }
    put(store, value) {
      const self = this;
      return new Promise(function (resolve, reject) {
        const r = self.tx(store, "readwrite").put(value);
        r.onsuccess = function () { resolve(value); };
        r.onerror = function () { reject(r.error); };
      });
    }
    get(store, key) {
      const self = this;
      return new Promise(function (resolve, reject) {
        const r = self.tx(store).get(key);
        r.onsuccess = function () { resolve(r.result || null); };
        r.onerror = function () { reject(r.error); };
      });
    }
    delete(store, key) {
      const self = this;
      return new Promise(function (resolve, reject) {
        const r = self.tx(store, "readwrite").delete(key);
        r.onsuccess = function () { resolve(); };
        r.onerror = function () { reject(r.error); };
      });
    }
    all(store) {
      const self = this;
      return new Promise(function (resolve, reject) {
        const r = self.tx(store).getAll();
        r.onsuccess = function () { resolve(r.result || []); };
        r.onerror = function () { reject(r.error); };
      });
    }
    byIndex(store, index, value) {
      const self = this;
      return new Promise(function (resolve, reject) {
        const r = self.tx(store).index(index).getAll(value);
        r.onsuccess = function () { resolve(r.result || []); };
        r.onerror = function () { reject(r.error); };
      });
    }
  }

  class Logger {
    constructor(bus, store) {
      this.bus = bus;
      this.store = store;
    }
    async write(level, category, message, extra) {
      const rec = {
        id: uid("log"),
        ts: now(),
        level: level,
        category: category,
        message: String(message || ""),
        extra: extra || null
      };
      try { await this.store.put("logs", rec); } catch (e) { console.error("log write failed", e); }
      this.bus.emit("log", rec);
      return rec;
    }
    info(cat, msg, extra) { return this.write("info", cat, msg, extra); }
    warn(cat, msg, extra) { return this.write("warn", cat, msg, extra); }
    error(cat, msg, extra) { return this.write("error", cat, msg, extra); }
    async query(filter) {
      const all = await this.store.all("logs");
      filter = filter || {};
      return all.filter(function (x) {
        if (filter.level && x.level !== filter.level) return false;
        if (filter.category && x.category !== filter.category) return false;
        if (filter.from && x.ts < filter.from) return false;
        if (filter.to && x.ts > filter.to) return false;
        if (filter.q && (x.message + JSON.stringify(x.extra || {})).indexOf(filter.q) < 0) return false;
        return true;
      }).sort(function (a, b) { return b.ts - a.ts; });
    }
  }

  class Config {
    constructor(store, bus) {
      this.store = store;
      this.bus = bus;
      this.value = clone(DEFAULT_SETTINGS);
    }
    async load() {
      const rec = await this.store.get("kv", "settings");
      if (rec && rec.value) this.value = this.merge(clone(DEFAULT_SETTINGS), rec.value);
      // 旧版 builtin / 本地占位 → 统一默认 DeepSeek
      if (!this.value.ai) this.value.ai = clone(DEFAULT_SETTINGS.ai);
      if (!this.value.ai.provider || this.value.ai.provider === "builtin") {
        this.value.ai.provider = "deepseek";
      }
      if (!this.value.ai.openai) this.value.ai.openai = clone(DEFAULT_SETTINGS.ai.openai);
      const base = this.value.ai.openai.baseUrl || "";
      const model = this.value.ai.openai.model || "";
      if (!base || /127\.0\.0\.1|localhost|local:\/\//.test(base)) {
        this.value.ai.openai.baseUrl = "https://api.deepseek.com/v1";
      }
      if (!model || model === "local-model" || /^builtin/.test(model)) {
        this.value.ai.openai.model = "deepseek-chat";
      }
      this.value.ai.currentModel = this.value.ai.openai.model;
      this.value.ai.modelPath = this.value.ai.openai.baseUrl;
      return this.value;
    }
    merge(base, over) {
      if (!over || typeof over !== "object") return base;
      Object.keys(over).forEach(function (k) {
        if (over[k] && typeof over[k] === "object" && !Array.isArray(over[k]) && typeof base[k] === "object") {
          base[k] = this.merge(base[k] || {}, over[k]);
        } else base[k] = over[k];
      }.bind(this));
      return base;
    }
    async save(patch) {
      if (patch) this.value = this.merge(this.value, patch);
      await this.store.put("kv", { key: "settings", value: clone(this.value), updatedAt: now() });
      this.bus.emit("settings", this.value);
      return this.value;
    }
    get() { return this.value; }
  }

  async function withTimeout(promise, ms, label) {
    let t;
    const timeout = new Promise(function (_, reject) {
      t = setTimeout(function () { reject(new Error((label || "操作") + "超时（" + ms + "ms）")); }, ms);
    });
    try { return await Promise.race([promise, timeout]); }
    finally { clearTimeout(t); }
  }

  async function retry(fn, times, delay, logger, label) {
    let last;
    for (let i = 1; i <= times; i++) {
      try { return await fn(i); }
      catch (e) {
        last = e;
        if (logger) await logger.warn("retry", (label || "任务") + " 第 " + i + " 次失败：" + e.message);
        if (i < times) await sleep(delay * i);
      }
    }
    throw last;
  }

  const Native = {
    available: function () { return !!(global.webkit && global.webkit.messageHandlers && global.webkit.messageHandlers.native); },
    call: function (action, payload) {
      return new Promise(function (resolve, reject) {
        if (!Native.available()) {
          resolve({ ok: false, skipped: true, action: action });
          return;
        }
        const id = uid("n");
        const handler = function (ev) {
          const data = ev.detail || {};
          if (data.id !== id) return;
          global.removeEventListener("native-result", handler);
          if (data.ok) resolve(data);
          else reject(new Error(data.error || "native error"));
        };
        global.addEventListener("native-result", handler);
        try {
          global.webkit.messageHandlers.native.postMessage({ id: id, action: action, payload: payload || {} });
        } catch (e) { reject(e); }
      });
    }
  };

  global.WAA = {
    APP_ID: APP_ID,
    DEFAULT_SETTINGS: DEFAULT_SETTINGS,
    util: {
      now: now, uid: uid, clone: clone, formatTime: formatTime, formatClock: formatClock,
      todayKey: todayKey, escapeHtml: escapeHtml, sleep: sleep, hash: hash,
      fingerprint: fingerprint, inWorkHours: inWorkHours, truncate: truncate, parseList: parseList,
      withTimeout: withTimeout, retry: retry
    },
    EventBus: EventBus,
    Store: Store,
    Logger: Logger,
    Config: Config,
    Native: Native
  };
})(window);
