package nlScript.mic;

import ij.IJ;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleContext;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.function.Consumer;

public class ConfigureOllama {

	private final Ollama ollama;

	private final Ollama original;

	private Consumer<String> ollamaStdout;

	private Consumer<String> ollamaStderr;

	private Consumer<String> ollamaStdin;

	public ConfigureOllama(Ollama ollama) {
		this.ollama = new Ollama(ollama);
		this.original = ollama;
	}

	public void configure() {
		JDialog dialog = new JDialog();

		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		dialog.getContentPane().setLayout(gridbag);

		JLabel hostLabel = new JLabel("Host:");
		c.gridx = c.gridy = 0;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(5, 5, 5, 5);
		dialog.getContentPane().add(hostLabel, c);

		JTextField hostTF = new JTextField(ollama.getHost());
		hostTF.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {
				hostTF.selectAll();
			}

			@Override
			public void focusLost(FocusEvent e) {
				ollama.setHost(hostTF.getText());
			}
		});
		hostTF.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_ENTER) {
					ollama.setHost(hostTF.getText());
				}
			}
		});
		c.gridx++;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		dialog.getContentPane().add(hostTF, c);

		JButton resetHost = new JButton("\u21ba");
		resetHost.setMargin(new Insets(0, 0, 0, 0));
		resetHost.addActionListener(l -> {
			hostTF.setText(original.getHost());
			ollama.setHost(original.getHost());
		});
		c.gridx++;
		c.weightx = 0;
		dialog.getContentPane().add(resetHost, c);


		JLabel portLabel = new JLabel("Port:");
		c.gridx = 0;
		c.gridy++;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(5, 5, 5, 5);
		dialog.getContentPane().add(portLabel, c);

		NumberFormat numberFormat = NumberFormat.getIntegerInstance();
		numberFormat.setGroupingUsed(false);
		NumberFormatter formatter = new NumberFormatter(numberFormat);
		formatter.setValueClass(Long.class);
		formatter.setAllowsInvalid(false);
		JFormattedTextField portTF = new JFormattedTextField(formatter);
		portTF.setValue(ollama.getPort());
		portTF.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {
				portTF.selectAll();
			}

			@Override
			public void focusLost(FocusEvent e) {
				ollama.setPort(Integer.parseInt(portTF.getText()));
			}
		});
		portTF.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_ENTER) {
					ollama.setPort(Integer.parseInt(portTF.getText()));
				}
			}
		});
		c.gridx++;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		dialog.getContentPane().add(portTF, c);

		JButton resetPort = new JButton("\u21ba");
		resetPort.setMargin(new Insets(0, 0, 0, 0));
		resetPort.addActionListener(l -> {
			portTF.setValue(original.getPort());
			ollama.setPort(original.getPort());
		});
		c.gridx++;
		c.weightx = 0;
		dialog.getContentPane().add(resetPort, c);

		JLabel outputLabel = new JLabel("Ollama output");
		c.gridx = 0;
		c.gridy++;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0; c.weighty = 0;
		c.gridwidth = GridBagConstraints.REMAINDER;
		dialog.getContentPane().add(outputLabel, c);

		JTextPane outputTextArea = new JTextPane();
		outputTextArea.setPreferredSize(new Dimension(400, 300));
		JScrollPane scrollPane = new JScrollPane(outputTextArea);
		c.gridx = 0;
		c.gridy++;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = c.weighty = 1;
		c.gridwidth = GridBagConstraints.REMAINDER;
		dialog.getContentPane().add(scrollPane, c);

		ollamaStdout = s -> appendToOutput(outputTextArea, s, Color.BLACK);
		ollamaStderr = s -> appendToOutput(outputTextArea, s, Color.RED);
		ollamaStdin  = s -> appendToOutput(outputTextArea, s, Color.GREEN);


		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
		JButton cancel = new JButton("Cancel");
		JButton test   = new JButton("Test");
		JButton ok     = new JButton("Ok");

		buttons.add(cancel);
		buttons.add(test);
		buttons.add(ok);

		c.gridx = 0;
		c.gridy++;
		c.weightx = 1;
		c.weighty = 0;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(5, 5, 5, 5);
		dialog.getContentPane().add(buttons, c);

		cancel.addActionListener(l -> {
			dialog.dispose();
		});

		ok.addActionListener(l -> {
			if(!test(dialog))
				return;
			original.setHost(ollama.getHost());
			original.setPort(ollama.getPort());
			dialog.dispose();
		});

		test.addActionListener(l -> {
			test(dialog);
		});


		dialog.pack();
		dialog.setVisible(true);
	}

	private void appendToOutput(JTextPane tp, String msg, Color c) {
		if(!msg.endsWith("\n"))
			msg += "\n";
		StyleContext sc = StyleContext.getDefaultStyleContext();
		AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c);

		aset = sc.addAttribute(aset, StyleConstants.FontFamily, "Lucida Console");
		aset = sc.addAttribute(aset, StyleConstants.Alignment, StyleConstants.ALIGN_JUSTIFIED);

		int len = tp.getDocument().getLength();
		tp.setCaretPosition(len);
		tp.setCharacterAttributes(aset, false);
		tp.replaceSelection(msg);
	}

	private boolean test(Component parent) {
		if(!ollama.isOllamaRunning(ollamaStdout, ollamaStderr, ollamaStdin)) {
			noOllama(parent);
			return false;
		}
		try {
			if(!ollama.isModelAvailable(ollama.getModel(), ollamaStdout, ollamaStderr, ollamaStdin))
				return modelDoesntExist(parent);
			else {
				success(parent);
				return true;
			}
		} catch (Exception e) {
			IJ.handleException(e);
			return false;
		}
	}

	private void noOllama(Component parent) {
		JOptionPane.showMessageDialog(parent,
				"Ollama is not running on " + ollama.getHost() + ":" + ollama.getPort() + "\n" +
				"Please make sure it is installed and running:\n\n" +
				"See https://ollama.com/",
				"Error",
				JOptionPane.ERROR_MESSAGE);
	}

	private boolean modelDoesntExist(Component parent) {
		int res = JOptionPane.showOptionDialog(
				parent,
				"Ollama is running, but '" + ollama.getModel() + "'\n" +
				"doesn't exist. Create it on " + ollama.getHost() + "?",
				"Error",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.ERROR_MESSAGE,
				null, null, null);

		try {
			if (res == JOptionPane.OK_OPTION) {
				ollama.createModel(ollamaStdout, ollamaStderr, ollamaStdin);
				if(ollama.isModelAvailable(ollama.getModel(), ollamaStdout, ollamaStderr, ollamaStdin)) {
					success(parent);
					return true;
				} else {
					JOptionPane.showMessageDialog(parent,
							"Tried to install '" + ollama.getModel() + "' but since it is still not\n" +
							"available, installation must have failed. Please install it manually.",
							"Error",
							JOptionPane.ERROR_MESSAGE);
					return false;
				}
			}
			return false;
		} catch (Ollama.OllamaException e) {
			e.printStackTrace();
			return false;
		}
	}

	private void success(Component parent) {
		JOptionPane.showMessageDialog(parent,
				"Ollama is running on " + ollama.getHost() + ":" + ollama.getPort() + ",\n" +
				"and '" + ollama.getModel() + "' is installed and available.",
				"Success",
				JOptionPane.INFORMATION_MESSAGE);
	}

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		new ConfigureOllama(new Ollama()).configure();
	}
}
