// Handles the /safe-term, /accept, and /reject PR comment commands (a
// single comment can contain several, one per line - see pr-safe-term-command.yml
// for the parsing). All three operate on the current _flagged-report.json:
//
//  - /safe-term <term>: profanity-match items only. Adds <term> as a
//    permanent safe-term exception for every wordlist code implicated, then
//    re-checks matching items - ones that come back clean get applied and
//    dropped from the report; anything still flagged stays with fresh
//    reasons. The only command that persists a rule for future runs.
//  - /accept <term>: any reason. Keeps the flagged newValue (already sitting
//    in the file from the initial flagged-report apply step) and drops the
//    item from the report - a one-off "this instance is fine," no lasting
//    exception recorded.
//  - /reject <term>: any reason. Reverts the file back to oldValue (or, if
//    the key didn't exist before it was flagged, removes it entirely so it
//    falls back to English/absence rather than being pinned to "") and
//    drops the item from the report. Also best-effort deletes the rejected
//    translation from Crowdin itself, so the same bad text doesn't get
//    re-downloaded and re-flagged on the next sync - but only if Crowdin's
//    current translation for that string+language still exactly matches
//    what's being rejected, so a translator's newer fix in the meantime
//    isn't clobbered.
//
// A <term> matches a flagged item if it's a substring of one of the item's
// matchedWords or vice versa - lets a reviewer paste either the exact
// flagged snippet or a shorter/longer phrase around it, same convention
// /safe-term already used.
//
// COMMANDS_JSON env var is a JSON array of {cmd, term} objects in comment
// order. Writes _safe-term-result.json (reply text + per-command outcomes +
// remaining flagged count) for the calling workflow to post back and to
// decide whether to auto-merge.
"use strict";

const fs = require("fs");
const path = require("path");
const {
  REPO_ROOT,
  LANG_DIR,
  resolveWordlistCode,
  checkContentIssues,
  loadSafeTermExceptions,
  saveSafeTermExceptions,
  patchJsonFile,
} = require("./lib/content-safety");
const {
  getProjectLanguageIds,
  resolveCrowdinLanguageId,
  resolveStringId,
  listTranslations,
  deleteTranslation,
} = require("./lib/crowdin-api");

const REPORT_PATH = path.join(REPO_ROOT, "_flagged-report.json");
const RESULT_PATH = path.join(REPO_ROOT, "_safe-term-result.json");

function writeResult(result) {
  fs.writeFileSync(RESULT_PATH, JSON.stringify(result, null, 2), "utf8");
  console.log(JSON.stringify(result, null, 2));
}

function findMatchingIndexes(report, term, { profanityOnly }) {
  return report
    .map((item, i) => ({ item, i }))
    .filter(({ item }) => {
      if (profanityOnly && !item.reasons.includes("profanity-match"))
        return false;
      return item.matchedWords.some(
        (w) => term.includes(w) || w.includes(term),
      );
    })
    .map(({ i }) => i);
}

function describeCommand(r) {
  const label = `\`/${r.cmd} ${r.term}\``;
  if (r.status === "not-found") {
    return `- ${label}: no matching flagged item found.`;
  }
  if (r.cmd === "safe-term") {
    return `- ${label}: added as a safe-term exception (${r.affectedCodes.join(", ")}) and resolved ${r.resolvedCount} item(s).`;
  }
  if (r.cmd === "accept") {
    return `- ${label}: accepted ${r.count} item(s) as-is.`;
  }
  if (r.cmd === "reject") {
    const crowdinParts = [];
    if (r.crowdinDeleted)
      crowdinParts.push(`${r.crowdinDeleted} removed from Crowdin`);
    if (r.crowdinSkipped)
      crowdinParts.push(
        `${r.crowdinSkipped} left on Crowdin (already changed there)`,
      );
    if (r.crowdinFailed)
      crowdinParts.push(
        `${r.crowdinFailed} Crowdin sync failed - see workflow log`,
      );
    const suffix =
      crowdinParts.length > 0 ? ` (${crowdinParts.join(", ")})` : "";
    return `- ${label}: rejected ${r.count} item(s), reverted to the original value${suffix}.`;
  }
  return `- ${label}: unknown command, ignored.`;
}

// Best-effort delete of the rejected translation from Crowdin itself, so it
// isn't re-downloaded and re-flagged on the next sync. Only deletes if
// Crowdin's current translation for that string+language still exactly
// matches the rejected text - if it doesn't (translator already fixed it,
// or it was never there), leave Crowdin untouched rather than guess.
async function syncRejectionToCrowdin(item, ctx) {
  if (!ctx.token || !ctx.projectId) return "no-token";

  try {
    // The filename (%locale_with_underscore%, possibly further overridden
    // via Crowdin's own Language Mapping UI) is an export-naming
    // convenience, not guaranteed to equal the language's real API id -
    // e.g. the project can map it to "fr" for file naming while the actual
    // registered language id underneath is still "fr-FR". Always resolve
    // against the project's real target-language list rather than
    // trusting the filename directly.
    if (!ctx.projectLanguageIds) {
      ctx.projectLanguageIds = await getProjectLanguageIds(
        ctx.token,
        ctx.projectId,
      );
    }
    const languageId = resolveCrowdinLanguageId(
      item.language,
      ctx.projectLanguageIds,
    );
    if (!languageId) {
      console.warn(
        `WARNING: Crowdin sync failed for ${item.file} [${item.key}]: no Crowdin target language matches "${item.language}" (project languages: ${ctx.projectLanguageIds.join(", ")})`,
      );
      return "failed";
    }

    const stringId = await resolveStringId(
      ctx.token,
      ctx.projectId,
      item.key,
      ctx.stringIdCache,
    );
    if (!stringId) {
      console.warn(
        `WARNING: Crowdin sync failed for ${item.file} [${item.key}]: no Crowdin string found with identifier "${item.key}"`,
      );
      return "failed";
    }

    const translations = await listTranslations(
      ctx.token,
      ctx.projectId,
      stringId,
      languageId,
    );
    const matches = translations.filter((t) => t.text === item.newValue);
    if (matches.length === 0) return "skipped";

    for (const match of matches) {
      await deleteTranslation(ctx.token, ctx.projectId, match.id);
    }
    return "deleted";
  } catch (err) {
    console.warn(
      `WARNING: Crowdin sync failed for ${item.file} [${item.key}]: ${err.message}`,
    );
    return "failed";
  }
}

async function main() {
  let commands;
  try {
    commands = JSON.parse(process.env.COMMANDS_JSON || "[]");
  } catch {
    writeResult({ status: "error", message: "Could not parse COMMANDS_JSON." });
    process.exitCode = 1;
    return;
  }

  if (commands.length === 0) {
    writeResult({ status: "error", message: "No commands provided." });
    process.exitCode = 1;
    return;
  }

  if (!fs.existsSync(REPORT_PATH)) {
    writeResult({
      status: "not-found",
      reply:
        "No `_flagged-report.json` found on this branch - nothing to process.",
    });
    return;
  }

  let report = JSON.parse(fs.readFileSync(REPORT_PATH, "utf8"));
  const exceptions = loadSafeTermExceptions();

  const sourceMap = JSON.parse(
    fs.readFileSync(path.join(LANG_DIR, "en_us.json"), "utf8"),
  );

  const updatesByFile = new Map(); // file -> Map(key -> value | undefined)
  function queueUpdate(file, key, value) {
    if (!updatesByFile.has(file)) updatesByFile.set(file, new Map());
    updatesByFile.get(file).set(key, value);
  }

  const commandResults = [];
  let exceptionsChanged = false;
  const crowdinCtx = {
    token: process.env.CROWDIN_TOKEN,
    projectId: process.env.CROWDIN_PROJECT_ID,
    projectLanguageIds: null, // fetched lazily, cached across all /reject commands in this run
    stringIdCache: new Map(),
  };

  for (const { cmd, term } of commands) {
    if (cmd === "safe-term") {
      const matchingIndexes = findMatchingIndexes(report, term, {
        profanityOnly: true,
      });
      if (matchingIndexes.length === 0) {
        commandResults.push({ cmd, term, status: "not-found" });
        continue;
      }

      const affectedCodes = [
        ...new Set(
          matchingIndexes.map((i) => resolveWordlistCode(report[i].language)),
        ),
      ];
      for (const code of affectedCodes) {
        if (!exceptions[code]) exceptions[code] = [];
        if (!exceptions[code].includes(term)) exceptions[code].push(term);
      }
      exceptionsChanged = true;

      let resolvedCount = 0;
      const nextReport = [];
      for (let i = 0; i < report.length; i++) {
        if (!matchingIndexes.includes(i)) {
          nextReport.push(report[i]);
          continue;
        }
        const item = report[i];
        const { flagged, matches, reasons } = await checkContentIssues(
          item.newValue,
          item.language,
          sourceMap[item.key],
          exceptions,
        );
        if (!flagged) {
          queueUpdate(item.file, item.key, item.newValue);
          resolvedCount++;
        } else {
          nextReport.push({ ...item, matchedWords: matches, reasons });
        }
      }
      report = nextReport;
      commandResults.push({
        cmd,
        term,
        status: "resolved",
        resolvedCount,
        affectedCodes,
      });
    } else if (cmd === "accept" || cmd === "reject") {
      const matchingIndexes = findMatchingIndexes(report, term, {
        profanityOnly: false,
      });
      if (matchingIndexes.length === 0) {
        commandResults.push({ cmd, term, status: "not-found" });
        continue;
      }

      const nextReport = [];
      let count = 0;
      let crowdinDeleted = 0;
      let crowdinSkipped = 0;
      let crowdinFailed = 0;
      for (let i = 0; i < report.length; i++) {
        if (!matchingIndexes.includes(i)) {
          nextReport.push(report[i]);
          continue;
        }
        const item = report[i];
        if (cmd === "accept") {
          queueUpdate(item.file, item.key, item.newValue);
        } else {
          queueUpdate(
            item.file,
            item.key,
            item.existedBefore ? item.oldValue : undefined,
          );
          const outcome = await syncRejectionToCrowdin(item, crowdinCtx);
          if (outcome === "deleted") crowdinDeleted++;
          else if (outcome === "skipped") crowdinSkipped++;
          else if (outcome === "failed") crowdinFailed++;
          // "no-token" -> Crowdin sync not configured for this run, stay silent about it in the tally.
        }
        count++;
      }
      report = nextReport;
      commandResults.push({
        cmd,
        term,
        status: cmd === "accept" ? "accepted" : "rejected",
        count,
        ...(cmd === "reject"
          ? { crowdinDeleted, crowdinSkipped, crowdinFailed }
          : {}),
      });
    } else {
      commandResults.push({ cmd, term, status: "unknown-command" });
    }
  }

  if (exceptionsChanged) saveSafeTermExceptions(exceptions);

  for (const [file, updates] of updatesByFile) {
    const filePath = path.join(LANG_DIR, file);
    const raw = fs.readFileSync(filePath, "utf8");
    fs.writeFileSync(filePath, patchJsonFile(raw, updates), "utf8");
  }

  if (report.length === 0) {
    // Nothing left to review - remove the report entirely
    fs.rmSync(REPORT_PATH, { force: true });
  } else {
    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
  }

  const replyLines = commandResults.map(describeCommand);
  replyLines.push("");
  replyLines.push(
    report.length === 0
      ? "No flagged items remain."
      : `${report.length} flagged item(s) still need review.`,
  );

  writeResult({
    status: "processed",
    commands: commandResults,
    remainingCount: report.length,
    reply: replyLines.join("\n"),
  });
}

main().catch((err) => {
  console.error(err);
  writeResult({ status: "error", message: String(err) });
  process.exitCode = 1;
});
