const { onRequest } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
let sharp = null;
try {
  sharp = require("sharp");
} catch (error) {
  logger.warn("sharp_not_installed", { message: error?.message || String(error) });
}

if (admin.apps.length === 0) {
  admin.initializeApp();
}

const DEFAULT_ALLOWED_DOMAINS = [
  "wikipedia.org",
  "reuters.com",
  "tagesschau.de",
  "bundesregierung.de",
  "heise.de",
  "github.com"
];

const MAX_RESULTS = 8;
const DEFAULT_RESULTS = 5;
const MAX_QUERY_LENGTH = 280;
const RATE_LIMIT_WINDOW_MS = 60 * 1000;
const RATE_LIMIT_MAX_REQUESTS = 20;
const PHOTO_MAX_INPUT_BYTES = 8 * 1024 * 1024;
const PHOTO_MAX_SIDE = 4096;
const PHOTO_DEFAULT_UPSCALE = 2;
const PHOTO_REMOVE_BG_TIMEOUT_MS = 45 * 1000;
const PHOTO_PROVIDER_BG_LOCAL = "local_heuristic";
const PHOTO_PROVIDER_BG_REMOVEBG = "removebg";
const PHOTO_PROVIDER_UPSCALE_LOCAL = "local_lanczos";
const requestCounters = new Map();

exports.webSearch = onRequest(
  {
    region: "europe-west1",
    cors: true,
    invoker: "public",
    timeoutSeconds: 30,
    memory: "256MiB"
  },
  async (req, res) => {
    res.set("Cache-Control", "no-store");

    if (req.method !== "POST") {
      res.status(405).json({ error: "Use POST" });
      return;
    }

    if (isRateLimited(req)) {
      res.status(429).json({ error: "Too many requests" });
      return;
    }

    if (!isAuthorized(req)) {
      res.status(401).json({ error: "Unauthorized" });
      return;
    }

    const query = String(req.body?.query || "").trim().slice(0, MAX_QUERY_LENGTH);
    if (!query) {
      res.status(400).json({ error: "query is required" });
      return;
    }

    const requestedResults = Number(req.body?.maxResults || DEFAULT_RESULTS);
    const maxResults = Math.max(2, Math.min(MAX_RESULTS, Number.isFinite(requestedResults) ? requestedResults : DEFAULT_RESULTS));
    const locale = normalizeLocale(req.body?.locale);
    const allowedDomains = normalizeAllowedDomains(req.body?.allowedDomains);
    const preferGithub = Boolean(req.body?.preferGithub);

    try {
      const braveToken = process.env.BRAVE_SEARCH_API_KEY;
      const githubToken = process.env.GITHUB_TOKEN;
      const braveResults = braveToken
        ? await searchWithBrave(query, maxResults, locale, braveToken)
        : [];
      const githubResults = await searchWithGitHub(query, maxResults, githubToken);

      const webProvider = braveResults.length > 0 ? "brave" : "duckduckgo";
      const fallbackResults = braveResults.length > 0 ? braveResults : await searchWithDuckDuckGo(query, maxResults);
      const provider = githubResults.length > 0 ? `${webProvider}+github` : webProvider;
      const merged = preferGithub
        ? dedupeByUrl([...githubResults, ...fallbackResults])
        : dedupeByUrl([...fallbackResults, ...githubResults]);

      const filtered = merged
        .filter((item) => isAllowedUrl(item.url, allowedDomains))
        .slice(0, maxResults);

      res.json({
        query,
        provider,
        preferGithub,
        fetchedAt: new Date().toISOString(),
        results: filtered
      });
    } catch (error) {
      logger.error("webSearch_failed", { message: error?.message || String(error) });
      res.status(500).json({ error: "Web search failed" });
    }
  }
);

exports.photoEdit = onRequest(
  {
    region: "europe-west1",
    cors: true,
    invoker: "public",
    timeoutSeconds: 120,
    memory: "1GiB"
  },
  async (req, res) => {
    res.set("Cache-Control", "no-store");

    if (req.method !== "POST") {
      res.status(405).json({ error: "Use POST" });
      return;
    }
    if (isRateLimited(req)) {
      res.status(429).json({ error: "Too many requests" });
      return;
    }
    if (!isAuthorized(req, "PHOTO_AI_TOKEN")) {
      res.status(401).json({ error: "Unauthorized" });
      return;
    }
    if (!sharp) {
      res.status(500).json({ error: "Image engine not available (sharp missing)." });
      return;
    }

    const action = normalizePhotoAction(req.body?.action);
    if (!action) {
      res.status(400).json({ error: "action must be 'background_remove' or 'upscale_hd'" });
      return;
    }
    const qualityMode = normalizeQualityMode(req.body?.qualityMode);

    const inputBuffer = decodeBase64Image(req.body?.imageBase64);
    if (!inputBuffer || inputBuffer.length === 0) {
      res.status(400).json({ error: "imageBase64 is required" });
      return;
    }
    if (inputBuffer.length > PHOTO_MAX_INPUT_BYTES) {
      res.status(413).json({ error: `image too large (max ${PHOTO_MAX_INPUT_BYTES} bytes)` });
      return;
    }

    const startedAt = Date.now();
    try {
      let outputBuffer;
      let outputMime;
      let provider = "";
      if (action === "background_remove") {
        provider = PHOTO_PROVIDER_BG_LOCAL;
        const removeBgApiKey = String(process.env.REMOVE_BG_API_KEY || "").trim();
        if (removeBgApiKey) {
          const remoteResult = await tryRemoveBgWithProvider(
            inputBuffer,
            removeBgApiKey
          );
          if (remoteResult.buffer && remoteResult.buffer.length > 0) {
            outputBuffer = remoteResult.buffer;
            provider = PHOTO_PROVIDER_BG_REMOVEBG;
          } else {
            logger.warn("photoEdit_removebg_fallback", {
              reason: remoteResult.error || "unknown"
            });
          }
        }
        if (!outputBuffer) {
          outputBuffer = await removeBackgroundApprox(inputBuffer);
        }
        outputMime = "image/png";
      } else {
        const factor = clampNumber(Number(req.body?.upscaleFactor || PHOTO_DEFAULT_UPSCALE), 1.1, 4);
        const upscaled = await upscaleImage(inputBuffer, factor, qualityMode);
        outputBuffer = upscaled.buffer;
        outputMime = upscaled.mimeType;
        provider = PHOTO_PROVIDER_UPSCALE_LOCAL;
      }

      const meta = await sharp(outputBuffer).metadata();
      const processingMs = Date.now() - startedAt;
      logger.info("photoEdit_success", {
        action,
        provider,
        qualityMode,
        width: Number(meta.width || 0),
        height: Number(meta.height || 0),
        bytes: outputBuffer.length,
        processingMs
      });
      res.json({
        success: true,
        action,
        provider,
        qualityMode,
        mimeType: outputMime,
        imageBase64: outputBuffer.toString("base64"),
        meta: {
          width: Number(meta.width || 0),
          height: Number(meta.height || 0),
          bytes: outputBuffer.length,
          processingMs
        }
      });
    } catch (error) {
      logger.error("photoEdit_failed", {
        action,
        message: error?.message || String(error)
      });
      res.status(500).json({ error: "Photo processing failed" });
    }
  }
);

exports.deleteAccount = onRequest(
  {
    region: "europe-west1",
    cors: true,
    invoker: "public",
    timeoutSeconds: 120,
    memory: "512MiB"
  },
  async (req, res) => {
    res.set("Cache-Control", "no-store");

    if (req.method !== "POST") {
      res.status(405).json({ error: "Use POST" });
      return;
    }

    const idToken = extractBearerToken(req);
    if (!idToken) {
      res.status(401).json({ error: "Unauthorized" });
      return;
    }

    let decodedToken;
    try {
      decodedToken = await admin.auth().verifyIdToken(idToken);
    } catch (error) {
      logger.warn("deleteAccount_token_invalid", {
        message: error?.message || String(error)
      });
      res.status(401).json({ error: "Unauthorized" });
      return;
    }

    const uid = String(decodedToken?.uid || "").trim();
    if (!uid) {
      res.status(401).json({ error: "Unauthorized" });
      return;
    }

    try {
      await deleteUserFirestoreData(uid);
      await deleteOwnedCollabSessions(uid);
      await detachUserFromCollaborations(uid);
      await deleteStoragePrefix(`profile_images/${uid}.jpg`);
      await deleteStoragePrefix(`profile_images/${uid}/`);
      await deleteStoragePrefix(`rag_docs/${uid}/`);

      try {
        await admin.auth().deleteUser(uid);
      } catch (error) {
        if (!isIgnorableAuthDeleteError(error)) {
          throw error;
        }
      }

      logger.info("deleteAccount_success", { uid });
      res.json({
        success: true,
        uid
      });
    } catch (error) {
      logger.error("deleteAccount_failed", {
        uid,
        message: error?.message || String(error)
      });
      res.status(500).json({ error: "Account deletion failed" });
    }
  }
);

async function deleteUserFirestoreData(uid) {
  const firestore = admin.firestore();
  await firestore.recursiveDelete(firestore.collection("users").doc(uid));
  await firestore.recursiveDelete(firestore.collection("user_memory").doc(uid));
  await firestore.recursiveDelete(firestore.collection("user_knowledge").doc(uid));
}

async function deleteOwnedCollabSessions(uid) {
  const firestore = admin.firestore();
  const snapshot = await firestore.collection("collab_sessions")
    .where("ownerId", "==", uid)
    .get();

  for (const doc of snapshot.docs) {
    await firestore.recursiveDelete(doc.ref);
  }
}

async function detachUserFromCollaborations(uid) {
  const firestore = admin.firestore();
  const snapshot = await firestore.collection("collab_sessions")
    .where("participants", "array-contains", uid)
    .get();

  for (const doc of snapshot.docs) {
    const data = doc.data() || {};
    if (String(data.ownerId || "").trim() === uid) {
      continue;
    }

    const participants = Array.isArray(data.participants)
      ? data.participants.filter((participant) => String(participant || "").trim() !== uid)
      : [];
    const participantRoles = { ...(data.participantRoles || {}) };
    delete participantRoles[uid];

    const updates = {
      participants,
      participantRoles
    };

    if (participants.length === 0) {
      await firestore.recursiveDelete(doc.ref);
      continue;
    }

    await doc.ref.update(updates);
    await doc.ref.collection("presence").doc(uid).delete().catch((error) => {
      if (!isIgnorableNotFoundError(error)) {
        throw error;
      }
    });
  }
}

async function deleteStoragePrefix(prefix) {
  const bucket = admin.storage().bucket();
  const [files] = await bucket.getFiles({ prefix });
  for (const file of files) {
    await file.delete().catch((error) => {
      if (!isIgnorableNotFoundError(error)) {
        throw error;
      }
    });
  }
}

function extractBearerToken(req) {
  const authHeader = String(req.get("authorization") || "").trim();
  if (!authHeader.startsWith("Bearer ")) return "";
  return authHeader.slice(7).trim();
}

function isIgnorableAuthDeleteError(error) {
  const code = String(error?.code || error?.errorInfo?.code || "").toLowerCase();
  return code === "auth/user-not-found";
}

function isIgnorableNotFoundError(error) {
  const code = Number(error?.code || 0);
  const message = String(error?.message || "").toLowerCase();
  return code === 404 || message.includes("not found") || message.includes("no such object");
}

function isAuthorized(req, tokenEnvName = "WEBSEARCH_TOKEN") {
  const expectedToken = process.env[tokenEnvName] || process.env.WEBSEARCH_TOKEN;
  if (!expectedToken) return true;
  const authHeader = String(req.get("authorization") || "");
  const bearer = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
  return bearer && bearer === expectedToken;
}

function isRateLimited(req) {
  const now = Date.now();
  const ip = getClientIp(req);
  const timestamps = requestCounters.get(ip) || [];
  const fresh = timestamps.filter((ts) => now - ts <= RATE_LIMIT_WINDOW_MS);
  if (fresh.length >= RATE_LIMIT_MAX_REQUESTS) {
    requestCounters.set(ip, fresh);
    return true;
  }
  fresh.push(now);
  requestCounters.set(ip, fresh);
  return false;
}

function getClientIp(req) {
  const forwardedFor = String(req.get("x-forwarded-for") || "");
  const first = forwardedFor.split(",")[0].trim();
  if (first) return first;
  return req.ip || "unknown";
}

function normalizeLocale(raw) {
  const locale = String(raw || "de-DE").trim();
  if (!locale) return "de-DE";
  return locale.slice(0, 20);
}

function normalizeAllowedDomains(raw) {
  if (!Array.isArray(raw) || raw.length === 0) return DEFAULT_ALLOWED_DOMAINS;
  const cleaned = raw
    .map((value) => String(value || "").trim().toLowerCase())
    .filter(Boolean)
    .slice(0, 25);
  return cleaned.length > 0 ? cleaned : DEFAULT_ALLOWED_DOMAINS;
}

function isAllowedUrl(url, allowedDomains) {
  try {
    const host = new URL(url).hostname.toLowerCase();
    return allowedDomains.some((domain) => host === domain || host.endsWith(`.${domain}`));
  } catch (_) {
    return false;
  }
}

async function searchWithBrave(query, maxResults, locale, apiKey) {
  const url = new URL("https://api.search.brave.com/res/v1/web/search");
  url.searchParams.set("q", query);
  url.searchParams.set("count", String(maxResults));
  url.searchParams.set("search_lang", locale.slice(0, 2).toLowerCase());
  url.searchParams.set("safesearch", "strict");

  const response = await fetch(url.toString(), {
    headers: {
      "Accept": "application/json",
      "X-Subscription-Token": apiKey
    }
  });
  if (!response.ok) {
    throw new Error(`Brave search failed (${response.status})`);
  }

  const json = await response.json();
  const results = Array.isArray(json?.web?.results) ? json.web.results : [];
  return results.map((entry) => ({
    title: safeText(entry?.title, 160),
    url: safeUrl(entry?.url),
    snippet: safeText(entry?.description, 360),
    publishedAt: safeText(entry?.age, 40)
  })).filter((item) => item.title && item.url);
}

async function searchWithDuckDuckGo(query, maxResults) {
  const url = new URL("https://duckduckgo.com/html/");
  url.searchParams.set("q", query);
  url.searchParams.set("kp", "1");

  const response = await fetch(url.toString(), {
    headers: {
      "Accept": "text/html",
      "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
    }
  });
  if (!response.ok) {
    throw new Error(`DuckDuckGo fallback failed (${response.status})`);
  }

  const html = await response.text();
  const flat = [];
  const pattern = /<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)<\/a>[\s\S]*?<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*>([\s\S]*?)<\/a>/gi;
  let match;

  while ((match = pattern.exec(html)) !== null && flat.length < maxResults) {
    const rawHref = decodeHtmlEntities(match[1] || "");
    const resolved = resolveDuckDuckGoRedirect(rawHref);
    const title = safeText(stripHtml(match[2] || ""), 160);
    const snippet = safeText(stripHtml(match[3] || ""), 360);
    if (!resolved || !title) continue;
    flat.push({
      title,
      url: resolved,
      snippet,
      publishedAt: ""
    });
  }

  return flat;
}

async function searchWithGitHub(query, maxResults, token) {
  const perPage = Math.max(1, Math.min(maxResults, 5));
  const headers = {
    "Accept": "application/vnd.github+json",
    "User-Agent": "bamachat-web-research"
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const repoUrl = new URL("https://api.github.com/search/repositories");
  repoUrl.searchParams.set("q", query);
  repoUrl.searchParams.set("sort", "stars");
  repoUrl.searchParams.set("order", "desc");
  repoUrl.searchParams.set("per_page", String(perPage));

  const issueUrl = new URL("https://api.github.com/search/issues");
  issueUrl.searchParams.set("q", `${query} is:issue`);
  issueUrl.searchParams.set("sort", "updated");
  issueUrl.searchParams.set("order", "desc");
  issueUrl.searchParams.set("per_page", String(perPage));

  try {
    const [reposRes, issuesRes] = await Promise.all([
      fetch(repoUrl.toString(), { headers }),
      fetch(issueUrl.toString(), { headers })
    ]);

    const reposJson = reposRes.ok ? await reposRes.json() : {};
    const issuesJson = issuesRes.ok ? await issuesRes.json() : {};

    const repoItems = Array.isArray(reposJson?.items) ? reposJson.items : [];
    const issueItems = Array.isArray(issuesJson?.items) ? issuesJson.items : [];

    const repoResults = repoItems.map((repo) => ({
      title: safeText(`[GitHub Repo] ${repo?.full_name || repo?.name || ""}`, 160),
      url: safeUrl(repo?.html_url),
      snippet: safeText(repo?.description, 360),
      publishedAt: safeText(repo?.updated_at, 40)
    }));

    const issueResults = issueItems.map((issue) => ({
      title: safeText(`[GitHub Issue] ${issue?.title || ""}`, 160),
      url: safeUrl(issue?.html_url),
      snippet: safeText(issue?.body || "", 360),
      publishedAt: safeText(issue?.updated_at, 40)
    }));

    return dedupeByUrl([...repoResults, ...issueResults])
      .filter((item) => item.title && item.url)
      .slice(0, maxResults);
  } catch (error) {
    logger.warn("github_search_failed", { message: error?.message || String(error) });
    return [];
  }
}

function dedupeByUrl(items) {
  const seen = new Set();
  const result = [];
  for (const item of items) {
    const url = safeUrl(item?.url);
    if (!url || seen.has(url)) continue;
    seen.add(url);
    result.push({ ...item, url });
  }
  return result;
}

function safeText(value, maxLen) {
  const clean = String(value || "")
    .replace(/\s+/g, " ")
    .replace(/<[^>]+>/g, "")
    .trim();
  return clean.slice(0, maxLen);
}

function safeUrl(value) {
  const raw = String(value || "").trim();
  if (!raw) return "";
  try {
    const parsed = new URL(raw);
    if (!["http:", "https:"].includes(parsed.protocol)) return "";
    return parsed.toString();
  } catch (_) {
    return "";
  }
}

function stripHtml(value) {
  return decodeHtmlEntities(String(value || "").replace(/<[^>]+>/g, " "));
}

function decodeHtmlEntities(value) {
  return String(value || "")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">");
}

function resolveDuckDuckGoRedirect(url) {
  const normalized = String(url || "").trim().startsWith("//")
    ? `https:${String(url || "").trim()}`
    : String(url || "").trim();
  const clean = safeUrl(normalized);
  if (!clean) return "";
  try {
    const parsed = new URL(clean);
    if (parsed.hostname.endsWith("duckduckgo.com") && parsed.pathname === "/l/") {
      const target = parsed.searchParams.get("uddg");
      return target ? safeUrl(decodeURIComponent(target)) : "";
    }
    return clean;
  } catch (_) {
    return "";
  }
}

function normalizePhotoAction(raw) {
  const value = String(raw || "").trim().toLowerCase();
  if (value === "background_remove") return value;
  if (value === "upscale_hd") return value;
  return "";
}

function normalizeQualityMode(raw) {
  const value = String(raw || "").trim().toLowerCase();
  if (value === "fast") return "fast";
  if (value === "high") return "high";
  return "balanced";
}

function decodeBase64Image(raw) {
  const value = String(raw || "").trim();
  if (!value) return null;
  const commaIndex = value.indexOf(",");
  const payload = value.startsWith("data:") && commaIndex >= 0
    ? value.slice(commaIndex + 1)
    : value;
  try {
    const clean = payload.replace(/\s+/g, "");
    return Buffer.from(clean, "base64");
  } catch (_) {
    return null;
  }
}

function clampNumber(value, min, max) {
  if (!Number.isFinite(value)) return min;
  return Math.min(max, Math.max(min, value));
}

async function tryRemoveBgWithProvider(inputBuffer, apiKey) {
  const formData = new FormData();
  formData.set("size", "auto");
  formData.set("format", "png");
  formData.set("image_file_b64", inputBuffer.toString("base64"));

  try {
    const response = await fetch("https://api.remove.bg/v1.0/removebg", {
      method: "POST",
      headers: {
        "X-Api-Key": apiKey
      },
      body: formData,
      signal: AbortSignal.timeout(PHOTO_REMOVE_BG_TIMEOUT_MS)
    });
    if (!response.ok) {
      const errorBody = await response.text();
      return {
        buffer: null,
        error: `remove.bg failed (${response.status}) ${String(errorBody || "").slice(0, 160)}`
      };
    }
    const data = await response.arrayBuffer();
    return {
      buffer: Buffer.from(data),
      error: ""
    };
  } catch (error) {
    return {
      buffer: null,
      error: error?.message || String(error)
    };
  }
}

async function upscaleImage(inputBuffer, factor, qualityMode = "balanced") {
  const metadata = await sharp(inputBuffer).metadata();
  const srcWidth = Number(metadata.width || 0);
  const srcHeight = Number(metadata.height || 0);
  if (srcWidth <= 0 || srcHeight <= 0) {
    throw new Error("Unable to determine image size");
  }
  const targetWidth = Math.min(PHOTO_MAX_SIDE, Math.max(1, Math.round(srcWidth * factor)));
  const targetHeight = Math.min(PHOTO_MAX_SIDE, Math.max(1, Math.round(srcHeight * factor)));
  const hasAlpha = Boolean(metadata.hasAlpha);

  const kernel = qualityMode === "fast" ? sharp.kernel.cubic : sharp.kernel.lanczos3;
  let processor = sharp(inputBuffer)
    .rotate()
    .resize({
      width: targetWidth,
      height: targetHeight,
      fit: "fill",
      kernel
    });

  if (qualityMode === "high") {
    processor = processor
      .sharpen({ sigma: 1.2, m1: 0.25, m2: 2.8 })
      .modulate({ brightness: 1.01, saturation: 1.03 });
  } else if (qualityMode === "balanced") {
    processor = processor.sharpen({ sigma: 1.05, m1: 0.2, m2: 2.2 });
  }

  if (hasAlpha) {
    const buffer = await processor.png({ compressionLevel: 9 }).toBuffer();
    return { buffer, mimeType: "image/png" };
  }
  const buffer = await processor.jpeg({
    quality: 94,
    chromaSubsampling: "4:4:4",
    mozjpeg: true
  }).toBuffer();
  return { buffer, mimeType: "image/jpeg" };
}

async function removeBackgroundApprox(inputBuffer) {
  const { data, info } = await sharp(inputBuffer)
    .rotate()
    .ensureAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });
  const width = Number(info.width || 0);
  const height = Number(info.height || 0);
  const channels = Number(info.channels || 4);
  if (width <= 0 || height <= 0 || channels < 4) {
    throw new Error("Unsupported raw image format");
  }

  const bg = estimateCornerBackground(data, width, height, channels);
  const tolerance = estimateBackgroundTolerance(data, width, height, channels, bg);
  const visited = new Uint8Array(width * height);
  const queue = new Uint32Array(width * height);
  let queueHead = 0;
  let queueTail = 0;

  const maybeVisit = (x, y) => {
    if (x < 0 || y < 0 || x >= width || y >= height) return;
    const idx = y * width + x;
    if (visited[idx]) return;
    const px = idx * channels;
    const dist = colorDistance(data[px], data[px + 1], data[px + 2], bg.r, bg.g, bg.b);
    if (dist <= tolerance) {
      visited[idx] = 1;
      queue[queueTail++] = idx;
    }
  };

  for (let x = 0; x < width; x++) {
    maybeVisit(x, 0);
    maybeVisit(x, height - 1);
  }
  for (let y = 0; y < height; y++) {
    maybeVisit(0, y);
    maybeVisit(width - 1, y);
  }

  while (queueHead < queueTail) {
    const idx = queue[queueHead++];
    const y = Math.floor(idx / width);
    const x = idx - (y * width);
    maybeVisit(x + 1, y);
    maybeVisit(x - 1, y);
    maybeVisit(x, y + 1);
    maybeVisit(x, y - 1);
  }

  const softBand = 22;
  for (let i = 0; i < width * height; i++) {
    const px = i * channels;
    if (visited[i]) {
      data[px + 3] = 0;
      continue;
    }
    const dist = colorDistance(data[px], data[px + 1], data[px + 2], bg.r, bg.g, bg.b);
    if (dist <= tolerance + softBand) {
      const normalized = Math.max(0, Math.min(1, (dist - tolerance) / softBand));
      const alpha = Math.round(normalized * 255);
      data[px + 3] = Math.min(data[px + 3], alpha);
    }
  }

  return sharp(data, { raw: { width, height, channels: 4 } })
    .png({ compressionLevel: 9 })
    .toBuffer();
}

function estimateCornerBackground(data, width, height, channels) {
  const patch = Math.max(2, Math.min(12, Math.floor(Math.min(width, height) / 18)));
  const samples = [];
  const corners = [
    [0, 0],
    [Math.max(0, width - patch), 0],
    [0, Math.max(0, height - patch)],
    [Math.max(0, width - patch), Math.max(0, height - patch)]
  ];

  for (const [startX, startY] of corners) {
    for (let y = startY; y < Math.min(height, startY + patch); y++) {
      for (let x = startX; x < Math.min(width, startX + patch); x++) {
        const px = (y * width + x) * channels;
        samples.push([data[px], data[px + 1], data[px + 2]]);
      }
    }
  }

  if (samples.length === 0) {
    return { r: 255, g: 255, b: 255 };
  }

  let r = 0;
  let g = 0;
  let b = 0;
  for (const s of samples) {
    r += s[0];
    g += s[1];
    b += s[2];
  }
  return {
    r: Math.round(r / samples.length),
    g: Math.round(g / samples.length),
    b: Math.round(b / samples.length)
  };
}

function estimateBackgroundTolerance(data, width, height, channels, bg) {
  const patch = Math.max(2, Math.min(14, Math.floor(Math.min(width, height) / 16)));
  const distances = [];
  const corners = [
    [0, 0],
    [Math.max(0, width - patch), 0],
    [0, Math.max(0, height - patch)],
    [Math.max(0, width - patch), Math.max(0, height - patch)]
  ];

  for (const [startX, startY] of corners) {
    for (let y = startY; y < Math.min(height, startY + patch); y++) {
      for (let x = startX; x < Math.min(width, startX + patch); x++) {
        const px = (y * width + x) * channels;
        distances.push(colorDistance(data[px], data[px + 1], data[px + 2], bg.r, bg.g, bg.b));
      }
    }
  }
  if (distances.length === 0) return 28;
  const mean = distances.reduce((sum, value) => sum + value, 0) / distances.length;
  const variance = distances.reduce((sum, value) => {
    const delta = value - mean;
    return sum + delta * delta;
  }, 0) / distances.length;
  const std = Math.sqrt(Math.max(0, variance));
  return clampNumber(Math.round(mean + std * 2.2), 18, 95);
}

function colorDistance(r1, g1, b1, r2, g2, b2) {
  const dr = r1 - r2;
  const dg = g1 - g2;
  const db = b1 - b2;
  return Math.sqrt(dr * dr + dg * dg + db * db);
}
