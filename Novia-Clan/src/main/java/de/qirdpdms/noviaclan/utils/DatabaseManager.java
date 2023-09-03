package de.qirdpdms.noviaclan.utils;

import de.qirdpdms.noviaclan.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.HashMap;
import java.util.UUID;

public class DatabaseManager {

    private HashMap<UUID, UUID> clanInvitations = new HashMap<>();

    private final String host;
    private final String port;
    private final String database;
    private final String username;
    private final String password;

    private final FileConfiguration config;

    public DatabaseManager(Main plugin) {
        // Lade die Konfiguration aus der config.yml
        plugin.saveDefaultConfig();
        config = plugin.getConfig();

        // Verwende die in der Konfiguration definierten Datenbank-Einstellungen
        host = config.getString("database.host");
        port = config.getString("database.port");
        database = config.getString("database.name");
        username = config.getString("database.username");
        password = config.getString("database.password");

        initializeDatabase();
    }

    private Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + database,
                username,
                password
        );

        // Hier überprüfen, ob die Tabellen existieren, und sie erstellen, wenn nicht
        createTablesIfNotExist(connection);

        return connection;
    }

    private void createTablesIfNotExist(Connection connection) throws SQLException {
        // Überprüfe, ob die clan-Tabelle existiert, und erstelle sie, wenn nicht
        if (!doesTableExist(connection, "clan")) {
            String createClanTableQuery = "CREATE TABLE IF NOT EXISTS clan (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "clanname VARCHAR(255) NOT NULL," +
                    "clantag VARCHAR(255) NOT NULL," +
                    "clancreator VARCHAR(255) NOT NULL," +
                    "clanplayer TEXT," +
                    "clanränge TEXT," +
                    "clanbank DOUBLE," +
                    "clanverify BOOLEAN" +  // Removed the comma here
                    ")";
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createClanTableQuery);
            } catch (SQLException e) {
                e.printStackTrace();
                System.err.println("SQL Error: " + e.getMessage());
            }
        }



        // Überprüfe, ob die clan_player-Tabelle existiert, und erstelle sie, wenn nicht
        if (!doesTableExist(connection, "clan_player")) {
            String createClanPlayerTableQuery = "CREATE TABLE IF NOT EXISTS clan_player (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "uuid VARCHAR(36) NOT NULL," +
                    "spielername VARCHAR(255) NOT NULL," +
                    "clan VARCHAR(255)" +  // Removed the comma here
                    ")";
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createClanPlayerTableQuery);
            } catch (SQLException e) {
                e.printStackTrace();
                System.err.println("SQL Error: " + e.getMessage());
            }
        }
    }

    private boolean doesTableExist(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, null, tableName, null)) {
            return resultSet.next();
        }
    }

    private void initializeDatabase() {
        try (Connection connection = getConnection()) {
            // Hier ggf. weitere Initialisierungsschritte ausführen
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isPlayerRegistered(UUID uuid) {
        try (Connection connection = getConnection()) {
            String query = "SELECT uuid FROM clan_player WHERE uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next(); // Gibt true zurück, wenn der Spieler gefunden wurde, andernfalls false.
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false; // Bei Fehlern wird auch false zurückgegeben.
        }
    }

    public void registerPlayer(UUID uuid, String spielername) {
        try (Connection connection = getConnection()) {
            if (!isPlayerRegistered(uuid)) {
                String query = "INSERT INTO clan_player (uuid, spielername, clan) VALUES (?, ?, null)";
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, spielername);
                    statement.executeUpdate();
                }
            } else {
                return;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean registerClan(Player player, String clanName, String clanTag, String clanCreatorName) {
        if (clanExists(clanName)) {
            player.sendMessage("§b§lSuchtMc §8§l» §bClan ist breits vergeben.");
            return true;
        }

        if (isPlayerInClan(player.getUniqueId())) {
            player.sendMessage("§b§lSuchtMc §8§l» §bDu bist bereits in einem Clan.");
            return true;
        }

        try (Connection connection = getConnection()) {
            String query = "INSERT INTO clan (clanname, clantag, clancreator, clanplayer, clanränge, clanbank) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, clanName);
                statement.setString(2, clanTag);
                statement.setString(3, clanCreatorName);
                statement.setString(4, player.getName()); // Spielername anstelle von UUID
                statement.setString(5, "Gründer, Moderator, Mitglied"); // Standard-Ränge
                statement.setDouble(6, 0.0); // Clanbank mit Stand 0
                statement.executeUpdate(); // Fügt den Clan zur Datenbank hinzu.

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        String generatedClanName = generatedKeys.getString(1);

                        // Aktualisiere den Clan-Namen des Clan Creators
                        updateClan(generatedClanName, player.getName());
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Clan wurde erfolgreich erstellt
        return false;
    }

    public void updateClan(String clanName, String uuid) {
        try (Connection connection = getConnection()) {
            String query = "UPDATE clan_player SET clan = ? WHERE uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, clanName);
                statement.setString(2, uuid);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean clanExists(String clanName) {
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM clan WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, clanName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next(); // Gibt true zurück, wenn der Clan gefunden wurde, andernfalls false.
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false; // Bei Fehlern wird auch false zurückgegeben.
        }
    }

    public boolean isPlayerInClan(UUID playerUUID) {
        try (Connection connection = getConnection()) {
            String query = "SELECT clan FROM clan_player WHERE uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerUUID.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String clanName = resultSet.getString("clan");
                        return clanName != null && !clanName.isEmpty(); // Spieler ist in einem Clan, wenn clan nicht null oder leer ist
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void invitePlayer(Player sender, Player invitedPlayer, String clanName) {
        UUID senderUUID = sender.getUniqueId();
        UUID invitedPlayerUUID = invitedPlayer.getUniqueId();

        String senderClan = getClanName(senderUUID);

        if (senderClan == null) {
            sender.sendMessage("§b§lSuchtMc §8§l» §eDu bist in keinem Clan und kannst daher niemanden einladen.");
            return;
        }

        // Überprüfe, ob der Sender der Clan-Creator des Clans ist
        if (!isClanCreator(sender.getName(), senderClan)) {
            sender.sendMessage("§b§lSuchtMc §8§l» §bDu bist nicht der Clan-Creator deines Clans und kannst daher niemanden einladen.");
            return;
        }

        // Überprüfe, ob der Spieler bereits eine Einladung erhalten hat
        if (clanInvitations.containsKey(invitedPlayerUUID)) {
            sender.sendMessage("§b§lSuchtMc §8§l» §eDer Spieler hat bereits eine Einladung erhalten.");
            return;
        }

        // Speichere die Einladung in der HashMap
        clanInvitations.put(invitedPlayerUUID, senderUUID);

        // Sende eine Einladungsnachricht an den eingeladenen Spieler
        invitedPlayer.sendMessage("§b§lSuchtMc §8§l» §eDu wurdest in den Clan von " + clanName + " eingeladen. Akzeptiere mit /clan accept.");
        sender.sendMessage("§b§lSuchtMc §8§l» §eDie Einladung an " + invitedPlayer.getName() + " wurde verschickt.");
    }

    public void acceptInvitation(Player invitedPlayer, Player player) {
        UUID invitedPlayerUUID = invitedPlayer.getUniqueId();

        // Überprüfe, ob der Spieler eine Einladung erhalten hat
        if (!clanInvitations.containsKey(invitedPlayerUUID)) {
            invitedPlayer.sendMessage("§b§lSuchtMc §8§l» §eDu hast keine ausstehenden Einladungen.");
            return;
        }

        // Füge den Spieler dem Clan hinzu
        UUID senderUUID = clanInvitations.get(invitedPlayerUUID);
        String clanName = getClanName(senderUUID); // Hole den Clan-Namen des Einladenden

        if (clanName != null && !clanName.isEmpty()) {
            // Überprüfe, ob der Spieler bereits einem Clan angehört
            if (isPlayerInClan(invitedPlayerUUID)) {
                invitedPlayer.sendMessage("§b§lSuchtMc §8§l» §eDu bist bereits Mitglied eines Clans.");
                return;
            }

            boolean success = addToClan(player, clanName, invitedPlayerUUID);

            if (success) {
                invitedPlayer.sendMessage("§b§lSuchtMc §8§l» §eDu hast erfolgreich den Clan " + clanName + " beigetreten.");
                clanInvitations.remove(invitedPlayerUUID);
                updateClan(clanName, invitedPlayerUUID.toString());
            } else {
                invitedPlayer.sendMessage("§b§lSuchtMc §8§l» §bFehler beim Beitritt zum Clan " + clanName + ".");
            }
        } else {
            invitedPlayer.sendMessage("§b§lSuchtMc §8§l» §bFehler beim Beitritt zum Clan.");
        }
    }

    public String getClanName(UUID playerUUID) {
        try (Connection connection = getConnection()) {
            String query = "SELECT clan FROM clan_player WHERE uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerUUID.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String clanName = resultSet.getString("clan");
                        if (clanName != null && !clanName.equalsIgnoreCase("null")) {
                            return clanName;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Wenn der Clan-Name "null" ist oder nicht gefunden wurde, wird null zurückgegeben.
    }

    private boolean isClanCreator(String playerName, String clanName) {
        try (Connection connection = getConnection()) {
            String query = "SELECT clancreator FROM clan WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, clanName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String clanCreatorName = resultSet.getString("clancreator");
                        return playerName.equals(clanCreatorName);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    public boolean addToClan(Player player, String clanName, UUID playerUUID) {
        // Prüfe, ob der Clan existiert
        if (!clanExists(clanName)) {
            return false; // Der Clan existiert nicht
        }

        if (isPlayerInClan(playerUUID)) {
            return false; // Der Spieler ist bereits Mitglied eines Clans
        }

        try (Connection connection = getConnection()) {
            // Überprüfe, ob der Spieler bereits einem Clan angehört
            if (isPlayerInClan(playerUUID)) {
                return false; // Der Spieler ist bereits Mitglied eines Clans
            }

            // Füge den Spieler zur Liste der Clanmitglieder hinzu
            String query = "UPDATE clan SET clanplayer = CONCAT(clanplayer, ', ', ?) WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, player.getName());
                statement.setString(2, clanName);
                statement.executeUpdate();

                return true; // Erfolgreich dem Clan hinzugefügt
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false; // Bei einem Fehler wird false zurückgegeben
        }
    }
    public void leaveClan(UUID playerUUID) {
        // Überprüfe, ob der Spieler einem Clan angehört
        if (!isPlayerInClan(playerUUID)) {
            return; // Der Spieler ist in keinem Clan
        }

        try (Connection connection = getConnection()) {
            // Hole den aktuellen Clan des Spielers
            String currentClan = getClanName(playerUUID);

            if (currentClan != null) {
                // Entferne den Spieler aus der Clan-Tabelle
                String query = "UPDATE clan SET clanplayer = REPLACE(clanplayer, ?, '') WHERE clanname = ?";
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, playerUUID.toString());
                    statement.setString(2, currentClan);
                    statement.executeUpdate();
                }
            }

            // Setze den Clan des Spielers auf null, um ihn aus dem Clan in der clan_player-Tabelle zu entfernen
            String updateQuery = "UPDATE clan_player SET clan = null WHERE uuid = ?";
            try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
                updateStatement.setString(1, playerUUID.toString());
                updateStatement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public String getClanInfo(String clanName) {
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM clan WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, clanName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String clanTag = resultSet.getString("clantag");
                        String clanCreator = resultSet.getString("clancreator");
                        String clanPlayers = resultSet.getString("clanplayer");
                        double clanBank = resultSet.getDouble("clanbank");
                        boolean clanVerify = resultSet.getBoolean("clanverify"); // Holen Sie den Wert des clanverify-Felds

                        StringBuilder clanInfo = new StringBuilder();
                        clanInfo.append("§eClan Name: §b").append(clanName).append("\n");
                        clanInfo.append("§eClan Tag: §b").append(clanTag).append("\n");
                        clanInfo.append("§eClan Creator: §b").append(clanCreator).append("\n");
                        clanInfo.append("§eClan Players: §b").append(clanPlayers).append("\n");
                        clanInfo.append("§eClan Bank: §b").append(clanBank).append("\n");

                        // Überprüfen Sie den Wert von clanVerify und fügen Sie die entsprechende Nachricht hinzu
                        if (clanVerify) {
                            clanInfo.append("Clan Status: Verifiziert\n");
                        } else {
                            clanInfo.append("Clan Status: Nicht verifiziert\n");
                        }

                        return clanInfo.toString();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null; // Wenn der Clan nicht gefunden wird, wird null zurückgegeben.
    }
    public boolean setClanVerificationStatus(String clanName, boolean isVerified) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE clan SET clanverify = ? WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(updateQuery)) {
                statement.setBoolean(1, isVerified);
                statement.setString(2, clanName);
                int rowsUpdated = statement.executeUpdate();
                return rowsUpdated > 0; // Gibt true zurück, wenn mindestens eine Zeile aktualisiert wurde.
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false; // Bei Fehlern wird false zurückgegeben.
        }
    }

    public boolean deleteClan(String clanName) {
        if (!clanExists(clanName)) {
            return false; // Der Clan existiert nicht
        }

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false); // Deaktivieren der automatischen Commit-Funktion

            try {
                // Setze den Clan der Mitglieder auf null
                String updateQuery = "UPDATE clan_player SET clan = null WHERE clan = ?";
                try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
                    updateStatement.setString(1, clanName);
                    updateStatement.executeUpdate();
                }

                // Lösche den Clan aus der Datenbank
                String deleteQuery = "DELETE FROM clan WHERE clanname = ?";
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteQuery)) {
                    deleteStatement.setString(1, clanName);
                    deleteStatement.executeUpdate();
                }

                connection.commit(); // Commit der Transaktion
                return true; // Erfolgreich gelöscht
            } catch (SQLException e) {
                connection.rollback(); // Rollback bei einem Fehler
                e.printStackTrace();
                return false; // Bei einem Fehler wird false zurückgegeben
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false; // Bei einem Fehler beim Verbindungsaufbau wird false zurückgegeben
        }
    }



}