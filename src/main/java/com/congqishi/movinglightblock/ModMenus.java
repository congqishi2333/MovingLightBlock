package com.congqishi.movinglightblock;

import com.congqishi.movinglightblock.menu.RollingLightMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MovingLightBlockMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RollingLightMenu>> ROLLING_LIGHT =
            MENUS.register("rolling_light", () -> new MenuType<>(
                    (IContainerFactory<RollingLightMenu>) RollingLightMenu::fromNetwork,
                    FeatureFlags.VANILLA_SET));

    private ModMenus() {
    }
}
