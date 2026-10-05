package com.mrtahadarvish.unstableffa;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kill streaks and bounties.
 *
 * <ul>
 *   <li>Every kill in a row (without dying or leaving the arena) adds to your streak.</li>
 *   <li>Every {@code bounty.step} kills (5 by default) you hit a milestone: a themed announcement
 *       ("Dominating", "On a rampage"...), a title, a sound and a coin bonus.</li>
 *   <li>From the first milestone on there is a bounty on your head. Whoever ends your streak
 *       collects it.</li>
 * </ul>
 * Everything (step, coins, tiers, messages, sounds) is editable in config.yml under {@code bounty:}.
 */
public final class StreakManager {

    private final UnstableFFA plugin;
    private final Map<UUID, Integer> streaks = new HashMap<>();

    public StreakManager(UnstableFFA plugin) {
        this.plugin = plugin;
    }

    // ---- settings -------------------------------------------------------------------

    public boolean enabled() {
        return plugin.getConfig().getBoolean("bounty.enabled", true);
    }

    /** Kills between milestones (also the streak at which a bounty appears). */
    public int step() {
        return Math.max(1, plugin.getConfig().getInt("bounty.step", 5));
    }

    private int shutdownMin() {
        return Math.max(2, plugin.getConfig().getInt("bounty.shutdown-min-streak", 3));
    }

    // ---- queries --------------------------------------------------------------------

    public int get(UUID id) {
        return streaks.getOrDefault(id, 0);
    }

    /** Coins on the head of someone with this streak (0 until the first milestone). */
    public long bountyOf(int streak) {
        if (streak < step()) {
            return 0L;
        }
        long perStep = Math.max(0L, plugin.getConfig().getLong("bounty.coins-per-step", 10L));
        return (long) (streak / step()) * perStep;
    }

    /** Players that currently have a bounty, biggest first. */
    public List<Map.Entry<UUID, Integer>> activeBounties() {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : streaks.entrySet()) {
            if (entry.getValue() >= step() && Bukkit.getPlayer(entry.getKey()) != null) {
                list.add(Map.entry(entry.getKey(), entry.getValue()));
            }
        }
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list;
    }

    // ---- events ---------------------------------------------------------------------

    /**
     * A player died in an arena. Ends the victim's streak, pays the bounty to the killer (if there is one),
     * extends the killer's streak and sends the killer their action bar.
     *
     * @param killer     null when the victim died to the environment / themselves
     * @param baseReward the normal coins for a kill (already paid by the caller)
     */
    public void onKill(Player killer, Player victim, long baseReward) {
        Integer ended = streaks.remove(victim.getUniqueId());
        int victimStreak = ended == null ? 0 : ended;

        if (killer == null) {
            if (enabled() && victimStreak >= step()) {
                announceLost(victim.getName(), victimStreak);
            }
            return;
        }

        long claimed = 0L;
        long milestoneCoins = 0L;
        int streak = 0;
        boolean milestone = false;

        if (enabled()) {
            claimed = bountyOf(victimStreak);
            if (claimed > 0L) {
                plugin.data().addCoins(killer.getUniqueId(), claimed);
            }
            if (victimStreak >= shutdownMin()) {
                announceShutdown(killer, victim, victimStreak, claimed);
            }

            streak = streaks.merge(killer.getUniqueId(), 1, Integer::sum);
            if (streak > plugin.data().getStat(killer.getUniqueId(), "beststreak")) {
                plugin.data().setStat(killer.getUniqueId(), "beststreak", streak);
            }
            milestone = streak % step() == 0;
            if (milestone) {
                long perMilestone = Math.max(0L, plugin.getConfig().getLong("bounty.milestone-coins", 5L));
                milestoneCoins = (long) (streak / step()) * perMilestone;
                if (milestoneCoins > 0L) {
                    plugin.data().addCoins(killer.getUniqueId(), milestoneCoins);
                }
                announceMilestone(killer, streak);
            }
        }

        // action bar: coins earned + streak progress
        StringBuilder bar = new StringBuilder("<gold>+" + baseReward + " coins");
        if (claimed > 0L) {
            bar.append(" <yellow>+").append(claimed).append(" bounty");
        }
        if (milestoneCoins > 0L) {
            bar.append(" <green>+").append(milestoneCoins).append(" streak bonus");
        }
        bar.append(" <gray>(total: <yellow>").append(plugin.data().getCoins(killer.getUniqueId())).append("<gray>)");
        if (enabled() && streak > 0) {
            int next = step() - (streak % step());
            bar.append(" <dark_gray>| <red>Streak <white>").append(streak)
                    .append(" <dark_gray>(<gray>bounty ").append(streak >= step() ? "<yellow>" + bountyOf(streak) : "in " + next)
                    .append("<dark_gray>)");
        }
        killer.sendActionBar(plugin.parse(bar.toString()));
    }

    /** The player left the arena (lobby, quit, map reset...). Ends the streak; a bounty that was on them is lost. */
    public void reset(Player p) {
        Integer ended = streaks.remove(p.getUniqueId());
        if (ended != null && enabled() && ended >= step()) {
            announceLost(p.getName(), ended);
        }
    }

    public void clearAll() {
        streaks.clear();
    }

    // ---- announcements ----------------------------------------------------------------

    private void announceMilestone(Player p, int streak) {
        ConfigurationSection tier = tierFor(streak);
        long bounty = bountyOf(streak);

        String raw = tier == null ? "" : pick(tier.getStringList("messages"));
        if (raw.isEmpty()) {
            raw = "<red><bold>{player}</bold> <gray>is on a <white>{streak} <gray>kill streak!";
        }
        String tierName = tier == null ? "<red><bold>KILLING SPREE" : tier.getString("name", "<red><bold>KILLING SPREE");
        String soundKey = tier == null ? "entity.player.levelup" : tier.getString("sound", "entity.player.levelup");
        float pitch = tier == null ? 1f : (float) tier.getDouble("pitch", 1.0);
        boolean lightning = tier != null && tier.getBoolean("lightning", false);

        Component line = plugin.parse(fill(raw, "{player}", UnstableFFA.esc(p.getName()),
                "{streak}", String.valueOf(streak), "{bounty}", String.valueOf(bounty)));
        broadcast(line, sound(soundKey, pitch));

        // the streaker gets a big title
        String sub = "<gray>" + streak + " kill streak" + (bounty > 0L ? " <dark_gray>• <yellow>" + bounty + " coin bounty on your head" : "");
        p.showTitle(Title.title(plugin.parse(tierName), plugin.parse(sub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(500))));

        if (lightning) {
            p.getWorld().strikeLightningEffect(p.getLocation());   // visual + sound only, no damage or fire
        }
    }

    private void announceShutdown(Player killer, Player victim, int streak, long bounty) {
        String raw = pick(plugin.getConfig().getStringList(bounty > 0L ? "bounty.claimed-messages" : "bounty.shutdown-messages"));
        if (raw.isEmpty()) {
            raw = bounty > 0L
                    ? "<green>{killer} <gray>ended <red>{victim}<gray>'s <white>{streak} <gray>kill streak and claimed <yellow>{bounty} coins<gray>!"
                    : "<green>{killer} <gray>ended <red>{victim}<gray>'s <white>{streak} <gray>kill streak!";
        }
        Component line = plugin.parse(fill(raw, "{killer}", UnstableFFA.esc(killer.getName()),
                "{victim}", UnstableFFA.esc(victim.getName()), "{streak}", String.valueOf(streak),
                "{bounty}", String.valueOf(bounty)));
        broadcast(line, sound(plugin.getConfig().getString("bounty.claim-sound", "entity.experience_orb.pickup"), 0.8f));
    }

    private void announceLost(String name, int streak) {
        String raw = pick(plugin.getConfig().getStringList("bounty.lost-messages"));
        if (raw.isEmpty()) {
            raw = "<red>{player} <gray>lost their <white>{streak} <gray>kill streak - the <yellow>{bounty} coin <gray>bounty goes unclaimed.";
        }
        broadcast(plugin.parse(fill(raw, "{player}", UnstableFFA.esc(name),
                "{streak}", String.valueOf(streak), "{bounty}", String.valueOf(bountyOf(streak)))), null);
    }

    /** Sends a line to everybody (bounty.broadcast: all) or only to players in arenas (bounty.broadcast: arena). */
    private void broadcast(Component line, Sound sound) {
        boolean everyone = !"arena".equalsIgnoreCase(plugin.getConfig().getString("bounty.broadcast", "all"));
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (everyone || plugin.arenas().isArenaWorld(pl.getWorld())) {
                pl.sendMessage(line);
                if (sound != null) {
                    pl.playSound(sound);
                }
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    // ---- helpers ----------------------------------------------------------------------

    /** The tier for a streak: the highest configured tier that is not above it (or the lowest one if none is). */
    private ConfigurationSection tierFor(int streak) {
        ConfigurationSection tiers = plugin.getConfig().getConfigurationSection("bounty.tiers");
        if (tiers == null) {
            return null;
        }
        ConfigurationSection best = null;
        int bestKey = -1;
        ConfigurationSection lowest = null;
        int lowestKey = Integer.MAX_VALUE;
        for (String key : tiers.getKeys(false)) {
            ConfigurationSection section = tiers.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            int number;
            try {
                number = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                continue;
            }
            if (number <= streak && number > bestKey) {
                best = section;
                bestKey = number;
            }
            if (number < lowestKey) {
                lowest = section;
                lowestKey = number;
            }
        }
        return best != null ? best : lowest;
    }

    private static String pick(List<String> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }

    /** Replaces key/value pairs: fill(text, "{a}", "1", "{b}", "2"). */
    private static String fill(String text, String... pairs) {
        String out = text;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    /** A sound by its namespaced key (e.g. entity.wither.spawn); null if the key is malformed. */
    private static Sound sound(String key, float pitch) {
        try {
            return Sound.sound(Key.key(key.toLowerCase(Locale.ROOT)), Sound.Source.MASTER, 0.7f, pitch);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
