package clre20.honorBadge.update;

import clre20.honorBadge.HonorBadge;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 向 GitHub Releases 檢查更新。
 * <ul>
 *   <li>全部非同步，使用插件共用的單一網路執行緒，不另開執行緒。</li>
 *   <li>結果快取 10 分鐘（失敗只快取 1 分鐘），同時多個請求會共用同一次查詢。</li>
 *   <li>沒有定時輪詢，只在啟動、/hb version、管理員登入時查詢。</li>
 * </ul>
 */
public final class UpdateChecker {

    public static final String REPO = "clre20/HonorBadge";
    public static final String RELEASES_URL = "https://github.com/" + REPO + "/releases";
    /** 可用 JVM 參數 -Dhonorbadge.update-api=<網址> 改成測試用的伺服器，平常不需要設定。 */
    private static final String API_URL = System.getProperty("honorbadge.update-api",
            "https://api.github.com/repos/" + REPO + "/releases/latest");
    private static final long CACHE_MILLIS = 10 * 60 * 1000L;
    private static final long FAIL_CACHE_MILLIS = 60 * 1000L;

    public enum Status { LATEST, OUTDATED, DEV_NEWER, NO_RELEASE, FAILED }

    public record Result(Status status, String current, String latest, String url, String error) {
    }

    private final HonorBadge plugin;
    private final HttpClient client;
    private final ExecutorService netExecutor;
    private final String current;

    private Result cached;
    private long cachedAt;
    private CompletableFuture<Result> inFlight;

    public UpdateChecker(HonorBadge plugin, ExecutorService netExecutor) {
        this.plugin = plugin;
        this.current = plugin.getPluginMeta().getVersion();
        this.netExecutor = netExecutor;
        this.client = HttpClient.newBuilder()
                .executor(netExecutor)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String currentVersion() {
        return current;
    }

    public synchronized CompletableFuture<Result> check() {
        long now = System.currentTimeMillis();
        if (cached != null) {
            long ttl = cached.status() == Status.FAILED ? FAIL_CACHE_MILLIS : CACHE_MILLIS;
            if (now - cachedAt < ttl) {
                return CompletableFuture.completedFuture(cached);
            }
        }
        if (inFlight != null) {
            return inFlight;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "HonorBadge/" + current)
                .GET()
                .build();
        CompletableFuture<Result> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(this::parse, netExecutor)
                .exceptionally(e -> new Result(Status.FAILED, current, null, RELEASES_URL,
                        e.getCause() != null ? e.getCause().toString() : e.toString()));
        inFlight = future;
        future.whenComplete((result, error) -> {
            synchronized (UpdateChecker.this) {
                cached = result;
                cachedAt = System.currentTimeMillis();
                inFlight = null;
            }
        });
        return future;
    }

    private Result parse(HttpResponse<String> response) {
        if (response.statusCode() == 404) {
            return new Result(Status.NO_RELEASE, current, null, RELEASES_URL, null);
        }
        if (response.statusCode() != 200) {
            return new Result(Status.FAILED, current, null, RELEASES_URL, "HTTP " + response.statusCode());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : null;
        String url = json.has("html_url") ? json.get("html_url").getAsString() : RELEASES_URL;
        if (tag == null || tag.isBlank()) {
            return new Result(Status.NO_RELEASE, current, null, RELEASES_URL, null);
        }
        String latest = stripV(tag);
        int cmp = compare(stripV(current), latest);
        Status status = cmp < 0 ? Status.OUTDATED : cmp > 0 ? Status.DEV_NEWER : Status.LATEST;
        return new Result(status, current, latest, url, null);
    }

    static String stripV(String version) {
        String v = version.trim();
        return v.startsWith("v") || v.startsWith("V") ? v.substring(1) : v;
    }

    /** 依數字逐段比較，例如 1.10.0 > 1.9.3；非數字部分（如 -SNAPSHOT）忽略。 */
    static int compare(String a, String b) {
        String[] pa = a.split("[.\\-+]");
        String[] pb = b.split("[.\\-+]");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int x = i < pa.length ? number(pa[i]) : 0;
            int y = i < pb.length ? number(pb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int number(String part) {
        StringBuilder digits = new StringBuilder();
        for (char c : part.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
