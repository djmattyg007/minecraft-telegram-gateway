package fi.felixbade.TelegramGateway;

import java.util.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import static java.util.stream.Collectors.toList;

import org.bukkit.Bukkit;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.raid.RaidFinishEvent;
import org.bukkit.event.raid.RaidStopEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Raid;
import net.md_5.bungee.api.chat.TextComponent;

import fi.felixbade.TelegramBotClient.TelegramBotClient;
import fi.felixbade.TelegramBotClient.APIModel.*;

public class Main extends JavaPlugin implements Listener {
    private static final int NEARBY_DISTANCE = 64;

    private static long telegramChatId;
    private static String telegramToken;

    public static TelegramBotClient telegram;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.telegramChatId = this.getConfig().getLong("telegram-chat-id");
        this.telegramToken = this.getConfig().getString("telegram-token");

        Logger logger = Bukkit.getLogger();

        if (this.telegramToken.equals("987654321:AAxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyz")) {
            logger.warning("\033[31;1mWarning: Telegram access token has not been configured.\033[m");
        }

        this.telegram = new TelegramBotClient(this.telegramToken);

        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            TelegramUpdate[] updates = telegram.getNextUpdates();
            for (TelegramUpdate update : updates) {
                if (update.message == null) {
                    continue;
                }

                TelegramMessage message = update.message;

                long chatId = message.chat.id;
                if (chatId == this.telegramChatId) {
                    TextComponent formatted = Formatting.formatTelegramMessageToMinecraft(message);
                    Bukkit.getServer().spigot().broadcast(formatted);
                } else {
                    logger.warning(String.format("Message from an unknown chat: %d", chatId));

                    // Avoid infinite loops
                    if (message.from.is_bot) {
                        return;
                    }

                    String info = String.format(
                        "Set `telegram-chat-id` to `%d` in `plugins/TelegramGateway/config.yml` " +
                        "if you want to integrate this chat with the Minecraft chat", chatId
                    );
                    telegram.sendMarkdownMessage(chatId, info);
                }
            }
        }, 10, 10);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) {
            return;
        }

        String name = event.getPlayer().getName();
        String message = event.getMessage();
        String messageToTelegram = String.format("<%s> %s", name, message);
        telegram.sendMessage(telegramChatId, messageToTelegram);
    }

    // TODO: Add a "server command" event handler. Parse '/say ' and 'say ' commands.

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // TODO: Change this loop to use map stream
        List<String> onlinePlayers = new ArrayList<String>();
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            onlinePlayers.add(player.getName());
        }
        updatePlayersOnlineInTelegram(onlinePlayers.toArray(new String[onlinePlayers.size()]));

        String name = event.getPlayer().getName();
        String messageToTelegram = String.format("%s joined the game", name);
        telegram.sendMessage(telegramChatId, messageToTelegram);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // TODO: Change this loop to use filter+map stream
        List<String> onlinePlayers = new ArrayList<String>();
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (!player.equals(event.getPlayer())) {
                onlinePlayers.add(player.getName());
            }
        }
        updatePlayersOnlineInTelegram(onlinePlayers.toArray(new String[onlinePlayers.size()]));

        String name = event.getPlayer().getName();
        String messageToTelegram = String.format("%s left the game", name);
        telegram.sendMessage(telegramChatId, messageToTelegram);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        String messageToTelegram = event.getDeathMessage();
        telegram.sendMessage(telegramChatId, messageToTelegram);
    }

    @EventHandler
    public void onRaidTriggered(RaidTriggerEvent event) {
        String playerName = event.getPlayer().getName();
        Raid raid = event.getRaid();
        Location raidLocation = raid.getLocation();
        int badOmenLevel = raid.getBadOmenLevel();

        String messageToTelegram = String.format(
            "%s triggered a raid! Omen level: %d. Location: %d, %d, %d",
            playerName,
            badOmenLevel,
            raidLocation.getBlockX(),
            raidLocation.getBlockY(),
            raidLocation.getBlockZ()
        );
        telegram.sendMessage(telegramChatId, messageToTelegram);
    }

    @EventHandler
    public void onRaidFinished(RaidFinishEvent event) {
        List<Player> winners = event.getWinners();
        List<String> winnerNames = winners.stream()
            .map(winner -> winner.getName())
            .collect(toList());

        String messageToTelegram = String.format(
            "The raid was finished successfully! The winners are: %s",
            String.join(", ", winnerNames)
        );
        telegram.sendMessage(telegramChatId, messageToTelegram);
    }

    @EventHandler
    public void onRaidStopped(RaidStopEvent event) {
        String messageToTelegram = "";
        switch (event.getReason()) {
            case FINISHED:
                // Should already covered by onRaidFinished
                return;
            case NOT_IN_VILLAGE:
                messageToTelegram = "The raid ended because the raid location is no longer a village.";
                break;
            case PEACE:
                messageToTelegram = "The raid ended because the difficulty is now peaceful.";
                break;
            case TIMEOUT:
                messageToTelegram = "The raid ended because it went on for too long.";
                break;
            case UNSPAWNABLE:
                messageToTelegram = "The raid ended because there was nowhere for raiders to spawn.";
                break;
            default:
                messageToTelegram = "The raid ended, but no one knows why.";
                break;
        }
        telegram.sendMessage(telegramChatId, messageToTelegram);
    }

    @EventHandler
    public void onCreeperZapped(CreeperPowerEvent event) {
        if (event.getCause() != CreeperPowerEvent.PowerCause.LIGHTNING) {
            return;
        }
        LightningStrike lightning = event.getLightning();
        if (lightning == null) {
            return;
        }

        String messageToTelegram = "A creeper was struck by lightning!";

        Creeper creeper = event.getEntity();
        List<Entity> nearbyEntities = creeper.getNearbyEntities(NEARBY_DISTANCE, NEARBY_DISTANCE, NEARBY_DISTANCE);
        // TODO: Can this be replaced by a filter+map stream?
        List<String> nearbyPlayerNames = new ArrayList<String>();
        for (Entity nearbyEntity : nearbyEntities) {
            if (nearbyEntity instanceof Player) {
                Player nearbyPlayer = (Player) nearbyEntity;
                nearbyPlayerNames.add(nearbyPlayer.getName());
            }
        }
        if (nearbyPlayerNames.size() > 0) {
            messageToTelegram += String.format(
                " Nearby players: %s",
                String.join(", ", nearbyPlayerNames)
            );
        }

        telegram.sendMessage(telegramChatId, messageToTelegram);
    }

    public void updatePlayersOnlineInTelegram(String[] playerNames) {
        String message = "";
        if (playerNames.length == 0) {
            message = "Nobody online in Minecraft";
        } else {
            message = "Online in Minecraft: " + String.join(", ", playerNames);
        }
        telegram.setChatDescription(telegramChatId, message);
    }
}
