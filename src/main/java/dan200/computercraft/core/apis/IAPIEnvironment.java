package dan200.computercraft.core.apis;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.IComputerEnvironment;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.terminal.Terminal;

public interface IAPIEnvironment {
   Computer getComputer();

   int getComputerID();

   IComputerEnvironment getComputerEnvironment();

   Terminal getTerminal();

   FileSystem getFileSystem();

   void shutdown();

   void reboot();

   void queueEvent(String var1, Object[] var2);

   void setOutput(int var1, int var2);

   int getOutput(int var1);

   int getInput(int var1);

   void setBundledOutput(int var1, int var2);

   int getBundledOutput(int var1);

   int getBundledInput(int var1);

   void setPeripheralChangeListener(IAPIEnvironment.IPeripheralChangeListener var1);

   IPeripheral getPeripheral(int var1);

   String getLabel();

   void setLabel(String var1);

   interface IPeripheralChangeListener {
      void onPeripheralChanged(int var1, IPeripheral var2);
   }
}
