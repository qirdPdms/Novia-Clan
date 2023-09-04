package de.qirdpdms.noviaclan.utils;

import de.qirdpdms.noviaclan.Main;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private HashMap<UUID, UUID> clanInvitations = new HashMap<>();

    private final String host;
    private final String port;
    private final String database;
    private final String username;
    private final String password;

    private final String prefix;

    private final FileConfiguration config;

    private final Main plugin;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        config = plugin.getConfig();

        host = config.getString("database.host");
        port = config.getString("database.port");
        database = config.getString("database.name");
        username = config.getString("database.username");
        password = config.getString("database.password");
        prefix = ChatColor.translateAlternateColorCodes('&', config.getString("prefix"));

        initializeDatabase();
    }

    private Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + database,
                username,
                password
        );

        createTablesIfNotExist(connection);

        return connection;
    }

    private void createTablesIfNotExist(Connection connection) throws SQLException {
        if (!doesTableExist(connection, "clan")) {
            String createClanTableQuery = "CREATE TABLE IF NOT EXISTS clan (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "clanname VARCHAR(255) NOT NULL," +
                    "clantag VARCHAR(255) NOT NULL," +
                    "clancreator VARCHAR(255) NOT NULL," +
                    "clanplayer TEXT," +
                    "clanränge TEXT," +
                    "clanbank DOUBLE," +
                    "clanverify BOOLEAN" +
                    ")";
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createClanTableQuery);
            } catch (SQLException e) {
                e.printStackTrace();
                System.err.println("SQL Error: " + e.getMessage());
            }
        }


        if (!doesTableExist(connection, "clan_player")) {
            String createClanPlayerTableQuery = "CREATE TABLE IF NOT EXISTS clan_player (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "uuid VARCHAR(36) NOT NULL," +
                    "spielername VARCHAR(255) NOT NULL," +
                    "clan VARCHAR(255)" +
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
                    return resultSet.next();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
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
        if (isPlayerInClan(player.getUniqueId())) {
            player.sendMessage(prefix + "§bDu bist bereits in einem Clan");
            return true;
        }


        if (clanTag.length() > 4) {
            player.sendMessage(prefix + "§cDer Clan-Tag darf nicht mehr als 4 Zeichen haben.");
            return true;
        }

        try (Connection connection = getConnection()) {
            String query = "INSERT INTO clan (clanname, clantag, clancreator, clanplayer, clanränge, clanbank, clanverify) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, clanName);
                statement.setString(2, clanTag);
                statement.setString(3, clanCreatorName);
                statement.setString(4, player.getName());
                statement.setString(5, "Gründer, Moderator, Mitglied");
                statement.setDouble(6, 0.0);
                statement.setInt(7, 0);
                updateClan(clanName, player.getUniqueId().toString());
                statement.executeUpdate();


            }
            player.sendMessage(prefix + "§eDein Clan §b" + clanName + " §emit dem Tag §b" + clanTag + " §ewurde erfolgreich erstellt!");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
                    return resultSet.next();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
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
                        return clanName != null && !clanName.isEmpty();
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
            sender.sendMessage(prefix + "§eDu bist in keinem Clan und kannst daher niemanden einladen.");
            return;
        }

        if (isClanCreator(sender.getName(), senderClan)) {
            System.out.println("DEBUG: Der Spieler " + sender.getName() + " ist nicht der Clan-Creator des Clans " + senderClan);
            sender.sendMessage(prefix + "§bDu bist nicht der Clan-Creator deines Clans und kannst daher niemanden einladen.");
            return;
        }

        if (clanInvitations.containsKey(invitedPlayerUUID)) {
            sender.sendMessage(prefix + "§eDer Spieler hat bereits eine Einladung erhalten.");
            return;
        }

        clanInvitations.put(invitedPlayerUUID, senderUUID);

        TextComponent acceptButton = createAcceptButton();

        TextComponent message = new TextComponent(prefix + "§eDu wurdest in den Clan " + clanName + " eingeladen ");
        message.addExtra("§7[§a");
        message.addExtra(acceptButton);
        message.addExtra("§7]");

        invitedPlayer.spigot().sendMessage(message);

        sender.sendMessage(prefix + "§eDie Einladung an " + invitedPlayer.getName() + " wurde verschickt.");
    }

    public void acceptInvitation(Player invitedPlayer, Player player) {
        UUID invitedPlayerUUID = invitedPlayer.getUniqueId();

        if (!clanInvitations.containsKey(invitedPlayerUUID)) {
            invitedPlayer.sendMessage(prefix + "§eDu hast keine ausstehenden Einladungen.");
            return;
        }


        UUID senderUUID = clanInvitations.get(invitedPlayerUUID);
        String clanName = getClanName(senderUUID);

        if (clanName != null && !clanName.isEmpty()) {
            if (isPlayerInClan(invitedPlayerUUID)) {
                invitedPlayer.sendMessage(prefix + "§eDu bist bereits Mitglied eines Clans.");
                return;
            }

            boolean success = addToClan(invitedPlayer, clanName, invitedPlayerUUID);

            if (success) {
                invitedPlayer.sendMessage(prefix + "§eDu hast erfolgreich den Clan " + clanName + " beigetreten.");
                clanInvitations.remove(invitedPlayerUUID);
                updateClan(clanName, invitedPlayerUUID.toString());
            } else {
                invitedPlayer.sendMessage(prefix + "§bFehler beim Beitritt zum Clan " + clanName + ".");
            }
        } else {
            invitedPlayer.sendMessage(prefix + "§bFehler beim Beitritt zum Clan.");
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
        return null;
    }

    public boolean isClanCreator(String clanName, String playerName) {
        try (Connection connection = getConnection()) {
            String query = "SELECT clancreator FROM clan WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, clanName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String clanCreator = resultSet.getString("clancreator");
                        return playerName.equals(clanCreator);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    public String getClanCreator(String clanName) {
        try (Connection connection = getConnection()) {
            String query = "SELECT clancreator FROM clan WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, clanName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString("clancreator");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Rückgabe null, wenn der Clan-Ersteller nicht gefunden wurde
    }
    public boolean addToClan(Player player, String clanName, UUID playerUUID) {

        if (!clanExists(clanName)) {
            return false;
        }

        if (isPlayerInClan(playerUUID)) {
            return false;
        }

        try (Connection connection = getConnection()) {

            if (isPlayerInClan(playerUUID)) {
                return false;
            }

            String query = "UPDATE clan SET clanplayer = CONCAT(clanplayer, ', ', ?) WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, player.getName());
                statement.setString(2, clanName);
                statement.executeUpdate();

                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public boolean leaveClan(UUID playerUUID, Player player) {
        if (!isPlayerInClan(playerUUID)) {
            return false;
        }

        try (Connection connection = getConnection()) {
            String currentClan = getClanName(playerUUID);

            if (isClanCreator(currentClan, player.getName())) {
                player.sendMessage(prefix + "§eDu bist der Clan-Ersteller. Wenn du deinen Clan auflösen möchtest, gib /clan auflösen ein.");
                return true;
            }

            if (currentClan != null) {
                List<String> clanMembers = getClanPlayers(currentClan);
                clanMembers.remove(player.getName());
                String clanMembersString = String.join(", ", clanMembers);

                String query = "UPDATE clan SET clanplayer = ? WHERE clanname = ?";
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, clanMembersString);
                    statement.setString(2, currentClan);
                    statement.executeUpdate();

                }
                player.sendMessage(prefix + "§eDu hast deinen Clan verlassen.");



            }

            String updateQuery = "UPDATE clan_player SET clan = null WHERE uuid = ?";
            try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
                updateStatement.setString(1, playerUUID.toString());
                updateStatement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    public List<String> getClanPlayers(String clanName) {
        List<String> clanPlayers = new ArrayList<>();

        try (Connection connection = getConnection()) {
            String query = "SELECT clanplayer FROM clan WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, clanName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String clanPlayerString = resultSet.getString("clanplayer");
                        if (clanPlayerString != null && !clanPlayerString.isEmpty()) {
                            String[] clanPlayerArray = clanPlayerString.split(",");
                            clanPlayers.addAll(Arrays.asList(clanPlayerArray));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return clanPlayers;
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
                        boolean clanVerify = resultSet.getBoolean("clanverify");

                        StringBuilder clanInfo = new StringBuilder();
                        clanInfo.append("§eClan Name: §b").append(clanName).append("\n");
                        clanInfo.append("§eClan Tag: §b").append(clanTag).append("\n");
                        clanInfo.append("§eClan Creator: §b").append(clanCreator).append("\n");
                        clanInfo.append("§eClan Players: §b").append(clanPlayers).append("\n");
                        clanInfo.append("§eClan Bank: §b").append(clanBank).append("\n");

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

        return null;
    }

    public boolean setClanVerificationStatus(String clanName, boolean isVerified) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE clan SET clanverify = ? WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(updateQuery)) {
                statement.setBoolean(1, isVerified);
                statement.setString(2, clanName);
                int rowsUpdated = statement.executeUpdate();
                return rowsUpdated > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteClan(String clanName) {
        if (!clanExists(clanName)) {
            return false;
        }

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            try {
                String updateQuery = "UPDATE clan_player SET clan = null WHERE clan = ?";
                try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
                    updateStatement.setString(1, clanName);
                    updateStatement.executeUpdate();
                }

                String deleteQuery = "DELETE FROM clan WHERE clanname = ?";
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteQuery)) {
                    deleteStatement.setString(1, clanName);
                    deleteStatement.executeUpdate();
                }

                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                e.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean withdrawFromClanBank(String clanName, double amount) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE clan SET clanbank = clanbank - ? WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(updateQuery)) {
                statement.setDouble(1, amount);
                statement.setString(2, clanName);
                int rowsUpdated = statement.executeUpdate();
                return rowsUpdated > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean depositToClanBank(String clanName, double amount) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE clan SET clanbank = clanbank + ? WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(updateQuery)) {
                statement.setDouble(1, amount);
                statement.setString(2, clanName);
                int rowsUpdated = statement.executeUpdate();
                return rowsUpdated > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public double getClanBankBalance(String clanName) {
        try (Connection connection = getConnection()) {
            String query = "SELECT clanbank FROM clan WHERE clanname = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, clanName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getDouble("clanbank");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }


    public TextComponent createAcceptButton() {
        TextComponent acceptText = new TextComponent("Annehmen");
        acceptText.setColor(ChatColor.GREEN);
        acceptText.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan accept"));
        return acceptText;
    }

    public String getPlayerName(UUID playerUUID) {
        try (Connection connection = getConnection()) {
            String query = "SELECT spielername FROM clan_player WHERE uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerUUID.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString("spielername");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }



}