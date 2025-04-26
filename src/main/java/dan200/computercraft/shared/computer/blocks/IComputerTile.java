package dan200.computercraft.shared.computer.blocks;

import dan200.computercraft.shared.common.ITerminalTile;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.IComputer;

public interface IComputerTile extends ITerminalTile {
   void setComputerID(int var1);

   void setLabel(String var1);

   IComputer getComputer();

   IComputer createComputer();

   ComputerFamily getFamily();
}
