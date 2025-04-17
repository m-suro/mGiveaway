package me.msuro.mGiveaway.utils;

import me.msuro.mGiveaway.MGiveaway;
import me.msuro.mGiveaway.Giveaway;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class DBUtils {
    private static MGiveaway instance;
    private static File dbFile;

    public DBUtils() {
        try {
            instance = MGiveaway.getInstance();

            dbFile = new File(instance.getDataFolder(), "mgiveaway.sqlite");
            if (!dbFile.exists()) {
                instance.saveResource("mgiveaway.sqlite", false);
                instance.getLogger().info("Database file created!");
            } else {
                instance.getLogger().info("Database file loaded successfully!");
            }
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            Statement statement = connection.createStatement();
            if (statement == null || statement.isClosed()) {
                MGiveaway.setPaused(true);
                instance.getLogger().severe("Giveaways paused! Reload the plugin to try again!");
                throw new RuntimeException("Failed to create database file!", new SQLException("Statement is null or closed!"));
            }

        } catch (SQLException e) {
            MGiveaway.setPaused(true);
            instance.getLogger().severe("Giveaways paused! Reload the plugin to try again!");
            throw new RuntimeException("Failed to load database file!", e);

        }

    }

    private static Connection getConnection() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        } catch (SQLException e) {
            MGiveaway.setPaused(true);
            instance.getLogger().severe("Giveaways paused! Reload the plugin to try again!");
            throw new RuntimeException("Failed to get connection to database!", e);
        }
    }

    public void createGiveawayTable(String giveawayName) {
        try (Connection conn = getConnection();
             Statement statement = conn.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS `entries-" + giveawayName + "` (" +
                    "`discord_id` varchar(255) NOT NULL," +
                    "`minecraft_name` varchar(255) NOT NULL," +
                    "PRIMARY KEY (`discord_id`, `minecraft_name`));");


        } catch (SQLException e) {
            MGiveaway.setPaused(true);
            instance.getLogger().severe("Giveaways paused! Reload the plugin to try again!");
            throw new RuntimeException("Failed to create giveaway table!", e);
        }
    }

    public HashMap<String, String> refreshEntries(Giveaway giveaway) {
        HashMap<String, String> entries = new HashMap<>();
        createGiveawayTable(giveaway.name());
        try (Connection conn = getConnection(); Statement statement = conn.createStatement()) {
            statement.execute("SELECT * FROM `entries-" + giveaway.name() + "`;");
            ResultSet resultSet = statement.getResultSet();
            while (resultSet.next()) {
                entries.put(resultSet.getString("discord_id"), resultSet.getString("minecraft_name"));
            }

        } catch (SQLException e) {
            MGiveaway.setPaused(true);
            instance.getLogger().severe("Giveaways paused! Reload the plugin to try again!");
            throw new RuntimeException("Failed to refresh entries (giveaway: " + giveaway.name() + ")!", e);
        }
        return entries;
    }

    public void saveEntries(Giveaway giveaway) {
        HashMap<String, String> entries = giveaway.entries();
        createGiveawayTable(giveaway.name());
        if (entries == null || entries.isEmpty()) return;
        try (Connection conn = getConnection()) {
            instance.getLogger().info("Saving entries for giveaway: " + giveaway.name() + " (" + entries.size() + ")");
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                saveEntry(giveaway.name(), entry.getKey(), entry.getValue());
            }

        } catch (SQLException e) {
            MGiveaway.setPaused(true);
            instance.getLogger().severe("Giveaways paused! Reload the plugin to try again!");
            throw new RuntimeException("Failed to save entries (giveaway: " + giveaway.name() + ")!", e);
        }
    }

    public void saveEntry(String giveawayName, String discordId, String minecraftName) {
        createGiveawayTable(giveawayName);
        try (Connection conn = getConnection()) {
            String sql = "INSERT OR REPLACE INTO `entries-" + giveawayName + "` (discord_id, minecraft_name) VALUES (?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, discordId);
            pstmt.setString(2, minecraftName);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            MGiveaway.setPaused(true);
            instance.getLogger().severe("Giveaways paused! Reload the plugin to try again!");
            throw new RuntimeException("Failed to save entry (giveaway: " + giveawayName + ", discordId: " + discordId + ", minecraftName: " + minecraftName + ")!", e);
        }
    }
}
