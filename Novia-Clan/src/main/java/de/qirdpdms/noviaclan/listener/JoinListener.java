package de.qirdpdms.noviaclan.listener;

import de.qirdpdms.noviaclan.utils.DatabaseManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final DatabaseManager databaseManager;

    public JoinListener(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @EventHandler
    public void  onJoin(PlayerJoinEvent e){
        Player player = e.getPlayer();

        databaseManager.registerPlayer(player.getUniqueId(), player.getName());


    }
}
