package dan200.computercraft.core.computer;

import com.google.common.base.Objects;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.apis.BitAPI;
import dan200.computercraft.core.apis.FSAPI;
import dan200.computercraft.core.apis.HTTPAPI;
import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.apis.OSAPI;
import dan200.computercraft.core.apis.PeripheralAPI;
import dan200.computercraft.core.apis.RedstoneAPI;
import dan200.computercraft.core.apis.TermAPI;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.core.lua.ILuaMachine;
import dan200.computercraft.core.lua.LuaJLuaMachine;
import dan200.computercraft.core.terminal.Terminal;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class Computer {
   public static final String[] s_sideNames = new String[]{"bottom", "top", "back", "front", "right", "left"};
   private static IMount s_romMount = null;
   private int m_id;
   private String m_label;
   private final IComputerEnvironment m_environment;
   private int m_ticksSinceStart;
   private boolean m_startRequested;
   private Computer.State m_state;
   private boolean m_blinking;
   private ILuaMachine m_machine;
   private List<ILuaAPI> m_apis;
   private Computer.APIEnvironment m_apiEnvironment;
   private Terminal m_terminal;
   private FileSystem m_fileSystem;
   private IWritableMount m_rootMount;
   private int[] m_output;
   private int[] m_bundledOutput;
   private boolean m_outputChanged;
   private int[] m_input;
   private int[] m_bundledInput;
   private boolean m_inputChanged;
   private IPeripheral[] m_peripherals;

   public Computer(IComputerEnvironment environment, Terminal terminal, int id) {
      ComputerThread.start();
      this.m_id = id;
      this.m_label = null;
      this.m_environment = environment;
      this.m_ticksSinceStart = -1;
      this.m_startRequested = false;
      this.m_state = Computer.State.Off;
      this.m_blinking = false;
      this.m_terminal = terminal;
      this.m_fileSystem = null;
      this.m_machine = null;
      this.m_apis = new ArrayList<>();
      this.m_apiEnvironment = new Computer.APIEnvironment(this);
      this.m_output = new int[6];
      this.m_bundledOutput = new int[6];
      this.m_outputChanged = true;
      this.m_input = new int[6];
      this.m_bundledInput = new int[6];
      this.m_inputChanged = false;
      this.m_peripherals = new IPeripheral[6];

      for (int i = 0; i < 6; i++) {
         this.m_peripherals[i] = null;
      }

      this.m_rootMount = null;
      this.createAPIs();
   }

   public IAPIEnvironment getAPIEnvironment() {
      return this.m_apiEnvironment;
   }

   public void turnOn() {
      if (this.m_state == Computer.State.Off) {
         this.m_startRequested = true;
      }
   }

   public void shutdown() {
      this.stopComputer(false);
   }

   public void reboot() {
      this.stopComputer(true);
   }

   public boolean isOn() {
      synchronized (this) {
         return this.m_state == Computer.State.Running;
      }
   }

   public void abort(boolean hard) {
      synchronized (this) {
         if (this.m_state == Computer.State.Running) {
            if (hard) {
               this.m_machine.hardAbort("Too long without yielding");
            } else {
               this.m_machine.softAbort("Too long without yielding");
            }
         }
      }
   }

   public void unload() {
      synchronized (this) {
         this.stopComputer(false);
      }
   }

   public int getID() {
      return this.m_id;
   }

   public int assignID() {
      if (this.m_id < 0) {
         this.m_id = this.m_environment.assignNewID();
      }

      return this.m_id;
   }

   public void setID(int id) {
      this.m_id = id;
   }

   public String getLabel() {
      return this.m_label;
   }

   public void setLabel(String label) {
      if (!Objects.equal(label, this.m_label)) {
         this.m_label = label;
         this.m_outputChanged = true;
      }
   }

   public void advance(double _dt) {
      synchronized (this) {
         if (this.m_ticksSinceStart >= 0) {
            this.m_ticksSinceStart++;
         }

         if (this.m_startRequested && (this.m_ticksSinceStart < 0 || this.m_ticksSinceStart > 50)) {
            this.startComputer();
            this.m_startRequested = false;
         }

         if (this.m_state == Computer.State.Running) {
            synchronized (this.m_input) {
               if (this.m_inputChanged) {
                  this.queueEvent("redstone", null);
                  this.m_inputChanged = false;
               }
            }

            synchronized (this.m_apis) {
               for (ILuaAPI api : this.m_apis) {
                  api.advance(_dt);
               }
            }
         }
      }

      synchronized (this.m_terminal) {
         boolean blinking = this.m_terminal.getCursorBlink()
            && this.m_terminal.getCursorX() >= 0
            && this.m_terminal.getCursorX() < this.m_terminal.getWidth()
            && this.m_terminal.getCursorY() >= 0
            && this.m_terminal.getCursorY() < this.m_terminal.getHeight();
         if (blinking != this.m_blinking) {
            synchronized (this.m_output) {
               this.m_outputChanged = true;
               this.m_blinking = blinking;
            }
         }
      }
   }

   public boolean pollChanged() {
      return this.m_outputChanged;
   }

   public void clearChanged() {
      this.m_outputChanged = false;
   }

   public boolean isBlinking() {
      synchronized (this.m_terminal) {
         return this.isOn() && this.m_blinking;
      }
   }

   public IWritableMount getRootMount() {
      if (this.m_rootMount == null) {
         this.m_rootMount = this.m_environment.createSaveDirMount("computer/" + this.assignID(), this.m_environment.getComputerSpaceLimit());
      }

      return this.m_rootMount;
   }

   private boolean initFileSystem() {
      int id = this.assignID();

      try {
         this.m_fileSystem = new FileSystem("hdd", this.getRootMount());
         if (s_romMount == null) {
            s_romMount = this.m_environment.createResourceMount("computercraft", "lua/rom");
         }

         if (s_romMount != null) {
            this.m_fileSystem.mount("rom", "rom", s_romMount);
            return true;
         } else {
            return false;
         }
      } catch (FileSystemException var3) {
         var3.printStackTrace();
         return false;
      }
   }

   public int getRedstoneOutput(int side) {
      synchronized (this.m_output) {
         return this.isOn() ? this.m_output[side] : 0;
      }
   }

   private void setRedstoneOutput(int side, int level) {
      synchronized (this.m_output) {
         if (this.m_output[side] != level) {
            this.m_output[side] = level;
            this.m_outputChanged = true;
         }
      }
   }

   public void setRedstoneInput(int side, int level) {
      synchronized (this.m_input) {
         if (this.m_input[side] != level) {
            this.m_input[side] = level;
            this.m_inputChanged = true;
         }
      }
   }

   private int getRedstoneInput(int side) {
      synchronized (this.m_input) {
         return this.m_input[side];
      }
   }

   public int getBundledRedstoneOutput(int side) {
      synchronized (this.m_output) {
         return this.isOn() ? this.m_bundledOutput[side] : 0;
      }
   }

   private void setBundledRedstoneOutput(int side, int combination) {
      synchronized (this.m_output) {
         if (this.m_bundledOutput[side] != combination) {
            this.m_bundledOutput[side] = combination;
            this.m_outputChanged = true;
         }
      }
   }

   public void setBundledRedstoneInput(int side, int combination) {
      synchronized (this.m_input) {
         if (this.m_bundledInput[side] != combination) {
            this.m_bundledInput[side] = combination;
            this.m_inputChanged = true;
         }
      }
   }

   private int getBundledRedstoneInput(int side) {
      synchronized (this.m_input) {
         return this.m_bundledInput[side];
      }
   }

   public void addAPI(ILuaAPI api) {
      this.m_apis.add(api);
   }

   public void setPeripheral(int side, IPeripheral peripheral) {
      synchronized (this.m_peripherals) {
         IPeripheral existing = this.m_peripherals[side];
         if (existing == null && peripheral != null || existing != null && peripheral == null || existing != null && !existing.equals(peripheral)) {
            this.m_peripherals[side] = peripheral;
            this.m_apiEnvironment.onPeripheralChanged(side, peripheral);
         }
      }
   }

   public IPeripheral getPeripheral(int side) {
      synchronized (this.m_peripherals) {
         return this.m_peripherals[side];
      }
   }

   private void createAPIs() {
      this.m_apis.add(new TermAPI(this.m_apiEnvironment));
      this.m_apis.add(new RedstoneAPI(this.m_apiEnvironment));
      this.m_apis.add(new FSAPI(this.m_apiEnvironment));
      this.m_apis.add(new PeripheralAPI(this.m_apiEnvironment));
      this.m_apis.add(new OSAPI(this.m_apiEnvironment));
      this.m_apis.add(new BitAPI(this.m_apiEnvironment));
      if (ComputerCraft.http_enable) {
         this.m_apis.add(new HTTPAPI(this.m_apiEnvironment));
      }
   }

   private void initLua() {
      ILuaMachine machine = new LuaJLuaMachine(this);

      for (ILuaAPI api : this.m_apis) {
         machine.addAPI(api);
         api.startup();
      }

      InputStream biosStream;
      try {
         biosStream = Computer.class.getResourceAsStream("/assets/computercraft/lua/bios.lua");
      } catch (Exception var6) {
         biosStream = null;
      }

      if (biosStream != null) {
         machine.loadBios(biosStream);

         try {
            biosStream.close();
         } catch (IOException var5) {
         }

         if (machine.isFinished()) {
            this.m_terminal.reset();
            this.m_terminal.write("Error starting bios.lua");
            this.m_terminal.setCursorPos(0, 1);
            this.m_terminal.write("ComputerCraft may be installed incorrectly");
            machine.unload();
            this.m_machine = null;
         } else {
            this.m_machine = machine;
         }
      } else {
         this.m_terminal.reset();
         this.m_terminal.write("Error loading bios.lua");
         this.m_terminal.setCursorPos(0, 1);
         this.m_terminal.write("ComputerCraft may be installed incorrectly");
         machine.unload();
         this.m_machine = null;
      }
   }

   private void startComputer() {
      synchronized (this) {
         if (this.m_state != Computer.State.Off) {
            return;
         }

         this.m_state = Computer.State.Starting;
         this.m_ticksSinceStart = 0;
      }

      final Computer computer = this;
      ComputerThread.queueTask(new ITask() {
         @Override
         public Computer getOwner() {
            return computer;
         }

         @Override
         public void execute() {
            synchronized (this) {
               if (Computer.this.m_state == Computer.State.Starting) {
                  synchronized (Computer.this.m_terminal) {
                     Computer.this.m_terminal.reset();
                  }

                  if (!Computer.this.initFileSystem()) {
                     Computer.this.m_terminal.reset();
                     Computer.this.m_terminal.write("Error mounting lua/rom");
                     Computer.this.m_terminal.setCursorPos(0, 1);
                     Computer.this.m_terminal.write("ComputerCraft may be installed incorrectly");
                     Computer.this.m_state = Computer.State.Running;
                     Computer.this.stopComputer(false);
                  } else {
                     Computer.this.initLua();
                     if (Computer.this.m_machine == null) {
                        Computer.this.m_terminal.reset();
                        Computer.this.m_terminal.write("Error loading bios.lua");
                        Computer.this.m_terminal.setCursorPos(0, 1);
                        Computer.this.m_terminal.write("ComputerCraft may be installed incorrectly");
                        Computer.this.m_state = Computer.State.Running;
                        Computer.this.stopComputer(false);
                     } else {
                        Computer.this.m_state = Computer.State.Running;
                        synchronized (Computer.this.m_machine) {
                           Computer.this.m_machine.handleEvent(null, null);
                        }
                     }
                  }
               }
            }
         }
      }, computer);
   }

   private void stopComputer(final boolean reboot) {
      synchronized (this) {
         if (this.m_state != Computer.State.Running) {
            return;
         }

         this.m_state = Computer.State.Stopping;
      }

      final Computer computer = this;
      ComputerThread.queueTask(new ITask() {
         @Override
         public Computer getOwner() {
            return computer;
         }

         @Override
         public void execute() {
            synchronized (this) {
               if (Computer.this.m_state == Computer.State.Stopping) {
                  synchronized (Computer.this.m_apis) {
                     for (ILuaAPI api : Computer.this.m_apis) {
                        api.shutdown();
                     }
                  }

                  if (Computer.this.m_fileSystem != null) {
                     Computer.this.m_fileSystem.unload();
                     Computer.this.m_fileSystem = null;
                  }

                  if (Computer.this.m_machine != null) {
                     synchronized (Computer.this.m_terminal) {
                        Computer.this.m_terminal.reset();
                     }

                     synchronized (Computer.this.m_machine) {
                        Computer.this.m_machine.unload();
                        Computer.this.m_machine = null;
                     }
                  }

                  synchronized (Computer.this.m_output) {
                     for (int i = 0; i < 6; i++) {
                        Computer.this.m_output[i] = 0;
                        Computer.this.m_bundledOutput[i] = 0;
                     }

                     Computer.this.m_outputChanged = true;
                  }

                  Computer.this.m_state = Computer.State.Off;
                  if (reboot) {
                     Computer.this.m_startRequested = true;
                  }
               }
            }
         }
      }, computer);
   }

   public void queueEvent(final String event, final Object[] arguments) {
      synchronized (this) {
         if (this.m_state != Computer.State.Running) {
            return;
         }
      }

      final Computer computer = this;
      ITask task = new ITask() {
         @Override
         public Computer getOwner() {
            return computer;
         }

         @Override
         public void execute() {
            synchronized (this) {
               if (Computer.this.m_state != Computer.State.Running) {
                  return;
               }
            }

            synchronized (Computer.this.m_machine) {
               Computer.this.m_machine.handleEvent(event, arguments);
               if (Computer.this.m_machine.isFinished()) {
                  Computer.this.m_terminal.reset();
                  Computer.this.m_terminal.write("Error resuming bios.lua");
                  Computer.this.m_terminal.setCursorPos(0, 1);
                  Computer.this.m_terminal.write("ComputerCraft may be installed incorrectly");
                  Computer.this.stopComputer(false);
               }
            }
         }
      };
      ComputerThread.queueTask(task, computer);
   }

   private static class APIEnvironment implements IAPIEnvironment {
      private Computer m_computer;
      private IAPIEnvironment.IPeripheralChangeListener m_peripheralListener;

      public APIEnvironment(Computer computer) {
         this.m_computer = computer;
         this.m_peripheralListener = null;
      }

      @Override
      public Computer getComputer() {
         return this.m_computer;
      }

      @Override
      public int getComputerID() {
         return this.m_computer.assignID();
      }

      @Override
      public IComputerEnvironment getComputerEnvironment() {
         return this.m_computer.m_environment;
      }

      @Override
      public Terminal getTerminal() {
         return this.m_computer.m_terminal;
      }

      @Override
      public FileSystem getFileSystem() {
         return this.m_computer.m_fileSystem;
      }

      @Override
      public void shutdown() {
         this.m_computer.shutdown();
      }

      @Override
      public void reboot() {
         this.m_computer.reboot();
      }

      @Override
      public void queueEvent(String event, Object[] args) {
         this.m_computer.queueEvent(event, args);
      }

      @Override
      public void setOutput(int side, int output) {
         this.m_computer.setRedstoneOutput(side, output);
      }

      @Override
      public int getOutput(int side) {
         return this.m_computer.getRedstoneOutput(side);
      }

      @Override
      public int getInput(int side) {
         return this.m_computer.getRedstoneInput(side);
      }

      @Override
      public void setBundledOutput(int side, int output) {
         this.m_computer.setBundledRedstoneOutput(side, output);
      }

      @Override
      public int getBundledOutput(int side) {
         return this.m_computer.getBundledRedstoneOutput(side);
      }

      @Override
      public int getBundledInput(int side) {
         return this.m_computer.getBundledRedstoneInput(side);
      }

      @Override
      public IPeripheral getPeripheral(int side) {
         synchronized (this.m_computer.m_peripherals) {
            return this.m_computer.m_peripherals[side];
         }
      }

      @Override
      public void setPeripheralChangeListener(IAPIEnvironment.IPeripheralChangeListener listener) {
         synchronized (this.m_computer.m_peripherals) {
            this.m_peripheralListener = listener;
         }
      }

      @Override
      public String getLabel() {
         return this.m_computer.getLabel();
      }

      @Override
      public void setLabel(String label) {
         this.m_computer.setLabel(label);
      }

      public void onPeripheralChanged(int side, IPeripheral peripheral) {
         synchronized (this.m_computer.m_peripherals) {
            if (this.m_peripheralListener != null) {
               this.m_peripheralListener.onPeripheralChanged(side, peripheral);
            }
         }
      }
   }

   private static enum State {
      Off,
      Starting,
      Running,
      Stopping;
   }
}
