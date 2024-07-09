package nlScript.mic;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Microscope {

	public enum LED {
		LED_385(385),
		LED_470(470),
		LED_567(567),
		LED_625(625);

		public final int WAVELENGTH;

		LED(int wl) {
			WAVELENGTH = wl;
		}
	}

	public static class LEDSetting {
		private final LED led;
		private int intensity;

		public LEDSetting(LED led, int intensity) {
			this.led = led;
			this.intensity = intensity;
		}

		public void setIntensity(int intensity) {
			this.intensity = intensity;
		}

		public int getIntensity() {
			return this.intensity;
		}
	}

	public static class Channel {
		private final String name;
		private final List<LEDSetting> ledSettings;
		private int exposureTime;

		public Channel(String name, LEDSetting first, LEDSetting[] remaining, int exposureTime) {
			this.name = name;
			ledSettings = new ArrayList<>();
			ledSettings.add(first);
			ledSettings.addAll(Arrays.asList(remaining));
			this.exposureTime = exposureTime;
		}

		public LEDSetting getLEDSetting(LED led) {
			for(LEDSetting ledSetting : ledSettings)
				if(ledSetting.led == led)
					return ledSetting;
			return null;
		}

		public int getExposureTime() {
			return exposureTime;
		}

		public void setExposureTime(int exposureTime) {
			this.exposureTime = exposureTime;
		}
	}

	public enum Lens {
		FIVE(5, "5x"),
		TWENTY(20, "20x");

		public final float magnification;
		public final String label;

		Lens(float magnification, String label) {
			this.magnification = magnification;
			this.label = label;
		}

		public String toString() {
			return label;
		}
	}

	public enum MagnificationChanger {
		ZERO_FIVE(0.5f, "0.5x"),
		ONE_ZERO(1.0f, "1.0x"),
		TWO_ZERO(2.0f, "2.0x");

		public final float magnification;
		public final String label;

		MagnificationChanger(float magnification, String label) {
			this.magnification = magnification;
			this.label = label;
		}

		public String toString() {
			return label;
		}
	}

	public enum Binning {
		ONE(  1, "1x1"),
		TWO(  2, "2x2"),
		THREE(3, "3x3"),
		FOUR( 4, "4x4"),
		FIVE( 5, "5x5");

		public final int binning;
		public final String label;

		Binning(int binning, String label) {
			this.binning = binning;
			this.label = label;
		}

		public String toString() {
			return label;
		}
	}

	public static class Tuple3D {
		public final double x;
		public final double y;
		public final double z;

		public Tuple3D(Double[] t) {
			this.x = t[0];
			this.y = t[1];
			this.z = t[2];
		}

		public String toString() {
			return "(" + x + ", " + y + ", " + z + ")";
		}
	}

	public static class Position {
		public final String name;
		public final Tuple3D center;
		public final Tuple3D extent;

		public Position(String name, Double[] center, Double[] extent) {
			this.name = name;
			this.center = new Tuple3D(center);
			this.extent = new Tuple3D(extent);
		}

		public String toString() {
			return name + " " + center;
		}
	}

	public static class Incubation {
		private double temperature = 20;
		private double co2Concentration = 0;

		public void setTemperature(double temperature) {
			this.temperature = temperature;
		}

		public void setCO2Concentration(double co2Concentration) {
			this.co2Concentration = co2Concentration;
		}

		public void reset() {
			temperature = 20;
			co2Concentration = 0;
		}
	}

	public static final String ALL_CHANNELS = "ALL_CHANNELS";
	public static final String ALL_POSITIONS = "ALL_POSITIONS";

	private final List<Channel> channels = new ArrayList<>();
	private final List<Position> positions = new ArrayList<>();

	private Lens lens = Lens.FIVE;
	private MagnificationChanger magnificationChanger = MagnificationChanger.ONE_ZERO;
	private Binning binning = Binning.ONE;
	private final Incubation incubation = new Incubation();

	public void reset() {
		channels.clear();
		positions.clear();
		lens = Lens.FIVE;
		magnificationChanger = MagnificationChanger.ONE_ZERO;
		binning = Binning.ONE;
		incubation.reset();
	}

	public void addChannel(Channel channel) {
		this.channels.add(channel);
	}

	public Channel getChannel(String name) {
		for(Channel channel : channels)
			if(channel.name.equals(name))
				return channel;
		return null;
	}

	public void clearChannels() {
		this.channels.clear();
	}

	public void addPosition(Position position) {
		this.positions.add(position);
	}

	public Position getPosition(String name) {
		for(Position position : positions)
			if(position.name.equals(name))
				return position;
		return null;
	}

	public void clearPositions() {
		this.positions.clear();
	}

	public double getTemperature() {
		return incubation.temperature;
	}

	public void setTemperature(double temperature) {
		incubation.setTemperature(temperature);
	}

	public double getCO2Concentration() {
		return incubation.co2Concentration;
	}

	public void setCO2Concentration(double co2Concentration) {
		incubation.setCO2Concentration(co2Concentration);
	}

	public Lens getLens() {
		return lens;
	}

	public void setLens(Lens lens) {
		this.lens = lens;
	}

	public MagnificationChanger getMagnificationChanger() {
		return magnificationChanger;
	}

	public void setMagnificationChanger(MagnificationChanger mag) {
		this.magnificationChanger = mag;
	}

	public void setBinning(Binning binning) {
		this.binning = binning;
	}

	public void acquire(String[] positionNames, String[] channelNames, double dz) {
		Channel[] channels;
		if(channelNames.length > 0 && channelNames[0].equals(ALL_CHANNELS))
			channels = this.channels.toArray(new Channel[0]);
		else
			channels = Arrays.stream(channelNames).map(this::getChannel).toArray(Channel[]::new);

		Position[] positions;
		if(positionNames.length > 0 && positionNames[0].equals(ALL_POSITIONS))
			positions = this.positions.toArray(new Position[0]);
		else
			positions = Arrays.stream(positionNames).map(this::getPosition).toArray(Position[]::new);

		acquirePositionsAndChannels(positions, channels, dz);
	}

	public void acquirePositionsAndChannels(Position[] positions, Channel[] channels, double dz) {
		for(Position position : positions) {
			for(Channel channel : channels) {
				acquireSinglePositionAndChannel(position, channel);
			}
		}
	}

	public void acquireSinglePositionAndChannel(Position position, Channel channel) {
		Date currentDate = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy, HH:mm:ss", new Locale("en", "US"));
		String timeStamp = dateFormat.format(currentDate);

		System.out.println(timeStamp);
		System.out.println("======================");
		System.out.println("Stage position: " + position.name);
		System.out.println("  - " + position.center);
		System.out.println();
		System.out.println("Channel settings: " + channel.name);
		System.out.println("  - Exposure time: " + channel.exposureTime + "ms");
		for(LED led : LED.values()) {
            LEDSetting ledSetting = channel.getLEDSetting(led);
			if(ledSetting != null)
				System.out.println("  - LED " + led.WAVELENGTH + ": " + ledSetting.intensity + "%");
		}
		System.out.println();
		System.out.println("Optics:");
		System.out.println("  - Lens: " + this.lens);
		System.out.println("  - Mag.Changer: " + this.magnificationChanger);
		System.out.println("  - Binning: " + this.binning);
		System.out.println();
		System.out.println("Incubation:");
		System.out.println("  - Temperature: " + this.getTemperature() + "C");
		System.out.println("  - CO2 concentration: " + this.getCO2Concentration() + "%");
		System.out.println();
		System.out.println("Acquire stack");
		System.out.println();
		System.out.println();
	}

}
