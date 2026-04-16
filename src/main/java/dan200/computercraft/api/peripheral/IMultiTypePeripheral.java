package dan200.computercraft.api.peripheral;

/**
 * Optional extension of {@link IPeripheral} for peripherals that expose more than one type
 * string, matching the CC:Tweaked multi-type peripheral contract.
 *
 * <p>
 * When {@link dan200.computercraft.core.apis.PeripheralAPI} detects this interface it returns
 * all values from {@link #getTypes()} as separate Lua return values from
 * {@code peripheral.getType()}, allowing {@code peripheral.hasType()} and
 * {@code peripheral.find()} to match on any of the declared types.
 * </p>
 *
 * <p>
 * {@link #getType()} (from {@link IPeripheral}) must still return the <em>primary</em> type
 * (i.e. {@code getTypes()[0]}) for backward-compatibility with code that only reads the first
 * return value.
 * </p>
 */
public interface IMultiTypePeripheral extends IPeripheral {

    /**
     * Returns all types this peripheral satisfies, in priority order. The first entry must
     * equal the value returned by {@link #getType()}.
     *
     * @return a non-empty array of type strings, never {@code null}
     */
    String[] getTypes();
}
