/**
 * 桌面/浏览器管理界面。
 */
(function (global) {
  "use strict";
  const U = global.WAA.util;
  const $ = function (sel) { return document.querySelector(sel); };
  const $$ = function (sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); };

  const state = {
    bus: null,
    store: null,
    config: null,
    logger: null,
    ai: null,
    engine: null,
    page: "dashboard"
  };

  function toast(msg) {
    const wrap = $("#toasts");
    if (!wrap) return;
    const el = document.createElement("div");
    el.className = "toast";
    el.textContent = msg;
    wrap.appendChild(el);
    setTimeout(function () { el.remove(); }, 2600);
  }

  async function boot() {
    const bus = new global.WAA.EventBus();
    const store = new global.WAA.Store();
    await store.open();
    const logger = new global.WAA.Logger(bus, store);
    const config = new global.WAA.Config(store, bus);
    await config.load();
    const ai = new global.WAA.AI.AIRouter(logger);
    const engine = new global.WAA.Engine.MessageEngine({ store: store, config: config, logger: logger, bus: bus, ai: ai });

    state.bus = bus;
    state.store = store;
    state.config = config;
    state.logger = logger;
    state.ai = ai;
    state.engine = engine;

    wireNav();
    wireRuntimeButtons();
    bus.on("runtime", refreshRuntimeChip);
    bus.on("log", function () { if (state.page === "logs") renderLogs(); });
    bus.on("job", function () {
      refreshReviewBadge();
      if (state.page === "review") renderReview();
      if (state.page === "dashboard") renderDashboard();
      if (state.page === "chats") renderChats();
    });
    bus.on("settings", function () {
      refreshModelPill();
      if (state.page === "settings") renderSettings();
    });
    bus.on("message", function () {
      if (state.page === "dashboard") renderDashboard();
      if (state.page === "chats") renderChats();
    });

    refreshRuntimeChip();
    refreshModelPill();
    refreshReviewBadge();
    showPage("dashboard");

    // 关闭窗口提示
    const modal = $("#close-modal");
    if (modal) {
      $("#close-tray") && $("#close-tray").addEventListener("click", function () {
        modal.classList.remove("show");
        U.Native.call("minimizeToTray");
      });
      $("#close-quit") && $("#close-quit").addEventListener("click", function () {
        state.engine.stop();
        U.Native.call("quit");
        modal.classList.remove("show");
      });
    }
  }

  function wireNav() {
    $$("#nav button").forEach(function (btn) {
      btn.addEventListener("click", function () {
        $$("#nav button").forEach(function (b) { b.classList.remove("active"); });
        btn.classList.add("active");
        showPage(btn.getAttribute("data-page"));
      });
    });
  }

  function wireRuntimeButtons() {
    $("#btn-start") && $("#btn-start").addEventListener("click", async function () {
      await state.engine.start();
      toast("已启动");
      refreshRuntimeChip();
    });
    $("#btn-pause") && $("#btn-pause").addEventListener("click", async function () {
      if (state.engine.status === "paused") await state.engine.resume();
      else await state.engine.pause();
      refreshRuntimeChip();
    });
    $("#btn-stop") && $("#btn-stop").addEventListener("click", async function () {
      await state.engine.stop();
      refreshRuntimeChip();
    });
  }

  function showPage(name) {
    state.page = name;
    $$(".page").forEach(function (p) { p.classList.remove("active"); });
    const el = $("#page-" + name);
    if (el) el.classList.add("active");
    const titles = {
      dashboard: ["首页总览", "运行状态、今日数据与最近活动"],
      chats: ["会话管理", "联系人/群聊、上下文与最近回复"],
      review: ["人工审核", "编辑、重新生成、发送或忽略"],
      rules: ["回复规则", "模式、关键词、黑白名单与工作时间"],
      logs: ["运行日志", "消息检测 / AI / 发送 / 错误"],
      simulator: ["模拟器", "注入测试消息，不接触微信"],
      settings: ["软件设置", "AI 模型、风格、Prompt、接入方式"]
    };
    const t = titles[name] || [name, ""];
    $("#page-title").textContent = t[0];
    $("#page-desc").textContent = t[1];
    if (name === "dashboard") renderDashboard();
    if (name === "chats") renderChats();
    if (name === "review") renderReview();
    if (name === "rules") renderRules();
    if (name === "logs") renderLogs();
    if (name === "simulator") renderSimulator();
    if (name === "settings") renderSettings();
  }

  function refreshRuntimeChip() {
    const chip = $("#runtime-chip");
    const label = $("#runtime-label");
    if (!chip || !label) return;
    const st = state.engine ? state.engine.status : "stopped";
    chip.dataset.status = st;
    chip.classList.remove("running", "paused", "error");
    if (st === "running" || st === "paused") chip.classList.add(st);
    label.textContent = st === "running" ? "运行中" : st === "paused" ? "已暂停" : "已停止";
  }

  function refreshModelPill() {
    const pill = $("#model-pill");
    if (!pill || !state.config) return;
    const s = state.config.get();
    const st = state.ai.status(s);
    const ready = st.status === "ready";
    const nameMap = { deepseek: "DeepSeek", gemini: "Gemini", siliconflow: "硅基流动", openrouter: "OpenRouter", ollama: "Ollama", "offline-fallback": "离线兜底", builtin: "离线兜底" };
    const title = nameMap[st.provider] || st.model || "AI";
    pill.textContent = title + " · " + (ready ? "就绪" : (st.status === "need_key" ? "待填 Key" : "已配置"));
  }

  async function refreshReviewBadge() {
    const badge = $("#review-badge");
    if (!badge) return;
    const jobs = await state.store.all("jobs");
    const n = jobs.filter(function (j) { return j.status === "review"; }).length;
    badge.textContent = String(n);
  }

  async function renderDashboard() {
    const root = $("#page-dashboard");
    if (!root) return;
    const settings = state.config.get();
    const msgs = await state.store.all("messages");
    const jobs = await state.store.all("jobs");
    const chats = await state.store.all("conversations");
    const today = U.todayKey();
    const received = msgs.filter(function (m) { return !m.isSelf && U.todayKey(m.timestamp) === today; }).length;
    const aiCount = jobs.filter(function (j) { return U.todayKey(j.createdAt) === today; }).length;
    const sent = jobs.filter(function (j) { return j.status === "sent" && U.todayKey(j.repliedAt || j.updatedAt) === today; }).length;
    const pending = jobs.filter(function (j) { return j.status === "review"; }).length;
    root.innerHTML =
      '<div class="grid stats">' +
      card("运行状态", state.engine.status) +
      card("AI 模型", (function () {
        const p = settings.ai.provider || "deepseek";
        const map = { deepseek: "DeepSeek（默认）", gemini: "Gemini", siliconflow: "硅基流动", openrouter: "OpenRouter", ollama: "Ollama", "offline-fallback": "离线兜底", builtin: "DeepSeek（默认）" };
        return map[p] || p;
      })()) +
      card("今日消息", received) +
      card("今日 AI", aiCount) +
      card("今日发送", sent) +
      card("会话数", chats.length) +
      '</div>' +
      (pending ? '<div class="banner warn">有 ' + pending + ' 条待审核，请打开「人工审核」。</div>' : "") +
      '<div class="panel"><h3>快速开始</h3><p class="muted">点击启动后，可到模拟器发送测试消息。</p>' +
      '<button class="btn primary" id="dash-sim">模拟一条「你好，在吗？」</button></div>';
    const btn = $("#dash-sim");
    if (btn) btn.onclick = async function () {
      if (state.engine.status === "stopped") await state.engine.start();
      state.engine.inject({ conversationName: "张三", content: "你好，在吗？" });
      toast("已注入模拟消息");
    };
  }

  function card(title, value) {
    return '<div class="stat-card"><div class="stat-title">' + U.escapeHtml(title) + '</div><div class="stat-value">' + U.escapeHtml(String(value)) + '</div></div>';
  }

  async function renderChats() {
    const root = $("#page-chats");
    const chats = (await state.store.all("conversations")).sort(function (a, b) { return b.updatedAt - a.updatedAt; });
    if (!chats.length) {
      root.innerHTML = '<p class="muted">暂无会话</p>';
      return;
    }
    root.innerHTML = chats.map(function (c) {
      return '<div class="list-item"><div class="list-title">' + U.escapeHtml(c.name) +
        '</div><div class="muted">' + U.escapeHtml(c.lastMessage || "") +
        '</div><div class="muted">AI：' + U.escapeHtml(c.lastAiReply || "-") +
        '</div></div>';
    }).join("");
  }

  async function renderReview() {
    const root = $("#page-review");
    const jobs = (await state.store.all("jobs")).filter(function (j) { return j.status === "review"; })
      .sort(function (a, b) { return b.createdAt - a.createdAt; });
    if (!jobs.length) {
      root.innerHTML = '<p class="muted">暂无待审核回复</p>';
      return;
    }
    root.innerHTML = jobs.map(function (j) {
      return '<div class="panel" data-job="' + j.id + '">' +
        '<div class="list-title">' + U.escapeHtml(j.conversationName) + '</div>' +
        '<div class="muted">对方：' + U.escapeHtml(j.incomingText) + '</div>' +
        '<textarea class="input" rows="3">' + U.escapeHtml(j.editedText || j.generatedText) + '</textarea>' +
        '<div class="row">' +
        '<button class="btn primary" data-act="send">发送</button>' +
        '<button class="btn" data-act="regen">重新生成</button>' +
        '<button class="btn ghost" data-act="ignore">忽略</button>' +
        '</div></div>';
    }).join("");
    $$("#page-review [data-job]").forEach(function (panel) {
      const id = panel.getAttribute("data-job");
      panel.querySelector('[data-act="send"]').onclick = async function () {
        const text = panel.querySelector("textarea").value;
        await state.engine.approve(id, text);
        toast("已发送");
        renderReview();
        refreshReviewBadge();
      };
      panel.querySelector('[data-act="regen"]').onclick = async function () {
        await state.engine.regenerate(id);
        renderReview();
      };
      panel.querySelector('[data-act="ignore"]').onclick = async function () {
        await state.engine.ignore(id);
        renderReview();
        refreshReviewBadge();
      };
    });
  }

  function renderRules() {
    const root = $("#page-rules");
    const s = state.config.get();
    root.innerHTML =
      '<div class="panel form">' +
      fieldSelect("reply.mode", "回复模式", s.reply.mode, [
        ["review", "辅助回复/人工审核"],
        ["auto", "自动回复"],
        ["off", "关闭"]
      ]) +
      fieldCheck("reply.enabled", "启用自动回复", s.reply.enabled) +
      fieldCheck("reply.globalEnabled", "全局自动回复", s.reply.globalEnabled) +
      fieldNum("reply.cooldownSeconds", "冷却秒数", s.reply.cooldownSeconds) +
      fieldNum("reply.maxPerMinute", "每分钟上限", s.reply.maxPerMinute) +
      fieldNum("reply.maxPerDay", "每日上限", s.reply.maxPerDay) +
      fieldArea("lists.keywords", "关键词（每行一个）", (s.lists.keywords || []).map(function (k) { return k.keyword; }).join("\n")) +
      fieldArea("lists.blacklist", "黑名单", (s.lists.blacklist || []).join("\n")) +
      fieldArea("lists.whitelist", "白名单", (s.lists.whitelist || []).join("\n")) +
      '<button class="btn primary" id="save-rules">保存规则</button></div>';
    $("#save-rules").onclick = async function () {
      const mode = root.querySelector('[name="reply.mode"]').value;
      const enabled = root.querySelector('[name="reply.enabled"]').checked;
      const globalEnabled = root.querySelector('[name="reply.globalEnabled"]').checked;
      const cooldownSeconds = parseInt(root.querySelector('[name="reply.cooldownSeconds"]').value, 10) || 20;
      const maxPerMinute = parseInt(root.querySelector('[name="reply.maxPerMinute"]').value, 10) || 8;
      const maxPerDay = parseInt(root.querySelector('[name="reply.maxPerDay"]').value, 10) || 200;
      const keywords = U.parseList(root.querySelector('[name="lists.keywords"]').value).map(function (k) { return { keyword: k, enabled: true }; });
      const blacklist = U.parseList(root.querySelector('[name="lists.blacklist"]').value);
      const whitelist = U.parseList(root.querySelector('[name="lists.whitelist"]').value);
      await state.config.save({
        reply: { mode: mode, enabled: enabled, globalEnabled: globalEnabled, cooldownSeconds: cooldownSeconds, maxPerMinute: maxPerMinute, maxPerDay: maxPerDay },
        lists: { keywords: keywords, blacklist: blacklist, whitelist: whitelist }
      });
      toast("规则已保存");
    };
  }

  async function renderLogs() {
    const root = $("#page-logs");
    const logs = await state.logger.query({});
    root.innerHTML = logs.slice(0, 100).map(function (l) {
      return '<div class="log ' + l.level + '"><span class="muted">' + U.formatTime(l.ts) +
        '</span> [' + U.escapeHtml(l.category) + '] ' + U.escapeHtml(l.message) + '</div>';
    }).join("") || '<p class="muted">暂无日志</p>';
  }

  function renderSimulator() {
    const root = $("#page-simulator");
    root.innerHTML =
      '<div class="panel form">' +
      '<label>联系人/群名<input class="input" name="name" value="张三"/></label>' +
      '<label>消息内容<textarea class="input" name="content" rows="3">你好，在吗？</textarea></label>' +
      '<label class="check"><input type="checkbox" name="group"/> 群聊</label>' +
      '<button class="btn primary" id="sim-send">注入消息</button></div>';
    $("#sim-send").onclick = async function () {
      if (state.engine.status === "stopped") await state.engine.start();
      const name = root.querySelector('[name="name"]').value.trim() || "测试";
      const content = root.querySelector('[name="content"]').value.trim();
      const group = root.querySelector('[name="group"]').checked;
      state.engine.inject({
        conversationName: name,
        conversationType: group ? "group" : "contact",
        conversationId: (group ? "g_" : "c_") + U.hash(name),
        senderName: name,
        content: content
      });
      toast("已注入");
    };
  }

  function renderSettings() {
    const root = $("#page-settings");
    const s = state.config.get();
    root.innerHTML =
      '<div class="panel form">' +
      fieldSelect("ai.provider", "免费精选模型", s.ai.provider, [
        ["deepseek", "DeepSeek（默认推荐）"],
        ["gemini", "Gemini"],
        ["siliconflow", "硅基流动"],
        ["openrouter", "OpenRouter 免费路由"],
        ["ollama", "Ollama 本地"],
        ["openai-compatible", "自定义 API"],
        ["offline-fallback", "离线兜底模板"]
      ]) +
      '<p class="muted">源码不含 Key。DeepSeek / Gemini / 硅基流动 / OpenRouter 均可免费申请额度。</p>' +
      fieldSelect("ai.style", "回复风格", s.ai.style, [
        ["natural", "自然"], ["concise", "简洁"], ["formal", "正式"],
        ["warm", "热情"], ["professional", "专业"], ["humorous", "幽默"]
      ]) +
      fieldNum("ai.maxReplyLength", "回复长度", s.ai.maxReplyLength) +
      fieldNum("ai.contextSize", "上下文条数", s.ai.contextSize) +
      fieldNum("ai.temperature", "Temperature", s.ai.temperature) +
      fieldArea("ai.systemPrompt", "系统 Prompt", s.ai.systemPrompt) +
      fieldText("ai.ollama.baseUrl", "Ollama URL", s.ai.ollama.baseUrl) +
      fieldText("ai.ollama.model", "Ollama Model", s.ai.ollama.model) +
      fieldText("ai.openai.baseUrl", "模型 Base URL", s.ai.openai.baseUrl) +
      fieldText("ai.openai.model", "模型名", s.ai.openai.model) +
      fieldText("ai.openai.apiKey", "API Key（仅本地保存）", s.ai.openai.apiKey) +
      fieldCheck("ai.offlineFallbackEnabled", "云端失败时使用离线兜底", s.ai.offlineFallbackEnabled !== false) +
      fieldSelect("adapter.type", "消息接入", s.adapter.type, [
        ["simulator", "模拟器"], ["manual", "手动"], ["filewatch", "文件监视"]
      ]) +
      '<button class="btn primary" id="save-settings">保存设置</button></div>';
    $("#save-settings").onclick = async function () {
      let provider = val("ai.provider");
      let openai = {
        baseUrl: val("ai.openai.baseUrl"),
        model: val("ai.openai.model"),
        apiKey: val("ai.openai.apiKey")
      };
      const presets = Object.assign({}, s.ai.presets || {});
      if (global.WAA.AI.FREE_PRESETS[provider]) {
        const tmp = { ai: { openai: openai, presets: presets, provider: provider } };
        global.WAA.AI.applyPresetToSettings(tmp, provider);
        // 用户手动改过的值优先
        openai = {
          baseUrl: val("ai.openai.baseUrl") || tmp.ai.openai.baseUrl,
          model: val("ai.openai.model") || tmp.ai.openai.model,
          apiKey: val("ai.openai.apiKey")
        };
        presets[provider] = Object.assign({}, presets[provider] || {}, openai);
      }
      await state.config.save({
        ai: {
          provider: provider,
          currentModel: openai.model,
          modelPath: openai.baseUrl,
          style: val("ai.style"),
          maxReplyLength: num("ai.maxReplyLength"),
          contextSize: num("ai.contextSize"),
          temperature: parseFloat(val("ai.temperature")) || 0.7,
          systemPrompt: val("ai.systemPrompt"),
          offlineFallbackEnabled: root.querySelector('[name="ai.offlineFallbackEnabled"]').checked,
          presets: presets,
          ollama: { baseUrl: val("ai.ollama.baseUrl"), model: val("ai.ollama.model") },
          openai: openai
        },
        adapter: { type: val("adapter.type") }
      });
      refreshModelPill();
      toast("设置已保存");
    };
    // 切换精选模型时自动填入推荐 URL/模型
    root.querySelector('[name="ai.provider"]').addEventListener("change", function () {
      const provider = this.value;
      const preset = global.WAA.AI.FREE_PRESETS[provider];
      if (!preset) return;
      const saved = (s.ai.presets && s.ai.presets[provider]) || {};
      root.querySelector('[name="ai.openai.baseUrl"]').value = saved.baseUrl || preset.baseUrl;
      root.querySelector('[name="ai.openai.model"]').value = saved.model || preset.model;
      if (saved.apiKey) root.querySelector('[name="ai.openai.apiKey"]').value = saved.apiKey;
    });
    function val(name) { return root.querySelector('[name="' + name + '"]').value; }
    function num(name) { return parseInt(val(name), 10) || 0; }
  }

  function fieldText(name, label, value) {
    return '<label>' + label + '<input class="input" name="' + name + '" value="' + U.escapeHtml(value == null ? "" : value) + '"/></label>';
  }
  function fieldNum(name, label, value) {
    return '<label>' + label + '<input class="input" type="number" step="any" name="' + name + '" value="' + U.escapeHtml(String(value)) + '"/></label>';
  }
  function fieldArea(name, label, value) {
    return '<label>' + label + '<textarea class="input" name="' + name + '" rows="3">' + U.escapeHtml(value || "") + '</textarea></label>';
  }
  function fieldCheck(name, label, checked) {
    return '<label class="check"><input type="checkbox" name="' + name + '"' + (checked ? " checked" : "") + "/> " + label + "</label>";
  }
  function fieldSelect(name, label, value, options) {
    return '<label>' + label + '<select class="input" name="' + name + '">' +
      options.map(function (o) {
        return '<option value="' + o[0] + '"' + (o[0] === value ? " selected" : "") + ">" + o[1] + "</option>";
      }).join("") + "</select></label>";
  }

  document.addEventListener("DOMContentLoaded", function () {
    boot().catch(function (e) {
      console.error(e);
      toast("初始化失败：" + e.message);
    });
  });
})(window);
