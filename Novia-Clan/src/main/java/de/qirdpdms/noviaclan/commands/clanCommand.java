package de.qirdpdms.noviaclan.commands;

import de.qirdpdms.noviaclan.Main;
import de.qirdpdms.noviaclan.utils.DatabaseManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StringUtil;

import java.util.*;

public class clanCommand implements CommandExecutor, TabCompleter {

    private final DatabaseManager databaseManager;

    private final FileConfiguration config;

    private final String prefix;

    private final Main plugin;

    public clanCommand(DatabaseManager databaseManager, Main plugin) {
        this.databaseManager = databaseManager;
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        prefix = ChatColor.translateAlternateColorCodes('&', config.getString("prefix"));
    }

    private final Map<UUID, Boolean> pendingClanDissolutions = new HashMap<>();

    private boolean hasPendingClanDissolution(UUID playerUUID) {
        return pendingClanDissolutions.getOrDefault(playerUUID, false);
    }

    private void removePendingClanDissolution(UUID playerUUID) {
        pendingClanDissolutions.remove(playerUUID);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern verwendet werden.");
            return true;
        }
        Economy eco = Main.getEconomy();
        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("clan")) {
            if (args.length == 0) {
                player.sendMessage(prefix + "§eClan Befehle:");
                player.sendMessage("§b/clan create <ClanName> <ClanTag> - Erstelle einen neuen Clan.");
                player.sendMessage("§b/clan invite <SpielerName> - Lade einen Spieler in deinen Clan ein.");
                player.sendMessage("§b/clan accept - Akzeptiere eine Einladung in einen Clan.");
                player.sendMessage("§b/clan leave - Verlasse deinen aktuellen Clan.");
                return true;
            } else if (args.length >= 3 && args[0].equalsIgnoreCase("create")) {
                String clanName = args[1];
                String clanTag = args[2];
                String clanCreator = player.getName();
                if (eco.has(player, 50000)){
                    if (!databaseManager.registerClan(player, clanName, clanTag, clanCreator)) {
                        databaseManager.updateClan(clanName, playerUUID.toString());
                        eco.withdrawPlayer(player, 50000);
                    }
                }else {
                    player.sendMessage(prefix + "§bDu hast nicht genügend geld");
                }


                return true;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
                Player invitedPlayer = player.getServer().getPlayer(args[1]);
                String clanName = databaseManager.getClanName(playerUUID);
                if (invitedPlayer != null) {
                    databaseManager.invitePlayer(player, invitedPlayer, clanName);
                } else {
                    player.sendMessage(prefix + "§bSpieler nicht gefunden.");
                }
                return true;
            } else if (args.length == 1 && args[0].equalsIgnoreCase("accept")) {
                databaseManager.acceptInvitation(player, player);
                return true;
            } else if (args.length == 1 && args[0].equalsIgnoreCase("leave")) {
                databaseManager.leaveClan(playerUUID, player);
                return true;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
                String clanName = args[1];
                String clanInfo = databaseManager.getClanInfo(clanName);

                if (clanInfo != null) {
                    player.sendMessage(prefix + "§eInformationen über den Clan " + clanName + ":");
                    player.sendMessage(clanInfo);
                } else {
                    player.sendMessage(prefix + "§cDer Clan " + clanName + " wurde nicht gefunden.");
                }
                return true;
            }else if (args.length == 4 && args[0].equalsIgnoreCase("mod") && args[1].equalsIgnoreCase("verify")) {
                String clanName = args[2];
                boolean isVerified = args[3].equalsIgnoreCase("true");

                if (databaseManager.setClanVerificationStatus(clanName, isVerified)) {
                    player.sendMessage(prefix + "§eVerifizierungsstatus des Clans '" + clanName + "' wurde geändert.");
                } else {
                    player.sendMessage(prefix + "§cFehler beim Ändern des Verifizierungsstatus des Clans.");
                }
                return true;
            }else if (args.length == 3 && args[0].equalsIgnoreCase("mod") && args[1].equalsIgnoreCase("delete")) {
                String clanName = args[2];

                if (databaseManager.deleteClan(clanName)) {
                    player.sendMessage(prefix + "§eDer Clan '" + clanName + "' wurde gelöscht.");
                } else {
                    player.sendMessage(prefix + "§cFehler beim Löschen des Clans.");
                }
                return true;
            } else if (args.length == 3 && args[0].equalsIgnoreCase("bank")) {
                String action = args[1].toLowerCase();
                String clanName = databaseManager.getClanName(playerUUID);

                if (clanName == null) {
                    player.sendMessage(prefix + "§cDu bist in keinem Clan.");
                    return true;
                }

                String clanOwner = databaseManager.getClanCreator(clanName);

                if (!clanOwner.equals(player.getName())) {
                    player.sendMessage(prefix + "§cNur der Clan-Owner kann Geld in die Clanbank einzahlen/auszahlen.");
                    return true;
                }

                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(prefix + "§cUngültiger Betrag.");
                    return true;
                }

                switch (action) {
                    case "einzahlen":
                        if (eco.has(player, amount)) {
                            if (databaseManager.depositToClanBank(clanName, amount)) {
                                eco.withdrawPlayer(player, amount);
                                player.sendMessage(prefix + "§aDu hast " + amount + " in die Clanbank eingezahlt.");
                            } else {
                                player.sendMessage(prefix + "§cFehler beim Einzahlen in die Clanbank.");
                            }
                        } else {
                            player.sendMessage(prefix + "§cDu hast nicht genügend Geld.");
                        }
                        return true;
                    case "auszahlen":
                        double clanBankBalance = databaseManager.getClanBankBalance(clanName);
                        if (clanBankBalance >= amount) {
                            if (databaseManager.withdrawFromClanBank(clanName, amount)) {
                                eco.depositPlayer(player, amount);
                                player.sendMessage(prefix + "§aDu hast " + amount + " aus der Clanbank abgehoben.");
                            } else {
                                player.sendMessage(prefix + "§cFehler beim Abheben aus der Clanbank.");
                            }
                        } else {
                            player.sendMessage(prefix + "§cDie Clanbank hat nicht genügend Geld.");
                        }
                        return true;
                }
            }else if (args.length == 1 && args[0].equalsIgnoreCase("auflösen")) {
                // Überprüfen, ob der Spieler der Clan-Ersteller ist
                String clanName = databaseManager.getClanName(playerUUID);
                if (databaseManager.isClanCreator(clanName, player.getName())) {
                    // Setze die ausstehende Clan-Auflösungsbestätigung für den Clan-Ersteller
                    pendingClanDissolutions.put(playerUUID, true);

                    // Erstelle den Textkomponenten-Knopf für die Clan-Auflösungsbestätigung
                    ComponentBuilder confirmButton = new ComponentBuilder("Klicke hier, um die Clan-Auflösung zu bestätigen");
                    confirmButton.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan confirm"));

                    // Sende die Nachricht mit dem Textkomponenten-Knopf
                    player.spigot().sendMessage(confirmButton.create());

                    // Erstelle eine Aufgabe, um die Nachricht nach 30 Sekunden zu löschen
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // Überprüfen, ob der Spieler noch online ist und die Nachricht existiert
                            if (player.isOnline() && player.getLastPlayed() != 0) {
                                // Lösche die Nachricht
                                player.sendMessage(""); // Leerzeichen, um die vorherige Nachricht zu entfernen
                            }
                        }
                    }.runTaskLater(plugin, 30 * 20); // 30 Sekunden (20 Ticks pro Sekunde)
                } else {
                    player.sendMessage(prefix + "§cDu bist nicht der Clan-Ersteller und kannst den Clan nicht auflösen.");
                }
                return true;
            }else if (args.length == 1 && args[0].equalsIgnoreCase("confirmauflösen")) {
                // Überprüfen, ob der Spieler eine ausstehende Clan-Auflösungsbestätigung hat
                if (hasPendingClanDissolution(playerUUID)) {
                    // Hole den Clan-Namen des Spielers
                    String clanName = databaseManager.getClanName(playerUUID);

                    // Löse den Clan auf
                    if (databaseManager.deleteClan(clanName)) {
                        // Erfolgreiche Clan-Auflösung
                        player.sendMessage(prefix + "§eDein Clan '" + clanName + "' wurde erfolgreich aufgelöst.");
                    } else {
                        player.sendMessage(prefix + "§cFehler beim Auflösen des Clans.");
                    }

                    // Entferne die ausstehende Clan-Auflösungsbestätigung
                    removePendingClanDissolution(playerUUID);
                } else {
                    player.sendMessage(prefix + "§cEs liegt keine ausstehende Clan-Auflösungsbestätigung vor.");
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (cmd.getName().equalsIgnoreCase("clan") && args.length > 0) {
            if (args.length == 1) {
                completions.add("create");
                completions.add("invite");
                completions.add("leave");
                completions.add("bank");
                completions.add("info");
                completions.add("mod");
            } else if (args[0].equalsIgnoreCase("create")) {
                if (args.length == 2) {
                    completions.add("<ClanName>");
                } else if (args.length == 3) {
                    completions.add("<ClanTag>");
                }
            } else if (args[0].equalsIgnoreCase("invite")) {
                if (args.length == 2) {
                    List<String> onlinePlayers = new ArrayList<>();
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        onlinePlayers.add(onlinePlayer.getName());
                    }


                    StringUtil.copyPartialMatches(args[1], onlinePlayers, completions);
                }
            } else if (args[0].equalsIgnoreCase("mod") && args.length == 2) {
                completions.add("verify");
                completions.add("delete");
            } else if (args[0].equalsIgnoreCase("bank")) {
                if (args.length == 2) {
                    completions.add("einzahlen");
                    completions.add("auszahlen");
                }
            }
        }
        return completions;
    }
}