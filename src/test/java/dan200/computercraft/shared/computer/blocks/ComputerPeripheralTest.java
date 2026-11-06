package dan200.computercraft.shared.computer.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import dan200.computercraft.shared.computer.core.ServerComputer;

class ComputerPeripheralTest {

    @Test
    void exposesTheModernMethodSurface() {
        ComputerPeripheral peripheral = new ComputerPeripheral("computer", mock(ServerComputer.class));
        List<String> methods = Arrays.asList(peripheral.getMethodNames());

        assertEquals(Arrays.asList("turnOn", "shutdown", "reboot", "getID", "isOn", "getLabel"), methods);
    }

    @Test
    void getLabelReadsTheComputerLabel() throws Exception {
        ServerComputer computer = mock(ServerComputer.class);
        when(computer.getLabel()).thenReturn("my-computer");
        ComputerPeripheral peripheral = new ComputerPeripheral("computer", computer);

        Object[] result = peripheral.callMethod(null, null, 5, new Object[0]);

        assertEquals(1, result.length);
        assertEquals("my-computer", result[0]);
    }

    @Test
    void getLabelReturnsNilWhenNoLabelIsSet() throws Exception {
        ServerComputer computer = mock(ServerComputer.class);
        when(computer.getLabel()).thenReturn(null);
        ComputerPeripheral peripheral = new ComputerPeripheral("computer", computer);

        Object[] result = peripheral.callMethod(null, null, 5, new Object[0]);

        assertTrue(result.length == 0 || result[0] == null);
    }
}
