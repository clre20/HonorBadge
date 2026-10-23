package clre20.honorBadge.gui;

import clre20.honorBadge.HonorBadge;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 箱子介面的共同基底。介面只存觀看者的 UUID，不持有 Player。
 */
public abstract class Menu {

    protected final HonorBadge plugin;
    protected final UUID viewer;
    protected Inventory inventory;

    protected Menu(HonorBadge plugin, UUID viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract Component title();

    protected abstract int rows();

    protected abstract void render();

    /** 左鍵點擊帶有動作標籤的按鈕時呼叫。 */
    public abstract void onClick(Player player, String action, String value);

    /** 介面被關閉時呼叫（包含換到另一個介面）。 */
    public void onClose(Player player) {
    }

    public final void open(Player player) {
        MenuHolder holder = new MenuHolder(plugin, this);
        inventory = Bukkit.createInventory(holder, rows() * 9, title());
        holder.setInventory(inventory);
        render();
        player.openInventory(inventory);
    }

    /** 重新繪製目前開著的介面（標題不變）。 */
    public final void refresh() {
        if (inventory != null) {
            inventory.clear();
            render();
        }
    }

    // ---------- 工具 ----------

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected ItemStack button(String guiItemKey, Material fallback, String msgKey, Map<String, String> ph,
                               String action, String value) {
        Material material = plugin.settings().guiItem(guiItemKey, fallback);
        Component name = plugin.messages().itemName(msgKey + ".name", ph);
        List<Component> lore = plugin.messages().itemLore(msgKey + ".lore", ph);
        return ItemFactory.create(plugin, material, name, lore, action, value, false);
    }

    protected void fillRow(int row) {
        ItemStack filler = ItemFactory.filler(plugin);
        for (int i = row * 9; i < row * 9 + 9; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    protected void fillAll() {
        ItemStack filler = ItemFactory.filler(plugin);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }
}
