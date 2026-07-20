// Shared detection/parsing logic for the content-safety pipeline. Used by
// split-by-content-safety.js (the main diff-and-split check) and
// process-pr-commands.js (the /safe-term, /accept, /reject PR comment
// automation) so both stay in sync instead of drifting apart as
// separately-maintained copies.
"use strict";

const fs = require("fs");
const path = require("path");

const REPO_ROOT = path.resolve(__dirname, "..", "..", "..");
const LANG_DIR = path.join(
  REPO_ROOT,
  "common/src/main/resources/assets/iris_search/lang",
);
const SAFE_TERM_EXCEPTIONS_PATH = path.join(
  __dirname,
  "..",
  "..",
  "data",
  "safe-term-exceptions.json",
);

const WORDLIST_REPO = "SpacEagle17/Profanity-Filter";
const WORDLIST_PATH = "lists";

// ---------------------------------------------------------------------
// Profanity wordlists
// ---------------------------------------------------------------------

// Overrides for language identifiers whose wordlist code isn't just the
// part before the underscore (see resolveWordlistCode below). Empty for
// now - every current locale here reduces cleanly.
const PROFANITY_LANG_OVERRIDES = {};

function resolveWordlistCode(langId) {
  return (
    PROFANITY_LANG_OVERRIDES[langId] ?? langId.split(/[_-]/)[0].toLowerCase()
  );
}

// Scripts that don't reliably delimit words with spaces, so a wordlist
// entry can only be found by scanning for it as a substring - tokenizing
// on whitespace would just produce one giant "token" per sentence.
const SUBSTRING_ONLY_CODES = new Set(["ja", "zh"]);

function loadSafeTermExceptions() {
  return fs.existsSync(SAFE_TERM_EXCEPTIONS_PATH)
    ? JSON.parse(fs.readFileSync(SAFE_TERM_EXCEPTIONS_PATH, "utf8"))
    : {};
}

function saveSafeTermExceptions(exceptions) {
  fs.writeFileSync(
    SAFE_TERM_EXCEPTIONS_PATH,
    JSON.stringify(exceptions, null, 2) + "\n",
    "utf8",
  );
}

// Known-safe terms that happen to contain a flagged substring. Lives in its
// own JSON file (rather than inline here) so the PR comment automation
// (.github/workflows/pr-safe-term-command.yml) can safely add new entries
// without editing JS source.
function maskSafeTerms(text, wordlistCode, exceptions) {
  const terms = exceptions[wordlistCode];
  if (!terms) return text;
  let masked = text;
  for (const term of terms) {
    masked = masked.split(term).join(" ".repeat(term.length));
  }
  return masked;
}

const TOKEN_RE = /[\p{L}\p{N}]+/gu;

async function fetchWordlist(wordlistCode) {
  const token = process.env.PROFANITY_REPO_TOKEN;
  if (!token) {
    throw new Error(
      "PROFANITY_REPO_TOKEN env var is not set - cannot fetch wordlists.",
    );
  }
  const url = `https://api.github.com/repos/${WORDLIST_REPO}/contents/${WORDLIST_PATH}/${wordlistCode}.json`;
  const res = await fetch(url, {
    headers: {
      Authorization: `token ${token}`,
      Accept: "application/vnd.github.raw",
    },
  });
  if (!res.ok) return [];
  const words = await res.json();
  return Array.isArray(words)
    ? words.filter((w) => typeof w === "string" && w.trim())
    : [];
}

const MIN_SUBSTRING_LATIN_LENGTH = 3;
const PURE_LATIN_RE = /^[a-z]+$/;

function buildIndex(words, wordlistCode) {
  const wordSet = new Set();
  const substringList = [];
  const forceSubstring = SUBSTRING_ONLY_CODES.has(wordlistCode);

  for (const raw of words) {
    const w = raw.toLowerCase().trim();
    if (!w) continue;
    if (forceSubstring || w.includes(" ")) {
      if (PURE_LATIN_RE.test(w) && w.length < MIN_SUBSTRING_LATIN_LENGTH)
        continue;
      substringList.push(w);
    } else {
      wordSet.add(w);
    }
  }

  return { wordSet, substringList };
}

const wordlistIndexCache = new Map();
async function getWordlistIndex(wordlistCode) {
  if (wordlistIndexCache.has(wordlistCode))
    return wordlistIndexCache.get(wordlistCode);
  const words = await fetchWordlist(wordlistCode);
  const index = words.length > 0 ? buildIndex(words, wordlistCode) : null;
  wordlistIndexCache.set(wordlistCode, index);
  return index;
}

const warnedLanguages = new Set();
function warnOnce(langId, message) {
  if (warnedLanguages.has(langId)) return;
  warnedLanguages.add(langId);
  console.warn(
    `WARNING: ${message} - flagging "${langId}" changes for manual review instead of skipping the check.`,
  );
}

// Fails closed: no wordlist coverage means we can't rule out profanity, so
// treat it as flagged rather than silently letting it through unchecked.
async function checkProfanity(text, langId, exceptions) {
  const wordlistCode = resolveWordlistCode(langId);

  const index = await getWordlistIndex(wordlistCode);
  if (!index) {
    warnOnce(langId, `wordlist fetch failed/empty for "${wordlistCode}"`);
    return { flagged: true, matches: [], noWordlist: true };
  }

  const lower = text.toLowerCase();
  const matches = new Set();

  const tokens = lower.match(TOKEN_RE) || [];
  for (const token of tokens) {
    if (index.wordSet.has(token)) matches.add(token);
  }

  const maskedForSubstring = maskSafeTerms(lower, wordlistCode, exceptions);
  for (const phrase of index.substringList) {
    if (maskedForSubstring.includes(phrase)) matches.add(phrase);
  }

  return {
    flagged: matches.size > 0,
    matches: [...matches],
    noWordlist: false,
  };
}

// ---------------------------------------------------------------------
// Phishing checks: suspicious URLs, homoglyph domains, phone numbers
// ---------------------------------------------------------------------

// Each entry is either a bare domain (any path/subdomain on it is allowed)
// or a domain+path prefix (only that exact page or its sub-paths are
// allowed).
// TODO: verify these slugs against the actual project pages.
const WHITELISTED_URL_PREFIXES = [
  "github.com/SpacEagle17/IrisSearch",
  "modrinth.com/mod/irissearch",
  "curseforge.com/minecraft/mc-mods/iris-search",
  "crowdin.com/project/irissearch",
  "patreon.com/c/SpacEagle17",
  "patreon.com/cw/SpacEagle17",
  "patreon.com/spaceagle17",
  "ko-fi.com/spaceagle17",
];

// The domain-label character class includes the homoglyph ranges (not just
// [a-z0-9-]) so a spoofed domain like "gооgle.com" (Cyrillic о's) is
// captured as one complete token instead of the regex skipping the
// non-ASCII characters and matching a truncated, misleading fragment.
const URL_RE =
  /(https?:\/\/[^\s"'<>]+|www\.[^\s"'<>]+|\b[a-z0-9\-Ѐ-ӿͰ-Ͽ]+(?:\.[a-z0-9\-Ѐ-ӿͰ-Ͽ]+)*\.(?:com|dev|gg|io|org|net|co|app|me|xyz|link)\b[^\s"'<>]*)/giu;

// Strips protocol/www and a trailing slash, giving a "domain/path" string
// suitable for both domain-only and domain+path prefix comparisons.
function normalizeUrl(urlLike) {
  return urlLike
    .replace(/^https?:\/\//i, "")
    .replace(/^www\./i, "")
    .replace(/\/$/, "")
    .toLowerCase();
}

function matchesWhitelistEntry(normalizedUrl, entry) {
  const prefix = entry.toLowerCase().replace(/\/$/, "");

  if (!prefix.includes("/")) {
    // Bare domain: allow the domain itself, any path on it, or any subdomain.
    return (
      normalizedUrl === prefix ||
      normalizedUrl.startsWith(`${prefix}/`) ||
      normalizedUrl.endsWith(`.${prefix}`) ||
      normalizedUrl.includes(`.${prefix}/`)
    );
  }

  // Domain+path: only that exact page or a sub-path of it - startsWith alone
  // would let "iris-search-scam" slip through a prefix meant to match only
  // "iris-search", so a "/" boundary is required after the prefix.
  return normalizedUrl === prefix || normalizedUrl.startsWith(`${prefix}/`);
}

function isUrlWhitelisted(normalizedUrl) {
  return WHITELISTED_URL_PREFIXES.some((entry) =>
    matchesWhitelistEntry(normalizedUrl, entry),
  );
}

// Cyrillic and Greek ranges commonly used to spoof Latin domain names
// (e.g. a Cyrillic "о" standing in for a Latin "o" in "gооgle.com").
const HOMOGLYPH_RE = /[Ѐ-ӿͰ-Ͽ]/;
const LATIN_RE = /[a-z]/i;

function hasHomoglyphMix(token) {
  return LATIN_RE.test(token) && HOMOGLYPH_RE.test(token);
}

function findSuspiciousUrls(text, englishSourceValue) {
  const matches = text.match(URL_RE) || [];
  const sourceLower = (englishSourceValue || "").toLowerCase();
  const found = [];

  for (const match of matches) {
    if (hasHomoglyphMix(match)) {
      found.push({ reason: "homoglyph-url", detail: match });
      continue;
    }

    const normalized = normalizeUrl(match);
    if (!normalized || !normalized.includes(".")) continue;

    const alreadyInSource = sourceLower.includes(normalized);
    if (!alreadyInSource && !isUrlWhitelisted(normalized)) {
      found.push({ reason: "suspicious-url", detail: match });
    }
  }

  return found;
}

// Permissive on purpose - false positives just mean an extra manual review,
// while a missed real phone number defeats the point of the check.
const PHONE_RE = /(?:\+\d{1,3}[\s.-]?)?(?:\(?\d{2,4}\)?[\s.-]){2,5}\d{2,4}/g;

function findPhoneNumbers(text) {
  const matches = text.match(PHONE_RE) || [];
  return matches
    .map((m) => m.trim())
    .filter((m) => m.replace(/\D/g, "").length >= 7);
}

// ---------------------------------------------------------------------
// HTML/script injection - defense in depth in case any translated string
// ever ends up rendered outside Minecraft's own text renderer.
// ---------------------------------------------------------------------

const HTML_INJECTION_RE =
  /<\s*(script|iframe|object|embed|link|style|img)\b|javascript:|on(error|click|load|mouseover|focus|mouseenter)\s*=/i;

function findHtmlInjection(text) {
  const match = text.match(HTML_INJECTION_RE);
  return match ? match[0] : null;
}

// ---------------------------------------------------------------------
// Spam / repetition
// ---------------------------------------------------------------------

// Repeated characters (e.g. "!!!!!!!!") or repeated words (e.g. "hello hello hello hello hello")
function hasSpamRepetition(text) {
  if (/(.)\1{7,}/u.test(text)) return true;
  const words = text.toLowerCase().match(/[\p{L}\p{N}]{3,}/gu) || [];
  let run = 1;
  for (let i = 1; i < words.length; i++) {
    if (words[i] === words[i - 1]) {
      run++;
      if (run >= 5) return true;
    } else {
      run = 1;
    }
  }
  return false;
}

// ---------------------------------------------------------------------
// Minecraft formatting codes
// ---------------------------------------------------------------------

const COLOR_CODE_RE = /§[0-9a-fk-or]/gi;

// Strips Minecraft color codes to avoid false positives in the content-safety checks
function stripColorCodes(text) {
  return text.replace(COLOR_CODE_RE, "");
}

// ---------------------------------------------------------------------
// Combined check
// ---------------------------------------------------------------------

// Cheap synchronous checks run first so an obvious phishing/injection/spam
// hit never needs a wordlist fetch. Profanity (the only async check, since
// it fetches remote data) only runs if nothing else already flagged it.
async function checkContentIssues(text, langId, englishSourceValue, exceptions) {
  const cleanText = stripColorCodes(text);
  const cleanSource = englishSourceValue
    ? stripColorCodes(englishSourceValue)
    : englishSourceValue;
  const issues = [];

  for (const url of findSuspiciousUrls(cleanText, cleanSource)) {
    issues.push({ reason: url.reason, detail: url.detail });
  }
  for (const phone of findPhoneNumbers(cleanText)) {
    issues.push({ reason: "phone-number", detail: phone });
  }
  const html = findHtmlInjection(cleanText);
  if (html) issues.push({ reason: "html-injection", detail: html });
  if (hasSpamRepetition(cleanText)) {
    issues.push({
      reason: "spam-repetition",
      detail: "repeated character/word pattern",
    });
  }

  if (issues.length > 0) {
    return {
      flagged: true,
      matches: issues.map((i) => i.detail),
      reasons: [...new Set(issues.map((i) => i.reason))],
    };
  }

  const profanity = await checkProfanity(cleanText, langId, exceptions);
  return {
    flagged: profanity.flagged,
    matches: profanity.matches,
    reasons: profanity.flagged
      ? [profanity.noWordlist ? "no-wordlist-coverage" : "profanity-match"]
      : [],
  };
}

// ---------------------------------------------------------------------
// File parsing/patching
// ---------------------------------------------------------------------

// A value of `undefined` in `updates` means "remove this key" (used by the
// /reject PR command to undo a key that didn't exist before it was flagged),
// as opposed to setting it to an empty string, which is a real value.
function patchJsonFile(oldRaw, updates) {
  const obj = JSON.parse(oldRaw);
  for (const [key, value] of updates) {
    if (value === undefined) delete obj[key];
    else obj[key] = value;
  }
  return JSON.stringify(obj, null, 2) + "\n";
}

module.exports = {
  REPO_ROOT,
  LANG_DIR,
  resolveWordlistCode,
  loadSafeTermExceptions,
  saveSafeTermExceptions,
  checkContentIssues,
  patchJsonFile,
};
