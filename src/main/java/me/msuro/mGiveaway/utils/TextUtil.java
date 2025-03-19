package me.msuro.mGiveaway.utils;

import me.msuro.mGiveaway.MGiveaway;
import me.msuro.mGiveaway.Giveaway;
import me.msuro.mGiveaway.utils.colors.ColorAPI;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtil {

    private static MGiveaway instance;
    public static String prefix;

    public static void setInstance(MGiveaway instance) {
        TextUtil.instance = instance;
    }

    /**
     * Replaces color codes in a string with the corresponding color.
     *
     * @param text The text to colorize
     * @return The colorized text
     */
    public static String color(String text) {
        return text == null ? null : ColorAPI.process(text);
    }

    public static String process(String text) {
        if (text == null || text.isBlank()) return "null";
        text = replacePlaceholders(text, Map.of("%prefix%", prefix));
        text = color(text);
        return text;
    }

    /**
     * Replaces & with § and &#HHHHHH with §x§H§H§H§H§H§H
     * Used for UpdateChecker messages which require {@link net.md_5.bungee.api.chat.BaseComponent} to be sent
     * @param text The text to convert
     * @return The converted text
     */
    public static String toMinecraftHex(String text) {
        Pattern pattern = Pattern.compile("&#([0-9A-Fa-f]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = "§x" + "§" + hex.charAt(0)
                    + "§" + hex.charAt(1)
                    + "§" + hex.charAt(2)
                    + "§" + hex.charAt(3)
                    + "§" + hex.charAt(4)
                    + "§" + hex.charAt(5);
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString().replace("&", "§");
    }

    public static String replaceJsonPlaceholders(String json, Giveaway giveaway) {
        // GLOBAL REPLACEMENTS - PLACEHOLDERS: {TIME-LEFT}, {ENTRIES}, {WIN-COUNT}, {PRIZE}, {END-TIME}
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        json = replacePlaceholders(json, Map.of(
                "{TIME-LEFT}", "<t:" + giveaway.endTimeParsed().atZone(ZoneId.systemDefault()).toEpochSecond() + ":R>",
                "{END-TIME}", giveaway.endTime(),
                "{ENTRIES}", String.valueOf(giveaway.entries().size()),
                "{WIN-COUNT}", giveaway.winCount().toString(),
                "{PRIZE}", giveaway.prize()
        ));

        HashMap<String, String> entries = giveaway.entries();

        // END GIVEAWAY EMBED REPLACEMENTS - PLACEHOLDER: {WINNERS}
        if (giveaway.state() == Giveaway.State.ENDED) {
            StringBuilder sb = new StringBuilder(" ");
            for (String key : giveaway.winners().keySet()) {
                sb.append("<@")
                        .append(key)
                        .append(">" + ": ")
                        .append(entries.get(key).replace("_", "\\\\_"))
                        .append("\\n");
            }
            if (sb.length() > 2) {
                sb.delete(sb.length() - 2, sb.length());
                json = replacePlaceholders(json, Map.of("{WINNERS}", sb.toString()));
            } else {
                json = replacePlaceholders(json, Map.of("{WINNERS}", ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_GLOBAL_NO_WINNERS)));
            }
        }

        // LOG EMBED GIVEAWAY REPLACEMENTS - PLACEHOLDERS: {GIVEAWAY-NAME}, {ENTRIES-COUNT}, {PRIZE}, {COMMANDS}, {WINNERS-MENTIONS}, {ENTRIES-LIST}
        StringBuilder sb = new StringBuilder(" ");
        for (String key : giveaway.winners().keySet()) {
            sb.append("<@").append(key).append("> ");
        }
        json = replacePlaceholders(json, Map.of(
                "{GIVEAWAY-NAME}", giveaway.name(),
                "{ENTRIES-COUNT}", String.valueOf(giveaway.entries().size()),
                "{PRIZE}", giveaway.prize(),
                "{COMMANDS}", String.join(",", giveaway.prizeCommands()),
                "{WINNERS-MENTIONS}", sb.toString()
        ));
        sb = new StringBuilder(" ");
        for (String entry : entries.keySet()) {
            sb.append("<@").append(entry).append(">: ").append(entries.get(entry).replace("_", "\\\\_")).append("\\n");
        }
        json = replacePlaceholders(json, Map.of("{ENTRIES-LIST}", sb.toString()));

        return json;
    }

    public static String replacePlaceholders(String text, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }
}
