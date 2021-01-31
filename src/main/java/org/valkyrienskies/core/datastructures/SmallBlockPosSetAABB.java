package org.valkyrienskies.core.datastructures;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.joml.primitives.AABBd;
import org.valkyrienskies.core.game.ChunkClaim;
import org.valkyrienskies.core.util.VSIterationUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Iterator;

/**
 * A wrapper around SmallBlockPosSet that can make create tight AxisAlignedBB containing all BlockPos in the Set.
 * All operations (except clear) run in O(1) average time.
 */
@JsonDeserialize(using = SmallBlockPosSetAABB.SmallBlockPosSetAABBDeserializer.class)
@JsonSerialize(using = SmallBlockPosSetAABB.SmallBlockPosSetAABBSerializer.class)
public class SmallBlockPosSetAABB implements IBlockPosSetAABB {

    private final SmallBlockPosSet blockPosSet;
    private final int centerX, centerY, centerZ;
    private final int xSize, ySize, zSize;
    private final FastMinMaxMap xMap, yMap, zMap; // Only non-final so we can clear() quickly.

    public SmallBlockPosSetAABB(ChunkClaim chunkClaim) {
        final Vector3ic centerCoordinates = chunkClaim.getCenterBlockCoordinates(new Vector3i());
        final Vector3ic claimSize = chunkClaim.getBlockSize(new Vector3i());

        this.blockPosSet = new SmallBlockPosSet(centerCoordinates.x(), centerCoordinates.z());
        this.centerX = centerCoordinates.x();
        this.centerY = centerCoordinates.y();
        this.centerZ = centerCoordinates.z();
        this.xSize = claimSize.x();
        this.ySize = claimSize.y();
        this.zSize = claimSize.z();
        this.xMap = new FastMinMaxMap(xSize);
        this.yMap = new FastMinMaxMap(ySize);
        this.zMap = new FastMinMaxMap(zSize);
    }

    public SmallBlockPosSetAABB(int centerX, int centerY, int centerZ, int xSize, int ySize, int zSize) {
        this(new SmallBlockPosSet(centerX, centerZ), centerX, centerY, centerZ, xSize, ySize, zSize);
    }

    private SmallBlockPosSetAABB(SmallBlockPosSet blockPosSet, int centerX, int centerY, int centerZ, int xSize, int ySize, int zSize) {
        this.blockPosSet = blockPosSet;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.xSize = xSize;
        this.ySize = ySize;
        this.zSize = zSize;
        this.xMap = new FastMinMaxMap(xSize);
        this.yMap = new FastMinMaxMap(ySize);
        this.zMap = new FastMinMaxMap(zSize);
    }

    @Nullable
    @Override
    public AABBd makeAABB() {
        if (blockPosSet.isEmpty()) {
            return null;
        } else {
            int minX = xMap.getFront() - (xSize / 2), maxX = xMap.getBack() - (xSize / 2);
            int minY = yMap.getFront() - (ySize / 2), maxY = yMap.getBack() - (ySize / 2);
            int minZ = zMap.getFront() - (zSize / 2), maxZ = zMap.getBack() - (zSize / 2);
            minX += blockPosSet.getCenterX(); maxX += blockPosSet.getCenterX();
            minZ += blockPosSet.getCenterZ(); maxZ += blockPosSet.getCenterZ();
            return new AABBd(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    @Override
    public boolean add(int x, int y, int z) throws IllegalArgumentException {
        boolean setResult = blockPosSet.add(x, y, z);
        if (setResult) {
            incrementAABBMaker(x, y, z);
        }
        return setResult;
    }

    private void incrementAABBMaker(int x, int y, int z) {
        xMap.increment(x - blockPosSet.getCenterX() + (xSize / 2));
        yMap.increment(y + (ySize / 2));
        zMap.increment(z - blockPosSet.getCenterZ() + (zSize / 2));
    }

    @Override
    public boolean remove(int x, int y, int z) {
        boolean setResult = blockPosSet.remove(x, y, z);
        if (setResult) {
            decrementAABBMaker(x, y, z);
        }
        return setResult;
    }

    private void decrementAABBMaker(int x, int y, int z) {
        xMap.decrement(x - blockPosSet.getCenterX() + (xSize / 2));
        yMap.decrement(y + (ySize / 2));
        zMap.decrement(z - blockPosSet.getCenterZ() + (zSize / 2));
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return blockPosSet.contains(x, y, z);
    }

    @Override
    public boolean canStore(int x, int y, int z) {
        return blockPosSet.canStore(x, y, z);
    }

    @Override
    public int size() {
        return blockPosSet.size();
    }

    @Override
	@Nonnull
    public Iterator<Vector3ic> iterator() {
        return blockPosSet.iterator();
    }

    @Override
    public void clear() {
        blockPosSet.clear();
        this.xMap.clear();
        this.yMap.clear();
        this.zMap.clear();
    }

    @Override
    public void forEach(@Nonnull VSIterationUtils.IntTernaryConsumer action) {
        blockPosSet.forEach(action);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof IBlockPosSetAABB) {
            return (((IBlockPosSetAABB) other).size() == size()) && ((IBlockPosSetAABB) other).containsAll(this);
        }
        return false;
    }

    public static class SmallBlockPosSetAABBSerializer extends StdSerializer<SmallBlockPosSetAABB> {

        public SmallBlockPosSetAABBSerializer() {
            super((Class<SmallBlockPosSetAABB>) null);
        }

        @Override
        public void serialize(SmallBlockPosSetAABB value, JsonGenerator gen,
                              SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeObjectField("blockPosSet", value.blockPosSet);
            gen.writeNumberField("centerX", value.centerX);
            gen.writeNumberField("centerY", value.centerY);
            gen.writeNumberField("centerZ", value.centerZ);
            gen.writeNumberField("xSize", value.xSize);
            gen.writeNumberField("ySize", value.ySize);
            gen.writeNumberField("zSize", value.zSize);
            gen.writeEndObject();
        }
    }

    public static class SmallBlockPosSetAABBDeserializer extends StdDeserializer<SmallBlockPosSetAABB> {

        private final ObjectMapper objectMapper = new ObjectMapper();

        public SmallBlockPosSetAABBDeserializer() {
            super((Class<?>) null);
        }

        @Override
        public SmallBlockPosSetAABB deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            // The blockPosSet gets loaded
            SmallBlockPosSet blockPosSet = objectMapper.treeToValue(node.get("blockPosSet"), SmallBlockPosSet.class);
            int centerX = node.get("centerX").asInt();
            int centerY = node.get("centerY").asInt();
            int centerZ = node.get("centerZ").asInt();
            int xSize = node.get("xSize").asInt();
            int ySize = node.get("ySize").asInt();
            int zSize = node.get("zSize").asInt();
            SmallBlockPosSetAABB wrapper = new SmallBlockPosSetAABB(blockPosSet, centerX, centerY, centerZ, xSize, ySize, zSize);
            // The AABB maker is a derivative of the blockPosSet.
            blockPosSet.forEach(wrapper::incrementAABBMaker);
            return wrapper;
        }
    }
}
