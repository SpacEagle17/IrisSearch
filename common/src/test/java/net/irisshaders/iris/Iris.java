package net.irisshaders.iris;

import net.irisshaders.iris.shaderpack.ShaderPack;

import java.util.Optional;

/**
 * Test-only stand-in for Iris's {@code net.irisshaders.iris.Iris} class. {@code
 * IrisShaderPackTranslations} resolves this by fully-qualified name via reflection, so this fake
 * only needs a static, no-arg {@code getCurrentPack()} returning an {@code Optional<ShaderPack>}.
 *
 * <p>MUST stay under {@code src/test/java} -- {@code common} never has a real Iris dependency
 * (only the loader modules do), so this is safe there, but it must never leak into {@code
 * src/main/java} or a shaded mod jar.
 */
public class Iris {
    private static ShaderPack currentPack;

    public static Optional<ShaderPack> getCurrentPack() {
        return Optional.ofNullable(currentPack);
    }

    /** Test hook: sets the "currently loaded" shader pack. */
    public static void setCurrentPack(ShaderPack pack) {
        currentPack = pack;
    }

    /** Test hook: clears state back to "no pack loaded" between tests. */
    public static void reset() {
        currentPack = null;
    }
}
