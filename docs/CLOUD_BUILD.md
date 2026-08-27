# 不装 Android Studio：用 GitHub Actions 云端打包 APK

本项目已配置 GitHub Actions。你把代码推到 GitHub 后，云端会自动编译，生成可安装的 Debug APK。

## 你需要准备

1. GitHub 账号：https://github.com/signup  
2. 本机有 `git`（你这台 Mac 已有）  
3. 手机允许安装「未知来源 / 外部来源」应用  

**不需要** Android Studio、JDK、Android SDK。

## 五步完成

### 1）在 GitHub 新建仓库

浏览器打开：https://github.com/new  

- Repository name：例如 `wechat-ai-assistant`  
- 选 **Public**（Private 也可以，免费额度略不同）  
- **不要**勾选 “Add a README”  
- 点 Create repository  

创建后页面会显示仓库地址，形如：

```text
https://github.com/你的用户名/wechat-ai-assistant.git
```

### 2）在终端推送本项目

打开「终端」，复制执行（把网址换成你的仓库地址）：

```bash
cd "/Users/zhuruilong/Desktop/comate项目文件/wechat-ai-assistant"

git init
git add .
git commit -m "初始提交：微信 AI 助手 Android + 云端打包"
git branch -M main
git remote add origin https://github.com/你的用户名/wechat-ai-assistant.git
git push -u origin main
```

若提示登录：用 GitHub 用户名 + **Personal Access Token**（不要用账户密码）。  
Token 创建：GitHub → Settings → Developer settings → Personal access tokens。

### 3）等待云端编译

1. 打开你的仓库页面  
2. 点顶部 **Actions**  
3. 进入工作流 **Build Android APK**  
4. 等几分钟，状态变为绿色成功  

也可在 Actions 页点 **Run workflow** 手动再跑一次。

### 4）下载 APK

在成功的那次运行页面往下找 **Artifacts**：

- 名称：`WeChatAIAssistant-debug-apk`  
- 下载 zip，解压得到 `WeChatAIAssistant-debug.apk`

### 5）安装到手机

1. 把 APK 传到手机（隔空投送 / 微信文件传输 / 数据线）  
2. 打开文件并安装（允许「未知来源」）  
3. 打开 App → 设置 → DeepSeek → 填入免费 API Key  
4. 首页启动 → 先用「模拟消息」验证  

安装包名是：`com.waa.assistant.debug`

## 常见问题

**Actions 失败？**  
把红色失败步骤的日志复制发给我，我帮你改。

**下载不到 Artifacts？**  
确认工作流已成功（绿色）。Artifacts 默认保留 14 天。

**安装后打不开 / 闪退？**  
把机型、Android 版本告诉我；也可先在设置里切「离线兜底」排除 Key 问题。

**想打正式签名包？**  
需要 keystore。当前先用 Debug 包足够自用安装测试。

## 本地对应文件

```text
.github/workflows/build-apk.yml   ← 云端打包脚本
android/                          ← Android 工程
```
