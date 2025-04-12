package me.msuro.mGiveaway.utils;

import me.msuro.mGiveaway.MGiveaway;
import me.msuro.mGiveaway.Giveaway;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.Objects;

public class DiscordUtil {

    private final MGiveaway instance;

    private JDA jda;

    public DiscordUtil() {
        instance = MGiveaway.getInstance();
    }

    public JDA getJDA() {
        return jda;
    }

    public void build() {
        String token = ConfigUtil.getAndValidate(ConfigUtil.TOKEN);
        // "XXX" is the default value for all config values
        if (token.equalsIgnoreCase("XXX")) {
            instance.getLogger().severe("Bot token not set!");
            MGiveaway.setPaused(true);
            instance.getLogger().severe(ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_PLUGIN_PAUSED));
            return;
        }
        try {
            jda = JDABuilder.createDefault(token).setActivity(Activity.playing("with giveaways")).setStatus(OnlineStatus.ONLINE).enableIntents(GatewayIntent.MESSAGE_CONTENT).build();
        } catch (InvalidTokenException e) {
            instance.getLogger().severe("Failed to start Discord bot " + e.getMessage());
            MGiveaway.setPaused(true);
            instance.getLogger().severe(ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_PLUGIN_PAUSED));
            return;
        }
        jda.getPresence().setActivity(getActivity());
        jda.getPresence().setStatus(getStatus());
        logActivity();
        logStatus();
    }

    private Activity getActivity() {
        String activity = ConfigUtil.getAndValidate(ConfigUtil.ACTIVITY);
        String activityText = ConfigUtil.getAndValidate(ConfigUtil.ACTIVITY_TEXT);
        String activityUrl = ConfigUtil.getAndValidate(ConfigUtil.ACTIVITY_URL);

        return switch (activity) {
            case "PLAYING" -> Activity.playing(activityText);
            case "WATCHING" -> Activity.watching(activityText);
            case "LISTENING" -> Activity.listening(activityText);
            case "STREAMING" -> Activity.streaming(activityText, activityUrl);
            default -> Activity.playing("Minecraft");
        };
    }

    private OnlineStatus getStatus() {
        String status = ConfigUtil.getAndValidate(ConfigUtil.STATUS);
        return switch (status) {
            case "IDLE", "AFK" -> OnlineStatus.IDLE;
            case "DO_NOT_DISTURB", "DND" -> OnlineStatus.DO_NOT_DISTURB;
            case "INVISIBLE", "OFFLINE" -> OnlineStatus.INVISIBLE;
            default -> OnlineStatus.ONLINE;
        };
    }

    public void logActivity() {
        instance.getServer().getConsoleSender().sendMessage(TextUtil.process("[mGiveaway] Activity set to: &7" + Objects.requireNonNull(jda.getPresence().getActivity()).getName()));
    }

    public void logStatus() {
        String color = switch (jda.getPresence().getStatus()) {
            case ONLINE -> "&a&l";
            case IDLE -> "&e&l";
            case DO_NOT_DISTURB -> "&c&l";
            default -> "&7&l";
        };
        instance.getServer().getConsoleSender().sendMessage(TextUtil.process("[mGiveaway] Status set to: " + color + jda.getPresence().getStatus()));
    }



    public ItemComponent getButton(Giveaway giveaway) {
        String type = ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_BUTTON_JOIN_BUTTON_TYPE);
        String text = ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_BUTTON_JOIN_BUTTON_TEXT);
        String emoji = ConfigUtil.getOptional(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_BUTTON_JOIN_BUTTON_EMOJI);
        if(emoji == null || emoji.isEmpty()) {
            return switch (type) {
                case "SECONDARY" -> Button.secondary("giveaway_" + giveaway.name(), text);
                case "SUCCESS" -> Button.success("giveaway_" + giveaway.name(), text);
                case "DANGER" -> Button.danger("giveaway_" + giveaway.name(), text);
                default -> Button.primary("giveaway_" + giveaway.name(), text);
            };
        } else {
            Emoji emo = Emoji.fromUnicode(emoji);
            return switch (type) {
                case "SECONDARY" -> Button.secondary("giveaway_" + giveaway.name(), text).withEmoji(emo);
                case "SUCCESS" -> Button.success("giveaway_" + giveaway.name(), text).withEmoji(emo);
                case "DANGER" -> Button.danger("giveaway_" + giveaway.name(), text).withEmoji(emo);
                default -> Button.primary("giveaway_" + giveaway.name(), text).withEmoji(emo);
            };
        }
    }

    public Modal getJoinForm(Giveaway giveaway) {
        ItemComponent ic = TextInput.create("nick", ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_MODAL_NICK_INPUT_QUESTION), TextInputStyle.SHORT)
                .setPlaceholder(ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_MODAL_NICK_INPUT_PLACEHOLDER))
                .setMinLength(3)
                .setMaxLength(16)
                .setRequired(true)
                .build();
        return Modal.create("giveaway_" + giveaway.name(), ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_MODAL_JOIN_MODAL_TITLE))
                .addActionRow(ic)
                .build();
    }

}
