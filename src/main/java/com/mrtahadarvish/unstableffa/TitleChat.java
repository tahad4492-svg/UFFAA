package com.mrtahadarvish.unstableffa;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a player's equipped shop title in front of their name in chat:
 * "Lost Cause Steve » hello".
 *
 * With {@code titles.replace-rank-prefix: true} (default) the whole chat line of a player that wears a
 * title is built here, so the rank prefix of LuckPerms / EssentialsChat / LPC / other chat plugins is not
 * shown for them - only the title. Players without a title keep the normal chat format.
 *
 * Both the modern Paper chat event and the old (legacy) one are handled, and both run last (MONITOR),
 * because chat plugins use either of them and the last one to set the format wins.
 */
public final class TitleChat implements Listener {

    private static final String SECTION = "§";

    /** Legacy chat format needs section-sign colour codes; hex colours (gradients) use the §x§r§r§g§g§b§b form. */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /** TAB plugin text format: &-codes and &#rrggbb hex colours. */
    private static final LegacyComponentSerializer TAB_FORMAT = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    /** The coloured title as a component, as section-sign text (legacy chat) and as TAB text. Chat runs on another thread. */
    private record Cached(Component title, String legacy, String tab) { }

    private final UnstableFFA plugin;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();
    private final TabSupport tab;

    public TitleChat(UnstableFFA plugin) {
        this.plugin = plugin;
        this.tab = new TabSupport(plugin, this);
        this.tab.start();
    }

    /** The title as TAB-plugin text (ends with a reset and a space), or null if the player has none. */
    public String tabText(UUID id) {
        Cached cached = cache.get(id);
        return cached == null ? null : cached.tab();
    }

    /** The coloured title, or null if the player has none. */
    public Component titleComponent(UUID id) {
        Cached cached = cache.get(id);
        return cached == null ? null : cached.title();
    }

    /** Re-reads the player's equipped title. Call after buying, equipping or removing one. */
    public void refresh(Player p) {
        UUID id = p.getUniqueId();
        String titleId = plugin.data().getTitle(id);
        ShopManager.Title title = titleId == null ? null : plugin.shop().getTitle(titleId);
        if (title == null || (title.price > 0 && !plugin.data().ownsTitle(id, title.id))) {
            cache.remove(id);
        } else {
            Component component = plugin.parse(title.mini());
            cache.put(id, new Cached(component, LEGACY.serialize(component), TAB_FORMAT.serialize(component) + "&r "));
        }
        tab.update(p);   // tab list follows the equipped title right away
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        refresh(p);
        // TAB loads the player a moment after the join, so try again shortly (the 2 second check catches the rest)
        Bukkit.getScheduler().runTaskLater(plugin, () -> tab.update(p), 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> tab.update(p), 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cache.remove(e.getPlayer().getUniqueId());
        tab.forget(e.getPlayer().getUniqueId());
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("titles.show-in-chat", true);
    }

    private boolean replaceRankPrefix() {
        return plugin.getConfig().getBoolean("titles.replace-rank-prefix", true);
    }

    // ---- modern Paper chat ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!enabled()) {
            return;
        }
        Cached cached = cache.get(e.getPlayer().getUniqueId());
        if (cached == null) {
            return;
        }
        final Component title = cached.title();
        final boolean replace = replaceRankPrefix();
        e.renderer((source, name, message, viewer) -> Component.text()
                .append(title)
                .append(Component.space())
                // plain username = no LuckPerms prefix hiding inside the display name
                .append(replace ? Component.text(source.getName(), NamedTextColor.WHITE) : name)
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(message)
                .build());
    }

    // ---- legacy chat (EssentialsChat, LPC and many others still use this one) ---------------------

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent e) {
        if (!enabled()) {
            return;
        }
        Cached cached = cache.get(e.getPlayer().getUniqueId());
        if (cached == null) {
            return;
        }
        String title = cached.legacy().replace("%", "%%");
        if (replaceRankPrefix()) {
            String name = e.getPlayer().getName().replace("%", "%%");
            // %2$s = the message; the player's name is written out so no prefix can sneak in through the display name
            e.setFormat(title + " " + SECTION + "f" + name + " " + SECTION + "8» " + SECTION + "r%2$s");
        } else {
            e.setFormat(title + " " + SECTION + "r" + e.getFormat());
        }
    }
}
