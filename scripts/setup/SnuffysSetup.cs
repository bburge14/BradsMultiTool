// Snuffy's Launcher Setup: the one file to send a friend. It downloads Snuffy's Launcher from GitHub into the user's
// own folder (%LOCALAPPDATA%\Snuffy's Launcher: no admin rights), puts it on the desktop and opens it (with no window
// of its own: PowerShell running it hidden). Run again, it just opens the launcher (which updates itself from then on).
// C# 5 for .NET Framework 4 (in every Windows 10 and 11); built by scripts/split_plugins.py with csc or mcs.
using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;

[assembly: AssemblyTitle("Snuffy's Launcher Setup")]
[assembly: AssemblyProduct("Snuffy's Launcher")]
[assembly: AssemblyDescription("Installs Snuffy's Launcher for Snuffy's RuneLite plugins")]

namespace Snuffys
{
    public static class Setup
    {
        private const string Zip = "https://codeload.github.com/bburge14/snuffys-launcher/zip/refs/heads/main";
        private static readonly Color Window = Color.FromArgb(32, 33, 36);
        private static readonly Color Text = Color.FromArgb(241, 243, 244);
        private static readonly Color Muted = Color.FromArgb(154, 160, 166);

        private static string Home
        {
            get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Snuffy's Launcher"); }
        }

        [STAThread]
        public static void Main()
        {
            Application.EnableVisualStyles();
            if (File.Exists(Path.Combine(Home, "scripts", "launcher.ps1")))
            {
                // Already there: make sure of the shortcut and open it
                Shortcut();
                Open();
                return;
            }
            Form form = new Form();
            form.Text = "Snuffy's Launcher Setup";
            form.FormBorderStyle = FormBorderStyle.FixedSingle;
            form.MaximizeBox = false;
            form.StartPosition = FormStartPosition.CenterScreen;
            form.ClientSize = new Size(420, 130);
            form.BackColor = Window;
            form.ForeColor = Text;
            form.Font = new Font("Segoe UI", 9.5f);
            try
            {
                form.Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            }
            catch (Exception)
            {
                // the default icon
            }
            Label title = new Label();
            title.Text = "Installing Snuffy's Launcher";
            title.Font = new Font("Segoe UI", 12f, FontStyle.Bold);
            title.Location = new Point(20, 16);
            title.AutoSize = true;
            form.Controls.Add(title);
            Label status = new Label();
            status.Text = "Downloading...";
            status.ForeColor = Muted;
            status.Location = new Point(20, 50);
            status.Size = new Size(380, 20);
            form.Controls.Add(status);
            ProgressBar bar = new ProgressBar();
            bar.Location = new Point(20, 80);
            bar.Size = new Size(380, 10);
            bar.Style = ProgressBarStyle.Marquee;
            form.Controls.Add(bar);
            form.Shown += delegate
            {
                Thread work = new Thread(delegate()
                {
                    string problem = Install(delegate(string said) { form.BeginInvoke((MethodInvoker) delegate { status.Text = said; }); });
                    form.BeginInvoke((MethodInvoker) delegate
                    {
                        if (problem != null)
                        {
                            MessageBox.Show(form, "Couldn't install Snuffy's Launcher:\n\n" + problem
                                + "\n\nCheck your internet connection and run this again.", "Snuffy's Launcher Setup",
                                MessageBoxButtons.OK, MessageBoxIcon.Warning);
                        }
                        else
                        {
                            Shortcut();
                            Open();
                        }
                        form.Close();
                    });
                });
                work.IsBackground = true;
                work.Start();
            };
            Application.Run(form);
        }

        // Downloads the launcher's zip and puts its files in Home; null when it worked, else what went wrong
        private static string Install(Action<string> say)
        {
            string zip = Path.Combine(Path.GetTempPath(), "snuffys-launcher-setup.zip");
            string unzipped = Path.Combine(Path.GetTempPath(), "snuffys-launcher-setup");
            try
            {
                ServicePointManager.SecurityProtocol = (SecurityProtocolType) 3072;
                using (WebClient web = new WebClient())
                {
                    web.Headers.Add("User-Agent", "snuffys-launcher-setup");
                    web.DownloadFile(Zip, zip);
                }
                say("Unpacking...");
                if (Directory.Exists(unzipped))
                {
                    Directory.Delete(unzipped, true);
                }
                Unzip(zip, unzipped);
                // The zip holds one folder (snuffys-launcher-main): its files become Home
                string[] inner = Directory.GetDirectories(unzipped);
                string from = inner.Length == 1 ? inner[0] : unzipped;
                Copy(from, Home);
                say("Done.");
                return null;
            }
            catch (Exception e)
            {
                return e.Message;
            }
            finally
            {
                try
                {
                    File.Delete(zip);
                    if (Directory.Exists(unzipped))
                    {
                        Directory.Delete(unzipped, true);
                    }
                }
                catch (Exception)
                {
                    // left for Windows to clear
                }
            }
        }

        // Windows PowerShell's Expand-Archive (in every Windows 10 and 11), with no window
        private static void Unzip(string zip, string to)
        {
            ProcessStartInfo start = new ProcessStartInfo("powershell.exe", "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden "
                + "-Command \"Expand-Archive -LiteralPath '" + zip.Replace("'", "''") + "' -DestinationPath '" + to.Replace("'", "''")
                + "' -Force\"");
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            using (Process unzip = Process.Start(start))
            {
                unzip.WaitForExit();
                if (unzip.ExitCode != 0 || !Directory.Exists(to))
                {
                    throw new IOException("the download couldn't be unpacked");
                }
            }
        }

        private static void Copy(string from, string to)
        {
            Directory.CreateDirectory(to);
            foreach (string file in Directory.GetFiles(from))
            {
                File.Copy(file, Path.Combine(to, Path.GetFileName(file)), true);
            }
            foreach (string folder in Directory.GetDirectories(from))
            {
                Copy(folder, Path.Combine(to, Path.GetFileName(folder)));
            }
        }

        // "Snuffy's Launcher" on the desktop: PowerShell running the launcher hidden (as its own shortcut is), its icon
        private static void Shortcut()
        {
            try
            {
                string desktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                string link = Path.Combine(desktop, "Snuffy's Launcher.lnk");
                Type shellType = Type.GetTypeFromProgID("WScript.Shell");
                object shell = Activator.CreateInstance(shellType);
                object shortcut = shellType.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { link });
                Type type = shortcut.GetType();
                // A "Snuffy's Launcher" shortcut to another copy (a developer's own) stays: this one gets its own name
                string args = (string) type.InvokeMember("Arguments", BindingFlags.GetProperty, null, shortcut, null);
                if (File.Exists(link) && (args == null || args.IndexOf(Home, StringComparison.OrdinalIgnoreCase) < 0))
                {
                    link = Path.Combine(desktop, "Snuffy's Launcher (installed).lnk");
                    shortcut = shellType.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { link });
                }
                string powershell = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),
                    "WindowsPowerShell\\v1.0\\powershell.exe");
                type.InvokeMember("TargetPath", BindingFlags.SetProperty, null, shortcut, new object[] { powershell });
                type.InvokeMember("Arguments", BindingFlags.SetProperty, null, shortcut, new object[] {
                    "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \"" + Path.Combine(Home, "scripts\\launcher.ps1") + "\"" });
                type.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, shortcut, new object[] { Home });
                type.InvokeMember("WindowStyle", BindingFlags.SetProperty, null, shortcut, new object[] { 7 });
                type.InvokeMember("IconLocation", BindingFlags.SetProperty, null, shortcut, new object[] {
                    Path.Combine(Home, "assets\\launcher.ico") + ",0" });
                type.InvokeMember("Save", BindingFlags.InvokeMethod, null, shortcut, null);
            }
            catch (Exception)
            {
                // no shortcut: the launcher's "Make a desktop shortcut" does it
            }
        }

        // The launcher, with no window of its own: PowerShell running it hidden, as the desktop shortcut does (not through
        // its .vbs, so an install whose .vbs is old still opens; the launcher then updates itself)
        private static void Open()
        {
            try
            {
                string powershell = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),
                    "WindowsPowerShell\\v1.0\\powershell.exe");
                ProcessStartInfo start = new ProcessStartInfo(powershell, "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden "
                    + "-File \"" + Path.Combine(Home, "scripts\\launcher.ps1") + "\"");
                start.WorkingDirectory = Home;
                start.UseShellExecute = false;
                start.CreateNoWindow = true;
                Process.Start(start);
            }
            catch (Exception e)
            {
                MessageBox.Show("Couldn't open Snuffy's Launcher: " + e.Message, "Snuffy's Launcher Setup");
            }
        }
    }
}
