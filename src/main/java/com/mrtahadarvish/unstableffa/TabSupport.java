package com.mrtahadarvish.unstableffa;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Puts the equipped shop title in front of the player's name in the tab list.
 *
 * <ul>
 *   <li>With the TAB plugin installed the title is handed to TAB as the player's tab prefix (through TAB's
 *       API, found by reflection so UnstableFFA does not need TAB to build or run).</li>
 *   <li>Without TAB (or if TAB's tab list formatting is switched off) the normal player list name is used.</li>
 * </ul>
 * TAB forgets values given through its API when it is reloaded, so everything is re-checked every 2 seconds.
 */
public final class TabSupport {

    private enum Result { DONE, NOT_READY, NO_TAB, FAILED }

    private final UnstableFFA plugin;
    private final TitleChat chat;
    /** Players whose TAB prefix was set by us (so we only reset our own values). */
    private final Set<UUID> appliedTab = new HashSet<>();
    /** The title we put into the plain player list name (when TAB is not used). */
    private final Map<UUID, Component> appliedBukkit = new HashMap<>();
    private boolean warned;

    public TabSupport(UnstableFFA plugin, TitleChat chat) {
        this.plugin = plugin;
        this.chat = chat;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                update(p);
            }
        }, 40L, 40L);
    }

    public void forget(UUID id) {
        appliedTab.remove(id);
        appliedBukkit.remove(id);
    }

    /** Brings the tab list entry of this player in line with their equipped title. Main thread only. */
    public void update(Player p) {
        if (!p.isOnline()) {
            return;
        }
        boolean show = plugin.getConfig().getBoolean("titles.show-in-tab", true);
        UUID id = p.getUniqueId();
        String tabText = show ? chat.tabText(id) : null;
        Component title = show ? chat.titleComponent(id) : null;

        if (Bukkit.getPluginManager().isPluginEnabled("TAB")) {
            Result result = applyTab(p, tabText);
            if (result == Result.DONE || result == Result.NOT_READY) {
                return;   // handled, or TAB has not loaded this player yet (retried in 2 seconds)
            }
        }
        applyBukkit(p, title);
    }

    // ---- TAB (reflection) -----------------------------------------------------------------

    private Result applyTab(Player p, String desired) {
        UUID id = p.getUniqueId();
        try {
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) {
                return Result.NOT_READY;
            }
            Object manager = apiClass.getMethod("getTabListFormatManager").invoke(api);
            if (manager == null) {
                return Result.NO_TAB;   // tablist-name-formatting is disabled in TAB's config
            }
            Object tabPlayer = apiClass.getMethod("getPlayer", UUID.class).invoke(api, id);
            if (tabPlayer == null) {
                return Result.NOT_READY;
            }
            Method setPrefix = find(manager.getClass(), "setPrefix", 2);
            if (setPrefix == null) {
                return fail("this TAB version has no setPrefix in its tab list API");
            }
            Method getCustom = find(manager.getClass(), "getCustomPrefix", 1);
            Object current = getCustom == null ? null : call(getCustom, manager, tabPlayer);

            if (desired != null) {
                if (!desired.equals(current)) {
                    call(setPrefix, manager, tabPlayer, desired);
                }
                appliedTab.add(id);
            } else if (appliedTab.remove(id)) {
                call(setPrefix, manager, tabPlayer, (Object) null);   // back to what TAB's config says
            }
            return Result.DONE;
        } catch (ClassNotFoundException ex) {
            return Result.NO_TAB;
        } catch (Exception ex) {
            return fail(String.valueOf(ex));
        }
    }

    private Result fail(String reason) {
        if (!warned) {
            warned = true;
            plugin.getLogger().warning("Could not put the title into the TAB list through TAB's API (" + reason
                    + "). Using the normal player list name instead - if the title is missing in the tab list, "
                    + "send this message to the plugin author.");
        }
        return Result.FAILED;
    }

    private static Method find(Class<?> type, String name, int parameters) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameters) {
                return method;
            }
        }
        return null;
    }

    private static Object call(Method method, Object target, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException ex) {
            method.setAccessible(true);   // TAB's implementation class is not public
            return method.invoke(target, args);
        }
    }

    // ---- plain Bukkit player list name ------------------------------------------------------------

    private void applyBukkit(Player p, Component title) {
        UUID id = p.getUniqueId();
        Component last = appliedBukkit.get(id);
        if (Objects.equals(title, last)) {
            return;
        }
        if (title == null) {
            p.playerListName(null);
            appliedBukkit.remove(id);
        } else {
            p.playerListName(title.append(Component.space()).append(Component.text(p.getName())));
            appliedBukkit.put(id, title);
        }
    }
}
