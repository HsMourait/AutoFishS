package com.autofishs.client;

import com.autofishs.AutoFishSConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Grid of fish item slots with a search box. Each slot shows a fish; clicking toggles it
 * in the fishing blacklist; blacklisted slots get a red background. Hovering shows the
 * item tooltip. Scrolls when there are more fish than fit on screen.
 */
public class BlacklistScreen extends Screen
{
    private static final int SLOT = 18;
    private static final int STEP = 20;
    private static final int TOP = 55;
    private static final int MARGIN = 15;
    private static final int BOTTOM_RESERVED = 40;

    private static final int BG_NORMAL = 0x40222222;
    private static final int BG_BLACKLISTED = 0xA0E04040;
    private static final int BG_HOVER = 0x60FFFFFF;

    private final Screen parent;
    private final List<Item> allFishItems = new ArrayList<>();
    private final List<Item> filteredItems = new ArrayList<>();
    private EditBox searchBox;
    private int columns = 9;
    private int scrollRows = 0;

    public BlacklistScreen(Screen parent)
    {
        super(Component.translatable("autofishs.blacklist.title"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        allFishItems.clear();
        for (Item item : BuiltInRegistries.ITEM)
        {
            if (item.builtInRegistryHolder().is(ItemTags.FISHES)) allFishItems.add(item);
        }
        allFishItems.sort(Comparator.comparing(i -> i.getDescription().getString()));

        columns = Mth.clamp((this.width - MARGIN * 2) / STEP, 7, 18);
        scrollRows = 0;

        searchBox = new EditBox(this.font, this.width / 2 - 100, 26, 200, 18,
                Component.translatable("autofishs.blacklist.search"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("autofishs.blacklist.search"));
        searchBox.setResponder(text -> {
            scrollRows = 0;
            applyFilter();
        });
        addRenderableWidget(searchBox);

        applyFilter();

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private void applyFilter()
    {
        filteredItems.clear();
        String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty())
        {
            filteredItems.addAll(allFishItems);
            return;
        }
        for (Item item : allFishItems)
        {
            if (item.getDescription().getString().toLowerCase(Locale.ROOT).contains(query)
                    || itemId(item).toLowerCase(Locale.ROOT).contains(query))
            {
                filteredItems.add(item);
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks)
    {
        renderBackground(guiGraphics);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        ItemStack hovered = null;
        int visibleRows = Math.max(1, (this.height - TOP - BOTTOM_RESERVED) / STEP);
        for (int row = 0; row < visibleRows; row++)
        {
            for (int col = 0; col < columns; col++)
            {
                int index = (scrollRows + row) * columns + col;
                if (index >= filteredItems.size()) break;

                Item item = filteredItems.get(index);
                int x = MARGIN + col * STEP;
                int y = TOP + row * STEP;
                boolean blacklisted = isBlacklisted(item);

                guiGraphics.fill(x, y, x + SLOT, y + SLOT, blacklisted ? BG_BLACKLISTED : BG_NORMAL);
                boolean hoveredSlot = mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT;
                if (hoveredSlot)
                {
                    guiGraphics.fill(x, y, x + SLOT, y + SLOT, BG_HOVER);
                    hovered = item.getDefaultInstance();
                }
                guiGraphics.renderItem(item.getDefaultInstance(), x, y);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        // Show the item tooltip on hover (like inventory/JEI), but slots cannot be taken.
        if (hovered != null)
        {
            guiGraphics.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        int col = (int) ((mouseX - MARGIN) / STEP);
        int row = (int) ((mouseY - TOP) / STEP);
        if (col >= 0 && col < columns && row >= 0)
        {
            int index = (scrollRows + row) * columns + col;
            if (index >= 0 && index < filteredItems.size())
            {
                AutoFishSConfig.toggleBlacklist(itemId(filteredItems.get(index)));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        int totalRows = (filteredItems.size() + columns - 1) / columns;
        int visibleRows = Math.max(1, (this.height - TOP - BOTTOM_RESERVED) / STEP);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        scrollRows = Mth.clamp(scrollRows - (int) Math.round(delta), 0, maxScroll);
        return true;
    }

    private static boolean isBlacklisted(Item item)
    {
        return AutoFishSConfig.FISHING_BLACKLIST.get().contains(itemId(item));
    }

    private static String itemId(Item item)
    {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        return rl.toString();
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
