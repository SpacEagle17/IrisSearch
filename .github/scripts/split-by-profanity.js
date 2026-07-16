// Checks incoming translation updates (_new/) for profanity using remote wordlists.
// Clean updates are patched in-place; flagged content is saved to _flagged-report.json.
// Requires: `PROFANITY_REPO_TOKEN` environment variable.
"use strict";

const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..", "..");
const LANG_DIR = path.join(
  ROOT,
  "common/src/main/resources/assets/iris_search/lang",
);
const NEW_DIR = path.join(ROOT, "_new");
const REPORT_PATH = path.join(ROOT, "_flagged-report.json");

const WORDLIST_REPO = "SpacEagle17/Profanity-Filter";
const WORDLIST_PATH = "lists";

// Overrides for language identifiers whose wordlist code isn't just the
// part before the underscore (see resolveWordlistCode below). Empty for
// now - every current locale here reduces cleanly.
const PROFANITY_LANG_OVERRIDES = {};

function resolveWordlistCode(langId) {
  return PROFANITY_LANG_OVERRIDES[langId] ?? langId.split("_")[0];
}

// Scripts that don't reliably delimit words with spaces, so a wordlist
// entry can only be found by scanning for it as a substring - tokenizing
// on whitespace would just produce one giant "token" per sentence.
const SUBSTRING_ONLY_CODES = new Set(["ja", "zh"]);

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

// Builds a fast lookup index for one wordlist:
// - wordSet: single-word entries, checked via O(1) Set membership against
//   tokenized input (for space-delimited languages).
// - substringList: multi-word phrases, plus (for CJK) every entry, checked
//   via .includes() since tokenization doesn't apply there.
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
async function checkProfanity(text, langId) {
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

  for (const phrase of index.substringList) {
    if (lower.includes(phrase)) matches.add(phrase);
  }

  return {
    flagged: matches.size > 0,
    matches: [...matches],
    noWordlist: false,
  };
}

function patchJsonFile(oldRaw, updates) {
  const obj = JSON.parse(oldRaw);
  for (const [key, value] of updates) obj[key] = value;
  return JSON.stringify(obj, null, 2) + "\n";
}

async function main() {
  const flaggedReport = [];

  if (fs.existsSync(NEW_DIR)) {
    const newJsonFiles = fs
      .readdirSync(NEW_DIR)
      .filter((f) => f.endsWith(".json"));

    for (const fileName of newJsonFiles) {
      const langId = fileName.replace(/\.json$/, "");
      const oldPath = path.join(LANG_DIR, fileName);
      const newPath = path.join(NEW_DIR, fileName);
      if (!fs.existsSync(oldPath)) continue;

      const oldRaw = fs.readFileSync(oldPath, "utf8");
      const newRaw = fs.readFileSync(newPath, "utf8");
      const oldObj = JSON.parse(oldRaw);
      const newObj = JSON.parse(newRaw);

      const cleanUpdates = new Map();
      for (const [key, newValue] of Object.entries(newObj)) {
        if (typeof newValue !== "string") continue;
        if (oldObj[key] === newValue) continue;
        const { flagged, matches, noWordlist } = await checkProfanity(
          newValue,
          langId,
        );
        if (flagged) {
          flaggedReport.push({
            file: fileName,
            key,
            language: langId,
            oldValue: oldObj[key] ?? "",
            newValue,
            matchedWords: matches,
            reason: noWordlist ? "no-wordlist-coverage" : "profanity-match",
          });
        } else {
          cleanUpdates.set(key, newValue);
        }
      }

      if (cleanUpdates.size > 0) {
        fs.writeFileSync(oldPath, patchJsonFile(oldRaw, cleanUpdates), "utf8");
        console.log(
          `${fileName}: applied ${cleanUpdates.size} clean update(s)`,
        );
      }
    }
  }

  fs.rmSync(NEW_DIR, { recursive: true, force: true });

  fs.writeFileSync(REPORT_PATH, JSON.stringify(flaggedReport, null, 2), "utf8");

  if (flaggedReport.length === 0) {
    console.log("No flagged content this run.");
  } else {
    console.log(`${flaggedReport.length} flagged item(s):`);
    for (const item of flaggedReport) {
      console.log(
        `  ${item.file} [${item.key}] (${item.language}) matched: ${item.matchedWords.join(", ")}`,
      );
    }
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
