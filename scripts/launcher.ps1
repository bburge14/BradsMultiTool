# Snuffy's launcher window (Launch-BradsMultiTool.bat opens it), two tabs. Dev: pushes the plugins to their own
# repositories and publishes them to RuneLite's Plugin Hub (docs/ROLLOUT.md). Play: updates the plugins (on opening, then checked every
# minute in the background while it's open, pulled as soon as there's one), warns (red, a bold Relaunch, a Windows
# notification) when the RuneLite that's open was built from an older version, Relaunch closes and restarts RuneLite with any update, checks the setup (RuneLite
# installed, session saving on in RuneLite (configure), a Jagex session saved, Java, git) with a button to fix
# each, and walks through saving a session; lets you pick which plugins RuneLite loads, and launches it. The Toolbelt has every separate
# plugin in it, so it's either the Toolbelt on its own or any of the separate ones. Launching runs hidden with a
# progress bar (building, starting, running; the log a click away) and the window stays open while RuneLite runs.
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
# Anything that stops the launcher says what in a message (and in %TEMP%\snuffys-launcher-error.log), instead of the
# window just not opening (its console is hidden)
$startupLog = Join-Path $env:TEMP 'snuffys-launcher-error.log'
# Everything the launcher says (its steps, any error) goes to %TEMP%\snuffys-launcher.log too: its console is hidden
try { Start-Transcript -Path (Join-Path $env:TEMP 'snuffys-launcher.log') -Force | Out-Null } catch { }
function Show-Problem($problem) {
    try { Set-Content -Path $startupLog -Value $problem } catch { }
    Write-Host "PROBLEM: $problem"
    [void][System.Windows.Forms.MessageBox]::Show("Snuffy's Launcher hit a problem:`r`n`r`n$problem`r`n`r`n(Saved in ${startupLog}: send it to Claude.)",
        "Snuffy's Launcher")
}
# An error inside the window (drawing it, a click) says what instead of closing it
try { [System.Windows.Forms.Application]::SetUnhandledExceptionMode([System.Windows.Forms.UnhandledExceptionMode]::CatchException) } catch { }
[System.Windows.Forms.Application]::add_ThreadException({
    param($source, $info)
    Show-Problem ($info.Exception.ToString())
})
Write-Host "Starting $(Get-Date)"
trap {
    Show-Problem "$($_.Exception.Message)`r`n`r`n$($_.InvocationInfo.PositionMessage)"
    exit 1
}
# Finding RuneLite's "Are you sure you want to exit?" box, for Relaunch to answer it
Add-Type -Namespace Snuffys -Name Win -MemberDefinition @'
[DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern IntPtr FindWindowEx(IntPtr parent, IntPtr after, string cls, string title);
[DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hwnd);
[DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hwnd, int show);
[DllImport("kernel32.dll")] public static extern IntPtr GetConsoleWindow();
'@
# No console window behind the launcher, however it was started (the .bat, the shortcut): only the launcher shows.
# Everything it runs (the build, git, Python) runs hidden too.
$consoleWindow = [Snuffys.Win]::GetConsoleWindow()
if ($consoleWindow -ne [IntPtr]::Zero) { [void][Snuffys.Win]::ShowWindow($consoleWindow, 0) }
[System.Windows.Forms.Application]::EnableVisualStyles()
# GDI+ text off (Windows' own, crisper text), before any window is made
try { [System.Windows.Forms.Application]::SetCompatibleTextRenderingDefault($false) } catch { }
# The window itself (dark theme, rounded cards, switches, the Launch button that fills as it builds, the Dev
# steps, high-DPI): the C# below, which Windows PowerShell 5.1 compiles (C# 5). The logic stays here
$compileErrors = $null
$uiSource = @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Windows.Forms;

namespace Snuffys
{
    // ---- The launcher's look: colours and the drawing helpers every control shares ----

    public static class Theme
    {
        public static readonly Color Window = Color.FromArgb(32, 33, 36);
        public static readonly Color Surface = Color.FromArgb(48, 49, 52);
        public static readonly Color Raised = Color.FromArgb(60, 64, 67);
        public static readonly Color RaisedHover = Color.FromArgb(74, 78, 82);
        public static readonly Color RaisedDown = Color.FromArgb(88, 92, 96);
        public static readonly Color Border = Color.FromArgb(72, 74, 78);
        public static readonly Color Text = Color.FromArgb(241, 243, 244);
        public static readonly Color Muted = Color.FromArgb(154, 160, 166);
        public static readonly Color Accent = Color.FromArgb(0, 120, 212);
        public static readonly Color AccentHover = Color.FromArgb(24, 136, 226);
        public static readonly Color AccentDown = Color.FromArgb(0, 98, 180);
        public static readonly Color Link = Color.FromArgb(138, 180, 248);
        public static readonly Color Good = Color.FromArgb(88, 204, 120);
        public static readonly Color Bad = Color.FromArgb(242, 105, 105);
        public static readonly Color Warn = Color.FromArgb(242, 180, 80);

        public const string FontName = "Segoe UI";

        public static Font Body(float size)
        {
            return new Font(FontName, size, FontStyle.Regular);
        }

        public static Font Bold(float size)
        {
            return new Font(FontName, size, FontStyle.Bold);
        }

        [DllImport("dwmapi.dll")]
        private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);

        [DllImport("uxtheme.dll", CharSet = CharSet.Unicode)]
        private static extern int SetWindowTheme(IntPtr hwnd, string app, string idList);

        // Windows 10 (1809 on) and 11: dark scroll bars on a control, as Explorer's dark mode has (a white one on a
        // dark box is hard to look past). Ignored where there's no dark theme
        public static void DarkScrollBars(Control control)
        {
            EventHandler apply = delegate
            {
                try
                {
                    SetWindowTheme(control.Handle, "DarkMode_Explorer", null);
                }
                catch (Exception)
                {
                    // older Windows (or not Windows): its own scroll bars
                }
            };
            if (control.IsHandleCreated) apply(control, EventArgs.Empty);
            else control.HandleCreated += apply;
        }

        // Windows 10 (20H1 on) and 11: the title bar dark too (attribute 20; 19 on older builds). Ignored elsewhere
        public static void DarkTitleBar(IntPtr hwnd)
        {
            int on = 1;
            try
            {
                if (DwmSetWindowAttribute(hwnd, 20, ref on, 4) != 0)
                {
                    DwmSetWindowAttribute(hwnd, 19, ref on, 4);
                }
            }
            catch (Exception)
            {
                // no DWM: the title bar stays as it is
            }
        }

        [DllImport("user32.dll")]
        private static extern bool SetProcessDPIAware();

        // Sharp on scaled displays: Windows draws the window at the screen's real resolution (the layout scales with
        // it, AutoScaleMode.Dpi) instead of stretching a blurry 96 DPI picture. Before any window is made
        public static void DpiAware()
        {
            try
            {
                SetProcessDPIAware();
            }
            catch (Exception)
            {
                // not Windows, or too old: stays as it is
            }
        }

        // A rounded rectangle, radius in pixels
        public static GraphicsPath Round(RectangleF r, float radius)
        {
            GraphicsPath path = new GraphicsPath();
            float d = Math.Min(radius * 2, Math.Min(r.Width, r.Height));
            if (d <= 0)
            {
                path.AddRectangle(r);
                return path;
            }
            path.AddArc(r.X, r.Y, d, d, 180, 90);
            path.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            path.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            path.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            path.CloseFigure();
            return path;
        }

        // How much bigger than 96 DPI this is drawn (1 at 100% scaling, 1.5 at 150%)
        public static float Scale(Graphics g)
        {
            return g.DpiX / 96f;
        }

        // The colour behind a control (for its rounded corners)
        public static Color Behind(Control control)
        {
            Control parent = control.Parent;
            while (parent != null && parent.BackColor.A < 255)
            {
                parent = parent.Parent;
            }
            return parent != null ? parent.BackColor : Window;
        }

        public static Color Mix(Color a, Color b, float amount)
        {
            return Color.FromArgb(
                (int) (a.R + (b.R - a.R) * amount), (int) (a.G + (b.G - a.G) * amount), (int) (a.B + (b.B - a.B) * amount));
        }

        // A picture made small and square-ish, for icons (null when the file isn't there)
        public static Image Picture(string path, int size)
        {
            try
            {
                if (path == null || !File.Exists(path))
                {
                    return null;
                }
                using (Image full = path.EndsWith(".ico", StringComparison.OrdinalIgnoreCase)
                    ? (Image) new Icon(path, 256, 256).ToBitmap() : Image.FromFile(path))
                {
                    float scale = Math.Min((float) size / full.Width, (float) size / full.Height);
                    int w = Math.Max(1, (int) (full.Width * scale));
                    int h = Math.Max(1, (int) (full.Height * scale));
                    Bitmap small = new Bitmap(size, size);
                    using (Graphics g = Graphics.FromImage(small))
                    {
                        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                        g.DrawImage(full, (size - w) / 2, (size - h) / 2, w, h);
                    }
                    return small;
                }
            }
            catch (Exception)
            {
                return null;
            }
        }
    }

    // A rounded button: primary ones in the accent colour, the rest dark grey (a bold font marks one that needs
    // pressing: it goes accent too). Value (1-99) fills it from the left like a progress bar
    public class RoundButton : Button
    {
        private bool hover;
        private bool down;
        private int value;
        public bool Primary;

        public RoundButton()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer
                | ControlStyles.ResizeRedraw | ControlStyles.SupportsTransparentBackColor, true);
            FlatStyle = FlatStyle.Flat;
            FlatAppearance.BorderSize = 0;
            Cursor = Cursors.Hand;
            Font = Theme.Body(9);
        }

        public int Value
        {
            get { return value; }
            set { this.value = Math.Max(0, Math.Min(100, value)); Invalidate(); }
        }

        protected override void OnMouseEnter(EventArgs e) { hover = true; Invalidate(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { hover = false; down = false; Invalidate(); base.OnMouseLeave(e); }
        protected override void OnMouseDown(MouseEventArgs e) { down = true; Invalidate(); base.OnMouseDown(e); }
        protected override void OnMouseUp(MouseEventArgs e) { down = false; Invalidate(); base.OnMouseUp(e); }
        protected override void OnEnabledChanged(EventArgs e) { Invalidate(); base.OnEnabledChanged(e); }
        protected override void OnFontChanged(EventArgs e) { Invalidate(); base.OnFontChanged(e); }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.Clear(Theme.Behind(this));
            g.SmoothingMode = SmoothingMode.AntiAlias;
            float s = Theme.Scale(g);
            bool accent = Primary || Font.Bold;
            Color fill;
            if (!Enabled)
            {
                fill = Theme.Raised;
            }
            else if (accent)
            {
                fill = down ? Theme.AccentDown : hover ? Theme.AccentHover : Theme.Accent;
            }
            else
            {
                fill = down ? Theme.RaisedDown : hover ? Theme.RaisedHover : Theme.Raised;
            }
            RectangleF box = new RectangleF(0, 0, Width - 1, Height - 1);
            using (GraphicsPath path = Theme.Round(box, 6 * s))
            {
                using (SolidBrush brush = new SolidBrush(fill))
                {
                    g.FillPath(brush, path);
                }
                // Progress: the accent filling it from the left
                if (value > 0 && value < 100)
                {
                    Region clip = g.Clip;
                    g.SetClip(path);
                    using (SolidBrush brush = new SolidBrush(Theme.Accent))
                    {
                        g.FillRectangle(brush, 0, 0, Width * value / 100f, Height);
                    }
                    g.Clip = clip;
                }
            }
            Color text = !Enabled && value == 0 ? Theme.Muted : accent || value > 0 ? Color.White : Theme.Text;
            TextRenderer.DrawText(g, Text, Font, new Rectangle(0, 0, Width, Height), text,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
        }
    }

    // A card: a rounded surface with a 1 px border and, optionally, a bold title at its top left
    public class Card : Panel
    {
        public string Title;
        /** An accent border (something new on it). */
        public bool Accent;

        public Card()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer
                | ControlStyles.ResizeRedraw, true);
            BackColor = Theme.Surface;
            ForeColor = Theme.Text;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.Clear(Theme.Behind(this));
            g.SmoothingMode = SmoothingMode.AntiAlias;
            float s = Theme.Scale(g);
            using (GraphicsPath path = Theme.Round(new RectangleF(0.5f, 0.5f, Width - 1.5f, Height - 1.5f), 10 * s))
            {
                using (SolidBrush brush = new SolidBrush(BackColor))
                {
                    g.FillPath(brush, path);
                }
                using (Pen pen = new Pen(Accent ? Theme.Accent : Theme.Border, Accent ? 2 * s : 1))
                {
                    g.DrawPath(pen, path);
                }
            }
            if (!string.IsNullOrEmpty(Title))
            {
                using (Font bold = Theme.Bold(10))
                {
                    TextRenderer.DrawText(g, Title, bold, new Point((int) (14 * s), (int) (11 * s)), Theme.Text);
                }
            }
        }
    }

    // An on/off switch with a picture and a name (a check box underneath: Checked, CheckedChanged)
    public class ToggleSwitch : CheckBox
    {
        public Image Icon;

        public ToggleSwitch()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer
                | ControlStyles.ResizeRedraw, true);
            AutoSize = false;
            Cursor = Cursors.Hand;
            Font = Theme.Body(9);
        }

        protected override void OnEnabledChanged(EventArgs e) { Invalidate(); base.OnEnabledChanged(e); }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.Clear(Theme.Behind(this));
            g.SmoothingMode = SmoothingMode.AntiAlias;
            float s = Theme.Scale(g);
            float w = 34 * s;
            float h = 18 * s;
            float y = (Height - h) / 2;
            Color track = !Checked ? Theme.Raised : Enabled ? Theme.Accent : Theme.Mix(Theme.Accent, Theme.Surface, 0.55f);
            using (GraphicsPath path = Theme.Round(new RectangleF(0, y, w, h), h / 2))
            {
                using (SolidBrush brush = new SolidBrush(track))
                {
                    g.FillPath(brush, path);
                }
                if (!Checked)
                {
                    using (Pen pen = new Pen(Theme.Border))
                    {
                        g.DrawPath(pen, path);
                    }
                }
            }
            float knob = h - 6 * s;
            float x = Checked ? w - knob - 3 * s : 3 * s;
            using (SolidBrush brush = new SolidBrush(Enabled ? Color.White : Theme.Muted))
            {
                g.FillEllipse(brush, x, y + 3 * s, knob, knob);
            }
            float left = w + 8 * s;
            if (Icon != null)
            {
                float size = 18 * s;
                g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                g.DrawImage(Icon, left, (Height - size) / 2, size, size);
                left += size + 6 * s;
            }
            TextRenderer.DrawText(g, Text, Font, new Rectangle((int) left, 0, Width - (int) left, Height),
                Enabled ? Theme.Text : Theme.Muted, TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
        }
    }

    // A segmented switch: SegmentButtons (radio buttons underneath: one checked at a time) on a dark rounded track
    public class SegmentTrack : Panel
    {
        public SegmentTrack()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer
                | ControlStyles.ResizeRedraw, true);
            BackColor = Theme.Window;
            Padding = new Padding(3);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.Clear(Theme.Behind(this));
            g.SmoothingMode = SmoothingMode.AntiAlias;
            float s = Theme.Scale(g);
            using (GraphicsPath path = Theme.Round(new RectangleF(0.5f, 0.5f, Width - 1.5f, Height - 1.5f), 8 * s))
            {
                using (SolidBrush brush = new SolidBrush(BackColor))
                {
                    g.FillPath(brush, path);
                }
                using (Pen pen = new Pen(Theme.Border))
                {
                    g.DrawPath(pen, path);
                }
            }
        }

        // Adds the segments side by side, sharing the width
        public SegmentButton[] Segments(params string[] names)
        {
            SegmentButton[] out_ = new SegmentButton[names.Length];
            int inner = Width - 6;
            for (int i = 0; i < names.Length; i++)
            {
                SegmentButton segment = new SegmentButton();
                segment.Text = names[i];
                segment.Location = new Point(3 + inner * i / names.Length, 3);
                segment.Size = new Size(inner * (i + 1) / names.Length - inner * i / names.Length, Height - 6);
                Controls.Add(segment);
                out_[i] = segment;
            }
            return out_;
        }
    }

    public class SegmentButton : RadioButton
    {
        private bool hover;

        public SegmentButton()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer
                | ControlStyles.ResizeRedraw, true);
            AutoSize = false;
            Cursor = Cursors.Hand;
            Font = Theme.Body(9);
        }

        protected override void OnMouseEnter(EventArgs e) { hover = true; Invalidate(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { hover = false; Invalidate(); base.OnMouseLeave(e); }
        protected override void OnCheckedChanged(EventArgs e) { Invalidate(); base.OnCheckedChanged(e); }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.Clear(Theme.Behind(this));
            g.SmoothingMode = SmoothingMode.AntiAlias;
            float s = Theme.Scale(g);
            if (Checked || hover)
            {
                using (GraphicsPath path = Theme.Round(new RectangleF(0, 0, Width - 1, Height - 1), 6 * s))
                using (SolidBrush brush = new SolidBrush(Checked ? Theme.Raised : Theme.Surface))
                {
                    g.FillPath(brush, path);
                }
            }
            using (Font font = Checked ? Theme.Bold(9) : Theme.Body(9))
            {
                TextRenderer.DrawText(g, Text, font, new Rectangle(0, 0, Width, Height), Checked ? Theme.Text : Theme.Muted,
                    TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
            }
        }
    }

    // A status chip: a dot and the text on a soft pill of its colour (ForeColor: green, red, or plain text for neutral)
    public class Chip : Label
    {
        public Chip()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer
                | ControlStyles.ResizeRedraw, true);
            AutoSize = false;
            Font = Theme.Body(8.5f);
            ForeColor = Theme.Muted;
        }

        // The whole text on hover (a long one is cut short with "...")
        private static readonly ToolTip Tip = new ToolTip();

        protected override void OnTextChanged(EventArgs e)
        {
            base.OnTextChanged(e);
            Tip.SetToolTip(this, Text);
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.Clear(Theme.Behind(this));
            if (string.IsNullOrEmpty(Text))
            {
                return;
            }
            g.SmoothingMode = SmoothingMode.AntiAlias;
            float s = Theme.Scale(g);
            Size text = TextRenderer.MeasureText(g, Text, Font);
            float dot = 7 * s;
            float width = Math.Min(Width - 1, text.Width + dot + 22 * s);
            Color tone = ForeColor == Theme.Text ? Theme.Muted : ForeColor;
            using (GraphicsPath path = Theme.Round(new RectangleF(0, 0, width, Height - 1), (Height - 1) / 2f))
            using (SolidBrush brush = new SolidBrush(Color.FromArgb(46, tone)))
            {
                g.FillPath(brush, path);
            }
            using (SolidBrush brush = new SolidBrush(tone))
            {
                g.FillEllipse(brush, 9 * s, (Height - dot) / 2, dot, dot);
            }
            TextRenderer.DrawText(g, Text, Font, new Rectangle((int) (9 * s + dot + 5 * s), 0, (int) width, Height),
                ForeColor == Theme.Text ? Theme.Text : Theme.Mix(tone, Color.White, 0.25f),
                TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
        }
    }

    // A text link in the theme's link colour
    public class Link : LinkLabel
    {
        public Link(string text)
        {
            Text = text;
            AutoSize = true;
            LinkColor = Theme.Link;
            VisitedLinkColor = Theme.Link;
            ActiveLinkColor = Theme.Text;
            LinkBehavior = LinkBehavior.HoverUnderline;
            Font = Theme.Body(9);
            BackColor = Color.Transparent;
        }
    }

    // One line of the setup checklist: a tick or a cross, what it says, and a button to fix it
    public class SetupRow
    {
        public Label Mark;
        public Label Text;
        public RoundButton Button;
    }

    // ---- The window ----

    public class LauncherUi
    {
        public Form Form;
        // Header
        public Chip UpdateStatus;
        public Chip RunningStatus;
        public Link CheckAgain;
        public SegmentButton PlayNav;
        public SegmentButton DevNav;
        // Play
        public Panel Play;
        public TextBox UpdateLog;
        public Card NewsCard;
        public Card SetupCard;
        public Label SetupSummary;
        public Link SetupToggle;
        public Dictionary<string, SetupRow> Rows = new Dictionary<string, SetupRow>();
        public SegmentButton Toolbelt;
        public SegmentButton Separate;
        public Label Note;
        public Dictionary<string, ToggleSwitch> Boxes = new Dictionary<string, ToggleSwitch>();
        public RoundButton Launch;
        public Label LaunchStatus;
        public Link ShowLog;
        public RoundButton Relaunch;
        public RoundButton Close;
        public Link Shortcut;
        // Dev
        public Panel Dev;
        public Card PushCard;
        public Dictionary<string, ToggleSwitch> DevTicks = new Dictionary<string, ToggleSwitch>();
        public SegmentButton Patch;
        public SegmentButton Minor;
        public SegmentButton Major;
        public RoundButton DevCheck;
        public RoundButton DevPush;
        public RoundButton DevPublish;
        public Link PrLink;
        public RoundButton DevStatusButton;
        public Label DevStatus;
        public SegmentButton DevMainNav;
        public SegmentButton DevHubNav;
        public SegmentButton DevOutNav;
        private Label outputStatus;
        public Card HubSummary;
        public FlowLayoutPanel HubChips;
        public Label HubSummaryLine;
        public Label HubStatus;
        public FlowLayoutPanel PrList;
        public Link ShowOutput;
        public TextBox DevOut;
        public Link DevGuide;

        private readonly string root;
        private bool setupOpen = true;
        private int devTickCount;

        // The plugins the Play tab loads: key, name, icon (assets\hub)
        private static readonly string[][] Plugins = {
            new[] { "skilling", "Skilling Helper", "skilltracker" },
            new[] { "pvm", "PvM/Slayer", "pvm" },
            new[] { "gemcrab", "GemCrab Tracker", "gemcrab" },
            new[] { "tick", "Tick Counter", "tickcounter" },
            new[] { "planner", "Planner", "planner" },
            new[] { "alerts", "Alerts", "voices" },
        };

        // Whether there's a Dev tab (the dev launcher), or just Play (the friends launcher)
        private readonly bool dev;

        public LauncherUi(string root) : this(root, true)
        {
        }

        public LauncherUi(string root, bool dev)
        {
            this.root = root;
            this.dev = dev;
            Form = new Form();
            Form.AutoScaleDimensions = new SizeF(96F, 96F);
            Form.AutoScaleMode = AutoScaleMode.Dpi;
            Form.Text = "Snuffy's Launcher";
            Form.FormBorderStyle = FormBorderStyle.FixedSingle;
            Form.MaximizeBox = false;
            Form.StartPosition = FormStartPosition.CenterScreen;
            Form.ClientSize = new Size(480, 760);
            Form.Font = Theme.Body(9);
            Form.BackColor = Theme.Window;
            Form.ForeColor = Theme.Text;
            string icon = Path.Combine(Path.Combine(root, "assets"), "launcher.ico");
            try
            {
                if (File.Exists(icon))
                {
                    Form.Icon = new Icon(icon);
                }
            }
            catch (Exception)
            {
                // an icon this Windows can't read: the default one
            }
            Form.HandleCreated += delegate { Theme.DarkTitleBar(Form.Handle); };

            BuildDev();
            BuildPlay();
            BuildHeader();
            PlayNav.Checked = true;
            // The focus on Launch (Enter launches), not on a text box with its text all selected
            Form.Shown += delegate { Form.ActiveControl = Launch; UpdateLog.Select(0, 0); };
        }

        // Scrolls up and down only: a scroll bar never pushes the cards into a sideways one
        private static void UpDownOnly(FlowLayoutPanel flow)
        {
            Theme.DarkScrollBars(flow);
            flow.AutoScroll = false;
            flow.HorizontalScroll.Enabled = false;
            flow.HorizontalScroll.Visible = false;
            flow.HorizontalScroll.Maximum = 0;
            flow.AutoScroll = true;
        }

        private static Label Text(string text, int x, int y, int width, int height, Color colour, Font font)
        {
            Label label = new Label();
            label.Text = text;
            label.Location = new Point(x, y);
            label.Size = new Size(width, height);
            label.ForeColor = colour;
            label.BackColor = Color.Transparent;
            label.Font = font;
            return label;
        }

        private static RoundButton Button(string text, int x, int y, int width, int height, bool primary)
        {
            RoundButton button = new RoundButton();
            button.Text = text;
            button.Location = new Point(x, y);
            button.Size = new Size(width, height);
            button.Primary = primary;
            if (primary)
            {
                button.Font = Theme.Bold(9);
            }
            return button;
        }

        private string Asset(string folder, string name)
        {
            return Path.Combine(Path.Combine(Path.Combine(root, "assets"), folder), name);
        }

        // ---- Header: the logo, the name, the update chip, and Play | Dev ----

        private void BuildHeader()
        {
            Panel header = new Panel();
            header.Dock = DockStyle.Top;
            header.Height = 112;
            header.BackColor = Theme.Window;
            PictureBox logo = new PictureBox();
            // The Toolbelt's badge from its PNG (the .ico's big frame is PNG-compressed, which Windows' icon reader
            // turns into noise)
            logo.Image = Theme.Picture(Asset("hub", "multitool.png"), 44);
            logo.SizeMode = PictureBoxSizeMode.Zoom;
            logo.Location = new Point(18, 14);
            logo.Size = new Size(44, 44);
            header.Controls.Add(logo);
            header.Controls.Add(Text("Snuffy's Launcher", 72, 12, 250, 26, Theme.Text, new Font(Theme.FontName, 13, FontStyle.Bold)));
            // Two chips: your copy of the code (updates), and the RuneLite that's open (built from it, or older)
            UpdateStatus = new Chip();
            UpdateStatus.Location = new Point(72, 40);
            UpdateStatus.Size = new Size(150, 22);
            UpdateStatus.Text = "Code: checking...";
            header.Controls.Add(UpdateStatus);
            RunningStatus = new Chip();
            RunningStatus.Location = new Point(226, 40);
            RunningStatus.Size = new Size(238, 22);
            RunningStatus.Text = "RuneLite: not running";
            header.Controls.Add(RunningStatus);
            CheckAgain = new Link("Check again");
            CheckAgain.Location = new Point(384, 16);
            header.Controls.Add(CheckAgain);

            SegmentTrack nav = new SegmentTrack();
            nav.Location = new Point(16, 72);
            nav.Size = new Size(448, 34);
            SegmentButton[] tabs = nav.Segments("Play", "Dev");
            PlayNav = tabs[0];
            DevNav = tabs[1];
            PlayNav.CheckedChanged += delegate { Play.Visible = PlayNav.Checked; Dev.Visible = !PlayNav.Checked; };
            header.Controls.Add(nav);
            if (!dev)
            {
                // Just Play: no Play | Dev switch, and the room it took goes to the page
                nav.Visible = false;
                header.Height = 72;
            }
            Form.Controls.Add(header);
        }

        // ---- Play: what's new, setup (folds away once it's all set), plugins, and the launch bar ----

        private void BuildPlay()
        {
            Play = new Panel();
            Play.Dock = DockStyle.Fill;
            Play.BackColor = Theme.Window;

            // The launch bar, pinned to the bottom
            Panel bar = new Panel();
            bar.Dock = DockStyle.Bottom;
            bar.Height = 132;
            bar.BackColor = Theme.Window;
            LaunchStatus = Text("Ready.", 18, 6, 300, 20, Theme.Muted, Theme.Body(9));
            bar.Controls.Add(LaunchStatus);
            ShowLog = new Link("Show log");
            ShowLog.Location = new Point(404, 8);
            ShowLog.Visible = false;
            bar.Controls.Add(ShowLog);
            Launch = Button("Launch RuneLite", 16, 30, 448, 48, true);
            Launch.Font = Theme.Bold(11);
            bar.Controls.Add(Launch);
            Relaunch = Button("Relaunch", 16, 86, 140, 34, false);
            Relaunch.Enabled = false;
            bar.Controls.Add(Relaunch);
            Close = Button("Close", 364, 86, 100, 34, false);
            Close.DialogResult = DialogResult.Cancel;
            bar.Controls.Add(Close);
            Shortcut = new Link("Make a desktop shortcut");
            Shortcut.Location = new Point(170, 95);
            bar.Controls.Add(Shortcut);
            Form.AcceptButton = Launch;
            Form.CancelButton = Close;

            FlowLayoutPanel flow = new FlowLayoutPanel();
            flow.Dock = DockStyle.Fill;
            flow.FlowDirection = FlowDirection.TopDown;
            flow.WrapContents = false;
            UpDownOnly(flow);
            flow.Padding = new Padding(16, 6, 0, 6);
            flow.BackColor = Theme.Window;

            // What's new
            Card news = new Card();
            NewsCard = news;
            news.Title = "What's new";
            news.Size = new Size(448, 96);
            news.Margin = new Padding(0, 0, 0, 12);
            UpdateLog = new TextBox();
            UpdateLog.Multiline = true;
            UpdateLog.ReadOnly = true;
            UpdateLog.ScrollBars = ScrollBars.Vertical;
            UpdateLog.BorderStyle = BorderStyle.None;
            UpdateLog.BackColor = Theme.Surface;
            UpdateLog.ForeColor = Color.FromArgb(205, 208, 212);
            UpdateLog.Font = Theme.Body(9);
            UpdateLog.Location = new Point(14, 38);
            Theme.DarkScrollBars(UpdateLog);
            // The whole list in a window of its own, big enough to read
            Link showAll = new Link("Show all");
            showAll.Location = new Point(380, 12);
            showAll.LinkClicked += delegate { ShowText("What's new", UpdateLog.Text); };
            news.Controls.Add(showAll);
            UpdateLog.Size = new Size(420, 46);
            UpdateLog.TabStop = false;
            news.Controls.Add(UpdateLog);
            flow.Controls.Add(news);

            // Setup: a summary line that opens into the checklist
            SetupCard = new Card();
            SetupCard.Title = "Setup";
            SetupCard.Size = new Size(448, 182);
            SetupCard.Margin = new Padding(0, 0, 0, 12);
            SetupSummary = Text("", 80, 12, 260, 20, Theme.Muted, Theme.Body(9));
            SetupCard.Controls.Add(SetupSummary);
            SetupToggle = new Link("Hide");
            SetupToggle.Location = new Point(390, 13);
            SetupToggle.LinkClicked += delegate { ShowSetup(!setupOpen); };
            SetupCard.Controls.Add(SetupToggle);
            int y = 42;
            foreach (string key in new[] { "runelite", "flag", "session", "java", "git" })
            {
                SetupRow row = new SetupRow();
                row.Mark = Text("", 14, y + 4, 20, 20, Theme.Muted, new Font("Segoe UI Symbol", 9, FontStyle.Bold));
                row.Text = Text("", 36, y + 4, 290, 20, Theme.Text, Theme.Body(9));
                row.Button = Button("", 332, y, 102, 26, false);
                row.Button.Visible = false;
                SetupCard.Controls.Add(row.Mark);
                SetupCard.Controls.Add(row.Text);
                SetupCard.Controls.Add(row.Button);
                Rows[key] = row;
                y += 27;
            }
            flow.Controls.Add(SetupCard);

            // Plugins: the Toolbelt or the separate ones, each a switch
            Card plugins = new Card();
            plugins.Title = "Plugins to load";
            plugins.Size = new Size(448, 192);
            plugins.Margin = new Padding(0);
            SegmentTrack which = new SegmentTrack();
            which.BackColor = Theme.Window;
            which.Location = new Point(14, 40);
            which.Size = new Size(420, 32);
            SegmentButton[] choice = which.Segments("Snuffy's Toolbelt", "Separate plugins");
            Toolbelt = choice[0];
            Separate = choice[1];
            plugins.Controls.Add(which);
            Note = Text("The Toolbelt is every plugin in one.", 16, 78, 410, 18, Theme.Muted, Theme.Body(8.5f));
            plugins.Controls.Add(Note);
            for (int i = 0; i < Plugins.Length; i++)
            {
                ToggleSwitch toggle = new ToggleSwitch();
                toggle.Text = Plugins[i][1];
                toggle.Icon = Theme.Picture(Asset("hub", Plugins[i][2] + ".png"), 36);
                toggle.Location = new Point(16 + (i / 3) * 210, 102 + (i % 3) * 28);
                toggle.Size = new Size(dev ? 208 : 186, 24);
                plugins.Controls.Add(toggle);
                Boxes[Plugins[i][0]] = toggle;
                if (!dev)
                {
                    // The friends launcher: remove a plugin (its download, and its updates), or add it back
                    Link remove = new Link("\u2715");
                    remove.Tag = Plugins[i][0];
                    remove.Location = new Point(16 + (i / 3) * 210 + 190, 105 + (i % 3) * 28);
                    versionTip.SetToolTip(remove, "Remove " + Plugins[i][1] + " (its download; add it back any time)");
                    plugins.Controls.Add(remove);
                    RemoveLinks[Plugins[i][0]] = remove;
                }
            }
            if (!dev)
            {
                Link removeToolbelt = new Link("Remove");
                removeToolbelt.Tag = "toolbelt";
                removeToolbelt.Location = new Point(384, 78);
                versionTip.SetToolTip(removeToolbelt, "Remove Snuffy's Toolbelt (its download; add it back any time)");
                plugins.Controls.Add(removeToolbelt);
                RemoveLinks["toolbelt"] = removeToolbelt;
                Note.Width = 360;
            }
            Toolbelt.CheckedChanged += delegate { SetToolbelt(); };
            flow.Controls.Add(plugins);

            Play.Controls.Add(flow);
            Play.Controls.Add(bar);
            Form.Controls.Add(Play);
        }

        /** The friends launcher's remove buttons, by plugin key (toolbelt too). */
        public Dictionary<string, Link> RemoveLinks = new Dictionary<string, Link>();
        private readonly HashSet<string> removed = new HashSet<string>();

        public bool IsRemoved(string key)
        {
            return removed.Contains(key);
        }

        /** A plugin removed (switched off, its button a + to add it back) or there again. */
        public void SetRemoved(string key, bool gone)
        {
            if (gone) removed.Add(key); else removed.Remove(key);
            Link link;
            if (RemoveLinks.TryGetValue(key, out link))
            {
                string name = key == "toolbelt" ? "Snuffy's Toolbelt" : NameOf(key);
                link.Text = gone ? (key == "toolbelt" ? "Add back" : "+") : (key == "toolbelt" ? "Remove" : "\u2715");
                versionTip.SetToolTip(link, gone ? "Add " + name + " back (downloads it again)"
                    : "Remove " + name + " (its download; add it back any time)");
            }
            if (key == "toolbelt")
            {
                Toolbelt.Enabled = !gone;
                if (gone && Toolbelt.Checked)
                {
                    Separate.Checked = true;
                }
            }
            else if (Boxes.ContainsKey(key))
            {
                if (gone)
                {
                    Boxes[key].Checked = false;
                }
                Boxes[key].Enabled = !gone && !Toolbelt.Checked;
            }
        }

        private static string NameOf(string key)
        {
            foreach (string[] plugin in Plugins)
            {
                if (plugin[0] == key) return plugin[1];
            }
            return key;
        }

        private void SetToolbelt()
        {
            Note.Text = Toolbelt.Checked ? "The Toolbelt is every plugin in one: the switches don't apply."
                : "Load just the ones switched on.";
            foreach (KeyValuePair<string, ToggleSwitch> box in Boxes)
            {
                box.Value.Enabled = !Toolbelt.Checked && !removed.Contains(box.Key);
            }
        }

        // Open (every line, its fix button) or folded to its summary line
        public void ShowSetup(bool open)
        {
            setupOpen = open;
            foreach (SetupRow row in Rows.Values)
            {
                row.Mark.Visible = open;
                row.Text.Visible = open;
            }
            foreach (SetupRow row in Rows.Values)
            {
                row.Button.Parent = open ? SetupCard : null;
            }
            SetupCard.Height = open ? 182 : 44;
            // What's new takes the room the checklist leaves when it's folded
            NewsCard.Height = open ? 96 : 96 + 138;
            UpdateLog.Height = NewsCard.Height - 50;
            SetupToggle.Text = open ? "Hide" : "Show";
        }

        // After the setup check: "All set" (folded) or how many need you (opened)
        public void UpdateSetupSummary()
        {
            int problems = 0;
            foreach (SetupRow row in Rows.Values)
            {
                if (row.Mark.Text != "\u2714")
                {
                    problems++;
                }
            }
            SetupSummary.Text = problems == 0 ? "\u2714  All set" : problems + (problems == 1 ? " thing needs" : " things need") + " you";
            SetupSummary.ForeColor = problems == 0 ? Theme.Good : Theme.Bad;
            ShowSetup(problems > 0);
        }

        // ---- Dev: 1 push, 2 publish, 3 status, with each plugin's pull request as a card ----

        private void BuildDev()
        {
            Dev = new Panel();
            Dev.Dock = DockStyle.Fill;
            Dev.BackColor = Theme.Window;
            Dev.Visible = false;
            // Two sub-tabs: Publish (the steps, with the Plugin Hub's summary on top) and Plugin Hub (every card)
            FlowLayoutPanel flow = DevFlow();
            FlowLayoutPanel board = DevFlow();
            board.Visible = false;
            // Output: what Push and Publish print, filling the tab (it switches here while they run)
            Panel output = new Panel();
            output.Dock = DockStyle.Fill;
            output.Padding = new Padding(16, 4, 16, 12);
            output.BackColor = Theme.Window;
            output.Visible = false;
            Panel top = new Panel();
            top.Dock = DockStyle.Top;
            top.Height = 40;
            top.BackColor = Theme.Window;
            SegmentTrack sub = new SegmentTrack();
            sub.Location = new Point(16, 4);
            sub.Size = new Size(448, 30);
            SegmentButton[] subs = sub.Segments("Publish", "Plugin Hub", "Output");
            DevMainNav = subs[0];
            DevHubNav = subs[1];
            DevOutNav = subs[2];
            DevMainNav.Checked = true;
            EventHandler pick = delegate
            {
                flow.Visible = DevMainNav.Checked;
                board.Visible = DevHubNav.Checked;
                output.Visible = DevOutNav.Checked;
            };
            DevMainNav.CheckedChanged += pick;
            DevHubNav.CheckedChanged += pick;
            DevOutNav.CheckedChanged += pick;
            top.Controls.Add(sub);

            // The Plugin Hub at a glance: how many in each state, how many new; click for the board
            HubSummary = new Card();
            HubSummary.Title = "Plugin Hub";
            HubSummary.Size = new Size(448, 104);
            HubSummary.Margin = new Padding(0, 0, 0, 12);
            HubSummary.Cursor = Cursors.Hand;
            Link openBoard = new Link("Open the board");
            openBoard.Location = new Point(346, 12);
            openBoard.LinkClicked += delegate { ShowHub(); };
            HubSummary.Controls.Add(openBoard);
            HubChips = new FlowLayoutPanel();
            HubChips.Location = new Point(12, 38);
            HubChips.Size = new Size(424, 28);
            HubChips.BackColor = Theme.Surface;
            HubChips.WrapContents = true;
            HubSummary.Controls.Add(HubChips);
            HubSummaryLine = Text("Not checked yet: it checks on its own while the launcher is open.", 14, 74, 420, 20, Theme.Muted,
                Theme.Body(8.5f));
            HubSummary.Controls.Add(HubSummaryLine);
            HubSummary.Click += delegate { ShowHub(); };
            HubSummaryLine.Click += delegate { ShowHub(); };
            HubChips.Click += delegate { ShowHub(); };
            flow.Controls.Add(HubSummary);

            // 1. Push
            PushCard = new Card();
            PushCard.Title = "1   Push to repos";
            PushCard.Size = new Size(448, 246);
            PushCard.Margin = new Padding(0, 0, 0, 12);
            PushCard.Controls.Add(Text("Copies each plugin switched on to its own GitHub repository; the changed ones get a new version.",
                16, 34, 416, 34, Theme.Muted, Theme.Body(8.5f)));
            SegmentTrack level = new SegmentTrack();
            level.BackColor = Theme.Window;
            level.Location = new Point(14, 162);
            level.Size = new Size(420, 30);
            SegmentButton[] levels = level.Segments("Patch (fixes)", "Minor (features)", "Major");
            Patch = levels[0];
            Minor = levels[1];
            Major = levels[2];
            Patch.Checked = true;
            PushCard.Controls.Add(level);
            DevCheck = Button("Check (dry run)", 14, 200, 140, 32, false);
            DevPush = Button("Push to repos", 162, 200, 140, 32, false);
            PushCard.Controls.Add(DevCheck);
            PushCard.Controls.Add(DevPush);

            // 2. Publish
            Card publish = new Card();
            publish.Title = "2   Publish to RuneLite";
            publish.Size = new Size(448, 118);
            publish.Margin = new Padding(0, 0, 0, 12);
            publish.Controls.Add(Text("One Plugin Hub pull request per plugin; publishing again updates the open ones.", 16, 34, 416, 34,
                Theme.Muted, Theme.Body(8.5f)));
            DevPublish = Button("Publish to RuneLite", 14, 72, 180, 34, true);
            publish.Controls.Add(DevPublish);
            PrLink = new Link("Open the new pull requests");
            PrLink.Location = new Point(206, 81);
            PrLink.Visible = false;
            publish.Controls.Add(PrLink);

            // 3. Status
            Card status = new Card();
            status.Title = "Plugin Hub";
            status.Size = new Size(430, 86);
            status.Margin = new Padding(0, 0, 0, 8);
            DevStatusButton = Button("Check now", 14, 40, 120, 32, false);
            status.Controls.Add(DevStatusButton);
            HubStatus = Text("Checks on its own while the launcher is open.", 144, 40, 274, 34, Theme.Muted, Theme.Body(8.5f));
            HubStatus.TextAlign = ContentAlignment.MiddleLeft;
            status.Controls.Add(HubStatus);
            board.Controls.Add(status);

            PrList = new FlowLayoutPanel();
            PrList.FlowDirection = FlowDirection.TopDown;
            PrList.WrapContents = false;
            PrList.AutoSize = true;
            PrList.AutoSizeMode = AutoSizeMode.GrowAndShrink;
            PrList.BackColor = Theme.Window;
            PrList.Margin = new Padding(0);
            board.Controls.Add(PrList);
            // HubStatus says it on the summary card too
            HubStatus.TextChanged += delegate { HubSummaryLine.Text = HubStatus.Text; };
            HubStatus.ForeColorChanged += delegate { HubSummaryLine.ForeColor = HubStatus.ForeColor == Theme.Text ? Theme.Muted : HubStatus.ForeColor; };
            flow.Controls.Add(PushCard);
            flow.Controls.Add(publish);
            // What the last push or publish did
            DevStatus = Text("", 0, 0, 448, 20, Theme.Muted, Theme.Body(9));
            DevStatus.Margin = new Padding(4, 0, 0, 4);
            flow.Controls.Add(DevStatus);

            Panel links = new Panel();
            links.Size = new Size(448, 24);
            links.BackColor = Theme.Window;
            ShowOutput = new Link("Show output");
            ShowOutput.Location = new Point(0, 2);
            ShowOutput.LinkClicked += delegate { ShowDevOutput(true); };
            links.Controls.Add(ShowOutput);
            DevGuide = new Link("How it works (ROLLOUT.md)");
            DevGuide.Location = new Point(290, 2);
            links.Controls.Add(DevGuide);
            flow.Controls.Add(links);

            DevOut = new TextBox();
            Theme.DarkScrollBars(DevOut);
            DevOut.Multiline = true;
            DevOut.ReadOnly = true;
            DevOut.ScrollBars = ScrollBars.Vertical;
            DevOut.Font = new Font("Consolas", 8.5f);
            DevOut.BackColor = Theme.Surface;
            DevOut.ForeColor = Theme.Text;
            DevOut.BorderStyle = BorderStyle.FixedSingle;
            DevOut.Dock = DockStyle.Fill;
            output.Controls.Add(DevOut);
            outputStatus = Text("Nothing run yet: Check, Push to repos and Publish show what they do here.", 0, 0, 448, 24,
                Theme.Muted, Theme.Body(9));
            outputStatus.Dock = DockStyle.Top;
            output.Controls.Add(outputStatus);
            DevStatus.TextChanged += delegate { if (DevStatus.Text.Length > 0) outputStatus.Text = DevStatus.Text; };
            DevStatus.ForeColorChanged += delegate { outputStatus.ForeColor = DevStatus.ForeColor; };

            Dev.Controls.Add(flow);
            Dev.Controls.Add(board);
            Dev.Controls.Add(output);
            Dev.Controls.Add(top);
            Form.Controls.Add(Dev);
        }

        // A dark window with some text in it, resizable, closed with Esc or its button
        public void ShowText(string title, string text)
        {
            Form window = new Form();
            window.Text = title;
            window.Icon = Form.Icon;
            window.StartPosition = FormStartPosition.CenterParent;
            window.AutoScaleDimensions = new SizeF(96F, 96F);
            window.AutoScaleMode = AutoScaleMode.Dpi;
            window.ClientSize = new Size(640, 520);
            window.MinimizeBox = false;
            window.BackColor = Theme.Window;
            window.ForeColor = Theme.Text;
            window.Padding = new Padding(16);
            window.HandleCreated += delegate { Theme.DarkTitleBar(window.Handle); };
            TextBox box = new TextBox();
            box.Multiline = true;
            box.ReadOnly = true;
            box.ScrollBars = ScrollBars.Vertical;
            box.BorderStyle = BorderStyle.None;
            box.BackColor = Theme.Window;
            box.ForeColor = Theme.Text;
            box.Font = Theme.Body(10);
            box.Dock = DockStyle.Fill;
            box.Text = text;
            box.TabStop = false;
            Theme.DarkScrollBars(box);
            window.Controls.Add(box);
            RoundButton close = Button("Close", 0, 0, 100, 32, false);
            close.Dock = DockStyle.Bottom;
            close.DialogResult = DialogResult.Cancel;
            window.Controls.Add(close);
            window.CancelButton = close;
            window.Shown += delegate { box.Select(0, 0); window.ActiveControl = close; };
            window.ShowDialog(Form);
            window.Dispose();
        }

        private static FlowLayoutPanel DevFlow()
        {
            FlowLayoutPanel flow = new FlowLayoutPanel();
            flow.Dock = DockStyle.Fill;
            flow.FlowDirection = FlowDirection.TopDown;
            flow.WrapContents = false;
            UpDownOnly(flow);
            flow.Padding = new Padding(16, 4, 0, 10);
            flow.BackColor = Theme.Window;
            return flow;
        }

        /** The Dev tab's Plugin Hub board (from a notification, or the summary card). */
        public void ShowHub()
        {
            DevNav.Checked = true;
            DevHubNav.Checked = true;
        }

        // The summary card's chips: how many pull requests in each state, and how many new comments
        private void DrawSummary()
        {
            HubChips.SuspendLayout();
            HubChips.Controls.Clear();
            Dictionary<string, int> counts = new Dictionary<string, int>();
            List<string> order = new List<string>();
            foreach (Pull pull in pulls)
            {
                string label = StateOf(pull);
                if (!counts.ContainsKey(label))
                {
                    counts[label] = 0;
                    order.Add(label);
                }
                counts[label]++;
            }
            int unread = Unread();
            if (unread > 0)
            {
                HubChips.Controls.Add(SummaryChip(unread + " new", Theme.Accent));
            }
            foreach (string label in order)
            {
                HubChips.Controls.Add(SummaryChip(counts[label] + " " + label.Replace(" \u00b7 build OK", ""), Tone(label)));
            }
            if (pulls.Count == 0)
            {
                HubChips.Controls.Add(SummaryChip("No pull requests yet", Theme.Muted));
            }
            // One row of chips, or two when they don't fit
            int width = 0;
            foreach (Control chip in HubChips.Controls)
            {
                width += chip.Width + 6;
            }
            int rows = width > HubChips.Width ? 2 : 1;
            HubChips.Height = rows * 28;
            HubSummaryLine.Top = 46 + rows * 28;
            HubSummary.Height = 76 + rows * 28;
            HubChips.ResumeLayout();
            HubSummary.Accent = unread > 0;
            HubSummary.Invalidate();
            DevHubNav.Text = unread > 0 ? "Plugin Hub  (" + unread + " new)" : "Plugin Hub";
        }

        private Chip SummaryChip(string text, Color tone)
        {
            Chip chip = new Chip();
            chip.Text = text;
            chip.ForeColor = tone;
            chip.Size = new Size(TextRenderer.MeasureText(text, chip.Font).Width + 30, 22);
            chip.Margin = new Padding(0, 0, 6, 6);
            chip.Cursor = Cursors.Hand;
            chip.Click += delegate { ShowHub(); };
            return chip;
        }

        // The Output sub-tab (show), or back to Publish
        public void ShowDevOutput(bool show)
        {
            if (show) DevOutNav.Checked = true;
            else DevMainNav.Checked = true;
        }

        // A plugin to push: a switch in the Push card (two to a row, room for its version)
        public ToggleSwitch AddDevTick(string folder, bool on)
        {
            ToggleSwitch toggle = new ToggleSwitch();
            toggle.Text = folder;
            toggle.Checked = on;
            toggle.Location = new Point(16 + (devTickCount % 2) * 210, 70 + (devTickCount / 2) * 22);
            toggle.Size = new Size(206, 20);
            toggle.Font = Theme.Body(8.5f);
            PushCard.Controls.Add(toggle);
            DevTicks[folder] = toggle;
            devTickCount++;
            return toggle;
        }

        private readonly ToolTip versionTip = new ToolTip();

        // A plugin's version beside its name: on Play (its key: skilling, pvm, ..., or toolbelt) and on Dev (its
        // folder), with what it means on hover
        public void SetPluginVersion(string key, string folder, string version, string tip)
        {
            // (the friends launcher keeps the version in the tooltip: its remove buttons take the room)
            string suffix = string.IsNullOrEmpty(version) || !dev ? "" : " " + version;
            if (!dev && !string.IsNullOrEmpty(version))
            {
                tip = version + ": " + tip;
            }
            if (key == "toolbelt")
            {
                Toolbelt.Text = "Snuffy's Toolbelt" + suffix;
                versionTip.SetToolTip(Toolbelt, tip);
            }
            else
            {
                foreach (string[] plugin in Plugins)
                {
                    if (plugin[0] == key && Boxes.ContainsKey(key))
                    {
                        Boxes[key].Text = plugin[1] + suffix;
                        versionTip.SetToolTip(Boxes[key], tip);
                    }
                }
            }
            if (folder != null && DevTicks.ContainsKey(folder))
            {
                DevTicks[folder].Text = folder + suffix;
                versionTip.SetToolTip(DevTicks[folder], tip);
            }
        }

        // The version bump picked: patch, minor or major
        public string Level()
        {
            return Major.Checked ? "major" : Minor.Checked ? "minor" : "patch";
        }

        // ---- The Plugin Hub board: each plugin's pull request as a card, kept up to date; comments you haven't
        // opened marked new (clicking the card opens the first of them and clears it) ----

        private class Pull
        {
            public string Plugin, Title, Url, State, Check, CheckUrl, Date, Earlier;
            public int Number;
            public List<string[]> Comments = new List<string[]>();
        }

        private readonly List<Pull> pulls = new List<Pull>();
        private readonly HashSet<string> seenComments = new HashSet<string>();
        private readonly HashSet<string> toldComments = new HashSet<string>();
        private readonly Dictionary<string, string> knownStates = new Dictionary<string, string>();
        private bool seenLoaded;
        private bool firstLook;
        private readonly List<string> news = new List<string>();

        /** Where it remembers what you've opened and been told (a file of its own, next to the launcher). */
        public string SeenPath;

        public void BeginPullRequests()
        {
            pulls.Clear();
            if (!seenLoaded)
            {
                seenLoaded = true;
                firstLook = SeenPath == null || !File.Exists(SeenPath);
                if (!firstLook)
                {
                    foreach (string line in File.ReadAllLines(SeenPath))
                    {
                        string[] parts = line.Split(' ');
                        if (parts.Length == 2 && parts[0] == "seen") seenComments.Add(parts[1]);
                        else if (parts.Length == 2 && parts[0] == "told") toldComments.Add(parts[1]);
                        else if (parts.Length == 3 && parts[0] == "state") knownStates[parts[1]] = parts[2];
                    }
                }
            }
        }

        /** One plugin's pull request; comments as {id, who, date, text, link}, oldest first. */
        public void AddPullRequest(string plugin, int number, string title, string url, string state, string check,
            string checkUrl, string date, string earlier, string[] ids, string[] users, string[] dates, string[] texts, string[] urls)
        {
            Pull pull = new Pull();
            pull.Plugin = plugin; pull.Number = number; pull.Title = title; pull.Url = url; pull.State = state;
            pull.Check = check; pull.CheckUrl = checkUrl; pull.Date = date; pull.Earlier = earlier;
            for (int i = 0; ids != null && i < ids.Length; i++)
            {
                pull.Comments.Add(new[] { ids[i], users[i], dates[i], texts[i], urls[i] });
            }
            pulls.Add(pull);
            string name = Friendly(plugin);
            // What's new since last time, for a Windows notification (nothing the first time: that's the baseline)
            foreach (string[] c in pull.Comments)
            {
                if (firstLook)
                {
                    seenComments.Add(c[0]);
                    toldComments.Add(c[0]);
                }
                else if (!toldComments.Contains(c[0]))
                {
                    toldComments.Add(c[0]);
                    news.Add(name + ": " + c[1] + " commented");
                }
            }
            string label = StateOf(pull);
            string key = number.ToString();
            string before;
            if (!firstLook && knownStates.TryGetValue(key, out before) && before != label.Replace(' ', '_'))
            {
                news.Add(name + ": " + label);
            }
            knownStates[key] = label.Replace(' ', '_');
        }

        /** Draws the cards and saves; returns what's new (one line each) for a notification, or "". */
        public string EndPullRequests()
        {
            firstLook = false;
            Save();
            DrawPullRequests();
            string said = string.Join("\n", news.ToArray());
            news.Clear();
            return said;
        }

        /** Comments you haven't opened, on every card. */
        public int Unread()
        {
            int count = 0;
            foreach (Pull pull in pulls)
            {
                foreach (string[] c in pull.Comments)
                {
                    if (!seenComments.Contains(c[0])) count++;
                }
            }
            return count;
        }

        private static string Friendly(string plugin)
        {
            string name = plugin.StartsWith("snuffys-") ? plugin.Substring(8) : plugin;
            switch (name)
            {
                case "skilling-helper": return "Skilling Helper";
                case "pvm-slayer": return "PvM/Slayer";
                case "gemcrab-tracker": return "GemCrab Tracker";
                case "tick-counter": return "Tick Counter";
                case "planner": return "Planner";
                case "alerts": return "Alerts";
                case "toolbelt": return "Toolbelt";
                default: return name;
            }
        }

        // The state shown on the chip: published, closed, the build, or the latest review's verdict
        private static string StateOf(Pull pull)
        {
            if (pull.State == "merged") return "Published";
            if (pull.State == "closed") return "Closed";
            if (pull.Check == "failed") return "Build failed";
            if (pull.Check == "running") return "Building";
            for (int i = pull.Comments.Count - 1; i >= 0; i--)
            {
                if (pull.Comments[i][0].StartsWith("r"))
                {
                    if (pull.Comments[i][3].StartsWith("asked for changes")) return "Changes asked";
                    if (pull.Comments[i][3].StartsWith("approved")) return "Approved";
                    break;
                }
            }
            return pull.Check == "ok" ? "In review \u00b7 build OK" : "In review";
        }

        private static Color Tone(string label)
        {
            if (label == "Published" || label == "Approved") return Theme.Good;
            if (label == "Closed") return Theme.Muted;
            if (label == "Build failed") return Theme.Bad;
            if (label == "Building" || label == "Changes asked") return Theme.Warn;
            return Theme.Link;
        }

        private void Save()
        {
            if (SeenPath == null) return;
            List<string> lines = new List<string>();
            foreach (string id in seenComments) lines.Add("seen " + id);
            foreach (string id in toldComments) lines.Add("told " + id);
            foreach (KeyValuePair<string, string> state in knownStates) lines.Add("state " + state.Key + " " + state.Value);
            try
            {
                File.WriteAllLines(SeenPath, lines.ToArray());
            }
            catch (Exception)
            {
                // not saved: the same comments show as new next time
            }
        }

        private void DrawPullRequests()
        {
            PrList.SuspendLayout();
            PrList.Controls.Clear();
            foreach (Pull pull in pulls)
            {
                PrList.Controls.Add(PullCard(pull));
            }
            PrList.ResumeLayout();
            int unread = Unread();
            DevNav.Text = unread > 0 ? "Dev  (" + unread + " new)" : "Dev";
            DrawSummary();
        }

        private Card PullCard(Pull pull)
        {
            List<string[]> unread = new List<string[]>();
            foreach (string[] c in pull.Comments)
            {
                if (!seenComments.Contains(c[0])) unread.Add(c);
            }
            string[] latest = pull.Comments.Count > 0 ? pull.Comments[pull.Comments.Count - 1] : null;
            string label = StateOf(pull);
            Card card = new Card();
            card.Size = new Size(430, latest != null ? 76 : 56);
            card.Margin = new Padding(0, 0, 0, 8);
            card.Cursor = Cursors.Hand;
            card.Accent = unread.Count > 0;
            card.Controls.Add(Text("#" + pull.Number + "  " + Friendly(pull.Plugin), 14, 9, 196, 20, Theme.Text, Theme.Bold(9)));
            Chip chip = new Chip();
            chip.Text = label;
            chip.ForeColor = Tone(label);
            chip.Location = new Point(278, 8);
            chip.Size = new Size(140, 22);
            card.Controls.Add(chip);
            if (unread.Count > 0)
            {
                Chip fresh = new Chip();
                fresh.Text = unread.Count + " new";
                fresh.ForeColor = Theme.Accent;
                fresh.Location = new Point(208, 8);
                fresh.Size = new Size(66, 22);
                card.Controls.Add(fresh);
            }
            string second = pull.State == "open" ? (pull.Check == "failed" ? "Build failed: click to see why" : "Waiting for RuneLite's review")
                : (pull.State == "merged" ? "Merged " : "Closed ") + pull.Date;
            if (!string.IsNullOrEmpty(pull.Earlier)) second += "  \u00b7  " + pull.Earlier;
            card.Controls.Add(Text(second, 14, 31, 404, 18, Theme.Muted, Theme.Body(8.5f)));
            if (latest != null)
            {
                card.Controls.Add(Text(latest[1] + ": " + latest[3], 14, 51, 404, 18,
                    unread.Count > 0 ? Theme.Text : Theme.Muted, Theme.Body(8.5f)));
            }
            // Opens the first comment you haven't seen (and marks them all seen), a failed build's log, or the page
            string page = unread.Count > 0 ? unread[0][4] : pull.Check == "failed" && !string.IsNullOrEmpty(pull.CheckUrl) ? pull.CheckUrl : pull.Url;
            Pull which = pull;
            EventHandler open = delegate
            {
                try { System.Diagnostics.Process.Start(page); } catch (Exception) { }
                foreach (string[] c in which.Comments) seenComments.Add(c[0]);
                Save();
                DrawPullRequests();
            };
            card.Click += open;
            foreach (Control child in card.Controls)
            {
                child.Click += open;
                child.Cursor = Cursors.Hand;
            }
            new ToolTip().SetToolTip(card, "Click to open " + (unread.Count > 0 ? "the new comment" : "it") + ": " + page);
            return card;
        }
    }
}
'@
# (the compiler's own messages in the error, should it fail: Add-Type on its own only says "compilation errors")
try {
    Add-Type -ReferencedAssemblies System.Windows.Forms, System.Drawing -TypeDefinition $uiSource -ErrorVariable compileErrors -ErrorAction Stop
} catch {
    throw ("The window's code didn't compile:`r`n" + (($compileErrors | Select-Object -First 8 | Out-String).Trim()))
}
# Sharp on high-DPI screens (Windows scales the window properly rather than stretching a blurry one)
[Snuffys.Theme]::DpiAware()

$root = Split-Path $PSScriptRoot -Parent
$choiceFile = Join-Path $root '.launch-choice'
# The version (commit) RuneLite was last launched with, to tell when the open one is out of date
$runningFile = Join-Path $root '.running-commit'
$creds = Join-Path $env:USERPROFILE '.runelite\credentials.properties'
$green = [Snuffys.Theme]::Good
$red = [Snuffys.Theme]::Bad
$gray = [Snuffys.Theme]::Muted
$textColor = [Snuffys.Theme]::Text

# The friends launcher (its own repository, friends.json beside this): no Dev tab; it downloads and updates each plugin's
# own repository into plugins\ and builds them with runner\, instead of this repository's code
$friendsConfig = Join-Path $root 'friends.json'
$friends = Test-Path $friendsConfig
$friendPlugins = if ($friends) { @((Get-Content $friendsConfig -Raw | ConvertFrom-Json).plugins) } else { @() }
$pluginsHome = Join-Path $root 'plugins'
# (friends) Plugins you removed: not downloaded or updated until you add them back
$removedFile = Join-Path $root '.removed-plugins'
function Get-Removed {
    if (-not (Test-Path $removedFile)) { return @() }
    return @(Get-Content $removedFile | Where-Object { $_ })
}

# (friends) What the background updater knows: each repository's commit and recent history (.plugin-state.json)
$pluginState = Join-Path $root '.plugin-state.json'
function Get-PluginState {
    $state = @{}
    if (Test-Path $pluginState) {
        try {
            $saved = Get-Content $pluginState -Raw | ConvertFrom-Json
            foreach ($property in $saved.PSObject.Properties) { $state[$property.Name] = $property.Value }
        } catch { }
    }
    return $state
}

# The code's version: this repository's commit, or (friends) the launcher's and every downloaded plugin's
function Get-Head {
    if (-not $friends) { return (git -C $root rev-parse HEAD 2>$null) }
    $state = Get-PluginState
    $parts = @()
    if ($state['launcher']) { $parts += "launcher=$($state['launcher'].sha)" }
    foreach ($plugin in $friendPlugins) {
        if ($state[$plugin.folder] -and (Test-Path (Join-Path (Join-Path $pluginsHome $plugin.folder) 'build.gradle'))) {
            $parts += "$($plugin.folder)=$($state[$plugin.folder].sha)"
        }
    }
    return ($parts -join ';')
}

# (friends) What changed in the plugins since a Get-Head (or lately, with none), one line each, from their saved
# history: all of it plugin code, so all of it needs a Relaunch
function Get-FriendsChanges($since) {
    $was = @{}
    if ($since) {
        foreach ($part in ($since -split ';')) {
            $pair = $part -split '=', 2
            if ($pair.Count -eq 2) { $was[$pair[0]] = $pair[1] }
        }
    }
    $state = Get-PluginState
    $lines = New-Object System.Collections.Generic.List[string]
    foreach ($plugin in $friendPlugins) {
        $known = $state[$plugin.folder]
        if (-not $known -or -not $known.history) { continue }
        $count = 0
        foreach ($commit in @($known.history)) {
            if ($since -and $was[$plugin.folder] -and $commit.sha -eq $was[$plugin.folder]) { break }
            if (-not $since -and $count -ge 2) { break }
            if ($since -and -not $was[$plugin.folder] -and $count -ge 3) { break }
            $lines.Add("- $($plugin.name): $($commit.subject)")
            $count++
        }
    }
    return @{ Text = ($lines -join "`r`n"); Needed = $lines.Count; All = $lines.Count }
}

$ui = New-Object Snuffys.LauncherUi($root, (-not $friends))
$form = $ui.Form
$icon = Join-Path $root 'assets\launcher.ico'

# ---- Updates: two chips under the name (your copy of the code; the RuneLite that's open), what's new in its card ----
$updateStatus = $ui.UpdateStatus
$runningStatus = $ui.RunningStatus
$updateLog = $ui.UpdateLog
$checkAgain = $ui.CheckAgain

function Update-Plugins {
    if ($friends) {
        Update-Friends
        return
    }
    $updateStatus.Text = 'Code: checking...'
    $updateStatus.ForeColor = $textColor
    $updateLog.Text = ''
    $form.Refresh()
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        $updateStatus.Text = "Code: no updates (no git)"
        $updateStatus.ForeColor = $red
        $updateLog.Text = "git isn't installed, so the plugins can't update: launching the version you have."
        return
    }
    $old = (git -C $root rev-parse HEAD 2>$null)
    # From the branch's upstream, else origin's branch of the same name (a checkout without one set still updates)
    git -C $root rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $out = (git -C $root pull --ff-only 2>&1 | Out-String)
    } else {
        $branch = (git -C $root rev-parse --abbrev-ref HEAD 2>$null | Out-String).Trim()
        $out = (git -C $root pull --ff-only origin $branch 2>&1 | Out-String)
    }
    if ($LASTEXITCODE -ne 0) {
        $updateStatus.Text = "Code: couldn't update"
        $updateStatus.ForeColor = $red
        $updateLog.Text = "Couldn't update (offline, or files changed here):`r`n" + $out.Trim()
        return
    }
    $new = (git -C $root rev-parse HEAD 2>$null)
    if ($old -eq $new) {
        $updateStatus.Text = 'Code: up to date'
        $updateStatus.ForeColor = $green
        $updateLog.Text = "Recent changes:`r`n" + (Get-Changes '-5').Text
    } else {
        $updateStatus.Text = 'Code: just updated'
        $updateStatus.ForeColor = $green
        $updateLog.Text = "Just updated:`r`n" + (Get-Changes "$old..$new").Text
    }
}
$checkAgain.Add_LinkClicked({ Update-Plugins })

# (friends) The launcher itself and each plugin, from GitHub with no git: scripts\update-plugins.ps1 in the background
# (no window), then Finish-Friends shows what it did
$script:updater = $null
$script:updateBefore = $null
function Update-Friends {
    if ($script:updater -and -not $script:updater.HasExited) { return }
    $updateStatus.Text = 'Code: checking...'
    $updateStatus.ForeColor = $textColor
    $script:updateBefore = Get-Head
    $updater = Join-Path $root 'scripts\update-plugins.ps1'
    $script:updater = Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass',
        '-WindowStyle', 'Hidden', '-File', "`"$updater`"", '-Root', "`"$root`"" -WindowStyle Hidden -PassThru
}

function Finish-Friends {
    $script:updater = $null
    $result = $null
    try { $result = Get-Content (Join-Path $root '.update-result.json') -Raw | ConvertFrom-Json } catch { }
    $after = Get-Head
    $problems = if ($result) { @($result.problems | Where-Object { $_ }) } else { @('the update') }
    if ($problems) {
        $updateStatus.Text = "Code: couldn't update"
        $updateStatus.ForeColor = $red
        $updateLog.Text = "Couldn't update " + ($problems -join ', ') + " (offline? GitHub busy?): it tries again in a while.`r`n" +
            (Get-FriendsChanges $null).Text
    } elseif ($script:updateBefore -eq $after) {
        $updateStatus.Text = 'Code: up to date'
        $updateStatus.ForeColor = $green
        $updateLog.Text = "Recent changes:`r`n" + (Get-FriendsChanges $null).Text
    } else {
        $updateStatus.Text = 'Code: just updated'
        $updateStatus.ForeColor = $green
        $updateLog.Text = "Just updated:`r`n" + (Get-FriendsChanges $script:updateBefore).Text
    }
    if ($result -and @($result.changed) -contains 'launcher') {
        $updateLog.Text = "The launcher itself was updated: close it and open it again to use the new one.`r`n" + $updateLog.Text
    }
    Check-RunningVersion
    Update-Versions
    Check-Setup
    if ($script:launchAfterUpdate) {
        $script:launchAfterUpdate = $false
        Start-RuneLite
    }
}

# ---- Setup checklist: RuneLite, its session setting, the saved session, Java, git ----
$runeLiteChoice = Join-Path $root '.launch-runelite'
$shell = New-Object -ComObject WScript.Shell

# Where RuneLite is: the one you picked with Find it..., its usual folders, its uninstall entry, its Start menu
# shortcuts, a running RuneLite, or under the Jagex Launcher's folder. Null if none of them has it.
function Find-RuneLite {
    $candidates = New-Object System.Collections.Generic.List[string]
    if (Test-Path $runeLiteChoice) { $candidates.Add((Get-Content $runeLiteChoice -Raw).Trim()) }
    foreach ($base in @($env:LOCALAPPDATA, $env:ProgramFiles, ${env:ProgramFiles(x86)})) {
        if ($base) { $candidates.Add((Join-Path $base 'RuneLite\RuneLite.exe')) }
    }
    foreach ($key in @('HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                       'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                       'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*')) {
        Get-ItemProperty $key -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -like 'RuneLite*' } | ForEach-Object {
            if ($_.InstallLocation) { $candidates.Add((Join-Path $_.InstallLocation 'RuneLite.exe')) }
            if ($_.DisplayIcon) { $candidates.Add(($_.DisplayIcon -split ',')[0].Trim('"')) }
        }
    }
    foreach ($menu in @((Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs'),
                        (Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs'))) {
        Get-ChildItem $menu -Recurse -Filter 'RuneLite*.lnk' -ErrorAction SilentlyContinue | ForEach-Object {
            $candidates.Add($shell.CreateShortcut($_.FullName).TargetPath)
        }
    }
    Get-Process -Name 'RuneLite' -ErrorAction SilentlyContinue | ForEach-Object { if ($_.Path) { $candidates.Add($_.Path) } }
    foreach ($jagex in @((Join-Path ${env:ProgramFiles(x86)} 'Jagex Launcher'), (Join-Path $env:ProgramFiles 'Jagex Launcher'),
                         (Join-Path $env:ProgramData 'Jagex'))) {
        if ($jagex -and (Test-Path $jagex)) {
            Get-ChildItem $jagex -Recurse -Depth 4 -Filter 'RuneLite.exe' -ErrorAction SilentlyContinue |
                ForEach-Object { $candidates.Add($_.FullName) }
        }
    }
    return $candidates | Where-Object { $_ -and ($_ -like '*RuneLite.exe') -and (Test-Path $_) } | Select-Object -First 1
}

$script:rlExe = $null
$script:rlSettings = $null
$jagexLauncher = @(
    (Join-Path ${env:ProgramFiles(x86)} 'Jagex Launcher\JagexLauncher.exe'),
    (Join-Path $env:ProgramFiles 'Jagex Launcher\JagexLauncher.exe')
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
$flag = '--insecure-write-credentials'

# Each row: its mark, its text, its fix button (the Setup card folds to "All set" when every row is ticked)
$rows = $ui.Rows
$tip = New-Object System.Windows.Forms.ToolTip

function Set-Row($key, $ok, $text, $buttonText = $null, $hint = $null) {
    $row = $rows[$key]
    $row.Mark.Text = if ($ok) { [char]0x2714 } else { [char]0x2716 }
    $row.Mark.ForeColor = if ($ok) { $green } else { $red }
    $row.Text.Text = $text
    $tip.SetToolTip($row.Text, $hint)
    $row.Button.Visible = [bool]$buttonText
    if ($buttonText) { $row.Button.Text = $buttonText }
}

# The client arguments "RuneLite (configure)" saved: Ok is false when its settings can't be read. (Wrapped in a
# hashtable: PowerShell would turn an empty list returned on its own into $null.)
function Get-ClientArguments {
    if (-not $script:rlSettings -or -not (Test-Path $script:rlSettings)) { return @{ Ok = $true; Args = @() } }
    try {
        $json = Get-Content $script:rlSettings -Raw | ConvertFrom-Json
        return @{ Ok = $true; Args = @($json.clientArguments | Where-Object { $_ }) }
    } catch {
        return @{ Ok = $false; Args = @() }
    }
}

# Only reads the character's name and when the file was saved; the session itself is never shown
# (friends) The launcher's own Java (java\), when Windows has none
function Get-JavaHome {
    $home17 = Join-Path $root 'java'
    if (Test-Path (Join-Path $home17 'bin\java.exe')) { return $home17 }
    return $null
}

$script:javaJob = $null
function Start-JavaDownload {
    if ($script:javaJob -and -not $script:javaJob.HasExited) { return }
    $getter = Join-Path $root 'scripts\get-java.ps1'
    $script:javaJob = Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass',
        '-WindowStyle', 'Hidden', '-File', "`"$getter`"", '-Root', "`"$root`"" -WindowStyle Hidden -PassThru
}

function Check-Setup {
    $script:rlExe = Find-RuneLite
    $hasRuneLite = [bool]$script:rlExe
    if ($hasRuneLite) {
        # RuneLite keeps its launcher settings (what configure saves) next to RuneLite.exe
        $script:rlSettings = Join-Path (Split-Path $script:rlExe -Parent) 'settings.json'
        Set-Row 'runelite' $true 'RuneLite is installed.' $null $script:rlExe
    } else {
        $script:rlSettings = $null
        Set-Row 'runelite' $false "Couldn't find RuneLite." 'Find it...' 'Point to RuneLite.exe, or get RuneLite from runelite.net if you need it'
    }

    $arguments = Get-ClientArguments
    if (-not $arguments.Ok) {
        Set-Row 'flag' $false "Couldn't read RuneLite's settings." 'Open configure' $script:rlSettings
    } elseif ($arguments.Args -contains $flag) {
        Set-Row 'flag' $true 'Session saving is on in RuneLite (configure).' $null $flag
    } elseif ($hasRuneLite) {
        Set-Row 'flag' $false "Session saving is off in RuneLite (configure)." 'Turn it on' "Adds $flag to RuneLite's client arguments"
    } else {
        Set-Row 'flag' $false 'Session saving: install RuneLite first.'
    }

    if (Test-Path $creds) {
        $who = (Select-String -Path $creds -Pattern '^JX_DISPLAY_NAME=(.*)$' | Select-Object -First 1)
        $name = if ($who) { $who.Matches[0].Groups[1].Value } else { 'your account' }
        $saved = (Get-Item $creds).LastWriteTime.ToString('g')
        Set-Row 'session' $true "Session saved for $name ($saved)." 'Refresh it' "If RuneLite asks for an email and password instead of showing Play, it has expired: refresh it"
    } else {
        Set-Row 'session' $false 'No Jagex session saved yet.' 'Save one' 'Log in once through the Jagex Launcher to save it'
    }

    if (Get-Command java -ErrorAction SilentlyContinue) {
        $version = (cmd /c 'java -version 2>&1' | Select-Object -First 1)
        Set-Row 'java' $true "Java: $version" $null
    } elseif (Get-JavaHome) {
        Set-Row 'java' $true "Java: Snuffy's own (in the launcher's folder)" $null (Get-JavaHome)
    } elseif ($friends) {
        # No Java: a portable one for the launcher, fetched once in the background
        $download = Join-Path $root '.java-download.txt'
        $said = if (Test-Path $download) { (Get-Content $download -Raw).Trim() } else { '' }
        if ($said -like 'failed*' -and -not ($script:javaJob -and -not $script:javaJob.HasExited)) {
            Set-Row 'java' $false "Couldn't get Java: try again." 'Try again' $said
        } else {
            Start-JavaDownload
            Set-Row 'java' $false 'Getting Java (once, about 190 MB)...' $null 'A portable Java 17 for the launcher, in its own folder'
        }
    } else {
        Set-Row 'java' $false "Java isn't installed (needed to build)." 'Get Java' 'Install a JDK 11 or newer (Temurin 17 recommended)'
    }

    if ($friends) {
        Set-Row 'git' $true 'Updates come straight from GitHub.'
    } elseif (Get-Command git -ErrorAction SilentlyContinue) {
        Set-Row 'git' $true 'git is installed (for updates).'
    } else {
        Set-Row 'git' $false "git isn't installed (no updates)." 'Get git'
    }
    $launch.Enabled = [bool](Get-Command java -ErrorAction SilentlyContinue) -or [bool](Get-JavaHome)
    $ui.UpdateSetupSummary()
}

# Point to RuneLite.exe (remembered), or get RuneLite
$rows['runelite'].Button.Add_Click({
    $dialog = New-Object System.Windows.Forms.OpenFileDialog
    $dialog.Title = 'Where is RuneLite.exe?'
    $dialog.Filter = 'RuneLite|RuneLite.exe|Programs|*.exe'
    if ($dialog.ShowDialog() -eq 'OK') {
        Set-Content -Path $runeLiteChoice -Value $dialog.FileName
    } elseif ([System.Windows.Forms.MessageBox]::Show("Don't have RuneLite? Open runelite.net to get it?", "Snuffy's", 'YesNo') -eq 'Yes') {
        Start-Process 'https://runelite.net/'
    }
    Check-Setup
})
$rows['java'].Button.Add_Click({
    if ($friends) {
        Remove-Item (Join-Path $root '.java-download.txt') -ErrorAction SilentlyContinue
        Check-Setup
    } else {
        Start-Process 'https://adoptium.net/temurin/releases/?version=17'
    }
})
$rows['git'].Button.Add_Click({ Start-Process 'https://git-scm.com/download/win' })

# Turn on session saving: adds the argument to RuneLite's own settings (backed up first), or opens configure
$rows['flag'].Button.Add_Click({
    if (Get-Process -Name 'RuneLite' -ErrorAction SilentlyContinue) {
        [System.Windows.Forms.MessageBox]::Show('Close RuneLite first, then try again.', "Snuffy's") | Out-Null
        return
    }
    $answer = [System.Windows.Forms.MessageBox]::Show(
        "Add $flag to RuneLite's client arguments?`n`nThat's the setting under Start menu > RuneLite (configure) > Client arguments. It makes RuneLite save your Jagex session when you launch it from the Jagex Launcher, so the dev client can log in. A backup of RuneLite's settings is kept (settings.json.bak).`n`nNo: open RuneLite (configure) to add it yourself.",
        'Turn on session saving', 'YesNoCancel')
    if ($answer -eq 'Yes') {
        try {
            if (Test-Path $script:rlSettings) {
                Copy-Item $script:rlSettings "$script:rlSettings.bak" -Force
                $json = Get-Content $script:rlSettings -Raw | ConvertFrom-Json
            } else {
                $json = [pscustomobject]@{}
            }
            $current = @($json.clientArguments | Where-Object { $_ })
            $updated = @($current + $flag)
            if ($json.PSObject.Properties.Name -contains 'clientArguments') {
                $json.clientArguments = $updated
            } else {
                $json | Add-Member -NotePropertyName 'clientArguments' -NotePropertyValue $updated
            }
            # Without a byte order mark: RuneLite reads the file as plain UTF-8 and would reset its settings otherwise
            [System.IO.File]::WriteAllText($script:rlSettings, ($json | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding $false))
        } catch {
            [System.Windows.Forms.MessageBox]::Show("Couldn't change RuneLite's settings: $($_.Exception.Message)`n`nAdd it yourself in RuneLite (configure).", "Snuffy's") | Out-Null
            Start-Process $script:rlExe -ArgumentList '--configure'
        }
    } elseif ($answer -eq 'No') {
        Start-Process $script:rlExe -ArgumentList '--configure'
        [System.Windows.Forms.MessageBox]::Show("In the window that opened, add  $flag  to Client arguments and click Save.", "Snuffy's") | Out-Null
    }
    Check-Setup
})

# Save (or refresh) a session: open the Jagex Launcher, then watch for the file
$rows['session'].Button.Add_Click({
    if (-not ((Get-ClientArguments).Args -contains $flag)) {
        [System.Windows.Forms.MessageBox]::Show('Turn on session saving first (the row above).', "Snuffy's") | Out-Null
        return
    }
    if ($jagexLauncher) { Start-Process $jagexLauncher }
    [System.Windows.Forms.MessageBox]::Show(
        ($(if ($jagexLauncher) { 'The Jagex Launcher is opening.' } else { "Open the Jagex Launcher (it wasn't found in Program Files)." }) +
        "`n`nPick RuneLite as the game client, press Play and log in once. When RuneLite is open you can close it: this window ticks the row as soon as the session is saved."),
        'Save a Jagex session') | Out-Null
    $script:sessionBefore = if (Test-Path $creds) { (Get-Item $creds).LastWriteTime } else { $null }
    $watch.Start()
})
$watch = New-Object System.Windows.Forms.Timer
$watch.Interval = 2000
$watch.Add_Tick({
    if ((Test-Path $creds) -and ((Get-Item $creds).LastWriteTime -ne $script:sessionBefore)) {
        $watch.Stop()
        Check-Setup
    }
})

# ---- Plugins ----
$plugins = [ordered]@{
    'skilling' = 'Skilling Helper'
    'pvm'      = 'PvM/Slayer'
    'gemcrab'  = 'GemCrab Tracker'
    'tick'     = 'Tick Counter'
    'planner'  = 'Planner'
    'alerts'   = 'Alerts'
}
$last = 'toolbelt'
if (Test-Path $choiceFile) {
    $saved = (Get-Content $choiceFile -Raw).Trim()
    if ($saved) { $last = $saved }
}
$lastNames = $last.ToLower() -split '[,+ ]+'

# The Toolbelt or separate plugins (a two-way switch), and a switch for each separate one
$toolbelt = $ui.Toolbelt
$separate = $ui.Separate
$boxes = $ui.Boxes
foreach ($key in $plugins.Keys) {
    $boxes[$key].Checked = ($lastNames -contains $key) -or ($lastNames -contains 'separate')
}
$updateBoxes = { foreach ($key in @($boxes.Keys)) { $boxes[$key].Enabled = $separate.Checked -and -not $ui.IsRemoved($key) } }
$toolbelt.Add_CheckedChanged($updateBoxes)
$separate.Add_CheckedChanged($updateBoxes)
if ($lastNames -contains 'toolbelt') { $toolbelt.Checked = $true } else { $separate.Checked = $true }
& $updateBoxes

# (friends) Each plugin's remove button: deletes its download and stops its updates; + adds it back (downloads it)
function Toggle-Removed($key) {
    $removed = Get-Removed
    $plugin = $friendPlugins | Where-Object { $_.key -eq $key } | Select-Object -First 1
    if (-not $plugin) { return }
    if ($removed -contains $key) {
        Set-Content -Path $removedFile -Value @($removed | Where-Object { $_ -ne $key })
        $ui.SetRemoved($key, $false)
        & $updateBoxes
        Update-Plugins
        Update-Versions
        return
    }
    if (RuneLite-Running) {
        [System.Windows.Forms.MessageBox]::Show("Close RuneLite first, then remove $($plugin.name).", "Snuffy's") | Out-Null
        return
    }
    $answer = [System.Windows.Forms.MessageBox]::Show("Remove $($plugin.name)? Its download is deleted and it isn't updated; " +
        "your settings for it stay in RuneLite. Add it back any time with +.", "Snuffy's", 'YesNo')
    if ($answer -ne 'Yes') { return }
    $dir = Join-Path $pluginsHome $plugin.folder
    if (Test-Path $dir) {
        try {
            Remove-Item -Path $dir -Recurse -Force -ErrorAction Stop
        } catch {
            [System.Windows.Forms.MessageBox]::Show("Couldn't delete it: $($_.Exception.Message)", "Snuffy's") | Out-Null
            return
        }
    }
    Set-Content -Path $removedFile -Value @($removed + $key)
    $ui.SetRemoved($key, $true)
    & $updateBoxes
    Update-Versions
}
if ($friends) {
    foreach ($key in (Get-Removed)) { $ui.SetRemoved($key, $true) }
    & $updateBoxes
    foreach ($link in $ui.RemoveLinks.Values) {
        $link.Add_LinkClicked({ param($source, $info) Toggle-Removed ([string]$source.Tag) })
    }
}

# ---- Launch ----
# The big Launch button: it fills as RuneLite builds and starts (the status line above it says what's happening;
# the build runs hidden, its output in a log a click away)
$launch = $ui.Launch
$progress = $ui.Launch
$close = $ui.Close
$launchStatus = $ui.LaunchStatus
$showLog = $ui.ShowLog

$logFile = Join-Path $env:TEMP "snuffys-runelite.log"
$showLog.Add_LinkClicked({ if (Test-Path $logFile) { Start-Process notepad.exe -ArgumentList "`"$logFile`"" } })
$script:runner = $null
$script:logOffset = 0
$script:tasks = 0
$script:stage = 'idle'

function Set-Status($text, $color, $value) {
    $launchStatus.Text = $text
    $launchStatus.ForeColor = $color
    if ($value -ge 0) { $progress.Value = [Math]::Min(100, $value) } else { $progress.Value = 0 }
    $now = $progress.Value
    $launch.Text = if ($now -gt 0 -and $now -lt 100) { "$($text.TrimEnd('.'))  $now%" }
                   elseif ($now -ge 100) { 'RuneLite is running' } else { 'Launch RuneLite' }
}

# What's new in the log since last time (read without locking it: the build is still writing)
function Read-NewLog {
    if (-not (Test-Path $logFile)) { return '' }
    try {
        $stream = [System.IO.File]::Open($logFile, 'Open', 'Read', 'ReadWrite')
        try {
            if ($stream.Length -le $script:logOffset) { return '' }
            $stream.Seek($script:logOffset, 'Begin') | Out-Null
            $reader = New-Object System.IO.StreamReader($stream)
            $text = $reader.ReadToEnd()
            $script:logOffset = $stream.Length
            return $text
        } finally {
            $stream.Dispose()
        }
    } catch {
        return ''
    }
}

function RuneLite-Window {
    return Get-Process -Name 'java', 'javaw' -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like '*RuneLite*' } | Select-Object -First 1
}

$watcher = New-Object System.Windows.Forms.Timer
$watcher.Interval = 500
$watcher.Add_Tick({
    $new = Read-NewLog
    if ($new) {
        $script:tasks += ([regex]::Matches($new, '(?m)^> Task :')).Count
        if ($new -match 'BUILD FAILED|FAILURE: Build failed') { $script:stage = 'failed' }
        elseif ($script:stage -eq 'building' -and $new -match "Loading Snuffy's") { $script:stage = 'starting' }
    }
    switch ($script:stage) {
        'building' { Set-Status "Building Snuffy's plugins..." $textColor ([Math]::Min(65, 5 + 7 * $script:tasks)) }
        'starting' {
            if (RuneLite-Window) {
                $script:stage = 'running'
                Set-Status 'RuneLite is running.' $green 100
                Check-RunningVersion
                Update-Versions
            } else {
                Set-Status 'Starting RuneLite...' $textColor ([Math]::Min(95, [Math]::Max($progress.Value, 70) + 1))
            }
        }
    }
    if ($script:stage -eq 'failed') {
        $watcher.Stop()
        Set-Status "Couldn't build or start RuneLite: see the log." $red -1
        $showLog.Visible = $true
        $launch.Enabled = $true
        return
    }
    if ($script:runner -and $script:runner.HasExited) {
        $watcher.Stop()
        Check-RunningVersion
        Update-Versions
        $launch.Enabled = $true
        $relaunch.Enabled = $true
        if ($script:stage -eq 'running') {
            Set-Status 'RuneLite closed.' $textColor 0
        } else {
            Set-Status "RuneLite stopped before it opened: see the log." $red -1
            $showLog.Visible = $true
        }
        $script:stage = 'idle'
    }
})

function Start-RuneLite {
    if ($separate.Checked -and -not ($boxes.Values | Where-Object { $_.Checked })) {
        [System.Windows.Forms.MessageBox]::Show('Tick at least one plugin, or pick the Toolbelt.', "Snuffy's") | Out-Null
        return
    }
    $load = if ($toolbelt.Checked) { 'toolbelt' } else { ($plugins.Keys | Where-Object { $boxes[$_].Checked }) -join ',' }
    Set-Content -Path $choiceFile -Value $load
    # Hidden: no console window; everything it prints goes to the log
    Set-Content -Path $logFile -Value ''
    $script:logOffset = 0
    $script:tasks = 0
    $script:stage = 'building'
    $showLog.Visible = $true
    $launch.Enabled = $false
    Set-Status "Building Snuffy's plugins..." $textColor 5
    Set-Content -Path $runningFile -Value (Get-Head)
    $script:warnedFor = $null
    if ($friends -and $script:updater -and -not $script:updater.HasExited) {
        # (an update is putting new files in place: launch once it's done)
        $script:launchAfterUpdate = $true
        Set-Status "Updating Snuffy's plugins first..." $textColor 3
        return
    }
    if ($friends) {
        # The chosen plugins' classes (the Toolbelt on its own), built from the downloaded repositories by runner\
        $keys = $load -split ','
        $classes = @($friendPlugins | Where-Object { $keys -contains $_.key -and (Test-Path (Join-Path $pluginsHome $_.folder)) } |
            ForEach-Object { $_.class }) -join ','
        if (-not $classes) {
            Set-Status "Snuffy's plugins aren't downloaded yet: press Check again (it needs git)." $red 0
            $launch.Enabled = $true
            return
        }
        $javaHome = Get-JavaHome
        $useJava = if ($javaHome -and -not (Get-Command java -ErrorAction SilentlyContinue)) { "set `"JAVA_HOME=$javaHome`" && set `"PATH=$javaHome\bin;%PATH%`" && " } else { '' }
        $command = "$useJava" + "gradlew.bat --console=plain runSnuffys `"-Pclasses=$classes`" > `"$logFile`" 2>&1"
        $script:runner = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $command -WorkingDirectory (Join-Path $root 'runner') -WindowStyle Hidden -PassThru
    } else {
        $command = "gradlew.bat --console=plain runBrad `"-Pload=$load`" > `"$logFile`" 2>&1"
        $script:runner = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $command -WorkingDirectory $root -WindowStyle Hidden -PassThru
    }
    $watcher.Start()
    $relaunch.Enabled = $true
    $relaunch.Font = New-Object System.Drawing.Font('Segoe UI', 9)
}
$launch.Add_Click({ Start-RuneLite })

function RuneLite-Running {
    return $script:runner -and -not $script:runner.HasExited
}

# ---- Relaunch: close RuneLite (as its X button does, forced if it hangs), update, launch again ----
$relaunch = $ui.Relaunch
$script:closingSince = $null
$closer = New-Object System.Windows.Forms.Timer
$closer.Interval = 500
# RuneLite's exit warning (its Exit warning setting): Relaunch answers OK for you. Only a window titled "Exit"
# belonging to RuneLite's own process, brought to the front first, so Enter can't land anywhere else
function Confirm-Exit {
    $window = RuneLite-Window
    if (-not $window) { return }
    $hwnd = [IntPtr]::Zero
    while (($hwnd = [Snuffys.Win]::FindWindowEx([IntPtr]::Zero, $hwnd, [NullString]::Value, 'Exit')) -ne [IntPtr]::Zero) {
        $owner = 0
        [void][Snuffys.Win]::GetWindowThreadProcessId($hwnd, [ref]$owner)
        if ($owner -eq $window.Id -and [Snuffys.Win]::SetForegroundWindow($hwnd)) {
            Start-Sleep -Milliseconds 150
            [System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
            return
        }
    }
}

# Whether any RuneLite is open: the one we started, or one opened before this window
function RuneLite-Open {
    return (RuneLite-Running) -or [bool](RuneLite-Window)
}

$closer.Add_Tick({
    Confirm-Exit
    if (-not (RuneLite-Open)) {
        $closer.Stop()
        Update-Plugins
        Start-RuneLite
        return
    }
    # Still open after 20 seconds: end it and the build behind it
    if (((Get-Date) - $script:closingSince).TotalSeconds -gt 20) {
        if (RuneLite-Running) {
            & taskkill.exe /PID $script:runner.Id /T /F | Out-Null
        } else {
            $window = RuneLite-Window
            if ($window) { & taskkill.exe /PID $window.Id /T /F | Out-Null }
        }
    }
})
$relaunch.Add_Click({
    if (-not (RuneLite-Open)) {
        Update-Plugins
        Start-RuneLite
        return
    }
    Set-Status 'Closing RuneLite...' $textColor 0
    $relaunch.Enabled = $false
    $window = RuneLite-Window
    if ($window) { [void]$window.CloseMainWindow() }
    $script:closingSince = Get-Date
    $closer.Start()
})

# ---- Updates while the launcher's open: fetched every minute in the background (the window never freezes),
# pulled as soon as there's one; and the open RuneLite checked against the version it was built from ----

# The branch updates come from: its upstream, else origin's branch of the same name
function Get-Upstream {
    $up = (git -C $root rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>$null)
    if ($LASTEXITCODE -eq 0 -and $up) { return $up.Trim() }
    $branch = (git -C $root rev-parse --abbrev-ref HEAD 2>$null)
    if ($branch) { return "origin/$($branch.Trim())" }
    return $null
}

# The open RuneLite's version: this launcher's, or (opened before this window) the one saved at its launch
function Running-Commit {
    if (-not ((RuneLite-Running) -or (RuneLite-Window))) { return $null }
    if (Test-Path $runningFile) { return (Get-Content $runningFile -ErrorAction SilentlyContinue | Select-Object -First 1) }
    return $null
}

$tray = New-Object System.Windows.Forms.NotifyIcon
$tray.Icon = if ($form.Icon) { $form.Icon } else { [System.Drawing.SystemIcons]::Information }
$tray.Text = "Snuffy's Launcher"
$script:balloonFor = $null
$tray.Add_BalloonTipClicked({
    if ($script:balloonFor -eq 'hub') { $ui.ShowHub() }
    $form.Activate()
})
$form.Add_FormClosed({ $tray.Visible = $false; $tray.Dispose() })
$script:fetch = $null
$script:lastFetch = [DateTime]::MinValue
$script:warnedFor = $null

# Says when the open RuneLite is older than the plugins here (once a notification per new version)
# What changed in a range of commits ("<old>..HEAD", or "-5" for the latest five), one line each with what it
# touches: the plugins it changes (RuneLite needs a Relaunch for those), or the launcher and docs only (it doesn't).
# Needed: how many change what RuneLite runs
$pluginNames = @{ 'skilling-helper' = 'Skilling Helper'; 'pvm-slayer' = 'PvM/Slayer'; 'gemcrab-tracker' = 'GemCrab Tracker';
    'tick-counter' = 'Tick Counter'; 'planner' = 'Planner'; 'alerts' = 'Alerts'; 'toolbelt' = 'Toolbelt' }
function Get-Changes($range) {
    $lines = New-Object System.Collections.Generic.List[string]
    $needed = 0
    foreach ($entry in @(git -C $root log --max-count=30 --format='%h %s' $range 2>$null)) {
        if (-not $entry) { continue }
        $hash, $subject = $entry -split ' ', 2
        if ($subject.Length -gt 150) { $subject = $subject.Substring(0, 147) + '...' }
        $files = @(git -C $root diff-tree --no-commit-id --name-only -r $hash 2>$null)
        $code = @($files | Where-Object { $_ -match '^(src/main/|plugins/|build\.gradle|settings\.gradle|gradle/)' }).Count -gt 0
        $folders = @($files | Where-Object { $_ -match '^plugins/[^/]+/' } | ForEach-Object { ([regex]::Match($_, '^plugins/([^/]+)/')).Groups[1].Value } |
            Select-Object -Unique)
        # (the Toolbelt has every plugin in it: named only when it's the one that changed)
        $names = @($folders | Where-Object { $_ -ne 'toolbelt' } | ForEach-Object { if ($pluginNames[$_]) { $pluginNames[$_] } else { $_ } })
        if (-not $names -and $folders -contains 'toolbelt') { $names = @('Toolbelt') }
        if ($code) {
            $needed++
            $what = if ($names) { $names -join ', ' } else { 'every plugin' }
            $lines.Add("- $subject`r`n      -> $what (Relaunch to get it)")
        } else {
            $lines.Add("- $subject`r`n      -> launcher or docs only (no Relaunch needed)")
        }
    }
    return @{ Text = ($lines -join "`r`n"); Needed = $needed; All = $lines.Count }
}

function Check-RunningVersion {
    $running = Running-Commit
    $head = Get-Head
    # The RuneLite chip: not running, running this code, running older code (how far behind), or running from
    # before the launcher kept track
    if (-not ((RuneLite-Running) -or (RuneLite-Window))) {
        $runningStatus.Text = 'RuneLite: not running'
        $runningStatus.ForeColor = $textColor
    } elseif (-not $running -or -not $head) {
        $runningStatus.Text = 'RuneLite: running'
        $runningStatus.ForeColor = $textColor
    } elseif ($running.Trim() -eq $head.Trim()) {
        $runningStatus.Text = 'RuneLite: latest code'
        $runningStatus.ForeColor = $green
    }
    $changes = $null
    if ($running -and $head -and $running.Trim() -ne $head.Trim()) {
        $changes = if ($friends) { Get-FriendsChanges $running.Trim() } else { Get-Changes "$($running.Trim())..HEAD" }
        # What's new: what the open RuneLite is missing, and which of it needs a Relaunch
        $updateLog.Text = $(if ($changes.Needed -gt 0) {
                "The open RuneLite is missing $($changes.All) update$(if ($changes.All -ne 1) { 's' }); $($changes.Needed) change$(if ($changes.Needed -eq 1) { 's' }) plugins (press Relaunch):"
            } else {
                "RuneLite has every plugin change. Since it opened, only the launcher or docs changed:"
            }) + "`r`n" + $changes.Text
        $updateLog.Select(0, 0)
        if ($changes.Needed -eq 0) {
            $runningStatus.Text = 'RuneLite: latest plugins'
            $runningStatus.ForeColor = $green
        }
    }
    if (-not $changes -or $changes.Needed -eq 0) {
        if ($script:warnedFor) {
            $script:warnedFor = $null
            $relaunch.Font = New-Object System.Drawing.Font('Segoe UI', 9)
        }
        return
    }
    $runningStatus.Text = "RuneLite: $($changes.Needed) update$(if ($changes.Needed -ne 1) { 's' }) behind, Relaunch"
    $runningStatus.ForeColor = $red
    $relaunch.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
    $relaunch.Enabled = $true
    if ($script:warnedFor -ne $head) {
        $script:warnedFor = $head
        $tray.Visible = $true
        $script:balloonFor = 'update'
        $tray.ShowBalloonTip(10000, "Snuffy's plugins updated", 'RuneLite is running an older version of your plugins. Open the launcher and press Relaunch to use the new one.', 'Info')
    }
}

# Each plugin's version beside its name (Play and Dev): the last one pushed to its repository (Push to repos keeps
# them in .publish-cache), with a + when the code RuneLite runs (or, closed, your copy) has changed since then
$hubKeys = [ordered]@{ 'skilling-helper' = 'skilling'; 'pvm-slayer' = 'pvm'; 'gemcrab-tracker' = 'gemcrab';
    'tick-counter' = 'tick'; 'planner' = 'planner'; 'alerts' = 'alerts'; 'toolbelt' = 'toolbelt' }
function Update-Versions {
    if ($friends) {
        foreach ($plugin in $friendPlugins) {
            $gradle = Join-Path (Join-Path $pluginsHome $plugin.folder) 'build.gradle'
            $found = if (Test-Path $gradle) { Select-String -Path $gradle -Pattern "^version = '([^']+)'" | Select-Object -First 1 } else { $null }
            if ($found) {
                $version = $found.Matches[0].Groups[1].Value
                $ui.SetPluginVersion($plugin.key, $null, "v$version", "Version $version, from its GitHub repository")
            } else {
                $ui.SetPluginVersion($plugin.key, $null, '', 'Not downloaded yet')
            }
        }
        return
    }
    if (-not (Test-Path $publishConfig)) { return }
    $config = Get-Content $publishConfig -Raw | ConvertFrom-Json
    $code = Running-Commit
    $which = 'the RuneLite that is open'
    if (-not $code) {
        $code = (git -C $root rev-parse HEAD 2>$null)
        $which = 'your copy of the code'
    }
    foreach ($folder in $config.plugins.PSObject.Properties.Name) {
        $key = $hubKeys[$folder]
        $cache = Join-Path $root ".publish-cache\$($config.plugins.$folder.hubName)"
        $gradle = Join-Path $cache 'build.gradle'
        $version = $null
        if (Test-Path $gradle) {
            $found = Select-String -Path $gradle -Pattern "^version = '([^']+)'" | Select-Object -First 1
            if ($found) { $version = $found.Matches[0].Groups[1].Value }
        }
        if (-not $version) {
            $ui.SetPluginVersion($key, $folder, '', 'Not pushed from this PC yet: Push to repos gives it a version')
            continue
        }
        # The commit it was last pushed from, and whether this plugin's code changed after it
        $from = (git -C $cache log -1 --grep='From BradsMultiTool' --format=%B 2>$null | Out-String)
        $changed = $false
        if ($code -and $from -match 'BradsMultiTool ([0-9a-f]{7,40})') {
            $pushed = $Matches[1]
            git -C $root cat-file -e "$pushed^{commit}" 2>$null
            if ($LASTEXITCODE -eq 0) {
                git -C $root diff --quiet $pushed $code.Trim() -- "plugins/$folder" 2>$null
                $changed = $LASTEXITCODE -eq 1
            }
        }
        $tip = if ($changed) { "v$version is the last version pushed to its repository; $which has changes since then (Push to repos makes the next version)" }
                else { "v${version}: the last version pushed to its repository, the same as $which" }
        $ui.SetPluginVersion($key, $folder, "v$version$(if ($changed) { '+' })", $tip)
    }
}

$autoUpdate = New-Object System.Windows.Forms.Timer
$autoUpdate.Interval = 5000
$autoUpdate.Add_Tick({
    if ($friends) {
        if ($script:javaJob -and $script:javaJob.HasExited) {
            $script:javaJob = $null
            Check-Setup
        }
        if ($script:updater -and $script:updater.HasExited) {
            Finish-Friends
            return
        }
        if (-not $script:updater -and ((Get-Date) - $script:lastFetch).TotalMinutes -ge 15) {
            $script:lastFetch = Get-Date
            Update-Friends
        }
        Check-RunningVersion
        return
    }
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { return }
    # Not while the Dev tab is pushing: it publishes the commit it started with
    if ($script:publishRun -and -not $script:publishRun.HasExited) { return }
    if ($script:fetch) {
        if (-not $script:fetch.HasExited) { return }
        $script:fetch = $null
        $behindAny = $false
        foreach ($dir in @($root)) {
            $upstream = (git -C $dir rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>$null)
            if (-not $upstream) { continue }
            $behind = (git -C $dir rev-list --count "HEAD..$($upstream.Trim())" 2>$null)
            if ($LASTEXITCODE -eq 0 -and [int]$behind -gt 0) { $behindAny = $true }
        }
        if ($behindAny) { Update-Plugins }
        Check-RunningVersion
        Update-Versions
        return
    }
    if (((Get-Date) - $script:lastFetch).TotalSeconds -ge 60) {
        $script:lastFetch = Get-Date
        $script:fetch = Start-Process -FilePath 'git' -ArgumentList '-C', "`"$root`"", 'fetch', '--quiet' -WindowStyle Hidden -PassThru
    }
})
$autoUpdate.Start()

# Desktop shortcut: opens the launcher straight through PowerShell, minimised and hidden (no cmd window, unlike the
# .bat); made with the Toolbelt's icon. Shortcuts to the .bat (or to this) are brought up to date when the launcher runs
$bat = Join-Path $root $(if ($friends) { 'Launch-Snuffys.bat' } else { 'Launch-BradsMultiTool.bat' })
$launcherScript = Join-Path $root 'scripts\launcher.ps1'
$powershellExe = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$launcherArgs = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$launcherScript`""
$desktop = [Environment]::GetFolderPath('Desktop')
function Set-LauncherShortcut($link) {
    $link.TargetPath = $powershellExe
    $link.Arguments = $launcherArgs
    $link.WorkingDirectory = $root
    $link.WindowStyle = 7
    $link.IconLocation = "$icon,0"
    $link.Save()
}
function Set-ShortcutIcons {
    Get-ChildItem $desktop -Filter '*.lnk' -ErrorAction SilentlyContinue | ForEach-Object {
        $link = $shell.CreateShortcut($_.FullName)
        $ours = ($link.TargetPath -ieq $bat) -or ($link.TargetPath -ieq $powershellExe -and $link.Arguments -like "*$launcherScript*")
        if ($ours -and ($link.TargetPath -ine $powershellExe -or $link.Arguments -ne $launcherArgs -or $link.WindowStyle -ne 7 -or $link.IconLocation -notlike "$icon*")) {
            Set-LauncherShortcut $link
        }
    }
}
$shortcut = $ui.Shortcut
$shortcut.Add_LinkClicked({
    Set-LauncherShortcut ($shell.CreateShortcut((Join-Path $desktop "Snuffy's Launcher.lnk")))
    [System.Windows.Forms.MessageBox]::Show("Snuffy's Launcher is on your desktop.", "Snuffy's") | Out-Null
})

# ---- Dev tab: push the plugins to their own repositories, then publish them to RuneLite (Plugin Hub pull
# request). Runs scripts\publish_plugins.py (repositories in publish.json; docs\ROLLOUT.md) hidden, output live. ----

# Python 3: "py -3" (the Windows launcher) or "python"
function Find-Python {
    # The comma keeps a one-item list a list (PowerShell would hand back the bare string)
    if (Get-Command py -ErrorAction SilentlyContinue) { return ,@('py', '-3') }
    if (Get-Command python -ErrorAction SilentlyContinue) { return ,@('python') }
    return $null
}

$publishConfig = Join-Path $root 'publish.json'
$devTicks = [ordered]@{}
if (Test-Path $publishConfig) {
    $config = Get-Content $publishConfig -Raw | ConvertFrom-Json
    foreach ($folder in $config.plugins.PSObject.Properties.Name) {
        $devTicks[$folder] = $ui.AddDevTick($folder, [bool]$config.plugins.$folder.publish)
    }
}
# 1 Push (Check is a dry run), 2 Publish, 3 Status: where each Plugin Hub pull request is (open, published or
# closed, its build check, reviewers' comments), a card each
$devCheck = $ui.DevCheck
$devPush = $ui.DevPush
$devPublish = $ui.DevPublish
$devStatusButton = $ui.DevStatusButton
$devStatus = $ui.DevStatus
$devOut = $ui.DevOut
$prLink = $ui.PrLink
# One pull request per plugin: opens each page the last step printed
$prLink.Add_LinkClicked({ foreach ($url in @($script:prUrls)) { Start-Process $url } })
$devGuide = $ui.DevGuide
$devGuide.Add_LinkClicked({ Start-Process notepad.exe -ArgumentList "`"$(Join-Path $root 'docs\ROLLOUT.md')`"" })

$script:publishLog = Join-Path $env:TEMP 'snuffys-publish.log'
$script:publishRun = $null
$script:prUrls = @()
$devPoll = New-Object System.Windows.Forms.Timer
$devPoll.Interval = 500
$devPoll.Add_Tick({
    if (Test-Path $script:publishLog) {
        try {
            $stream = [System.IO.File]::Open($script:publishLog, 'Open', 'Read', 'ReadWrite')
            $reader = New-Object System.IO.StreamReader($stream)
            $text = $reader.ReadToEnd()
            $stream.Dispose()
            if ($text -ne $devOut.Text) {
                $devOut.Text = $text
                $devOut.SelectionStart = $devOut.Text.Length
                $devOut.ScrollToCaret()
            }
            $found = [regex]::Matches($text, 'https://github\.com/runelite/plugin-hub/compare/\S+')
            if ($found.Count -gt 0) {
                $script:prUrls = @($found | ForEach-Object { $_.Value } | Select-Object -Unique)
                $prLink.Visible = $true
            }
        } catch { }
    }
    if ($script:publishRun -and $script:publishRun.HasExited) {
        $devPoll.Stop()
        $devCheck.Enabled = $true
        $devPush.Enabled = $true
        $devPublish.Enabled = $true
        $devStatusButton.Enabled = $true
        if ($script:publishRun.ExitCode -ne 0) {
            $devStatus.Text = 'Stopped: see the output below.'
            $devStatus.ForeColor = $red
            $ui.ShowDevOutput($true)
        } else {
            $devStatus.Text = 'Done.'
            $devStatus.ForeColor = $green
            $ui.ShowDevOutput($true)
        }
        $script:publishRun = $null
        Update-Versions
        # A publish changes the pull requests: check them again
        if ($script:devStep -eq 'hub') { $script:hubNext = Get-Date }
    }
})

# Runs one step: 'push' (with $dryRun for Check) or 'hub'
function Start-DevStep($step, $dryRun) {
    $python = Find-Python
    if (-not $python) {
        $answer = [System.Windows.Forms.MessageBox]::Show("This needs Python 3. Open python.org to get it?", "Snuffy's", 'YesNo')
        if ($answer -eq 'Yes') { Start-Process 'https://www.python.org/downloads/' }
        return
    }
    $chosen = @($devTicks.Keys | Where-Object { $devTicks[$_].Checked })
    # (the status check is for the pull request, not the ticked plugins)
    if ($step -eq 'status') { $chosen = @() }
    elseif (-not $chosen) {
        [System.Windows.Forms.MessageBox]::Show('Tick at least one plugin.', "Snuffy's") | Out-Null
        return
    }
    $level = $ui.Level()
    if (-not $dryRun -and $step -ne 'status') {
        $what = $(if ($step -eq 'push') { "Push " + ($chosen -join ', ') + " to their repositories (changed ones get a new $level version)?" }
                  else { "Get the Plugin Hub pull request ready for " + ($chosen -join ', ') + "?" })
        $answer = [System.Windows.Forms.MessageBox]::Show($what, "Snuffy's", 'YesNo')
        if ($answer -ne 'Yes') { return }
    }
    Set-Content -Path $script:publishLog -Value ''
    $devOut.Text = ''
    $script:devStep = $step
    $ui.ShowDevOutput($true)
    $prLink.Visible = $false
    $script:prUrls = @()
    $devCheck.Enabled = $false
    $devPush.Enabled = $false
    $devPublish.Enabled = $false
    $devStatusButton.Enabled = $false
    $devStatus.Text = $(if ($dryRun) { 'Checking...' } elseif ($step -eq 'push') { 'Pushing...' } elseif ($step -eq 'status') { 'Asking GitHub...' } else { 'Publishing...' })
    $devStatus.ForeColor = $textColor
    $pyArgs = @($python | Select-Object -Skip 1) + @('-u', 'scripts\publish_plugins.py', $step) + $chosen
    if ($step -eq 'push') { $pyArgs += @('--bump', $level) }
    if ($dryRun) { $pyArgs += '--dry-run' }
    $command = "`"$($python[0])`" " + ($pyArgs -join ' ') + " > `"$($script:publishLog)`" 2>&1"
    # cmd /c drops the first and last quote of what it runs: wrap the whole line in one more pair
    $script:publishRun = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "`"$command`"" -WorkingDirectory $root -WindowStyle Hidden -PassThru
    # Reading the handle now keeps the exit code readable once it ends (Windows PowerShell)
    $null = $script:publishRun.Handle
    $devPoll.Start()
}
$devCheck.Add_Click({ Start-DevStep 'push' $true })
$devPush.Add_Click({ Start-DevStep 'push' $false })
$devPublish.Add_Click({ Start-DevStep 'hub' $false })
$devStatusButton.Add_Click({ Start-HubCheck $true })

# ---- The Plugin Hub board: each plugin's pull request checked in the background (on opening, then every 10
# minutes signed in to GitHub, else hourly: GitHub allows 60 unsigned requests an hour), a card each; comments you
# haven't opened marked new, and a Windows notification for a new comment or a change (published, build failed) ----
$ui.SeenPath = Join-Path $root '.hub-seen'
$hubJson = Join-Path $env:TEMP 'snuffys-hub-status.json'
$hubErr = Join-Path $env:TEMP 'snuffys-hub-status.err'
$script:hubRun = $null
$script:hubNext = Get-Date
$script:hubManual = $false

function Start-HubCheck($manual) {
    if ($script:hubRun -and -not $script:hubRun.HasExited) { return }
    $python = Find-Python
    if (-not $python -or -not (Test-Path $publishConfig)) {
        $script:hubNext = (Get-Date).AddHours(1)
        if ($manual) { [System.Windows.Forms.MessageBox]::Show('Checking the Plugin Hub needs Python 3 and publish.json.', "Snuffy's") | Out-Null }
        return
    }
    $script:hubManual = $manual
    $ui.HubStatus.Text = 'Asking GitHub...'
    $ui.HubStatus.ForeColor = $textColor
    $devStatusButton.Enabled = $false
    $pyArgs = @($python | Select-Object -Skip 1) + @('-u', 'scripts\publish_plugins.py', 'status', '--json')
    $command = "`"$($python[0])`" " + ($pyArgs -join ' ') + " > `"$hubJson`" 2> `"$hubErr`""
    $script:hubRun = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "`"$command`"" -WorkingDirectory $root -WindowStyle Hidden -PassThru
}

function Read-HubCheck {
    $devStatusButton.Enabled = $true
    $script:hubRun = $null
    $data = $null
    try { $data = Get-Content $hubJson -Raw | ConvertFrom-Json } catch { }
    if (-not $data -or -not $data.ok) {
        $why = if ($data -and $data.problem) { $data.problem } else { ((Get-Content $hubErr -ErrorAction SilentlyContinue | Select-Object -Last 1) -join '') }
        $ui.HubStatus.Text = "Couldn't check: $why"
        $ui.HubStatus.ForeColor = $red
        $script:hubNext = (Get-Date).AddMinutes(30)
        return
    }
    $ui.BeginPullRequests()
    foreach ($pull in @($data.pulls | Where-Object { $_ })) {
        $comments = @($pull.comments | Where-Object { $_ })
        $earlier = (@($pull.earlier | Where-Object { $_ }) | Select-Object -First 2 | ForEach-Object {
            "#$($_.number) $(if ($_.state -eq 'merged') { 'published' } else { $_.state })" }) -join ', '
        if ($earlier) { $earlier = "Before: $earlier" }
        $ui.AddPullRequest([string]$pull.plugin, [int]$pull.number, [string]$pull.title, [string]$pull.url, [string]$pull.state,
            [string]$pull.check, [string]$pull.checkUrl, [string]$pull.date, [string]$earlier,
            [string[]]@($comments | ForEach-Object { [string]$_.id }), [string[]]@($comments | ForEach-Object { [string]$_.user }),
            [string[]]@($comments | ForEach-Object { [string]$_.date }), [string[]]@($comments | ForEach-Object { [string]$_.text }),
            [string[]]@($comments | ForEach-Object { [string]$_.url }))
    }
    $news = $ui.EndPullRequests()
    $minutes = if ($data.authenticated) { 10 } else { 60 }
    $script:hubNext = (Get-Date).AddMinutes($minutes)
    $count = @($data.pulls | Where-Object { $_ }).Count
    $ui.HubStatus.Text = "$count pull request$(if ($count -ne 1) { 's' }), checked $((Get-Date).ToString('t')); next at $($script:hubNext.ToString('t'))" +
        $(if (-not $data.authenticated) { ' (hourly: not signed in to GitHub)' } else { '' })
    $ui.HubStatus.ForeColor = $green
    if ($news) {
        $script:balloonFor = 'hub'
        $tray.Visible = $true
        $tray.ShowBalloonTip(10000, "Snuffy's Plugin Hub", $news, 'Info')
    }
}

$hubTimer = New-Object System.Windows.Forms.Timer
$hubTimer.Interval = 2000
$hubTimer.Add_Tick({
    if ($script:hubRun) {
        if ($script:hubRun.HasExited) { Read-HubCheck }
        return
    }
    if ((Get-Date) -ge $script:hubNext) { Start-HubCheck $false }
})
$hubTimer.Start()
if (-not (Test-Path $publishConfig)) {
    $devStatus.Text = 'publish.json is missing: update the launcher.'
    $devStatus.ForeColor = $red
    $devCheck.Enabled = $false
    $devPush.Enabled = $false
    $devPublish.Enabled = $false
    $devStatusButton.Enabled = $false
}



$form.Add_Shown({
    Write-Host 'Window shown'
    try {
        Set-ShortcutIcons
        Check-Setup
        Update-Plugins
        Check-RunningVersion
        Update-Versions
    } catch {
        Show-Problem "$($_.Exception.Message)`r`n`r`n$($_.InvocationInfo.PositionMessage)"
    }
    $script:lastFetch = Get-Date
    Write-Host 'Checks done'
})
Write-Host 'Window built, opening it'
try {
    [void]$form.ShowDialog()
} catch {
    Show-Problem ($_.Exception.ToString())
}
Write-Host "Window closed $(Get-Date)"
