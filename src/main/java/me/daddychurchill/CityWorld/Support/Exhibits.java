package me.daddychurchill.CityWorld.Support;

import java.util.List;

import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * A museum's exhibits (owner, 2026-10-01: "a good way to get swords, shields, axes and spears loot out there —
 * probably damaged"): an artifact lying in an item frame on a podium, an armour stand in old armour, a shelf of
 * artifacts. Everything on show comes from three item tags a pack can extend — {@code #cityworld:museum/artifacts}
 * (weapons and tools, mostly what the podiums show), {@code #cityworld:museum/relics} (sherds, shells, instruments;
 * mostly what the shelves hold) and {@code #cityworld:museum/armour} — and anything with durability is worn most of
 * the way down.
 *
 * <p>Entities are built directly and added through the region, as in {@link Armoury}.
 */
public final class Exhibits {

	private Exhibits() {
	}

	public static final TagKey<Item> ARTIFACTS = TagKey.create(Registries.ITEM,
			Armoury.WEAPONS.location().withPath("museum/artifacts"));
	public static final TagKey<Item> RELICS = TagKey.create(Registries.ITEM,
			Armoury.WEAPONS.location().withPath("museum/relics"));
	public static final TagKey<Item> ARMOUR = TagKey.create(Registries.ITEM,
			Armoury.WEAPONS.location().withPath("museum/armour"));

	/** What the shipped tags say without a datapack, and the fallback if a pack empties them. */
	private static final Item[] ARTIFACT_FALLBACK = { Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
			Items.GOLDEN_SWORD, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.BOW, Items.CROSSBOW,
			Items.SHIELD, Items.TRIDENT, Items.STONE_PICKAXE, Items.GOLDEN_HOE, Items.FLINT_AND_STEEL };
	private static final Item[] RELIC_FALLBACK = { Items.GOAT_HORN, Items.NAUTILUS_SHELL, Items.SPYGLASS,
			Items.COMPASS, Items.CLOCK, Items.BRUSH, Items.BONE, Items.AMETHYST_SHARD, Items.BOWL, Items.FLINT };
	private static final Item[] ARMOUR_FALLBACK = { Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE,
			Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE,
			Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE,
			Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS, Items.IRON_HELMET, Items.IRON_CHESTPLATE,
			Items.IRON_LEGGINGS, Items.IRON_BOOTS };

	/** One of {@code item}, worn down to between a twentieth and two fifths of its life if it wears at all. */
	static ItemStack aged(Item item, Odds odds) {
		ItemStack stack = new ItemStack(item);
		if (stack.isDamageableItem()) {
			int life = stack.getMaxDamage();
			stack.setDamageValue(Math.min(life - 1, (int) (life * (0.6 + 0.35 * odds.getRandomDouble()))));
		}
		return stack;
	}

	/** Something to show: a weapon or tool {@code artifactOdds} of the time, else a relic. */
	private static ItemStack artifact(Odds odds, double artifactOdds) {
		List<Item> pool = odds.playOdds(artifactOdds) ? Armoury.pool(ARTIFACTS, ARTIFACT_FALLBACK)
				: Armoury.pool(RELICS, RELIC_FALLBACK);
		return aged(pool.get(odds.getRandomInt(pool.size())), odds);
	}

	/** A podium with an artifact lying on it in an item frame. The podium is at {@code y}, the frame above it. */
	public static boolean podium(RealBlocks chunk, Odds odds, int x, int y, int z) {
		ServerLevelAccessor server = chunk.getServerLevel();
		if (server == null || !chunk.isEmpty(x, y, z) || !chunk.isEmpty(x, y + 1, z))
			return false;
		chunk.setBlock(x, y, z, Material.QUARTZ_PILLAR);
		ItemFrame frame = new ItemFrame(server.getLevel(),
				new BlockPos(chunk.getOriginX() + x, y + 1, chunk.getOriginZ() + z), Direction.UP);
		frame.setSilent(true);
		frame.setItem(artifact(odds, 0.8), false);
		// never setRotation(int): it updates comparators through the real level, and a worker asking the real
		// level for a block of the chunk it is generating waits on the server thread for ever (see the AT)
		frame.setRotation(odds.getRandomInt(8), false);
		server.addFreshEntityWithPassengers(frame);
		return true;
	}

	/** An armour stand on a plinth, in two to four pieces of old armour, facing {@code facing}. */
	public static boolean armour(RealBlocks chunk, Odds odds, int x, int y, int z, BlockFace facing) {
		if (chunk.getServerLevel() == null || !chunk.isEmpty(x, y, z) || !chunk.isEmpty(x, y + 1, z)
				|| !chunk.isEmpty(x, y + 2, z))
			return false;
		chunk.setBlock(x, y, z, Material.of(net.minecraft.world.level.block.Blocks.CHISELED_QUARTZ_BLOCK));
		return Armoury.armourStand(chunk, odds, x, y + 1, z, facing, Armoury.pool(ARMOUR, ARMOUR_FALLBACK), 4,
				stack -> aged(stack.getItem(), odds));
	}

	/**
	 * A shelf of artifacts on a podium, facing {@code facing}: a shelf block from the shelf pool, each of its slots
	 * holding an artifact. False where this version or install has no shelf block, or the shelf holds nothing, so the
	 * caller can stand a podium there instead.
	 */
	public static boolean shelf(RealBlocks chunk, Odds odds, int x, int y, int z, BlockFace facing) {
		ServerLevelAccessor server = chunk.getServerLevel();
		if (server == null || !chunk.isEmpty(x, y, z) || !chunk.isEmpty(x, y + 1, z))
			return false;
		Material shelf = FurnitureTags.pick(FurnitureTags.SHELF, odds);
		if (shelf == null)
			return false;
		chunk.setBlock(x, y, z, Material.QUARTZ_PILLAR);
		if (!chunk.setFurniture(x, y + 1, z, shelf, FurnitureTags.facingFor(shelf, facing))) {
			chunk.setBlock(x, y, z, Material.AIR);
			return false;
		}
		// ask the REGION for the block entity: the chunk only has a stub for a block placed during generation
		var entity = server.getBlockEntity(new BlockPos(chunk.getOriginX() + x, y + 1, chunk.getOriginZ() + z));
		if (entity instanceof Container container)
			for (int slot = 0; slot < container.getContainerSize(); slot++)
				if (container.getItem(slot).isEmpty())
					container.setItem(slot, artifact(odds, 0.25));
		return true;
	}
}
