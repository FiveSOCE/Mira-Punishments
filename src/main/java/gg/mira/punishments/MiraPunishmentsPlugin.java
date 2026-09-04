package gg.mira.punishments;

import com.mira.core.api.MiraCore;
import com.mira.core.api.MiraCoreProvider;
import com.mira.core.api.ModuleHealth;
import com.mira.core.api.PaginationService;
import gg.mira.punishments.api.event.PunishmentExpiredEvent;
import gg.mira.punishments.api.event.PunishmentIssuedEvent;
import gg.mira.punishments.api.event.PunishmentRevokedEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class MiraPunishmentsPlugin extends JavaPlugin implements Listener {
    private MiraCore core;
    private PunishmentService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core = MiraCoreProvider.require();
        service = new PunishmentService(this);

        getServer().getServicesManager().register(PunishmentApi.class, service, this, ServicePriority.Normal);
        core.modules().register(this, "MiraPunishments");
        core.services().register(PunishmentApi.class, service);
        core.modules().setHealth(this, ModuleHealth.HEALTHY,
                "Persistent cases, active enforcement, expiry lifecycle and audit integration ready");

        getServer().getPluginManager().registerEvents(this, this);

        long expiryTicks = Math.max(5L, getConfig().getLong("expiry.scan-seconds", 30L)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::scanExpiries, 20L, expiryTicks);

        getLogger().info("MiraPunishments v" + getPluginMeta().getVersion()
                + " enabled with " + service.caseCount() + " historical case(s).");
    }

    @Override
    public void onDisable() {
        if (service != null) service.save();
        getServer().getServicesManager().unregisterAll(this);
        if (core != null) {
            if (service != null) core.services().unregister(PunishmentApi.class, service);
            core.modules().unregister(this);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Punishment active = service.active(event.getPlayer().getUniqueId(), PunishmentType.BAN);
        if (active != null) {
            event.getPlayer().kick(Component.text("You are banned: " + active.reason() + service.untilSuffix(active)));
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Punishment active = service.active(event.getPlayer().getUniqueId(), PunishmentType.MUTE);
        if (active == null) return;
        event.setCancelled(true);
        msg(event.getPlayer(), "&cYou are muted: &f" + active.reason() + service.untilSuffix(active));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "history" -> history(sender, args);
            case "warn" -> issue(sender, args, PunishmentType.WARN);
            case "mute" -> issue(sender, args, PunishmentType.MUTE);
            case "miraban" -> issue(sender, args, PunishmentType.BAN);
            case "unmute" -> revoke(sender, args, PunishmentType.MUTE);
            case "miraunban" -> revoke(sender, args, PunishmentType.BAN);
            case "mirakick" -> kick(sender, args);
            case "punish" -> punish(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("punish")) {
            if (args.length == 1) return complete(args[0], List.of("history", "case", "active", "note", "reload"));
            if (args.length == 2 && Set.of("history", "active", "note").contains(args[0].toLowerCase(Locale.ROOT))) {
                return complete(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            }
            return List.of();
        }
        if (args.length == 1 && !Set.of("miraunban").contains(name)) {
            return complete(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return List.of();
    }

    private boolean punish(CommandSender sender, String[] args) {
        if (args.length == 0) {
            punishHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "history" -> history(sender, Arrays.copyOfRange(args, 1, args.length));
            case "case" -> showCase(sender, args);
            case "active" -> showActive(sender, args);
            case "note" -> note(sender, args);
            case "reload" -> {
                service.reload();
                scanExpiries();
                msg(sender, "&aMiraPunishments reloaded.");
                yield true;
            }
            default -> {
                punishHelp(sender);
                yield true;
            }
        };
    }

    private boolean showCase(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "&cUsage: /punish case <case-id>");
            return true;
        }
        Punishment punishment = service.caseById(args[1]).orElse(null);
        if (punishment == null) {
            msg(sender, "&cCase not found.");
            return true;
        }

        msg(sender, "&6Case &f" + punishment.id());
        msg(sender, "&7Player: &f" + punishment.playerName() + " &8(" + punishment.player() + ")");
        msg(sender, "&7Type: &f" + punishment.type() + " &7Status: " + statusColour(punishment));
        msg(sender, "&7Staff: &f" + punishment.staff());
        msg(sender, "&7Created: &f" + Instant.ofEpochMilli(punishment.createdAt()));
        msg(sender, "&7Expires: &f" + (punishment.expiresAt() <= 0 ? "Never" : Instant.ofEpochMilli(punishment.expiresAt())));
        if (punishment.revoked()) {
            msg(sender, "&7Revoked by: &f" + punishment.revokedBy()
                    + " &7at &f" + Instant.ofEpochMilli(punishment.revokedAt()));
        }
        msg(sender, "&7Reason: &f" + punishment.reason());
        return true;
    }

    private boolean showActive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "&cUsage: /punish active <player>");
            return true;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            msg(sender, "&cPlayer not found.");
            return true;
        }
        List<Punishment> active = service.active(target.getUniqueId());
        msg(sender, "&6Active punishments &7- &f" + displayName(target));
        if (active.isEmpty()) {
            msg(sender, "&7None.");
            return true;
        }
        for (Punishment punishment : active) {
            msg(sender, "&7- &f" + punishment.id() + " &8| &f" + punishment.type()
                    + " &8| &f" + punishment.reason() + service.untilSuffix(punishment));
        }
        return true;
    }

    private boolean note(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg(sender, "&cUsage: /punish note <player> <note>");
            return true;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            msg(sender, "&cPlayer not found.");
            return true;
        }
        String note = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        if (note.isBlank()) {
            msg(sender, "&cNote cannot be blank.");
            return true;
        }
        Punishment punishment = service.add(target.getUniqueId(), displayName(target), PunishmentType.NOTE,
                sender.getName(), note, 0L);
        issued(sender, punishment);
        msg(sender, "&aStaff note added as case &f" + punishment.id() + "&a.");
        return true;
    }

    private boolean issue(CommandSender sender, String[] args, PunishmentType type) {
        if (args.length < 2) {
            msg(sender, "&cUsage: /" + commandName(type) + " <player> "
                    + (type == PunishmentType.WARN ? "<reason>" : "<duration|perm> <reason>"));
            return true;
        }

        OfflinePlayer target = resolve(args[0]);
        if (target == null) {
            msg(sender, "&cPlayer not found.");
            return true;
        }

        int reasonStart = 1;
        long expiresAt = 0L;
        if (type == PunishmentType.BAN || type == PunishmentType.MUTE) {
            if (args.length < 3) {
                msg(sender, "&cDuration and reason are required.");
                return true;
            }
            long duration = service.parseDuration(args[1]);
            if (duration == Long.MIN_VALUE) {
                msg(sender, "&cInvalid duration. Examples: 30m, 12h, 7d, perm");
                return true;
            }
            expiresAt = duration <= 0 ? 0L : safeExpiry(duration);
            reasonStart = 2;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length)).trim();
        if (reason.isBlank()) {
            msg(sender, "&cReason cannot be blank.");
            return true;
        }

        Punishment punishment = service.add(target.getUniqueId(), displayName(target), type,
                sender.getName(), reason, expiresAt);
        issued(sender, punishment);

        Player online = target.getPlayer();
        if (online != null) {
            switch (type) {
                case BAN -> online.kick(Component.text("You are banned: " + reason + service.untilSuffix(punishment)));
                case WARN -> msg(online, "&eWarning: &f" + reason);
                case MUTE -> msg(online, "&cYou have been muted: &f" + reason + service.untilSuffix(punishment));
                default -> { }
            }
        }

        msg(sender, "&a" + type + " recorded for &f" + displayName(target)
                + "&a as case &f" + punishment.id() + "&a.");
        return true;
    }

    private boolean kick(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "&cUsage: /mirakick <player> <reason>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            msg(sender, "&cThat player must be online to kick them.");
            return true;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (reason.isBlank()) {
            msg(sender, "&cReason cannot be blank.");
            return true;
        }

        Punishment punishment = service.add(target.getUniqueId(), target.getName(), PunishmentType.KICK,
                sender.getName(), reason, 0L);
        issued(sender, punishment);
        target.kick(Component.text("You were kicked: " + reason));
        msg(sender, "&aKicked &f" + target.getName() + "&a. Case &f" + punishment.id() + "&a.");
        return true;
    }

    private boolean revoke(CommandSender sender, String[] args, PunishmentType type) {
        if (args.length < 1) {
            msg(sender, "&cPlayer required.");
            return true;
        }
        OfflinePlayer target = resolve(args[0]);
        if (target == null) {
            msg(sender, "&cPlayer not found.");
            return true;
        }

        List<Punishment> revoked = service.revoke(target.getUniqueId(), type, sender.getName());
        for (Punishment punishment : revoked) {
            Bukkit.getPluginManager().callEvent(new PunishmentRevokedEvent(punishment));
            core.audit().record("MiraPunishments", "PUNISHMENT_REVOKED",
                    sender instanceof Player player ? player.getUniqueId() : null,
                    sender.getName(), punishment.id(), "Revoked punishment",
                    Map.of("player", punishment.player().toString(), "type", punishment.type().name()));
        }

        msg(sender, "&aRevoked &f" + revoked.size() + " &aactive "
                + type.name().toLowerCase(Locale.ROOT) + " punishment(s).");
        return true;
    }

    private boolean history(CommandSender sender, String[] args) {
        if (args.length < 1) {
            msg(sender, "&cUsage: /history <player> [page]");
            return true;
        }
        OfflinePlayer target = resolve(args[0]);
        if (target == null) {
            msg(sender, "&cPlayer not found.");
            return true;
        }

        int requestedPage = args.length >= 2 ? parseInt(args[1], 1) : 1;
        List<Punishment> entries = service.history(target.getUniqueId());
        int pageSize = Math.max(1, Math.min(20, getConfig().getInt("history.page-size", 8)));
        PaginationService.Page<Punishment> page = core.pagination().page(entries, requestedPage, pageSize);

        msg(sender, "&6Punishment History &7- &f" + displayName(target)
                + " &8(" + page.page() + "/" + page.pages() + ")");
        for (Punishment punishment : page.values()) {
            msg(sender, "&7" + Instant.ofEpochMilli(punishment.createdAt())
                    + " &f" + punishment.id() + " &8| &f" + punishment.type()
                    + " &8| " + statusColour(punishment)
                    + " &8| &f" + punishment.reason());
        }
        if (entries.isEmpty()) msg(sender, "&7No history.");
        return true;
    }

    private void issued(CommandSender sender, Punishment punishment) {
        Bukkit.getPluginManager().callEvent(new PunishmentIssuedEvent(punishment));
        core.audit().record("MiraPunishments", "PUNISHMENT_ISSUED",
                sender instanceof Player player ? player.getUniqueId() : null,
                sender.getName(), punishment.id(), "Issued punishment",
                Map.of("player", punishment.player().toString(),
                        "type", punishment.type().name(),
                        "expiresAt", Long.toString(punishment.expiresAt())));
    }

    private void scanExpiries() {
        for (Punishment punishment : service.markNewExpiries()) {
            if (getConfig().getBoolean("expiry.emit-events", true)) {
                Bukkit.getPluginManager().callEvent(new PunishmentExpiredEvent(punishment));
            }
            core.audit().record("MiraPunishments", "PUNISHMENT_EXPIRED", null, "scheduler",
                    punishment.id(), "Punishment expired",
                    Map.of("player", punishment.player().toString(), "type", punishment.type().name()));
        }
    }

    private long safeExpiry(long duration) {
        try {
            return Math.addExact(System.currentTimeMillis(), duration);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private String statusColour(Punishment punishment) {
        if (punishment.revoked()) return "&cREVOKED";
        if (service.isExpired(punishment)) return "&8EXPIRED";
        if (punishment.type() == PunishmentType.BAN || punishment.type() == PunishmentType.MUTE) return "&aACTIVE";
        return "&7RECORDED";
    }

    private String commandName(PunishmentType type) {
        return switch (type) {
            case BAN -> "miraban";
            case MUTE -> "mute";
            case WARN -> "warn";
            default -> type.name().toLowerCase(Locale.ROOT);
        };
    }

    private OfflinePlayer resolve(String raw) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(raw);
        if (player.getName() == null && !player.hasPlayedBefore() && !player.isOnline()) return null;
        return player;
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private void punishHelp(CommandSender sender) {
        msg(sender, "&6MiraPunishments");
        msg(sender, "&7/punish history <player> [page]");
        msg(sender, "&7/punish case <case-id>");
        msg(sender, "&7/punish active <player>");
        msg(sender, "&7/punish note <player> <note>");
        msg(sender, "&7/punish reload");
    }

    private void msg(CommandSender sender, String raw) {
        core.messages().send(sender, raw);
    }

    private int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static List<String> complete(String prefix, Collection<String> values) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .distinct().sorted().toList();
    }

    public interface PunishmentApi {
        boolean isBanned(UUID player);
        boolean isMuted(UUID player);
        List<Punishment> history(UUID player);
        List<Punishment> active(UUID player);
        Optional<Punishment> active(UUID player, PunishmentType type);
        Optional<Punishment> caseById(String id);
        long caseCount();
    }

    public enum PunishmentType { BAN, MUTE, WARN, KICK, NOTE }

    /**
     * Record shape intentionally remains compatible with v0.1.0.
     * Expiry-notification state is persisted separately from the public record.
     */
    public record Punishment(String id, UUID player, String playerName, PunishmentType type, String staff, String reason,
                             long createdAt, long expiresAt, boolean revoked, String revokedBy, long revokedAt) {}

    public static final class PunishmentService implements PunishmentApi {
        private final MiraPunishmentsPlugin plugin;
        private final File file;
        private YamlConfiguration data;
        private final Map<UUID, List<Punishment>> records = new LinkedHashMap<>();
        private final Set<String> expiryRecorded = new HashSet<>();

        PunishmentService(MiraPunishmentsPlugin plugin) {
            this.plugin = plugin;
            this.file = new File(plugin.getDataFolder(), "punishments.yml");
            reload();
        }

        synchronized void reload() {
            plugin.getDataFolder().mkdirs();
            data = YamlConfiguration.loadConfiguration(file);
            records.clear();
            expiryRecorded.clear();

            ConfigurationSection root = data.getConfigurationSection("records");
            if (root == null) return;

            for (String uuidText : root.getKeys(false)) {
                UUID uuid;
                try { uuid = UUID.fromString(uuidText); }
                catch (IllegalArgumentException exception) { continue; }

                List<Punishment> list = new ArrayList<>();
                ConfigurationSection user = root.getConfigurationSection(uuidText);
                if (user == null) continue;

                for (String id : user.getKeys(false)) {
                    String base = id + ".";
                    try {
                        Punishment punishment = new Punishment(id, uuid,
                                user.getString(base + "player-name", "unknown"),
                                PunishmentType.valueOf(user.getString(base + "type", "NOTE")),
                                user.getString(base + "staff", "CONSOLE"),
                                user.getString(base + "reason", "No reason"),
                                user.getLong(base + "created-at"),
                                user.getLong(base + "expires-at"),
                                user.getBoolean(base + "revoked"),
                                user.getString(base + "revoked-by", ""),
                                user.getLong(base + "revoked-at"));
                        list.add(punishment);
                        if (user.getBoolean(base + "expiry-recorded", false)) expiryRecorded.add(id);
                    } catch (RuntimeException ignored) {
                    }
                }
                list.sort(Comparator.comparingLong(Punishment::createdAt).reversed());
                records.put(uuid, list);
            }
        }

        synchronized Punishment add(UUID uuid, String name, PunishmentType type,
                                    String staff, String reason, long expiresAt) {
            String id = newCaseId();
            Punishment punishment = new Punishment(id, uuid,
                    name == null ? "unknown" : name,
                    type, staff, reason, System.currentTimeMillis(), expiresAt,
                    false, "", 0L);
            records.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(0, punishment);
            save();
            return punishment;
        }

        synchronized List<Punishment> revoke(UUID uuid, PunishmentType type, String staff) {
            List<Punishment> list = records.get(uuid);
            if (list == null) return List.of();

            List<Punishment> revoked = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Punishment current = list.get(i);
                if (current.type() != type || !isActive(current)) continue;

                Punishment replacement = new Punishment(current.id(), current.player(), current.playerName(),
                        current.type(), current.staff(), current.reason(), current.createdAt(), current.expiresAt(),
                        true, staff, System.currentTimeMillis());
                list.set(i, replacement);
                revoked.add(replacement);
            }
            if (!revoked.isEmpty()) save();
            return List.copyOf(revoked);
        }

        Punishment active(UUID uuid, PunishmentType type) {
            return active(uuid).stream().filter(punishment -> punishment.type() == type).findFirst().orElse(null);
        }

        @Override
        public synchronized List<Punishment> active(UUID uuid) {
            return records.getOrDefault(uuid, List.of()).stream()
                    .filter(this::isActive)
                    .toList();
        }

        @Override
        public synchronized Optional<Punishment> active(UUID uuid, PunishmentType type) {
            return active(uuid).stream().filter(punishment -> punishment.type() == type).findFirst();
        }

        @Override
        public synchronized Optional<Punishment> caseById(String id) {
            if (id == null || id.isBlank()) return Optional.empty();
            return records.values().stream().flatMap(Collection::stream)
                    .filter(punishment -> punishment.id().equalsIgnoreCase(id))
                    .findFirst();
        }

        @Override public synchronized boolean isBanned(UUID player) { return active(player, PunishmentType.BAN) != null; }
        @Override public synchronized boolean isMuted(UUID player) { return active(player, PunishmentType.MUTE) != null; }
        @Override public synchronized List<Punishment> history(UUID player) {
            return List.copyOf(records.getOrDefault(player, List.of()));
        }
        @Override public synchronized long caseCount() {
            return records.values().stream().mapToLong(Collection::size).sum();
        }

        synchronized List<Punishment> markNewExpiries() {
            long now = System.currentTimeMillis();
            List<Punishment> expired = new ArrayList<>();
            for (List<Punishment> list : records.values()) {
                for (Punishment punishment : list) {
                    if (punishment.revoked() || punishment.expiresAt() <= 0
                            || punishment.expiresAt() > now || expiryRecorded.contains(punishment.id())) {
                        continue;
                    }
                    expiryRecorded.add(punishment.id());
                    expired.add(punishment);
                }
            }
            if (!expired.isEmpty()) save();
            return List.copyOf(expired);
        }

        boolean isActive(Punishment punishment) {
            return !punishment.revoked()
                    && (punishment.expiresAt() <= 0 || punishment.expiresAt() > System.currentTimeMillis())
                    && (punishment.type() == PunishmentType.BAN || punishment.type() == PunishmentType.MUTE);
        }

        boolean isExpired(Punishment punishment) {
            return !punishment.revoked() && punishment.expiresAt() > 0
                    && punishment.expiresAt() <= System.currentTimeMillis();
        }

        String untilSuffix(Punishment punishment) {
            if (punishment.expiresAt() <= 0) return " (permanent)";
            long seconds = Math.max(0L, (punishment.expiresAt() - System.currentTimeMillis()) / 1000L);
            long days = seconds / 86400L;
            long hours = (seconds % 86400L) / 3600L;
            long minutes = (seconds % 3600L) / 60L;
            if (days > 0) return " (" + days + "d " + hours + "h remaining)";
            if (hours > 0) return " (" + hours + "h " + minutes + "m remaining)";
            return " (" + minutes + "m remaining)";
        }

        long parseDuration(String input) {
            if (input == null) return Long.MIN_VALUE;
            if (input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent")) return 0L;
            if (input.length() < 2) return Long.MIN_VALUE;
            try {
                long amount = Long.parseLong(input.substring(0, input.length() - 1));
                if (amount <= 0) return Long.MIN_VALUE;
                Duration duration = switch (Character.toLowerCase(input.charAt(input.length() - 1))) {
                    case 's' -> Duration.ofSeconds(amount);
                    case 'm' -> Duration.ofMinutes(amount);
                    case 'h' -> Duration.ofHours(amount);
                    case 'd' -> Duration.ofDays(amount);
                    case 'w' -> Duration.ofDays(Math.multiplyExact(amount, 7L));
                    default -> null;
                };
                return duration == null ? Long.MIN_VALUE : duration.toMillis();
            } catch (RuntimeException exception) {
                return Long.MIN_VALUE;
            }
        }

        synchronized void save() {
            data = new YamlConfiguration();
            for (Map.Entry<UUID, List<Punishment>> entry : records.entrySet()) {
                for (Punishment punishment : entry.getValue()) {
                    String base = "records." + entry.getKey() + "." + punishment.id() + ".";
                    data.set(base + "player-name", punishment.playerName());
                    data.set(base + "type", punishment.type().name());
                    data.set(base + "staff", punishment.staff());
                    data.set(base + "reason", punishment.reason());
                    data.set(base + "created-at", punishment.createdAt());
                    data.set(base + "expires-at", punishment.expiresAt());
                    data.set(base + "revoked", punishment.revoked());
                    data.set(base + "revoked-by", punishment.revokedBy());
                    data.set(base + "revoked-at", punishment.revokedAt());
                    data.set(base + "expiry-recorded", expiryRecorded.contains(punishment.id()));
                }
            }
            try {
                data.save(file);
            } catch (IOException exception) {
                plugin.getLogger().severe("Could not save punishments.yml: " + exception.getMessage());
            }
        }

        private String newCaseId() {
            for (int attempts = 0; attempts < 20; attempts++) {
                String candidate = UUID.randomUUID().toString().substring(0, 8);
                if (caseById(candidate).isEmpty()) return candidate;
            }
            return UUID.randomUUID().toString();
        }
    }
}
