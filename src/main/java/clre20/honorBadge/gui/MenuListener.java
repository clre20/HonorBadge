package clre20.honorBadge.gui;

import clre20.honorBadge.HonorBadge;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 箱子介面事件：只處理 HonorBadge 的介面，其他介面立即略過。
 * 所有點擊一律取消（物品拿不走），只有「左鍵點擊上方介面的按鈕」才會執行動作。
 */
public final class MenuListener implements Listener {

    private final HonorBadge plugin;

    public MenuListener(HonorBadge plugin) {
        this.plugin = plugin;
    }

    private static MenuHolder holderOf(Inventory top) {
        return top.getHolder(false) instanceof MenuHolder holder ? holder : null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        MenuHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getClick() != ClickType.LEFT || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(plugin.keys().action(), PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        String value = pdc.get(plugin.keys().value(), PersistentDataType.STRING);
        holder.menu().onClick(player, action, value);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (holderOf(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        MenuHolder holder = holderOf(event.getInventory());
        if (holder != null && event.getPlayer() instanceof Player player) {
            holder.menu().onClose(player);
        }
    }
}
