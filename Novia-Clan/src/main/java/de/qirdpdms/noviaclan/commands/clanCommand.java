package de.qirdpdms.noviaclan.commands;

import de.qirdpdms.noviaclan.Main;
import de.qirdpdms.noviaclan.utils.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class clanCommand implements CommandExecutor, TabCompleter {

    private final DatabaseManager databaseManager;

    public clanCommand(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
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
        String prefix = "§b§lSuchtMc §8§l» ";

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
                        player.sendMessage(prefix + "§eClan '" + clanName + "' mit Tag '" + clanTag + "' wurde erstellt.");
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
                databaseManager.leaveClan(playerUUID);
                player.sendMessage(prefix + "§eDu hast deinen Clan verlassen.");
                return true;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
                String clanName = args[1]; // Clan-Namen aus dem Befehl extrahieren
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
                boolean isVerified = args[3].equalsIgnoreCase("true"); // Überprüfen, ob der vierte Parameter "true" ist

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
                completions.add("accept");
                completions.add("leave");
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
                    // Hier könntest du Spieler als Vorschläge hinzufügen, aber das ist komplexer und erfordert eine Spielerliste.
                }
            } else if (args[0].equalsIgnoreCase("mod") && args.length == 2) {
                completions.add("verify");
                completions.add("delete");
            }
        }
        return completions;
    }
}