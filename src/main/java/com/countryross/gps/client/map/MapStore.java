package com.countryross.gps.client.map;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Terreno ja descoberto pelo jogador (por mundo/servidor), guardado em "tiles" de 16x16 blocos.
 * Cada bloco descoberto guarda altura e tipo (0 = terreno, 1 = agua, 2 = construcao/estrada).
 */
public final class MapStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAGIC = 0x47505332;
    public static final int REGION_TILES = 32; // 32x32 tiles = 512x512 blocos por textura

    public static final class Tile {
        public final short[] h = new short[256];
        public final byte[] k = new byte[256];
        public final boolean[] seen = new boolean[256];
    }

    // cores (R, G, B) do visual verde analogico
    private static final int[] WATER = {28, 92, 70};
    private static final int[] LAND = {104, 165, 78};
    private static final int[] BUILT = {205, 232, 150};
    private static final int[] CONTOUR = {52, 112, 52};
    private static final int[] CONTOUR_INDEX = {30, 78, 36};

    public static final int CONTOUR_INTERVAL = 4;

    private static final Map<Long, Tile> tiles = new HashMap<>();
    private static final Set<Long> regionsWithData = new HashSet<>();
    private static long seenCount = 0;
    private static boolean dirty = false;
    private static Path file;

    private MapStore() {}

    private static long key(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------ acesso

    public static boolean isSeen(int x, int z) {
        Tile t = tiles.get(key(x >> 4, z >> 4));
        return t != null && t.seen[((z & 15) << 4) | (x & 15)];
    }

    public static void set(int x, int z, int height, int kind) {
        int cx = x >> 4, cz = z >> 4;
        Tile t = tiles.get(key(cx, cz));
        if (t == null) {
            t = new Tile();
            tiles.put(key(cx, cz), t);
            regionsWithData.add(key(Math.floorDiv(cx, REGION_TILES), Math.floorDiv(cz, REGION_TILES)));
        }
        int i = ((z & 15) << 4) | (x & 15);
        if (!t.seen[i]) seenCount++;
        t.h[i] = (short) height;
        t.k[i] = (byte) kind;
        t.seen[i] = true;
        dirty = true;
    }

    public static Tile tile(int cx, int cz) {
        return tiles.get(key(cx, cz));
    }

    public static boolean hasRegion(int rx, int rz) {
        return regionsWithData.contains(key(rx, rz));
    }

    public static long count() {
        return seenCount;
    }

    private static int kindAt(int x, int z) {
        Tile t = tiles.get(key(x >> 4, z >> 4));
        if (t == null) return -1;
        int i = ((z & 15) << 4) | (x & 15);
        return t.seen[i] ? t.k[i] : -1;
    }

    private static int heightAt(int x, int z) {
        Tile t = tiles.get(key(x >> 4, z >> 4));
        return t == null ? 0 : t.h[((z & 15) << 4) | (x & 15)];
    }

    // ------------------------------------------------------------------ cor de cada bloco

    private static int abgr(int r, int g, int b) {
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private static int abgr(int[] c) {
        return abgr(c[0], c[1], c[2]);
    }

    /** Cor (ABGR, para NativeImage) do bloco (x,z); 0 = ainda nao descoberto (transparente). */
    public static int colorAt(int x, int z) {
        int kind = kindAt(x, z);
        if (kind < 0) return 0;
        if (kind == 1) return abgr(WATER);

        int h = heightAt(x, z);

        // sombreamento de relevo (luz vindo do noroeste)
        int hw = (kindAt(x - 1, z) == 0 || kindAt(x - 1, z) == 2) ? heightAt(x - 1, z) : h;
        int hn = (kindAt(x, z - 1) == 0 || kindAt(x, z - 1) == 2) ? heightAt(x, z - 1) : h;
        int slope = (hw + hn) - 2 * h;
        int shade = Math.max(-24, Math.min(24, -slope * 5));

        if (kind == 2) {
            return abgr(BUILT[0] + shade / 2, BUILT[1] + shade / 2, BUILT[2] + shade / 2);
        }

        // curvas de nivel (linha fina do lado mais alto) e linha de costa
        int band = Math.floorDiv(h, CONTOUR_INTERVAL);
        boolean line = false;
        boolean index = false;
        for (int n = 0; n < 4; n++) {
            int nx = x + (n == 0 ? 1 : n == 2 ? -1 : 0);
            int nz = z + (n == 1 ? 1 : n == 3 ? -1 : 0);
            int nk = kindAt(nx, nz);
            if (nk == 1) {
                line = true;
            } else if (nk == 0) {
                int b2 = Math.floorDiv(heightAt(nx, nz), CONTOUR_INTERVAL);
                if (b2 < band) {
                    line = true;
                    if (Math.floorMod(band, 5) == 0) index = true;
                }
            }
        }
        if (line) return abgr(index ? CONTOUR_INDEX : CONTOUR);

        return abgr(LAND[0] + (int) (shade * 0.7), LAND[1] + shade, LAND[2] + (int) (shade * 0.6));
    }

    // ------------------------------------------------------------------ salvar / carregar

    public static void load(Path f) {
        clear();
        file = f;
        if (!Files.exists(f)) return;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new InflaterInputStream(Files.newInputStream(f))))) {
            if (in.readInt() != MAGIC) return;
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                int cx = in.readInt();
                int cz = in.readInt();
                Tile t = new Tile();
                byte[] mask = new byte[32];
                in.readFully(mask);
                for (int c = 0; c < 256; c++) {
                    t.seen[c] = (mask[c >> 3] & (1 << (c & 7))) != 0;
                    if (t.seen[c]) seenCount++;
                }
                for (int c = 0; c < 256; c++) t.h[c] = in.readShort();
                in.readFully(t.k);
                tiles.put(key(cx, cz), t);
                regionsWithData.add(key(Math.floorDiv(cx, REGION_TILES), Math.floorDiv(cz, REGION_TILES)));
            }
            LOGGER.info("[GPS] Mapa carregado: {} blocos descobertos.", seenCount);
        } catch (Exception e) {
            LOGGER.error("[GPS] Nao consegui carregar o mapa {}: {}", f, e.toString());
            tiles.clear();
            regionsWithData.clear();
            seenCount = 0;
        }
    }

    private static byte[] serialize() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(bos)))) {
            out.writeInt(MAGIC);
            out.writeInt(tiles.size());
            for (Map.Entry<Long, Tile> e : tiles.entrySet()) {
                long key = e.getKey();
                out.writeInt((int) (key >> 32));
                out.writeInt((int) key);
                Tile t = e.getValue();
                byte[] mask = new byte[32];
                for (int c = 0; c < 256; c++) if (t.seen[c]) mask[c >> 3] |= (byte) (1 << (c & 7));
                out.write(mask);
                for (int c = 0; c < 256; c++) out.writeShort(t.h[c]);
                out.write(t.k);
            }
        }
        return bos.toByteArray();
    }

    /** Salva em segundo plano (so se algo mudou). */
    public static void saveAsync() {
        if (!dirty || file == null) return;
        final byte[] data;
        try {
            data = serialize();
        } catch (IOException e) {
            LOGGER.error("[GPS] Erro preparando o mapa para salvar: {}", e.toString());
            return;
        }
        dirty = false;
        final Path target = file;
        CompletableFuture.runAsync(() -> write(target, data));
    }

    /** Salva agora (ao sair do mundo). */
    public static void saveNow() {
        if (!dirty || file == null) return;
        try {
            write(file, serialize());
            dirty = false;
        } catch (IOException e) {
            LOGGER.error("[GPS] Erro salvando o mapa: {}", e.toString());
        }
    }

    private static void write(Path target, byte[] data) {
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("[GPS] Nao consegui salvar o mapa {}: {}", target, e.toString());
        }
    }

    public static void clear() {
        tiles.clear();
        regionsWithData.clear();
        seenCount = 0;
        dirty = false;
        file = null;
    }
}
