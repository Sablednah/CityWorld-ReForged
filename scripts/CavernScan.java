// Where are the large caverns for a seed? Runs the mod's own cavern noise (ShapeProvider_Normal.inCavern,
// same constants, same vendored SimplexNoiseGenerator) over an area and lists the rooms, biggest first,
// each with a /tp point on its floor. Reads no chunks, so anything the world put there afterwards (a
// mine, a sewer, a lava lake, an Alex's cave) can differ. Keep the constants in step with inCavern.
//
//   JAVA=tools/jdk21/bin; $JAVA/javac -cp build/classes/java/main -d /tmp/cs scripts/CavernScan.java
//   $JAVA/java -cp build/classes/java/main:/tmp/cs CavernScan <seed> <x> <z> <radius>
//
// The seed and the player's position are in the save's level.dat (Data/WorldGenSettings/seed,
// Data/Player/Pos). First used 2026-09-25 on the owner's 1.20.1 apocalypse world: one room 82 blocks
// from him, y -17..15, which he then confirmed in game.
import me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator;
import java.util.*;
public class CavernScan {
  // ---- clusters ----
  static double regionThr = 0.60, roomThr = 0.40;
  public static void main(String[] a) {
    long seed = Long.parseLong(a[0]); int cx = Integer.parseInt(a[1]), cz = Integer.parseInt(a[2]), r = Integer.parseInt(a[3]);
    SimplexNoiseGenerator region = new SimplexNoiseGenerator(seed + 909), room = new SimplexNoiseGenerator(seed + 919), tex = new SimplexNoiseGenerator(seed + 929);
    int step = 4; int w = (2 * r) / step + 1;
    int[][] count = new int[w][w]; int[][] lo = new int[w][w]; int[][] hi = new int[w][w];
    for (int i = 0; i < w; i++) for (int j = 0; j < w; j++) {
      int x = cx - r + i * step, z = cz - r + j * step;
      double reg = region.noise(x / 400.0, z / 400.0); double t = (reg - regionThr) / 0.12; double field = t <= 0 ? 0 : t >= 1 ? 1 : t * t * (3 - 2 * t);
      lo[i][j] = 999; hi[i][j] = -999;
      if (field <= 0) continue;
      for (int y = -52; y <= 26; y += 2) {
        double edge = Math.min(y + 52, 26 - y) / 14.0; double env = edge >= 1 ? 1 : edge * edge * (3 - 2 * edge);
        double rm = room.noise(x / 60.0, y / 30.0, z / 60.0); double tx = tex.noise(x / 11.0, y / 11.0, z / 11.0) * 0.22;
        double shelf = (Math.floorMod(y, 6) / 6.0) * 0.14;
        if ((rm + tx) * env * field > roomThr + shelf) { count[i][j]++; lo[i][j] = Math.min(lo[i][j], y); hi[i][j] = Math.max(hi[i][j], y); }
      }
    }
    // flood-fill clusters of columns with >= 4 carved samples (>= 8 blocks of air)
    boolean[][] seen = new boolean[w][w]; List<int[]> out = new ArrayList<>();
    for (int i = 0; i < w; i++) for (int j = 0; j < w; j++) {
      if (seen[i][j] || count[i][j] < 4) continue;
      Deque<int[]> q = new ArrayDeque<>(); q.add(new int[]{i, j}); seen[i][j] = true;
      int n = 0; long sx = 0, sz = 0; int minY = 999, maxY = -999, tallest = 0;
      while (!q.isEmpty()) { int[] c = q.poll(); n++; sx += c[0]; sz += c[1]; minY = Math.min(minY, lo[c[0]][c[1]]); maxY = Math.max(maxY, hi[c[0]][c[1]]); tallest = Math.max(tallest, count[c[0]][c[1]] * 2);
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) { int ni = c[0] + d[0], nj = c[1] + d[1]; if (ni < 0 || nj < 0 || ni >= w || nj >= w || seen[ni][nj] || count[ni][nj] < 4) continue; seen[ni][nj] = true; q.add(new int[]{ni, nj}); } }
      out.add(new int[]{n, (int)(cx - r + (sx / n) * step), (int)(cz - r + (sz / n) * step), minY, maxY, tallest});
    }
    out.sort((p, q) -> q[0] - p[0]);
    System.out.printf("scanned %d x %d blocks around (%d, %d); %d caverns (columns of >=8 blocks of air, clustered)%n", 2*r, 2*r, cx, cz, out.size());
    for (int k = 0; k < Math.min(12, out.size()); k++) { int[] o = out.get(k); System.out.printf("  ~%5d blocks^2 footprint  centre x=%d z=%d  tallest column %d  (%d blocks from you)  %s%n", o[0]*step*step, o[1], o[2], o[5], (int)Math.hypot(o[1]-cx, o[2]-cz), spot(region, room, tex, o[1], o[2])); }
  }

  /** A /tp onto the floor of the tallest air column within 60 blocks of a cavern's centre. */
  static String spot(SimplexNoiseGenerator region, SimplexNoiseGenerator room, SimplexNoiseGenerator tex, int cx, int cz) {
    int bestRun = 0, bx = 0, bz = 0, by0 = 0, by1 = 0;
    for (int x = cx - 60; x <= cx + 60; x += 2) for (int z = cz - 60; z <= cz + 60; z += 2) {
      double reg = region.noise(x / 400.0, z / 400.0); double t = (reg - regionThr) / 0.12; double field = t <= 0 ? 0 : t >= 1 ? 1 : t * t * (3 - 2 * t);
      if (field <= 0) continue;
      int run = 0, y0 = 0;
      for (int y = -52; y <= 27; y++) {
        double edge = Math.min(y + 52, 26 - y) / 14.0; double env = edge >= 1 ? 1 : edge * edge * (3 - 2 * edge);
        double rm = room.noise(x / 60.0, y / 30.0, z / 60.0); double tx = tex.noise(x / 11.0, y / 11.0, z / 11.0) * 0.22;
        double shelf = (Math.floorMod(y, 6) / 6.0) * 0.14;
        boolean air = y <= 26 && (rm + tx) * env * field > roomThr + shelf;
        if (air) { if (run == 0) y0 = y; run++; if (run > bestRun) { bestRun = run; bx = x; bz = z; by0 = y0; by1 = y; } } else run = 0;
      }
    }
    return String.format("/tp %d %d %d (air y %d..%d)", bx, by0 + 1, bz, by0, by1);
  }
}
