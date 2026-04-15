package dev.lumie;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import dev.lumie.utilities.ProcessInfo;
import dev.lumie.utilities.WinAPI;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Core DLL injection engine.
 *
 * Technique: CreateRemoteThread + LoadLibraryA
 *  1. Open target process with full access.
 *  2. Allocate memory in the target process for the DLL path string.
 *  3. Write the DLL path into that allocation.
 *  4. Resolve LoadLibraryA address from kernel32 (same in every process).
 *  5. Spawn a remote thread that calls LoadLibraryA(dllPath).
 *  6. Wait for the thread and clean up handles / memory.
 */
public class InjectionEngine {

    private final WinAPI api = WinAPI.INSTANCE;

    // ── Injection ─────────────────────────────────────────────────────────────

    /**
     * Inject {@code dllFile} into the process identified by {@code pid}.
     *
     * @param pid     Target process ID
     * @param dllFile Absolute path to the DLL
     * @param log     Callback for status messages shown in the GUI
     * @throws InjectionException on any failure
     */
    public void inject(int pid, File dllFile, Consumer<String> log) throws InjectionException {

        if (!dllFile.exists()) {
            throw new InjectionException("DLL file does not exist: " + dllFile.getAbsolutePath());
        }

        String dllPath = dllFile.getAbsolutePath();
        log.accept("Target DLL : " + dllPath);
        log.accept("Target PID : " + pid);

        // 1 ── Open the target process ─────────────────────────────────────────
        log.accept("Opening process...");
        WinNT.HANDLE hProcess = api.OpenProcess(WinAPI.PROCESS_ALL_ACCESS, false, pid);
        if (hProcess == null || Pointer.nativeValue(hProcess.getPointer()) == 0) {
            throw new InjectionException("OpenProcess failed. Error: " + api.GetLastError()
                    + "  (are you running as Administrator?)");
        }

        Pointer  remoteBuffer = null;
        WinNT.HANDLE hThread  = null;

        try {
            // 2 ── Allocate remote memory for the DLL path ─────────────────────
            byte[] pathBytes = (dllPath + "\0").getBytes(StandardCharsets.US_ASCII);
            log.accept("Allocating " + pathBytes.length + " bytes in remote process...");

            remoteBuffer = api.VirtualAllocEx(
                    hProcess, null, pathBytes.length,
                    WinAPI.MEM_COMMIT, WinAPI.PAGE_READWRITE);

            if (remoteBuffer == null) {
                throw new InjectionException("VirtualAllocEx failed. Error: " + api.GetLastError());
            }
            log.accept("Remote buffer allocated at: 0x" + Long.toHexString(Pointer.nativeValue(remoteBuffer)));

            // 3 ── Write DLL path into the remote buffer ───────────────────────
            log.accept("Writing DLL path to remote process memory...");
            IntByReference bytesWritten = new IntByReference(0);
            boolean wrote = api.WriteProcessMemory(hProcess, remoteBuffer,
                    pathBytes, pathBytes.length, bytesWritten);

            if (!wrote || bytesWritten.getValue() != pathBytes.length) {
                throw new InjectionException("WriteProcessMemory failed. Error: " + api.GetLastError()
                        + "  (wrote " + bytesWritten.getValue() + "/" + pathBytes.length + " bytes)");
            }
            log.accept("DLL path written successfully (" + bytesWritten.getValue() + " bytes).");

            // 4 ── Get LoadLibraryA address from kernel32 ──────────────────────
            log.accept("Resolving LoadLibraryA...");
            WinNT.HANDLE hKernel32 = api.GetModuleHandleA("kernel32.dll");
            if (hKernel32 == null) {
                throw new InjectionException("GetModuleHandle(kernel32) failed. Error: " + api.GetLastError());
            }
            Pointer loadLibAddr = api.GetProcAddress(hKernel32, "LoadLibraryA");
            if (loadLibAddr == null) {
                throw new InjectionException("GetProcAddress(LoadLibraryA) failed. Error: " + api.GetLastError());
            }
            log.accept("LoadLibraryA @ 0x" + Long.toHexString(Pointer.nativeValue(loadLibAddr)));

            // 5 ── Create remote thread targeting LoadLibraryA ─────────────────
            log.accept("Creating remote thread...");
            IntByReference threadId = new IntByReference(0);
            hThread = api.CreateRemoteThread(
                    hProcess, null, 0,
                    loadLibAddr, remoteBuffer,
                    0, threadId);

            if (hThread == null || Pointer.nativeValue(hThread.getPointer()) == 0) {
                throw new InjectionException("CreateRemoteThread failed. Error: " + api.GetLastError());
            }
            log.accept("Remote thread created (TID " + threadId.getValue() + "). Waiting for completion...");

            // 6 ── Wait for the thread to finish loading the DLL ───────────────
            int waitResult = api.WaitForSingleObject(hThread, 10_000); // 10-second timeout
            if (waitResult != 0) {
                throw new InjectionException("WaitForSingleObject returned " + waitResult
                        + ". The DLL may not have loaded correctly.");
            }

            log.accept("✓ Injection successful! \"" + dllFile.getName() + "\" loaded into PID " + pid + ".");

        } finally {
            // Always clean up
            if (remoteBuffer != null) {
                api.VirtualFreeEx(hProcess, remoteBuffer, 0, WinAPI.MEM_RELEASE);
                log.accept("Remote buffer freed.");
            }
            if (hThread != null)  api.CloseHandle(hThread);
            api.CloseHandle(hProcess);
            log.accept("Handles closed.");
        }
    }

    // ── Process enumeration ───────────────────────────────────────────────────

    /**
     * Return a snapshot of all currently running processes.
     */
    public List<ProcessInfo> listProcesses() {
        List<ProcessInfo> list = new ArrayList<>();

        WinNT.HANDLE snapshot = api.CreateToolhelp32Snapshot(WinAPI.TH32CS_SNAPPROCESS, 0);
        if (snapshot == null) return list;

        try {
            WinAPI.PROCESSENTRY32 entry = new WinAPI.PROCESSENTRY32();
            if (api.Process32First(snapshot, entry)) {
                do {
                    int    pid  = entry.th32ProcessID;
                    String name = Native.toString(entry.szExeFile);
                    list.add(new ProcessInfo(pid, name, ""));
                    entry = new WinAPI.PROCESSENTRY32();          // reset for next call
                } while (api.Process32Next(snapshot, entry));
            }
        } finally {
            api.CloseHandle(snapshot);
        }

        list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return list;
    }

    // ── Exception ─────────────────────────────────────────────────────────────

    public static class InjectionException extends Exception {
        public InjectionException(String message) { super(message); }
    }
}
