package clre20.honorBadge.util;

import clre20.honorBadge.config.ConfigManager;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/**
 * 介面音效，使用音效 ID 字串，跨版本相容。
 */
public final class Sounds {

    private final Supplier<ConfigManager> settings;

    public Sounds(Supplier<ConfigManager> settings) {
        this.settings = settings;
    }

    public void click(Player player) {
        play(player, settings.get().soundClick());
    }

    public void success(Player player) {
        play(player, settings.get().soundSuccess());
    }

    public void fail(Player player) {
        play(player, settings.get().soundFail());
    }

    private void play(Player player, String sound) {
        ConfigManager config = settings.get();
        if (config == null || !config.soundsEnabled() || sound == null || sound.isBlank()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException ignored) {
            // 音效 ID 格式錯誤時略過
        }
    }
}
