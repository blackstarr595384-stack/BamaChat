const axios = require("axios");
const crypto = require("crypto");

const HF_TOKEN = String(process.env.HF_TOKEN || "").trim();
const HF_MODEL = String(process.env.HF_MODEL || "meta-llama/Llama-2-7b-chat-hf").trim();
const ALLOWED_ORIGIN = String(process.env.ALLOWED_ORIGIN || "").trim();
const PROXY_AUTH_TOKEN = String(process.env.PROXY_AUTH_TOKEN || "").trim();
const ALLOW_MISSING_ORIGIN = String(process.env.ALLOW_MISSING_ORIGIN || "true").toLowerCase() !== "false";
const RATE_LIMIT_WINDOW_MS = Number(process.env.RATE_LIMIT_WINDOW_MS || 60000);
const RATE_LIMIT_MAX_REQUESTS = Number(process.env.RATE_LIMIT_MAX_REQUESTS || 30);

const rateLimitStore = new Map();

function isOriginAllowed(originHeader) {
  if (!originHeader) return ALLOW_MISSING_ORIGIN;
  if (!ALLOWED_ORIGIN) return false;
  return originHeader === ALLOWED_ORIGIN;
}

function isProxyTokenValid(req) {
  if (!PROXY_AUTH_TOKEN) return true;
  const headerToken = String(req.headers["x-proxy-token"] || "").trim();
  return headerToken.length > 0 && headerToken === PROXY_AUTH_TOKEN;
}

function buildRateLimitKey(req) {
  const forwardedFor = String(req.headers["x-forwarded-for"] || "").split(",")[0].trim();
  const ip = forwardedFor || "unknown";
  const token = String(req.headers["x-proxy-token"] || "").trim();
  const tokenHash = token
    ? crypto.createHash("sha256").update(token).digest("hex").slice(0, 16)
    : "anon";
  return `${ip}:${tokenHash}`;
}

function consumeRateLimit(key) {
  const now = Date.now();
  const existing = rateLimitStore.get(key);
  if (!existing || now >= existing.resetAt) {
    const next = {
      count: 1,
      resetAt: now + RATE_LIMIT_WINDOW_MS,
    };
    rateLimitStore.set(key, next);
    return {
      allowed: true,
      remaining: Math.max(0, RATE_LIMIT_MAX_REQUESTS - next.count),
      retryAfterSeconds: Math.ceil(RATE_LIMIT_WINDOW_MS / 1000),
    };
  }

  existing.count += 1;
  rateLimitStore.set(key, existing);
  const remaining = Math.max(0, RATE_LIMIT_MAX_REQUESTS - existing.count);
  const retryAfterSeconds = Math.max(1, Math.ceil((existing.resetAt - now) / 1000));
  return {
    allowed: existing.count <= RATE_LIMIT_MAX_REQUESTS,
    remaining,
    retryAfterSeconds,
  };
}

module.exports = async (req, res) => {
  const origin = String(req.headers.origin || "").trim();
  if (isOriginAllowed(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
  }
  res.setHeader("Access-Control-Allow-Methods", "POST,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type,x-proxy-token");

  if (req.method === "OPTIONS") {
    return res.status(204).end();
  }
  if (req.method !== "POST") {
    return res.status(405).json({ error: "method not allowed" });
  }
  if (!isOriginAllowed(origin)) {
    return res.status(403).json({ error: "origin not allowed" });
  }
  if (!isProxyTokenValid(req)) {
    return res.status(401).json({ error: "unauthorized" });
  }
  const limit = consumeRateLimit(buildRateLimitKey(req));
  res.setHeader("X-RateLimit-Limit", String(RATE_LIMIT_MAX_REQUESTS));
  res.setHeader("X-RateLimit-Remaining", String(limit.remaining));
  if (!limit.allowed) {
    res.setHeader("Retry-After", String(limit.retryAfterSeconds));
    return res.status(429).json({ error: "rate limit exceeded" });
  }
  if (!HF_TOKEN) {
    return res.status(500).json({ error: "Server is not configured (missing HF_TOKEN)." });
  }

  try {
    const message = String(req.body?.message || "").trim();
    if (!message) {
      return res.status(400).json({ error: "message is required" });
    }

    const upstream = await axios.post(
      `https://api-inference.huggingface.co/models/${HF_MODEL}`,
      { inputs: message },
      {
        headers: {
          Authorization: `Bearer ${HF_TOKEN}`,
          "Content-Type": "application/json",
        },
        timeout: 45000,
      }
    );

    const generated = Array.isArray(upstream.data)
      ? upstream.data?.[0]?.generated_text
      : upstream.data?.generated_text;

    if (!generated || typeof generated !== "string") {
      return res.status(502).json({ error: "Invalid upstream response" });
    }

    return res.status(200).json({ reply: generated });
  } catch (error) {
    const status = error?.response?.status;
    const data = error?.response?.data;
    const detail = typeof data === "string" ? data : JSON.stringify(data || {});
    console.error("Vercel /api/chat failed", status || "", detail || error.message);
    return res.status(500).json({ error: "AI request failed" });
  }
};
