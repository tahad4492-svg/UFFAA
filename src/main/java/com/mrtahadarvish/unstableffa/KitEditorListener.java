package com.mrtahadarvish.unstableffa;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Inside the kit editor the player may only pick up and put down the kit's items between the
 * editor slots. Everything else (shift-click, number keys, drops, drags, the player's own
 * inventory...) is cancelled, so nothing can be added, removed or duplicated.
 */
public final class KitEditorListener implements Listener {

    private static final Set<InventoryAction> ALLOWED = EnumSet.of(
            InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_SOME, InventoryAction.PICKUP_HALF, InventoryAction.PICKUP_ONE,
            InventoryAction.PLACE_ALL, InventoryAction.PLACE_SOME, InventoryAction.PLACE_ONE,
            InventoryAction.SWAP_WITH_CURSOR);

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof KitEditorMenu menu)) {
            return;
        }
        e.setCancelled(true);   // everything is blocked unless allowed below
        if (!(e.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;   // clicks in the player's own inventory do nothing
        }
        int slot = e.getSlot();
        if (menu.isChooser()) {
            menu.handleChooserClick(player, slot);
            return;
        }
        if (menu.isEditable(slot)) {
            if (ALLOWED.contains(e.getAction())) {
                e.setCancelled(false);
            }
            return;
        }
        menu.handleButton(player, slot, e.getCursor());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof KitEditorMenu) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof KitEditorMenu && e.getPlayer() instanceof Player player) {
            player.setItemOnCursor(null);   // never let a held copy of a kit item leak into the real inventory
        }
    }
}
