# MrTahaDarvish's UnstableFFA

An "unstable SMP" style FFA plugin for **Paper 1.21.11** (Java 21).

- Coins: **2 coins per kill** (configurable)
- `/kit` shop menu: admins add kits + prices, bought kits are yours **forever**
- Lobby where players can do nothing (no PvP, building, breaking, potions, items, inventory moves)
- Built-in world manager (create / import worlds, like Multiverse-Core)
- End-portal system: you place an end portal, link it to an arena, players jump in and fight
- Arena maps **reset every 15 minutes**: explosions, cobwebs, lava, fire, placed blocks... all undone, the map itself is restored exactly

## Build

You need JDK 21 and Maven:

```
mvn package
```
(or double-click `build.bat` on Windows / run `./build.sh`)

The jar is `target/UnstableFFA-1.0.0.jar`. Drop it in your server's `plugins/` folder and restart.

## Setup walkthrough (as admin / OP)

1. **Lobby**: stand in your lobby world where players should spawn and run `/ufa setlobby`.
   Players get no permissions there. You (OP) can build in the lobby only while in **creative** mode.
2. **Get your map worlds in**
   - Copy a map folder (e.g. `capital_city`) into the server root, then `/ufa world create capital_city void`
     (use `void` so nothing generates outside the map; `normal` generates terrain around it).
   - Or create a fresh one: `/ufa world create law_castle void`, `/ufa world tp law_castle`, then build/paste.
   - Worlds are remembered and reloaded on every restart.
3. **Make it an arena**: `/ufa world tp capital_city`, then `/ufa arena create capital_city`.
   It starts in *edit mode*: nothing is recorded, you can build freely in creative.
4. **Add spawn points**: stand where players should appear and run `/ufa arena addspawn capital_city` (repeat for more; one is chosen at random).
5. **Lock it**: `/ufa arena lock capital_city`. The map exactly as it is now is the saved map.
   From now on every block change is recorded and undone every 15 minutes.
   Need to change the map later? `/ufa arena edit capital_city` (undoes any player changes first), edit, lock again.
6. **Portal**: in the lobby build a normal end portal (12 end portal frames + eyes of ender), stand next to it and run
   `/ufa portal create capital capital_city`. Everyone who steps into that portal is sent to the arena with their selected kit.
7. **Kits**: put the items you want in your inventory (armor slots + offhand count too) and run
   `/kit create knight 50` (price 50 coins; `0` = free). Then `/kit seticon knight` while holding the item you want as the menu icon, or `/kit seticon knight head Notch` to use a player's head (skin fetched automatically). You can also do it at creation: `/kit create knight 50 Notch`.

## Commands

| Command | Who | What |
|---|---|---|
| `/kit` | everyone | open the kit menu (click to select / buy) |
| `/kit create <name> [price]` | admin | save your inventory as a kit |
| `/kit update <name>` | admin | overwrite a kit with your inventory |
| `/kit setprice <name> <price>` | admin | change a price (owners keep the kit) |
| `/kit seticon <name>` | admin | hand item = menu icon |
| `/kit delete <name>`, `/kit list` | admin | |
| `/kit give <player> <name>` / `revoke` | admin | unlock / remove a kit for someone |
| `/coins [player]` | everyone | show coins |
| `/coins give\|take\|set <player> <n>` | admin | |
| `/lobby` (`/hub`, `/spawn`) | everyone | go to the lobby |
| `/ufa setlobby` | admin | set lobby spawn |
| `/ufa world create <name> [normal\|flat\|void\|nether\|end]` | admin | create or import a world |
| `/ufa world list` / `/ufa world tp <name>` | admin | |
| `/ufa arena create\|addspawn\|clearspawns\|lock\|edit\|reset\|list\|delete` | admin | arena management |
| `/ufa portal create <id> <arena> [more arenas...]` / `addarena` / `removearena` / `setarenas` / `next` / `remove` / `list` | admin | end-portal links (one portal can rotate through many arenas) |
| `/kit edit` (or right-click an anvil in the lobby) | everyone | rearrange the slots of your kits |
| `/bounty` (`/bounties`, `/streak`) | everyone | who has a bounty right now + your own streak |
| `/leaderboard [kills\|deaths\|coins\|streak]` | everyone | top 10 |
| `/shop addtitle <id> <price> <color1> <color2> <Title_Words>` | admin | add a chat title to the shop |
| `/ufa reload` | admin | reload config + kits |

Permissions: `ufa.use` (default everyone), `ufa.admin` (default OP).

## How the player loop works

1. Join -> teleported to the lobby with a **Kit Selector** (nether star) in the hotbar.
2. `/kit` or the star -> pick or buy a kit. A free `starter` kit exists by default.
3. Jump into an end portal -> teleported to the arena spawn with the selected kit, 3 seconds of spawn protection.
4. Kill someone -> **+2 coins**. Die -> nothing drops, you respawn in the lobby automatically.

## How the 30 minute map reset works

When an arena is locked the plugin records the **original state of each block the first time it changes**
(placing, breaking, TNT / creeper / bed / crystal explosions, fire, lava / water flow, pistons, falling sand, tree and crop growth, ice / snow, etc.).
Every 30 minutes (players get warnings at 60/30/10/5..1 seconds) it:

1. sends everyone in the arena to the lobby (configurable),
2. removes leftover entities (dropped items, primed TNT, arrows, pearls, end crystals, ...),
3. puts every recorded block back over a few ticks (no lag spike).

Because only changed blocks are stored, the map itself is never copied or touched. Pending changes are also
restored when the server stops, so the map is never left damaged.

## Config

`plugins/UnstableFFA/config.yml`: coins per kill, reset interval (default 30), warning times, blocks restored per tick,
spawn protection, auto respawn, which entities get cleared on reset, and the chat prefix.

## Known limits

- Chest / furnace / other container contents are not tracked, so players are blocked from opening containers in live arenas (`arena.block-containers`).
- Item frames and armor stands in live arenas can't be edited by players (their changes can't be restored).
- If the server **crashes** (not a normal stop) while an arena has unreset changes, those changes are lost from memory and the map stays damaged. Re-copy the original map folder in that case.
- This source was written against the Paper 1.21.11 API but has **not been compiled or run on a server yet** (the build environment could not reach the Paper Maven repository). If `mvn package` reports an API mismatch, send the error message and it can be fixed quickly.


## New in this version

- **Kit names**: keep capital letters (`/kit create Knight 50`), shown in plain white. `_` shows as a space (`Fire_Mage` -> "Fire Mage"). Rename with `/kit rename <kit> <New_Name>`. Existing kits keep their old lowercase name until you rename them.
- **Shulker boxes** work in arenas: open, fill, empty, place and break. (Chests, furnaces etc. stay blocked.)
- **Scoreboard**: sidebar with `UnstableFFA`, `Active Players` and `Coins`. Config: `scoreboard.*` (`active-players: arena` counts fighters, `online` counts everyone).
- **/shop**: barrel-sized menu of sections; each section holds items for coins. Items are delivered into your inventory the next time you enter an arena (a kit replaces the inventory, so purchases wait for you). 
  - `/shop addsection <id> [Title_Words]` (icon = item in hand), `/shop settitle`, `/shop seticon <section> [head <player>]`, `/shop removesection`
  - `/shop additem <section> <price>` sells the stack in your hand, `/shop removeitem <section> <number>`, `/shop setprice <section> <number> <price>`, `/shop list`
- **Rotating portal**: give a portal several arenas and it cycles through them. Every 15 minutes the open arena resets, players in it go to the lobby, and the portal moves on to the next arena.
  - `/ufa portal create <id> arena1 arena2 arena3` or, for an existing portal, `/ufa portal setarenas <id> arena1 arena2 arena3`
  - `/ufa portal next <id>` forces the switch right now. Arenas waiting for their turn don't run their own timer.
- Old `config.yml` files are upgraded automatically on start (reset time becomes 15 minutes).


## Update 3

- **Shop in arenas**: players can use `/shop` inside arenas as long as they have at least 1 empty inventory slot. Bought items go straight into the inventory (put your Orbital Cannons / Dog Cannons in with `/shop additem <section> <price>` while holding them). In the lobby a purchase still waits and is added when the player enters an arena.
- **Pets die with their owner**: every tamed animal (wolves, cats, horses, dog-cannon dogs...) of a player is removed when that player dies in an arena, leaves the arena (`/lobby`, map reset) or quits.
- **Leaderboard**: kills and deaths are now tracked. `/leaderboard [kills|deaths|coins]` (aliases `/lb`, `/top`). `/ufa leaderboard set` places a floating top-killers hologram where you stand (updates every 30 s); `/ufa leaderboard remove` deletes it.
- **/playerhead <name>** (admin): gives the head with that player's real skin, e.g. `/playerhead Wemmbu`. Hold it and run `/kit seticon <kit>` or `/shop seticon <section>` to use it as an icon. If the server can't reach Mojang you get a name-only head plus a hint; in that case use `/playerhead texture <value>` with a texture value from minecraft-heads.com.
- **Portal hologram**: floating text above every portal with the arena name, **Active Players** in that arena and the countdown to the next reset/map change. Config: `hologram.enabled`.


## Update 4

- `/playerhead <name>` now asks Mojang directly (with a backup service) and caches every skin in `skins.yml`. If it fails, the message says why (name doesn't exist, rate limited, server can't reach Mojang) and the reason is also printed in the console. The `/head` alias was removed to avoid clashing with other plugins.
- Kills now also count for projectiles, primed TNT and a player's tamed pets (dog cannons). `/leaderboard` also shows your own number; the `/top` alias was removed (EssentialsX uses it).


## Update 5 (build 6)

Check `/ufa version` after installing - it must say **build 6**. Delete the old UnstableFFA jar from `plugins/` first.

### One portal, many arenas (no more glitchy holograms)
- Put all your arenas on **one** portal: `/ufa portal create main arena1 arena2 arena3 arena4`.
- Add or remove arenas later: `/ufa portal addarena main arena5`, `/ufa portal removearena main arena2`, `/ufa portal setarenas main a b c d`.
- Every reset interval (15 min) the open arena resets and the portal moves to the next arena. `/ufa portal next main` forces the switch.
- Creating a second portal on the same end-portal blocks is now refused (it tells you to use `addarena`), and duplicate holograms at the same spot are drawn only once.
- If you already made extra portals by mistake: `/ufa portal list`, `/ufa portal remove <extra id>`, then `/ufa portal addarena <main id> <arena>`.

### Chat titles in the shop
- `/shop addtitle <id> <price> <color1> <color2> <Title_Words>` - `_` becomes a space, colors are names (`red`, `pink`, `gray`, `white`, `gold`...) or hex (`#ff5555`).
  - `/shop addtitle lostcause 300 red pink Lost_Cause`
  - `/shop addtitle invisibleknight 350 gray white Invisible_Knight`
- Other title commands: `/shop removetitle <id>`, `/shop titleprice <id> <price>`, `/shop givetitle <player> <id>`, `/shop list`.
- Players see a **Titles** entry in `/shop`. Click to buy (it equips automatically); click an owned title to equip / unequip it. In chat it shows as `Lost Cause Name » message` with the gradient.
- Config: `titles.show-in-chat`.

### Kit editor
- In the lobby, right-click an **anvil** (or `/kit edit`), pick one of your kits and move its items around like your own inventory (bottom row = hotbar). You can't take, add, use or duplicate anything - the editor only lets you swap slots.
- **Save layout** stores it for you; from then on that kit is always given in your layout. **Reset to default** removes it. Armor and off-hand stay as the kit defines them.
- If an admin changes the items of a kit (`/kit update`), players' saved layouts for that kit are ignored until they save again.

### Kill streaks and bounties
- Kills in a row (no death, not leaving the arena) build a **streak**. Every **5 kills** is a milestone with its own announcement for everyone, a big title, a sound and a coin bonus:
  - 5 = **Dominating**, 10 = **Rampage**, 15 = **Unstoppable**, 20 = **Godlike**, 25 = **Legendary**, 30+ = **Beyond Unstable** (the bigger ones also strike a harmless lightning effect).
  - e.g. `{player} is Dominating with 5 streaks!` / `{player} is on a RAMPAGE with 10 kills!` - every tier has several variants and one is picked at random.
- From 5 kills on there is a **bounty** on the player: `(streak / 5) * 10` coins. Whoever kills them gets it on top of the normal 2 coins and the server announces it ("BOUNTY CLAIMED..."). Dying alone or leaving the arena makes the bounty vanish ("ran away from a 10 kill streak").
- `/bounty` lists every active bounty and your own streak; `/leaderboard streak` shows the best streaks ever. Your action bar shows your streak after every kill.
- Everything is in `config.yml` under `bounty:` - step, coin amounts, which streak gets which title, sounds, and all message lists (MiniMessage, placeholders `{player} {streak} {bounty}` and `{killer} {victim}`). Add as many messages as you like. `bounty.enabled: false` turns it off, `bounty.broadcast: arena` shows announcements only to players inside arenas.


## Update 6 (build 7) - titles replace the LuckPerms prefix in chat

- A player who wears a shop title now shows in chat as `Title Username » message`. The rank prefix from LuckPerms (or from the chat plugin that shows it: EssentialsX Chat, LPC, etc.) is **not** shown for them. Players without a title keep your normal chat format.
- UnstableFFA now handles both the modern Paper chat event and the old one that most chat plugins still use, and runs after them, so its format wins.
- Config: `titles.replace-rank-prefix: true` (default). Set it to `false` to keep the rank prefix and only add the title in front of it.
- Not covered: chat plugins that cancel the chat event and send their own messages (e.g. ChatControl, VentureChat). For those, turn off their prefix for chat or tell me which plugin it is.


## Update 7 (build 8) - titles in the tab list (TAB plugin)

- A player's equipped shop title is now also shown in front of their name in the **tab list**. With the **TAB** plugin it is given to TAB as the player's tab prefix through TAB's API (no setup in TAB needed, `tablist-name-formatting` must be enabled in TAB's `config.yml`, which it is by default). Without TAB the normal player list name is used.
- Players without a title are left exactly as TAB shows them. If TAB is reloaded (`/tab reload`) the titles come back within 2 seconds.
- Config: `titles.show-in-tab: true` (set to `false` to turn it off).
- If TAB cannot be controlled (very old or changed TAB version) the console prints a `Could not put the title into the TAB list...` warning with the reason.
- Not changed: the name tag above the head and the TAB sidebar/belowname. Ask if you want the title there too.
