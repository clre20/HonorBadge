package clre20.honorBadge.gui;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.title.Title;
import clre20.honorBadge.util.ColorUtil;
import com.google.common.collect.ImmutableMultimap;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 介面物品產生器。每個物品都寫入 honorbadge:gui 標籤做為插件簽署，
 * 按鈕另外寫入 honorbadge:action／honorbadge:value。
 */
public final class ItemFactory {

    private ItemFactory() {
    }

    public static ItemStack create(HonorBadge plugin, Material material, Component name, List<Component> lore,
                                   String action, String value, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(name);
        meta.lore(lore);
        hideTooltipExtras(meta);
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(plugin.keys().gui(), PersistentDataType.STRING, plugin.getName());
        if (action != null) {
            pdc.set(plugin.keys().action(), PersistentDataType.STRING, action);
        }
        if (value != null) {
            pdc.set(plugin.keys().value(), PersistentDataType.STRING, value);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack filler(HonorBadge plugin) {
        Material material = plugin.settings().guiItem("filler", Material.GRAY_STAINED_GLASS_PANE);
        return create(plugin, material, ColorUtil.item(plugin.messages().raw("gui.filler-name")),
                List.of(), null, null, false);
    }

    /** 稱號物品：名稱＝顯示名稱，說明＝稱號說明 + extraLore。 */
    public static ItemStack title(HonorBadge plugin, Title title, List<Component> extraLore,
                                  String action, boolean glow) {
        List<Component> lore = new ArrayList<>(title.lore().size() + extraLore.size());
        for (String line : title.lore()) {
            lore.add(ColorUtil.item(line));
        }
        lore.addAll(extraLore);
        Component name = title.nameComponent().decorationIfAbsent(
                net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
        return create(plugin, title.icon(), name, lore, action, title.id(), glow);
    }

    private static void hideTooltipExtras(ItemMeta meta) {
        try {
            // 1.20.5 之後要有屬性修飾器，HIDE_ATTRIBUTES 才會隱藏武器傷害等資訊
            meta.setAttributeModifiers(ImmutableMultimap.of());
        } catch (Throwable ignored) {
            // 部分版本不支援時略過
        }
        meta.addItemFlags(ItemFlag.values());
    }
}
