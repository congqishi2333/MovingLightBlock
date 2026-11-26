package com.congqishi.movinglightblock;

import com.congqishi.movinglightblock.entity.RollingLightEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MovingLightBlockMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<RollingLightEntity>> ROLLING_LIGHT =
            ENTITIES.register("rolling_light", () -> EntityType.Builder
                    .of(RollingLightEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .build("movinglightblock:rolling_light"));

    private ModEntities() {
    }
}
