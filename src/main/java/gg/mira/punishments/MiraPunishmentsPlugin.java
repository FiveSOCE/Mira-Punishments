package gg.mira.punishments;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

public final class MiraPunishmentsPlugin extends JavaPlugin implements Listener {
    private static final String PREFIX = "&5&lMira &8>> &r";
    private PunishmentService service;

    @Override
    public void onEnable() {
        service = new PunishmentService(this);
        getServer().getServicesManager().register(PunishmentApi.class, service, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        service.save();
        getServer().getServicesManager().unregisterAll(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Punishment active = service.active(event.getPlayer().getUniqueId(), PunishmentType.BAN);
        if (active != null) event.getPlayer().kick(Component.text("You are banned: " + active.reason() + service.untilSuffix(active)));
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Punishment active = service.active(event.getPlayer().getUniqueId(), PunishmentType.MUTE);
        if (active != null) {
            event.setCancelled(true);
            msg(event.getPlayer(), "&cYou are muted: &f" + active.reason() + service.untilSuffix(active));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("history")) return history(sender, args);
        if (name.equals("warn")) return issue(sender, args, PunishmentType.WARN);
        if (name.equals("mute")) return issue(sender, args, PunishmentType.MUTE);
        if (name.equals("miraban")) return issue(sender, args, PunishmentType.BAN);
        if (name.equals("unmute")) return revoke(sender, args, PunishmentType.MUTE);
        if (name.equals("miraunban")) return revoke(sender, args, PunishmentType.BAN);
        if (name.equals("punish")) return punish(sender, args);
        return false;
    }

    private boolean punish(CommandSender sender, String[] args) {
        if (args.length == 0) { msg(sender, "&6MiraPunishments &7/punish <history|note|reload> ..."); return true; }
        if (args[0].equalsIgnoreCase("history")) return history(sender, Arrays.copyOfRange(args, 1, args.length));
        if (args[0].equalsIgnoreCase("note")) {
            if (args.length < 3) { msg(sender, "&cUsage: /punish note <player> <note>"); return true; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            String note = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            service.add(target.getUniqueId(), target.getName(), PunishmentType.NOTE, sender.getName(), note, 0L);
            msg(sender, "&aStaff note added.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            service.reload();
            msg(sender, "&aMiraPunishments reloaded.");
            return true;
        }
        msg(sender, "&cUnknown subcommand.");
        return true;
    }

    private boolean issue(CommandSender sender, String[] args, PunishmentType type) {
        if (args.length < 2) {
            msg(sender, "&cUsage: /" + type.name().toLowerCase(Locale.ROOT) + " <player> " + (type == PunishmentType.WARN ? "<reason>" : "<duration|perm> <reason>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        int reasonStart = 1;
        long expiresAt = 0L;
        if (type == PunishmentType.BAN || type == PunishmentType.MUTE) {
            if (args.length < 3) { msg(sender, "&cDuration and reason are required."); return true; }
            long duration = service.parseDuration(args[1]);
            if (duration == Long.MIN_VALUE) { msg(sender, "&cInvalid duration. Examples: 30m, 12h, 7d, perm"); return true; }
            expiresAt = duration <= 0 ? 0L : System.currentTimeMillis() + duration;
            reasonStart = 2;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length));
        Punishment punishment = service.add(target.getUniqueId(), target.getName(), type, sender.getName(), reason, expiresAt);
        Player online = target.getPlayer();
        if (online != null) {
            if (type == PunishmentType.BAN) online.kick(Component.text("You are banned: " + reason + service.untilSuffix(punishment)));
            else if (type == PunishmentType.WARN) msg(online, "&eWarning: &f" + reason);
            else if (type == PunishmentType.MUTE) msg(online, "&cYou have been muted: &f" + reason + service.untilSuffix(punishment));
        }
        msg(sender, "&a" + type + " recorded for " + (target.getName() == null ? target.getUniqueId() : target.getName()) + ".");
        return true;
    }

    private boolean revoke(CommandSender sender, String[] args, PunishmentType type) {
        if (args.length < 1) { msg(sender, "&cPlayer required."); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        int count = service.revoke(target.getUniqueId(), type, sender.getName());
        msg(sender, "&aRevoked " + count + " active " + type.name().toLowerCase(Locale.ROOT) + " punishment(s).");
        return true;
    }

    private boolean history(CommandSender sender, String[] args) {
        if (args.length < 1) { msg(sender, "&cUsage: /history <player> [page]"); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        int page = args.length >= 2 ? parseInt(args[1], 1) : 1;
        List<Punishment> entries = service.history(target.getUniqueId());
        int pages = Math.max(1, (entries.size() + 7) / 8);
        page = Math.max(1, Math.min(page, pages));
        msg(sender, "&6Punishment History &7- &f" + args[0] + " &8(" + page + "/" + pages + ")");
        int from = (page - 1) * 8;
        for (int i = from; i < Math.min(entries.size(), from + 8); i++) {
            Punishment p = entries.get(i);
            msg(sender, "&7" + Instant.ofEpochMilli(p.createdAt()) + " &f" + p.type() + " &7by &f" + p.staff() + " &8- &f" + p.reason() + (p.revoked() ? " &c[REVOKED]" : ""));
        }
        if (entries.isEmpty()) msg(sender, "&7No history.");
        return true;
    }

    private void msg(CommandSender sender, String raw) { sender.sendMessage(ChatColor.translateAlternateColorCodes('&', PREFIX + raw)); }
    private int parseInt(String s, int fallback) { try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return fallback; } }

    public interface PunishmentApi {
        boolean isBanned(UUID player);
        boolean isMuted(UUID player);
        List<Punishment> history(UUID player);
    }

    public enum PunishmentType { BAN, MUTE, WARN, KICK, NOTE }

    public record Punishment(String id, UUID player, String playerName, PunishmentType type, String staff, String reason,
                             long createdAt, long expiresAt, boolean revoked, String revokedBy, long revokedAt) {}

    public static final class PunishmentService implements PunishmentApi {
        private final MiraPunishmentsPlugin plugin;
        private final File file;
        private YamlConfiguration data;
        private final Map<UUID, List<Punishment>> records = new HashMap<>();

        PunishmentService(MiraPunishmentsPlugin plugin) {
            this.plugin = plugin;
            this.file = new File(plugin.getDataFolder(), "punishments.yml");
            reload();
        }

        void reload() {
            plugin.getDataFolder().mkdirs();
            data = YamlConfiguration.loadConfiguration(file);
            records.clear();
            ConfigurationSection root = data.getConfigurationSection("records");
            if (root == null) return;
            for (String uuidText : root.getKeys(false)) {
                UUID uuid;
                try { uuid = UUID.fromString(uuidText); } catch (IllegalArgumentException ex) { continue; }
                List<Punishment> list = new ArrayList<>();
                ConfigurationSection user = root.getConfigurationSection(uuidText);
                if (user == null) continue;
                for (String id : user.getKeys(false)) {
                    String base = id + ".";
                    try {
                        list.add(new Punishment(id, uuid, user.getString(base + "player-name", "unknown"),
                                PunishmentType.valueOf(user.getString(base + "type", "NOTE")), user.getString(base + "staff", "CONSOLE"),
                                user.getString(base + "reason", "No reason"), user.getLong(base + "created-at"), user.getLong(base + "expires-at"),
                                user.getBoolean(base + "revoked"), user.getString(base + "revoked-by", ""), user.getLong(base + "revoked-at")));
                    } catch (Exception ignored) {}
                }
                list.sort(Comparator.comparingLong(Punishment::createdAt).reversed());
                records.put(uuid, list);
            }
        }

        Punishment add(UUID uuid, String name, PunishmentType type, String staff, String reason, long expiresAt) {
            Punishment p = new Punishment(UUID.randomUUID().toString().substring(0, 8), uuid, name == null ? "unknown" : name, type,
                    staff, reason, System.currentTimeMillis(), expiresAt, false, "", 0L);
            records.computeIfAbsent(uuid, k -> new ArrayList<>()).add(0, p);
            save();
            return p;
        }

        int revoke(UUID uuid, PunishmentType type, String staff) {
            List<Punishment> list = records.getOrDefault(uuid, List.of());
            int count = 0;
            for (int i = 0; i < list.size(); i++) {
                Punishment p = list.get(i);
                if (p.type() == type && isActive(p)) {
                    list.set(i, new Punishment(p.id(), p.player(), p.playerName(), p.type(), p.staff(), p.reason(), p.createdAt(), p.expiresAt(), true, staff, System.currentTimeMillis()));
                    count++;
                }
            }
            if (count > 0) save();
            return count;
        }

        Punishment active(UUID uuid, PunishmentType type) {
            for (Punishment p : records.getOrDefault(uuid, List.of())) if (p.type() == type && isActive(p)) return p;
            return null;
        }

        boolean isActive(Punishment p) {
            return !p.revoked() && (p.expiresAt() <= 0 || p.expiresAt() > System.currentTimeMillis());
        }

        @Override public boolean isBanned(UUID player) { return active(player, PunishmentType.BAN) != null; }
        @Override public boolean isMuted(UUID player) { return active(player, PunishmentType.MUTE) != null; }
        @Override public List<Punishment> history(UUID player) { return List.copyOf(records.getOrDefault(player, List.of())); }

        String untilSuffix(Punishment p) {
            if (p.expiresAt() <= 0) return " (permanent)";
            long ms = Math.max(0, p.expiresAt() - System.currentTimeMillis());
            return " (" + Duration.ofMillis(ms).toMinutes() + "m remaining)";
        }

        long parseDuration(String input) {
            if (input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent")) return 0L;
            if (input.length() < 2) return Long.MIN_VALUE;
            try {
                long amount = Long.parseLong(input.substring(0, input.length() - 1));
                return switch (Character.toLowerCase(input.charAt(input.length() - 1))) {
                    case 's' -> amount * 1000L;
                    case 'm' -> amount * 60_000L;
                    case 'h' -> amount * 3_600_000L;
                    case 'd' -> amount * 86_400_000L;
                    case 'w' -> amount * 604_800_000L;
                    default -> Long.MIN_VALUE;
                };
            } catch (NumberFormatException ex) { return Long.MIN_VALUE; }
        }

        synchronized void save() {
            data = new YamlConfiguration();
            for (Map.Entry<UUID, List<Punishment>> entry : records.entrySet()) {
                for (Punishment p : entry.getValue()) {
                    String base = "records." + entry.getKey() + "." + p.id() + ".";
                    data.set(base + "player-name", p.playerName());
                    data.set(base + "type", p.type().name());
                    data.set(base + "staff", p.staff());
                    data.set(base + "reason", p.reason());
                    data.set(base + "created-at", p.createdAt());
                    data.set(base + "expires-at", p.expiresAt());
                    data.set(base + "revoked", p.revoked());
                    data.set(base + "revoked-by", p.revokedBy());
                    data.set(base + "revoked-at", p.revokedAt());
                }
            }
            try { data.save(file); } catch (IOException ex) { plugin.getLogger().severe("Could not save punishments.yml: " + ex.getMessage()); }
        }
    }
}
