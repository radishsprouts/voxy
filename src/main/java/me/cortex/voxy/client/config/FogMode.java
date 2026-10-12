package me.cortex.voxy.client.config;

import net.minecraft.network.chat.Component;

public enum FogMode {
    OFF("Off", 0.0f, 0.0f),
    DEFAULT("Voxy Default (Thick)", 1.0f, 0.0f),
    SUBTLE("Subtle (Recommended)", 0.35f, 0.0f),
    EXTENDED("Extended Fade", 0.60f, 0.25f),
    ATMOSPHERIC("Atmospheric Haze", 0.20f, 0.0f);

    public final String displayName;
    public final float maxOpacity;
    public final float startRatio;

    FogMode(String displayName, float maxOpacity, float startRatio) {
        this.displayName = displayName;
        this.maxOpacity = maxOpacity;
        this.startRatio = startRatio;
    }

    public Component getComponent() {
        return Component.literal(this.displayName);
    }
}
