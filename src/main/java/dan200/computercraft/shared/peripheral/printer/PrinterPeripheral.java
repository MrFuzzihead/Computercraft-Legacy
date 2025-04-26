package dan200.computercraft.shared.peripheral.printer;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.terminal.Terminal;

public class PrinterPeripheral implements IPeripheral {
   private final TilePrinter m_printer;

   public PrinterPeripheral(TilePrinter printer) {
      this.m_printer = printer;
   }

   @Override
   public String getType() {
      return "printer";
   }

   @Override
   public String[] getMethodNames() {
      return new String[]{"write", "setCursorPos", "getCursorPos", "getPageSize", "newPage", "endPage", "getInkLevel", "setPageTitle", "getPaperLevel"};
   }

   @Override
   public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args) throws LuaException {
      switch (method) {
         case 0: {
            String text;
            if (args.length > 0 && args[0] != null) {
               text = args[0].toString();
            } else {
               text = "";
            }

            Terminal page = this.getCurrentPage();
            page.write(text);
            page.setCursorPos(page.getCursorX() + text.length(), page.getCursorY());
            return null;
         }
         case 1:
            if (args.length == 2 && args[0] != null && args[0] instanceof Number && args[1] != null && args[1] instanceof Number) {
               int xx = ((Number)args[0]).intValue() - 1;
               int yx = ((Number)args[1]).intValue() - 1;
               Terminal pagex = this.getCurrentPage();
               pagex.setCursorPos(xx, yx);
               return null;
            }

            throw new LuaException("Expected number, number");
         case 2: {
            Terminal page = this.getCurrentPage();
            int x = page.getCursorX();
            int y = page.getCursorY();
            return new Object[]{x + 1, y + 1};
         }
         case 3: {
            Terminal page = this.getCurrentPage();
            int width = page.getWidth();
            int height = page.getHeight();
            return new Object[]{width, height};
         }
         case 4:
            return new Object[]{this.m_printer.startNewPage()};
         case 5:
            this.getCurrentPage();
            return new Object[]{this.m_printer.endCurrentPage()};
         case 6:
            return new Object[]{this.m_printer.getInkLevel()};
         case 7:
            String title = "";
            if (args.length > 0 && args[0] != null) {
               if (!(args[0] instanceof String)) {
                  throw new LuaException("Expected string");
               }

               title = (String)args[0];
            }

            this.getCurrentPage();
            this.m_printer.setPageTitle(title);
            return null;
         case 8:
            return new Object[]{this.m_printer.getPaperLevel()};
         default:
            return null;
      }
   }

   @Override
   public void attach(IComputerAccess computer) {
   }

   @Override
   public void detach(IComputerAccess computer) {
   }

   @Override
   public boolean equals(IPeripheral other) {
      if (other instanceof PrinterPeripheral) {
         PrinterPeripheral otherPrinter = (PrinterPeripheral)other;
         if (otherPrinter.m_printer == this.m_printer) {
            return true;
         }
      }

      return false;
   }

   private Terminal getCurrentPage() throws LuaException {
      Terminal currentPage = this.m_printer.getCurrentPage();
      if (currentPage == null) {
         throw new LuaException("Page not started");
      } else {
         return currentPage;
      }
   }
}
