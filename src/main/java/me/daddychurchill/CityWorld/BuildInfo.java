package me.daddychurchill.CityWorld;

import java.io.InputStream;
import java.util.Properties;

/**
 * Which build this is.
 *
 * <p>A version number answers "which release". During development that is a different question from
 * "which bytes", and the gap costs real time: a jar rebuilt under an unchanged version, a dependency
 * range admitting a jar it cannot run against, an instance nobody can tell is stale from its
 * filename. CityWorld deploys to nine instances by copying jars around, so it has the same blindness.
 *
 * <p><b>The log line is the half that matters.</b> A stamp inside the jar says what is on disk; the
 * startup line says what actually <em>ran</em>, which is the question a bug report needs answered.
 *
 * <p>Read from a generated properties file rather than the manifest, because this has to work in a
 * dev run too, where the mod is loaded from a classes directory and there is no jar to carry a
 * manifest. The manifest carries the same values for anything inspecting a jar without loading it
 * ({@code unzip -p <jar> META-INF/MANIFEST.MF}), which is what answers "is this instance jar stale".
 *
 * <p>Shared format, agreed across Sable's mods — {@code commit} (8 chars, {@code -dirty} when built
 * from uncommitted changes), {@code branch}, {@code time} (the <em>commit's</em> timestamp, UTC
 * ISO-8601), {@code version}. CityWorld's {@code version} additionally carries the Minecraft target,
 * since it ships three jars per release and "5.7.0" alone does not say which of them ran.
 */
public final class BuildInfo {

    /** Namespaced: a bare {@code /build.properties} would collide with every other mod doing this. */
    private static final String RESOURCE = "/cityworld/build.properties";

    /** One stamp, read as a unit. A record so a caller cannot be handed a half-filled one. */
    public record Stamp(String commit, String branch, String time, String version) {
        static final Stamp UNKNOWN = new Stamp("unknown", "unknown", "unknown", "unknown");
    }

    private static final Stamp STAMP = read();

    /**
     * Parses a stamp from a stream, or {@link Stamp#UNKNOWN} if it cannot be.
     *
     * <p><b>Public, and taking a stream, deliberately</b>: the failure paths can then be driven
     * directly by a test instead of through classpath games. Four of Sable's mods each built
     * throwaway class directories to test this before Chronicler pointed out the shape was the
     * problem, not the testing.
     *
     * <p><b>All-or-nothing, which is the entire reason the record is built after {@code load}
     * returns.</b> {@code Properties.load} parses line by line and throws part-way on a bad escape
     * <em>having already populated the earlier keys</em> — measured here by printing
     * {@code stringPropertyNames()} from the catch and finding {@code [commit, branch, version]}
     * sitting there fully formed. So the parser does not merely risk handing you a half-stamp: it
     * builds one, and only the throw stops you using it. A stamp reporting a real-looking commit
     * with the rest missing is worse than no stamp, because it looks like an answer.
     *
     * <p><b>The catch is {@code Exception}, not {@code IOException}, and that is load-bearing:</b>
     * {@code Properties.load} throws {@code IllegalArgumentException} on a bad unicode escape.
     * Narrowing it compiles, reads correctly, passes review, and takes the mod down at class-init as
     * an {@code ExceptionInInitializerError} — failing to load over a diagnostic.
     */
    public static Stamp parse(InputStream in) {
        if (in == null)
            return Stamp.UNKNOWN;
        try {
            Properties properties = new Properties();
            properties.load(in);
            return new Stamp(
                    properties.getProperty("commit", "unknown"),
                    properties.getProperty("branch", "unknown"),
                    properties.getProperty("time", "unknown"),
                    properties.getProperty("version", "unknown"));
        } catch (Exception malformed) {
            return Stamp.UNKNOWN;
        }
    }

    private static Stamp read() {
        try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            return parse(in);
        } catch (Exception unreadable) {
            // A missing or unreadable stamp must never stop the mod loading: it is diagnostic
            // information, not a dependency.
            return Stamp.UNKNOWN;
        }
    }

    /** The stamp this build was loaded with; never null, never half-filled. */
    public static Stamp stamp() {
        return STAMP;
    }

    public static String commit() {
        return STAMP.commit();
    }

    public static String branch() {
        return STAMP.branch();
    }

    public static String time() {
        return STAMP.time();
    }

    public static String version() {
        return STAMP.version();
    }

    /**
     * The one-line form for the startup log:
     * {@code 5.7.0+mc1.21.11 (build a1b2c3d4 on master, 2026-09-10T07:47:11Z)}.
     *
     * <p>A {@code -dirty} suffix on the commit means it was built with uncommitted changes — worth
     * seeing in somebody's log before spending an hour reproducing against a tag that is not what
     * they ran.
     */
    public static String describe() {
        return STAMP.version() + " (build " + STAMP.commit() + " on " + STAMP.branch() + ", "
                + STAMP.time() + ")";
    }

    private BuildInfo() {}
}
