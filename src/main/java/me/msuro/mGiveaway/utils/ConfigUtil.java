package me.msuro.mGiveaway.utils;

import me.msuro.mGiveaway.MGiveaway;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigUtil {

    private static MGiveaway instance;
    private static YamlConfiguration config;

    public ConfigUtil() {
        instance = MGiveaway.getInstance();

        instance.saveDefaultConfig();
        config = YamlConfiguration.loadConfiguration(new File(instance.getDataFolder(), "config.yml"));

        if (!new File(instance.getDataFolder(), "config.yml").exists()) {
            instance.saveResource("config.yml", false);
            instance.getLogger().info("Config file created!");
        } else {
            if (config.getKeys(false).isEmpty()) {
                instance.getLogger().warning("Config file is empty! Loading default config...");
                instance.saveResource("config.yml", true);
                reloadConfig();
            } else {
                instance.getLogger().info("Config file loaded successfully!");
            }
        }
        if (getOptional(CONFIG_VERSION) == null) {
            config.set(CONFIG_VERSION, "0.1");
            saveConfig();
        }
        updateConfig();

    }

    public static void saveConfig() {
        try {
            config.save(new File(instance.getDataFolder(), "config.yml"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to save config file!", e);
        }
        instance.reloadConfig();
    }

    public void saveConfig(YamlConfiguration config) {
        try {
            config.save(new File(instance.getDataFolder(), "config.yml"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to save config file!", e);
        }
        ConfigUtil.config = config;
        instance.reloadConfig();
    }

    public static YamlConfiguration getConfig() {
        return config;
    }

    public static void reloadConfig() {
        instance.reloadConfig();
        config = YamlConfiguration.loadConfiguration(new File(instance.getDataFolder(), "config.yml"));
    }

    public static String getPrefix() {
        return getOrDefault(PREFIX);
    }

    /**
     * Gets a value from the config file and validates it.
     * Reports a warning if the value is set to default "XXX".
     * Returns "null" if the value is not set. (Sends an error message)
     *
     * @param key The key of the value to get
     * @return The value if it exists, "null" otherwise
     */
    public static String getAndValidate(String key) {
        String value = config.getString(key);
        if (value == null) {
            instance.getLogger().severe("Config value " + key + " not found!");
            MGiveaway.setPaused(true);
            instance.getLogger().severe("Giveaways paused! Reload the plugin to try again!");
            value = "null";
        } else if (value.equals("XXX")) {
            instance.getLogger().warning("Config value " + key + " not set!");
        }
        return value;
    }

    /**
     * Gets a value from the default config file if it is not set in the current config file.
     * Useful for getting plugin messages that are configurable but required for the plugin to work.
     *
     * @param key The key of the value to get
     * @return The value if it exists, "null" otherwise
     */
    public static String getOrDefault(String key) {
        String value = config.getString(key);
        if (value == null) {
            InputStream defaultConfigStream = instance.getResource("config.yml");
            if (defaultConfigStream == null) {
                instance.getLogger().severe("Default config not found!");
                return "null";
            }
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
            value = defaultConfig.getString(key);
            if (value == null) {
                instance.getLogger().severe("Default config value " + key + " not found!");
                return "null";
            }
        }
        return value;
    }

    public static String getOptional(String key) {
        return config.getString(key);
    }

    public static Integer getInt(String key) {
        int value = config.getInt(key, -2147483648);
        if (value == -2147483648) {
            instance.getLogger().severe("Config value " + key + " not found!");
        }
        return value;
    }

    /**
     * Updates the stats for a user
     *
     * @param userId The user's ID
     * @param type   1 for entered, 2 for won
     */
    public static void updateStat(String userId, int type) {
        String path = (type == 2 ? STAT_WON : STAT_ENTERED).replace("%s", userId);
        int value = config.getInt(path, 0);
        config.set(path, value + 1);
        saveConfig();
    }

    public static boolean createGiveaway(String name, String prize, String minecraftPrize, String duration, int winners, String command, boolean requirements) throws IllegalArgumentException {
        if (getOptional("giveaways." + name + ".settings.end_time") != null) return false;
        config.createSection("giveaways." + name);
        long durationInSeconds = parseDuration(duration);
        LocalDateTime endTime = LocalDateTime.now().plusSeconds(durationInSeconds);
        config.set("giveaways." + name + ".settings.end_time", endTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        config.set("giveaways." + name + ".settings.winners", winners);
        config.set("giveaways." + name + ".settings.commands", List.of(command));
        config.set("giveaways." + name + ".settings.started", false);
        config.set("giveaways." + name + ".settings.prize_formatted", prize);
        config.set("giveaways." + name + ".settings.minecraft_prize", minecraftPrize);

        if (!requirements) config.set(ConfigUtil.FORCE_START.replace("%s", name), true);

        saveConfig();
        reloadConfig();
        return true;
    }

    Map<String, Long> timeMultipliers = new HashMap<>();


    /**
     * Parses a duration string and returns the total duration in seconds.
     * The duration string should be in the format "1mo 2w 7d 5m 3s".
     * The supported units are months (mo), weeks (w), days (d), minutes (m), and seconds (s).
     *
     * @param duration The duration string to parse
     * @return The total duration in seconds
     */
    public static long parseDuration(String duration) {
        if (duration == null || duration.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration must not be null or empty.");
        }

        duration = duration.trim().toLowerCase();

        Map<String, Long> timeMultipliers = new HashMap<>();
        timeMultipliers.put("mo", 30L * 24 * 60 * 60);
        timeMultipliers.put("w", 7L * 24 * 60 * 60);
        timeMultipliers.put("d", 24L * 60 * 60);
        timeMultipliers.put("h", 60L * 60);
        timeMultipliers.put("m", 60L);
        timeMultipliers.put("s", 1L);

        long totalSeconds = 0;

        // Improved regex to ensure only valid unit characters and capture groups
        Pattern pattern = Pattern.compile("^\\s*(\\d+(?:mo|w|d|h|m|s))(\\s+\\d+(?:mo|w|d|h|m|s))*\\s*$");
        Matcher matcher = pattern.matcher(duration);

        if (!matcher.matches()) {
            IllegalArgumentException e = new IllegalArgumentException("Invalid duration format: " + duration);
            e.addSuppressed(new IllegalArgumentException("Valid format: 1mo 2w 3d 4h 5m 6s"));
            e.addSuppressed(new IllegalArgumentException("Invalid format: " + duration));
            throw e;
        }

        Pattern blockPattern = Pattern.compile("(\\d+)(mo|w|d|h|m|s)");
        Matcher blockMatcher = blockPattern.matcher(duration);

        while (blockMatcher.find()) {
            long value = Long.parseLong(blockMatcher.group(1));
            String unit = blockMatcher.group(2);


            if (!timeMultipliers.containsKey(unit)) {
                throw new IllegalArgumentException("Unknown time unit: " + unit);
            }

            try {
                totalSeconds = Math.addExact(totalSeconds, Math.multiplyExact(value, timeMultipliers.get(unit)));
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("Duration value too large: " + value + unit, ex);
            }


        }

        return totalSeconds;
    }


    public static void updateConfig() {
        String version = getAndValidate(CONFIG_VERSION);
        if(isLowerThan(version, "0.5")) {
            // Discord command options description
            config.set(DISCORD_OPTIONS_NAME, getOrDefault(DISCORD_OPTIONS_NAME));
            config.set(DISCORD_OPTIONS_PRIZE, getOrDefault(DISCORD_OPTIONS_PRIZE));
            config.set(DISCORD_OPTIONS_MINECRAFT_PRIZE, getOrDefault(DISCORD_OPTIONS_MINECRAFT_PRIZE));
            config.set(DISCORD_OPTIONS_DURATION, getOrDefault(DISCORD_OPTIONS_DURATION));
            config.set(DISCORD_OPTIONS_WINNERS, getOrDefault(DISCORD_OPTIONS_WINNERS));
            config.set(DISCORD_OPTIONS_COMMAND, getOrDefault(DISCORD_OPTIONS_COMMAND));
            config.set(DISCORD_OPTIONS_REQUIREMENTS, getOrDefault(DISCORD_OPTIONS_REQUIREMENTS));
            // Giveaway join messages
            config.set(MESSAGES_DISCORD_GIVEAWAY_JOIN_ALREADY_JOINED, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_JOIN_ALREADY_JOINED));
            config.set(MESSAGES_DISCORD_GIVEAWAY_JOIN_NICK_ALREADY_JOINED, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_JOIN_NICK_ALREADY_JOINED));
            config.set(MESSAGES_DISCORD_GIVEAWAY_JOIN_JOINED, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_JOIN_JOINED));
            config.set(MESSAGES_DISCORD_GIVEAWAY_JOIN_NOT_STARTED, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_JOIN_NOT_STARTED));
            config.set(MESSAGES_DISCORD_GIVEAWAY_JOIN_ALREADY_ENDED, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_JOIN_ALREADY_ENDED));
            // Giveaway command error messages
            config.set(MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_NO_PERMISSION, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_NO_PERMISSION));
            config.set(MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_PLUGIN_PAUSED, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_PLUGIN_PAUSED));
            // Giveaway requirement error messages
            config.set(MESSAGES_DISCORD_GIVEAWAY_REQUIREMENT_ERROR_NULL_PLAYER, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_REQUIREMENT_ERROR_NULL_PLAYER));
            config.set(MESSAGES_DISCORD_GIVEAWAY_REQUIREMENT_ERROR_REQUIREMENTS_NOT_MET, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_REQUIREMENT_ERROR_REQUIREMENTS_NOT_MET));
            // Giveaway modal messages
            config.set(MESSAGES_DISCORD_GIVEAWAY_MODAL_JOIN_MODAL_TITLE, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_MODAL_JOIN_MODAL_TITLE));
            config.set(MESSAGES_DISCORD_GIVEAWAY_MODAL_NICK_INPUT_QUESTION, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_MODAL_NICK_INPUT_QUESTION));
            config.set(MESSAGES_DISCORD_GIVEAWAY_MODAL_NICK_INPUT_PLACEHOLDER, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_MODAL_NICK_INPUT_PLACEHOLDER));
            // Giveaway button messages
            config.set(MESSAGES_DISCORD_GIVEAWAY_BUTTON_JOIN_BUTTON_TYPE, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_BUTTON_JOIN_BUTTON_TYPE));
            config.set(MESSAGES_DISCORD_GIVEAWAY_BUTTON_JOIN_BUTTON_TEXT, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_BUTTON_JOIN_BUTTON_TEXT));
            config.set(MESSAGES_DISCORD_GIVEAWAY_BUTTON_JOIN_BUTTON_EMOJI, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_BUTTON_JOIN_BUTTON_EMOJI));
            // In-game messages
            config.set(MESSAGES_IN_GAME_NO_PERMISSION, getOrDefault(MESSAGES_IN_GAME_NO_PERMISSION));
            // Embed titles
            config.set(MESSAGES_DISCORD_GIVEAWAY_EMBED_TITLE_SUCCESS, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_EMBED_TITLE_SUCCESS));
            config.set(MESSAGES_DISCORD_GIVEAWAY_EMBED_TITLE_ERROR, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_EMBED_TITLE_ERROR));
            // Log embed
            config.set(GIVEAWAY_LOG_EMBED, getOrDefault(GIVEAWAY_LOG_EMBED));
            // Config version
            config.set(CONFIG_VERSION, "0.5");
            instance.getLogger().info("Config updated to version 0.5!");
        }
        if(isLowerThan(version, "0.7")) {
            config.set(MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_MISSING_REQUIRED_ARGS, getOrDefault(MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_MISSING_REQUIRED_ARGS));
            config.set(UPDATE_AVAILABLE, getOrDefault(UPDATE_AVAILABLE));
            config.set(UPDATE_AVAILABLE_HOVER, getOrDefault(UPDATE_AVAILABLE_HOVER));
            // Config version
            config.set(CONFIG_VERSION, "0.7");
            instance.getLogger().info("Config updated to version 0.7!");
        }
        if(isLowerThan(version, "0.7.2")) {
            config.set(MESSAGE_DISCORD_GIVEAWAY_COMMAND_ERROR_ALREADY_EXISTS, getOrDefault(MESSAGE_DISCORD_GIVEAWAY_COMMAND_ERROR_ALREADY_EXISTS));
            config.set(MESSAGE_DISCORD_GIVEAWAY_COMMAND_SUCCESS_CREATED, getOrDefault(MESSAGE_DISCORD_GIVEAWAY_COMMAND_SUCCESS_CREATED));
            // Config version
            config.set(CONFIG_VERSION, "0.7.2");
            instance.getLogger().info("Config updated to version 0.7.2!");
        }
        if (isLowerThan(version, "0.7.3")) {
            config.set(GIVEAWAY_INFO_PERSONAL_ON_JOIN, getOrDefault(GIVEAWAY_INFO_PERSONAL_ON_JOIN));
            config.set(GIVEAWAY_INFO_GLOBAL_ON_START, getOrDefault(GIVEAWAY_INFO_GLOBAL_ON_START));
            config.set(GIVEAWAY_INFO_GLOBAL_ON_END, getOrDefault(GIVEAWAY_INFO_GLOBAL_ON_END));
            // Config version
            config.set(CONFIG_VERSION, "0.7.3");
            instance.getLogger().info("Config updated to version 0.7.3!");
        }
        if (isLowerThan(version, "0.7.4")) {
            config.set(MESSAGES_GLOBAL_NO_WINNERS, getOrDefault(MESSAGES_GLOBAL_NO_WINNERS));
            // Config version
            config.set(CONFIG_VERSION, "0.7.4");
            instance.getLogger().info("Config updated to version 0.7.4!");
        }
        saveConfig();
        reloadConfig();
     }

    /**
     * Compares two version strings to determine if the configVersion is lower than the currentVersion.
     * Version strings are expected to be in the format "major.minor.patch" or similar,
     * where parts are separated by dots.
     * <p>
     * The comparison is performed part by part, from left to right. If a version string has fewer parts
     * than the other, the missing parts are treated as 0.
     * For example:
     * <ul>
     *     <li>"0.5" is lower than "0.6"</li>
     *     <li>"0.6" is lower than "0.6.1"</li>
     *     <li>"0.5" is lower than "0.5.1"</li>
     *     <li>"0.5.1" is NOT lower than "0.5"</li>
     *     <li>"1.0" is NOT lower than "0.9"</li>
     *     <li>"0.9" is lower than "1.0"</li>
     * </ul>
     *
     * @param configVersion  The version string to compare against the current version.
     * @param currentVersion The current version string.
     * @return {@code true} if the {@code configVersion} is lower than the {@code currentVersion}, {@code false} otherwise.
     *         Returns {@code false} if the versions are equal or if the {@code configVersion} is higher.
     * @throws NumberFormatException if any version part is not a valid integer.
     */
    public static boolean isLowerThan(String configVersion, String currentVersion) {
        String[] configVersionParts = configVersion.split("\\.");
        String[] currentVersionParts = currentVersion.split("\\.");

        int length = Math.max(configVersionParts.length, currentVersionParts.length);
        for (int i = 0; i < length; i++) {
            int configVersionPart = 0;
            if (i < configVersionParts.length) {
                configVersionPart = Integer.parseInt(configVersionParts[i]);
            }

            int currentVersionPart = 0;
            if (i < currentVersionParts.length) {
                currentVersionPart = Integer.parseInt(currentVersionParts[i]);
            }

            if (configVersionPart < currentVersionPart) {
                return true;
            } else if (configVersionPart > currentVersionPart) {
                return false;
            }
        }
        return false;
    }

    public static final String CONFIG_VERSION = "config_version";
    public static final String PREFIX = "prefix";
    public static final String BROADCAST_INTERVAL = "broadcast_interval";
    public static final String BROADCAST_MESSAGE = "broadcast_message";

    public static final String TOKEN = "discord.bot.token";

    public static final String UPDATE_AVAILABLE = "messages.in_game.update_available";
    public static final String UPDATE_AVAILABLE_HOVER = "messages.in_game.update_available_hover";

    public static final String MESSAGES_GLOBAL_NO_WINNERS = "messages.global.no_winners";

    public static final String GIVEAWAY_EMBED = "discord.bot.giveaway_embed";
    public static final String GIVEAWAY_END_EMBED = "discord.bot.giveaway_end_embed";
    public static final String GIVEAWAY_LOG_EMBED = "discord.bot.giveaway_log_embed";
    public static final String GIVEAWAY_CHANNEL = "discord.bot.giveaway_channel";

    public static final String LOG_EMBED_CHANNEL = "discord.bot.log_embed_channel";

    public static final String ACTIVITY = "discord.bot.activity";
    public static final String ACTIVITY_TEXT = "discord.bot.activity_text";
    public static final String ACTIVITY_URL = "discord.bot.activity_url";
    public static final String STATUS = "discord.bot.status";

    public static final String COMMAND_NAME = "discord.bot.command.name";
    public static final String COMMAND_DESCRIPTION = "discord.bot.command.description";

    public static final String STAT_ENTERED = "stats.%s.entered";
    public static final String STAT_WON = "stats.%s.won";

    public static final String SCH_START = "giveaways.%s.settings.scheduled_start";
    public static final String END_TIME = "giveaways.%s.settings.end_time";
    public static final String WINNERS = "giveaways.%s.settings.winners";
    public static final String COMMANDS = "giveaways.%s.settings.commands";
    public static final String STARTED = "giveaways.%s.settings.started";
    public static final String PRIZE_FORMATTED = "giveaways.%s.settings.prize_formatted";
    public static final String MINECRAFT_PRIZE = "giveaways.%s.settings.minecraft_prize";
    public static final String EMBED_ID = "giveaways.%s.settings.embed_id";
    public static final String FORCE_START = "giveaways.%s.settings.forcestart";
    public static final String ENDED = "giveaways.%s.ended";

    public static final String REQUIREMENT_PERMISSION = "giveaways.%s.requirements.permission";
    public static final String REQUIREMENT_GROUP = "giveaways.%s.requirements.group";
    public static final String REQUIREMENT_PLACEHOLDER = "giveaways.%s.requirements.placeholder";
    // %t = type, %r = requirement
    public static final String REQUIREMENT_FORMATTED = "giveaways.%s.requirements.%t.%r.formatted";

    public static final String DISCORD_OPTIONS_NAME = "discord.bot.command.options.name";
    public static final String DISCORD_OPTIONS_PRIZE = "discord.bot.command.options.prize";
    public static final String DISCORD_OPTIONS_MINECRAFT_PRIZE = "discord.bot.command.options.minecraft_prize";
    public static final String DISCORD_OPTIONS_DURATION = "discord.bot.command.options.duration";
    public static final String DISCORD_OPTIONS_WINNERS = "discord.bot.command.options.winners";
    public static final String DISCORD_OPTIONS_COMMAND = "discord.bot.command.options.command";
    public static final String DISCORD_OPTIONS_REQUIREMENTS = "discord.bot.command.options.requirements";

    public static final String MESSAGES_DISCORD_GIVEAWAY_JOIN_ALREADY_JOINED = "messages.discord.giveaway_join.already_joined";
    public static final String MESSAGES_DISCORD_GIVEAWAY_JOIN_NICK_ALREADY_JOINED = "messages.discord.giveaway_join.nick_already_joined";
    public static final String MESSAGES_DISCORD_GIVEAWAY_JOIN_JOINED = "messages.discord.giveaway_join.joined";
    public static final String MESSAGES_DISCORD_GIVEAWAY_JOIN_NOT_STARTED = "messages.discord.giveaway_join.not_started";
    public static final String MESSAGES_DISCORD_GIVEAWAY_JOIN_ALREADY_ENDED = "messages.discord.giveaway_join.already_ended";

    public static final String MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_NO_PERMISSION = "messages.discord.giveaway_command_error.no_permission";
    public static final String MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_PLUGIN_PAUSED = "messages.discord.giveaway_command_error.plugin_paused";
    public static final String MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_MISSING_REQUIRED_ARGS = "messages.discord.giveaway_command_error.missing_required_args";
    public static final String MESSAGE_DISCORD_GIVEAWAY_COMMAND_ERROR_ALREADY_EXISTS = "messages.discord.giveaway_command_error.already_exists";

    public static final String MESSAGE_DISCORD_GIVEAWAY_COMMAND_SUCCESS_CREATED = "messages.discord.giveaway_command_success.created";

    public static final String MESSAGES_DISCORD_GIVEAWAY_REQUIREMENT_ERROR_NULL_PLAYER = "messages.discord.giveaway_requirement_error.null_player";
    public static final String MESSAGES_DISCORD_GIVEAWAY_REQUIREMENT_ERROR_REQUIREMENTS_NOT_MET = "messages.discord.giveaway_requirement_error.requirements_not_met";

    public static final String MESSAGES_DISCORD_GIVEAWAY_MODAL_JOIN_MODAL_TITLE = "messages.discord.giveaway_modal.join_modal_title";
    public static final String MESSAGES_DISCORD_GIVEAWAY_MODAL_NICK_INPUT_QUESTION = "messages.discord.giveaway_modal.nick_input_question";
    public static final String MESSAGES_DISCORD_GIVEAWAY_MODAL_NICK_INPUT_PLACEHOLDER = "messages.discord.giveaway_modal.nick_input_placeholder";

    public static final String MESSAGES_DISCORD_GIVEAWAY_BUTTON_JOIN_BUTTON_TYPE = "messages.discord.giveaway_button.join_button_type";
    public static final String MESSAGES_DISCORD_GIVEAWAY_BUTTON_JOIN_BUTTON_TEXT = "messages.discord.giveaway_button.join_button_text";
    public static final String MESSAGES_DISCORD_GIVEAWAY_BUTTON_JOIN_BUTTON_EMOJI = "messages.discord.giveaway_button.join_button_emoji";

    public static final String MESSAGES_IN_GAME_NO_PERMISSION = "messages.in_game.no_permission";

    public static final String MESSAGES_DISCORD_GIVEAWAY_EMBED_TITLE_SUCCESS = "messages.discord.embed_title.success";
    public static final String MESSAGES_DISCORD_GIVEAWAY_EMBED_TITLE_ERROR = "messages.discord.embed_title.error";

    public static final String GIVEAWAY_INFO_PERSONAL_ON_JOIN = "messages.in_game.giveaway_info_personal.on_join";
    public static final String GIVEAWAY_INFO_GLOBAL_ON_START = "messages.in_game.giveaway_info_global.on_start";
    public static final String GIVEAWAY_INFO_GLOBAL_ON_END = "messages.in_game.giveaway_info_global.on_end";

}
