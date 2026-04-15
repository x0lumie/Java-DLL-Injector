package dev.lumie.utilities;

/**
 * Holds information about a running Windows process.
 */
public class ProcessInfo {
    private final int pid;
    private final String name;
    private final String fullPath;

    public ProcessInfo(int pid, String name, String fullPath) {
        this.pid    = pid;
        this.name   = name;
        this.fullPath = fullPath;
    }

    public int getPid()         { return pid; }
    public String getName()     { return name; }

    @Override
    public String toString() {
        return String.format("[%d]  %s", pid, name);
    }
}
