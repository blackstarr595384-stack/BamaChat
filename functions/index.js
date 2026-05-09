const { onRequest } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");

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

function isAuthorized(req) {
  const expectedToken = process.env.WEBSEARCH_TOKEN;
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
