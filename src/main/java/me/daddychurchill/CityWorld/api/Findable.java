package me.daddychurchill.CityWorld.api;

/**
 * One kind of place a search can ask for: the menu of a compass, or any tool that lists "what is there to find in
 * this world". Part of CityWorld's small <b>public API</b>; obtain the list from {@link CityWorldAPI#findables} and
 * search with {@link CityWorldAPI#find}.
 *
 * @param id    stable id to search by: {@code place:<kind>} (a kind of lot: {@code place:hospital}),
 *              {@code shop:<trade>} ({@code shop:bookshop}), {@code district:<family>} ({@code district:industrial}),
 *              {@code building:<schematic>} ({@code building:winchester}), or {@code city} (the nearest city)
 * @param group what it is, for grouping a list: {@code place}, {@code shop}, {@code district}, {@code building}
 *              or {@code city}
 * @param title what to show a player ("Hospital", "Bookshop", "Industrial district", "The Winchester Tavern")
 */
public record Findable(String id, String group, String title) {
}
