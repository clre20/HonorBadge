package clre20.honorBadge.gui;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.title.Title;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * 刪除確認介面。
 */
public final class DeleteMenu extends Menu {

    private final String id;

    public DeleteMenu(HonorBadge plugin, UUID viewer, String id) {
        super(plugin, viewer);
        this.id = id;
    }

    @Override
    protected Component title() {
        return plugin.messages().get("gui.delete.title", Map.of("id", id));
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void render() {
        Title title = plugin.titles().get(id);
        Map<String, String> ph = Map.of(
                "id", id,
                "title", title == null ? id : title.displayName(),
                "holders", String.valueOf(plugin.players().holderCount(id)));
        set(11, button("delete-confirm", Material.RED_CONCRETE, "gui.delete.confirm", ph, "confirm", null));
        Material infoIcon = title == null ? plugin.settings().guiItem("delete-info", Material.PAPER) : title.icon();
        set(13, ItemFactory.create(plugin, infoIcon,
                plugin.messages().itemName("gui.delete.info.name", ph),
                plugin.messages().itemLore("gui.delete.info.lore", ph), null, null, false));
        set(15, button("delete-cancel", Material.LIME_CONCRETE, "gui.delete.cancel", ph, "cancel", null));
        fillAll();
    }

    @Override
    public void onClick(Player player, String action, String value) {
        switch (action) {
            case "confirm" -> confirm(player);
            case "cancel" -> {
                plugin.messages().send(player, "delete.cancelled");
                plugin.sounds().click(player);
                plugin.sync(player::closeInventory);
            }
            default -> {
            }
        }
    }

    private void confirm(Player player) {
        plugin.sync(player::closeInventory);
        Title title = plugin.titles().get(id);
        if (title == null) {
            plugin.messages().send(player, "general.title-not-found", Map.of("id", id));
            plugin.sounds().fail(player);
            return;
        }
        UUID lock = plugin.edits().lockedBy(id);
        if (lock != null) {
            String editor = plugin.players().nameOf(lock);
            plugin.messages().send(player, "general.title-being-edited", Map.of("id", id, "editor", editor));
            plugin.sounds().fail(player);
            return;
        }
        plugin.titleService().delete(player, title);
    }
}
