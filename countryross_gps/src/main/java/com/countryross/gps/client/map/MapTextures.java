package com.countryross.gps.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Texturas do mapa: uma de 512x512 blocos (1 pixel = 1 bloco) por regiao explorada. */
public final class MapTextures {
    public static final int SIZE = MapStore.REGION_TILES * 16; // 512

    public static final class Reg {
        public final ResourceLocation loc;
        private final DynamicTexture tex;
        private final NativeImage img;
        private boolean dirty;

        private Reg(ResourceLocation loc, DynamicTexture tex, NativeImage img) {
            this.loc = loc;
            this.tex = tex;
            this.img = img;
        }
    }

    private static final Map<Long, Reg> regs = new HashMap<>();

    private MapTextures() {}

    private static long key(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    private static String coord(int v) {
        return v < 0 ? "m" + (-v) : String.valueOf(v);
    }

    /** Textura da regiao (cria e preenche na primeira vez); null se a regiao nao tem terreno descoberto. */
    public static Reg get(int rx, int rz) {
        Reg r = regs.get(key(rx, rz));
        if (r != null) return r;
        if (!MapStore.hasRegion(rx, rz)) return null;

        NativeImage img = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, true);
        int cx0 = rx * MapStore.REGION_TILES;
        int cz0 = rz * MapStore.REGION_TILES;
        for (int tz = 0; tz < MapStore.REGION_TILES; tz++) {
            for (int tx = 0; tx < MapStore.REGION_TILES; tx++) {
                MapStore.Tile t = MapStore.tile(cx0 + tx, cz0 + tz);
                if (t == null) continue;
                for (int c = 0; c < 256; c++) {
                    if (!t.seen[c]) continue;
                    int lx = tx * 16 + (c & 15);
                    int lz = tz * 16 + (c >> 4);
                    int wx = (cx0 + tx) * 16 + (c & 15);
                    int wz = (cz0 + tz) * 16 + (c >> 4);
                    img.setPixelRGBA(lx, lz, MapStore.colorAt(wx, wz));
                }
            }
        }
        DynamicTexture tex = new DynamicTexture(img);
        tex.setFilter(false, false);
        ResourceLocation loc = Minecraft.getInstance().getTextureManager()
                .register("countryross_gps_r" + coord(rx) + "_" + coord(rz), tex);
        r = new Reg(loc, tex, img);
        regs.put(key(rx, rz), r);
        return r;
    }

    /** Atualiza o pixel de um bloco e de seus vizinhos (as curvas de nivel dependem deles). */
    public static void refresh(List<int[]> cells) {
        for (int[] c : cells) {
            refreshOne(c[0], c[1]);
            refreshOne(c[0] + 1, c[1]);
            refreshOne(c[0] - 1, c[1]);
            refreshOne(c[0], c[1] + 1);
            refreshOne(c[0], c[1] - 1);
        }
    }

    private static void refreshOne(int x, int z) {
        int rx = Math.floorDiv(x, SIZE);
        int rz = Math.floorDiv(z, SIZE);
        Reg r = regs.get(key(rx, rz));
        if (r == null) return;
        r.img.setPixelRGBA(Math.floorMod(x, SIZE), Math.floorMod(z, SIZE), MapStore.colorAt(x, z));
        r.dirty = true;
    }

    /** Envia para a placa de video o que mudou (chamar uma vez por quadro). */
    public static void uploadDirty() {
        for (Reg r : regs.values()) {
            if (r.dirty) {
                r.tex.upload();
                r.dirty = false;
            }
        }
    }

    public static void clear() {
        for (Reg r : regs.values()) Minecraft.getInstance().getTextureManager().release(r.loc);
        regs.clear();
    }
}
