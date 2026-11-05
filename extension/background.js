const DEFAULT_ENDPOINT = "http://127.0.0.1:8920";
const REQUEST_TIMEOUT_MS = 5000;

/**
 * Retrieves the configured daemon base endpoint URL.
 * @returns {Promise<string>}
 */
async function getEndpoint() {
  return new Promise((resolve) => {
    chrome.storage.sync.get({ daemonEndpoint: DEFAULT_ENDPOINT }, (items) => {
      resolve(items.daemonEndpoint || DEFAULT_ENDPOINT);
    });
  });
}

/**
 * Validates whether a target URL is dispatchable to Android.
 * @param {string} url
 * @returns {boolean}
 */
function isDispatchableUrl(url) {
  if (!url || typeof url !== "string") return false;
  const trimmed = url.trim().toLowerCase();
  return (
    trimmed.startsWith("http://") ||
    trimmed.startsWith("https://") ||
    trimmed.startsWith("ftp://") ||
    trimmed.startsWith("magnet:")
  );
}

/**
 * Sets temporary badge text and color on the extension action.
 * @param {string} text
 * @param {string} color
 * @param {number} durationMs
 */
function setActionBadge(text, color, durationMs = 0) {
  chrome.action.setBadgeText({ text });
  chrome.action.setBadgeBackgroundColor({ color });

  if (durationMs > 0) {
    setTimeout(() => {
      chrome.action.setBadgeText({ text: "" });
    }, durationMs);
  }
}

/**
 * Displays a system desktop notification.
 * @param {string} title
 * @param {string} message
 * @param {number} priority
 */
function showNotification(title, message, priority = 0) {
  chrome.notifications.create({
    type: "basic",
    iconUrl: "icons/icon128.png",
    title,
    message,
    priority,
  });
}

/**
 * Injected script executed in the active tab context to extract media playback timestamp.
 * Detects playing or active HTML5 video element (YouTube, etc.).
 * @returns {number|null}
 */
function extractMediaTimestampInTab() {
  try {
    // If an ad is currently playing on YouTube, avoid capturing ad duration
    if (document.querySelector(".ad-showing, .ad-interrupting")) {
      return null;
    }

    const videos = Array.from(document.querySelectorAll("video"));
    for (const video of videos) {
      if (video && !isNaN(video.currentTime) && video.currentTime > 1) {
        return Math.floor(video.currentTime);
      }
    }
  } catch (_) {}
  return null;
}

/**
 * Formats seconds into MM:SS or HH:MM:SS format for UI notifications.
 * @param {number} totalSeconds
 * @returns {string}
 */
function formatTimeDisplay(totalSeconds) {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
  }
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

/**
 * Resolves the target URL for a tab, appending media timestamp for YouTube videos if available.
 * @param {chrome.tabs.Tab} tab
 * @returns {Promise<{ url: string, displayTitle: string }>}
 */
async function resolveTabUrl(tab) {
  if (!tab || !tab.url) {
    return { url: "", displayTitle: "" };
  }

  let finalUrl = tab.url;
  let displayTitle = tab.title || "";

  try {
    const urlObj = new URL(tab.url);
    const host = urlObj.hostname.toLowerCase();
    const isYouTube = host.includes("youtube.com") || host.includes("youtu.be");

    if (isYouTube && tab.id) {
      const results = await chrome.scripting.executeScript({
        target: { tabId: tab.id },
        func: extractMediaTimestampInTab,
      });

      if (results && results[0] && typeof results[0].result === "number") {
        const timestamp = results[0].result;
        if (timestamp > 0) {
          // If viewing a YouTube Short (/shorts/ID), convert to /watch?v=ID so timestamping works reliably
          if (urlObj.pathname.startsWith("/shorts/")) {
            const shortId = urlObj.pathname.split("/")[2];
            if (shortId) {
              urlObj.pathname = "/watch";
              urlObj.searchParams.set("v", shortId);
            }
          }

          urlObj.searchParams.set("t", `${timestamp}s`);
          finalUrl = urlObj.toString();
          displayTitle = `${displayTitle} (at ${formatTimeDisplay(timestamp)})`;
        }
      }
    }
  } catch (err) {
    console.debug("Could not extract video timestamp:", err);
  }

  return { url: finalUrl, displayTitle };
}

/**
 * Sends a URL to the Singularity desktop daemon.
 * @param {string} url
 * @param {string} [title]
 */
async function sendUrlToPhone(url, title = "") {
  if (!isDispatchableUrl(url)) {
    setActionBadge("!", "#DC2626", 2500);
    showNotification(
      "Unsupported URL",
      "Internal browser pages (chrome://, about:) cannot be dispatched.",
      1
    );
    return;
  }

  setActionBadge("...", "#2563EB", 0);

  const endpoint = await getEndpoint();
  const targetApi = `${endpoint.replace(/\/+$/, "")}/api/browser/open`;

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(targetApi, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ url }),
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      let errText = `Daemon returned HTTP ${response.status}`;
      try {
        const errJson = await response.json();
        if (errJson && errJson.error) {
          errText = errJson.error;
        }
      } catch (_) {}
      throw new Error(errText);
    }

    setActionBadge("✓", "#16A34A", 2000);
    const displayMsg = title ? `${title}\n${url}` : url;
    showNotification("Sent to Phone 📱", displayMsg, 0);
  } catch (error) {
    clearTimeout(timeoutId);
    setActionBadge("!", "#DC2626", 3000);
    const reason =
      error.name === "AbortError"
        ? "Connection timed out"
        : error.message || "Could not connect to Singularity daemon";
    showNotification("Failed to Send", reason, 1);
  }
}

/**
 * Sets up the browser context menus.
 */
function setupContextMenus() {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({
      id: "send-link-to-phone",
      title: "📱 Send link to Phone",
      contexts: ["link"],
    });

    chrome.contextMenus.create({
      id: "send-page-to-phone",
      title: "📱 Send current page to Phone",
      contexts: ["page"],
    });

    chrome.contextMenus.create({
      id: "send-media-to-phone",
      title: "📱 Send media to Phone",
      contexts: ["image", "video", "audio"],
    });
  });
}

// Lifecycle listeners
chrome.runtime.onInstalled.addListener(() => {
  setupContextMenus();
});

chrome.runtime.onStartup.addListener(() => {
  setupContextMenus();
});

// Single-click toolbar icon trigger
chrome.action.onClicked.addListener(async (tab) => {
  if (tab && tab.url) {
    const { url, displayTitle } = await resolveTabUrl(tab);
    sendUrlToPhone(url, displayTitle);
  }
});

// Context menu item click trigger
chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  if (info.menuItemId === "send-link-to-phone" && info.linkUrl) {
    sendUrlToPhone(info.linkUrl, "Hyperlink");
  } else if (info.menuItemId === "send-page-to-phone") {
    if (tab && tab.url) {
      const { url, displayTitle } = await resolveTabUrl(tab);
      sendUrlToPhone(url, displayTitle);
    } else if (info.pageUrl) {
      sendUrlToPhone(info.pageUrl, "Web Page");
    }
  } else if (info.menuItemId === "send-media-to-phone" && info.srcUrl) {
    sendUrlToPhone(info.srcUrl, "Media");
  }
});

// Keyboard command trigger (Alt+Shift+S)
chrome.commands.onCommand.addListener(async (command) => {
  if (command === "send-active-tab") {
    chrome.tabs.query({ active: true, currentWindow: true }, async (tabs) => {
      if (tabs && tabs.length > 0 && tabs[0].url) {
        const { url, displayTitle } = await resolveTabUrl(tabs[0]);
        sendUrlToPhone(url, displayTitle);
      }
    });
  }
});
