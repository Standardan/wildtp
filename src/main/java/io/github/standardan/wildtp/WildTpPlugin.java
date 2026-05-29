package io.github.standardan.wildtp;

import io.github.standardan.wildtp.command.WildCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class WildTpPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginCommand command = Objects.requireNonNull(getCommand("wild"), "wild missing from plugin.yml");
        command.setExecutor(new WildCommand(this));
        getLogger().info("WildTp enabled.");
    }
}
