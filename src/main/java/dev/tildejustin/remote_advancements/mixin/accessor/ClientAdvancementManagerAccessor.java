package dev.tildejustin.remote_advancements.mixin.accessor;

import net.minecraft.advancement.*;
import net.minecraft.client.network.ClientAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ClientAdvancementManager.class)
public interface ClientAdvancementManagerAccessor {
    @Accessor("advancementProgresses")
    Map<Advancement, AdvancementProgress> getAdvancementProgresses();
}
