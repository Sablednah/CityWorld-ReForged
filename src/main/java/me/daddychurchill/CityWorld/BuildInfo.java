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
 * from uncommitted changes), {@code branch}, {@code time} (UTC ISO-8601), {@code version}. CityWorld's
 * {@code version} additionally carries the Minecraft target, since it ships three jars per release.
 */
public final class BuildInfo {

    /** Namespaced: a bare {@code /build.properties} would collide with every other mod doing this. */
    private static final String RESOURCE = "/cityworld/build.properties";

    private static final String COMMIT;
    private static final String BRANCH;
    private static final String TIME;
    private static final String VERSION;

    static {
        String commit = "unknown", branch = "unknown", time = "unknown", version = "unknown";
        try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                Properties p = new Properties();
                // Every read happens AFTER load() returns, and that ordering is the whole degrade
                // guarantee — do not "tidy" it into reading as you go. Measured: given a stamp whose
                // first three lines are valid and whose fourth holds a bad escape, load() throws
                // having already populated commit, branch and version. Read incrementally and a
                // corrupt stamp reports a real-looking commit with the rest missing, which is worse
                // than no stamp because it looks like an answer. This way it is all or nothing.
                p.load(in);
                commit = p.getProperty("commit", commit);
                branch = p.getProperty("branch", branch);
                time = p.getProperty("time", time);
                version = p.getProperty("version", version);
            }
        } catch (Exception ignored) {
            // catch (Exception), and the breadth is load-bearing rather than lazy: Properties.load
            // throws IllegalArgumentException — NOT IOException — on a malformed unicode escape.
            // A catch (IOException) compiles, reads correctly, passes review, and then takes the mod
            // down at class-init the first time a stamp is corrupted: an ExceptionInInitializerError
            // out of a static initialiser, i.e. failing to load over a diagnostic.
            //
            // A missing or unreadable stamp must never stop the mod loading: it is diagnostic
            // information, not a dependency. Both bad paths are exercised, not assumed — see
            // PORTING.md's build-stamp section for the cases.
        }
        COMMIT = commit;
        BRANCH = branch;
        TIME = time;
        VERSION = version;
    }

    public static String commit() {
        return COMMIT;
    }

    public static String branch() {
        return BRANCH;
    }

    public static String time() {
        return TIME;
    }

    public static String version() {
        return VERSION;
    }

    /**
     * The one-line form for the startup log:
     * {@code 5.7.0+mc1.21.11 (build a1b2c3d4 on master, 2026-09-10T07:24:24Z)}.
     *
     * <p>A {@code -dirty} suffix on the commit means it was built with uncommitted changes — worth
     * seeing in somebody's log before spending an hour reproducing against a tag that is not what
     * they ran.
     */
    public static String describe() {
        return VERSION + " (build " + COMMIT + " on " + BRANCH + ", " + TIME + ")";
    }

    private BuildInfo() {}
}
