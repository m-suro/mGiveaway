package me.msuro.mGiveaway.discord;

import me.msuro.mGiveaway.Giveaway;
import me.msuro.mGiveaway.MGiveaway;
import me.msuro.mGiveaway.Requirement;
import me.msuro.mGiveaway.utils.ConfigUtil;
import me.msuro.mGiveaway.utils.EmbedUtil;
import me.msuro.mGiveaway.utils.GiveawayManager;
import me.msuro.mGiveaway.utils.TextUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class DiscordListener extends ListenerAdapter {

    private final MGiveaway instance;

    public DiscordListener() {
        this.instance = MGiveaway.getInstance();
        instance.getDiscordUtil().getJDA().addEventListener(this);
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        new DiscordCommand(event);
        instance.getLogger().info("Discord command registered! [" + event.getGuild().getName() + "]");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase(ConfigUtil.getAndValidate(ConfigUtil.COMMAND_NAME))) return;
        try {
            if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_SERVER)) {
                event.replyEmbeds(EmbedUtil.getReplyEmbed(false, ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_NO_PERMISSION))).setEphemeral(true).queue();
                return;
            }
            if (MGiveaway.isPaused()) {
                event.replyEmbeds(EmbedUtil.getReplyEmbed(false, ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_PLUGIN_PAUSED))).setEphemeral(true).queue();
                return;
            }
            OptionMapping nameOption = event.getOption("name");
            OptionMapping prizeOption = event.getOption("prize");
            OptionMapping minecraftPrizeOption = event.getOption("minecraft_prize");
            OptionMapping durationOption = event.getOption("duration");
            OptionMapping winnersOption = event.getOption("winners");
            OptionMapping commandOption = event.getOption("command");
            OptionMapping requirementsOption = event.getOption("requirements");
            if (nameOption == null || prizeOption == null || minecraftPrizeOption == null || durationOption == null || winnersOption == null || commandOption == null) {
                event.replyEmbeds(EmbedUtil.getReplyEmbed(false, "Error creating giveaway! Missing required options.")).setEphemeral(true).queue();
                return;
            }
            String name = nameOption.getAsString();
            String prize = prizeOption.getAsString();
            String minecraftPrize = minecraftPrizeOption.getAsString();
            String duration = durationOption.getAsString();
            int winners = winnersOption.getAsLong() > 0 ? (int) winnersOption.getAsLong() : 1;
            String command = commandOption.getAsString();
            boolean requirements = requirementsOption != null && requirementsOption.getAsBoolean();
            if (!ConfigUtil.createGiveaway(name, prize, minecraftPrize, duration, winners, command, requirements)) {
                String desc = TextUtil.replacePlaceholders(ConfigUtil.getAndValidate(ConfigUtil.MESSAGE_DISCORD_GIVEAWAY_COMMAND_ERROR_ALREADY_EXISTS), Map.of("%name%", name));
                event.replyEmbeds(EmbedUtil.getReplyEmbed(false, desc)).setEphemeral(true).queue();
            } else {
                String desc = TextUtil.replacePlaceholders(ConfigUtil.getAndValidate(ConfigUtil.MESSAGE_DISCORD_GIVEAWAY_COMMAND_SUCCESS_CREATED), Map.of("%name%", name));
                event.replyEmbeds(EmbedUtil.getReplyEmbed(true, desc)).setEphemeral(true).queue();
                GiveawayManager manager = instance.getGiveawayManager();
                Giveaway giveaway = manager.listGiveaways().get(name);
                if (giveaway != null && giveaway.shouldStart()) {
                    manager.startGiveaway(giveaway);
                    instance.getLogger().info("Giveaway started: " + giveaway.name());
                }
            }
        } catch (IllegalArgumentException e) {
            instance.getLogger().severe("Error creating giveaway! " + e.getMessage());
            String error = String.join(",", Arrays.stream(e.getSuppressed()).map(Throwable::getMessage).toArray(String[]::new));
            event.replyEmbeds(EmbedUtil.getReplyEmbed(false, "Error creating giveaway! Please try again.\n" + error)).setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getMessage().getAuthor().getId().equalsIgnoreCase(instance.getDiscordUtil().getJDA().getSelfUser().getId()))
            return;
        if (!event.getComponentId().startsWith("giveaway_")) return;
        if (MGiveaway.isPaused()) {
            event.replyEmbeds(EmbedUtil.getReplyEmbed(false, ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_PLUGIN_PAUSED))).setEphemeral(true).queue();
            return;
        }
        Giveaway giveaway = instance.getGiveaway(event.getComponentId().substring(9));
        if (giveaway == null) return;
        if (giveaway.hasEnded()) {
            event.replyEmbeds(EmbedUtil.getReplyEmbed(false, ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_JOIN_ALREADY_ENDED))).setEphemeral(true).queue();
            return;
        }
        if (giveaway.state() != Giveaway.State.STARTED) {
            event.replyEmbeds(EmbedUtil.getReplyEmbed(false, ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_JOIN_NOT_STARTED))).setEphemeral(true).queue();
            return;
        }
        if (giveaway.entries().containsKey(event.getUser().getId())) {
            String msg = TextUtil.replacePlaceholders(ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_JOIN_ALREADY_JOINED), Map.of("%player%", giveaway.entries().get(event.getUser().getId())));
            event.replyEmbeds(EmbedUtil.getReplyEmbed(false, msg)).setEphemeral(true).queue();
            return;
        }
        Modal modal = instance.getDiscordUtil().getJoinForm(giveaway);
        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!Objects.requireNonNull(event.getMessage()).getAuthor().getId().equalsIgnoreCase(instance.getDiscordUtil().getJDA().getSelfUser().getId()))
            return;
        if (!event.getModalId().startsWith("giveaway_")) return;
        if (MGiveaway.isPaused()) {
            event.replyEmbeds(EmbedUtil.getReplyEmbed(false, ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_COMMAND_ERROR_PLUGIN_PAUSED))).setEphemeral(true).queue();
            return;
        }
        Giveaway giveaway = instance.getGiveaway(event.getModalId().substring(9));
        if (giveaway == null) return;
        // Check if giveaway has ended
        if (giveaway.state() == Giveaway.State.ENDED) {
            event.replyEmbeds(EmbedUtil.getReplyEmbed(false, ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_JOIN_ALREADY_ENDED))).setEphemeral(true).queue();
            return;
        }
        // Check if giveaway has started
        if (giveaway.state() != Giveaway.State.STARTED) {
            event.replyEmbeds(EmbedUtil.getReplyEmbed(false, ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_JOIN_NOT_STARTED))).setEphemeral(true).queue();
            return;
        }
        // Check if the provided nick is already joined
        String nick = Objects.requireNonNull(event.getValue("nick")).getAsString();
        if (giveaway.entries().containsValue(nick)) {
            event.replyEmbeds(EmbedUtil.getReplyEmbed(false, ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_JOIN_NICK_ALREADY_JOINED))).setEphemeral(true).queue();
            return;
        }
        // Check if this discord user is already joined
        if (giveaway.entries().containsKey(event.getUser().getId())) {
            String msg = TextUtil.replacePlaceholders(ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_JOIN_ALREADY_JOINED), Map.of("%player%", giveaway.entries().get(event.getUser().getId())));
            event.replyEmbeds(EmbedUtil.getReplyEmbed(false, msg)).setEphemeral(true).queue();
            return;
        }
        // Check if the player meets the requirements
        instance.getGiveawayManager().checkRequirementsAsync(giveaway, nick, unmetRequirements -> {
            if (unmetRequirements == null) {
                instance.getLogger().warning("Error during asynchronous player lookup for " + nick + " in giveaway " + giveaway.name());
                event.replyEmbeds(EmbedUtil.getReplyEmbed(false, "Error checking requirements. Please try again.")).setEphemeral(true).queue(); // Generic error message
                return;
            }

            if (unmetRequirements.isEmpty()) {
                // Player meets all requirements - proceed with entry
                instance.getGiveawayManager().addEntry(giveaway, event.getUser().getId(), nick);
                String msg = TextUtil.replacePlaceholders(ConfigUtil.getAndValidate("messages.discord.giveaway_join.joined"), Map.of("%player%", nick));
                event.replyEmbeds(EmbedUtil.getReplyEmbed(true, msg)).setEphemeral(true).queue();
                ConfigUtil.updateStat(event.getUser().getId(), 1);
                MessageEmbed embed = EmbedUtil.getEmbedBuilderFromConfig(giveaway, 1).build();
                event.getChannel().editMessageEmbedsById(giveaway.embedId(), embed).queue();

            } else {
                if (unmetRequirements.get(0).type() == Requirement.Type.NULLPLAYER) {
                    event.replyEmbeds(EmbedUtil.getReplyEmbed(false, ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_REQUIREMENT_ERROR_NULL_PLAYER))).setEphemeral(true).queue();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                int i = 0;
                for (Requirement requirement : unmetRequirements) {
                    if (requirement.getFormatted() == null || requirement.getFormatted().isEmpty() || requirement.getFormatted().equalsIgnoreCase("null"))
                        continue;
                    if (i < unmetRequirements.size() - 1) sb.append(requirement.getFormatted()).append(", ");
                    else sb.append(requirement.getFormatted());
                    i++;
                }
                String msg = TextUtil.replacePlaceholders(ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_DISCORD_GIVEAWAY_REQUIREMENT_ERROR_REQUIREMENTS_NOT_MET), Map.of("%requirements%", sb.toString()));
                event.replyEmbeds(EmbedUtil.getReplyEmbed(false, msg)).setEphemeral(true).queue();
            }
        });
    }
}
