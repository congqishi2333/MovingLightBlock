package com.congqishi.movinglightblock.menu;

import com.congqishi.movinglightblock.ModMenus;
import com.congqishi.movinglightblock.MovingLightBlockMod;
import com.congqishi.movinglightblock.entity.RollingLightEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.extensions.IItemStackExtension;
import net.neoforged.neoforge.network.IContainerFactory;

/**
 * Workbench-like menu attached to a rolling light.
 *
 * <p>The single left-hand slot accepts a crafting table, furnace or chest item. That choice
 * enables the matching panel on the right: 3x3 crafting, smelting, or 27-slot storage.
 * Player inventory is always available at the bottom.</p>
 */
public class RollingLightMenu extends AbstractContainerMenu {
    public static final int MODE_NONE = 0;
    public static final int MODE_CRAFTING = 1;
    public static final int MODE_SMELTING = 2;
    public static final int MODE_STORAGE = 3;

    private static final int MODULE_SLOT = 0;
    private static final int CRAFT_START = 1;
    private static final int CRAFT_RESULT = 10;
    private static final int SMELT_INPUT = 11;
    private static final int SMELT_FUEL = 12;
    private static final int SMELT_RESULT = 13;
    private static final int STORAGE_START = 14;
    private static final int STORAGE_END = STORAGE_START + 27;
    private static final int INV_START = STORAGE_END;
    private static final int INV_END = INV_START + 36;

    private static final int COOK_TIME = 200;

    private final RollingLightEntity entity;
    private final int entityId;
    private final Player player;
    private final Level level;

    private final SimpleContainer module = new SimpleContainer(1);
    private final TransientCraftingContainer crafting;
    private final ResultContainer craftResult = new ResultContainer();
    private final SimpleContainer smeltInput = new SimpleContainer(1);
    private final SimpleContainer smeltFuel = new SimpleContainer(1);
    private final SimpleContainer smeltOutput = new SimpleContainer(1);
    private final SimpleContainer storage = new SimpleContainer(27);

    private final DataSlot modeData;
    private final DataSlot progressData;
    private final DataSlot burnData;

    private int burnTime;
    private int burnDuration;
    private int smeltProgress;

    public static RollingLightMenu fromNetwork(int windowId, Inventory inventory, RegistryFriendlyByteBuf data) {
        int id = data == null ? -1 : data.readVarInt();
        Entity entity = id >= 0 ? inventory.player.level().getEntity(id) : null;
        return new RollingLightMenu(windowId, inventory,
                entity instanceof RollingLightEntity light ? light : null);
    }

    public RollingLightMenu(int windowId, Inventory inventory, RollingLightEntity entity) {
        super(ModMenus.ROLLING_LIGHT.get(), windowId);
        this.entity = entity;
        this.entityId = entity == null ? -1 : entity.getId();
        this.player = inventory.player;
        this.level = inventory.player.level();

        this.modeData = DataSlot.standalone();
        this.progressData = DataSlot.standalone();
        this.burnData = DataSlot.standalone();
        addDataSlot(modeData);
        addDataSlot(progressData);
        addDataSlot(burnData);

        addSlot(new ModuleSlot(module, 0, 156, 40));

        this.crafting = new TransientCraftingContainer(this, 3, 3);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new ModeSlot(crafting, col + row * 3, 196 + col * 20, 30 + row * 20,
                        MODE_CRAFTING, false));
            }
        }
        addSlot(new ModeResultSlot(player, crafting, craftResult, 266, 50, MODE_CRAFTING));

        addSlot(new ModeSlot(smeltInput, 0, 210, 50, MODE_SMELTING, false));
        addSlot(new ModeSlot(smeltFuel, 0, 210, 82, MODE_SMELTING, false));
        addSlot(new ModeSlot(smeltOutput, 0, 282, 54, MODE_SMELTING, true));

        for (int i = 0; i < 27; i++) {
            addSlot(new ModeSlot(storage, i, 204 + (i % 9) * 20, 30 + (i / 9) * 20,
                    MODE_STORAGE, false));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 204 + col * 20, 150 + row * 20));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 204 + col * 20, 208));
        }

        slotsChanged(module);
    }

    public int getMode() {
        return modeData.get();
    }

    public int getSmeltProgress() {
        return progressData.get();
    }

    public int getBurnProgress() {
        return burnData.get();
    }

    public RollingLightEntity getEntity() {
        return entity;
    }

    @Override
    public boolean stillValid(Player player) {
        if (entityId < 0) {
            return false;
        }
        Entity tracked = player.level().getEntity(entityId);
        return tracked instanceof RollingLightEntity
                && !tracked.isRemoved()
                && player.distanceToSqr(tracked) <= 64.0D;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        int mode = modeOf(module.getItem(0));
        if (mode != modeData.get()) {
            modeData.set(mode);
        }
        if (mode == MODE_CRAFTING && !level.isClientSide) {
            updateCraftResult();
        }
    }

    /** Called once per server tick by {@code MovingLightBlockMod} while this menu is open. */
    public void serverTick() {
        if (!player.level().isClientSide) {
            tickSmelting();
        }
    }

    private static int modeOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return MODE_NONE;
        }
        if (stack.getItem() == Items.CRAFTING_TABLE) {
            return MODE_CRAFTING;
        }
        if (stack.getItem() == Items.FURNACE) {
            return MODE_SMELTING;
        }
        if (stack.getItem() == Items.CHEST) {
            return MODE_STORAGE;
        }
        return MODE_NONE;
    }

    private static boolean isModuleItem(ItemStack stack) {
        return modeOf(stack) != MODE_NONE;
    }

    private void updateCraftResult() {
        CraftingInput input = CraftingInput.of(3, 3, crafting.getItems());
        RecipeHolder<CraftingRecipe> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
        craftResult.setItem(0, recipe == null
                ? ItemStack.EMPTY
                : recipe.value().assemble(input, level.registryAccess()));
        broadcastChanges();
    }

    private void tickSmelting() {
        if (modeData.get() != MODE_SMELTING) {
            smeltProgress = 0;
            progressData.set(0);
            burnData.set(0);
            return;
        }

        ItemStack input = smeltInput.getItem(0);
        ItemStack output = smeltOutput.getItem(0);
        RecipeHolder<SmeltingRecipe> recipe = findSmeltingRecipe(input);
        boolean canSmelt = recipe != null && canAcceptOutput(
                recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess()), output);

        if (burnTime > 0) {
            burnTime--;
        }

        if (burnTime <= 0 && canSmelt && !smeltFuel.getItem(0).isEmpty()) {
            int fuelTime = smeltFuel.getItem(0).getBurnTime(RecipeType.SMELTING);
            if (fuelTime > 0) {
                burnTime = burnDuration = fuelTime;
                smeltFuel.getItem(0).shrink(1);
                if (smeltFuel.getItem(0).isEmpty()) {
                    smeltFuel.setItem(0, ItemStack.EMPTY);
                }
            }
        }

        if (burnTime > 0 && canSmelt) {
            smeltProgress++;
            if (smeltProgress >= COOK_TIME) {
                smeltItem(recipe, input, output);
                smeltProgress = 0;
            }
        } else if (!canSmelt) {
            smeltProgress = 0;
        }

        progressData.set(smeltProgress);
        burnData.set(burnDuration > 0 ? burnTime * COOK_TIME / burnDuration : 0);
    }

    private RecipeHolder<SmeltingRecipe> findSmeltingRecipe(ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        return level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level)
                .orElse(null);
    }

    private static boolean canAcceptOutput(ItemStack result, ItemStack output) {
        if (result.isEmpty()) {
            return false;
        }
        if (output.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void smeltItem(RecipeHolder<SmeltingRecipe> recipe, ItemStack input, ItemStack output) {
        ItemStack result = recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess());
        input.shrink(1);
        if (output.isEmpty()) {
            smeltOutput.setItem(0, result);
        } else {
            output.grow(result.getCount());
        }
        smeltOutput.setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem() || !slot.isActive()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int mode = modeData.get();

        if (index == CRAFT_RESULT) {
            if (!moveItemStackTo(stack, INV_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
            ((ResultSlot) slot).onTake(player, original);
        } else if (index < INV_START) {
            if (!moveItemStackTo(stack, INV_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            int start;
            int end;
            switch (mode) {
                case MODE_CRAFTING -> {
                    start = CRAFT_START;
                    end = CRAFT_RESULT + 1;
                }
                case MODE_SMELTING -> {
                    start = SMELT_INPUT;
                    end = SMELT_RESULT + 1;
                }
                case MODE_STORAGE -> {
                    start = STORAGE_START;
                    end = STORAGE_END;
                }
                default -> {
                    start = MODULE_SLOT;
                    end = MODULE_SLOT + 1;
                }
            }
            if (!moveItemStackTo(stack, start, end, false)) {
                return ItemStack.EMPTY;
            }
        }

        slot.setChanged();
        return original;
    }

    @Override
    public void removed(Player player) {
        if (!level.isClientSide) {
            returnItems(player, module);
            returnItems(player, crafting);
            returnItems(player, craftResult);
            returnItems(player, smeltInput);
            returnItems(player, smeltFuel);
            returnItems(player, smeltOutput);
            returnItems(player, storage);
        }
        super.removed(player);
    }

    private static void returnItems(Player player, Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                container.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private class ModuleSlot extends Slot {
        ModuleSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isModuleItem(stack) && super.mayPlace(stack);
        }
    }

    private class ModeSlot extends Slot {
        private final int requiredMode;
        private final boolean outputOnly;

        ModeSlot(Container container, int index, int x, int y, int requiredMode, boolean outputOnly) {
            super(container, index, x, y);
            this.requiredMode = requiredMode;
            this.outputOnly = outputOnly;
        }

        @Override
        public boolean isActive() {
            return modeData.get() == requiredMode;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !outputOnly && isActive() && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive() && super.mayPickup(player);
        }
    }

    private class ModeResultSlot extends ResultSlot {
        private final int requiredMode;

        ModeResultSlot(Player player, TransientCraftingContainer craftSlots, ResultContainer resultSlots,
                       int x, int y, int requiredMode) {
            super(player, craftSlots, resultSlots, 0, x, y);
            this.requiredMode = requiredMode;
        }

        @Override
        public boolean isActive() {
            return modeData.get() == requiredMode;
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive() && super.mayPickup(player);
        }
    }
}
