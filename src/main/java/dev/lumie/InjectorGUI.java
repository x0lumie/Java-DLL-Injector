package dev.lumie;

import dev.lumie.utilities.ProcessInfo;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main GUI for the DLL Injector.
 *
 * Layout
 * ┌─────────────────────────────────────────────┐
 * │  Process List         [🔄 Refresh] [Filter] │
 * │  ┌───────────────────────────────────────┐  │
 * │  │ PID │ Process Name                    │  │
 * │  │ ... │ ...                             │  │
 * │  └───────────────────────────────────────┘  │
 * │  DLL Path: [____________________] [Browse]  │
 * │                      [  Inject  ]           │
 * │  ─── Log ──────────────────────────────── │
 * │  > Ready.                                   │
 * └─────────────────────────────────────────────┘
 */
public class InjectorGUI extends JFrame {

    // ── Colours / fonts ───────────────────────────────────────────────────────
    private static final Color BG_DARK     = new Color(18,  20,  24);
    private static final Color BG_PANEL    = new Color(26,  29,  35);
    private static final Color BG_TABLE    = new Color(22,  25,  31);
    private static final Color ACCENT      = new Color(0,  180, 120);
    private static final Color ACCENT_DARK = new Color(0,  140,  90);
    private static final Color TEXT_MAIN   = new Color(220, 225, 235);
    private static final Color TEXT_DIM    = new Color(120, 130, 150);
    private static final Color BORDER_CLR  = new Color(45,  50,  60);
    private static final Color ROW_SEL     = new Color(0,   80,  55);
    private static final Color LOG_OK      = new Color(80,  210, 130);
    private static final Color LOG_ERR     = new Color(240, 90,  80);
    private static final Color LOG_INFO    = new Color(140, 165, 200);

    private static final Font FONT_MONO  = new Font("Consolas",    Font.PLAIN, 12);
    private static final Font FONT_SMALL = new Font("Segoe UI",    Font.PLAIN, 11);
    private static final Font FONT_BOLD  = new Font("Segoe UI",    Font.BOLD,  13);
    private static final Font FONT_TITLE = new Font("Segoe UI",    Font.BOLD,  15);

    // ── State ─────────────────────────────────────────────────────────────────
    private final InjectionEngine engine    = new InjectionEngine();
    private final ExecutorService        executor  = Executors.newSingleThreadExecutor();
    private       List<ProcessInfo>      processes;

    // ── Widgets ───────────────────────────────────────────────────────────────
    private JTable              processTable;
    private DefaultTableModel   tableModel;
    private JTextField          filterField;
    private JTextField          dllPathField;
    private JTextPane           logPane;
    private JButton             injectButton;
    private JLabel              statusBar;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // ─────────────────────────────────────────────────────────────────────────
    public InjectorGUI() {
        super("DLL Injector");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(780, 640);
        setMinimumSize(new Dimension(640, 520));
        setLocationRelativeTo(null);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        buildUI();
        applyTheme();
        setVisible(true);

        // Load process list on startup
        refreshProcesses();
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);

        root.add(buildTitleBar(),   BorderLayout.NORTH);
        root.add(buildCenter(),     BorderLayout.CENTER);
        root.add(buildStatusBar(),  BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 10));
        bar.setBackground(BG_PANEL);
        bar.setBorder(new MatteBorder(0, 0, 1, 0, BORDER_CLR));

        JLabel icon  = new JLabel("💉");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));

        JLabel title = new JLabel("DLL Injector");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_MAIN);

        JLabel sub = new JLabel("CreateRemoteThread  ·  LoadLibraryA");
        sub.setFont(FONT_SMALL);
        sub.setForeground(TEXT_DIM);

        bar.add(icon);
        bar.add(title);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(sub);
        return bar;
    }

    private JSplitPane buildCenter() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildProcessPanel(), buildBottomPanel());
        split.setDividerLocation(320);
        split.setDividerSize(4);
        split.setBackground(BG_DARK);
        split.setBorder(null);
        split.setContinuousLayout(true);
        return split;
    }

    // ── Process panel ─────────────────────────────────────────────────────────

    private JPanel buildProcessPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(10, 10, 4, 10));

        // Header row
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(BG_DARK);

        JLabel lbl = new JLabel("Running Processes");
        lbl.setFont(FONT_BOLD);
        lbl.setForeground(TEXT_MAIN);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        controls.setBackground(BG_DARK);

        filterField = styledTextField("Filter by name...", 16);
        filterField.addActionListener(e -> applyFilter());

        JButton refresh = accentButton("⟳  Refresh");
        refresh.addActionListener(e -> refreshProcesses());

        controls.add(filterField);
        controls.add(refresh);

        header.add(lbl, BorderLayout.WEST);
        header.add(controls, BorderLayout.EAST);

        // Table
        String[] cols = {"PID", "Process Name"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        processTable = new JTable(tableModel);
        styleTable(processTable);
        processTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        processTable.getColumnModel().getColumn(0).setMaxWidth(100);

        JScrollPane scroll = darkScroll(processTable);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll,  BorderLayout.CENTER);
        return panel;
    }

    // ── Bottom panel (DLL selector + log) ─────────────────────────────────────

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(4, 10, 10, 10));

        panel.add(buildDllRow(),   BorderLayout.NORTH);
        panel.add(buildLogPanel(), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDllRow() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(BG_DARK);
        row.setBorder(new EmptyBorder(6, 0, 6, 0));

        JLabel lbl = new JLabel("DLL Path:");
        lbl.setFont(FONT_BOLD);
        lbl.setForeground(TEXT_MAIN);
        lbl.setPreferredSize(new Dimension(75, 28));

        dllPathField = styledTextField("C:\\path\\to\\your.dll", 999);

        JButton browse = accentButton("Browse…");
        browse.addActionListener(e -> browseDll());

        injectButton = new JButton("  ▶  Inject  ");
        injectButton.setFont(FONT_BOLD);
        injectButton.setForeground(Color.WHITE);
        injectButton.setBackground(ACCENT);
        injectButton.setFocusPainted(false);
        injectButton.setBorder(new CompoundBorder(
                new LineBorder(ACCENT_DARK, 1, true),
                new EmptyBorder(5, 18, 5, 18)));
        injectButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        injectButton.addActionListener(e -> doInject());
        injectButton.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { injectButton.setBackground(ACCENT_DARK); }
            @Override public void mouseExited (MouseEvent e) { injectButton.setBackground(ACCENT); }
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setBackground(BG_DARK);
        right.add(browse);
        right.add(injectButton);

        row.add(lbl,         BorderLayout.WEST);
        row.add(dllPathField, BorderLayout.CENTER);
        row.add(right,        BorderLayout.EAST);
        return row;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(BG_DARK);

        JLabel lbl = new JLabel("Log");
        lbl.setFont(FONT_BOLD);
        lbl.setForeground(TEXT_DIM);

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(BG_TABLE);
        logPane.setFont(FONT_MONO);
        logPane.setBorder(new EmptyBorder(6, 8, 6, 8));

        JScrollPane scroll = darkScroll(logPane);

        JButton clearBtn = dimButton("Clear");
        clearBtn.addActionListener(e -> logPane.setText(""));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_DARK);
        header.add(lbl, BorderLayout.WEST);
        header.add(clearBtn, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll,  BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        bar.setBackground(new Color(14, 16, 20));
        bar.setBorder(new MatteBorder(1, 0, 0, 0, BORDER_CLR));

        statusBar = new JLabel("Ready.");
        statusBar.setFont(FONT_SMALL);
        statusBar.setForeground(TEXT_DIM);
        bar.add(statusBar);
        return bar;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void refreshProcesses() {
        setStatus("Refreshing process list...");
        injectButton.setEnabled(false);

        executor.submit(() -> {
            processes = engine.listProcesses();
            SwingUtilities.invokeLater(() -> {
                populateTable(processes);
                setStatus("Loaded " + processes.size() + " processes.");
                injectButton.setEnabled(true);
            });
        });
    }

    private void applyFilter() {
        if (processes == null) return;
        String q = filterField.getText().trim().toLowerCase();
        List<ProcessInfo> filtered = q.isEmpty()
                ? processes
                : processes.stream()
                .filter(p -> p.getName().toLowerCase().contains(q)
                        || String.valueOf(p.getPid()).contains(q))
                .toList();
        populateTable(filtered);
        setStatus(filtered.size() + " process(es) shown.");
    }

    private void populateTable(List<ProcessInfo> list) {
        tableModel.setRowCount(0);
        for (ProcessInfo p : list) {
            tableModel.addRow(new Object[]{p.getPid(), p.getName()});
        }
    }

    private void browseDll() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select DLL to inject");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Dynamic Link Libraries (*.dll)", "dll"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            dllPathField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void doInject() {
        int selectedRow = processTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select a target process from the list.",
                    "No Process Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String dllPath = dllPathField.getText().trim();
        if (dllPath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please specify a DLL path.",
                    "No DLL Specified", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int    pid     = (int) tableModel.getValueAt(selectedRow, 0);
        String procName = (String) tableModel.getValueAt(selectedRow, 1);
        File   dllFile  = new File(dllPath);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Inject  " + dllFile.getName() + "\ninto  " + procName + "  (PID " + pid + ")?",
                "Confirm Injection", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) return;

        injectButton.setEnabled(false);
        setStatus("Injecting...");
        appendLog("─────────────────────────────────────────", LOG_INFO);

        executor.submit(() -> {
            try {
                engine.inject(pid, dllFile, msg ->
                        SwingUtilities.invokeLater(() -> appendLog(msg, LOG_OK)));
                SwingUtilities.invokeLater(() -> setStatus("Injection complete."));
            } catch (InjectionEngine.InjectionException ex) {
                SwingUtilities.invokeLater(() -> {
                    appendLog("ERROR: " + ex.getMessage(), LOG_ERR);
                    setStatus("Injection failed.");
                    JOptionPane.showMessageDialog(this,
                            ex.getMessage(), "Injection Failed", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> injectButton.setEnabled(true));
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void appendLog(String msg, Color color) {
        String ts   = "[" + LocalTime.now().format(TIME_FMT) + "]  ";
        String line = ts + msg + "\n";

        javax.swing.text.StyledDocument doc = logPane.getStyledDocument();
        javax.swing.text.SimpleAttributeSet attr = new javax.swing.text.SimpleAttributeSet();
        javax.swing.text.StyleConstants.setForeground(attr, color);
        javax.swing.text.StyleConstants.setFontFamily(attr, "Consolas");
        javax.swing.text.StyleConstants.setFontSize(attr, 12);

        try {
            doc.insertString(doc.getLength(), line, attr);
        } catch (javax.swing.text.BadLocationException ignored) {}

        logPane.setCaretPosition(doc.getLength());
    }

    private void setStatus(String msg) {
        statusBar.setText(msg);
    }

    // ── Styling helpers ───────────────────────────────────────────────────────

    private void styleTable(JTable t) {
        t.setBackground(BG_TABLE);
        t.setForeground(TEXT_MAIN);
        t.setFont(FONT_MONO);
        t.setRowHeight(22);
        t.setGridColor(BORDER_CLR);
        t.setSelectionBackground(ROW_SEL);
        t.setSelectionForeground(Color.WHITE);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new Dimension(8, 0));
        t.setFillsViewportHeight(true);

        JTableHeader th = t.getTableHeader();
        th.setBackground(BG_PANEL);
        th.setForeground(TEXT_DIM);
        th.setFont(FONT_SMALL);
        th.setBorder(new MatteBorder(0, 0, 1, 0, BORDER_CLR));
        th.setReorderingAllowed(false);
    }

    private JScrollPane darkScroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBackground(BG_DARK);
        s.getViewport().setBackground(BG_TABLE);
        s.setBorder(new LineBorder(BORDER_CLR, 1));
        s.getVerticalScrollBar().setUI(
                new javax.swing.plaf.basic.BasicScrollBarUI() {
                    @Override protected void configureScrollBarColors() {
                        thumbColor = new Color(60, 68, 82);
                        trackColor = BG_TABLE;
                    }
                });
        return s;
    }

    private JTextField styledTextField(String placeholder, int cols) {
        JTextField f = new JTextField(cols);
        f.setFont(FONT_MONO);
        f.setBackground(BG_TABLE);
        f.setForeground(TEXT_MAIN);
        f.setCaretColor(ACCENT);
        f.setBorder(new CompoundBorder(
                new LineBorder(BORDER_CLR, 1, true),
                new EmptyBorder(4, 8, 4, 8)));
        f.putClientProperty("JTextField.placeholderText", placeholder);
        return f;
    }

    private JButton accentButton(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT_SMALL);
        b.setForeground(ACCENT);
        b.setBackground(BG_PANEL);
        b.setFocusPainted(false);
        b.setBorder(new CompoundBorder(
                new LineBorder(BORDER_CLR, 1, true),
                new EmptyBorder(4, 10, 4, 10)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton dimButton(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT_SMALL);
        b.setForeground(TEXT_DIM);
        b.setBackground(BG_DARK);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 0));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void applyTheme() {
        UIManager.put("OptionPane.background",       BG_PANEL);
        UIManager.put("Panel.background",            BG_PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT_MAIN);
        UIManager.put("Button.background",           BG_PANEL);
        UIManager.put("Button.foreground",           TEXT_MAIN);
    }
}
