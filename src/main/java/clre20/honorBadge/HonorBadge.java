package clre20.honorBadge;

import clre20.honorBadge.command.HonorBadgeCommand;
import clre20.honorBadge.compatibility.NativeDisplay;
import clre20.honorBadge.compatibility.PlaceholderHook;
import clre20.honorBadge.config.ConfigManager;
import clre20.honorBadge.config.MessageManager;
import clre20.honorBadge.edit.ChatInputManager;
import clre20.honorBadge.edit.EditSessionManager;
import clre20.honorBadge.gui.MenuHolder;
import clre20.honorBadge.gui.MenuListener;
import clre20.honorBadge.listener.ChatListener;
import clre20.honorBadge.listener.PlayerListener;
import clre20.honorBadge.player.PlayerDataManager;
import clre20.honorBadge.service.TitleService;
import clre20.honorBadge.title.TitleManager;
import clre20.honorBadge.update.UpdateChecker;
import clre20.honorBadge.util.Keys;
import clre20.honorBadge.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class HonorBadge extends JavaPlugin {

    // 執行緒：檔案讀寫一條、網路一條，不會無限制增加
    private ExecutorService io;
    private ExecutorService net;

    private Keys keys;
    private MessageManager messages;
    private ConfigManager settings;
    private Sounds sounds;
    private TitleManager titles;
    private PlayerDataManager players;
    private EditSessionManager edits;
    private ChatInputManager chatInput;
    private NativeDisplay nativeDisplay;
    private TitleService titleService;
    private UpdateChecker updates;
    private PlaceholderHook placeholderHook;

    @Override
    public void onEnable() {
        io = Executors.newSingleThreadExecutor(namedThread("HonorBadge-IO"));
        net = Executors.newSingleThreadExecutor(namedThread("HonorBadge-Net"));

        keys = new Keys(this);
        messages = new MessageManager(this);
        settings = new ConfigManager(this);
        messages.load();
        settings.load();
        sounds = new Sounds(() -> settings);

        titles = new TitleManager(this);
        titles.load();
        players = new PlayerDataManager(this);
        edits = new EditSessionManager();
        chatInput = new ChatInputManager(this);
        nativeDisplay = new NativeDisplay(this);
        titleService = new TitleService(this);
        updates = new UpdateChecker(this, net);

        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);

        PluginCommand command = getCommand("honorbadge");
        if (command != null) {
            HonorBadgeCommand executor = new HonorBadgeCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        players.start();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderHook = new PlaceholderHook(this);
            placeholderHook.register();
            getLogger().info(console("console.placeholderapi-hooked", Map.of()));
        } else {
            getLogger().warning(console("console.placeholderapi-missing", Map.of()));
        }

        nativeDisplay.refreshAll();
        getLogger().info(console("console.enabled", Map.of(
                "version", getPluginMeta().getVersion(),
                "count", String.valueOf(titles.size()))));

        if (settings.checkOnStartup()) {
            if (getServer().getPluginManager().isPluginEnabled("HttpHub") && clre20.httpHub.api.HttpHubAPI.isAvailable()) {
            clre20.httpHub.api.HttpHubAPI.registerUpdateCheck(this, "clre20/HonorBadge", getPluginMeta().getVersion(), "honorbadge.version", null);
        } else {
            updates.check().thenAccept(this::logUpdateResult);
        }
        }
    }

    @Override
    public void onDisable() {
        if (edits != null) {
            edits.endAll();
        }
        if (chatInput != null) {
            chatInput.cancelAll();
        }
        closeMenus();
        if (nativeDisplay != null) {
            nativeDisplay.clearAll();
        }
        if (placeholderHook != null) {
            placeholderHook.unregister();
            placeholderHook = null;
        }
        Bukkit.getScheduler().cancelTasks(this);

        if (io != null) {
            io.shutdown();
            try {
                if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                    getLogger().warning("等待檔案寫入逾時");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (players != null) {
            players.shutdown();
        }
        if (net != null) {
            net.shutdownNow();
        }
    }

    /** /hb reload：重新讀取並補齊 config.yml、messages.yml，再重新載入稱號。 */
    public void reload() {
        edits.endAll();
        chatInput.cancelAll();
        closeMenus();
        waitForIo();

        messages.load();
        settings.load();
        titles.load();
        if (placeholderHook != null) {
            placeholderHook.clearCache();
        }
        players.scheduleAutosave();
        nativeDisplay.refreshAll();
    }

    private void waitForIo() {
        try {
            io.submit(() -> { }).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "等待檔案寫入逾時", e);
        }
    }

    private void closeMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof MenuHolder) {
                player.closeInventory();
            }
        }
    }

    private void logUpdateResult(UpdateChecker.Result result) {
        Map<String, String> ph = Map.of(
                "current", result.current(),
                "latest", result.latest() == null ? "" : result.latest(),
                "url", result.url() == null ? UpdateChecker.RELEASES_URL : result.url(),
                "error", result.error() == null ? "" : result.error());
        switch (result.status()) {
            case LATEST -> getLogger().info(console("console.update-latest", ph));
            case OUTDATED -> getLogger().warning(console("console.update-outdated", ph));
            case DEV_NEWER -> getLogger().info(console("console.update-dev-newer", ph));
            case NO_RELEASE -> getLogger().info(console("console.update-no-release", ph));
            case FAILED -> getLogger().warning(console("console.update-failed", ph));
        }
    }

    private static ThreadFactory namedThread(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    // ==================== 共用工具 ====================

    /** 在下一個 tick 於主執行緒執行。 */
    public void sync(Runnable task) {
        if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    /** 主控台訊息（messages 尚未載入時回傳 key，避免 NPE）。 */
    public String console(String key, Map<String, String> placeholders) {
        return messages == null ? key : messages.console(key, placeholders);
    }

    public ExecutorService io() { return io; }
    public Keys keys() { return keys; }
    public MessageManager messages() { return messages; }
    public ConfigManager settings() { return settings; }
    public Sounds sounds() { return sounds; }
    public TitleManager titles() { return titles; }
    public PlayerDataManager players() { return players; }
    public EditSessionManager edits() { return edits; }
    public ChatInputManager chatInput() { return chatInput; }
    public NativeDisplay nativeDisplay() { return nativeDisplay; }
    public TitleService titleService() { return titleService; }
    public UpdateChecker updates() { return updates; }
}
