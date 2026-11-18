package com.countryross.gps.client.map;

import com.countryross.gps.GpsConfig;
import com.countryross.gps.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Descobre o terreno ao redor do jogador enquanto ele anda (so com o GPS no inventario, se configurado),
 * lendo os chunks que o proprio cliente ja carregou.
 */
public final class MapTracker {
    private static int tickCounter = 0;
    private static int lastX = Integer.MIN_VALUE;
    private static int lastZ = Integer.MIN_VALUE;

    private MapTracker() {}

    // ------------------------------------------------------------------ ciclo de vida

    public static void onJoin() {
        Minecraft mc = Minecraft.getInstance();
        MapTextures.clear();
        MapStore.load(mapFile(worldKey(mc)));
        tickCounter = 0;
        lastX = Integer.MIN_VALUE;
        lastZ = Integer.MIN_VALUE;
    }

    public static void onLeave() {
        MapStore.saveNow();
        MapStore.clear();
        MapTextures.clear();
    }

    private static String worldKey(Minecraft mc) {
        String raw;
        if (mc.getSingleplayerServer() != null) {
            raw = "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        } else if (mc.getCurrentServer() != null) {
            raw = "mp_" + mc.getCurrentServer().ip;
        } else {
            raw = "unknown";
        }
        return raw.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Path mapFile(String key) {
        return FMLPaths.GAMEDIR.get().resolve("countryross_gps").resolve("maps").resolve(key + ".bin");
    }

    // ------------------------------------------------------------------ a cada tick

    public static boolean hasGps(LocalPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(ModItems.GPS.get())) return true;
        }
        return false;
    }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;

        tickCounter++;
        if (tickCounter % 600 == 0) MapStore.saveAsync(); // a cada 30 s

        if (tickCounter % 5 != 0) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (GpsConfig.requireItem() && !hasGps(p)) return;

        int px = Mth.floor(p.getX());
        int pz = Mth.floor(p.getZ());
        // so escaneia se andou um pouco (ou de vez em quando)
        if (Math.abs(px - lastX) < 2 && Math.abs(pz - lastZ) < 2 && tickCounter % 40 != 0) return;
        lastX = px;
        lastZ = pz;

        if (GpsConfig.surfaceOnly()) {
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, px, pz);
            if (p.getY() < surface - 12) return;
        }
        scan(level, px, pz, GpsConfig.revealRadius());
    }

    private static void scan(ClientLevel level, int cx, int cz, int radius) {
        int r2 = radius * radius;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<int[]> fresh = new ArrayList<>();
        int[] out = new int[2];
        for (int dz = -radius; dz <= radius; dz++) {
            int z = cz + dz;
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz > r2) continue;
                int x = cx + dx;
                if (MapStore.isSeen(x, z)) continue;
                ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (sample(level, chunk, x, z, top, pos, out)) {
                    MapStore.set(x, z, out[0], out[1]);
                    fresh.add(new int[]{x, z});
                }
            }
        }
        if (!fresh.isEmpty()) MapTextures.refresh(fresh);
    }

    /** Desce a coluna ate achar o "chao" (ignora ar, folhas e plantas). out[0] = altura, out[1] = tipo. */
    private static boolean sample(ClientLevel level, ChunkAccess chunk, int x, int z, int top,
                                  BlockPos.MutableBlockPos pos, int[] out) {
        int minY = level.getMinBuildHeight();
        int y = Math.min(top, level.getMaxBuildHeight() - 1);
        for (; y >= minY; y--) {
            pos.set(x, y, z);
            BlockState s = chunk.getBlockState(pos);
            if (s.isAir() || s.is(BlockTags.LEAVES)) continue;
            FluidState f = s.getFluidState();
            if (f.is(FluidTags.WATER)) {
                out[0] = y;
                out[1] = 1;
                return true;
            }
            if (!f.isEmpty()) { // lava etc.
                out[0] = y;
                out[1] = 0;
                return true;
            }
            if (s.getCollisionShape(level, pos).isEmpty()) continue; // plantas, tochas, trilhos...
            out[0] = y;
            out[1] = isNatural(s) ? 0 : 2;
            return true;
        }
        return false;
    }

    /** Blocos "naturais" viram terreno; o resto (tabuas, tijolos, concreto...) vira construcao/estrada. */
    private static boolean isNatural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.LOGS) || s.is(BlockTags.FLOWERS)
                || s.is(BlockTags.ICE) || s.is(BlockTags.SNOW) || s.is(BlockTags.STONE_ORE_REPLACEABLES)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SNOW_BLOCK)
                || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CALCITE)
                || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.BEDROCK);
    }
}
