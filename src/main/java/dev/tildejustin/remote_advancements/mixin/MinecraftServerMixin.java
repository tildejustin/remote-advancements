package dev.tildejustin.remote_advancements.mixin;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.*;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow
    public abstract PlayerManager getPlayerManager();

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;saveAllPlayerData()V"))
    private void sendPacketForAutosave(CallbackInfo ci) {
        this.getPlayerManager().sendToAll(new CustomPayloadS2CPacket(new Identifier("remote-advancements", "autosave"), new PacketByteBuf(Unpooled.buffer(0))));
    }
}
