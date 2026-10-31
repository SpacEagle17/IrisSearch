package com.spaceagle17.irissearch.engine;

import com.spaceagle17.irissearch.engine.ShaderOptionsSearchEngine.MenuScopedQuery;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Per-keystroke caches for {@link ShaderOptionsSearchEngine}. So we re-use as much as possible when the query or the shader pack/language state doesn't change.
 *
 */
final class SearchCache {
    private SearchCache() {}

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern MENU_ARROW_PATTERN = Pattern.compile("\\s+>");
    private static final String[] NO_WORDS = new String[0];

    /** Everything about a query that doesn't depend on the option being scored. */
    static final class PreparedQuery {
        final String raw;
        final int version;
        final MenuScopedQuery scoped;
        final String term; // lowercased, trimmed, menu scope removed
        final String[] tokens;
        final String[] termSynonyms;     // single-token queries: synonyms of the whole term
        final String[][] tokenSynonyms;  // multi-token queries: synonyms of each token
        private final Map<String, Integer> scopeTierByPath = new ConcurrentHashMap<>();

        PreparedQuery(String raw, int version) {
            this.raw = raw;
            this.version = version;
            this.scoped = ShaderOptionsSearchEngine.parseMenuScope(raw);
            this.term = scoped.term().toLowerCase(Locale.ROOT).trim();
            if (term.isEmpty()) {
                tokens = NO_WORDS;
                termSynonyms = NO_WORDS;
                tokenSynonyms = null;
            } else {
                tokens = WHITESPACE_PATTERN.split(term);
                if (tokens.length <= 1) {
                    termSynonyms = SearchDictionaries.resolveSynonyms(term);
                    tokenSynonyms = null;
                } else {
                    termSynonyms = NO_WORDS;
                    tokenSynonyms = new String[tokens.length][];
                    for (int i = 0; i < tokens.length; i++) tokenSynonyms[i] = SearchDictionaries.resolveSynonyms(tokens[i]);
                }
            }
        }

        /** The menu scope tier of {@code path} for this query's scope, 0 if the query has none. Many options share a path. */
        int scopeTier(String path) {
            if (!scoped.hasScope() || path == null || path.isEmpty()) return 0;
            return scopeTierByPath.computeIfAbsent(path, p -> ShaderOptionsSearchEngine.menuScopeTier(p, scoped.menuScope()));
        }
    }

    private static volatile PreparedQuery lastPrepared = null;

    /** The prepared form of {@code query}, rebuilt only when the query or {@code version} changes. */
    static PreparedQuery prepare(String query, int version) {
        PreparedQuery last = lastPrepared;
        if (last != null && last.version == version && last.raw.equals(query)) return last;
        PreparedQuery prepared = new PreparedQuery(query, version);
        lastPrepared = prepared;
        return prepared;
    }

    /** Search-ready text of one option, with the word lists the typo fallback walks. */
    record OptionText(String translated, String defaultName, String rawId, String comment,
                      String[] translatedWords, String[] defaultWords, String[] valueTexts) {}

    /** Search-ready text of one submenu (screen id). */
    record MenuText(String translated, String rawId, String[] translatedWords) {
        static final MenuText EMPTY = new MenuText("", "", NO_WORDS);
    }

    /** Option and menu text as of one {@code version}; a new instance replaces it when the version moves on. */
    static final class Texts {
        final int version;
        private final Map<String, OptionText> options = new ConcurrentHashMap<>();
        private final Map<String, MenuText> menus = new ConcurrentHashMap<>();

        private Texts(int version) { this.version = version; }

        OptionText option(String optionId) {
            return options.computeIfAbsent(optionId, SearchCache::buildOptionText);
        }

        /** {@link MenuText#EMPTY} for a null id (root-level option / unknown path). */
        MenuText menu(String menuId) {
            if (menuId == null) return MenuText.EMPTY;
            return menus.computeIfAbsent(menuId, SearchCache::buildMenuText);
        }
    }

    private static volatile Texts texts = new Texts(-1);

    /** The text cache for the currently loaded shader pack and languages. */
    static Texts texts() {
        int version = IrisShaderPackTranslations.stateVersion();
        Texts current = texts;
        if (current.version != version) texts = current = new Texts(version);
        return current;
    }

    /** Drops everything cached. */
    static void invalidate() {
        texts = new Texts(-1);
        lastPrepared = null;
    }

    private static OptionText buildOptionText(String optionId) {
        String translated = MinecraftLanguageAccess.getLowercaseString("option." + optionId);
        String defaultName = IrisShaderPackTranslations.getLowercaseDefaultTranslatedString("option." + optionId);
        String comment = MinecraftLanguageAccess.getLowercaseString("option." + optionId + ".comment");
        return new OptionText(translated, defaultName, optionId.toLowerCase(Locale.ROOT), comment,
                splitWords(translated), splitWords(defaultName), IrisShaderPackTranslations.getOptionValueTexts(optionId));
    }

    private static MenuText buildMenuText(String menuId) {
        String translated = resolveMenuName(menuId);
        return new MenuText(translated, menuId.toLowerCase(Locale.ROOT), splitWords(translated));
    }

    /** The active language's lowercase name for a submenu, falling back to the pack's own en_us name. */
    private static String resolveMenuName(String screenId) {
        String active = getReadableMenuName(screenId);
        if (!active.isEmpty()) return active;
        if (screenId == null || screenId.isEmpty()) return "";
        return MENU_ARROW_PATTERN.matcher(IrisShaderPackTranslations.getLowercaseDefaultTranslatedString("screen." + screenId)).replaceAll("");
    }

    /** Lowercase, search-ready counterpart of {@link ShaderOptionsSearchEngine#getDisplaySettingsName}. */
    private static String getReadableMenuName(String screenId) {
        if (screenId == null || screenId.isEmpty()) return "";
        return MENU_ARROW_PATTERN.matcher(MinecraftLanguageAccess.getLowercaseString("screen." + screenId)).replaceAll("");
    }

    private static String[] splitWords(String text) {
        return text.isEmpty() ? NO_WORDS : WHITESPACE_PATTERN.split(text);
    }
}
