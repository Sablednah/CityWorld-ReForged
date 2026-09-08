package me.daddychurchill.CityWorld.integration.journeymap;

import java.util.EnumSet;

import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.server.overlay.OverlayShapeProps;

/**
 * Builds the style block for a map overlay.
 *
 * <p>Its only reason to exist is one import. JourneyMap moved {@code Context} from
 * {@code journeymap.api.v2.client.display} (1.21.11, 26.1) to {@code journeymap.api.v2.common} in
 * 26.2, and {@link OverlayShapeProps} names it. Keeping that import here means <b>this file is the
 * only one that differs between version branches</b> — {@link CityPlanOverlay} stays byte-identical
 * everywhere, so cherry-picking it across the three branches never conflicts. If a future
 * JourneyMap moves something else, move that here too.
 */
final class OverlayProps {

    private OverlayProps() {}

    /** Every UI and every map type, which is what CityWorld's overlays always want. */
    static OverlayShapeProps everywhere(int fillColor, float fillOpacity, int strokeColor, float strokeWidth,
            float strokeOpacity, int displayOrder, int minZoom, int maxZoom, String label, String title) {
        return new OverlayShapeProps(fillColor, fillOpacity, strokeColor, strokeWidth, strokeOpacity,
                displayOrder, minZoom, maxZoom,
                EnumSet.allOf(Context.UI.class), EnumSet.allOf(Context.MapType.class), label, title);
    }
}
