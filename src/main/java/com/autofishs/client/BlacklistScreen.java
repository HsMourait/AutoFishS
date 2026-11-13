package com.autofishs.client;

import com.autofishs.AutoFishSConfig;
import com.wdiscute.starcatcher.Starcatcher;
import com.wdiscute.starcatcher.registry.FishProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Grid of possible catches with a search box. Clicking a slot toggles it in the fishing
 * blacklist; blacklisted slots get a red background. Hovering shows the item tooltip.
 * <p>
 * The catch list comes from Starcatcher's own fish registry ({@code starcatcher:fish}) rather
 * than the {@code #minecraft:fishes} item tag, because plenty of catches are registered as
 * plain items and never receive that tag — e.g. {@code starcatcher:geode_eel} lives in
 * {@code SCItems.ITEMS}, not in the bucketable-fish registry the tag is derived from.
 * {@code #minecraft:fishes} is still merged in so vanilla/modded fish remain selectable.
 * <p>
 * Note on {@code BuiltInRegistries.ITEM}: Forge marks it {@code @Deprecated} in favour of
 * {@code ForgeRegistries.ITEMS}, so javac emits a deprecation note for every use. We keep the
 * vanilla registry on purpose — it is the same API on 1.21.1 NeoForge (where it is no longer
 * deprecated and {@code ForgeRegistries} no longer exists), so this file stays portable across
 * the 1.20.1/1.21.1 split instead of being rewritten twice. The note is cosmetic.
 */
public class BlacklistScreen extends Screen
{
    private static final int SLOT = 18;
    private static final int STEP = 20;
    private static final int MARGIN = 12;
    private static final int GRID_TOP = 58;
    private static final int BOTTOM_RESERVED = 44;
    private static final int MAX_COLUMNS = 24;

    private static final int BG_NORMAL = 0x40222222;
    private static final int BG_BLACKLISTED = 0xA0E04040;
    private static final int BG_HOVER = 0x60FFFFFF;

    private static final int COLOR_TITLE = 0xFFFFFF;
    private static final int COLOR_SUBTITLE = 0xFF9AA0A6;

    /** Registry-derived catches, cached so the screen still works when opened without a level. */
    private static final List<Item> CACHED_REGISTRY_ITEMS = new ArrayList<>();

    private final Screen parent;
    private final List<Item> allItems = new ArrayList<>();
    private final List<Item> filteredItems = new ArrayList<>();
    private final Set<Item> blacklisted = new HashSet<>();

    private EditBox searchBox;

    private int columns = 1;
    private int visibleRows = 1;
    private int gridLeft = 0;
    private int gridTop = GRID_TOP;
    private int scrollRows = 0;
    private int maxScrollRows = 0;

    public BlacklistScreen(Screen parent)
    {
        super(Component.translatable("autofishs.blacklist.title"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        allItems.clear();
        allItems.addAll(collectCatchItems());
        allItems.sort(Comparator.comparing(i -> i.getDescription().getString(), String.CASE_INSENSITIVE_ORDER));

        int boxWidth = Math.min(240, this.width - 2 * MARGIN);
        searchBox = new EditBox(this.font, (this.width - boxWidth) / 2, 26, boxWidth, 18,
                Component.translatable("autofishs.blacklist.search"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("autofishs.blacklist.search"));
        searchBox.setResponder(text -> {
            scrollRows = 0;
            applyFilter();
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());

        scrollRows = 0;
        applyFilter();
    }

    // ------------------------------------------------------------------ data

    /**
     * Every item that can actually appear as {@code FishingMinigameScreen.itemBeingFished}:
     * the fish item of every registered {@link FishProperties}, plus its minigame override item
     * (which is what the minigame displays when set).
     */
    private static List<Item> collectCatchItems()
    {
        Set<Item> items = new LinkedHashSet<>();

        // 1. Authoritative source: Starcatcher's fish registry.
        Level level = Minecraft.getInstance().level;
        if (level != null)
        {
            Registry<FishProperties> registry =
                    level.registryAccess().registryOrThrow(Starcatcher.FISH_REGISTRY_KEY);
            for (FishProperties fp : registry)
            {
                // Trophies are not fishing catches — they are awarded for progress, so they can
                // never show up as a minigame target and have no business being blacklistable.
                if (fp.catchInfo().fishEntryType() == FishProperties.CatchInfo.FishEntryType.TROPHY)
                    continue;

                addIfReal(items, fp.catchInfo().fish());
                addIfReal(items, fp.catchInfo().overrideMinigameWith());
            }
            CACHED_REGISTRY_ITEMS.clear();
            CACHED_REGISTRY_ITEMS.addAll(items);
        }
        else if (!CACHED_REGISTRY_ITEMS.isEmpty())
        {
            items.addAll(CACHED_REGISTRY_ITEMS);
        }

        // 2. Vanilla and other mods' fish. (Item#builtInRegistryHolder is deprecated in 1.20.1;
        //    wrapAsHolder is the current way to test a tag on an Item.)
        for (Item item : BuiltInRegistries.ITEM)
        {
            if (BuiltInRegistries.ITEM.wrapAsHolder(item).is(ItemTags.FISHES))
                items.add(item);
        }

        // 3. Anything already blacklisted, so entries set elsewhere stay visible and removable.
        for (String id : AutoFishSConfig.FISHING_BLACKLIST.get())
        {
            Item item = itemOrNull(id);
            if (item != null)
                items.add(item);
        }

        return new ArrayList<>(items);
    }

    private static void addIfReal(Set<Item> out, Holder<Item> holder)
    {
        if (holder == null)
            return;

        Item item = holder.value();
        if (item == null || item == Items.AIR)
            return;

        // "missingno" is Starcatcher's placeholder for "this fish has no minigame override item".
        // Compare by registry id rather than touching SCItems.MISSINGNO: that field's type
        // (neobackports DeferredItem) is not on our compile classpath.
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null && id.equals(Starcatcher.rl("missingno")))
            return;

        out.add(item);
    }

    private static Item itemOrNull(String id)
    {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null)
            return null;

        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? null : item;
    }

    private void applyFilter()
    {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);

        filteredItems.clear();
        for (Item item : allItems)
        {
            if (query.isEmpty()
                    || item.getDescription().getString().toLowerCase(Locale.ROOT).contains(query)
                    || itemId(item).toLowerCase(Locale.ROOT).contains(query))
            {
                filteredItems.add(item);
            }
        }

        refreshBlacklistSnapshot();
        layout();
    }

    /** Rebuilds the {@link #blacklisted} lookup set from config; called on open and after a toggle. */
    private void refreshBlacklistSnapshot()
    {
        blacklisted.clear();
        for (String id : AutoFishSConfig.FISHING_BLACKLIST.get())
        {
            Item item = itemOrNull(id);
            if (item != null)
                blacklisted.add(item);
        }
    }

    // ------------------------------------------------------------------ layout

    /** Recomputes the grid geometry so it stays centred and adapts to the window size. */
    private void layout()
    {
        int availableWidth = Math.max(STEP, this.width - 2 * MARGIN);
        columns = Mth.clamp(availableWidth / STEP, 1, MAX_COLUMNS);
        gridLeft = (this.width - columns * STEP) / 2;

        int availableHeight = Math.max(STEP, this.height - GRID_TOP - BOTTOM_RESERVED);
        visibleRows = Math.max(1, availableHeight / STEP);
        // centre the used rows inside the available band
        gridTop = GRID_TOP + Math.max(0, (availableHeight - visibleRows * STEP) / 2);

        int totalRows = (filteredItems.size() + columns - 1) / columns;
        maxScrollRows = Math.max(0, totalRows - visibleRows);
        scrollRows = Mth.clamp(scrollRows, 0, maxScrollRows);
    }

    /**
     * Maps a cursor position to an index into {@link #filteredItems}, or -1 when the cursor is
     * not over a slot that is actually drawn. This is the single source of truth for both
     * rendering and clicking, which is what keeps the clickable area exactly on the slots
     * (the previous version accepted clicks in the 2px gaps, below the last visible row, and
     * on any mouse button).
     */
    private int slotIndexAt(double mouseX, double mouseY)
    {
        if (mouseX < gridLeft || mouseY < gridTop)
            return -1;

        int col = (int) ((mouseX - gridLeft) / STEP);
        int row = (int) ((mouseY - gridTop) / STEP);
        if (col < 0 || col >= columns || row < 0 || row >= visibleRows)
            return -1;

        // reject the 2px gutter between slots
        if (mouseX - (gridLeft + col * STEP) >= SLOT || mouseY - (gridTop + row * STEP) >= SLOT)
            return -1;

        int index = (scrollRows + row) * columns + col;
        return index < filteredItems.size() ? index : -1;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks)
    {
        renderBackground(guiGraphics);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, COLOR_TITLE);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("autofishs.blacklist.count", blacklisted.size(), allItems.size()),
                this.width / 2, 46, COLOR_SUBTITLE);

        int gridBottom = gridTop + visibleRows * STEP;
        guiGraphics.enableScissor(0, gridTop - 2, this.width, gridBottom + 2);

        Item hovered = null;
        for (int row = 0; row < visibleRows; row++)
        {
            for (int col = 0; col < columns; col++)
            {
                int index = (scrollRows + row) * columns + col;
                if (index >= filteredItems.size())
                    break;

                Item item = filteredItems.get(index);
                int x = gridLeft + col * STEP;
                int y = gridTop + row * STEP;

                boolean isBlack = blacklisted.contains(item);
                guiGraphics.fill(x, y, x + SLOT, y + SLOT, isBlack ? BG_BLACKLISTED : BG_NORMAL);

                if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT)
                {
                    guiGraphics.fill(x, y, x + SLOT, y + SLOT, BG_HOVER);
                    hovered = item;
                }

                guiGraphics.renderItem(item.getDefaultInstance(), x + 1, y + 1);
            }
        }

        guiGraphics.disableScissor();

        // widget layer (search box, back button) on top of the grid
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        if (hovered != null)
            guiGraphics.renderTooltip(this.font, hovered.getDefaultInstance(), mouseX, mouseY);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0)
        {
            int index = slotIndexAt(mouseX, mouseY);
            if (index >= 0)
            {
                AutoFishSConfig.toggleBlacklist(itemId(filteredItems.get(index)));
                refreshBlacklistSnapshot();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (maxScrollRows <= 0)
            return false;

        int before = scrollRows;
        scrollRows = Mth.clamp(scrollRows - (int) Math.round(delta), 0, maxScrollRows);
        return scrollRows != before;
    }

    // ------------------------------------------------------------------ misc

    private static String itemId(Item item)
    {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    @Override
    public void onClose()
    {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen()
    {
        return true;
    }
}
