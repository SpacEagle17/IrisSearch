// Compares freshly-downloaded Crowdin translations (in _new/) against the
// current committed versions, and splits the changed keys into two groups:
//
// - "clean" changes are patched directly onto the existing files in place
//   (the caller commits these straight to the target branch).
// - "flagged" changes (profanity, phishing, injection, spam - see
//   lib/content-safety.js) are written to _flagged-report.json instead of
//   being applied, so they can go through the PR review path applied by
//   apply-flagged-report.js.
//
// Requires: `PROFANITY_REPO_TOKEN` environment variable.
"use strict";

const fs = require("fs");
const path = require("path");
const {
  REPO_ROOT,
  LANG_DIR,
  checkContentIssues,
  loadSafeTermExceptions,
  patchJsonFile,
} = require("./lib/content-safety");

const NEW_DIR = path.join(REPO_ROOT, "_new");
const REPORT_PATH = path.join(REPO_ROOT, "_flagged-report.json");

async function main() {
  const sourceMap = JSON.parse(
    fs.readFileSync(path.join(LANG_DIR, "en_us.json"), "utf8"),
  );
  const exceptions = loadSafeTermExceptions();

  const flaggedReport = [];

  if (fs.existsSync(NEW_DIR)) {
    const newJsonFiles = fs
      .readdirSync(NEW_DIR)
      .filter((f) => f.endsWith(".json"));

    for (const fileName of newJsonFiles) {
      const langId = fileName.replace(/\.json$/, "");
      const oldPath = path.join(LANG_DIR, fileName);
      const newPath = path.join(NEW_DIR, fileName);

      // If the old file doesn't exist, we treat it as an empty object. For new languages which did not exist before.
      const oldRaw = fs.existsSync(oldPath)
        ? fs.readFileSync(oldPath, "utf8")
        : "{}";
      const newRaw = fs.readFileSync(newPath, "utf8");
      const oldObj = JSON.parse(oldRaw);
      const newObj = JSON.parse(newRaw);

      const cleanUpdates = new Map();
      for (const [key, newValue] of Object.entries(newObj)) {
        if (typeof newValue !== "string") continue;
        if (oldObj[key] === newValue) continue;
        const { flagged, matches, reasons } = await checkContentIssues(
          newValue,
          langId,
          sourceMap[key],
          exceptions,
        );
        if (flagged) {
          flaggedReport.push({
            file: fileName,
            key,
            language: langId,
            oldValue: oldObj[key] ?? "",
            newValue,
            matchedWords: matches,
            reasons,
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

  // Clean up the staging directory now to prevent commit issues.
  fs.rmSync(NEW_DIR, { recursive: true, force: true });

  fs.writeFileSync(REPORT_PATH, JSON.stringify(flaggedReport, null, 2), "utf8");

  if (flaggedReport.length === 0) {
    console.log("No flagged content this run.");
  } else {
    console.log(`${flaggedReport.length} flagged item(s):`);
    for (const item of flaggedReport) {
      console.log(
        `  ${item.file} [${item.key}] (${item.language}) [${item.reasons.join(", ")}] matched: ${item.matchedWords.join(", ")}`,
      );
    }
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
