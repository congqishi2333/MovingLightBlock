package com.congqishi.movinglightblock.menu;

import com.congqishi.movinglightblock.ModMenus;
import com.congqishi.movinglightblock.MovingLightBlockMod;
import com.congqishi.movinglightblock.entity.RollingLightEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
 * <p>The module slot accepts a crafting table, furnace or chest item. That choice enables the
 * matching panel: 3x3 crafting, smelting, or 18-slot storage. Player inventory is always
 * available at the bottom. Storage mode scales the same artwork and 18 slots up to 256x242.</p>
 */
public class RollingLightMenu extends AbstractContainerMenu {
    public static final int MODE_NONE = 0;
    public static final int MODE_CRAFTING = 1;
    public static final int MODE_SMELTING = 2;
    public static final int MODE_STORAGE = 3;

    private static final int MODULE_SLOT = 0;
    private static final int TORCH_SLOT = 1;
    private static final int CRAFT_START = 2;
    private static final int CRAFT_RESULT = 11;
    private static final int SMELT_INPUT = 12;
    private static final int SMELT_FUEL = 13;
    private static final int SMELT_RESULT = 14;
    /** Storage artwork is 176x166; the enlarged GUI renders it at 256x242. */
    public static final int STORAGE_GUI_WIDTH = 256;
    public static final int STORAGE_GUI_HEIGHT = 242;

    private static final int STORAGE_COUNT = 18;
    private static final int STORAGE_START = 15;
    private static final int STORAGE_END = STORAGE_START + STORAGE_COUNT;
    private static final int INV_START = STORAGE_END;
    private static final int INV_END = INV_START + 36;

    private static final int COOK_TIME = 200;

    /** Converts an artwork coordinate to the enlarged storage GUI coordinate. */
    public static int scaleX(int value) {
        return Math.round(value * (float) STORAGE_GUI_WIDTH / 176.0F);
    }

    public static int scaleY(int value) {
        return Math.round(value * (float) STORAGE_GUI_HEIGHT / 166.0F);
    }

    /** Module slot position for each mode, matching the corresponding GUI texture cell. */
    private static final int[] MODULE_X = {132, 132, 132, scaleX(136)};
    private static final int[] MODULE_Y = {6, 6, 7, scaleY(63)};

    /** Torch slot position for each mode, matching the second top cell in every texture. */
    private static final int[] TORCH_X = {152, 152, 152, scaleX(154)};
    private static final int[] TORCH_Y = {6, 6, 7, scaleY(63)};

    private final RollingLightEntity entity;
    private final int entityId;
    private final Player player;
    private final Inventory inventory;
    private final Level level;

    private final SimpleContainer module = new SimpleContainer(1);
    private final TorchContainer torches = new TorchContainer();
    private final TransientCraftingContainer crafting;
    private final ResultContainer craftResult = new ResultContainer();
    private final SimpleContainer smeltInput = new SimpleContainer(1);
    private final SimpleContainer smeltFuel = new SimpleContainer(1);
    private final SimpleContainer smeltOutput = new SimpleContainer(1);
    private final SimpleContainer storage = new SimpleContainer(STORAGE_COUNT);
    private ModuleSlot moduleSlot;
    private TorchSlot torchSlot;
    private int layoutMode = -1;
    private boolean building = true;

    private final DataSlot modeData;
    private final DataSlot lightData;
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
        this.inventory = inventory;
        this.level = inventory.player.level();

        this.modeData = DataSlot.standalone();
        this.lightData = DataSlot.standalone();
        this.progressData = DataSlot.standalone();
        this.burnData = DataSlot.standalone();
        addDataSlot(modeData);
        addDataSlot(lightData);
        addDataSlot(progressData);
        addDataSlot(burnData);

        this.moduleSlot = new ModuleSlot(module, 0, MODULE_X[MODE_NONE], MODULE_Y[MODE_NONE]);
        this.torchSlot = new TorchSlot(torches, 0, TORCH_X[MODE_NONE], TORCH_Y[MODE_NONE]);
        addSlot(this.moduleSlot);
        addSlot(this.torchSlot);

        this.crafting = new TransientCraftingContainer(this, 3, 3);

        // On the server the cube owns the persistent contents; the client receives the
        // light level through lightData when the menu is opened. This must happen after all
        // containers exist: applyStoredMenu() fills the crafting container too.
        if (entity != null && !level.isClientSide) {
            applyStoredMenu(entity.getStoredMenu());
            int savedLight = Mth.clamp(entity.getLightLevel(), 0, 15);
            this.lightData.set(savedLight);
            this.torches.setItem(0, savedLight == 0
                    ? ItemStack.EMPTY : new ItemStack(Items.TORCH, savedLight));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new ModeSlot(crafting, col + row * 3, 70 + col * 18, 25 + row * 18,
                        MODE_CRAFTING, false));
            }
        }
        addSlot(new ModeResultSlot(player, crafting, craftResult, 134, 43, MODE_CRAFTING));

        addSlot(new ModeSlot(smeltInput, 0, 70, 22, MODE_SMELTING, false));
        addSlot(new ModeSlot(smeltFuel, 0, 152, 7, MODE_SMELTING, false));
        addSlot(new ModeSlot(smeltOutput, 0, 130, 40, MODE_SMELTING, true));

        // Storage keeps its original 6x3 cells, just scaled up with the artwork.
        for (int i = 0; i < STORAGE_COUNT; i++) {
            addSlot(new ModeSlot(storage, i,
                    scaleX(64 + (i % 6) * 18), scaleY(7 + (i / 6) * 18),
                    MODE_STORAGE, false));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }

        // Stored contents may already have filled the module slot while the menu was still
        // being assembled. Apply the final mode only after every slot exists.
        this.building = false;
        slotsChanged(module);
    }

    public int getMode() {
        return modeData.get();
    }

    /**
     * Rebuilds the module and torch slots on the cells drawn in the current mode's texture.
     * Slot.x/y are final, so the slots are replaced rather than moved; keeping them at index 0
     * and 1 preserves the indices used by quick-move logic.
     */
    public void updateSpecialSlots() {
        int mode = modeData.get();
        if (mode < 0 || mode >= MODULE_X.length) {
            mode = MODE_NONE;
        }
        if (mode == this.layoutMode) {
            return;
        }
        this.layoutMode = mode;

        int mx = MODULE_X[mode];
        int my = MODULE_Y[mode];
        int tx = TORCH_X[mode];
        int ty = TORCH_Y[mode];
        this.slots.removeIf(slot -> slot == this.moduleSlot || slot == this.torchSlot);
        this.torchSlot = new TorchSlot(this.torches, 0, tx, ty);
        this.moduleSlot = new ModuleSlot(this.module, 0, mx, my);
        this.slots.add(0, this.torchSlot);
        this.slots.add(0, this.moduleSlot);

        // Storage keeps the same inventory cells, scaled with the enlarged artwork.
        // Replacing slots in place keeps their indices stable.
        boolean large = mode == MODE_STORAGE;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = 8 + col * 18;
                int y = 84 + row * 18;
                this.slots.set(INV_START + row * 9 + col,
                        new Slot(this.inventory, 9 + row * 9 + col,
                                large ? scaleX(x) : x, large ? scaleY(y) : y));
            }
        }
        for (int col = 0; col < 9; col++) {
            int x = 8 + col * 18;
            this.slots.set(INV_START + 27 + col,
                    new Slot(this.inventory, col,
                            large ? scaleX(x) : x, large ? scaleY(142) : 142));
        }
    }

    public boolean isTorchSlot(Slot slot) {
        return slot != null && slot == this.torchSlot;
    }

    /** Number of torches installed in the cube, which is also its real block light level. */
    public int getLightLevel() {
        return Mth.clamp(this.lightData.get(), 0, 15);
    }

    private void setTorchLevel(int level) {
        int clamped = Mth.clamp(level, 0, 15);
        if (this.lightData.get() == clamped) {
            return;
        }
        this.lightData.set(clamped);
        if (this.entity != null && !this.level.isClientSide) {
            this.entity.setLightLevel(clamped);
        }
    }

    /** Restores the cube's stored GUI contents when its menu is opened. */
    private void applyStoredMenu(CompoundTag tag) {
        HolderLookup.Provider registries = level.registryAccess();
        module.setItem(0, loadStack(tag, "Module", registries));
        craftResult.setItem(0, loadStack(tag, "CraftResult", registries));
        smeltInput.setItem(0, loadStack(tag, "SmeltInput", registries));
        smeltFuel.setItem(0, loadStack(tag, "SmeltFuel", registries));
        smeltOutput.setItem(0, loadStack(tag, "SmeltOutput", registries));
        loadStacks(tag.getList("Crafting", Tag.TAG_COMPOUND), registries, crafting);
        loadStacks(tag.getList("Storage", Tag.TAG_COMPOUND), registries, storage);
    }

    /** Writes the current GUI contents back to the cube so they survive close/retract. */
    public void saveToEntity() {
        if (entity == null || level.isClientSide) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        HolderLookup.Provider registries = level.registryAccess();
        saveStack(tag, "Module", module.getItem(0), registries);
        saveStack(tag, "CraftResult", craftResult.getItem(0), registries);
        saveStack(tag, "SmeltInput", smeltInput.getItem(0), registries);
        saveStack(tag, "SmeltFuel", smeltFuel.getItem(0), registries);
        saveStack(tag, "SmeltOutput", smeltOutput.getItem(0), registries);
        tag.put("Crafting", saveStacks(registries, crafting.getContainerSize(), crafting::getItem));
        tag.put("Storage", saveStacks(registries, storage.getContainerSize(), storage::getItem));
        entity.setStoredMenu(tag);
    }

    /** Empties the GUI after its contents have been copied into the retract item. */
    public void clearContents() {
        module.clearContent();
        torches.clearContent();
        crafting.clearContent();
        craftResult.clearContent();
        smeltInput.clearContent();
        smeltFuel.clearContent();
        smeltOutput.clearContent();
        storage.clearContent();
    }

    private static void saveStack(CompoundTag tag, String key, ItemStack stack,
                                  HolderLookup.Provider registries) {
        if (!stack.isEmpty()) {
            tag.put(key, stack.save(registries, new CompoundTag()));
        }
    }

    private static ItemStack loadStack(CompoundTag tag, String key,
                                       HolderLookup.Provider registries) {
        return tag.contains(key, Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound(key))
                : ItemStack.EMPTY;
    }

    private interface StackSource {
        ItemStack get(int index);
    }

    private static ListTag saveStacks(HolderLookup.Provider registries, int size,
                                      StackSource source) {
        ListTag list = new ListTag();
        for (int i = 0; i < size; i++) {
            ItemStack stack = source.get(i);
            // ItemStack.save() throws on EMPTY, while an empty compound is the correct
            // sentinel for an empty slot and is restored by loadStacks() as EMPTY.
            list.add(stack.isEmpty()
                    ? new CompoundTag()
                    : stack.save(registries, new CompoundTag()));
        }
        return list;
    }

    private static void loadStacks(ListTag list, HolderLookup.Provider registries,
                                   net.minecraft.world.Container container) {
        if (container == null) {
            return;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            Tag tag = i < list.size() ? list.get(i) : null;
            container.setItem(i, tag instanceof CompoundTag compound
                    ? ItemStack.parseOptional(registries, compound) : ItemStack.EMPTY);
        }
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
        if (this.building) {
            return;
        }
        int mode = modeOf(module.getItem(0));
        boolean changed = mode != modeData.get();
        if (changed) {
            modeData.set(mode);
            // Push the new mode to the client immediately; otherwise the panel only
            // appears after another inventory refresh or on the next GUI open.
            if (!level.isClientSide) {
                broadcastChanges();
            }
        }
        updateSpecialSlots();
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

        if (index == TORCH_SLOT) {
            if (!moveItemStackTo(stack, INV_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index == CRAFT_RESULT) {
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
            boolean movedToTorch = stack.getItem() == Items.TORCH
                    && moveItemStackTo(stack, TORCH_SLOT, TORCH_SLOT + 1, false);
            if (!movedToTorch && !moveItemStackTo(stack, start, end, false)) {
                return ItemStack.EMPTY;
            }
        }

        slot.setChanged();
        return original;
    }

    @Override
    public void removed(Player player) {
        if (!level.isClientSide) {
            if (entity != null && !entity.isRemoved()) {
                // The cube keeps its contents while it exists, just like a chest or furnace.
                saveToEntity();
            } else {
                // No cube to save into (for example after a forced removal): never lose items.
                returnItems(player, module);
                returnItems(player, crafting);
                returnItems(player, craftResult);
                returnItems(player, smeltInput);
                returnItems(player, smeltFuel);
                returnItems(player, smeltOutput);
                returnItems(player, storage);
            }
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

    /**
     * The cube's one persistent torch slot. Its count is mirrored into {@link #lightData} and
     * into the entity's NBT, so the light stays with the cube after the GUI is closed.
     */
    private class TorchContainer extends SimpleContainer {
        private ItemStack stack = ItemStack.EMPTY;

        TorchContainer() {
            super(1);
        }

        @Override
        public ItemStack getItem(int index) {
            return stack;
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = stack.split(Math.min(count, stack.getCount()));
            setChanged();
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack taken = stack;
            stack = ItemStack.EMPTY;
            setTorchLevel(0);
            return taken;
        }

        @Override
        public void setItem(int index, ItemStack incoming) {
            // Keep the whole stack: silently truncating 64 torches to 15 lost the remainder.
            stack = incoming.isEmpty() || incoming.getItem() != Items.TORCH
                    ? ItemStack.EMPTY : incoming.copy();
            setTorchLevel(stack.getCount());
        }

        @Override
        public int getMaxStackSize() {
            return 15;
        }

        @Override
        public void setChanged() {
            setTorchLevel(stack.getCount());
        }

        @Override
        public boolean isEmpty() {
            return stack.isEmpty();
        }

        @Override
        public void clearContent() {
            stack = ItemStack.EMPTY;
            setTorchLevel(0);
        }
    }

    private class TorchSlot extends Slot {
        TorchSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() == Items.TORCH && super.mayPlace(stack);
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
