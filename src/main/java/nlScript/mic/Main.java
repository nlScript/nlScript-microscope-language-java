package nlScript.mic;

import nlScript.Parser;
import nlScript.ui.ACEditor;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Main {
	public static void main(String[] args) {
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
			editor.getOutputArea().setText(editor.getOutputArea().getText() + bos.toString());
		});

		editor.setBeforeRun(lc::reset);
		editor.setAfterRun(() -> lc.getTimeline().process(Runnable::run));
		editor.setVisible(true);
	}
}
