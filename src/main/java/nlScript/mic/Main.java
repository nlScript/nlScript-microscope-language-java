package nlScript.mic;

import java.awt.*;

import ij.IJ;
import ij.plugin.PlugIn;
import nlScript.Parser;
import nlScript.ui.ACEditor;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class Main implements PlugIn {

	public static void main(String[] args) {
		new ij.ImageJ();
		run();
	}

	public void run(String args) {
		run();
	}

	public static void run() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		LanguageControl lc = new LanguageControl();
		Parser parser = lc.initParser();
		ACEditor editor = new ACEditor(parser);

		Microscope mic = lc.microscope;
		mic.addAcquisitionListener((position, channel) -> {
			Date currentDate = new Date();
			SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy, HH:mm:ss", new Locale("en", "US"));
			String timeStamp = dateFormat.format(currentDate);

			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			PrintStream out = new PrintStream(bos);
			out.println(timeStamp);
			out.println("======================");
			out.println("Stage position: " + position.name);
			out.println("  - " + position.center);
			out.println();
			out.println("Channel settings: " + channel.name);
			out.println("  - Exposure time: " + channel.getExposureTime() + "ms");
			for(Microscope.LED led : Microscope.LED.values()) {
				Microscope.LEDSetting ledSetting = channel.getLEDSetting(led);
				if(ledSetting != null)
					out.println("  - LED " + led.WAVELENGTH + ": " + ledSetting.getIntensity() + "%");
			}
			out.println();
			out.println("Optics:");
			out.println("  - Lens: " + mic.getLens());
			out.println("  - Mag.Changer: " + mic.getMagnificationChanger());
			out.println("  - Binning: " + mic.getBinning());
			out.println();
			out.println("Incubation:");
			out.println("  - Temperature: " + mic.getTemperature() + "C");
			out.println("  - CO2 concentration: " + mic.getCO2Concentration() + "%");
			out.println();
			out.println("Acquire stack");
			out.println();
			out.println();
			out.close();
			editor.getOutputArea().setText(editor.getOutputArea().getText() + bos);
		});

		editor.setBeforeRun(lc::reset);
		editor.setAfterRun(() -> lc.getTimeline().process(Runnable::run));
		editor.setVisible(true);

		editor.getTextArea().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if(!e.isControlDown())
					return;
				Point p = e.getPoint();
				SwingUtilities.convertPointToScreen(p, editor.getTextArea());
				showAIAutocompletion(editor, p.x, p.y);
			}
		});
	}

	public static void showAIAutocompletion(ACEditor editor, int x, int y) {
		JDialog dialog = new JDialog(editor.getFrame());
		JTextArea ta = new JTextArea(2, 30);
		Font taFont = UIManager.getFont("Label.font");
		if(taFont != null)
			ta.setFont(taFont.deriveFont((float) ta.getFont().getSize()));

		ta.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
					final AtomicInteger caret = new AtomicInteger(editor.getTextArea().getCaretPosition());
					String context = editor.getText().substring(0, caret.get());
					String sentence = ta.getText();
					dialog.dispose();
					new Thread(() -> {
						Ollama ollama = new Ollama();
						try {
							boolean prev = editor.isAutocompletionEnabled();
							editor.setAutocompletionEnabled(false);
							ollama.query(context, sentence, s -> {
								System.out.print(s);
								try {
									editor.getTextArea().getDocument().insertString(caret.getAndAdd(s.length()), s, null);
								} catch (BadLocationException ex) {
									throw new RuntimeException(ex);
								}
							});
							editor.setAutocompletionEnabled(prev);
						} catch(Exception ex) {
							IJ.handleException(ex);
						}
					}).start();
				}
				else if(e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					dialog.dispose();
				}
			}
		});
		ta.setBorder(null);
		ta.setLineWrap(true);
		JScrollPane scroll = new JScrollPane(ta);
		ResizableUndecoratedFrame.makeUndecoratedResizable(dialog);
		dialog.setModal(true);
		dialog.getContentPane().add(scroll);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		buttons.setBackground(Color.GRAY);
		buttons.setBorder(ta.getBorder());
		JLabel label = new JLabel("Press Ctrl-Enter to confirm or Esc to cancel");
		label.setForeground(Color.WHITE);
		buttons.add(label);

		dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
		dialog.pack();
		dialog.setLocation(x, y);
		dialog.setVisible(true);
	}
}
