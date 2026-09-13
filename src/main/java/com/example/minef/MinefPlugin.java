package com.example.minef;

import org.bukkit.plugin.java.JavaPlugin;

public class MinefPlugin extends JavaPlugin {

    private BotSession session;

    @Override
    public void onEnable() {
        session = new BotSession(this);
        MinefCommand executor = new MinefCommand(this, session);
        getCommand("minef").setExecutor(executor);
        getCommand("minef").setTabCompleter(executor);
        getLogger().info("Minef включён. Используйте /minef start чтобы запустить виртуального игрока.");
    }

    @Override
    public void onDisable() {
        if (session != null) {
            session.stop();
        }
    }
}
