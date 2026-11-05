# Singularity — Send to Phone (Web Extension)

Instantly transfer active browser tabs, hyperlinks, and media URLs from your Linux desktop to your paired Android phone. Links open automatically in their associated Android app (YouTube, Reddit, GitHub, Twitter, Maps, etc.) or your default mobile browser.

---

## 1. Installation

This extension is built with **Manifest V3** and is compatible with any Chromium-based browser (**Google Chrome**, **Brave**, **Microsoft Edge**, **Vivaldi**, **Chromium**).

### Steps:
1. Open your browser and navigate to the Extensions page:
   - **Chrome / Chromium:** `chrome://extensions`
   - **Brave:** `brave://extensions`
   - **Edge:** `edge://extensions`
2. Enable **Developer mode** (toggle switch in the top-right corner).
3. Click **Load unpacked** in the top-left toolbar.
4. Select the `extension/` directory from this repository:
   ```text
   /home/kanhaji/Projects/singularity/extension
   ```
5. Pin the **Singularity — Send to Phone** icon to your browser toolbar for easy access.

---

## 2. Usage

### 🚀 1. One-Click Toolbar Push
- While browsing any webpage, click the **Singularity icon** in your extensions toolbar.
- The extension displays a `...` badge while dispatching, transitions to `✓` upon delivery, and triggers a desktop notification.
- The page opens immediately on your connected Android phone.

### ⏱️ 2. Smart YouTube Timestamp Sync
- When you are watching a **YouTube video or Short** on your desktop and click the extension icon or press `Alt + Shift + S`, Singularity automatically queries the video element's current playback position.
- It attaches the precise timestamp (e.g. `&t=142s`), seamlessly resuming the video on your phone from the exact second you left off.
- The desktop notification displays the captured timestamp (e.g., `Video Title (at 2:22)`).

### ⌨️ 3. Keyboard Shortcut
- Press **`Alt + Shift + S`** (or customized via `chrome://extensions/shortcuts`).
- Instantly pushes the active tab without having to click the mouse.

### 🔗 3. Right-Click Context Menus
- Right-click any hyperlink: Select **📱 Send link to Phone**.
- Right-click any image, video, or audio file: Select **📱 Send media to Phone**.
- Right-click anywhere on the page: Select **📱 Send current page to Phone**.

---

## 3. Configuration

By default, the extension connects to the local Singularity HTTP file server on `http://127.0.0.1:8920/api/browser/open`.

If you run `singularity-daemon` on a custom port or remote host, you can update the endpoint in browser storage:
```javascript
chrome.storage.sync.set({ daemonEndpoint: "http://127.0.0.1:8920" });
```

---

## 4. Verification

You can verify that the daemon endpoint is reachable via terminal:
```bash
curl -X POST http://127.0.0.1:8920/api/browser/open \
  -H "Content-Type: application/json" \
  -d '{"url":"https://github.com"}'
```
Or dispatch a link via the CLI:
```bash
singularity open-url "https://github.com"
```

