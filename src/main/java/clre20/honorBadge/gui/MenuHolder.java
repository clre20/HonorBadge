package clre20.honorBadge.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * HonorBadge 箱子介面的簽署：所有介面都綁定這個 Holder，
 * 其他插件可用 {@code inventory.getHolder() instanceof MenuHolder} 或 {@link #getPlugin()} 辨識來源。
 * 介面內的每個按鈕物品也帶有 PersistentDataContainer 標籤 {@code honorbadge:gui}。
 */
public final class MenuHolder implements InventoryHolder {

    private final Plugin plugin;
    private final Menu menu;
    private Inventory inventory;

    MenuHolder(Plugin plugin, Menu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public Menu menu() {
        return menu;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
