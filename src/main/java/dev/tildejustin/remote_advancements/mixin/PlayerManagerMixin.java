package dev.tildejustin.remote_advancements.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.tildejustin.remote_advancements.mixin.accessor.MinecraftServerAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.network.*;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "onPlayerConnect", at = @At(value = "FIELD", target = "Lnet/minecraft/network/packet/s2c/play/CustomPayloadS2CPacket;BRAND:Lnet/minecraft/util/Identifier;"))
    private void sendWorldNamePacket(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci, @Local ServerPlayNetworkHandler serverPlayNetworkHandler) {
        // could string length overflow? max: 32767
        String dir = ((MinecraftServerAccessor) this.server).getSession().getDirectoryName();
        serverPlayNetworkHandler.sendPacket(new CustomPayloadS2CPacket(new Identifier("remote-advancements", "world-name"), new PacketByteBuf(Unpooled.buffer()).writeString(dir)));
    }
}
