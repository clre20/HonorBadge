package clre20.honorBadge.gui;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.title.Title;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理列表（/hb list）：列出所有稱號，點擊進入編輯。
 */
public final class AdminListMenu extends Menu {

    private static final int PAGE_SIZE = 45;

    private int page;
    private final int pages;

    public AdminListMenu(HonorBadge plugin, UUID viewer, int page) {
        super(plugin, viewer);
        int count = plugin.titles().size();
        this.pages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(page, pages - 1));
    }

    @Override
    protected Component title() {
        return plugin.messages().get("gui.admin-list.title",
                Map.of("page", String.valueOf(page + 1), "pages", String.valueOf(pages)));
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        List<Title> titles = plugin.titles().sorted();
        if (titles.isEmpty()) {
            set(22, button("empty", Material.STRUCTURE_VOID, "gui.admin-list.empty", Map.of(), null, null));
        }
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < titles.size(); i++) {
            Title title = titles.get(start + i);
            Map<String, String> ph = Map.of(
                    "id", title.id(),
                    "holders", String.valueOf(plugin.players().holderCount(title.id())),
                    "sort", String.valueOf(title.sort()));
            List<Component> lore = plugin.messages().itemLore("gui.admin-list.title-lore", ph, title.lore());
            // 說明已由 {lore} 展開，這裡不重複加入
            Title noLore = new Title(title.id(), title.displayName(), title.icon(), List.of(), title.sort(),
                    title.created(), plugin.settings().titleFormat());
            set(i, ItemFactory.title(plugin, noLore, lore, "edit", false));
        }
        if (page > 0) {
            set(45, button("previous-page", Material.ARROW, "gui.player.previous", Map.of(), "prev", null));
        }
        if (page < pages - 1) {
            set(53, button("next-page", Material.ARROW, "gui.player.next", Map.of(), "next", null));
        }
        set(49, button("close", Material.BARRIER, "gui.player.close", Map.of(), "close", null));
        fillRow(5);
    }

    @Override
    public void onClick(Player player, String action, String value) {
        switch (action) {
            case "prev" -> {
                plugin.sounds().click(player);
                plugin.sync(() -> new AdminListMenu(plugin, viewer, page - 1).open(player));
            }
            case "next" -> {
                plugin.sounds().click(player);
                plugin.sync(() -> new AdminListMenu(plugin, viewer, page + 1).open(player));
            }
            case "close" -> {
                plugin.sounds().click(player);
                plugin.sync(player::closeInventory);
            }
            case "edit" -> {
                if (!player.hasPermission("honorbadge.edit")) {
                    plugin.messages().send(player, "general.no-permission");
                    plugin.sounds().fail(player);
                    return;
                }
                if (!plugin.titles().exists(value)) {
                    plugin.messages().send(player, "general.title-not-found", Map.of("id", value));
                    refresh();
                    return;
                }
                plugin.sounds().click(player);
                plugin.sync(() -> EditMenu.start(plugin, player, value, false));
            }
            default -> {
            }
        }
    }
}
