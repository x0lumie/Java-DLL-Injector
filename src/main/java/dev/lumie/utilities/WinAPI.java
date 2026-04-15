package dev.lumie.utilities;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * JNA bindings for Windows API calls needed for DLL injection
 * and process enumeration.
 */
public interface WinAPI extends StdCallLibrary {

    WinAPI INSTANCE = Native.load("kernel32", WinAPI.class, W32APIOptions.DEFAULT_OPTIONS);

    // ── Process access flags ──────────────────────────────────────────────────
    int PROCESS_ALL_ACCESS          = 0x1F0FFF;

    // ── VirtualAllocEx flags ──────────────────────────────────────────────────
    int MEM_COMMIT                  = 0x1000;
    int MEM_RELEASE                 = 0x8000;
    int PAGE_READWRITE              = 0x04;

    // ── Toolhelp32 snapshot flags ─────────────────────────────────────────────
    int TH32CS_SNAPPROCESS          = 0x00000002;

    // ── PROCESSENTRY32 structure ──────────────────────────────────────────────
    class PROCESSENTRY32 extends Structure {
        public int  dwSize;
        public int  th32ProcessID;
        public char[] szExeFile = new char[260];  // MAX_PATH

        public PROCESSENTRY32() {
            dwSize = size();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList(
                    "dwSize","cntUsage","th32ProcessID","th32DefaultHeapID",
                    "th32ModuleID","cntThreads","th32ParentProcessID",
                    "pcPriClassBase","dwFlags","szExeFile"
            );
        }
    }

    // ── Kernel32 functions ────────────────────────────────────────────────────

    /** Create a snapshot of running processes. */
    WinNT.HANDLE CreateToolhelp32Snapshot(int dwFlags, int th32ProcessID);

    /** Get the first process from a snapshot. */
    boolean Process32First(WinNT.HANDLE hSnapshot, PROCESSENTRY32 lppe);

    /** Advance to the next process in a snapshot. */
    boolean Process32Next(WinNT.HANDLE hSnapshot, PROCESSENTRY32 lppe);

    /** Open a process by PID. */
    WinNT.HANDLE OpenProcess(int dwDesiredAccess, boolean bInheritHandle, int dwProcessId);

    /** Allocate memory in a remote process. */
    Pointer VirtualAllocEx(WinNT.HANDLE hProcess, Pointer lpAddress,
                           int dwSize, int flAllocationType, int flProtect);

    /** Free memory in a remote process. */
    boolean VirtualFreeEx(WinNT.HANDLE hProcess, Pointer lpAddress,
                          int dwSize, int dwFreeType);

    /** Write bytes into a remote process's memory. */
    boolean WriteProcessMemory(WinNT.HANDLE hProcess, Pointer lpBaseAddress,
                               byte[] lpBuffer, int nSize,
                               IntByReference lpNumberOfBytesWritten);

    /** Get the address of an exported function from a loaded module. */
    Pointer GetProcAddress(WinNT.HANDLE hModule, String lpProcName);

    /** Get a handle to a module already loaded in this process. */
    WinNT.HANDLE GetModuleHandleA(String lpModuleName);

    /**
     * Create a thread inside a remote process.
     * Used to call LoadLibraryA with the DLL path as its argument.
     */
    WinNT.HANDLE CreateRemoteThread(WinNT.HANDLE hProcess,
                                    WinBase.SECURITY_ATTRIBUTES lpThreadAttributes,
                                    int dwStackSize,
                                    Pointer lpStartAddress,
                                    Pointer lpParameter,
                                    int dwCreationFlags,
                                    IntByReference lpThreadId);

    /** Wait for an object (thread) to finish. */
    int WaitForSingleObject(WinNT.HANDLE hHandle, int dwMilliseconds);

    /** Close an open handle. */
    boolean CloseHandle(WinNT.HANDLE hObject);

    /** Return a human-readable error message for GetLastError(). */
    int GetLastError();
}
