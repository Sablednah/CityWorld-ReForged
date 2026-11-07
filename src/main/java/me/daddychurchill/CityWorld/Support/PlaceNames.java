package me.daddychurchill.CityWorld.Support;

import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.CityWorldSettings;

/**
 * The names of places: each city of a vanilla world ("Cities Apart") has one, and CityWorld's own land — one great
 * city — is named district by district, {@code naming.districtSize} platmaps square. A name is a pure function of
 * the world's seed and the place (a city by its centre, a district by its square), so a sign, a map and the API agree.
 *
 * <p>{@code naming.placeFormat} shapes it: {@code {name}} (the default) is a name from the word lists — a whole one
 * from {@code naming.placeNames} or one put together from {@code placeStarts} and {@code placeEnds} (Ash + ford) — and
 * {@code {n}} a number from 1 to 99, for the styles that number their places ({@code "City {n}"},
 * {@code "District {n}"}). An empty list keeps CityWorld's own words.
 */
public final class PlaceNames {

    private PlaceNames() {
    }

    /** The name of the place holding this chunk. */
    public static String at(CityWorldGenerator generator, int chunkX, int chunkZ) {
        var sites = generator.citySites;
        if (sites != null) { // vanilla land: the city whose cell this is (or the one nearest, outside any)
            var site = sites.siteAt(chunkX * 16 + 8, chunkZ * 16 + 8);
            if (site != null)
                return named(generator, 0x43495459L ^ (((long) site.centreX() << 32) ^ (site.centreZ() & 0xffffffffL)), true);
        }
        int size = Math.max(1, generator.getSettings().districtSize) * 10; // in chunks
        long dx = Math.floorDiv(chunkX, size), dz = Math.floorDiv(chunkZ, size);
        return named(generator, 0x44495354L ^ ((dx << 32) ^ (dz & 0xffffffffL)), false);
    }

    /** The name for a place by its key (a city's centre, a district's square); {@code town}: a city of its own. */
    public static String named(CityWorldGenerator generator, long key, boolean town) {
        CityWorldSettings s = generator.getSettings();
        Odds odds = new Odds(generator.getWorldSeed() ^ key * 0x9E3779B97F4A7C15L);
        String format = s.placeFormat == null || s.placeFormat.isBlank() ? "{name}" : s.placeFormat;
        int n = 1 + odds.getRandomInt(99);
        String name = format.contains("{name}") ? word(s, odds, town) : "";
        return format.replace("{name}", name).replace("{n}", Integer.toString(n)).trim();
    }

    private static String word(CityWorldSettings s, Odds odds, boolean town) {
        List<String> whole = s.placeNames.isEmpty() ? PLACE_NAMES : s.placeNames;
        List<String> starts = s.placeStarts.isEmpty() ? PLACE_STARTS : s.placeStarts;
        List<String> ends = s.placeEnds.isEmpty() ? PLACE_ENDS : s.placeEnds;
        // a district of the great city is as often a named quarter (Midtown, Soho) as a put-together name; a town
        // of its own mostly a put-together one (Ashford)
        if (!whole.isEmpty() && (starts.isEmpty() || ends.isEmpty() || odds.playOdds(town ? 0.15 : 0.5)))
            return whole.get(odds.getRandomInt(whole.size()));
        String start = starts.get(odds.getRandomInt(starts.size()));
        String end = ends.get(odds.getRandomInt(ends.size()));
        if (!start.isEmpty() && !end.isEmpty() && Character.toLowerCase(start.charAt(start.length() - 1)) == end.charAt(0))
            end = end.substring(1); // Ash + hill: Ashill, not Ashhill
        return start + end;
    }

    /** Fits a name to a sign of {@code max} characters: whole words while they fit, else cut. */
    public static String fit(String name, int max) {
        if (name.length() <= max)
            return name;
        int cut = name.lastIndexOf(' ', max);
        return cut > 0 ? name.substring(0, cut) : name.substring(0, max);
    }

    private static final List<String> PLACE_NAMES = List.of("Midtown", "Downtown", "Uptown", "Old Town", "Soho",
            "Chinatown", "Little Italy", "The Heights", "Docklands", "Riverside", "Northside", "Southside", "Eastside",
            "Westside", "Harbourside", "The Flats", "Market District", "Garment District", "Arts District",
            "Financial District", "Bay Ridge", "Hillcrest", "Parkside", "Greenwich", "Kingsbridge", "Highbury",
            "Brickworks", "Ironside", "Union Square", "Station Hill", "Canal Street", "Lakeview", "Westgate",
            "Cathedral Quarter", "Mill Town", "Foundry Row", "Silver Lake", "Echo Park", "Twin Peaks", "Oakmont");

    private static final List<String> PLACE_STARTS = List.of("Ash", "Oak", "North", "River", "Elm", "Fair", "Glen",
            "King", "Mill", "Stone", "West", "East", "South", "High", "Brook", "Clear", "Maple", "Pine", "Red", "Silver",
            "Spring", "Bay", "Bridge", "Cedar", "Cliff", "Copper", "Crown", "Ever", "Fox", "Gold", "Green", "Harbor",
            "Iron", "Lake", "Long", "Marsh", "Mead", "Moss", "New", "Old", "Raven", "Rock", "Rose", "Sand", "Shore",
            "Summer", "Sun", "Thorn", "Wood", "Wolf", "Hollow", "Amber", "Birch", "Coal", "Deer", "Frost", "Hazel");

    private static final List<String> PLACE_ENDS = List.of("ford", "field", "ton", "wood", "gate", "side", "view",
            "dale", "port", "bridge", "haven", "ville", "brook", "hill", "ridge", "mont", "worth", "bury", "ham",
            "stead", "shire", "crest", "vale", "wick", "by", "minster", "mouth", "well", "ley", "moor", "burg", "hurst");
}
