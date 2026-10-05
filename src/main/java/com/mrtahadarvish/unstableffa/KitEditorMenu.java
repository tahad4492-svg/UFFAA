package com.mrtahadarvish.unstableffa;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kit editor (right-click an anvil in the lobby, or /kit edit).
 * First screen: pick one of your kits. Second screen: a copy of the kit's 36 slots that you can
 * only rearrange - you can't add, remove or use anything. Save stores your layout, and from then
 * on that kit always comes in your arrangement.
 */
public final class KitEditorMenu implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_INFO = 47;
    private static final int SLOT_SAVE = 49;
    private static final int SLOT_RESET = 51;
    private static final int PER_PAGE = 45;
    /** Armor display slots in the editor: helmet, chestplate, leggings, boots. */
    private static final int[] ARMOR_GUI = {38, 39, 40, 41};
    private static final int SLOT_OFFHAND = 43;

    private final UnstableFFA plugin;
    private final KitManager.Kit kit;                       // null = kit chooser
    private final Map<Integer, String> slotToKit = new HashMap<>();
    private final Inventory inventory;

    // ---- opening ---------------------------------------------------------------------

    public static void openChooser(UnstableFFA plugin, Player player) {
        player.openInventory(new KitEditorMenu(plugin, player, null).inventory);
    }

    public static void openEditor(UnstableFFA plugin, Player player, KitManager.Kit kit) {
        player.openInventory(new KitEditorMenu(plugin, player, kit).inventory);
    }

    private KitEditorMenu(UnstableFFA plugin, Player player, KitManager.Kit kit) {
        this.plugin = plugin;
        this.kit = kit;
        Component title = kit == null
                ? plugin.parse("<dark_gray>Kit Editor - pick a kit")
                : plugin.parse("<dark_gray>Editing: " + UnstableFFA.esc(kit.display()));
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        if (kit == null) {
            buildChooser(player);
        } else {
            buildEditor(player);
        }
    }

    public boolean isChooser() {
        return kit == null;
    }

    // ---- chooser ---------------------------------------------------------------------------

    private void buildChooser(Player player) {
        UUID id = player.getUniqueId();
        int slot = 0;
        for (KitManager.Kit k : plugin.kits().all()) {
            boolean owned = k.price <= 0 || plugin.data().ownsKit(id, k.id);
            if (!owned || slot >= PER_PAGE) {
                continue;
            }
            ItemStack icon = k.icon != null ? k.icon.clone() : new ItemStack(Material.CHEST);
            icon.setAmount(1);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(plugin.parse("<!italic><white>" + UnstableFFA.esc(k.display())));
            List<Component> lore = new ArrayList<>();
            ItemStack[] saved = plugin.data().getLayout(id, k.id);
            boolean custom = saved != null && KitManager.sameItems(k.contents, saved);
            lore.add(plugin.parse("<!italic>" + (custom ? "<green>Your own layout is saved" : "<gray>Default layout")));
            lore.add(plugin.parse("<!italic><gray>Click to rearrange this kit"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
            slotToKit.put(slot, k.id);
            slot++;
        }
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(49, named(Material.ANVIL, "<white>Kit Editor"));
        if (slot == 0) {
            inventory.setItem(22, named(Material.BARRIER, "<gray>You don't own any kits yet - buy one in /kit"));
        }
    }

    public void handleChooserClick(Player player, int slot) {
        String kitId = slotToKit.get(slot);
        KitManager.Kit k = kitId == null ? null : plugin.kits().get(kitId);
        if (k != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
            openEditor(plugin, player, k);
        }
    }

    // ---- editor ---------------------------------------------------------------------------------

    /** GUI slot of a kit slot: main inventory (9-35) on the top three rows, hotbar (0-8) on the fourth. */
    private static int guiSlot(int kitSlot) {
        return kitSlot >= 9 ? kitSlot - 9 : 27 + kitSlot;
    }

    private static int kitSlot(int guiSlot) {
        return guiSlot < 27 ? guiSlot + 9 : guiSlot - 27;
    }

    public boolean isEditable(int guiSlot) {
        return kit != null && guiSlot >= 0 && guiSlot < 36;
    }

    private void buildEditor(Player player) {
        UUID id = player.getUniqueId();
        ItemStack[] start = kit.contents;
        ItemStack[] saved = plugin.data().getLayout(id, kit.id);
        if (saved != null && KitManager.sameItems(kit.contents, saved)) {
            start = saved;
        }
        for (int k = 0; k < 36; k++) {
            ItemStack item = k < start.length ? start[k] : null;
            inventory.setItem(guiSlot(k), item == null ? null : item.clone());
        }

        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 36; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        // armor and off-hand are shown but cannot be moved
        for (int i = 0; i < 4; i++) {
            ItemStack armor = i < kit.armor.length ? kit.armor[3 - i] : null;   // kit stores boots first
            inventory.setItem(ARMOR_GUI[i], armor == null ? named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>No armor") : armor.clone());
        }
        inventory.setItem(SLOT_OFFHAND, kit.offhand == null
                ? named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>No off-hand item") : kit.offhand.clone());

        inventory.setItem(SLOT_BACK, named(Material.ARROW, "<gray>Back"));
        ItemStack info = named(Material.PAPER, "<white>How it works");
        ItemMeta infoMeta = info.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.parse("<!italic><gray>Move the items to the slots you like."));
        lore.add(plugin.parse("<!italic><gray>Bottom row = hotbar. Armor is fixed."));
        lore.add(plugin.parse("<!italic><gray>Press Save when you're done."));
        infoMeta.lore(lore);
        info.setItemMeta(infoMeta);
        inventory.setItem(SLOT_INFO, info);
        inventory.setItem(SLOT_SAVE, named(Material.LIME_DYE, "<green><bold>Save layout"));
        inventory.setItem(SLOT_RESET, named(Material.REDSTONE, "<red>Reset to default"));
    }

    /** Buttons of the editor (the item slots are handled by the listener). */
    public void handleButton(Player player, int slot, ItemStack cursor) {
        if (kit == null) {
            return;
        }
        if (slot == SLOT_BACK) {
            openChooser(plugin, player);
        } else if (slot == SLOT_SAVE) {
            save(player, cursor);
        } else if (slot == SLOT_RESET) {
            plugin.data().clearLayout(player.getUniqueId(), kit.id);
            plugin.send(player, "<gray>Layout reset to the default of <white>" + UnstableFFA.esc(kit.display()) + "<gray>.");
            openEditor(plugin, player, kit);
        }
    }

    private void save(Player player, ItemStack cursor) {
        if (cursor != null && !cursor.getType().isAir()) {
            plugin.send(player, "<red>Put the item you are holding down first, then save.");
            return;
        }
        ItemStack[] layout = new ItemStack[36];
        for (int g = 0; g < 36; g++) {
            ItemStack item = inventory.getItem(g);
            layout[kitSlot(g)] = (item == null || item.getType().isAir()) ? null : item.clone();
        }
        KitManager.Kit live = plugin.kits().get(kit.id);
        if (live == null || !KitManager.sameItems(live.contents, layout)) {
            plugin.send(player, "<red>That layout doesn't match the kit's items (the kit may have been changed). Reopen the editor.");
            return;
        }
        plugin.data().setLayout(player.getUniqueId(), kit.id, layout);
        plugin.send(player, "<green>Saved! <white>" + UnstableFFA.esc(kit.display()) + "<green> will always come in your layout now.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
    }

    private ItemStack named(Material material, String miniMessageName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.parse("<!italic>" + miniMessageName));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
