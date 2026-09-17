package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.peripheral.IPeripheral;

class PeripheralMetadataTest {

    @Test
    void nullTypeIsRejectedBeforePeripheralIsQueued() {
        PeripheralAPI api = new PeripheralAPI(mock(IAPIEnvironment.class));
        IPeripheral peripheral = mock(IPeripheral.class);
        when(peripheral.getMethodNames()).thenReturn(new String[0]);
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> api.onPeripheralChanged(0, peripheral));
        assertEquals("Peripheral type must not be null", error.getMessage());
    }

    @Test
    void nullMethodArrayIsRejectedBeforePeripheralIsQueued() {
        PeripheralAPI api = new PeripheralAPI(mock(IAPIEnvironment.class));
        IPeripheral peripheral = mock(IPeripheral.class);
        when(peripheral.getType()).thenReturn("test");
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> api.onPeripheralChanged(0, peripheral));
        assertEquals("Peripheral method names must not be null", error.getMessage());
    }

    @Test
    void validMetadataStillAllowsEmptyArraysAndSkipsNullMethodEntries() throws Exception {
        PeripheralAPI api = new PeripheralAPI(mock(IAPIEnvironment.class));
        IPeripheral peripheral = mock(IPeripheral.class);
        when(peripheral.getType()).thenReturn("test");
        // Construct only the wrapper, avoiding unrelated async attach work in the global task pool.
        Class<?> wrapperClass = Class.forName(PeripheralAPI.class.getName() + "$PeripheralWrapper");
        Constructor<?> constructor = wrapperClass
            .getDeclaredConstructor(PeripheralAPI.class, IPeripheral.class, String.class);
        constructor.setAccessible(true);
        Field methods = wrapperClass.getDeclaredField("m_methodMap");
        methods.setAccessible(true);

        when(peripheral.getMethodNames()).thenReturn(new String[0]);
        Object empty = constructor.newInstance(api, peripheral, "bottom");
        assertTrue(((Map<?, ?>) methods.get(empty)).isEmpty());

        when(peripheral.getMethodNames()).thenReturn(new String[] { "first", null, "last" });
        Object sparse = constructor.newInstance(api, peripheral, "bottom");
        Map<?, ?> methodMap = (Map<?, ?>) methods.get(sparse);
        assertEquals(2, methodMap.size());
        assertEquals(0, methodMap.get("first"));
        assertEquals(2, methodMap.get("last"));
        assertFalse(methodMap.containsKey(null));
    }
}
