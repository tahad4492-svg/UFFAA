package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /bounty - who has a bounty on their head right now, and your own streak. */
public final class BountyCommand implements TabExecutor {

    private final UnstableFFA plugin;

    public BountyCommand(UnstableFFA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        StreakManager streaks = plugin.streaks();
        if (!streaks.enabled()) {
            plugin.send(sender, "<gray>The bounty system is turned off.");
            return true;
        }

        sender.sendMessage(plugin.parse("<gold><bold>Bounties <gray>- end their streak to collect the coins"));
        List<Map.Entry<UUID, Integer>> active = streaks.activeBounties();
        if (active.isEmpty()) {
            sender.sendMessage(plugin.parse("<gray>Nobody has a bounty right now. Get <white>" + streaks.step()
                    + " kills <gray>in a row to put one on yourself!"));
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Integer> entry : active) {
                Player target = Bukkit.getPlayer(entry.getKey());
                String name = target != null ? target.getName() : plugin.data().getName(entry.getKey());
                sender.sendMessage(plugin.parse("<red>#" + rank + " <white>" + UnstableFFA.esc(name)
                        + " <dark_gray>- <white>" + entry.getValue() + " <gray>streak <dark_gray>- <yellow>"
                        + streaks.bountyOf(entry.getValue()) + " coins"));
                rank++;
                if (rank > 10) {
                    break;
                }
            }
        }

        if (sender instanceof Player p) {
            int mine = streaks.get(p.getUniqueId());
            long best = plugin.data().getStat(p.getUniqueId(), "beststreak");
            String progress = mine >= streaks.step()
                    ? "<yellow>" + streaks.bountyOf(mine) + " coin bounty on your head"
                    : (streaks.step() - mine) + " more kill" + (streaks.step() - mine == 1 ? "" : "s") + " until you get a bounty";
            sender.sendMessage(plugin.parse("<gray>Your streak: <white>" + mine + " <dark_gray>(<gray>" + progress
                    + "<dark_gray>) <gray>Best: <white>" + best));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
