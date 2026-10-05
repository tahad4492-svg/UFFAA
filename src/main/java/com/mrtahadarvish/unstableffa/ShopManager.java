package com.mrtahadarvish.unstableffa;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The /shop: sections (titles) that each hold items with a coin price.
 * Stored in shop.yml, managed in-game by admins with /shop ... commands.
 */
public final class ShopManager {

    public static final int MAX_SECTIONS = 27;   // main menu is barrel sized
    public static final int MAX_ITEMS = 18;      // top two rows of a section menu
    public static final int MAX_TITLES = 18;     // chat titles menu has the same layout

    public static final class Entry {
        public final ItemStack item;
        public long price;

        Entry(ItemStack item, long price) {
            this.item = item;
            this.price = price;
        }
    }

    public static final class Section {
        public final String id;
        public String title;
        public ItemStack icon;
        public final List<Entry> items = new ArrayList<>();

        Section(String id, String title, ItemStack icon) {
            this.id = id;
            this.title = title;
            this.icon = icon;
        }

        public String display() {
            return title.replace('_', ' ');
        }
    }

    /** A chat title with a two-colour gradient, bought with coins. */
    public static final class Title {
        public final String id;
        public final String text;     // as typed, _ shows as a space
        public final String start;    // hex colour, e.g. #ff0000
        public final String end;
        public long price;

        Title(String id, String text, String start, String end, long price) {
            this.id = id;
            this.text = text;
            this.start = start;
            this.end = end;
            this.price = price;
        }

        public String display() {
            return text.replace('_', ' ');
        }

        /** MiniMessage for the coloured title text. */
        public String mini() {
            return "<gradient:" + start + ":" + end + ">" + UnstableFFA.esc(display()) + "</gradient>";
        }
    }

    private static final Map<String, String> COLOR_ALIASES = Map.ofEntries(
            Map.entry("pink", "#ff69b4"), Map.entry("hotpink", "#ff1493"), Map.entry("orange", "#ffa500"),
            Map.entry("lime", "#32cd32"), Map.entry("cyan", "#00ffff"), Map.entry("magenta", "#ff00ff"),
            Map.entry("purple", "#8a2be2"), Map.entry("violet", "#9400d3"), Map.entry("brown", "#8b4513"),
            Map.entry("silver", "#c0c0c0"), Map.entry("crimson", "#dc143c"), Map.entry("navy", "#000080"),
            Map.entry("teal", "#008080"), Map.entry("lightgray", "#d3d3d3"), Map.entry("lightgrey", "#d3d3d3"),
            Map.entry("grey", "#808080"), Map.entry("darkgray", "#404040"), Map.entry("darkgrey", "#404040"),
            Map.entry("skyblue", "#87ceeb"), Map.entry("lightblue", "#add8e6"), Map.entry("yellow", "#ffff55"));

    /** Turns "red", "pink", "#ff0000" or "ff0000" into "#rrggbb". Returns null if it is not a colour. */
    public static String parseColor(String input) {
        String s = input.toLowerCase(Locale.ROOT);
        if (s.matches("#[0-9a-f]{6}")) {
            return s;
        }
        if (s.matches("[0-9a-f]{6}")) {
            return "#" + s;
        }
        String alias = COLOR_ALIASES.get(s);
        if (alias != null) {
            return alias;
        }
        NamedTextColor named = NamedTextColor.NAMES.value(s);
        return named == null ? null : String.format(Locale.ROOT, "#%06x", named.value());
    }

    private final UnstableFFA plugin;
    private final File file;
    private final Map<String, Section> sections = new LinkedHashMap<>();
    private final Map<String, Title> titles = new LinkedHashMap<>();

    public ShopManager(UnstableFFA plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop.yml");
        load();
    }

    // ---- lookup --------------------------------------------------------------

    public Section get(String id) {
        return id == null ? null : sections.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Section> all() {
        return new ArrayList<>(sections.values());
    }

    public List<String> ids() {
        return new ArrayList<>(sections.keySet());
    }

    // ---- chat titles ---------------------------------------------------------

    public List<Title> titles() {
        return new ArrayList<>(titles.values());
    }

    public Title getTitle(String id) {
        return id == null ? null : titles.get(id.toLowerCase(Locale.ROOT));
    }

    /** @return the new title, or null if the id exists or the list is full */
    public Title addTitle(String rawId, String text, String startHex, String endHex, long price) {
        String id = rawId.toLowerCase(Locale.ROOT);
        if (titles.containsKey(id) || titles.size() >= MAX_TITLES) {
            return null;
        }
        Title title = new Title(id, text, startHex, endHex, Math.max(0L, price));
        titles.put(id, title);
        save();
        return title;
    }

    public boolean removeTitle(String id) {
        boolean removed = titles.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public boolean setTitlePrice(String id, long price) {
        Title title = getTitle(id);
        if (title == null) {
            return false;
        }
        title.price = Math.max(0L, price);
        save();
        return true;
    }

    // ---- editing -------------------------------------------------------------

    /** @return the new section, or null if the id exists or the shop is full */
    public Section createSection(String rawName, ItemStack icon) {
        String id = rawName.toLowerCase(Locale.ROOT);
        if (sections.containsKey(id) || sections.size() >= MAX_SECTIONS) {
            return null;
        }
        ItemStack shown = (icon == null || icon.getType().isAir()) ? new ItemStack(Material.CHEST) : icon.clone();
        shown.setAmount(1);
        Section section = new Section(id, rawName, shown);
        sections.put(id, section);
        save();
        return section;
    }

    public boolean deleteSection(String id) {
        boolean removed = sections.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public void setTitle(Section section, String title) {
        section.title = title;
        save();
    }

    public void setIcon(Section section, ItemStack icon) {
        ItemStack shown = icon.clone();
        shown.setAmount(1);
        section.icon = shown;
        save();
    }

    /** @return false if the section is full */
    public boolean addItem(Section section, ItemStack item, long price) {
        if (section.items.size() >= MAX_ITEMS) {
            return false;
        }
        section.items.add(new Entry(item.clone(), Math.max(0L, price)));
        save();
        return true;
    }

    /** @param number 1-based position shown in /shop list */
    public boolean removeItem(Section section, int number) {
        if (number < 1 || number > section.items.size()) {
            return false;
        }
        section.items.remove(number - 1);
        save();
        return true;
    }

    public boolean setPrice(Section section, int number, long price) {
        if (number < 1 || number > section.items.size()) {
            return false;
        }
        section.items.get(number - 1).price = Math.max(0L, price);
        save();
        return true;
    }

    // ---- io ------------------------------------------------------------------

    public void load() {
        sections.clear();
        titles.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection titleRoot = yaml.getConfigurationSection("titles");
        if (titleRoot != null) {
            for (String id : titleRoot.getKeys(false)) {
                ConfigurationSection s = titleRoot.getConfigurationSection(id);
                if (s != null && s.getString("text") != null && s.getString("start") != null && s.getString("end") != null) {
                    titles.put(id, new Title(id, s.getString("text"), s.getString("start"), s.getString("end"), s.getLong("price")));
                }
            }
        }
        ConfigurationSection root = yaml.getConfigurationSection("sections");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            ItemStack icon = s.getItemStack("icon");
            Section section = new Section(id, s.getString("title", id), icon == null ? new ItemStack(Material.CHEST) : icon);
            ConfigurationSection items = s.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ItemStack item = items.getItemStack(key + ".item");
                    if (item != null && section.items.size() < MAX_ITEMS) {
                        section.items.add(new Entry(item, items.getLong(key + ".price")));
                    }
                }
            }
            sections.put(id, section);
        }
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Title title : titles.values()) {
            String base = "titles." + title.id;
            out.set(base + ".text", title.text);
            out.set(base + ".start", title.start);
            out.set(base + ".end", title.end);
            out.set(base + ".price", title.price);
        }
        for (Section section : sections.values()) {
            String base = "sections." + section.id;
            out.set(base + ".title", section.title);
            out.set(base + ".icon", section.icon);
            for (int i = 0; i < section.items.size(); i++) {
                out.set(base + ".items." + i + ".item", section.items.get(i).item);
                out.set(base + ".items." + i + ".price", section.items.get(i).price);
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save shop.yml: " + ex.getMessage());
        }
    }
}
