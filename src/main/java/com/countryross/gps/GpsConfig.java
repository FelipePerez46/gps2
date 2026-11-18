package com.countryross.gps;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Config files (Forge TOML):
 *  - SERVER (applies to everyone, synced to players): world/serverconfig/countryross_gps-server.toml
 *  - CLIENT (each player's preferences): config/countryross_gps-client.toml
 */
public final class GpsConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final int DEFAULT_REVEAL_RADIUS = 64;

    private static final ForgeConfigSpec.IntValue REVEAL_RADIUS;
    private static final ForgeConfigSpec.BooleanValue SURFACE_ONLY;
    private static final ForgeConfigSpec.BooleanValue REQUIRE_ITEM;

    private static final ForgeConfigSpec.BooleanValue BOOT_ANIMATION;
    private static final ForgeConfigSpec.BooleanValue RADAR_EFFECT;
    private static final ForgeConfigSpec.BooleanValue SOUNDS;
    private static final ForgeConfigSpec.DoubleValue SOUND_VOLUME;
    private static final ForgeConfigSpec.DoubleValue DEFAULT_ZOOM;
    private static final ForgeConfigSpec.BooleanValue SHOW_GRID;
    private static final ForgeConfigSpec.BooleanValue SCANLINES;
    private static final ForgeConfigSpec.BooleanValue SHOW_COORDINATES;

    static {
        ForgeConfigSpec.Builder s = new ForgeConfigSpec.Builder();
        s.comment("CountryRoss GPS map (applies to all players on the server)",
                "Mapa do CountryRoss GPS (vale para todos os jogadores do servidor)").push("map");
        REVEAL_RADIUS = s
                .comment("Radius in blocks around the player that the GPS reveals while walking. Smaller = less terrain visible. Default: 64",
                        "Raio, em blocos, ao redor do jogador que o GPS revela enquanto ele anda. Menor = menos terreno visivel. Padrao: 64")
                .defineInRange("revealRadius", DEFAULT_REVEAL_RADIUS, 8, 128);
        SURFACE_ONLY = s
                .comment("true = only maps while the player is near the surface (does not reveal the surface from caves/mines).",
                        "true = so mapeia perto da superficie (nao revela a superficie enquanto o jogador esta em cavernas/minas).")
                .define("surfaceOnly", true);
        REQUIRE_ITEM = s
                .comment("true = terrain is only mapped while the player carries the CountryRoss GPS item.",
                        "true = o terreno so e mapeado enquanto o jogador carrega o item CountryRoss GPS.")
                .define("requireItem", true);
        s.pop();
        SERVER_SPEC = s.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.comment("CountryRoss GPS visual and sound preferences (only affect you)",
                "Preferencias visuais e sonoras do CountryRoss GPS (so afetam voce)").push("gps");
        BOOT_ANIMATION = c.comment("Device boot animation when opening (click or key skips it).",
                        "Animacao de inicializacao do aparelho ao abrir (clique ou tecla pula).")
                .define("bootAnimation", true);
        RADAR_EFFECT = c.comment("Radar sweep and pulse around the arrow.",
                        "Varredura de radar e pulso ao redor da setinha.")
                .define("radarEffect", true);
        SOUNDS = c.comment("Device sound effects.", "Efeitos sonoros do aparelho.").define("sounds", true);
        SOUND_VOLUME = c.comment("Sound effects volume (0.0 to 1.0).", "Volume dos efeitos sonoros (0.0 a 1.0).")
                .defineInRange("soundVolume", 0.6D, 0.0D, 1.0D);
        DEFAULT_ZOOM = c.comment("Zoom when opening the map (screen pixels per block). Lower = see more of the map.",
                        "Zoom ao abrir o mapa (pixels de tela por bloco). Menor = ve mais do mapa.")
                .defineInRange("defaultZoom", 1.5D, 0.125D, 8.0D);
        SHOW_GRID = c.comment("Coordinate grid on the map.", "Grade de coordenadas no mapa.").define("showGrid", true);
        SCANLINES = c.comment("Analog screen scanlines.", "Linhas de varredura de tela analogica.")
                .define("scanlines", true);
        SHOW_COORDINATES = c.comment("Show X/Z/ALT at the bottom of the device.",
                        "Mostrar X/Z/ALT no rodape do aparelho.")
                .define("showCoordinates", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private GpsConfig() {}

    public static int revealRadius() {
        try {
            return REVEAL_RADIUS.get();
        } catch (Exception e) {
            return DEFAULT_REVEAL_RADIUS;
        }
    }

    public static boolean surfaceOnly() { return get(SURFACE_ONLY, true); }
    public static boolean requireItem() { return get(REQUIRE_ITEM, true); }
    public static boolean bootAnimation() { return get(BOOT_ANIMATION, true); }
    public static boolean radarEffect() { return get(RADAR_EFFECT, true); }
    public static boolean sounds() { return get(SOUNDS, true); }
    public static boolean showGrid() { return get(SHOW_GRID, true); }
    public static boolean scanlines() { return get(SCANLINES, true); }
    public static boolean showCoordinates() { return get(SHOW_COORDINATES, true); }

    public static double soundVolume() {
        try {
            return SOUND_VOLUME.get();
        } catch (Exception e) {
            return 0.6D;
        }
    }

    public static double defaultZoom() {
        try {
            return DEFAULT_ZOOM.get();
        } catch (Exception e) {
            return 1.5D;
        }
    }

    private static boolean get(ForgeConfigSpec.BooleanValue v, boolean fallback) {
        try {
            return v.get();
        } catch (Exception e) {
            return fallback;
        }
    }
}
