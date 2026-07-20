// Shared Crowdin REST API v2 helpers, used by process-pr-commands.js (the
// /reject PR command deleting a rejected translation from Crowdin so it
// isn't re-downloaded and re-flagged on the next sync).
"use strict";

const CROWDIN_API = "https://api.crowdin.com/api/v2";

async function crowdinRequest(token, method, urlPath, body) {
  const res = await fetch(`${CROWDIN_API}${urlPath}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  // DELETE responses have no body.
  const json = res.status === 204 ? null : await res.json().catch(() => null);
  if (!res.ok) {
    const err = new Error(
      `Crowdin API ${method} ${urlPath} failed (${res.status}): ${JSON.stringify(json)}`,
    );
    err.body = json;
    throw err;
  }
  return json;
}

async function listTranslations(token, projectId, stringId, languageId) {
  const result = await crowdinRequest(
    token,
    "GET",
    `/projects/${projectId}/translations?stringId=${stringId}&languageId=${languageId}`,
  );
  return (result.data || []).map((item) => item.data);
}

async function deleteTranslation(token, projectId, translationId) {
  await crowdinRequest(token, "DELETE", `/projects/${projectId}/translations/${translationId}`);
}

// Get the list of language IDs that exist in the Crowdin project, so we can
// map a local file name to the correct Crowdin language ID (e.g. "fr" vs
// "fr-FR" vs "fr-CA"). File-naming placeholders like
// %locale%/%locale_with_underscore% (optionally further overridden via
// Crowdin's own Language Mapping UI) are export-naming conveniences, not
// guaranteed to equal the language's real API id.
async function getProjectLanguageIds(token, projectId) {
  const result = await crowdinRequest(token, "GET", `/projects/${projectId}`);
  return (result.data.targetLanguages || []).map((l) => l.id);
}

function resolveCrowdinLanguageId(langId, projectLanguageIds) {
  const [base, region] = langId.split(/[_-]/);
  const baseLower = base.toLowerCase();

  if (projectLanguageIds.includes(baseLower)) return baseLower;

  const dialectMatches = projectLanguageIds.filter((id) =>
    id.toLowerCase().startsWith(`${baseLower}-`),
  );
  if (dialectMatches.length === 1) return dialectMatches[0];
  if (dialectMatches.length > 1 && region) {
    const exact = dialectMatches.find(
      (id) => id.toLowerCase() === `${baseLower}-${region.toLowerCase()}`,
    );
    if (exact) return exact;
  }
  return null;
}

async function resolveStringId(token, projectId, key, cache) {
  const cacheKey = `${projectId}:${key}`;
  if (cache.has(cacheKey)) return cache.get(cacheKey);

  const filtered = await crowdinRequest(
    token,
    "GET",
    `/projects/${projectId}/strings?filter=${encodeURIComponent(key)}&scope=identifier&limit=500`,
  );
  let match = (filtered.data || []).find(
    (item) => item.data.identifier === key,
  );

  // If the filtered search didn't find it, try fetching all strings and searching manually.
  let all = null;
  if (!match) {
    all = await crowdinRequest(token, "GET", `/projects/${projectId}/strings?limit=500`);
    match = (all.data || []).find((item) => item.data.identifier === key);
  }

  if (!match) {
    // Dump exactly what Crowdin returned so a mismatch (different
    // separator/casing, unexpected identifier format for this file type,
    // pagination cutting off the string, etc.) is visible in the log
    // instead of just "not found".
    const allData = all ? all.data || [] : [];
    console.warn(
      `DEBUG resolveStringId("${key}"): filtered search returned ${
        (filtered.data || []).length
      } item(s), unfiltered listing returned ${allData.length} item(s) total.`,
    );
    console.warn(
      `DEBUG filtered identifiers: ${JSON.stringify(
        (filtered.data || []).map((i) => i.data.identifier),
      )}`,
    );
    console.warn(
      `DEBUG all identifiers: ${JSON.stringify(allData.map((i) => i.data.identifier))}`,
    );
  }

  const id = match ? match.data.id : null;
  cache.set(cacheKey, id);
  return id;
}

module.exports = {
  crowdinRequest,
  listTranslations,
  deleteTranslation,
  getProjectLanguageIds,
  resolveCrowdinLanguageId,
  resolveStringId,
};
