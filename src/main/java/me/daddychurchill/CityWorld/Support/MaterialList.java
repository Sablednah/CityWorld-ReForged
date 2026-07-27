package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.compat.Material;

/**
 * A named, configurable list of materials — the mechanism by which a world's palettes (road
 * pavement, ore types, tree woods, …) can be overridden per world.
 *
 * <p><b>Empty until P7, and that is the point.</b> {@code MaterialProvider} fills these from the
 * per-world YAML; an unconfigured world leaves them empty and every accessor falls back to the
 * caller's default. So the whole config layer can arrive later without the call sites changing —
 * they already pass the default they want.
 *
 * <p>Upstream stored Bukkit {@code ItemStack}s rather than materials, only so the config could
 * round-trip item names. Our {@code Material} shim already spans blocks and items, so this holds
 * materials directly and the {@code read}/{@code write} halves land with the config at P7.
 */
public class MaterialList {

	private final String listName;
	private List<Material> items;

	public MaterialList(String name) {
		super();
		listName = name;
	}

	public MaterialList(String name, Material... materials) {
		super();
		listName = name;
		add(materials);
	}

	public String getListName() {
		return listName;
	}

	private void init(boolean clear) {
		if (items == null)
			items = new ArrayList<>();
		else if (clear)
			items.clear();
	}

	public void add(Material... materials) {
		init(false);
		for (Material material : materials) {
			items.add(material);
		}
	}

	public void remove(Material material) {
		if (items != null)
			for (int i = items.size() - 1; i >= 0; i--)
				if (items.get(i) == material)
					items.remove(i);
	}

	public int count() {
		return items == null ? 0 : items.size();
	}

	public Material getRandomMaterial(Odds odds) {
		return getRandomMaterial(odds, Material.AIR);
	}

	public Material getRandomMaterial(Odds odds, Material defaultMaterial) {
		if (items == null || count() == 0)
			return defaultMaterial;
		else
			return items.get(odds.getRandomInt(count()));
	}

	public Material getNthMaterial(int index, Material defaultMaterial) {
		if (items == null || count() == 0 || index > count() - 1)
			return defaultMaterial;
		else
			return items.get(index);
	}
}
