package dev.tildejustin.remote_advancements.mixin;

import com.google.common.base.Charsets;
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
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.*;
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
    private final Identifier autosave = new Identifier("remote-advancements", "autosave");

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

    @Inject(method = "onCustomPayload", at = @At(value = "FIELD", target = "Lnet/minecraft/network/packet/s2c/play/CustomPayloadS2CPacket;BRAND:Lnet/minecraft/util/Identifier;"), cancellable = true)
    private void onCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci, @Local Identifier identifier, @Local PacketByteBuf packetByteBuf) throws IOException {
        if (root == null) {
            root = this.client.runDirectory.toPath().resolve("remote-advancements").resolve("save");
        }

        if (autosave.equals(identifier)) {
            if (worldName == null) {
                return;
            }

            Path outer = root.resolve(worldName);
            Path statsPath = outer.resolve(WorldSavePath.STATS.getRelativePath());
            Path advancementsPath = outer.resolve(WorldSavePath.ADVANCEMENTS.getRelativePath());

            StatHandler stats = this.client.player.getStatHandler();
            // ServerStatHandler#save
            FileUtils.writeStringToFile(statsPath.resolve(this.client.player.getUuid() + ".json").toFile(), statsAsString(((StatHandlerAccessor) stats).getStatMap()));
            ClientAdvancementManager advancements = this.client.player.networkHandler.getAdvancementHandler();
            saveAdvancements(advancementsPath.resolve(this.client.player.getUuid() + ".json").toFile(), ((ClientAdvancementManagerAccessor) advancements).getAdvancementProgresses());
            ci.cancel(); // will still trigger finally
        } else if (worldNameId.equals(identifier)) {
            worldName = packetByteBuf.readString();
            Path outer = root.resolve(worldName);
            Files.createDirectories(outer);
            Path stats = outer.resolve(WorldSavePath.STATS.getRelativePath());
            Files.deleteIfExists(stats);
            Files.createDirectory(stats);
            Path advancements = outer.resolve(WorldSavePath.ADVANCEMENTS.getRelativePath());
            Files.deleteIfExists(advancements);
            Files.createDirectory(advancements);
            ci.cancel(); // will still trigger finally
        }
    }

    // ServerStatHandler#asString
    @Unique
    protected String statsAsString(Object2IntMap<Stat<?>> statMap) {
        Map<StatType<?>, JsonObject> map = Maps.newHashMap();

        for (Object2IntMap.Entry<Stat<?>> entry : statMap.object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            map.computeIfAbsent(stat.getType(), statType -> new JsonObject())
                    .addProperty(getStatId(stat).toString(), entry.getIntValue());
        }

        JsonObject stats = new JsonObject();

        for (Map.Entry<StatType<?>, JsonObject> entry : map.entrySet()) {
            stats.add(Registry.STAT_TYPE.getId(entry.getKey()).toString(), entry.getValue());
        }

        JsonObject outer = new JsonObject();
        outer.add("stats", stats);
        outer.addProperty("DataVersion", SharedConstants.getGameVersion().getWorldVersion());
        return outer.toString();
    }

    // PlayerAdvancementTracker#save
    @Unique
    public void saveAdvancements(File advancementFile, Map<Advancement, AdvancementProgress> advancementToProgress) {
        Map<Identifier, AdvancementProgress> map = Maps.newHashMap();

        for (Map.Entry<Advancement, AdvancementProgress> entry : advancementToProgress.entrySet()) {
            AdvancementProgress advancementProgress = entry.getValue();
            if (advancementProgress.isAnyObtained()) {
                map.put(entry.getKey().getId(), advancementProgress);
            }
        }

        JsonElement jsonElement = GSON.toJsonTree(map);
        jsonElement.getAsJsonObject().addProperty("DataVersion", SharedConstants.getGameVersion().getWorldVersion());

        try {
            OutputStream outputStream = new FileOutputStream(advancementFile);
            Throwable exception = null;

            try {
                Writer writer = new OutputStreamWriter(outputStream, Charsets.UTF_8.newEncoder());
                Throwable exception2 = null;

                try {
                    GSON.toJson(jsonElement, writer);
                } catch (Throwable e) {
                    exception2 = e;
                    throw e;
                } finally {
                    if (writer != null) {
                        if (exception2 != null) {
                            try {
                                writer.close();
                            } catch (Throwable e) {
                                exception2.addSuppressed(e);
                            }
                        } else {
                            writer.close();
                        }
                    }
                }
            } catch (Throwable e) {
                exception = e;
                throw e;
            } finally {
                if (outputStream != null) {
                    if (exception != null) {
                        try {
                            outputStream.close();
                        } catch (Throwable e) {
                            exception.addSuppressed(e);
                        }
                    } else {
                        outputStream.close();
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Couldn't save player advancements to {}", advancementFile, e);
        }
    }
}
