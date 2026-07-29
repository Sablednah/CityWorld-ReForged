package me.daddychurchill.CityWorld.Support;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plugins.LootProvider;
import me.daddychurchill.CityWorld.Plugins.LootProvider.LootLocation;
import me.daddychurchill.CityWorld.compat.Block;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Location;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * The decoration half of the block seam — everything that writes into a <em>live</em> world rather
 * than a chunk under construction. Its sibling {@link InitialBlocks} covers the generation half.
 *
 * <p>The whole class rests on one abstract primitive, {@link #getActualBlock(int, int, int)}, which
 * subclasses ({@code RealBlocks}, {@code RelativeBlocks}, {@code WorldBlocks}, {@code CornerBlocks})
 * define to map their own coordinate space onto a real position. That is why it binds to a
 * {@link LevelAccessor} — which {@code AbstractBlocks} deliberately does not have, since the
 * generation side has no world to speak of yet.
 *
 * <h2>Porting note: the read-modify-write idiom is gone</h2>
 *
 * <p>The original repeatedly did:
 *
 * <pre>{@code
 * block.setType(material, false);           // write the default state
 * BlockData data = block.getBlockData();    // read it back, mutably
 * ((Directional) data).setFacing(facing);   // adjust
 * block.setBlockData(data, doPhysics);      // write it again
 * }</pre>
 *
 * <p>because Bukkit's {@code BlockData} was a mutable object you could only obtain from a placed
 * block. A modern {@link BlockState} is immutable and reachable straight from the block, so the
 * port derives the finished state first and writes <em>once</em>:
 *
 * <pre>{@code
 * setActualBlock(x, y, z, with(stateOf(material), HORIZONTAL_FACING, dir));
 * }</pre>
 *
 * <p>Same result, half the writes, and no window where a half-configured block is in the world.
 * The Bukkit {@code instanceof} chains ({@code Directional}, {@code Bisected}, {@code Levelled} …)
 * become {@code hasProperty} guards, which is what those interfaces really tested for; {@link #with}
 * is that translation and leaves the state untouched when the property is absent — exactly as the
 * old {@code instanceof} test fell through.
 */
public abstract class SupportBlocks extends AbstractBlocks {

	protected final LevelAccessor world;

	/** The live level this decorates — needed by lots that spin up their own {@link WorldBlocks}. */
	public final LevelAccessor getWorld() { return world; }

	private boolean doPhysics;
	private boolean doClearData;

	SupportBlocks(CityWorldGenerator generator, LevelAccessor world) {
		super(generator);

		this.world = world;

		doPhysics = false;
		doClearData = false;
	}

	public abstract Block getActualBlock(int x, int y, int z);

	public final boolean getDoPhysics() {
		return doPhysics;
	}

	public final boolean setDoPhysics(boolean dophysics) {
		boolean was = doPhysics;
		doPhysics = dophysics;
		return was;
	}

	public final boolean getDoClearData() {
		return doClearData;
	}

	public final void setDoClearData(boolean docleardata) {
		boolean was = doClearData;
		doClearData = docleardata;
	}

	@Override
	public final void setBlockIfEmpty(int x, int y, int z, Material material) {
		Block block = getActualBlock(x, y, z);
		if (isEmpty(block) && !isEmpty(x, y - 1, z))
			setActualBlock(block, material, getDoPhysics(x, z));
	}

	private boolean getDoPhysics(int x, int z) {
		boolean thisDoPhysics = doPhysics;
		if (thisDoPhysics)
			thisDoPhysics = !onEdgeXZ(x, z);
		return thisDoPhysics;
	}

	private void setActualBlock(Block block, Material material, boolean thisDoPhysics) {
		block.setType(material, thisDoPhysics);
	}

	/** Write a fully derived state, honouring the physics rules for this column. */
	private void setActualBlock(int x, int y, int z, BlockState state) {
		getActualBlock(x, y, z).setBlockData(state, getDoPhysics(x, z));
	}

	@Override
	public final void setBlock(int x, int y, int z, Material material) {
		setActualBlock(getActualBlock(x, y, z), material, getDoPhysics(x, z));
	}

	private boolean isType(Block block, Material... types) {
		Material type = block.getType();
		for (Material test : types)
			if (type == test)
				return true;
		return false;
	}

	public final boolean isType(int x, int y, int z, Material type) {
		return getActualBlock(x, y, z).getType() == type;
	}

	public final boolean isOfTypes(int x, int y, int z, Material... types) {
		return isType(getActualBlock(x, y, z), types);
	}

	public final void setBlockIfNot(int x, int y, int z, Material... types) {
		if (!isOfTypes(x, y, z, types))
			setBlock(x, y, z, types[0]);
	}

	private boolean isEmpty(Block block) {
		return block.isEmpty();
	}

	// NOTE the original special-cased the chunk edges here: under Bukkit, testing .isEmpty() on or
	// near the edge (0, 1, 14, 15) threw when CityWorld was the default world generator, so edge
	// columns were compared against Material.AIR instead. Both spellings are BlockState.isAir() now
	// and neither reaches outside the chunk, so the workaround is gone.
	@Override
	public final boolean isEmpty(int x, int y, int z) {
		return getActualBlock(x, y, z).isEmpty();
	}

	public final boolean isPartiallyEmpty(int x, int y1, int y2, int z) {
		for (int y = y1; y < y2; y++) {
			if (isEmpty(x, y, z))
				return true;
		}
		return false;
	}

	public final boolean isPartiallyEmpty(int x1, int x2, int y1, int y2, int z1, int z2) {
		for (int x = x1; x < x2; x++) {
			for (int y = y1; y < y2; y++) {
				for (int z = z1; z < z2; z++) {
					if (isEmpty(x, y, z))
						return true;
				}
			}
		}
		return false;
	}

	public abstract boolean isSurroundedByEmpty(int x, int y, int z);

	public final boolean isWater(int x, int y, int z) {
		return isOfTypes(x, y, z, Material.WATER);
//		return getActualBlock(x, y, z).isLiquid();
	}

	public abstract boolean isByWater(int x, int y, int z);

	public final Location getBlockLocation(int x, int y, int z) {
		Block block = getActualBlock(x, y, z);
		return new Location(world, block.getX(), block.getY(), block.getZ());
	}

	@Override
	public final void setAtmosphereBlock(int x, int y, int z, Material material) {
		setBlock(x, y, z, material);
		// West
		if (x > 0)
			clearFacing(x - 1, y, z, BlockFace.EAST);
		// East
		if (x < 15)
			clearFacing(x + 1, y, z, BlockFace.WEST);
		// North
		if (z > 0)
			clearFacing(x, y, z - 1, BlockFace.SOUTH);
		// South
		if (z < 15)
			clearFacing(x, y, z + 1, BlockFace.NORTH);
	}

	/**
	 * Break a neighbour's connection back towards the block we just placed. Unlike everywhere else
	 * in this class, this reads an existing state rather than deriving one from a material, so it
	 * keeps the original's read-modify-write shape — and its catch-all, which guarded against
	 * Bukkit throwing on access near the chunk edge.
	 */
	private void clearFacing(int x, int y, int z, BlockFace face) {
		try {
			Property<Boolean> faceProp = Material.faceProperty(face);
			Block block = getActualBlock(x, y, z);
			BlockState state = block.getBlockData();
			if (faceProp != null && state.hasProperty(faceProp))
				block.setBlockData(state.setValue(faceProp, false), false);
		} catch (Exception ignored) {

		}
	}

	@Override
	public final void clearBlock(int x, int y, int z) {
		getActualBlock(x, y, z).setType(Material.AIR, getDoPhysics(x, z));
	}

	@Override
	public final void setWalls(int x1, int x2, int y1, int y2, int z1, int z2, Material material) {
		if (material.hasFaces()) {
			setBlocks(x1 + 1, x2 - 1, y1, y2, z1, z1 + 1, material, BlockFace.EAST, BlockFace.WEST); // N
			setBlocks(x1 + 1, x2 - 1, y1, y2, z2 - 1, z2, material, BlockFace.EAST, BlockFace.WEST); // S
			setBlocks(x1, x1 + 1, y1, y2, z1 + 1, z2 - 1, material, BlockFace.SOUTH, BlockFace.NORTH); // W
			setBlocks(x2 - 1, x2, y1, y2, z1 + 1, z2 - 1, material, BlockFace.SOUTH, BlockFace.NORTH); // E
			setBlocks(x1, y1, y2, z1, material, BlockFace.SOUTH, BlockFace.EAST); // NW
			setBlocks(x1, y1, y2, z2 - 1, material, BlockFace.NORTH, BlockFace.EAST); // SW
			setBlocks(x2 - 1, y1, y2, z1, material, BlockFace.SOUTH, BlockFace.WEST); // NE
			setBlocks(x2 - 1, y1, y2, z2 - 1, material, BlockFace.NORTH, BlockFace.WEST); // SE
		} else {
			setBlocks(x1, x2, y1, y2, z1, z1 + 1, material); // N
			setBlocks(x1, x2, y1, y2, z2 - 1, z2, material); // S
			setBlocks(x1, x1 + 1, y1, y2, z1 + 1, z2 - 1, material); // W
			setBlocks(x2 - 1, x2, y1, y2, z1 + 1, z2 - 1, material); // E
		}
	}

	public final void fillBlocks(int x1, int x2, int y, int z1, int z2, Material material) {
		fillBlocks(x1, x2, y, y + 1, z1, z2, material);
	}

	private void fillBlocks(int x1, int x2, int y1, int y2, int z1, int z2, Material material) {
		if (!material.hasFaces()) {
			setBlocks(x1, x2, y1, y2, z1, z2, material);
		}
		setBlocks(x1, x2, y1, y2, z1, z2, material, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
		setBlocks(x1 + 1, x2 - 1, y1, y2, z1, z1 + 1, material, BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH); // N
		setBlocks(x1 + 1, x2 - 1, y1, y2, z2 - 1, z2, material, BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH); // S
		setBlocks(x1, x1 + 1, y1, y2, z1 + 1, z2 - 1, material, BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST); // W
		setBlocks(x2 - 1, x2, y1, y2, z1 + 1, z2 - 1, material, BlockFace.SOUTH, BlockFace.NORTH, BlockFace.WEST); // E
		setBlocks(x1, y1, y2, z1, material, BlockFace.SOUTH, BlockFace.EAST); // NW
		setBlocks(x1, y1, y2, z2 - 1, material, BlockFace.NORTH, BlockFace.EAST); // SW
		setBlocks(x2 - 1, y1, y2, z1, material, BlockFace.SOUTH, BlockFace.WEST); // NE
		setBlocks(x2 - 1, y1, y2, z2 - 1, material, BlockFace.NORTH, BlockFace.WEST); // SE
	}

	@Override
	public final int setLayer(int blocky, Material material) {
		setBlocks(0, width, blocky, blocky + 1, 0, width, material);
		return blocky + 1;
	}

	@Override
	public final int setLayer(int blocky, int height, Material material) {
		setBlocks(0, width, blocky, blocky + height, 0, width, material);
		return blocky + height;
	}

	@Override
	public final int setLayer(int blocky, int height, int inset, Material material) {
		setBlocks(inset, width - inset, blocky, blocky + height, inset, width - inset, material);
		return blocky + height;
	}

	// @@ I REALLY NEED TO FIGURE A DIFFERENT WAY TO DO THIS
	final boolean isNonstackableBlock(Block block) { // either because it really isn't or it just doesn't look
		// good
		return !block.getType().isOccluding();
	}

	public final boolean isNonstackableBlock(int x, int y, int z) {
		return isNonstackableBlock(getActualBlock(x, y, z));
	}

	private int clamp(double value, int min, int max) {
		return Mth.floor((value - Mth.floor(value)) * (max - min)) + min;
	}

	public final void setBlock(int x, int y, int z, Material material, boolean light) {
		setActualBlock(x, y, z, with(stateOf(material), BlockStateProperties.LIT, light));
	}

	/** A lantern (or soul/copper lantern) hung <em>under</em> a block or chain — sets the HANGING state so
	 *  it dangles instead of standing (a plain {@code setBlock} gives the floor-standing variant). */
	public final void setHangingLantern(int x, int y, int z, Material material) {
		setActualBlock(x, y, z, with(stateOf(material), BlockStateProperties.HANGING, true));
	}

	/** A trapdoor set OPEN — it stands vertical on its {@code facing} side, a raised panel: a toilet lid, a
	 *  cabinet door, a shelf front. A plain {@code setBlock} gives it flat (closed). */
	public final void setOpenTrapdoor(int x, int y, int z, Material material, BlockFace facing) {
		setActualBlock(x, y, z, with(withDirection(stateOf(material), facing), BlockStateProperties.OPEN, true));
	}

	/** Leaves that never decay — sets PERSISTENT so hand-built trees (zoo/biodome/decor) keep their canopy
	 *  even when the trunk is thin or interrupted. A plain {@code setBlock} leaves PERSISTENT false, so
	 *  leaves more than a few blocks from a log rot away. */
	public final void setLeaves(int x, int y, int z, Material material) {
		setActualBlock(x, y, z, with(stateOf(material), BlockStateProperties.PERSISTENT, true));
	}

	/** A "pipe" block (chorus plant, iron bars, glass pane, …) with the given side connections switched on,
	 *  so a stalk/run actually joins up instead of rendering as loose cubes. Faces map to the block's
	 *  {@code NORTH/SOUTH/EAST/WEST/UP/DOWN} boolean properties; absent ones are ignored. */
	public final void setPipeBlock(int x, int y, int z, Material material, BlockFace... connections) {
		BlockState state = stateOf(material);
		for (BlockFace face : connections)
			state = withPipe(state, face);
		setActualBlock(x, y, z, state);
	}

	private static BlockState withPipe(BlockState state, BlockFace face) {
		Direction dir = face.toDirection();
		if (dir == null)
			return state;
		return with(state, switch (dir) {
		case NORTH -> BlockStateProperties.NORTH;
		case SOUTH -> BlockStateProperties.SOUTH;
		case EAST -> BlockStateProperties.EAST;
		case WEST -> BlockStateProperties.WEST;
		case UP -> BlockStateProperties.UP;
		case DOWN -> BlockStateProperties.DOWN;
		}, true);
	}

	/**
	 * Place a block whose state carries a magnitude, from a 0..1 fraction of its range — the old
	 * {@code Ageable} / {@code Levelled} / {@code Snow} trio. Each of those exposed its own
	 * getMaximum*(); modern blocks all express it as one integer property, so
	 * {@link #withScaledLevel} finds whichever the block actually has.
	 */
	public final void setBlock(int x, int y, int z, Material material, double level) {
		setActualBlock(x, y, z, withScaledLevel(stateOf(material), level));
	}

	/**
	 * Note: this no longer fills the cauldron. The 1.13 flattening split Bukkit's one levelled
	 * CAULDRON into an empty {@code cauldron} plus {@code water_cauldron}/{@code lava_cauldron},
	 * and only the latter carry a level — so the level lands on a block with nowhere to put it and
	 * is dropped. Left as-is deliberately: picking the filled variant is a decoration concern, and
	 * decoration (with the callers that reach this) is Phase 5.
	 */
	public final void setCauldron(int x, int y, int z, Odds odds) {
		setBlock(x, y, z, Material.CAULDRON, odds.getRandomDouble());
	}

	public final void colorizeBlocks(int x1, int x2, int y1, int y2, int z1, int z2, Material find, Colors colors) {
		for (int x = x1; x < x2; x++) {
			for (int y = y1; y < y2; y++) {
				for (int z = z1; z < z2; z++) {
					if (isType(x, y, z, find))
						setBlock(x, y, z, colors.getTerracotta());
				}
			}
		}
	}

	public final void setBlockRandomly(int x, int y, int z, Odds odds, Material... materials) {
		setBlock(x, y, z, odds.getRandomMaterial(materials));
	}

	public final void setVine(int x, int y, int z, BlockFace... faces) {
		setActualBlock(x, y, z, Material.VINE.withFaces(faces));
	}

	public final void setVines(int x, int y1, int y2, int z, BlockFace... faces) {
		for (int y = y1; y < y2; y++)
			setVine(x, y, z, faces);
	}

	public final void setBlock(int x, int y, int z, Material material, RailShape shape, boolean powered) {
		BlockState state = withRailShape(stateOf(material), shape);
		setActualBlock(x, y, z, with(state, BlockStateProperties.POWERED, powered));
	}

	@Override
	public final void setBlock(int x, int y, int z, Material material, SlabType type) {
		setActualBlock(x, y, z, material.asSlab(type));
	}

	@Override
	public final void setBlock(int x, int y, int z, Material material, BlockFace facing) {
		setActualBlock(x, y, z, material.withFacing(facing));
	}

	@Override
	public final void setBlock(int x, int y, int z, Material material, BlockFace... facing) {
		setActualBlock(x, y, z, material.withFaces(facing));
	}

	public final void setStair(int x, int y, int z, Material material, BlockFace facing) {
		setStair(x, y, z, material, facing, StairsShape.STRAIGHT);
	}

	public final void setStair(int x, int y, int z, Material material, BlockFace facing, StairsShape shape) {
		BlockState state = withDirection(stateOf(material), facing);
		setActualBlock(x, y, z, with(state, BlockStateProperties.STAIRS_SHAPE, shape));
	}

	public final void drawCrane(DataContext context, Odds odds, int x, int y, int z) {
		Colors colors = new Colors(odds);

		// vertical bit
		setBlocks(x, y, y + 8, z, Material.IRON_BARS, BlockFace.WEST);
		setBlocks(x - 1, y, y + 8, z, Material.IRON_BARS, BlockFace.EAST); // 1.9 shows iron fences very thin now
		setBlocks(x, y + 8, y + 10, z, Material.STONE);
		setBlocks(x - 1, y + 8, y + 10, z, Material.STONE_SLAB);
		setBlock(x, y + 10, z, context.torchMat, BlockFace.UP);

		// horizontal bit
		setBlock(x + 1, y + 8, z, Material.GLASS);
		setBlocks(x + 2, x + 10, y + 8, y + 9, z, z + 1, Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST);
		setBlock(x + 10, y + 8, z, Material.IRON_BARS, BlockFace.WEST);
		setBlocks(x + 1, x + 10, y + 9, y + 10, z, z + 1, Material.STONE_SLAB);
		setBlock(x + 10, y + 9, z, Material.STONE_BRICK_STAIRS, BlockFace.WEST);

		// counter weight
		setBlock(x - 2, y + 9, z, Material.STONE_SLAB);
		setBlock(x - 3, y + 9, z, Material.STONE_BRICK_STAIRS, BlockFace.EAST);
		setBlocks(x - 3, x - 1, y + 7, y + 9, z, z + 1, colors.getConcrete());
	}

	public final void setTable(int x1, int x2, int y, int z1, int z2, Material tableLeg, Material tableTop) {
		for (int x = x1; x < x2; x++) {
			for (int z = z1; z < z2; z++) {
				setTable(x, y, z, tableLeg, tableTop);
			}
		}
	}

	public final void setTable(int x, int y, int z, Material tableLeg, Material tableTop) {
		setBlock(x, y, z, tableLeg);
		setBlock(x, y + 1, z, tableTop);
	}

	// Upstream took a trailing doPhysics flag here and in setBedBlock, but never read it — both
	// ended `setBlockData(data, getDoPhysics(x, z))`, so the second half of a door or bed was never
	// placed with physics the way the `true` at those call sites suggests. Dropped rather than
	// carried over: behaviour is unchanged, and the signature no longer promises something it
	// doesn't do.
	private void setDoorBlock(int x, int y, int z, Material material, BlockFace facing, Half half,
			DoorHingeSide hinge) {
		BlockState state = material.asDoorHalf(half == Half.TOP, facing);
		setActualBlock(x, y, z, with(state, BlockStateProperties.DOOR_HINGE, hinge));
	}

	//@@	public void setDoor(int x, int y, int z, Material material, BadMagic.Door direction) {
	public void setDoor(int x, int y, int z, Material material, BlockFace facing) {
		clearBlock(x, y, z);
		clearBlock(x, y + 1, z);

		DoorHingeSide hinge = DoorHingeSide.LEFT;

		facing = fixFacing(facing);
		facing = facing.getOppositeFace();

		setDoorBlock(x, y, z, material, facing, Half.BOTTOM, hinge);
		setDoorBlock(x, y + 1, z, material, facing, Half.TOP, hinge);
	}

	/** A door with a chosen hinge side — for building double doors (two leaves that meet in the middle). */
	public void setDoor(int x, int y, int z, Material material, BlockFace facing, boolean rightHinge) {
		clearBlock(x, y, z);
		clearBlock(x, y + 1, z);
		BlockFace f = fixFacing(facing).getOppositeFace();
		DoorHingeSide hinge = rightHinge ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
		setDoorBlock(x, y, z, material, f, Half.BOTTOM, hinge);
		setDoorBlock(x, y + 1, z, material, f, Half.TOP, hinge);
	}

	/** A 2-wide double door at (x,y,z): the second leaf sits one cell perpendicular to {@code facing}, the
	 *  two hinged so they open apart — a grand entrance. */
	public void setDoubleDoor(int x, int y, int z, Material material, BlockFace facing) {
		boolean alongX = facing == BlockFace.NORTH || facing == BlockFace.SOUTH;
		int x2 = alongX ? x + 1 : x;
		int z2 = alongX ? z : z + 1;
		setDoor(x, y, z, material, facing, false);
		setDoor(x2, y, z2, material, facing, true);
	}

	public void setFenceDoor(int x, int y1, int y2, int z, Material material, BlockFace facing) {

		facing = fixFacing(facing);

		if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) {
			setBlocks(x, y1, y2, z, material, BlockFace.EAST, BlockFace.WEST);
		} else if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
			setBlocks(x, y1, y2, z, material, BlockFace.NORTH, BlockFace.SOUTH);
		}
	}

	public final void setLadder(int x, int y1, int y2, int z, BlockFace direction) {

		// this calculates which wall the ladder is on
		int offsetX = 0;
		int offsetZ = 0;
		switch (direction) {
		case EAST:
			offsetX = -1;
			break;
		case WEST:
			offsetX = 1;
			break;
		case SOUTH:
			offsetZ = -1;
			break;
		case NORTH:
		default:
			offsetZ = 1;
			break;
		}

		// only put the ladder on the wall (see above) if there is actually a wall
		for (int y = y1; y < y2; y++) {
			if (!isEmpty(x + offsetX, y, z + offsetZ)) {
				setBlock(x, y, z, Material.LADDER, direction);
			}
		}
	}

	public final void setTallBlock(int x, int y, int z, Material material) {
		setBlock(x, y, z, material, Half.BOTTOM);
		setBlock(x, y + 1, z, material, Half.TOP);
	}

	private void setBlock(int x, int y, int z, Material material, Half half) {
		setActualBlock(x, y, z, withHalf(stateOf(material), half));
	}

	public final void setBlock(int x, int y, int z, Material material, BlockFace facing, Half half) {
		setActualBlock(x, y, z, withHalf(withDirection(stateOf(material), facing), half));
	}

	public final void setBlocks(int x1, int x2, int y, int z1, int z2, Material material, BlockFace facing) {
		for (int x = x1; x < x2; x++)
			for (int z = z1; z < z2; z++)
				setBlock(x, y, z, material, facing);
	}

	public final void setBlocks(int x1, int x2, int y, int z1, int z2, Material material, BlockFace... facing) {
		for (int x = x1; x < x2; x++)
			for (int z = z1; z < z2; z++)
				setBlock(x, y, z, material, facing);
	}

	public final void setBlocks(int x1, int x2, int y, int z1, int z2, Material material, BlockFace facing, Half half) {
		for (int x = x1; x < x2; x++)
			for (int z = z1; z < z2; z++)
				setBlock(x, y, z, material, facing, half);
	}

	public final void setBlocks(int x1, int x2, int y, int z1, int z2, Material material, Half half) {
		for (int x = x1; x < x2; x++)
			for (int z = z1; z < z2; z++)
				setBlock(x, y, z, material, half);
	}

	public final void setChest(CityWorldGenerator generator, int x, int y, int z, Odds odds,
			LootProvider lootProvider, LootLocation lootLocation) {
		setChest(generator, x, y, z, odds, lootProvider, lootLocation, Material.CHEST);
	}

	// As above but with a caller-chosen chest block (e.g. a copper chest for the mines). Any chest-family
	// block works: double-chest pairing keys off the CHEST_TYPE property and the loot fill off the
	// container block entity, neither of which is specific to plain oak chests.
	public final void setChest(CityWorldGenerator generator, int x, int y, int z, Odds odds,
			LootProvider lootProvider, LootLocation lootLocation, Material chestMaterial) {
		if (!onEdgeXZ(x, z)) {
			BlockFace facing = BlockFace.NORTH;
			if (isEmpty(x - 1, y, z))
				facing = BlockFace.WEST;
			else if (isEmpty(x - 1, y, z))
				facing = BlockFace.EAST;
			else if (isEmpty(x, y, z + 1))
				facing = BlockFace.SOUTH;
			setChest(generator, x, y, z, facing, odds, lootProvider, lootLocation, chestMaterial);
		}
	}

	public final void setChest(CityWorldGenerator generator, int x, int y, int z, BlockFace facing, Odds odds,
			LootProvider lootProvider, LootLocation lootLocation) {
		setChest(generator, x, y, z, facing, odds, lootProvider, lootLocation, Material.CHEST);
	}

	public final void setChest(CityWorldGenerator generator, int x, int y, int z, BlockFace facing, Odds odds,
			LootProvider lootProvider, LootLocation lootLocation, Material chestMaterial) {
		if (!onNearEdgeXZ(x, z)) {
//			generator.reportFormatted("CHEST AT %d, %d, %d", x, y, z);
			setBlock(x, y, z, chestMaterial, facing);
			Block block = getActualBlock(x, y, z);
			connectDoubleChest(x, y, z, facing, chestMaterial);
			if (isType(block, chestMaterial))
				lootProvider.setLoot(generator, odds, lootLocation, block);
		}
//		else
//			generator.reportFormatted("SKIPPED CHEST AT %d, %d, %d", x, y, z);
	}

	public final void setDoubleChest(CityWorldGenerator generator, int x, int y, int z, BlockFace facing, Odds odds,
			LootProvider lootProvider, LootLocation lootLocation) {
		switch (facing) {
		default:
		case EAST:
		case WEST:
			if (z == 15) // Whoops, too far
				z = 14;
			setChest(generator, x, y, z, facing, odds, lootProvider, lootLocation);
			setChest(generator, x, y, z + 1, facing, odds, lootProvider, lootLocation);
			break;
		case NORTH:
		case SOUTH:
			if (x == 15) // Whoops, too far
				x = 14;
			setChest(generator, x, y, z, facing, odds, lootProvider, lootLocation);
			setChest(generator, x + 1, y, z, facing, odds, lootProvider, lootLocation);
			break;
		}
	}

	public final void setWallSign(int x, int y, int z, BlockFace facing, String... lines) {
		setWallSign(x, y, z, Material.BIRCH_WALL_SIGN, facing, lines);
	}

	public final void setWallSign(int x, int y, int z, Material sign, BlockFace facing, String... lines) {
		setActualBlock(x, y, z, withDirection(stateOf(sign), facing));
		setSignText(getActualBlock(x, y, z), lines);
	}

	public final void setSignPost(int x, int y, int z, BlockFace rotation, String... lines) {
		setSignPost(x, y, z, Material.BIRCH_SIGN, rotation, lines);
	}

	public final void setSignPost(int x, int y, int z, Material sign, BlockFace rotation, String... lines) {
		BlockState state = stateOf(sign);
		Direction direction = rotation.toDirection();
		if (direction != null && direction.getAxis().isHorizontal())
			state = with(state, BlockStateProperties.ROTATION_16, RotationSegment.convertToSegment(direction));
		setActualBlock(x, y, z, state);
		setSignText(getActualBlock(x, y, z), lines);
	}

//	private int lastDistance = -1;

	public final void setLeaf(int x, int y, int z, Material material, boolean isPersistent) {
		setActualBlock(x, y, z, with(stateOf(material), BlockStateProperties.PERSISTENT, isPersistent));
	}

	public final void setLeaves(int x, int y1, int y2, int z, Material material, boolean isPersistent) {
		for (int y = y1; y < y2; y++)
			setLeaf(x, y, z, material, isPersistent);
	}

	public final void setLeaves(int x1, int x2, int y1, int y2, int z1, int z2, Material material,
			boolean isPersistent) {
		for (int x = x1; x < x2; x++)
			for (int y = y1; y < y2; y++)
				for (int z = z1; z < z2; z++)
					setLeaf(x, y, z, material, isPersistent);
	}

	public final void setLeafWalls(int x1, int x2, int y1, int y2, int z1, int z2, Material material,
			boolean isPersistent) {
		setLeaves(x1, x2, y1, y2, z1, z1 + 1, material, isPersistent); // N
		setLeaves(x1, x2, y1, y2, z2 - 1, z2, material, isPersistent); // S
		setLeaves(x1, x1 + 1, y1, y2, z1 + 1, z2 - 1, material, isPersistent); // W
		setLeaves(x2 - 1, x2, y1, y2, z1 + 1, z2 - 1, material, isPersistent); // E
	}

	/** See setDoorBlock: upstream's unused doPhysics flag is dropped here too. */
	private void setBedBlock(int x, int y, int z, Material material, BlockFace facing, BedPart part) {
		BlockState state = withDirection(stateOf(material), facing);
		setActualBlock(x, y, z, with(state, BlockStateProperties.BED_PART, part));
	}

	public final void setBed(int x, int y, int z, Material material, BlockFace facing) {
		switch (facing) {
		default:
		case NORTH:
			setBedBlock(x, y, z, material, BlockFace.SOUTH, BedPart.FOOT);
			setBedBlock(x, y, z + 1, material, BlockFace.SOUTH, BedPart.HEAD);
			break;
		case SOUTH:
			setBedBlock(x, y, z + 1, material, BlockFace.NORTH, BedPart.FOOT);
			setBedBlock(x, y, z, material, BlockFace.NORTH, BedPart.HEAD);
			break;
		case EAST:
			setBedBlock(x + 1, y, z, material, BlockFace.WEST, BedPart.FOOT);
			setBedBlock(x, y, z, material, BlockFace.WEST, BedPart.HEAD);
			break;
		case WEST:
			setBedBlock(x, y, z, material, BlockFace.EAST, BedPart.FOOT);
			setBedBlock(x + 1, y, z, material, BlockFace.EAST, BedPart.HEAD);
			break;
		}
	}

	private void connectDoubleChest(int x, int y, int z, BlockFace facing) {
		connectDoubleChest(x, y, z, facing, Material.CHEST);
	}

	private void connectDoubleChest(int x, int y, int z, BlockFace facing, Material chestMaterial) {
		Block block = getActualBlock(x, y, z);
		if (!isType(block, chestMaterial)) {
			return;
		}
		if (chestType(block) != ChestType.SINGLE) {
			return;
		}
		Block checkLeftBlock, checkRightBlock;
		switch (facing) {
		default:
		case EAST:
			checkLeftBlock = z > 0 ? getActualBlock(x, y, z - 1) : null;
			checkRightBlock = z < 15 ? getActualBlock(x, y, z + 1) : null;
			break;
		case SOUTH:
			checkLeftBlock = x < 15 ? getActualBlock(x + 1, y, z) : null;
			checkRightBlock = x > 0 ? getActualBlock(x - 1, y, z) : null;
			break;
		case WEST:
			checkLeftBlock = z < 15 ? getActualBlock(x, y, z + 1) : null;
			checkRightBlock = z > 0 ? getActualBlock(x, y, z - 1) : null;
			break;
		case NORTH:
			checkLeftBlock = x > 0 ? getActualBlock(x - 1, y, z) : null;
			checkRightBlock = x < 15 ? getActualBlock(x + 1, y, z) : null;
			break;
		}
		if (checkLeftBlock != null && isType(checkLeftBlock, chestMaterial) && chestFaces(checkLeftBlock, facing)) {
			pairChests(block, ChestType.RIGHT, checkLeftBlock, ChestType.LEFT);

		} else if (checkRightBlock != null && isType(checkRightBlock, chestMaterial)
				&& chestFaces(checkRightBlock, facing)) {
			pairChests(block, ChestType.LEFT, checkRightBlock, ChestType.RIGHT);
		}
	}

	private static ChestType chestType(Block block) {
		return block.getBlockData().getValueOrElse(BlockStateProperties.CHEST_TYPE, ChestType.SINGLE);
	}

	private static boolean chestFaces(Block block, BlockFace facing) {
		BlockState state = block.getBlockData();
		return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
				&& state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing.toDirection();
	}

	private static void pairChests(Block block, ChestType blockType, Block other, ChestType otherType) {
		block.setBlockData(with(block.getBlockData(), BlockStateProperties.CHEST_TYPE, blockType));
		other.setBlockData(with(other.getBlockData(), BlockStateProperties.CHEST_TYPE, otherType));
	}

	public final void setGate(int x, int y, int z, Material material, BlockFace facing, boolean isOpen) {
		BlockState state = withDirection(stateOf(material), facing);
		setActualBlock(x, y, z, with(state, BlockStateProperties.OPEN, isOpen));
	}

	public final void setWaterLoggedBlocks(int x, int y1, int y2, int z, Material material) {
		for (int y = y1; y < y2; y++)
			setWaterLoggedBlock(x, y, z, material);
	}

	private void setWaterLoggedBlock(int x, int y, int z, Material material) {
		setActualBlock(x, y, z, with(stateOf(material), BlockStateProperties.WATERLOGGED, true));
	}

	// ---- BlockData → BlockState translation helpers ------------------------------------------

	/**
	 * The material's default state, or air for an item-only material — mirroring what
	 * {@code Block.setType} does with one, so the derive-then-write paths behave like the old
	 * setType-then-adjust ones did.
	 */
	private static BlockState stateOf(Material material) {
		BlockState state = material.getBlockState();
		return state == null ? Blocks.AIR.defaultBlockState() : state;
	}

	/**
	 * Set a property if the block has it, otherwise leave the state alone. This is the port of
	 * Bukkit's {@code if (data instanceof Directional) ((Directional) data).setFacing(...)} — the
	 * interface test was really asking "does this block have that property?".
	 */
	private static <T extends Comparable<T>, V extends T> BlockState with(BlockState state, Property<T> property,
			V value) {
		return state.hasProperty(property) ? state.setValue(property, value) : state;
	}

	/**
	 * Bukkit's {@code Directional.setFacing} — the strict form, for blocks that face one way
	 * (stairs, gates, beds, wall signs). {@link Material#withFacing} is the broader chain that also
	 * falls back to connection faces and axes, and is used where the original tested for
	 * {@code MultipleFacing}/{@code Orientable} too.
	 */
	private static BlockState withDirection(BlockState state, BlockFace facing) {
		Direction direction = facing.toDirection();
		if (direction == null)
			return state;
		if (state.hasProperty(BlockStateProperties.FACING))
			return state.setValue(BlockStateProperties.FACING, direction);
		if (direction.getAxis().isHorizontal() && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
			return state.setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
		return state;
	}

	/**
	 * Bukkit's {@code Bisected.setHalf}. One Bukkit interface covers two modern properties: doors
	 * and tall plants split into {@code DOUBLE_BLOCK_HALF} (upper/lower), stairs and trapdoors into
	 * {@code HALF} (top/bottom).
	 */
	private static BlockState withHalf(BlockState state, Half half) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))
			return state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
					half == Half.TOP ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
		return with(state, BlockStateProperties.HALF, half);
	}

	/**
	 * Bukkit's {@code Rail.setShape}. Ordinary rails take all ten shapes; powered/detector/activator
	 * rails only the six straight ones, under a separate property — so try both, and ignore a curve
	 * asked of a rail that cannot bend (as Bukkit's own setter would have refused it).
	 */
	private static BlockState withRailShape(BlockState state, RailShape shape) {
		if (state.hasProperty(BlockStateProperties.RAIL_SHAPE))
			return state.setValue(BlockStateProperties.RAIL_SHAPE, shape);
		if (state.hasProperty(BlockStateProperties.RAIL_SHAPE_STRAIGHT)
				&& BlockStateProperties.RAIL_SHAPE_STRAIGHT.getPossibleValues().contains(shape))
			return state.setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, shape);
		return state;
	}

	/**
	 * Scale a 0..1 fraction across whichever magnitude property the block carries — the modern
	 * counterpart of Bukkit's {@code Ageable} (age), {@code Levelled} (level) and {@code Snow}
	 * (layers). Looked up by name because there is no single AGE property to reference: vanilla
	 * declares AGE_1 … AGE_25, and LEVEL alongside LEVEL_CAULDRON, each with its own range. The
	 * search order matches the original's instanceof chain.
	 */
	private BlockState withScaledLevel(BlockState state, double level) {
		for (String name : new String[] { "age", "level", "layers" }) {
			IntegerProperty property = intPropertyNamed(state, name);
			if (property != null) {
				int min = property.getPossibleValues().stream().min(Integer::compare).orElse(0);
				int max = property.getPossibleValues().stream().max(Integer::compare).orElse(0);
				return state.setValue(property, clamp(level, min, max));
			}
		}
		return state;
	}

	private static IntegerProperty intPropertyNamed(BlockState state, String name) {
		for (Property<?> property : state.getProperties()) {
			if (property instanceof IntegerProperty && property.getName().equals(name))
				return (IntegerProperty) property;
		}
		return null;
	}

	/**
	 * Write a sign's front text, the port of Bukkit's {@code Sign} block state.
	 *
	 * <p><b>This writes the field directly (via an access transformer) and deliberately does not go
	 * through {@code updateText}/{@code setText}.</b> Every public way into a sign ends at
	 * {@code markUpdated()}, which notifies the sign's level — and during decoration there is no good
	 * answer for that level:
	 *
	 * <ul>
	 * <li><b>Null NPEs.</b> A block entity reached through a {@code WorldGenRegion} over a
	 * {@code ProtoChunk} has no level — it is built on demand by {@code newBlockEntity} and never
	 * told where it lives — so {@code markUpdated}'s {@code level.sendBlockUpdated(…)} throws.
	 * <li><b>The real {@code ServerLevel} deadlocks</b>, which is worse, because it looks like it
	 * works. With a level present, {@code setChanged()} stops no-opping and runs
	 * {@code Level.blockEntityChanged} → {@code getChunkAt} → {@code ServerChunkCache.getChunk} →
	 * {@code CompletableFuture.join()} — a <em>synchronous chunk request from inside chunk
	 * generation</em>, on a generation worker. The future needs a worker to complete; the worker is
	 * blocked waiting for the future. The server wedges mid-"Preparing spawn area" with every worker
	 * parked at 0% CPU. An earlier pass reached for {@code setLevel} to silence the NPE above and
	 * armed this instead; it took a thread dump of a hung client to see it.
	 * </ul>
	 *
	 * <p>Neither notification is wanted. There are no clients to update during worldgen, and the
	 * block entity is already held by the chunk ({@code WorldGenRegion.getBlockEntity} calls
	 * {@code setBlockEntity} on the one it builds), so it is saved without being marked. Setting the
	 * text <em>is</em> the whole operation — which is why the sign needs no level at all, and is not
	 * given one.
	 */
	private void setSignText(Block block, String... lines) {
		BlockEntity entity = block.getState();
		if (!(entity instanceof SignBlockEntity sign))
			return;

		// Write the same text on both faces so hanging/free-standing signs read from either side (the
		// back is invisible on a wall sign, so mirroring it there costs nothing).
		SignText front = sign.getFrontText();
		SignText back = sign.getText(false);
		for (int i = 0; i < lines.length && i < SignText.LINES; i++) {
			// A null line means a blank one. Bukkit's setLine tolerated null; modern
			// Component.literal(null) throws, and it throws inside chunk generation, which fails
			// the whole chunk. Callers legitimately leave gaps — OdonymProvider's fossil names
			// fill only line 1 of a String[4] and leave the rest null.
			Component line = Component.literal(lines[i] == null ? "" : lines[i]);
			front = front.setMessage(i, line);
			back = back.setMessage(i, line);
		}
		sign.frontText = front;
		// Direct field write, same as frontText above — setText(back, false) routes through
		// markUpdated(), which NPEs on the null decoration-time level (see accesstransformer.cfg).
		sign.backText = back;
	}

}
