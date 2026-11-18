package com.countryross.gps.client;

import com.countryross.gps.GpsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Efeitos sonoros do aparelho (usam os sons de nota digital do proprio Minecraft). */
public final class GpsSounds {
    public static final String BEEP = "minecraft:block.note_block.bit";
    public static final String CHIME = "minecraft:block.note_block.chime";
    public static final String TICK = "minecraft:block.note_block.hat";
    public static final String PLING = "minecraft:block.note_block.pling";

    private GpsSounds() {}

    public static void play(String id, float pitch) {
        if (!GpsConfig.sounds()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(id));
        if (event == null) return;
        mc.player.playSound(event, (float) GpsConfig.soundVolume(), pitch);
    }
}
