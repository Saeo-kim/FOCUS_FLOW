// FOCUS FLOW desktop launcher (FocusFlow.exe).
//
// One click: makes sure the Spring Boot server runs (starting it if needed), waits until it answers,
// then opens FOCUS FLOW in its own browser app window (no address bar). When the last app window
// closes, the server this launcher started is shut down gracefully (Ctrl+C -> Spring shutdown hooks).
// A server started elsewhere (IntelliJ, start-focusflow.bat) is used as-is and never stopped.
//
// Written for the C# 5 compiler that ships with Windows (.NET Framework 4.x). Build with
// desktop\build-launcher.ps1. Command line:
//   FocusFlow.exe                         launch (default)
//   FocusFlow.exe --stop                  stop the server this launcher started
//   FocusFlow.exe --status                show whether the server is running
//   FocusFlow.exe --create-shortcut PATH  create a .lnk with the FOCUS FLOW icon and taskbar identity
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Management;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace FocusFlow
{
    static class Launcher
    {
        const string AppId = "FocusFlow.Desktop";
        const string AppName = "FOCUS FLOW";
        const int WebPort = 8080;
        const string StartMutexName = @"Local\FocusFlow.Launcher.Start";
        const string OwnerMutexName = @"Local\FocusFlow.Launcher.Owner";

        static readonly string AppUrl = "http://localhost:" + WebPort + "/";
        static readonly string ProbeUrl = "http://127.0.0.1:" + WebPort + "/login.html";

        static string exePath, projectDir, dataDir, logDir, browserProfile, runtimeJar, pidFile, iconPath;

        [STAThread]
        static int Main(string[] args)
        {
            SetCurrentProcessExplicitAppUserModelID(AppId);
            try { SetProcessDPIAware(); } catch { }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            exePath = Process.GetCurrentProcess().MainModule.FileName;
            projectDir = FindProjectDir(Path.GetDirectoryName(exePath));
            dataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "FocusFlow");
            logDir = Path.Combine(dataDir, "logs");
            browserProfile = Path.Combine(dataDir, "browser");
            runtimeJar = Path.Combine(dataDir, "app", "focus-flow.jar");
            pidFile = Path.Combine(dataDir, "server.pid");
            Directory.CreateDirectory(logDir);
            iconPath = projectDir == null ? exePath : Path.Combine(projectDir, @"desktop\icon\focus-flow.ico");
            if (!File.Exists(iconPath)) iconPath = exePath;

            try
            {
                if (args.Length >= 2 && args[0] == "--create-shortcut") { CreateShortcut(args[1]); return 0; }
                if (args.Length >= 1 && args[0] == "--stop") return StopCommand();
                if (args.Length >= 1 && args[0] == "--status") return StatusCommand();
                return Launch();
            }
            catch (Exception ex)
            {
                Log("FATAL " + ex);
                ShowError("FOCUS FLOW could not start.\n\n" + ex.Message);
                return 1;
            }
        }

        // ------------------------------------------------------------------ launch

        static int Launch()
        {
            if (projectDir == null) { ShowError("The FOCUS FLOW project folder was not found next to " + exePath + "."); return 1; }
            Log("Launch requested");

            using (var startMutex = new Mutex(false, StartMutexName))
            {
                // Clicks that arrive while another launcher is starting the server wait here, then reuse it.
                bool haveStart;
                try { haveStart = startMutex.WaitOne(TimeSpan.FromMinutes(12)); }
                catch (AbandonedMutexException) { haveStart = true; }
                if (!haveStart) { ShowError("Another FOCUS FLOW launch is still in progress."); return 1; }

                Mutex owner = null;
                bool startedHere = false;
                try
                {
                    ServerState state = Probe();
                    if (state == ServerState.Foreign)
                    {
                        ShowError("Port " + WebPort + " is used by another program, so FOCUS FLOW cannot start.\n\nClose that program and try again.");
                        return 1;
                    }

                    if (state == ServerState.Running)
                    {
                        Log("Server already running; reusing it");
                        // Adopt a server an earlier launcher started if that launcher is gone (e.g. it was killed).
                        if (ReadOwnedServer() != null) owner = TryTakeOwner();
                    }
                    else
                    {
                        Process server = StartServerWithSplash();
                        if (server == null) return 1;
                        startedHere = true;
                        owner = TryTakeOwner();
                    }

                    Process browser = OpenAppWindow();
                    startMutex.ReleaseMutex();
                    haveStart = false;

                    if (owner == null || browser == null)
                    {
                        Log(owner == null ? "Not the owner; leaving the server running" : "No dedicated browser; leaving the server running");
                        return 0;
                    }
                    MonitorUntilClosed();
                    return 0;
                }
                finally
                {
                    if (haveStart) { try { startMutex.ReleaseMutex(); } catch { } }
                    if (owner != null) { try { owner.ReleaseMutex(); } catch { } owner.Dispose(); }
                    if (startedHere) Log("Launcher finished");
                }
            }
        }

        static Mutex TryTakeOwner()
        {
            var m = new Mutex(false, OwnerMutexName);
            try { if (m.WaitOne(0)) return m; }
            catch (AbandonedMutexException) { return m; }
            m.Dispose();
            return null;
        }

        // ------------------------------------------------------------------ server

        enum ServerState { Stopped, Running, Foreign }

        static ServerState Probe()
        {
            if (!PortOpen("127.0.0.1", WebPort, 500)) return ServerState.Stopped;
            try
            {
                var req = (HttpWebRequest)WebRequest.Create(ProbeUrl);
                req.Proxy = null;
                req.Timeout = 3000;
                req.ReadWriteTimeout = 3000;
                using (var resp = (HttpWebResponse)req.GetResponse())
                using (var reader = new StreamReader(resp.GetResponseStream(), Encoding.UTF8))
                {
                    return reader.ReadToEnd().Contains("FOCUS FLOW") ? ServerState.Running : ServerState.Foreign;
                }
            }
            catch (WebException ex)
            {
                // Port is open but not answering HTTP yet: Tomcat is still starting.
                if (ex.Response == null) return ServerState.Stopped;
                return ServerState.Foreign;
            }
        }

        static bool PortOpen(string host, int port, int timeoutMs)
        {
            using (var client = new TcpClient())
            {
                try
                {
                    var ar = client.BeginConnect(host, port, null, null);
                    if (!ar.AsyncWaitHandle.WaitOne(timeoutMs)) return false;
                    client.EndConnect(ar);
                    return true;
                }
                catch { return false; }
            }
        }

        static Process StartServerWithSplash()
        {
            Process server = null;
            string error = null;
            var splash = new SplashForm(iconPath);
            var worker = new Thread(() =>
            {
                try { server = StartServer(splash); }
                catch (Exception ex) { error = ex.Message; Log("Start failed: " + ex); }
                finally { splash.CloseSafe(); }
            });
            worker.IsBackground = true;
            splash.Shown += (s, e) => worker.Start();
            Application.Run(splash);
            worker.Join();
            if (error != null)
            {
                ShowError(error + "\n\nLogs: " + logDir, true);
                return null;
            }
            return server;
        }

        static Process StartServer(SplashForm splash)
        {
            // 1. MySQL must be reachable. After a reboot the service may need a few more seconds.
            int dbPort = ReadDbPort();
            splash.SetStatus("Checking the database (MySQL :" + dbPort + ")...");
            var dbWait = Stopwatch.StartNew();
            while (!PortOpen("127.0.0.1", dbPort, 1000))
            {
                if (dbWait.Elapsed > TimeSpan.FromSeconds(60))
                    throw new Exception("MySQL is not answering on port " + dbPort + ".\nStart the MySQL service and try again.");
                Thread.Sleep(1000);
            }

            // 2. Make sure the application jar is up to date with the source code.
            splash.SetStatus("Preparing the app...");
            string builtJar = EnsureBuiltJar(splash);
            Directory.CreateDirectory(Path.GetDirectoryName(runtimeJar));
            // The server runs from a private copy so Gradle/IntelliJ builds never hit a locked jar.
            if (!File.Exists(runtimeJar) || File.GetLastWriteTimeUtc(runtimeJar) != File.GetLastWriteTimeUtc(builtJar)
                || new FileInfo(runtimeJar).Length != new FileInfo(builtJar).Length)
            {
                File.Copy(builtJar, runtimeJar, true);
                File.SetLastWriteTimeUtc(runtimeJar, File.GetLastWriteTimeUtc(builtJar));
                Log("Runtime jar updated from " + builtJar);
            }

            // 3. Start the server. It reads application-local.properties from the project folder.
            splash.SetStatus("Starting the FOCUS FLOW server...");
            string serverLog = Path.Combine(logDir, "server.log");
            RotateLog(serverLog);
            var psi = new ProcessStartInfo(FindJava(),
                "-Dfile.encoding=UTF-8 -jar \"" + runtimeJar + "\" --logging.file.name=\"" + serverLog + "\"");
            psi.WorkingDirectory = projectDir;
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true; // hidden console: lets --stop send a graceful Ctrl+C
            var server = Process.Start(psi);
            Log("Server started, pid " + server.Id);
            File.WriteAllText(pidFile, server.Id + "|" + server.StartTime.ToUniversalTime().Ticks);

            // 4. Wait until it answers.
            var wait = Stopwatch.StartNew();
            while (true)
            {
                if (server.HasExited)
                {
                    TryDelete(pidFile);
                    throw new Exception("The FOCUS FLOW server stopped while starting (exit code " + server.ExitCode + ").\n\n" + Tail(serverLog, 6));
                }
                ServerState st = Probe();
                if (st == ServerState.Running) break;
                if (wait.Elapsed > TimeSpan.FromMinutes(3))
                {
                    StopProcess(server);
                    TryDelete(pidFile);
                    throw new Exception("The FOCUS FLOW server did not answer within 3 minutes.");
                }
                Thread.Sleep(500);
            }
            Log("Server ready after " + wait.Elapsed.TotalSeconds.ToString("0.0") + "s");
            splash.SetStatus("Opening FOCUS FLOW...");
            return server;
        }

        static string EnsureBuiltJar(SplashForm splash)
        {
            string libs = Path.Combine(projectDir, @"build\libs");
            string jar = FindBuiltJar(libs);
            // Gradle leaves the jar untouched when only timestamps changed, so remember when a build last
            // confirmed it as current; otherwise every launch would rebuild.
            string stamp = Path.Combine(dataDir, "build.stamp");
            DateTime verified = jar == null ? DateTime.MinValue : File.GetLastWriteTimeUtc(jar);
            if (jar != null && File.Exists(stamp)) verified = Max(verified, File.GetLastWriteTimeUtc(stamp));
            DateTime buildStarted = DateTime.UtcNow;
            bool stale = jar == null || NewestSourceTime() > verified;
            if (!stale) return jar;

            splash.SetStatus(jar == null ? "Building FOCUS FLOW for the first time (a few minutes)..." : "Source changed - rebuilding FOCUS FLOW...");
            string buildLog = Path.Combine(logDir, "build.log");
            // Absolute path: cmd ignores the working directory when NoDefaultCurrentDirectoryInExePath is set.
            string gradlew = Path.Combine(projectDir, "gradlew.bat");
            var psi = new ProcessStartInfo("cmd.exe", "/s /c \"\"" + gradlew + "\" bootJar --console=plain > \"" + buildLog + "\" 2>&1\"");
            psi.WorkingDirectory = projectDir;
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            using (var p = Process.Start(psi))
            {
                if (!p.WaitForExit((int)TimeSpan.FromMinutes(10).TotalMilliseconds)) { try { p.Kill(); } catch { } }
                string newJar = FindBuiltJar(libs);
                if (p.HasExited && p.ExitCode == 0 && newJar != null)
                {
                    File.WriteAllText(stamp, newJar);
                    File.SetLastWriteTimeUtc(stamp, buildStarted);
                    Log("Build succeeded");
                    return newJar;
                }
            }
            Log("Build failed, see " + buildLog);
            if (jar != null) { Log("Using the previous build"); return jar; }
            throw new Exception("Building FOCUS FLOW failed. See " + buildLog);
        }

        static string FindBuiltJar(string libs)
        {
            if (!Directory.Exists(libs)) return null;
            return Directory.GetFiles(libs, "*.jar")
                .Where(f => !f.EndsWith("-plain.jar", StringComparison.OrdinalIgnoreCase))
                .OrderByDescending(f => File.GetLastWriteTimeUtc(f)).FirstOrDefault();
        }

        static DateTime NewestSourceTime()
        {
            DateTime newest = DateTime.MinValue;
            foreach (var f in new[] { "build.gradle", "settings.gradle" })
            {
                string p = Path.Combine(projectDir, f);
                if (File.Exists(p)) newest = Max(newest, File.GetLastWriteTimeUtc(p));
            }
            string main = Path.Combine(projectDir, @"src\main");
            if (Directory.Exists(main))
                foreach (var f in Directory.EnumerateFiles(main, "*", SearchOption.AllDirectories))
                    newest = Max(newest, File.GetLastWriteTimeUtc(f));
            return newest;
        }

        static DateTime Max(DateTime a, DateTime b) { return a > b ? a : b; }

        static string FindJava()
        {
            string home = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(home))
            {
                string j = Path.Combine(home, @"bin\java.exe");
                if (File.Exists(j)) return j;
            }
            foreach (var dir in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(';'))
            {
                try
                {
                    string j = Path.Combine(dir.Trim().Trim('"'), "java.exe");
                    if (File.Exists(j)) return j;
                }
                catch { }
            }
            throw new Exception("Java 21 was not found. Install JDK 21 or set JAVA_HOME.");
        }

        // DB_PORT from the environment or application-local.properties. Only that one key is read.
        static int ReadDbPort()
        {
            int port;
            string env = Environment.GetEnvironmentVariable("DB_PORT");
            if (int.TryParse(env, out port)) return port;
            foreach (var file in new[] { Path.Combine(projectDir, "application-local.properties"),
                                         Path.Combine(projectDir, @"src\main\resources\application-local.properties") })
            {
                if (!File.Exists(file)) continue;
                foreach (var line in File.ReadAllLines(file))
                {
                    string t = line.Trim();
                    if (t.StartsWith("DB_PORT") && t.Contains("="))
                    {
                        string key = t.Substring(0, t.IndexOf('=')).Trim();
                        if (key == "DB_PORT" && int.TryParse(t.Substring(t.IndexOf('=') + 1).Trim(), out port)) return port;
                    }
                }
            }
            return 3306; // application.properties default
        }

        // ------------------------------------------------------------------ ownership / stop

        class OwnedServer { public Process Process; }

        static OwnedServer ReadOwnedServer()
        {
            try
            {
                if (!File.Exists(pidFile)) return null;
                string[] parts = File.ReadAllText(pidFile).Trim().Split('|');
                int pid = int.Parse(parts[0]);
                long ticks = long.Parse(parts[1]);
                Process p = Process.GetProcessById(pid);
                if (p.HasExited || p.StartTime.ToUniversalTime().Ticks != ticks) { TryDelete(pidFile); return null; }
                return new OwnedServer { Process = p };
            }
            catch { TryDelete(pidFile); return null; }
        }

        static int StopCommand()
        {
            var owned = ReadOwnedServer();
            if (owned == null)
            {
                bool running = Probe() == ServerState.Running;
                MessageBox.Show(running
                    ? "FOCUS FLOW is running, but it was not started by this launcher (e.g. IntelliJ), so it was left alone."
                    : "The FOCUS FLOW server is not running.", AppName, MessageBoxButtons.OK, MessageBoxIcon.Information);
                return 0;
            }
            StopProcess(owned.Process);
            TryDelete(pidFile);
            return 0;
        }

        static int StatusCommand()
        {
            var owned = ReadOwnedServer();
            ServerState st = Probe();
            string msg = st == ServerState.Running
                ? "FOCUS FLOW server is running on port " + WebPort + (owned != null ? " (started by the launcher, pid " + owned.Process.Id + ")." : " (started outside the launcher).")
                : st == ServerState.Foreign ? "Port " + WebPort + " is used by another program." : "FOCUS FLOW server is not running.";
            MessageBox.Show(msg, AppName, MessageBoxButtons.OK, MessageBoxIcon.Information);
            return 0;
        }

        // Graceful: Ctrl+C on the server's hidden console runs Spring's shutdown hooks (closes DB pool cleanly).
        static void StopProcess(Process p)
        {
            if (p.HasExited) return;
            Log("Stopping server pid " + p.Id);
            bool signalled = SendCtrlC(p.Id);
            if (signalled && p.WaitForExit(30000)) { Log("Server stopped gracefully"); return; }
            try { p.Kill(); p.WaitForExit(10000); Log("Server terminated (graceful stop " + (signalled ? "timed out" : "unavailable") + ")"); }
            catch (Exception ex) { Log("Stop failed: " + ex.Message); }
        }

        static readonly object consoleLock = new object();

        static bool SendCtrlC(int pid)
        {
            lock (consoleLock)
            {
                FreeConsole();
                if (!AttachConsole((uint)pid)) return false;
                SetConsoleCtrlHandler(IntPtr.Zero, true); // do not let the signal end this launcher
                bool ok = GenerateConsoleCtrlEvent(0 /* CTRL_C_EVENT */, 0);
                Thread.Sleep(300);
                FreeConsole();
                return ok;
            }
        }

        // ------------------------------------------------------------------ app window

        static string FindBrowser()
        {
            string pf = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
            string pf86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
            string local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            foreach (var p in new[] {
                Path.Combine(pf, @"Google\Chrome\Application\chrome.exe"),
                Path.Combine(pf86, @"Google\Chrome\Application\chrome.exe"),
                Path.Combine(local, @"Google\Chrome\Application\chrome.exe"),
                Path.Combine(pf86, @"Microsoft\Edge\Application\msedge.exe"),
                Path.Combine(pf, @"Microsoft\Edge\Application\msedge.exe") })
                if (File.Exists(p)) return p;
            return null;
        }

        // Returns the dedicated browser process, or null when only the default browser could be used.
        static Process OpenAppWindow()
        {
            string browser = FindBrowser();
            if (browser == null)
            {
                Log("Chrome/Edge not found; opening the default browser");
                Process.Start(AppUrl);
                return null;
            }
            bool firstRun = !Directory.Exists(browserProfile);
            Directory.CreateDirectory(browserProfile);
            // Afterwards the browser remembers the size and position the user last left the window in.
            string args = "--user-data-dir=\"" + browserProfile + "\" --app=" + AppUrl +
                          " --no-first-run --no-default-browser-check" + (firstRun ? " --start-maximized" : "");
            AllowSetForegroundWindow(-1); // let the browser bring its window to the front
            var p = Process.Start(new ProcessStartInfo(browser, args) { UseShellExecute = false });
            Log("App window requested via " + Path.GetFileName(browser));
            // Chrome hands the request to an already running instance of this profile and exits.
            for (int i = 0; i < 40; i++)
            {
                if (FindBrowserMainProcesses().Count > 0) return p;
                Thread.Sleep(250);
            }
            return FindBrowserMainProcesses().Count > 0 ? p : null;
        }

        // Main browser processes (not renderers/GPU helpers) that use the FOCUS FLOW profile.
        static List<int> FindBrowserMainProcesses()
        {
            var result = new List<int>();
            try
            {
                using (var searcher = new ManagementObjectSearcher(
                    "SELECT ProcessId, CommandLine FROM Win32_Process WHERE Name='chrome.exe' OR Name='msedge.exe'"))
                {
                    foreach (ManagementObject mo in searcher.Get())
                    {
                        string cmd = mo["CommandLine"] as string;
                        if (cmd == null) continue;
                        if (cmd.IndexOf(browserProfile, StringComparison.OrdinalIgnoreCase) < 0) continue;
                        if (cmd.IndexOf("--type=", StringComparison.OrdinalIgnoreCase) >= 0) continue;
                        result.Add(Convert.ToInt32(mo["ProcessId"]));
                    }
                }
            }
            catch (Exception ex) { Log("Process scan failed: " + ex.Message); }
            return result;
        }

        // Runs while the app window is open, then stops the server this launcher owns.
        static void MonitorUntilClosed()
        {
            var tagged = new HashSet<IntPtr>();
            int missing = 0;
            while (true)
            {
                var owned = ReadOwnedServer();
                if (owned == null && Probe() != ServerState.Running) { Log("Server is gone; launcher exits"); return; }

                List<int> mains = FindBrowserMainProcesses();
                if (mains.Count > 0)
                {
                    missing = 0;
                    TagWindows(mains, tagged);
                    // Sleep on the browser process itself so closing the window is noticed at once.
                    try { Process.GetProcessById(mains[0]).WaitForExit(1500); } catch { }
                    continue;
                }

                // Grace period: a quick re-open (another click) should keep the server.
                if (++missing < 5) { Thread.Sleep(1000); continue; }

                Log("App window closed");
                if (owned != null) { StopProcess(owned.Process); TryDelete(pidFile); }
                return;
            }
        }

        // Group the browser app window under the FOCUS FLOW taskbar icon.
        static void TagWindows(List<int> pids, HashSet<IntPtr> tagged)
        {
            EnumWindows((hwnd, l) =>
            {
                uint pid;
                GetWindowThreadProcessId(hwnd, out pid);
                if (!pids.Contains((int)pid) || tagged.Contains(hwnd) || !IsWindowVisible(hwnd)) return true;
                if (GetWindow(hwnd, 4 /* GW_OWNER */) != IntPtr.Zero) return true;
                var cls = new StringBuilder(64);
                GetClassName(hwnd, cls, cls.Capacity);
                if (cls.ToString() != "Chrome_WidgetWin_1" || GetWindowTextLength(hwnd) == 0) return true;
                try
                {
                    SetWindowAppId(hwnd);
                    tagged.Add(hwnd);
                    if (IsIconic(hwnd)) ShowWindow(hwnd, 9 /* SW_RESTORE */);
                    SetForegroundWindow(hwnd);
                    Log("Tagged app window " + hwnd);
                }
                catch (Exception ex) { Log("Tagging window failed: " + ex.Message); tagged.Add(hwnd); }
                return true;
            }, IntPtr.Zero);
        }

        // ------------------------------------------------------------------ shell integration

        static readonly Guid AppUserModelFmtid = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3");

        static void SetWindowAppId(IntPtr hwnd)
        {
            Guid iid = typeof(IPropertyStore).GUID;
            IPropertyStore store;
            Marshal.ThrowExceptionForHR(SHGetPropertyStoreForWindow(hwnd, ref iid, out store));
            try
            {
                SetString(store, 2, "\"" + exePath + "\"");   // RelaunchCommand
                SetString(store, 3, iconPath + ",0");          // RelaunchIconResource
                SetString(store, 4, AppName);                  // RelaunchDisplayNameResource
                SetString(store, 5, AppId);                    // ID (last, moves the taskbar button)
                store.Commit();
            }
            finally { Marshal.ReleaseComObject(store); }
        }

        static void SetString(IPropertyStore store, uint pid, string value)
        {
            var key = new PropertyKey { fmtid = AppUserModelFmtid, pid = pid };
            var pv = new PropVariant { vt = 31 /* VT_LPWSTR */, p = Marshal.StringToCoTaskMemUni(value) };
            try { Marshal.ThrowExceptionForHR(store.SetValue(ref key, ref pv)); }
            finally { Marshal.FreeCoTaskMem(pv.p); }
        }

        static void CreateShortcut(string lnkPath)
        {
            var link = (IShellLinkW)new CShellLink();
            link.SetPath(exePath);
            link.SetArguments("");
            link.SetWorkingDirectory(Path.GetDirectoryName(exePath));
            link.SetIconLocation(iconPath, 0);
            link.SetDescription("Start the FOCUS FLOW server and open the app");
            var store = (IPropertyStore)link;
            SetString(store, 5, AppId);
            store.Commit();
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(lnkPath)));
            ((IPersistFile)link).Save(Path.GetFullPath(lnkPath), true);
            Marshal.ReleaseComObject(link);
        }

        // ------------------------------------------------------------------ helpers

        static string FindProjectDir(string start)
        {
            var dir = new DirectoryInfo(start);
            for (int i = 0; i < 4 && dir != null; i++, dir = dir.Parent)
                if (File.Exists(Path.Combine(dir.FullName, "gradlew.bat")) && File.Exists(Path.Combine(dir.FullName, "build.gradle")))
                    return dir.FullName;
            return null;
        }

        static readonly object logLock = new object();

        static void Log(string message)
        {
            try
            {
                lock (logLock)
                {
                    string file = Path.Combine(logDir ?? Path.GetTempPath(), "launcher.log");
                    if (File.Exists(file) && new FileInfo(file).Length > 1024 * 1024) RotateLog(file);
                    File.AppendAllText(file, DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + " [" + Process.GetCurrentProcess().Id + "] " + message + Environment.NewLine);
                }
            }
            catch { }
        }

        static void RotateLog(string file)
        {
            try
            {
                if (!File.Exists(file)) return;
                string prev = Path.Combine(Path.GetDirectoryName(file), Path.GetFileNameWithoutExtension(file) + ".previous.log");
                File.Copy(file, prev, true);
                File.Delete(file);
            }
            catch { }
        }

        static string Tail(string file, int lines)
        {
            try
            {
                if (!File.Exists(file)) return "";
                using (var fs = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
                using (var r = new StreamReader(fs))
                {
                    var all = r.ReadToEnd().Split('\n').Where(l => l.Trim().Length > 0).ToList();
                    var tail = all.Skip(Math.Max(0, all.Count - lines)).Select(l => l.Length > 220 ? l.Substring(0, 220) + "..." : l.TrimEnd());
                    return string.Join("\n", tail);
                }
            }
            catch { return ""; }
        }

        static void TryDelete(string file) { try { File.Delete(file); } catch { } }

        static void ShowError(string message, bool offerLogs = false)
        {
            if (offerLogs)
            {
                if (MessageBox.Show(message + "\n\nOpen the log folder?", AppName, MessageBoxButtons.YesNo, MessageBoxIcon.Error) == DialogResult.Yes)
                    Process.Start("explorer.exe", "\"" + logDir + "\"");
            }
            else MessageBox.Show(message, AppName, MessageBoxButtons.OK, MessageBoxIcon.Error);
        }

        // ------------------------------------------------------------------ interop

        [DllImport("shell32.dll")] static extern int SetCurrentProcessExplicitAppUserModelID([MarshalAs(UnmanagedType.LPWStr)] string appId);
        [DllImport("shell32.dll")] static extern int SHGetPropertyStoreForWindow(IntPtr hwnd, ref Guid iid, [MarshalAs(UnmanagedType.Interface)] out IPropertyStore store);
        [DllImport("user32.dll")] static extern bool SetProcessDPIAware();
        [DllImport("kernel32.dll", SetLastError = true)] static extern bool AttachConsole(uint pid);
        [DllImport("kernel32.dll")] static extern bool FreeConsole();
        [DllImport("kernel32.dll")] static extern bool SetConsoleCtrlHandler(IntPtr handler, bool add);
        [DllImport("kernel32.dll")] static extern bool GenerateConsoleCtrlEvent(uint ctrlEvent, uint processGroupId);
        delegate bool EnumWindowsProc(IntPtr hwnd, IntPtr lParam);
        [DllImport("user32.dll")] static extern bool EnumWindows(EnumWindowsProc cb, IntPtr lParam);
        [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
        [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr hwnd);
        [DllImport("user32.dll")] static extern IntPtr GetWindow(IntPtr hwnd, uint cmd);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetClassName(IntPtr hwnd, StringBuilder name, int max);
        [DllImport("user32.dll")] static extern int GetWindowTextLength(IntPtr hwnd);
        [DllImport("user32.dll")] static extern bool IsIconic(IntPtr hwnd);
        [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr hwnd, int cmd);
        [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr hwnd);
        [DllImport("user32.dll")] static extern bool AllowSetForegroundWindow(int pid);
    }

    [StructLayout(LayoutKind.Sequential, Pack = 4)]
    struct PropertyKey { public Guid fmtid; public uint pid; }

    [StructLayout(LayoutKind.Explicit, Size = 24)]
    struct PropVariant { [FieldOffset(0)] public ushort vt; [FieldOffset(8)] public IntPtr p; }

    [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IPropertyStore
    {
        [PreserveSig] int GetCount(out uint count);
        [PreserveSig] int GetAt(uint index, out PropertyKey key);
        [PreserveSig] int GetValue(ref PropertyKey key, out PropVariant value);
        [PreserveSig] int SetValue(ref PropertyKey key, ref PropVariant value);
        [PreserveSig] int Commit();
    }

    [ComImport, Guid("00021401-0000-0000-C000-000000000046")]
    class CShellLink { }

    [ComImport, Guid("000214F9-0000-0000-C000-000000000046"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IShellLinkW
    {
        void GetPath([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder file, int max, IntPtr fd, uint flags);
        void GetIDList(out IntPtr pidl);
        void SetIDList(IntPtr pidl);
        void GetDescription([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder name, int max);
        void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string name);
        void GetWorkingDirectory([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder dir, int max);
        void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string dir);
        void GetArguments([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder args, int max);
        void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string args);
        void GetHotkey(out short hotkey);
        void SetHotkey(short hotkey);
        void GetShowCmd(out int showCmd);
        void SetShowCmd(int showCmd);
        void GetIconLocation([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder path, int max, out int index);
        void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string path, int index);
        void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string path, uint reserved);
        void Resolve(IntPtr hwnd, uint flags);
        void SetPath([MarshalAs(UnmanagedType.LPWStr)] string file);
    }

    // Small start-up window shown while the server boots.
    class SplashForm : Form
    {
        readonly Label status;

        public SplashForm(string iconPath)
        {
            Text = "FOCUS FLOW";
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            ShowInTaskbar = true;
            BackColor = Color.FromArgb(0x16, 0x14, 0x40);
            ClientSize = new Size(440, 230);
            try { Icon = new Icon(iconPath); } catch { }

            var logo = new PictureBox { SizeMode = PictureBoxSizeMode.Zoom, Size = new Size(72, 72), Location = new Point(184, 30), BackColor = Color.Transparent };
            try { logo.Image = new Icon(iconPath, 128, 128).ToBitmap(); } catch { }
            var title = new Label
            {
                Text = "FOCUS FLOW", ForeColor = Color.White, Font = new Font("Segoe UI Semibold", 15f),
                AutoSize = false, TextAlign = ContentAlignment.MiddleCenter, Bounds = new Rectangle(0, 110, 440, 34)
            };
            status = new Label
            {
                Text = "Starting...", ForeColor = Color.FromArgb(0xC4, 0xB5, 0xFD), Font = new Font("Segoe UI", 9.5f),
                AutoSize = false, TextAlign = ContentAlignment.MiddleCenter, Bounds = new Rectangle(10, 150, 420, 24)
            };
            var bar = new ProgressBar { Style = ProgressBarStyle.Marquee, MarqueeAnimationSpeed = 25, Bounds = new Rectangle(70, 188, 300, 6) };
            Controls.AddRange(new Control[] { logo, title, status, bar });
        }

        public void SetStatus(string text)
        {
            if (IsHandleCreated) BeginInvoke(new Action(() => status.Text = text));
        }

        public void CloseSafe()
        {
            if (IsHandleCreated) BeginInvoke(new Action(Close));
        }
    }
}
