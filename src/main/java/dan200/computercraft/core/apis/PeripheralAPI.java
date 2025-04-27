package dan200.computercraft.core.apis;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ComputerThread;
import dan200.computercraft.core.computer.ITask;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;

public class PeripheralAPI implements ILuaAPI, IAPIEnvironment.IPeripheralChangeListener {

    private IAPIEnvironment m_environment;
    private FileSystem m_fileSystem;
    private PeripheralAPI.PeripheralWrapper[] m_peripherals;
    private boolean m_running;

    public PeripheralAPI(IAPIEnvironment _environment) {
        this.m_environment = _environment;
        this.m_environment.setPeripheralChangeListener(this);
        this.m_peripherals = new PeripheralAPI.PeripheralWrapper[6];

        for (int i = 0; i < 6; i++) {
            this.m_peripherals[i] = null;
        }

        this.m_running = false;
    }

    @Override
    public void onPeripheralChanged(int side, IPeripheral newPeripheral) {
        synchronized (this.m_peripherals) {
            if (this.m_peripherals[side] != null) {
                final PeripheralAPI.PeripheralWrapper wrapper = this.m_peripherals[side];
                ComputerThread.queueTask(new ITask() {

                    @Override
                    public Computer getOwner() {
                        return PeripheralAPI.this.m_environment.getComputer();
                    }

                    @Override
                    public void execute() {
                        synchronized (PeripheralAPI.this.m_peripherals) {
                            if (wrapper.isAttached()) {
                                wrapper.detach();
                            }
                        }
                    }
                }, null);
                this.m_environment.queueEvent("peripheral_detach", new Object[] { Computer.s_sideNames[side] });
            }

            if (newPeripheral != null) {
                this.m_peripherals[side] = new PeripheralAPI.PeripheralWrapper(
                    newPeripheral,
                    Computer.s_sideNames[side]);
            } else {
                this.m_peripherals[side] = null;
            }

            if (this.m_peripherals[side] != null) {
                final PeripheralAPI.PeripheralWrapper wrapper = this.m_peripherals[side];
                ComputerThread.queueTask(new ITask() {

                    @Override
                    public Computer getOwner() {
                        return PeripheralAPI.this.m_environment.getComputer();
                    }

                    @Override
                    public void execute() {
                        synchronized (PeripheralAPI.this.m_peripherals) {
                            if (PeripheralAPI.this.m_running && !wrapper.isAttached()) {
                                wrapper.attach();
                            }
                        }
                    }
                }, null);
                this.m_environment.queueEvent("peripheral", new Object[] { Computer.s_sideNames[side] });
            }
        }
    }

    @Override
    public String[] getNames() {
        return new String[] { "peripheral" };
    }

    @Override
    public void startup() {
        synchronized (this.m_peripherals) {
            this.m_fileSystem = this.m_environment.getFileSystem();
            this.m_running = true;

            for (int i = 0; i < 6; i++) {
                PeripheralAPI.PeripheralWrapper wrapper = this.m_peripherals[i];
                if (wrapper != null && !wrapper.isAttached()) {
                    wrapper.attach();
                }
            }
        }
    }

    @Override
    public void advance(double _dt) {}

    @Override
    public void shutdown() {
        synchronized (this.m_peripherals) {
            this.m_running = false;

            for (int i = 0; i < 6; i++) {
                PeripheralAPI.PeripheralWrapper wrapper = this.m_peripherals[i];
                if (wrapper != null && wrapper.isAttached()) {
                    wrapper.detach();
                }
            }

            this.m_fileSystem = null;
        }
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "isPresent", "getType", "getMethods", "call" };
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        switch (method) {
            case 0:
                boolean present = false;
                int side = this.parseSide(args);
                if (side >= 0) {
                    synchronized (this.m_peripherals) {
                        PeripheralAPI.PeripheralWrapper p = this.m_peripherals[side];
                        if (p != null) {
                            present = true;
                        }
                    }
                }

                return new Object[] { present };
            case 1:
                String type = null;
                int sidex = this.parseSide(args);
                if (sidex >= 0) {
                    synchronized (this.m_peripherals) {
                        PeripheralAPI.PeripheralWrapper p = this.m_peripherals[sidex];
                        if (p != null) {
                            type = p.getType();
                        }
                    }

                    if (type != null) {
                        return new Object[] { type };
                    }
                }

                return null;
            case 2:
                String[] methods = null;
                int side2 = this.parseSide(args);
                if (side2 >= 0) {
                    synchronized (this.m_peripherals) {
                        PeripheralAPI.PeripheralWrapper p = this.m_peripherals[side2];
                        if (p != null) {
                            methods = p.getMethods();
                        }
                    }
                }

                if (methods == null) {
                    return null;
                }

                Map<Object, Object> table = new HashMap<>();

                for (int i = 0; i < methods.length; i++) {
                    table.put(i + 1, methods[i]);
                }

                return new Object[] { table };
            case 3:
                if (args.length >= 2 && args[1] != null && args[1] instanceof String) {
                    String methodName = (String) args[1];
                    Object[] methodArgs = this.trimArray(args, 2);
                    int side3 = this.parseSide(args);
                    if (side3 >= 0) {
                        PeripheralAPI.PeripheralWrapper p = null;
                        synchronized (this.m_peripherals) {
                            p = this.m_peripherals[side3];
                        }

                        if (p != null) {
                            return p.call(context, methodName, methodArgs);
                        }
                    }

                    throw new LuaException("No peripheral attached");
                }

                throw new LuaException("Expected string, string");
            default:
                return null;
        }
    }

    private Object[] trimArray(Object[] array, int skip) {
        return Arrays.copyOfRange(array, skip, array.length);
    }

    private int parseSide(Object[] args) throws LuaException {
        if (args.length >= 1 && args[0] != null && args[0] instanceof String) {
            String side = (String) args[0];

            for (int n = 0; n < Computer.s_sideNames.length; n++) {
                if (side.equals(Computer.s_sideNames[n])) {
                    return n;
                }
            }

            return -1;
        } else {
            throw new LuaException("Expected string");
        }
    }

    private String findFreeLocation(String desiredLoc) {
        try {
            synchronized (this.m_fileSystem) {
                return !this.m_fileSystem.exists(desiredLoc) ? desiredLoc : null;
            }
        } catch (FileSystemException var5) {
            return null;
        }
    }

    private class PeripheralWrapper implements IComputerAccess {

        private final String m_side;
        private final IPeripheral m_peripheral;
        private String m_type;
        private String[] m_methods;
        private Map<String, Integer> m_methodMap;
        private boolean m_attached;
        private Set<String> m_mounts;

        public PeripheralWrapper(IPeripheral peripheral, String side) {
            this.m_side = side;
            this.m_peripheral = peripheral;
            this.m_attached = false;
            this.m_type = peripheral.getType();
            this.m_methods = peripheral.getMethodNames();

            assert this.m_type != null;

            assert this.m_methods != null;

            this.m_methodMap = new HashMap<>();

            for (int i = 0; i < this.m_methods.length; i++) {
                if (this.m_methods[i] != null) {
                    this.m_methodMap.put(this.m_methods[i], i);
                }
            }

            this.m_mounts = new HashSet<>();
        }

        public IPeripheral getPeripheral() {
            return this.m_peripheral;
        }

        public String getType() {
            return this.m_type;
        }

        public String[] getMethods() {
            return this.m_methods;
        }

        public synchronized boolean isAttached() {
            return this.m_attached;
        }

        public synchronized void attach() {
            this.m_attached = true;
            this.m_peripheral.attach(this);
        }

        public synchronized void detach() {
            this.m_peripheral.detach(this);
            this.m_attached = false;
            Iterator<String> it = this.m_mounts.iterator();

            while (it.hasNext()) {
                PeripheralAPI.this.m_fileSystem.unmount(it.next());
            }

            this.m_mounts.clear();
        }

        public Object[] call(ILuaContext context, String methodName, Object[] arguments)
            throws LuaException, InterruptedException {
            int method = -1;
            synchronized (this) {
                if (this.m_methodMap.containsKey(methodName)) {
                    method = this.m_methodMap.get(methodName);
                }
            }

            if (method >= 0) {
                return this.m_peripheral.callMethod(this, context, method, arguments);
            } else {
                throw new LuaException("No such method " + methodName);
            }
        }

        @Override
        public String mount(String desiredLoc, IMount mount) {
            return this.mount(desiredLoc, mount, this.m_side);
        }

        @Override
        public synchronized String mount(String desiredLoc, IMount mount, String driveName) {
            if (!this.m_attached) {
                throw new RuntimeException("You are not attached to this Computer");
            } else {
                String location = null;
                synchronized (PeripheralAPI.this.m_fileSystem) {
                    location = PeripheralAPI.this.findFreeLocation(desiredLoc);
                    if (location != null) {
                        try {
                            PeripheralAPI.this.m_fileSystem.mount(driveName, location, mount);
                        } catch (FileSystemException var8) {}
                    }
                }

                if (location != null) {
                    this.m_mounts.add(location);
                }

                return location;
            }
        }

        @Override
        public String mountWritable(String desiredLoc, IWritableMount mount) {
            return this.mountWritable(desiredLoc, mount, this.m_side);
        }

        @Override
        public synchronized String mountWritable(String desiredLoc, IWritableMount mount, String driveName) {
            if (!this.m_attached) {
                throw new RuntimeException("You are not attached to this Computer");
            } else {
                String location = null;
                synchronized (PeripheralAPI.this.m_fileSystem) {
                    location = PeripheralAPI.this.findFreeLocation(desiredLoc);
                    if (location != null) {
                        try {
                            PeripheralAPI.this.m_fileSystem.mountWritable(driveName, location, mount);
                        } catch (FileSystemException var8) {}
                    }
                }

                if (location != null) {
                    this.m_mounts.add(location);
                }

                return location;
            }
        }

        @Override
        public synchronized void unmount(String location) {
            if (!this.m_attached) {
                throw new RuntimeException("You are not attached to this Computer");
            } else {
                if (location != null) {
                    if (!this.m_mounts.contains(location)) {
                        throw new RuntimeException("You didn't mount this location");
                    }

                    PeripheralAPI.this.m_fileSystem.unmount(location);
                    this.m_mounts.remove(location);
                }
            }
        }

        @Override
        public synchronized int getID() {
            if (!this.m_attached) {
                throw new RuntimeException("You are not attached to this Computer");
            } else {
                return PeripheralAPI.this.m_environment.getComputerID();
            }
        }

        @Override
        public synchronized void queueEvent(String event, Object[] arguments) {
            if (!this.m_attached) {
                throw new RuntimeException("You are not attached to this Computer");
            } else {
                PeripheralAPI.this.m_environment.queueEvent(event, arguments);
            }
        }

        @Override
        public synchronized String getAttachmentName() {
            if (!this.m_attached) {
                throw new RuntimeException("You are not attached to this Computer");
            } else {
                return this.m_side;
            }
        }
    }
}
