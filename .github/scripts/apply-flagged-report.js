// Applies the flagged updates recorded by split-by-profanity.js onto the
// current files (which by this point already have the clean changes
// committed). Run this only on the localization branch, after the clean
// commit has landed on the target branch.
"use strict";

const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..", "..");
const LANG_DIR = path.join(ROOT, "common/src/main/resources/assets/iris_search/lang");
const REPORT_PATH = path.join(ROOT, "_flagged-report.json");

function patchJsonFile(oldRaw, updates) {
  const obj = JSON.parse(oldRaw);
  for (const [key, value] of updates) obj[key] = value;
  return JSON.stringify(obj, null, 2) + "\n";
}

function main() {
  if (!fs.existsSync(REPORT_PATH)) {
    console.log("No flagged report found - nothing to apply.");
    return;
  }

  const report = JSON.parse(fs.readFileSync(REPORT_PATH, "utf8"));
  if (report.length === 0) {
    console.log("Flagged report is empty - nothing to apply.");
    return;
  }

  const byFile = new Map();
  for (const item of report) {
    if (!byFile.has(item.file)) byFile.set(item.file, new Map());
    byFile.get(item.file).set(item.key, item.newValue);
  }

  for (const [file, updates] of byFile) {
    const filePath = path.join(LANG_DIR, file);
    const raw = fs.readFileSync(filePath, "utf8");
    const patched = patchJsonFile(raw, updates);
    fs.writeFileSync(filePath, patched, "utf8");
    console.log(`Applied ${updates.size} flagged update(s) to ${file}`);
  }
}

main();
