package dev.tildejustin.remote_advancements.mixin;

import com.google.common.collect.Maps;
import com.google.gson.*;
import com.llamalad7.mixinextras.sugar.Local;
import dev.tildejustin.remote_advancements.mixin.accessor.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.SharedConstants;
import net.minecraft.advancement.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.stat.*;
import net.minecraft.util.*;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Unique
    private static final Logger LOGGER = LogManager.getLogger();

    @Unique
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(AdvancementProgress.class, new AdvancementProgress.Serializer())
            .registerTypeAdapter(Identifier.class, new Identifier.Serializer())
            .setPrettyPrinting()
            .create();

    @Unique
    private final Identifier autosave = new Identifier("remote-advancements", "save");

    @Unique
    private final Identifier worldNameId = new Identifier("remote-advancements", "world-name");

    @Unique
    private String worldName = null;

    @Shadow
    private MinecraftClient client;

    @Unique
    private Path root = null;

    @Unique
    private static <T> Identifier getStatId(Stat<T> stat) {
        return stat.getType().getRegistry().getId(stat.getValue());
    }

    @Inject(method = "onGameJoin", at = @At("TAIL"))
    private void setCurrentWorldAsUnknown(CallbackInfo ci) {
        if (root == null) {
            root = this.client.runDirectory.toPath().resolve("remote-advancements").resolve("save");
        }

        this.worldName = null;
    }

    @Inject(method = "onCustomPayload", at = @At(value = "FIELD", target = "Lnet/minecraft/network/packet/s2c/play/CustomPayloadS2CPacket;BRAND:Lnet/minecraft/util/Identifier;"), cancellable = true)
    private void onCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci, @Local Identifier identifier, @Local PacketByteBuf data) {
        if (worldNameId.equals(identifier)) {
            ci.cancel();
            worldName = data.readString();
            return;
        }

        if (autosave.equals(identifier)) {
            ci.cancel();
            if (worldName == null) {
                return;
            }

            Path outer = root.resolve(worldName);
            Path statsPath = outer.resolve(WorldSavePath.STATS.getRelativePath());
            Path advancementsPath = outer.resolve(WorldSavePath.ADVANCEMENTS.getRelativePath());

            try {
                Files.createDirectories(outer);
                if (!Files.isDirectory(statsPath)) {
                    Files.createDirectory(statsPath);
                }
                if (!Files.isDirectory(advancementsPath)) {
                    Files.createDirectory(advancementsPath);
                }
            } catch (IOException e) {
                // unlucky
                LOGGER.log(Level.ERROR, "Failed to create world directories", e);
                return;
            }

            StatHandler stats = this.client.player.getStatHandler();
            saveStats(statsPath.resolve(this.client.player.getUuid() + ".json"), ((StatHandlerAccessor) stats).getStatMap());
            ClientAdvancementManager advancements = this.client.player.networkHandler.getAdvancementHandler();
            saveAdvancements(advancementsPath.resolve(this.client.player.getUuid() + ".json"), ((ClientAdvancementManagerAccessor) advancements).getAdvancementProgresses());
        }
    }

    // ServerStatHandler#asString
    @Unique
    protected void saveStats(Path statsFile, Object2IntMap<Stat<?>> statMap) {
        Map<StatType<?>, JsonObject> map = Maps.newHashMap();

        for (Object2IntMap.Entry<Stat<?>> entry : statMap.object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            map.computeIfAbsent(stat.getType(), statType -> new JsonObject()).addProperty(getStatId(stat).toString(), entry.getIntValue());
        }

        JsonObject statsJson = new JsonObject();

        for (Map.Entry<StatType<?>, JsonObject> entry : map.entrySet()) {
            statsJson.add(Registry.STAT_TYPE.getId(entry.getKey()).toString(), entry.getValue());
        }

        JsonObject outer = new JsonObject();
        outer.add("stats", statsJson);
        outer.addProperty("DataVersion", SharedConstants.getGameVersion().getWorldVersion());

        try {
            Files.write(statsFile, outer.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.error("Couldn't save player stats to {}", statsFile, e);
        }
    }

    // PlayerAdvancementTracker#save
    @Unique
    public void saveAdvancements(Path advancementFile, Map<Advancement, AdvancementProgress> advancementToProgress) {
        Map<Identifier, AdvancementProgress> map = Maps.newHashMap();

        for (Map.Entry<Advancement, AdvancementProgress> entry : advancementToProgress.entrySet()) {
            AdvancementProgress advancementProgress = entry.getValue();
            if (advancementProgress.isAnyObtained()) {
                map.put(entry.getKey().getId(), advancementProgress);
            }
        }

        JsonElement outer = GSON.toJsonTree(map);
        outer.getAsJsonObject().addProperty("DataVersion", SharedConstants.getGameVersion().getWorldVersion());

        try {
            Files.write(advancementFile, GSON.toJson(outer).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.error("Couldn't save player advancements to {}", advancementFile, e);
        }
    }
}
