# HonorBadge

HonorBadge 是一款專為 Paper伺服器設計的稱號系統。管理員透過箱子介面建立、編輯與發放稱號，玩家在介面中點一下即可切換。本插件不直接搶佔 Tab 列表、頭頂名稱或聊天格式，而是提供 PlaceholderAPI 變數交給 TAB、LPC 等插件顯示，避免與既有插件互相覆蓋。


> **版本檢查提示**：若伺服器有安裝 [HttpHub](https://github.com/clre20/HttpHub)，本插件自動由 HttpHub 集中處理版本檢查與管理員登入通知，避免各插件自開連線與背景執行緒。

## 功能特性

* **箱子介面操作**：
  * 玩家介面（`/hb`）只列出自己擁有的稱號，使用中的稱號會發光並標示「使用中」，支援分頁與一鍵卸下。
  * 管理介面可設定顯示名稱、圖示（主手物品）、說明（選填）與排序值，並提供即時預覽與聊天預覽。
  * 所有介面一律左鍵操作；右鍵、Shift、數字鍵、拖曳全部取消，物品無法被拿走。
  * 介面綁定專屬 `InventoryHolder`（`clre20.honorBadge.gui.MenuHolder`），按鈕物品帶有 `honorbadge:gui` 標籤，方便其他插件辨識來源。
* **完整顏色支援**：
  * 同時支援 `&a` 傳統代碼、`&#FF5555` HEX 與 MiniMessage（如 `<gradient:red:gold>戰神</gradient>`）。
  * PAPI 輸出會轉成 `§x§R§R§G§G§B§B` 格式，TAB 與 LPC 都能正確顯示漸層與 HEX。
* **「取代」顯示模式**：
  * `%honorbadge_display%`：玩家有戴稱號時顯示稱號，沒戴時顯示備用文字（預設為 LuckPerms 前綴）。
  * 備用文字可依用途分組，例如 `%honorbadge_display_admin%`，讓管理員沒戴稱號時仍顯示原本的身分。
  * 只影響顯示，不影響 LuckPerms 群組與權限。
* **離線玩家支援**：
  * `give`、`remove`、`playerlist` 可對曾經進過伺服器的離線玩家使用。
  * 刪除稱號時會從所有持有者身上移除，正在使用的玩家自動卸下。
* **效能與資料安全**：
  * 線上玩家資料放在記憶體，變更以髒資料標記，定時批次寫入、離線與關服時存檔。
  * 檔案讀寫集中在單一 I/O 執行緒，更新檢查使用單一網路執行緒，不會無限制增加執行緒。
  * 寫檔採「先寫暫存檔再取代」，避免關服時寫到一半造成損毀。
* **設定檔自動補齊**：
  * 啟動與 `/hb reload` 時，自動比對 `config.yml` 與 `messages.yml`，補上缺少的項目並保留原本的值；版本不同時先備份成 `.bak`。
  * 所有聊天訊息、介面標題、物品名稱與說明都在 `messages.yml`，可自由修改。
* **檢查更新**：
  * 啟動時以非同步方式向 [GitHub Releases](https://github.com/clre20/HonorBadge/releases) 檢查，結果寫在主控台。
  * 有 `honorbadge.version` 權限的管理員登入 3 秒後，若有新版本會收到提醒。
  * 結果快取 10 分鐘、沒有定時輪詢，不會超過 GitHub 每小時 60 次的限制。兩項功能都可在 `config.yml` 的 `update-checker` 關閉。

## 指令與權限

本插件主指令為 `/honorbadge`。

| 指令 | 說明 | 執行端 | 權限節點 | 預設 |
| :--- | :--- | :--- | :--- | :--- |
| `/hb` | 開啟玩家稱號介面 | 僅限玩家 | `honorbadge.use` | 所有人 |
| `/hb help` | 顯示指令說明（只列出有權限的指令） | 遊戲內 / 控制台 | `honorbadge.help` | 所有人 |
| `/hb version` | 查看版本並檢查更新 | 遊戲內 / 控制台 | `honorbadge.version` | OP |
| `/hb list` | 開啟管理用稱號列表（控制台顯示文字列表） | 遊戲內 / 控制台 | `honorbadge.list` | OP |
| `/hb create <ID>` | 建立稱號並開啟設定介面 | 僅限玩家 | `honorbadge.create` | OP |
| `/hb edit <ID>` | 開啟稱號設定介面 | 僅限玩家 | `honorbadge.edit` | OP |
| `/hb del <ID>` | 刪除稱號（玩家開啟確認介面，控制台直接刪除） | 遊戲內 / 控制台 | `honorbadge.delete` | OP |
| `/hb player <ID>` | 查看持有此稱號的玩家 | 遊戲內 / 控制台 | `honorbadge.player` | OP |
| `/hb playerlist <玩家>` | 查看玩家持有的稱號 | 遊戲內 / 控制台 | `honorbadge.playerlist` | OP |
| `/hb give <ID> <玩家>` | 給玩家稱號 | 遊戲內 / 控制台 | `honorbadge.give` | OP |
| `/hb remove <ID> <玩家>` | 移除玩家的稱號 | 遊戲內 / 控制台 | `honorbadge.remove` | OP |
| `/hb reload` | 重新載入設定、訊息與稱號 | 遊戲內 / 控制台 | `honorbadge.reload` | OP |

> 指令別名：`/hb` 等同 `/honorbadge`；`/hb delete` 等同 `/hb del`。
>
> `honorbadge.admin`：包含以上所有權限，預設 OP。
>
> 稱號 ID 只能使用小寫英文、數字與底線（`a-z 0-9 _`），顯示名稱在介面中另外設定。

## PlaceholderAPI 變數

| 變數 | 說明 |
| :--- | :--- |
| `%honorbadge_title%` | 套用 `title-format` 的稱號，例如 `[戰神] `；沒戴時為空 |
| `%honorbadge_title_raw%` | 只有稱號本身（有顏色、無外框）；沒戴時為空 |
| `%honorbadge_id%` | 目前稱號的 ID；沒戴時為空 |
| `%honorbadge_count%` | 擁有的稱號數量 |
| `%honorbadge_display%` | 有稱號顯示稱號，沒有則顯示 `display-fallback.default` |
| `%honorbadge_display_<名稱>%` | 同上，沒有稱號時改用 `display-fallback.<名稱>` |

## 使用範例

### 1. 建立並發放稱號
1. 管理員在遊戲內執行：
   ```bash
   /hb create warlord
   ```
2. 在介面中點「設定顯示名稱」，於聊天欄輸入 `<gradient:red:gold>戰神</gradient>`；需要的話再設定圖示、說明與排序，最後點「確認儲存」。
3. 於控制台或遊戲內發給玩家（離線玩家也可以）：
   ```bash
   hb give warlord Steve
   ```
4. 玩家輸入 `/hb`，點一下稱號即可使用。

### 2. 搭配 TAB 顯示在 Tab 列表與頭頂
`plugins/TAB/groups.yml`：
```yaml
_DEFAULT_:
  tabprefix: "%honorbadge_display%"
  tagprefix: "%honorbadge_display%"
```
`plugins/TAB/config.yml` 的 `placeholder-refresh-intervals` 加上：
```yaml
  '%honorbadge_display%': 500
```

### 3. 搭配 LPC 顯示在聊天前面
`plugins/LPC/config.yml`：
```yaml
chat-format: "&7%honorbadge_display%&f{name} &8» &f{message}"
group-formats:
  admin: "%honorbadge_display_admin%&f{name} &8» &f{message}"
  player: "%honorbadge_display_player%&f{name} &8» &f{message}"
```
沒戴稱號的管理員會顯示 `display-fallback.admin` 的內容，戴上後換成稱號。

### 4. 沒有 TAB／聊天插件的伺服器
將 `config.yml` 的 `native-display` 中 `chat`、`tab`、`nametag` 改為 `true`，由 HonorBadge 自己顯示稱號。已安裝 TAB 時請保持關閉，否則會被 TAB 的 anti-override 擋下。

## 檔案位置

| 路徑 | 說明 |
| :--- | :--- |
| `plugins/HonorBadge/config.yml` | 設定檔 |
| `plugins/HonorBadge/messages.yml` | 所有顯示文字 |
| `plugins/HonorBadge/data/<ID>.yml` | 稱號資料 |
| `plugins/HonorBadge/playerdata/<UUID>.yml` | 玩家擁有與使用中的稱號 |

## 相容性

* 伺服器：Paper 1.21.x ～ 26.x 與其分支（如 Purpur）；不支援 Spigot、Folia。
* Java：21 以上（26.x 伺服器需要 Java 25）。
* 選用依賴：PlaceholderAPI。
* 基岩版（Geyser）玩家可正常操作介面；HEX 與漸層會被轉成最接近的基本色。


# HonorBadge


> **Update Checker**: When [HttpHub](https://github.com/clre20/HttpHub) is installed, update checks and operator login alerts are handled centrally by HttpHub via virtual threads, preventing redundant background connections.

HonorBadge is a title (badge) system designed for Paper servers. Administrators create, edit, and grant titles through chest GUIs, and players switch titles with a single click. Instead of taking over the tab list, name tags, or chat format, the plugin exposes PlaceholderAPI placeholders so that plugins such as TAB and LPC can display titles without overriding each other.

## Features

* **Chest GUI Workflow**:
  * The player GUI (`/hb`) lists only owned titles; the equipped title glows and is marked "in use", with pagination and a one-click unequip button.
  * The editor GUI sets the display name, icon (main-hand item), optional description, and sort value, with a live item preview and a chat preview.
  * Every GUI is left-click only; right-click, Shift, number keys, and dragging are cancelled, so items can never be taken.
  * GUIs are bound to a dedicated `InventoryHolder` (`clre20.honorBadge.gui.MenuHolder`) and button items carry an `honorbadge:gui` tag so other plugins can identify them.
* **Full Color Support**:
  * Legacy `&a` codes, `&#FF5555` hex, and MiniMessage (e.g. `<gradient:red:gold>Warlord</gradient>`) all work together.
  * Placeholder output uses the `§x§R§R§G§G§B§B` format, so TAB and LPC render gradients and hex colors correctly.
* **Replace Display Mode**:
  * `%honorbadge_display%` shows the title when one is equipped, otherwise a fallback text (LuckPerms prefix by default).
  * Fallbacks can be grouped, e.g. `%honorbadge_display_admin%`, so staff keep their rank label when no title is equipped.
  * Display only — LuckPerms groups and permissions are never touched.
* **Offline Player Support**:
  * `give`, `remove`, and `playerlist` work for any player who has joined before.
  * Deleting a title removes it from every holder and unequips it automatically.
* **Performance & Data Safety**:
  * Online player data lives in memory; changes are flagged dirty and written in batches on a timer, on quit, and on shutdown.
  * All file I/O runs on a single I/O thread and update checks on a single network thread — no thread explosion.
  * Files are written to a temp file and then swapped in, so a crash mid-write cannot corrupt data.
* **Automatic Config Completion**:
  * On startup and `/hb reload`, missing keys in `config.yml` and `messages.yml` are added while keeping your values; a `.bak` backup is made when the version changes.
  * All chat messages, GUI titles, item names, and lore live in `messages.yml`.
* **Update Checker**:
  * Checks [GitHub Releases](https://github.com/clre20/HonorBadge/releases) asynchronously on startup and logs the result.
  * Admins with `honorbadge.version` get a reminder 3 seconds after joining when a newer version exists.
  * Results are cached for 10 minutes with no polling, staying well below GitHub's 60 requests/hour limit. Both features can be disabled under `update-checker` in `config.yml`.

## Commands & Permissions

The primary command for this plugin is `/honorbadge`.

| Command | Description | Executor | Permission Node | Default |
| :--- | :--- | :--- | :--- | :--- |
| `/hb` | Open the player title GUI | Player only | `honorbadge.use` | Everyone |
| `/hb help` | Show commands you have permission for | In-game / Console | `honorbadge.help` | Everyone |
| `/hb version` | Show version and check for updates | In-game / Console | `honorbadge.version` | OP |
| `/hb list` | Open the admin title list (text list on console) | In-game / Console | `honorbadge.list` | OP |
| `/hb create <ID>` | Create a title and open the editor | Player only | `honorbadge.create` | OP |
| `/hb edit <ID>` | Open the title editor | Player only | `honorbadge.edit` | OP |
| `/hb del <ID>` | Delete a title (confirmation GUI for players, immediate on console) | In-game / Console | `honorbadge.delete` | OP |
| `/hb player <ID>` | List players who own a title | In-game / Console | `honorbadge.player` | OP |
| `/hb playerlist <player>` | List a player's titles | In-game / Console | `honorbadge.playerlist` | OP |
| `/hb give <ID> <player>` | Grant a title | In-game / Console | `honorbadge.give` | OP |
| `/hb remove <ID> <player>` | Remove a title | In-game / Console | `honorbadge.remove` | OP |
| `/hb reload` | Reload config, messages, and titles | In-game / Console | `honorbadge.reload` | OP |

> Command Alias: `/hb` is an alias for `/honorbadge`; `/hb delete` equals `/hb del`.
>
> `honorbadge.admin` grants every permission above (default: OP).
>
> Title IDs may only contain lowercase letters, digits, and underscores (`a-z 0-9 _`); the display name is set in the GUI.

## PlaceholderAPI Placeholders

| Placeholder | Description |
| :--- | :--- |
| `%honorbadge_title%` | Title with `title-format` applied, e.g. `[Warlord] `; empty when none |
| `%honorbadge_title_raw%` | Title only (colored, no frame); empty when none |
| `%honorbadge_id%` | Equipped title ID; empty when none |
| `%honorbadge_count%` | Number of owned titles |
| `%honorbadge_display%` | Title when equipped, otherwise `display-fallback.default` |
| `%honorbadge_display_<name>%` | Same, but falls back to `display-fallback.<name>` |

## Compatibility

* Server: Paper 1.21.x – 26.x and forks (e.g. Purpur). Spigot and Folia are not supported.
* Java: 21+ (26.x servers require Java 25).
* Optional dependency: PlaceholderAPI.
* Bedrock (Geyser) players can use the GUIs; hex colors and gradients are shown as the nearest basic color.