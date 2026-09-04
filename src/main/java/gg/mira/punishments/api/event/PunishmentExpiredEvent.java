package gg.mira.punishments.api.event;

import gg.mira.punishments.MiraPunishmentsPlugin.Punishment;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PunishmentExpiredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Punishment punishment;

    public PunishmentExpiredEvent(Punishment punishment) {
        this.punishment = punishment;
    }

    public Punishment punishment() { return punishment; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
