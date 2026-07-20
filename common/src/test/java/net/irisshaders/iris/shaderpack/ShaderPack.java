package net.irisshaders.iris.shaderpack;

/**
 * Test-only stand-in for Iris's {@code net.irisshaders.iris.shaderpack.ShaderPack} class.
 * {@code IrisShaderPackTranslations} resolves this by fully-qualified name via reflection, so this
 * fake only needs an instance {@code getLanguageMap()}. MUST stay under {@code src/test/java}.
 */
public class ShaderPack {
    private final LanguageMap languageMap;

    public ShaderPack(LanguageMap languageMap) {
        this.languageMap = languageMap;
    }

    public LanguageMap getLanguageMap() {
        return languageMap;
    }
}
