package com.bgsoftware.ssboneblock.actions;

import com.bgsoftware.ssboneblock.actions.container.SetContainerAction;
import com.bgsoftware.ssboneblock.error.ParsingException;
import com.bgsoftware.ssboneblock.factory.BlockOffsetFactory;
import com.bgsoftware.ssboneblock.handler.PhasesHandler;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.api.wrappers.BlockOffset;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.lone.itemsadder.api.CustomBlock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import javax.annotation.Nullable;
import java.util.Optional;

public final class SetBlockAction extends Action {

    private final String iaBlockId;
    private final Material type;
    private final byte data;
    private final SetContainerAction containerAction;
    private final String nbt;

    private SetBlockAction(Material type, String iaBlockId, byte data, JsonObject container, @Nullable BlockOffset offsetPosition,
                           String nbt, PhasesHandler phasesHandler, String fileName) {
        super(offsetPosition);
        this.type = type;
        this.iaBlockId = iaBlockId;
        this.data = data;
        this.nbt = module.getNMSAdapter().isLegacy() ? removeBrackets(nbt) : nbt;
        this.containerAction = container == null ? null : SetContainerAction.fromJson(container, phasesHandler, fileName);
    }

    @Override
    public void run(Location location, Island island, @Nullable SuperiorPlayer superiorPlayer) {
        if (offsetPosition != null)
            location = offsetPosition.applyToLocation(location);

        Block block = location.getBlock();
        Key oldKey = block.getType() == Material.AIR ? null : Key.of(block);

        Key newKey;

        if (iaBlockId != null) {
            // Place ItemsAdder custom block
            CustomBlock customBlock = CustomBlock.place(iaBlockId, location);
            if (customBlock == null) {
                module.getPlugin().getLogger().warning("Failed to place IA block: " + iaBlockId);
                return;
            }
            newKey = Key.of("IA:" + iaBlockId); // Custom key prefix for IA blocks
        } else {
            module.getNMSAdapter().setBlock(location, type, data, nbt);

            if (containerAction != null)
                containerAction.run(block.getState());

            newKey = Key.of(type, data);
        }

        if (newKey.equals(oldKey))
            return;

        if (oldKey != null)
            island.handleBlockBreak(oldKey, 1, false);

        island.handleBlockPlace(newKey, 1, false);
    }

    public static Optional<Action> fromJson(JsonObject jsonObject, PhasesHandler phasesHandler, String fileName) throws ParsingException {
        JsonElement blockElement = jsonObject.get("block");

        if (!(blockElement instanceof JsonPrimitive))
            throw new ParsingException("Missing \"block\" section.");

        String block = blockElement.getAsString();
        byte materialData = jsonObject.has("data") ? jsonObject.get("data").getAsByte() : 0;

        Material type = null;
        String iaBlockId = null;

        // Try ItemsAdder first
        if (CustomBlock.isInRegistry(block)) {
            iaBlockId = block;
        } else {
            // Fallback to vanilla material
            try {
                type = Material.valueOf(block.toUpperCase());
            } catch (IllegalArgumentException error) {
                throw new ParsingException("Cannot parse `" + block + "` to a valid material or IA block id.");
            }
        }

        return Optional.of(new SetBlockAction(type, iaBlockId,
                materialData, jsonObject.getAsJsonObject("container"),
                BlockOffsetFactory.createOffset(jsonObject.get("offset")),
                jsonObject.has("nbt") ? (module.getNMSAdapter().isLegacy() ? "" : block) +
                        jsonObject.get("nbt").getAsString() : null,
                phasesHandler, fileName));
    }

    private static String removeBrackets(String str) {
        if (str != null) {
            if (str.startsWith("["))
                str = str.substring(1);
            if (str.endsWith("]"))
                str = str.substring(0, str.length() - 1);
        }
        return str;
    }

}
