package dan200.computercraft.core.apis;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;

public class OSAPI implements ILuaAPI {

    private IAPIEnvironment m_apiEnvironment;
    private final Map<Integer, OSAPI.Timer> m_timers;
    private final Map<Integer, OSAPI.Alarm> m_alarms;
    private int m_clock;
    private double m_time;
    private int m_day;
    private int m_nextTimerToken;
    private int m_nextAlarmToken;

    public OSAPI(IAPIEnvironment environment) {
        this.m_apiEnvironment = environment;
        this.m_nextTimerToken = 0;
        this.m_nextAlarmToken = 0;
        this.m_timers = new HashMap<>();
        this.m_alarms = new HashMap<>();
    }

    @Override
    public String[] getNames() {
        return new String[] { "os" };
    }

    @Override
    public void startup() {
        this.m_time = this.m_apiEnvironment.getComputerEnvironment()
            .getTimeOfDay();
        this.m_day = this.m_apiEnvironment.getComputerEnvironment()
            .getDay();
        this.m_clock = 0;
        synchronized (this.m_timers) {
            this.m_timers.clear();
        }

        synchronized (this.m_alarms) {
            this.m_alarms.clear();
        }
    }

    @Override
    public void advance(double dt) {
        synchronized (this.m_timers) {
            this.m_clock++;
            Iterator<Entry<Integer, OSAPI.Timer>> it = this.m_timers.entrySet()
                .iterator();

            while (it.hasNext()) {
                Entry<Integer, OSAPI.Timer> entry = it.next();
                OSAPI.Timer timer = entry.getValue();
                timer.m_ticksLeft--;
                if (timer.m_ticksLeft <= 0) {
                    this.queueLuaEvent("timer", new Object[] { entry.getKey() });
                    it.remove();
                }
            }
        }

        synchronized (this.m_alarms) {
            double previousTime = this.m_time;
            int previousDay = this.m_day;
            double time = this.m_apiEnvironment.getComputerEnvironment()
                .getTimeOfDay();
            int day = this.m_apiEnvironment.getComputerEnvironment()
                .getDay();
            if (time > previousTime || day > previousDay) {
                double now = this.m_day * 24.0 + this.m_time;
                Iterator<Entry<Integer, OSAPI.Alarm>> it = this.m_alarms.entrySet()
                    .iterator();

                while (it.hasNext()) {
                    Entry<Integer, OSAPI.Alarm> entry = it.next();
                    OSAPI.Alarm alarm = entry.getValue();
                    double t = alarm.m_day * 24.0 + alarm.m_time;
                    if (now >= t) {
                        this.queueLuaEvent("alarm", new Object[] { entry.getKey() });
                        it.remove();
                    }
                }
            }

            this.m_time = time;
            this.m_day = day;
        }
    }

    @Override
    public void shutdown() {
        synchronized (this.m_timers) {
            this.m_timers.clear();
        }

        synchronized (this.m_alarms) {
            this.m_alarms.clear();
        }
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "queueEvent", "startTimer", "setAlarm", "shutdown", "reboot", "computerID",
            "getComputerID", "setComputerLabel", "computerLabel", "getComputerLabel", "clock", "time", "day",
            "cancelTimer", "cancelAlarm" };
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
        switch (method) {
            case 0:
                if (args.length != 0 && args[0] != null && args[0] instanceof String) {
                    this.queueLuaEvent((String) args[0], this.trimArray(args, 1));
                    return null;
                }

                throw new LuaException("Expected string");
            case 1:
                if (args.length >= 1 && args[0] != null && args[0] instanceof Number) {
                    double timer = ((Number) args[0]).doubleValue();
                    synchronized (this.m_timers) {
                        this.m_timers.put(this.m_nextTimerToken, new OSAPI.Timer((int) Math.round(timer / 0.05)));
                        return new Object[] { this.m_nextTimerToken++ };
                    }
                }

                throw new LuaException("Expected number");
            case 2:
                if (args.length >= 1 && args[0] != null && args[0] instanceof Number) {
                    double time = ((Number) args[0]).doubleValue();
                    if (!(time < 0.0) && !(time >= 24.0)) {
                        synchronized (this.m_alarms) {
                            int day = time > this.m_time ? this.m_day : this.m_day + 1;
                            this.m_alarms.put(this.m_nextAlarmToken, new OSAPI.Alarm(time, day));
                            return new Object[] { this.m_nextAlarmToken++ };
                        }
                    }

                    throw new LuaException("Number out of range");
                }

                throw new LuaException("Expected number");
            case 3:
                this.m_apiEnvironment.shutdown();
                return null;
            case 4:
                this.m_apiEnvironment.reboot();
                return null;
            case 5:
            case 6:
                return new Object[] { this.getComputerID() };
            case 7:
                String label = null;
                if (args.length > 0 && args[0] != null) {
                    if (!(args[0] instanceof String)) {
                        throw new LuaException("Expected string or nil");
                    }

                    label = (String) args[0];
                    if (label.length() > 32) {
                        label = label.substring(0, 32);
                    }
                }

                this.m_apiEnvironment.setLabel(label);
                return null;
            case 8:
            case 9:
                String label9 = this.m_apiEnvironment.getLabel();
                if (label9 != null) {
                    return new Object[] { label9 };
                }

                return null;
            case 10:
                synchronized (this.m_timers) {
                    return new Object[] { this.m_clock * 0.05 };
                }
            case 11:
                synchronized (this.m_alarms) {
                    return new Object[] { this.m_time };
                }
            case 12:
                synchronized (this.m_alarms) {
                    return new Object[] { this.m_day };
                }
            case 13:
                if (args.length >= 1 && args[0] != null && args[0] instanceof Number) {
                    int token = ((Number) args[0]).intValue();
                    synchronized (this.m_timers) {
                        if (this.m_timers.containsKey(token)) {
                            this.m_timers.remove(token);
                        }

                        return null;
                    }
                }

                throw new LuaException("Expected number");
            case 14:
                if (args.length >= 1 && args[0] != null && args[0] instanceof Number) {
                    int token = ((Number) args[0]).intValue();
                    synchronized (this.m_alarms) {
                        if (this.m_alarms.containsKey(token)) {
                            this.m_alarms.remove(token);
                        }

                        return null;
                    }
                }

                throw new LuaException("Expected number");
            default:
                return null;
        }
    }

    private void queueLuaEvent(String event, Object[] args) {
        this.m_apiEnvironment.queueEvent(event, args);
    }

    private Object[] trimArray(Object[] array, int skip) {
        return Arrays.copyOfRange(array, skip, array.length);
    }

    private int getComputerID() {
        return this.m_apiEnvironment.getComputerID();
    }

    private class Alarm implements Comparable<OSAPI.Alarm> {

        public final double m_time;
        public final int m_day;

        public Alarm(double time, int day) {
            this.m_time = time;
            this.m_day = day;
        }

        public int compareTo(OSAPI.Alarm o) {
            double t = this.m_day * 24.0 + this.m_time;
            double ot = this.m_day * 24.0 + this.m_time;
            if (t < ot) {
                return -1;
            } else {
                return t > ot ? 1 : 0;
            }
        }
    }

    private static class Timer {

        public int m_ticksLeft;

        public Timer(int ticksLeft) {
            this.m_ticksLeft = ticksLeft;
        }
    }
}
