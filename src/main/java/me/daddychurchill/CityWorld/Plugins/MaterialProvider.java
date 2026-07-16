package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.MaterialList;

/**
 * The per-world material palettes.
 *
 * <p><b>Deliberately empty lists.</b> Upstream's job here is to read the per-world YAML into these
 * lists; that is P7. An unconfigured world leaves them empty, and every caller passes the default it
 * wants to {@code getNthMaterial}/{@code getRandomMaterial} — so empty lists reproduce an
 * unconfigured upstream world exactly, rather than approximating it.
 *
 * <p>Only the lists the ported code actually reads exist so far. Add the rest as their call sites
 * land, rather than transcribing all of upstream's now.
 */
public class MaterialProvider {

    /** Road palette. Indices are positional and upstream's call sites know them:
     *  0 = pavement, 1 = lines, 2 = pavement sidewalk, 3 = dirt road, 4 = dirt road sidewalk. */
    public final MaterialList itemsMaterialListFor_Roads = new MaterialList("Roads");

    public MaterialProvider(CityWorldGenerator generator) {
    }
}
