/**
 * CityWorld's JourneyMap integration — and, since the same shape works for any map mod, the
 * reference for doing this again.
 *
 * <h2>What it does</h2>
 *
 * JourneyMap draws what a player has <em>seen</em>. CityWorld decided where every road, district and
 * landmark goes before anyone arrived, and can answer for any chunk without generating it. The
 * integration hands that knowledge to the map: district tints and street grids over unexplored
 * ground, hover text naming what is planned in a chunk, and waypoints on landmarks and search hits.
 *
 * <h2>The five decisions worth copying</h2>
 *
 * <h3>1. The server side is the useful side</h3>
 *
 * JourneyMap API 2.0 added a <em>server</em> plugin ({@code IServerPlugin} / {@code IServerAPI}), and
 * that is what makes this cheap. The plan lives on the server; the server API pushes waypoints
 * ({@code addPlayerWaypoint}) and polygon overlays ({@code getOverlayApi().show(...)}) straight to
 * connected clients. One implementation covers single-player (the integrated server initialises the
 * server plugin too — verified in play) and dedicated servers, with no networking of our own.
 *
 * <p>Reach for a client plugin ({@code IClientPlugin}) only for things that are genuinely the
 * client's: options screens, toolbar buttons, mouse position, drawing. Those need your own packets
 * to ask the server anything — see {@code me.daddychurchill.CityWorld.network}.
 *
 * <h3>2. Keep it a soft dependency, structurally</h3>
 *
 * <ul>
 *   <li>The API is {@code compileOnly} in {@code build.gradle} and never shipped in the jar. Verify
 *       that: {@code unzip -l <jar> | grep journeymap/api} must come back empty.
 *   <li><b>Every {@code journeymap.*} reference lives in this package, and nothing outside it may
 *       reference this package.</b> JourneyMap discovers the plugin classes itself by scanning for
 *       {@code @JourneyMapPlugin}, so with JourneyMap absent they are never loaded and the missing
 *       API is never missed.
 *   <li>The generator talks only to {@link me.daddychurchill.CityWorld.api.MapMarkers}, in
 *       CityWorld's own types. That seam is what a second map mod hooks — the generator never learns
 *       another map mod exists.
 *   <li>The {@code neoforge.mods.toml} entry is {@code type="optional"}. Mind the
 *       {@code versionRange}: an optional dependency whose range is <em>unmet</em> still refuses to
 *       load, and JourneyMap's versions lead with the Minecraft version ({@code 1.21.11-6.0.0},
 *       {@code 26.2-6.0.7}), so the floor differs per branch. It is a gradle property for that reason.
 * </ul>
 *
 * <h3>3. Their exceptions are your crashes — wrap everything</h3>
 *
 * Callbacks here run inside JourneyMap's own event bus, during client setup and while the map screen
 * renders. An exception there is a crash report, not a log line. Every callback CityWorld registers
 * is wrapped ({@code safely(...)}), and the generator extends map mods the same courtesy: a listener
 * that throws in {@code MapMarkers} is logged and dropped, never allowed to fail a chunk.
 *
 * <h3>4. Isolate what the versions rename</h3>
 *
 * CityWorld ships one source across three Minecraft versions, and this arc alone hit five renames:
 * {@code ChunkPos} became a record (26.1, so {@code asLong} went), JourneyMap moved {@code Context}
 * to {@code journeymap.api.v2.common} (26.2), Minecraft renamed {@code GuiGraphics} to
 * {@code GuiGraphicsExtractor} and its {@code drawString} to {@code text} (26.1), and
 * {@code Minecraft.screen} stopped being public (26.2).
 *
 * <p>Three ways out, in order of preference: <b>avoid the API</b> (pack the chunk key by hand);
 * <b>never name the type</b> ({@code var graphics = event.getGuiGraphics()}); or, when neither
 * works, <b>put the call in a file of its own</b> — {@link OverlayProps} here and
 * {@code client.HudText} on the client side hold every difference between branches, so every other
 * file cherry-picks across cleanly. When the next rename lands, it goes in one of those two files.
 *
 * <h3>5. Measure it, because none of this is visible from a headless server</h3>
 *
 * <ul>
 *   <li>{@code -Dcityworld.maptest=true} — logs whether a map mod hooked in, fires one synthetic
 *       landmark, builds and times the overlay geometry, re-sweeps the same ground to prove the plan
 *       is deterministic, and prints the hover captions a sweep would produce (which is how three
 *       lot kinds were found reporting their class names to players).
 *   <li>{@code -Dcityworld.mapradius=N} — widens the sweep so a stationary player can be handed
 *       hundreds of overlays at once.
 *   <li>{@code -Dcityworld.mapstress=true} — client-side frame rate and frame time, every five
 *       seconds.
 *   <li>Drop JourneyMap's jar in {@code run/mods/} (it JarJars the API and common-networking, so one
 *       jar is the whole dev dependency) and run {@code ./gradlew runClient}, or
 *       {@code runClient -PcwJoin=127.0.0.1:25599} to join the dev server. <b>Only a real client
 *       catches a client-plugin crash</b>, which is how both of this arc's crashes reached a player.
 * </ul>
 *
 * <h2>Traps that cost a playtest round each</h2>
 *
 * <ul>
 *   <li><b>An {@code Option} is bound after the options-registry event returns.</b> Calling
 *       {@code option.get()} inside that event throws an NPE, and thrown during client setup that is
 *       a crash on the loading screen. Read options through a method that catches.
 *   <li><b>A toolbar icon that does not resolve kills the map.</b> JourneyMap's example mod points
 *       addons at {@code journeymap:/resources/assets/journeymap/theme/flat/icon/*.png}, which is not
 *       where the asset is ({@code assets/journeymap/theme/flat/icon/*.png}). The button then renders
 *       a null texture, JourneyMap throws inside {@code jm.fullscreen.render()} <em>every frame</em>,
 *       and closes the map to stay alive — presenting as "the map crashes when I press J". Ship your
 *       own icon and depend on nobody's asset layout.
 *   <li><b>Map-side errors are not in {@code logs/latest.log}.</b> They are in
 *       {@code journeymap/journeymap.log} inside the instance. Look there first.
 *   <li><b>Global waypoints arrive at login.</b> {@code addGlobalWaypoint} did not reach a connected
 *       client (sixteen landmarks announced in chat, no pins, no error either side); per-player
 *       waypoints do.
 *   <li><b>JourneyMap pulls a partly-off-screen polygon's label into view</b>, so labels on adjacent
 *       polygons stack on one screen row. Neighbouring platmaps are nearly always different
 *       districts, so most of them collided. The names moved to hover text instead.
 *   <li><b>Info slots belong to the minimap</b> and only appear if the player selects them in
 *       JourneyMap's settings — not the place for fullscreen hover text. The fullscreen block-info
 *       bar is read-only to addons, and {@code FullscreenRenderEvent} hands out a
 *       {@code GuiGraphicsExtractor} that exists only as a runtime mixin, so it cannot be compiled
 *       against. Drawing on NeoForge's own {@code ScreenEvent.Render.Post} needs none of them and
 *       works for any map mod that reports the hovered chunk.
 *   <li><b>Re-showing an overlay id is not free.</b> A platmap's plan never changes, so overlays are
 *       sent once per player and never re-sent; that also avoids a client accumulating two overlays
 *       for one square.
 * </ul>
 *
 * <h2>Costs, measured</h2>
 *
 * A cold platmap plan is ~180 ms, so a 7×7 sweep is ~9 s — hence ring-by-ring pushes (the nearest
 * ring lands in ~2.5 s) on one worker shared by all players, since planned platmaps are cached and
 * the second player through a city pays almost nothing. Drawn ground costs ~2 overlays and ~5.5
 * shapes per platmap, about 12 overlays per 160 blocks of travel. The server neither re-plans nor
 * re-sends a retained overlay, so the only real cost is the client's — which is why the ceiling is a
 * per-player setting rather than a constant.
 *
 * @see me.daddychurchill.CityWorld.api.MapMarkers the map-mod-agnostic seam
 * @see me.daddychurchill.CityWorld.network the packets the client half needs
 */
package me.daddychurchill.CityWorld.integration.journeymap;
