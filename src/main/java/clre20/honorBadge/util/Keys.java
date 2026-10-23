package clre20.honorBadge.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * PersistentDataContainer 標籤：honorbadge:gui（介面簽署）、honorbadge:action、honorbadge:value。
 */
public final class Keys {

    private final NamespacedKey gui;
    private final NamespacedKey action;
    private final NamespacedKey value;

    public Keys(Plugin plugin) {
        this.gui = new NamespacedKey(plugin, "gui");
        this.action = new NamespacedKey(plugin, "action");
        this.value = new NamespacedKey(plugin, "value");
    }

    public NamespacedKey gui() { return gui; }
    public NamespacedKey action() { return action; }
    public NamespacedKey value() { return value; }
}
