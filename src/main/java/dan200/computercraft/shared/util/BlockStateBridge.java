package dan200.computercraft.shared.util;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import dan200.computercraft.ComputerCraft;

/**
 * Reads modern-style named block states through GTNHLib, falling back to metadata.
 *
 * <h2>Why this is reflective</h2>
 * <p>
 * 1.7.10 has no block states; blocks are identified by {@code Block} plus an opaque metadata
 * integer. GTNHLib adds a real property registry
 * ({@code com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry}) which mods register
 * properties with, and ETFR uses it. GTNHLib is LGPL-3.0 and must stay optional, so nothing is
 * linked at compile time: the class is looked up by name once and every call is guarded.
 * </p>
 * <p>
 * Without GTNHLib — or for a block nobody registered properties for — the state table degrades to
 * {@code { metadata = n } }, which is what this mod exposed before.
 * </p>
 */
public final class BlockStateBridge {

    private static final String REGISTRY = "com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry";

    /** Resolved lazily: {@code null} means "not looked up yet", {@code ABSENT} means "not present". */
    private static final Object ABSENT = new Object();

    private static Object registryLookup;

    private BlockStateBridge() {}

    /**
     * The named state of a block, or {@code { metadata = n } } when no properties are available.
     *
     * @param world    the world, used for position-dependent properties
     * @param x        block x
     * @param y        block y
     * @param z        block z
     * @param block    the block, may be {@code null} for air
     * @param metadata the block's metadata
     * @return a table of property name to value, always containing {@code metadata} when falling back
     */
    public static Map<Object, Object> getState(IBlockAccess world, int x, int y, int z, Block block, int metadata) {
        Map<Object, Object> fromGtnhLib = tryGtnhLib(world, x, y, z);
        if (fromGtnhLib != null && !fromGtnhLib.isEmpty()) return fromGtnhLib;
        return metadataFallback(metadata);
    }

    /** The plain 1.7.10 representation: just the metadata integer. */
    public static Map<Object, Object> metadataFallback(int metadata) {
        Map<Object, Object> state = new HashMap<Object, Object>();
        state.put("metadata", metadata);
        return state;
    }

    /**
     * Whether GTNHLib's block-state registry is available.
     *
     * @return {@code true} when {@code BlockPropertyRegistry} resolved
     */
    public static boolean hasGtnhLib() {
        return registry() != ABSENT;
    }

    private static Object registry() {
        if (registryLookup == null) {
            try {
                Class<?> registryClass = Class.forName(REGISTRY);
                registryLookup = registryClass
                    .getMethod("getBlockState", IBlockAccess.class, int.class, int.class, int.class);
            } catch (ClassNotFoundException e) {
                registryLookup = ABSENT;
            } catch (NoSuchMethodException | LinkageError e) {
                ComputerCraft.logger
                    .warn("GTNHLib is present but BlockPropertyRegistry.getBlockState is not usable", e);
                registryLookup = ABSENT;
            }
        }
        return registryLookup;
    }

    /**
     * Asks GTNHLib for the block's state.
     *
     * @return the properties, or {@code null} when GTNHLib is absent or the call failed
     */
    @SuppressWarnings("unchecked")
    private static Map<Object, Object> tryGtnhLib(IBlockAccess world, int x, int y, int z) {
        Object method = registry();
        if (method == ABSENT || world == null) return null;

        final Map<Object, Object> state = new HashMap<Object, Object>();
        try {
            Object blockState = ((Method) method).invoke(null, world, x, y, z);
            if (blockState == null) return null;

            // BlockState.forEachValue(BiConsumer<String, Object>) exposes typed values, which is
            // what modern CC: Tweaked reports. toMap() would stringify everything.
            Method forEach = blockState.getClass()
                .getMethod("forEachValue", BiConsumer.class);
            forEach.invoke(blockState, (BiConsumer<String, Object>) (name, value) -> {
                Object converted = convert(value);
                if (converted != null) state.put(name, converted);
            });
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            // A misbehaving bridge must never break inspect(); fall back to metadata.
            ComputerCraft.logger.debug("Could not read a GTNHLib block state", e);
            return null;
        }

        return state;
    }

    /**
     * Converts a property value into something the Lua converter understands.
     *
     * @param value the raw property value
     * @return a String, Number, Boolean or null
     */
    private static Object convert(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean || value instanceof Number || value instanceof String) return value;
        // Enums (Direction, Half, ...) are reported by name, lowercased like 1.20 property names.
        if (value instanceof Enum) {
            return ((Enum<?>) value).name()
                .toLowerCase();
        }
        return String.valueOf(value);
    }
}
