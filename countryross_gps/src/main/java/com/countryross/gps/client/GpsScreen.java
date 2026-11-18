package com.countryross.gps.client;

import com.countryross.gps.CountryRossGps;
import com.countryross.gps.GpsConfig;
import com.countryross.gps.client.map.MapStore;
import com.countryross.gps.client.map.MapTextures;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do GPS: aparelho que desliza para dentro da tela, inicializa (busca de satelites), e mostra o terreno
 * ja descoberto em verde com curvas de nivel, com a setinha preta do jogador e um radar discreto.
 */
public class GpsScreen extends Screen {
    private static final ResourceLocation ARROW =
            new ResourceLocation(CountryRossGps.MODID, "textures/gui/arrow.png");

    private static final int COL_BODY_OUTER = 0xFF1C1F18;
    private static final int COL_BODY = 0xFF2E3328;
    private static final int COL_BEZEL = 0xFF0A0D09;
    private static final int COL_SCREEN = 0xFF0F2A12;
    private static final int COL_TEXT = 0xFF9BD17A;
    private static final int COL_TEXT_DIM = 0xFF5F8A4A;
    private static final int COL_OK = 0xFF3FA83F;
    private static final int COL_WARN = 0xFFD0603C;

    // linha do tempo da abertura (ms)
    private static final long SLIDE_MS = 260;
    private static final long BOOT_START = 260;
    private static final long BOOT_END = 1500;
    private static final long FADE_MS = 450;
    private static final long CLOSE_MS = 200;

    // zoom (pixels de tela por bloco): 0.125 mostra ~2000 blocos de largura
    private static final double MIN_ZOOM = 0.125;
    private static final double MAX_ZOOM = 8.0;

    private int devX, devW, devH, mapX, mapW, mapH;
    private int baseDevY, baseMapY, devY, mapY;

    private double viewX, viewZ;
    private double zoom;
    private boolean follow = true;

    private final long openTime = System.currentTimeMillis();
    private final boolean bootEnabled;
    private long revealAt;
    private final boolean[] beeped = new boolean[4];
    private boolean openTick, chimed;
    private boolean closing, closeSound2;
    private long closeStart;
    private long lastZoomSound;

    public GpsScreen() {
        super(Component.literal("CountryRoss GPS"));
        this.bootEnabled = GpsConfig.bootAnimation();
        this.revealAt = bootEnabled ? BOOT_END : SLIDE_MS;
        this.zoom = GpsConfig.defaultZoom();
        if (!bootEnabled) {
            chimed = true;
            for (int i = 0; i < beeped.length; i++) beeped[i] = true;
        }
    }

    @Override
    protected void init() {
        devH = Math.min(this.height - 12, 380);
        devW = Math.min(this.width - 12, (int) (devH * 0.78));
        devX = (this.width - devW) / 2;
        baseDevY = (this.height - devH) / 2;
        mapX = devX + 12;
        baseMapY = baseDevY + 26;
        mapW = devW - 24;
        mapH = devH - 26 - 56;
        devY = baseDevY;
        mapY = baseMapY;
        LocalPlayer p = this.minecraft != null ? this.minecraft.player : null;
        if (p != null) {
            viewX = p.getX();
            viewZ = p.getZ();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isBooting() {
        return System.currentTimeMillis() - openTime < revealAt;
    }

    private static float easeOut(float x) {
        float inv = 1f - x;
        return 1f - inv * inv * inv;
    }

    // ------------------------------------------------------------------ coordenadas

    private double sx(double wx) {
        return mapX + mapW / 2.0 + (wx - viewX) * zoom;
    }

    private double sz(double wz) {
        return mapY + mapH / 2.0 + (wz - viewZ) * zoom;
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        LocalPlayer player = this.minecraft != null ? this.minecraft.player : null;
        if (player == null) return;

        long now = System.currentTimeMillis();
        long t = now - openTime;

        // deslizar para dentro / para fora
        int offMax = this.height - baseDevY;
        float slide;
        if (closing) {
            slide = easeOut(Mth.clamp((now - closeStart) / (float) CLOSE_MS, 0f, 1f));
            if (now - closeStart >= CLOSE_MS) {
                this.minecraft.setScreen(null);
                return;
            }
        } else {
            slide = 1f - easeOut(Mth.clamp(t / (float) SLIDE_MS, 0f, 1f));
        }
        int off = (int) (slide * offMax);
        devY = baseDevY + off;
        mapY = baseMapY + off;

        playTimedSounds(now, t);

        boolean booting = t < revealAt;
        float fade = Mth.clamp((t - revealAt) / (float) FADE_MS, 0f, 1f);
        boolean overworld = player.level().dimension() == Level.OVERWORLD;

        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());
        if (follow) {
            viewX = px;
            viewZ = pz;
        }

        this.renderBackground(g);
        drawBody(g, overworld, now);

        g.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, COL_SCREEN);
        if (booting) {
            drawBoot(g, t, now);
        } else if (!overworld) {
            drawNoSignal(g, now);
        } else {
            MapTextures.uploadDirty();
            if (GpsConfig.showGrid()) drawGrid(g);
            drawTerrain(g);
            double ax = Mth.clamp(sx(px), mapX + 8, mapX + mapW - 8);
            double ay = Mth.clamp(sz(pz), mapY + 8, mapY + mapH - 8);
            drawRadar(g, ax, ay, now);
            drawArrow(g, ax, ay, Mth.lerp(partialTick, player.yRotO, player.getYRot()));
        }
        if (!booting && fade < 1f) {
            int a = (int) ((1f - fade) * 255f);
            g.fill(mapX, mapY, mapX + mapW, mapY + mapH, a << 24);
        }
        g.disableScissor();

        if (GpsConfig.scanlines()) {
            for (int yy = mapY; yy < mapY + mapH; yy += 2) {
                g.fill(mapX, yy, mapX + mapW, yy + 1, 0x14000000);
            }
        }

        if (bootEnabled && !booting && t - revealAt < 900 && (now / 150) % 2 == 0) {
            g.drawCenteredString(this.font, Component.translatable("gui.countryross_gps.signal_acquired"), mapX + mapW / 2, mapY + mapH - 16, COL_OK);
        }

        drawReadout(g, player, overworld, now);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void playTimedSounds(long now, long t) {
        if (closing) {
            if (!closeSound2 && now - closeStart > 110) {
                closeSound2 = true;
                GpsSounds.play(GpsSounds.BEEP, 0.6f);
            }
            return;
        }
        if (!openTick) {
            openTick = true;
            GpsSounds.play(GpsSounds.TICK, 1.0f);
        }
        float[] pitches = {0.8f, 0.95f, 1.1f, 1.3f};
        for (int i = 0; i < beeped.length; i++) {
            if (!beeped[i] && t >= BOOT_START + i * 250L && t < revealAt) {
                beeped[i] = true;
                GpsSounds.play(GpsSounds.BEEP, pitches[i]);
            }
        }
        if (!chimed && t >= revealAt) {
            chimed = true;
            GpsSounds.play(GpsSounds.CHIME, 1.4f);
        }
    }

    private void skipBoot() {
        long t = System.currentTimeMillis() - openTime;
        if (t < revealAt) revealAt = Math.max(t, SLIDE_MS);
        for (int i = 0; i < beeped.length; i++) beeped[i] = true;
    }

    private void drawBody(GuiGraphics g, boolean signal, long now) {
        g.fill(devX, devY, devX + devW, devY + devH, COL_BODY_OUTER);
        g.fill(devX + 2, devY + 2, devX + devW - 2, devY + devH - 2, COL_BODY);
        g.fill(mapX - 4, mapY - 4, mapX + mapW + 4, mapY + mapH + 4, COL_BEZEL);

        g.drawString(this.font, "COUNTRYROSS GPS", devX + 12, devY + 9, COL_TEXT, false);

        boolean blink = (now / 500) % 2 == 0;
        int led = signal ? 0xFF58E04F : (blink ? 0xFF9A3A22 : 0xFF3A1A12);
        g.fill(devX + devW - 20, devY + 9, devX + devW - 12, devY + 17, COL_BODY_OUTER);
        g.fill(devX + devW - 19, devY + 10, devX + devW - 13, devY + 16, led);

        int by = devY + devH - 16;
        for (int i = 0; i < 4; i++) {
            int bx = devX + devW / 2 - 44 + i * 24;
            g.fill(bx, by, bx + 16, by + 8, COL_BODY_OUTER);
            g.fill(bx + 1, by + 1, bx + 15, by + 7, 0xFF3D4435);
        }
    }

    private void drawBoot(GuiGraphics g, long t, long now) {
        long bt = t - BOOT_START;
        int x = mapX + 8;
        int y = mapY + 10;
        if (bt > 0) g.drawString(this.font, "COUNTRYROSS GPS", x, y, COL_TEXT, false);
        if (bt > 250) g.drawString(this.font, Component.translatable("gui.countryross_gps.boot.system"), x, y + 12, COL_TEXT_DIM, false);
        if (bt > 500) g.drawString(this.font, Component.translatable("gui.countryross_gps.boot.memory"), x, y + 24, COL_TEXT_DIM, false);
        if (bt > 750) {
            String dots = ".".repeat((int) ((bt / 250) % 4));
            g.drawString(this.font, Component.translatable("gui.countryross_gps.boot.searching").append(Component.literal(dots)), x, y + 40, COL_TEXT, false);
            float prog = Mth.clamp((bt - 750) / (float) (BOOT_END - BOOT_START - 750), 0f, 1f);
            int bw = mapW - 16;
            g.fill(x, y + 54, x + bw, y + 62, 0xFF1C3A1E);
            g.fill(x + 1, y + 55, x + 1 + (int) ((bw - 2) * prog), y + 61, COL_OK);
            g.drawString(this.font, Component.translatable("gui.countryross_gps.boot.sat", (int) (prog * 8)), x, y + 66, COL_TEXT_DIM, false);
        }
        if ((now / 400) % 2 == 0) g.fill(x, y + 84, x + 6, y + 86, COL_TEXT);
    }

    private void drawNoSignal(GuiGraphics g, long now) {
        java.util.Random rnd = new java.util.Random(now / 90);
        int cell = 6;
        for (int yy = mapY; yy < mapY + mapH; yy += cell) {
            for (int xx = mapX; xx < mapX + mapW; xx += cell) {
                if (rnd.nextInt(100) < 38) {
                    int a = 0x20 + rnd.nextInt(0x50);
                    g.fill(xx, yy, Math.min(xx + cell, mapX + mapW), Math.min(yy + cell, mapY + mapH),
                            (a << 24) | 0x3FA83F);
                }
            }
        }
        int cy = mapY + mapH / 2;
        g.fill(mapX + 10, cy - 18, mapX + mapW - 10, cy + 18, 0xCC0A180B);
        g.drawCenteredString(this.font, Component.translatable("gui.countryross_gps.no_signal"), mapX + mapW / 2, cy - 12,
                (now / 400) % 2 == 0 ? COL_WARN : COL_OK);
        g.drawCenteredString(this.font, Component.translatable("gui.countryross_gps.out_of_range"), mapX + mapW / 2, cy + 2, COL_TEXT_DIM);
    }

    private void drawGrid(GuiGraphics g) {
        int step = zoom >= 1.0 ? 100 : (zoom >= 0.3 ? 500 : 1000);
        double left = viewX - (mapW / 2.0) / zoom;
        double right = viewX + (mapW / 2.0) / zoom;
        double top = viewZ - (mapH / 2.0) / zoom;
        double bottom = viewZ + (mapH / 2.0) / zoom;
        for (long x = (long) Math.floor(left / step) * step; x <= right; x += step) {
            int s = (int) sx(x);
            g.fill(s, mapY, s + 1, mapY + mapH, 0x303F8F3F);
        }
        for (long z = (long) Math.floor(top / step) * step; z <= bottom; z += step) {
            int s = (int) sz(z);
            g.fill(mapX, s, mapX + mapW, s + 1, 0x303F8F3F);
        }
    }

    private void drawTerrain(GuiGraphics g) {
        int rs = MapTextures.SIZE;
        double left = viewX - (mapW / 2.0) / zoom;
        double right = viewX + (mapW / 2.0) / zoom;
        double top = viewZ - (mapH / 2.0) / zoom;
        double bottom = viewZ + (mapH / 2.0) / zoom;
        int rx0 = Math.floorDiv((int) Math.floor(left), rs);
        int rx1 = Math.floorDiv((int) Math.floor(right), rs);
        int rz0 = Math.floorDiv((int) Math.floor(top), rs);
        int rz1 = Math.floorDiv((int) Math.floor(bottom), rs);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                MapTextures.Reg reg = MapTextures.get(rx, rz);
                if (reg == null) continue;
                PoseStack pose = g.pose();
                pose.pushPose();
                pose.translate((float) sx((double) rx * rs), (float) sz((double) rz * rs), 0f);
                pose.scale((float) zoom, (float) zoom, 1f);
                g.blit(reg.loc, 0, 0, rs, rs, 0f, 0f, rs, rs, rs, rs);
                pose.popPose();
            }
        }
        RenderSystem.disableBlend();
    }

    /** Anel pontilhado do alcance, varredura de radar e pulso ao redor da setinha. */
    private void drawRadar(GuiGraphics g, double ax, double ay, long now) {
        double range = GpsConfig.revealRadius() * zoom;
        for (int a = 0; a < 360; a += 6) {
            double rad = Math.toRadians(a);
            int x = (int) (ax + Math.cos(rad) * range);
            int y = (int) (ay + Math.sin(rad) * range);
            g.fill(x, y, x + 1, y + 1, 0x663FA83F);
        }
        if (!GpsConfig.radarEffect()) return;

        double base = (now % 3200) / 3200.0 * 360.0;
        for (int i = 0; i < 6; i++) {
            double rad = Math.toRadians(base - i * 5.0);
            int color = ((0x70 - i * 0x12) << 24) | 0x7CFF6A;
            for (double d = 8; d < range; d += 2) {
                int x = (int) (ax + Math.cos(rad) * d);
                int y = (int) (ay + Math.sin(rad) * d);
                g.fill(x, y, x + 1, y + 1, color);
            }
        }

        double p = (now % 2000) / 2000.0;
        double pr = 6 + p * 16;
        int pc = ((int) ((1.0 - p) * 0xA0) << 24) | 0x7CFF6A;
        for (int a = 0; a < 360; a += 15) {
            double rad = Math.toRadians(a);
            int x = (int) (ax + Math.cos(rad) * pr);
            int y = (int) (ay + Math.sin(rad) * pr);
            g.fill(x, y, x + 1, y + 1, pc);
        }
    }

    private void drawArrow(GuiGraphics g, double ax, double ay, float yaw) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(ax, ay, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(yaw + 180f));
        g.blit(ARROW, -8, -8, 0, 0, 16, 16, 16, 16);
        pose.popPose();
        RenderSystem.disableBlend();
    }

    private void drawReadout(GuiGraphics g, LocalPlayer p, boolean overworld, long now) {
        int y0 = mapY + mapH + 8;
        if (GpsConfig.showCoordinates()) {
            g.drawString(this.font,
                    "X " + (int) Math.floor(p.getX()) + "   Z " + (int) Math.floor(p.getZ())
                            + "   ALT " + (int) Math.floor(p.getY()),
                    mapX, y0, COL_TEXT, false);
        }
        if (overworld) {
            g.drawString(this.font, Component.translatable("gui.countryross_gps.signal_ok", MapStore.count()), mapX, y0 + 12, COL_OK, false);
        } else if ((now / 500) % 2 == 0) {
            g.drawString(this.font, Component.translatable("gui.countryross_gps.no_signal"), mapX, y0 + 12, COL_WARN, false);
        }
        g.drawString(this.font, Component.translatable("gui.countryross_gps.hint"), mapX, y0 + 24, COL_TEXT_DIM, false);
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        closeStart = System.currentTimeMillis();
        GpsSounds.play(GpsSounds.BEEP, 0.9f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        if (isBooting()) {
            skipBoot();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (closing || isBooting()) return true;
        if (button == 0) {
            viewX -= dragX / zoom;
            viewZ -= dragY / zoom;
            follow = false;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (closing || isBooting()) return true;
        zoom = Mth.clamp(zoom * (delta > 0 ? 1.2 : 1.0 / 1.2), MIN_ZOOM, MAX_ZOOM);
        long now = System.currentTimeMillis();
        if (now - lastZoomSound > 90) {
            lastZoomSound = now;
            GpsSounds.play(GpsSounds.TICK, delta > 0 ? 1.7f : 1.2f);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;
        if (keyCode == GLFW.GLFW_KEY_R && !isBooting()) {
            follow = true;
            GpsSounds.play(GpsSounds.BEEP, 1.2f);
            return true;
        }
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && isBooting()) {
            skipBoot();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
