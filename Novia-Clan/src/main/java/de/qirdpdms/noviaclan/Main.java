package de.qirdpdms.noviaclan;

import de.qirdpdms.noviaclan.commands.clanCommand;
import de.qirdpdms.noviaclan.listener.JoinListener;
import de.qirdpdms.noviaclan.utils.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private DatabaseManager databaseManager;

    private static Economy econ = null;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        if (!setupEconomy() ) {
            System.out.println("Es wurde kein Geld Plugin gefunden!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }



        databaseManager = new DatabaseManager(this);

        PluginManager pl = Bukkit.getPluginManager();

        pl.registerEvents(new JoinListener(databaseManager), this);

        getCommand("clan").setExecutor(new clanCommand(databaseManager));

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

}
