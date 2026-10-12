package me.cortex.voxy.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.cortex.voxy.common.Logger;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            try {
                var opt = ConfigManager.CONFIG.getModOptions().stream()
                        .filter(a -> a.configId().equals("voxy"))
                        .findFirst();
                if (opt.isPresent() && !opt.get().pages().isEmpty()) {
                    var page = (OptionPage) opt.get().pages().get(0);
                    return (VideoSettingsScreen) VideoSettingsScreen.createScreen(parent, page);
                }
            } catch (Exception e) {
                Logger.error("Failed to open Voxy config screen from ModMenu", e);
            }
            return null;
        };
    }
}