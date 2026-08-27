/**
 * AI 调用层：可替换 Provider。
 * deepseek / gemini / siliconflow / openrouter — 市面免费精选（需用户自填 Key）
 * ollama   — 本机 Ollama
 * openai-compatible — 自定义兼容接口
 * offline-fallback — 无 Key/无网时的离线模板兜底
 */
(function (global) {
  "use strict";
  const U = global.WAA.util;

  const STYLES = {
    formal: { tone: "礼貌克制", extra: "用语正式，少用口语。" },
    natural: { tone: "自然随和", extra: "像朋友聊天。" },
    concise: { tone: "简短直接", extra: "能一句说完就一句。" },
    warm: { tone: "热情友好", extra: "主动一点，带点温度。" },
    professional: { tone: "专业稳妥", extra: "信息清楚，不闲聊。" },
    humorous: { tone: "轻松幽默", extra: "可以轻微玩笑，但不油腻。" }
  };

  class BuiltinModel {
    constructor() {
      this.id = "offline-zh-chat";
      this.status = "ready";
      this.path = "local://offline-fallback";
    }

    classify(text) {
      const t = String(text || "");
      const rules = [
        ["greeting", /(你好|在吗|在么|嗨|哈喽|hello|hi|早上好|晚上好|下午好|在不在)/i],
        ["thanks", /(谢谢|感谢|多谢|辛苦)/],
        ["bye", /(再见|拜拜|晚安|先这样|回头聊)/],
        ["ask_time", /(几点|什么时候|哪天|明天|后天|周末)/],
        ["ask_ok", /(方便|可以吗|行不行|能不能|是否)/],
        ["confirm", /(好的|收到|嗯嗯|ok|OK|可以|没问题|行)/],
        ["location", /(地址|在哪|位置|怎么走)/],
        ["price", /(价格|多少钱|费用|报价)/],
        ["meeting", /(开会|会议|见面|约一下)/],
        ["wait", /(等一下|稍后|稍后回|忙)/],
        ["question", /(吗|么|？|\?|怎么|为什么|如何)/]
      ];
      for (let i = 0; i < rules.length; i++) if (rules[i][1].test(t)) return rules[i][0];
      return "chat";
    }

    pick(arr, seed) {
      if (!arr || !arr.length) return "";
      return arr[Math.abs(seed) % arr.length];
    }

    seedFrom(text, temperature) {
      const bucket = temperature > 0.8 ? 1000 : 15000;
      let h = parseInt(U.hash(text + "|" + Math.floor(Date.now() / bucket)), 16);
      if (temperature < 0.2) h = parseInt(U.hash(text), 16);
      return h;
    }

    compose(intent, msg, ctx, style, maxLen, temperature) {
      const seed = this.seedFrom(msg.content + intent + style, temperature);
      const lastSelf = (ctx || []).filter(function (m) { return m.isSelf; }).slice(-1)[0];
      const lastUser = (ctx || []).filter(function (m) { return !m.isSelf; }).slice(-2);
      const asked = lastSelf ? lastSelf.content : "";

      const banks = {
        greeting: {
          natural: ["你好呀，在的，有什么事情可以直接跟我说～", "在的在的，刚看到消息。", "嗨，我在，你说。"],
          formal: ["您好，我在，请讲。", "您好，已看到消息，请问有什么可以帮您？"],
          concise: ["在的，请说。", "在，你说。"],
          warm: ["你好呀～我在的，随时说～", "看到啦，我在呢，慢慢说～"],
          professional: ["您好，我在线，请直接说明需求。", "已收到，我在，请讲。"],
          humorous: ["在的在的，信号满格。", "在呢，我又不是下班了哈哈"]
        },
        thanks: {
          natural: ["不客气，有需要再叫我。", "没事没事，应该的。"],
          formal: ["不客气，这是应该的。", "不客气，后续有问题随时联系。"],
          concise: ["不客气。", "应该的。"],
          warm: ["太客气啦～举手之劳。", "嘿嘿不客气，能帮上就好。"],
          professional: ["不客气，后续我继续跟进。", "应该的，有进展我同步你。"],
          humorous: ["谢什么谢，都是自己人。", "别客气，不然我还得客气回去哈哈"]
        },
        bye: {
          natural: ["好，回头聊。", "行，那先这样，有事再找我。"],
          formal: ["好的，再见。", "先这样，后续保持联系。"],
          concise: ["好，再见。", "嗯，回头说。"],
          warm: ["好呀，注意休息～", "拜拜，有消息随时找我～"],
          professional: ["好的，我们保持同步。", "先到这里，有进展再联系。"],
          humorous: ["行，我先去充电了。", "拜了个拜～"]
        },
        confirm: {
          natural: ["好的，我记下了。", "嗯嗯，收到。"],
          formal: ["好的，已确认。", "收到，按此执行。"],
          concise: ["收到。", "好的。"],
          warm: ["好嘞～我知道啦。", "嗯嗯收到～"],
          professional: ["收到，我这边按这个推进。", "确认，已同步。"],
          humorous: ["收到收到，已存档到大脑C盘。", "OK，任务get。"]
        },
        wait: {
          natural: ["好，那你忙，我等你消息。", "行，不急。"],
          formal: ["好的，我等候您的回复。", "明白，您先处理。"],
          concise: ["好，等你。", "不急。"],
          warm: ["好好好，你先忙～", "没事，我等你呀。"],
          professional: ["好的，我待命。", "明白，您处理完再继续。"],
          humorous: ["行，我原地待机。", "忙吧忙吧，我去喝口水。"]
        },
        price: {
          natural: ["价格这块我帮你确认一下，稍回你。", "费用我核对后再发你。"],
          formal: ["关于费用，我核实后尽快回复您。", "报价我整理后发给您。"],
          concise: ["我核对后回你。", "稍等，我确认费用。"],
          warm: ["钱的事我帮你问清楚哈～", "我去对一下价格，马上回。"],
          professional: ["报价我整理成条目后发给你。", "费用口径我确认后再同步。"],
          humorous: ["钱的事我可不敢瞎报，我先问清楚哈哈"]
        },
        location: {
          natural: ["位置我发你，你看方便不。", "地址我整理一下发过来。"],
          formal: ["地址我随后发给您。", "位置信息我整理后同步。"],
          concise: ["我发地址。", "稍后发位置。"],
          warm: ["地址马上发你～别走丢了。", "我发你定位哈。"],
          professional: ["地址与路线我一并发你。", "位置信息随后同步。"],
          humorous: ["别担心，我把导航级别的地址给你。"]
        },
        meeting: {
          natural: ["行，那我们约个时间，你哪会儿方便？", "可以，我看下日程回你。"],
          formal: ["好的，我确认时间后回复您。", "会议安排我协调后同步。"],
          concise: ["我看下时间。", "方便的话发我几个时段。"],
          warm: ["好呀，约起来～你啥时候方便？", "可以可以，我来约。"],
          professional: ["我核对日程后给出可选时段。", "会议我来协调，稍后同步。"],
          humorous: ["约！我日历已经掏出来了。"]
        },
        ask_time: {
          natural: ["时间我确认一下，尽快回你。", "我看下具体点，稍后发你。"],
          formal: ["具体时间我核实后回复。", "我确认档期后同步您。"],
          concise: ["我确认时间。", "稍后回你时间。"],
          warm: ["我帮你问清楚时间哈～", "稍等，我看几点。"],
          professional: ["时间节点我核对后书面同步。", "我确认后给准确时间。"],
          humorous: ["时间啊，我先去拷问一下日历。"]
        },
        ask_ok: {
          natural: ["可以的，你说具体点我来安排。", "方便，你直接说就行。"],
          formal: ["可以，请告知细节。", "方便，请继续说明。"],
          concise: ["可以。", "行，你说。"],
          warm: ["当然可以呀～", "方便方便，你说吧。"],
          professional: ["可以推进，请补充关键信息。", "可行，请给到具体要求。"],
          humorous: ["可以可以，这题我会。"]
        },
        question: {
          natural: ["我看一下，马上回你。", "这个问题我确认后告诉你。"],
          formal: ["我核实后回复您。", "收到问题，我确认后反馈。"],
          concise: ["我确认下。", "稍后回你。"],
          warm: ["我帮你看看哈～", "等我一下，我查清楚。"],
          professional: ["我核对信息后给你准确答复。", "收到，我核实口径再回。"],
          humorous: ["好问题，容我翻一下小抄。"]
        },
        chat: {
          natural: ["嗯嗯，我明白了。", "收到，我这边看着办。", "好的，我了解了。"],
          formal: ["已了解，我会妥善处理。", "收到，按此跟进。"],
          concise: ["明白。", "好的。"],
          warm: ["嗯嗯我懂啦～", "好的呀，我记着。"],
          professional: ["信息已收到，我继续处理。", "了解，我这边跟进。"],
          humorous: ["懂了懂了，脑内已加载。"]
        }
      };

      const styleBank = (banks[intent] && banks[intent][style]) || banks.chat.natural;
      let reply = this.pick(styleBank, seed);

      if (intent === "chat" && msg.content && msg.content.length > 8 && temperature >= 0.45) {
        const echo = U.truncate(msg.content.replace(/[。！？!?～~]+$/g, ""), 18);
        const bridges = {
          natural: ["嗯，关于「" + echo + "」，我知道了。", "「" + echo + "」这事我记下了，有进展告诉你。"],
          formal: ["关于「" + echo + "」，我已了解。", "就「" + echo + "」这一点，我会跟进。"],
          concise: ["「" + echo + "」收到。", "记下了。"],
          warm: ["「" + echo + "」我看到啦～", "嗯嗯，「" + echo + "」我帮你盯着。"],
          professional: ["「" + echo + "」已记录，我继续处理。", "该项我跟进。"],
          humorous: ["「" + echo + "」这题我记下了，不跑丢。"]
        };
        reply = this.pick(bridges[style] || bridges.natural, seed >> 3);
      }

      if (asked && /哪会儿|什么时候|方便吗/.test(asked) && /明天|后天|今晚|周末/.test(msg.content)) {
        reply = style === "concise" ? "好，就按你说的。" :
          style === "formal" ? "好的，按这个时间安排。" :
            "行，那就按你说的来～";
      }

      if (lastUser.length >= 2 && intent === "greeting") {
        reply = style === "formal" ? "您好，我一直在。" : "在的，刚才可能刷到下面了。";
      }

      if (style === "humorous" && temperature > 0.75 && Math.abs(seed) % 5 === 0) {
        reply = reply.replace(/。$/, "哈哈");
      }
      if (style === "warm" && !/[～呀哈]/.test(reply)) reply += "～";

      return U.truncate(reply, maxLen || 80);
    }

    async generate(payload) {
      const message = payload.message;
      const context = payload.context;
      const settings = payload.settings;
      const style = (settings.ai && settings.ai.style) || "natural";
      const maxLen = (settings.ai && settings.ai.maxReplyLength) || 80;
      const temperature = (settings.ai && settings.ai.temperature) || 0.7;
      const intent = this.classify(message.content);
      await U.sleep(180 + Math.floor(Math.random() * 220));
      const text = this.compose(intent, message, context || [], style, maxLen, temperature);
      return {
        provider: "offline-fallback",
        model: this.id,
        intent: intent,
        text: text,
        usage: { local: true, tokens: Math.ceil((message.content.length + text.length) / 2) }
      };
    }
  }

  class OllamaProvider {
    async generate(payload) {
      const cfg = payload.settings.ai.ollama || {};
      const body = {
        model: cfg.model || "qwen2.5:3b",
        stream: false,
        options: {
          temperature: payload.settings.ai.temperature || 0.7,
          num_predict: payload.settings.ai.maxReplyLength || 80
        },
        messages: buildChatMessages(payload)
      };
      const res = await U.withTimeout(fetch((cfg.baseUrl || "http://127.0.0.1:11434") + "/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      }), cfg.timeoutMs || 20000, "Ollama 请求");
      if (!res.ok) throw new Error("Ollama HTTP " + res.status);
      const data = await res.json();
      const text = ((data.message && data.message.content) || data.response || "").trim();
      if (!text) throw new Error("Ollama 返回空内容");
      return {
        provider: "ollama",
        model: body.model,
        text: U.truncate(text, payload.settings.ai.maxReplyLength || 200),
        usage: data.usage || null
      };
    }
    async health(settings) {
      try {
        const cfg = (settings && settings.ai && settings.ai.ollama) || {};
        const res = await U.withTimeout(
          fetch((cfg.baseUrl || "http://127.0.0.1:11434") + "/api/tags"),
          4000,
          "Ollama 健康检查"
        );
        return { ok: res.ok, detail: res.ok ? "Ollama 可用" : "HTTP " + res.status };
      } catch (e) {
        return { ok: false, detail: e.message };
      }
    }
  }

  class OpenAICompatibleProvider {
    async generate(payload) {
      const cfg = payload.settings.ai.openai || {};
      if (!cfg.baseUrl) throw new Error("未配置模型接口地址");
      const provider = payload.settings.ai.provider || "";
      if (!cfg.apiKey && provider !== "openai-compatible") {
        throw new Error("请先在设置中填写 API Key（可免费申请）");
      }
      const headers = { "Content-Type": "application/json" };
      if (cfg.apiKey) headers.Authorization = "Bearer " + cfg.apiKey;
      if (provider === "openrouter") {
        headers["HTTP-Referer"] = "https://waa.local";
        headers["X-Title"] = "WeChat AI Assistant";
      }
      const body = {
        model: cfg.model || "local-model",
        temperature: payload.settings.ai.temperature || 0.7,
        max_tokens: payload.settings.ai.maxReplyLength || 80,
        messages: buildChatMessages(payload)
      };
      const res = await U.withTimeout(fetch(cfg.baseUrl.replace(/\/$/, "") + "/chat/completions", {
        method: "POST",
        headers: headers,
        body: JSON.stringify(body)
      }), cfg.timeoutMs || 45000, "模型请求");
      if (!res.ok) throw new Error("模型 HTTP " + res.status);
      const data = await res.json();
      const text = (((data.choices || [])[0] || {}).message || {}).content || "";
      if (!String(text).trim()) throw new Error("模型返回空内容");
      return {
        provider: provider || "openai-compatible",
        model: body.model,
        text: U.truncate(String(text).trim(), payload.settings.ai.maxReplyLength || 200),
        usage: data.usage || null
      };
    }
  }

  function buildChatMessages(payload) {
    const s = payload.settings;
    const style = STYLES[s.ai.style] || STYLES.natural;
    const sys = (s.ai.systemPrompt || "") +
      " 回复风格：" + style.tone + "。" + style.extra +
      " 请用中文，尽量不超过" + (s.ai.maxReplyLength || 80) + "字，不要使用 Markdown。";
    const msgs = [{ role: "system", content: sys }];
    (payload.context || []).forEach(function (m) {
      msgs.push({
        role: m.isSelf ? "assistant" : "user",
        content: (m.senderName ? m.senderName + "：" : "") + m.content
      });
    });
    msgs.push({
      role: "user",
      content: (payload.message.senderName ? payload.message.senderName + "：" : "") + payload.message.content
    });
    return msgs;
  }

  const FREE_PRESETS = {
    deepseek: { name: "DeepSeek", baseUrl: "https://api.deepseek.com/v1", model: "deepseek-chat" },
    gemini: { name: "Gemini", baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai", model: "gemini-2.0-flash" },
    siliconflow: { name: "硅基流动", baseUrl: "https://api.siliconflow.cn/v1", model: "Qwen/Qwen2.5-7B-Instruct" },
    openrouter: { name: "OpenRouter", baseUrl: "https://openrouter.ai/api/v1", model: "deepseek/deepseek-chat-v3-0324:free" }
  };

  function applyPresetToSettings(settings, provider) {
    const preset = FREE_PRESETS[provider];
    if (!preset) return settings;
    const saved = (settings.ai.presets && settings.ai.presets[provider]) || {};
    settings.ai.provider = provider;
    settings.ai.openai = settings.ai.openai || {};
    settings.ai.openai.baseUrl = saved.baseUrl || preset.baseUrl;
    settings.ai.openai.model = saved.model || preset.model;
    settings.ai.openai.apiKey = saved.apiKey || settings.ai.openai.apiKey || "";
    settings.ai.currentModel = settings.ai.openai.model;
    settings.ai.modelPath = settings.ai.openai.baseUrl;
    return settings;
  }

  class AIRouter {
    constructor(logger) {
      this.logger = logger;
      this.offline = new BuiltinModel();
      this.ollama = new OllamaProvider();
      this.openai = new OpenAICompatibleProvider();
    }
    status(settings) {
      const p = settings.ai.provider;
      if (p === "builtin" || p === "offline-fallback") {
        return { provider: "offline-fallback", model: "offline-zh-chat", path: "local://offline", status: "ready", detail: "离线兜底模板" };
      }
      if (p === "ollama") {
        return { provider: p, model: settings.ai.ollama.model, path: settings.ai.ollama.baseUrl, status: "configured", detail: "需本机 Ollama" };
      }
      const key = (settings.ai.openai && settings.ai.openai.apiKey) || "";
      const name = (FREE_PRESETS[p] && FREE_PRESETS[p].name) || "云端模型";
      return {
        provider: p,
        model: settings.ai.openai.model,
        path: settings.ai.openai.baseUrl,
        status: key ? "ready" : "need_key",
        detail: name + (key ? " · 就绪" : " · 待填 Key")
      };
    }
    async generate(payload) {
      let provider = payload.settings.ai.provider || "deepseek";
      if (provider === "builtin") provider = "offline-fallback";
      const self = this;
      const cloudProviders = { deepseek: 1, gemini: 1, siliconflow: 1, openrouter: 1, openai: 1, "openai-compatible": 1 };
      const run = async function () {
        if (provider === "ollama") return await self.ollama.generate(payload);
        if (provider === "offline-fallback") return await self.offline.generate(payload);
        if (cloudProviders[provider]) {
          return await self.openai.generate(payload);
        }
        return await self.offline.generate(payload);
      };
      await this.logger.info("ai", "请求生成回复", {
        provider: provider,
        conversationId: payload.message.conversationId,
        preview: U.truncate(payload.message.content, 80)
      });
      try {
        const out = await U.retry(function () {
          return U.withTimeout(run(), 45000, "AI 生成");
        }, 2, 600, this.logger, "AI 生成");
        out.text = U.truncate(String(out.text || "").replace(/\s+/g, " ").trim(), payload.settings.ai.maxReplyLength || 80);
        await this.logger.info("ai", "生成完成", { provider: out.provider, text: out.text, intent: out.intent || null });
        return out;
      } catch (e) {
        if (payload.settings.ai.offlineFallbackEnabled !== false && provider !== "offline-fallback") {
          await this.logger.warn("ai", "云端失败，回退离线模板：" + e.message, { provider: provider });
          const fb = await self.offline.generate(payload);
          fb.text = U.truncate(String(fb.text || "").replace(/\s+/g, " ").trim(), payload.settings.ai.maxReplyLength || 80);
          return fb;
        }
        await this.logger.error("ai", "生成失败：" + e.message, { provider: provider });
        throw e;
      }
    }
  }

  global.WAA.AI = {
    AIRouter: AIRouter,
    BuiltinModel: BuiltinModel,
    STYLES: STYLES,
    FREE_PRESETS: FREE_PRESETS,
    applyPresetToSettings: applyPresetToSettings
  };
})(window);
