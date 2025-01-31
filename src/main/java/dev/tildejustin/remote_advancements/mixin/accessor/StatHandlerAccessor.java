package dev.tildejustin.remote_advancements.mixin.accessor;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.stat.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StatHandler.class)
public interface StatHandlerAccessor {
    @Accessor("statMap")
    Object2IntMap<Stat<?>> getStatMap();
}
