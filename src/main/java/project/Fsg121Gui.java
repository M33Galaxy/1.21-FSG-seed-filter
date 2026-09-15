package project;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 1.21+ FSG 自动筛种 GUI（中英双语，默认中文）。
 */
public final class Fsg121Gui extends JFrame {
	private final JComboBox<String> langBox = new JComboBox<>(new String[]{"中文", "English"});
	private final JLabel langLabel = new JLabel();
	private final JRadioButton modeOne = new JRadioButton();
	private final JRadioButton modeCount = new JRadioButton();
	private final JRadioButton modeCont = new JRadioButton();
	private final JLabel countUnitLabel = new JLabel();
	private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
	private final JSpinner threadSpinner = new JSpinner(new SpinnerNumberModel(
			Math.max(1, Runtime.getRuntime().availableProcessors()), 1, 256, 1));
	private final JLabel threadsLabel = new JLabel();
	private final JCheckBox writeFileBox = new JCheckBox();
	private final JTextField outputField = new JTextField(28);
	private final JButton browseBtn = new JButton();
	private final JButton startBtn = new JButton();
	private final JButton stopBtn = new JButton();
	private final JButton copyAllBtn = new JButton();
	private final JTextArea logArea = new JTextArea();
	private final JLabel statusLabel = new JLabel();
	private final JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
	private final JPanel optPanel = new JPanel(new GridBagLayout());
	private final JScrollPane scroll = new JScrollPane();
	private final AtomicReference<Fsg121Project.Session> sessionRef = new AtomicReference<>();
	private boolean applyingLang;

	private Fsg121Gui() {
		super();
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setMinimumSize(new Dimension(760, 520));

		outputField.setText("fsgresults.txt");
		setFileControlsEnabled(false);
		writeFileBox.addActionListener(e -> setFileControlsEnabled(writeFileBox.isSelected()));

		ButtonGroup modes = new ButtonGroup();
		modes.add(modeOne);
		modes.add(modeCount);
		modes.add(modeCont);
		modeOne.setSelected(true);
		modeCount.addChangeListener(e -> countSpinner.setEnabled(modeCount.isSelected()));
		countSpinner.setEnabled(false);

		JPanel langPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
		langPanel.add(langLabel);
		langPanel.add(langBox);
		langBox.setSelectedIndex(0);
		langBox.addActionListener(e -> {
			if (applyingLang) {
				return;
			}
			Fsg121I18n.setLang(langBox.getSelectedIndex() == 0 ? Fsg121I18n.Lang.ZH : Fsg121I18n.Lang.EN);
			applyLanguage();
		});

		modePanel.add(modeOne);
		modePanel.add(modeCount);
		modePanel.add(countSpinner);
		modePanel.add(countUnitLabel);
		modePanel.add(modeCont);

		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(4, 6, 4, 6);
		gc.anchor = GridBagConstraints.WEST;
		gc.gridx = 0;
		gc.gridy = 0;
		optPanel.add(threadsLabel, gc);
		gc.gridx = 1;
		optPanel.add(threadSpinner, gc);
		gc.gridx = 0;
		gc.gridy = 1;
		optPanel.add(writeFileBox, gc);
		gc.gridx = 1;
		gc.fill = GridBagConstraints.HORIZONTAL;
		gc.weightx = 1;
		optPanel.add(outputField, gc);
		gc.gridx = 2;
		gc.weightx = 0;
		gc.fill = GridBagConstraints.NONE;
		browseBtn.addActionListener(e -> browseOutput());
		optPanel.add(browseBtn, gc);

		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
		stopBtn.setEnabled(false);
		btnPanel.add(startBtn);
		btnPanel.add(stopBtn);
		btnPanel.add(copyAllBtn);
		btnPanel.add(statusLabel);

		logArea.setEditable(false);
		logArea.setFocusable(true);
		logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		scroll.setViewportView(logArea);

		JPanel north = new JPanel(new BorderLayout(0, 6));
		north.add(langPanel, BorderLayout.NORTH);
		north.add(modePanel, BorderLayout.CENTER);
		JPanel mid = new JPanel(new BorderLayout(0, 6));
		mid.add(optPanel, BorderLayout.NORTH);
		mid.add(btnPanel, BorderLayout.SOUTH);
		north.add(mid, BorderLayout.SOUTH);

		JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		root.add(north, BorderLayout.NORTH);
		root.add(scroll, BorderLayout.CENTER);
		setContentPane(root);

		startBtn.addActionListener(e -> startSearch());
		stopBtn.addActionListener(e -> stopSearch());
		copyAllBtn.addActionListener(e -> copyAllSeeds());

		applyLanguage();
		pack();
		setLocationRelativeTo(null);
	}

	private void applyLanguage() {
		applyingLang = true;
		try {
			setTitle(Fsg121I18n.t("title"));
			langLabel.setText(Fsg121I18n.t("lang"));
			langBox.setSelectedIndex(Fsg121I18n.lang() == Fsg121I18n.Lang.ZH ? 0 : 1);
			modePanel.setBorder(BorderFactory.createTitledBorder(Fsg121I18n.t("stop.title")));
			modeOne.setText(Fsg121I18n.t("stop.one"));
			modeCount.setText(Fsg121I18n.t("stop.count"));
			modeCont.setText(Fsg121I18n.t("stop.cont"));
			countUnitLabel.setText(Fsg121I18n.t("stop.unit"));
			optPanel.setBorder(BorderFactory.createTitledBorder(Fsg121I18n.t("opts.title")));
			threadsLabel.setText(Fsg121I18n.t("opts.threads"));
			writeFileBox.setText(Fsg121I18n.t("opts.writeFile"));
			browseBtn.setText(Fsg121I18n.t("opts.browse"));
			startBtn.setText(Fsg121I18n.t("btn.start"));
			stopBtn.setText(Fsg121I18n.t("btn.stop"));
			copyAllBtn.setText(Fsg121I18n.t("btn.copy"));
			if (sessionRef.get() == null || !sessionRef.get().isRunning()) {
				String st = statusLabel.getText();
				if (st == null || st.isEmpty() || st.equals("就绪") || st.equals("Ready")
						|| st.startsWith("已筛") || st.startsWith("Structure")) {
					statusLabel.setText(Fsg121I18n.t("status.ready"));
				}
			}
			logArea.setToolTipText(Fsg121I18n.t("hits.tip"));
			scroll.setBorder(BorderFactory.createTitledBorder(Fsg121I18n.t("hits.title")));
			revalidate();
			repaint();
		} finally {
			applyingLang = false;
		}
	}

	private void setFileControlsEnabled(boolean on) {
		outputField.setEnabled(on);
		browseBtn.setEnabled(on);
	}

	private void browseOutput() {
		Path current = Path.of(outputField.getText().trim().isEmpty() ? "fsgresults.txt" : outputField.getText().trim());
		Path startDir = current.getParent() != null ? current.getParent() : Path.of("").toAbsolutePath();
		JFileChooser fc = new JFileChooser(startDir.toFile());
		fc.setSelectedFile(startDir.resolve(
				current.getFileName() != null ? current.getFileName().toString() : "fsgresults.txt").toFile());
		if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			Path chosen = fc.getSelectedFile().toPath();
			Path cwd = Path.of("").toAbsolutePath().normalize();
			Path abs = chosen.toAbsolutePath().normalize();
			if (abs.startsWith(cwd)) {
				outputField.setText(cwd.relativize(abs).toString());
			} else {
				outputField.setText(abs.toString());
			}
		}
	}

	private void copyAllSeeds() {
		String text = logArea.getText();
		StringBuilder seeds = new StringBuilder();
		for (String line : text.split("\\R")) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("---")) {
				continue;
			}
			if (line.startsWith("同时写入") || line.startsWith("Also writing")) {
				continue;
			}
			try {
				Long.parseLong(line);
				seeds.append(line).append('\n');
			} catch (NumberFormatException ignored) {
			}
		}
		String out = seeds.toString();
		if (out.isEmpty()) {
			JOptionPane.showMessageDialog(this, Fsg121I18n.t("dlg.noSeeds"),
					Fsg121I18n.t("dlg.copyTitle"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(out), null);
		int n = out.split("\\R").length;
		statusLabel.setText(Fsg121I18n.format("status.copied", n));
	}

	private void startSearch() {
		if (sessionRef.get() != null && sessionRef.get().isRunning()) {
			return;
		}
		Fsg121Project.Options opt = new Fsg121Project.Options();
		if (writeFileBox.isSelected()) {
			String path = outputField.getText().trim();
			if (path.isEmpty()) {
				JOptionPane.showMessageDialog(this, Fsg121I18n.t("dlg.needFile"),
						Fsg121I18n.t("dlg.tip"), JOptionPane.WARNING_MESSAGE);
				return;
			}
			opt.output = Path.of(path);
		} else {
			opt.output = null;
		}
		opt.threads = ((Number) threadSpinner.getValue()).intValue();
		if (modeOne.isSelected()) {
			opt.stopMode = Fsg121Project.StopMode.ONE;
			opt.targetHits = 1;
		} else if (modeCount.isSelected()) {
			opt.stopMode = Fsg121Project.StopMode.COUNT;
			opt.targetHits = ((Number) countSpinner.getValue()).intValue();
		} else {
			opt.stopMode = Fsg121Project.StopMode.CONTINUOUS;
			opt.targetHits = Integer.MAX_VALUE;
		}
		opt.hitListener = seed -> SwingUtilities.invokeLater(() -> {
			logArea.append(Long.toString(seed));
			logArea.append(System.lineSeparator());
			logArea.setCaretPosition(logArea.getDocument().getLength());
		});
		opt.statusListener = st -> SwingUtilities.invokeLater(() ->
				statusLabel.setText(Fsg121I18n.format("status.struct", String.format("%,d", st.structTried()))));
		opt.finishedListener = () -> SwingUtilities.invokeLater(() -> {
			startBtn.setEnabled(true);
			stopBtn.setEnabled(false);
			statusLabel.setText(statusLabel.getText() + Fsg121I18n.t("status.stopped"));
			sessionRef.set(null);
		});

		logArea.append(Fsg121I18n.t("log.start"));
		logArea.append("\n");
		if (opt.output != null) {
			logArea.append(Fsg121I18n.format("log.writeFile", opt.output.toString()));
			logArea.append("\n");
		}
		startBtn.setEnabled(false);
		stopBtn.setEnabled(true);
		statusLabel.setText(Fsg121I18n.format("status.struct", "0"));
		try {
			sessionRef.set(Fsg121Project.start(opt));
		} catch (Exception ex) {
			startBtn.setEnabled(true);
			stopBtn.setEnabled(false);
			JOptionPane.showMessageDialog(this, ex.getMessage(),
					Fsg121I18n.t("dlg.startFail"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void stopSearch() {
		Fsg121Project.Session s = sessionRef.get();
		if (s != null) {
			s.stop();
			statusLabel.setText(Fsg121I18n.t("status.stopping"));
		}
	}

	public static void launch() {
		SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ignored) {
			}
			Fsg121I18n.setLang(Fsg121I18n.Lang.ZH);
			new Fsg121Gui().setVisible(true);
		});
	}

	public static void main(String[] args) {
		launch();
	}
}
