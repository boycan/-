/**
 * 处理引擎：去重、规则、上下文、冷却、队列、审核/发送。
 */
(function (global) {
  "use strict";
  const U = global.WAA.util;

  class RulesEngine {
    evaluate(message, settings) {
      if (settings.reply.mode === "off") return { allow: false, reason: "回复模式关闭" };
      if (!settings.reply.enabled) return { allow: false, reason: "自动回复关闭" };
      if (message.isSelf) return { allow: false, reason: "自己的消息" };
      if (!String(message.content || "").trim()) return { allow: false, reason: "空消息" };

      const targets = [message.conversationName, message.senderName].filter(Boolean);
      const lists = settings.lists || {};
      if ((lists.blacklist || []).some(function (b) {
        return targets.some(function (t) { return String(t).indexOf(b) >= 0; });
      })) return { allow: false, reason: "黑名单" };

      if (lists.whitelistOnly) {
        const hit = (lists.whitelist || []).some(function (w) {
          return targets.some(function (t) { return String(t).indexOf(w) >= 0; });
        });
        if (!hit) return { allow: false, reason: "白名单未命中" };
      }

      if (!settings.reply.globalEnabled) {
        const contacts = lists.contacts || [];
        const groups = lists.groups || [];
        const isGroup = message.conversationType === "group";
        const ok = isGroup
          ? groups.some(function (g) { return String(message.conversationName).indexOf(g) >= 0; })
          : contacts.some(function (c) { return targets.some(function (t) { return String(t).indexOf(c) >= 0; }); });
        if (!ok) return { allow: false, reason: "未命中指定联系人/群" };
      }

      const kws = (lists.keywords || []).filter(function (k) { return k.enabled !== false; });
      if (kws.length) {
        const hit = kws.some(function (k) { return String(message.content).indexOf(k.keyword) >= 0; });
        if (!hit) return { allow: false, reason: "未命中关键词" };
      }

      if (settings.reply.workHoursEnabled) {
        const inWork = U.inWorkHours(settings);
        const policy = inWork ? settings.reply.workHoursPolicy : settings.reply.offHoursPolicy;
        if (policy === "off") return { allow: false, reason: inWork ? "工作时间关闭" : "非工作时间关闭" };
      }
      return { allow: true, reason: "通过" };
    }
  }

  class MessageEngine {
    constructor(deps) {
      this.store = deps.store;
      this.config = deps.config;
      this.logger = deps.logger;
      this.bus = deps.bus;
      this.ai = deps.ai;
      this.rules = new RulesEngine();
      this.adapter = null;
      this.queue = [];
      this.busy = false;
      this.status = "stopped";
      this.cooldownUntil = {};
      this.mergeBuf = {};
      this.errorStreak = 0;
      this.seen = {};
    }

    async start() {
      if (this.status === "running") return;
      const settings = this.config.get();
      this.adapter = global.WAA.Adapters.createAdapter(settings.adapter.type || "simulator");
      const self = this;
      await this.adapter.start(function (msg) { self.enqueue(msg); });
      this.status = "running";
      await this.config.save({ runtime: { status: "running", lastStartedAt: U.now(), error: null } });
      await this.logger.info("runtime", "引擎启动 adapter=" + this.adapter.id);
      this.bus.emit("runtime", this.status);
      this._pump();
    }

    async pause() {
      this.status = "paused";
      await this.config.save({ runtime: { status: "paused" } });
      await this.logger.warn("runtime", "引擎暂停");
      this.bus.emit("runtime", this.status);
    }

    async resume() {
      this.status = "running";
      this.errorStreak = 0;
      await this.config.save({ runtime: { status: "running", error: null } });
      await this.logger.info("runtime", "引擎恢复");
      this.bus.emit("runtime", this.status);
      this._pump();
    }

    async stop() {
      this.status = "stopped";
      if (this.adapter) await this.adapter.stop();
      this.adapter = null;
      await this.config.save({ runtime: { status: "stopped", lastStoppedAt: U.now() } });
      await this.logger.info("runtime", "引擎停止");
      this.bus.emit("runtime", this.status);
    }

    enqueue(msg) {
      if (this.status === "stopped") return;
      this.queue.push(msg);
      this.bus.emit("message", msg);
      this._pump();
    }

    inject(partial) {
      if (this.adapter && this.adapter.inject) this.adapter.inject(partial);
      else if (this.adapter && this.adapter.push) this.adapter.push(partial);
    }

    _pump() {
      if (this.busy) return;
      const self = this;
      this.busy = true;
      (async function loop() {
        while (self.queue.length && self.status !== "stopped") {
          if (self.status === "paused") break;
          const msg = self.queue.shift();
          try { await self.process(msg); }
          catch (e) {
            await self.logger.error("engine", "处理异常：" + e.message);
            await self._onError();
          }
        }
        self.busy = false;
      })();
    }

    async process(raw) {
      const settings = this.config.get();
      const message = Object.assign({}, raw);
      message.fingerprint = message.fingerprint || U.fingerprint(message);
      await this.logger.info("message", "检测到：" + message.conversationName + " - " + U.truncate(message.content, 60));

      if (this.seen[message.fingerprint]) {
        await this.logger.info("guard", "去重命中");
        return;
      }
      const existed = await this.store.byIndex("messages", "fingerprint", message.fingerprint);
      if (existed && existed.length) {
        this.seen[message.fingerprint] = 1;
        await this.logger.info("guard", "数据库去重命中");
        return;
      }
      this.seen[message.fingerprint] = 1;

      const now = U.now();
      const prev = this.mergeBuf[message.conversationId];
      let content = message.content;
      if (prev && now - prev.ts <= (settings.reply.mergeWindowSeconds || 8) * 1000) {
        content = prev.content + "\n" + message.content;
      }
      this.mergeBuf[message.conversationId] = { ts: now, content: content };
      message.content = content;

      await this._persistIncoming(message);
      const decision = this.rules.evaluate(message, settings);
      if (!decision.allow) {
        await this.logger.info("rules", "拦截：" + decision.reason);
        return;
      }

      if ((this.cooldownUntil[message.conversationId] || 0) > now) {
        await this.logger.warn("guard", "冷却中");
        return;
      }

      const job = {
        id: U.uid("job"),
        conversationId: message.conversationId,
        conversationName: message.conversationName,
        messageId: message.id,
        incomingText: message.content,
        generatedText: "",
        editedText: "",
        status: "generating",
        provider: "",
        intent: "",
        error: "",
        createdAt: now,
        updatedAt: now,
        repliedAt: null
      };
      await this.store.put("jobs", job);
      this.bus.emit("job", job);

      const ctxSize = settings.ai.contextSize || 8;
      let context = [];
      if (settings.reply.useContext) {
        const all = await this.store.byIndex("messages", "conversationId", message.conversationId);
        context = all.sort(function (a, b) { return a.timestamp - b.timestamp; }).slice(-ctxSize);
      }

      let ai;
      try {
        ai = await this.ai.generate({ message: message, context: context, settings: settings });
      } catch (e) {
        job.status = "failed";
        job.error = e.message;
        job.updatedAt = U.now();
        await this.store.put("jobs", job);
        await this._onError();
        return;
      }
      this.errorStreak = 0;
      job.generatedText = ai.text;
      job.editedText = ai.text;
      job.provider = ai.provider;
      job.intent = ai.intent || "";
      job.updatedAt = U.now();

      if (settings.reply.mode === "review" || settings.reply.mode === "assist") {
        job.status = "review";
        await this.store.put("jobs", job);
        this.bus.emit("job", job);
        await this.logger.info("review", "待审核：" + message.conversationName);
        return;
      }
      if (settings.reply.mode === "auto") {
        job.status = "sending";
        await this.store.put("jobs", job);
        const send = await this.adapter.send({
          conversationId: message.conversationId,
          to: message.conversationName,
          text: ai.text
        });
        if (send && send.ok !== false) {
          await this._persistSelf(message, ai.text);
          this.cooldownUntil[message.conversationId] = U.now() + (settings.reply.cooldownSeconds || 20) * 1000;
          job.status = "sent";
          job.repliedAt = U.now();
        } else {
          job.status = "review";
          job.error = (send && send.error) || "发送失败";
        }
        job.updatedAt = U.now();
        await this.store.put("jobs", job);
        this.bus.emit("job", job);
      }
    }

    async approve(jobId, text) {
      const job = await this.store.get("jobs", jobId);
      if (!job) return;
      const finalText = String(text != null ? text : (job.editedText || job.generatedText)).trim();
      if (!finalText) return;
      job.editedText = finalText;
      job.status = "sending";
      job.updatedAt = U.now();
      await this.store.put("jobs", job);
      const send = this.adapter
        ? await this.adapter.send({ conversationId: job.conversationId, to: job.conversationName, text: finalText })
        : { ok: true };
      if (send && send.ok !== false) {
        await this._persistSelf({
          conversationId: job.conversationId,
          conversationName: job.conversationName,
          conversationType: "contact"
        }, finalText);
        const settings = this.config.get();
        this.cooldownUntil[job.conversationId] = U.now() + (settings.reply.cooldownSeconds || 20) * 1000;
        job.status = "sent";
        job.repliedAt = U.now();
        job.error = "";
      } else {
        job.status = "review";
        job.error = (send && send.error) || "发送失败";
      }
      job.updatedAt = U.now();
      await this.store.put("jobs", job);
      this.bus.emit("job", job);
    }

    async ignore(jobId) {
      const job = await this.store.get("jobs", jobId);
      if (!job) return;
      job.status = "ignored";
      job.updatedAt = U.now();
      await this.store.put("jobs", job);
      this.bus.emit("job", job);
    }

    async regenerate(jobId) {
      const job = await this.store.get("jobs", jobId);
      if (!job) return;
      const settings = this.config.get();
      const all = await this.store.byIndex("messages", "conversationId", job.conversationId);
      const context = all.sort(function (a, b) { return a.timestamp - b.timestamp; }).slice(-(settings.ai.contextSize || 8));
      const message = {
        id: job.messageId,
        conversationId: job.conversationId,
        conversationName: job.conversationName,
        senderId: job.conversationId,
        senderName: job.conversationName,
        content: job.incomingText,
        timestamp: job.createdAt,
        isSelf: false
      };
      job.status = "generating";
      await this.store.put("jobs", job);
      try {
        const ai = await this.ai.generate({ message: message, context: context, settings: settings });
        job.generatedText = ai.text;
        job.editedText = ai.text;
        job.provider = ai.provider;
        job.intent = ai.intent || "";
        job.status = "review";
        job.error = "";
      } catch (e) {
        job.status = "failed";
        job.error = e.message;
      }
      job.updatedAt = U.now();
      await this.store.put("jobs", job);
      this.bus.emit("job", job);
    }

    async _persistIncoming(message) {
      await this.store.put("messages", {
        id: message.id,
        conversationId: message.conversationId,
        senderId: message.senderId,
        senderName: message.senderName,
        content: message.content,
        contentType: message.contentType || "text",
        timestamp: message.timestamp,
        isSelf: !!message.isSelf,
        fingerprint: message.fingerprint
      });
      const old = await this.store.get("conversations", message.conversationId);
      await this.store.put("conversations", {
        id: message.conversationId,
        name: message.conversationName,
        type: message.conversationType || "contact",
        lastMessage: message.content,
        lastAiReply: (old && old.lastAiReply) || "",
        updatedAt: U.now(),
        messageCount: ((old && old.messageCount) || 0) + 1,
        replyCount: (old && old.replyCount) || 0
      });
    }

    async _persistSelf(source, text) {
      const msg = {
        id: U.uid("msg"),
        conversationId: source.conversationId,
        senderId: "self",
        senderName: "我",
        content: text,
        contentType: "text",
        timestamp: U.now(),
        isSelf: true
      };
      msg.fingerprint = U.fingerprint(msg);
      await this.store.put("messages", msg);
      const old = await this.store.get("conversations", source.conversationId);
      if (old) {
        old.lastAiReply = text;
        old.lastMessage = text;
        old.updatedAt = U.now();
        old.replyCount = (old.replyCount || 0) + 1;
        await this.store.put("conversations", old);
      }
    }

    async _onError() {
      this.errorStreak += 1;
      const settings = this.config.get();
      if (settings.reply.autoPauseOnError && this.errorStreak >= (settings.reply.errorPauseThreshold || 3)) {
        await this.pause();
        await this.config.save({ runtime: { error: "连续失败，已自动暂停" } });
      }
    }
  }

  global.WAA.Engine = { MessageEngine: MessageEngine, RulesEngine: RulesEngine };
})(window);
