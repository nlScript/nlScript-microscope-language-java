package nlScript.mic;

import nlScript.Parser;
import nlScript.core.Autocompletion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;

import static nlScript.mic.Microscope.*;

public class LanguageControl {

	private static <T> T[] convert(Object data, Class<? extends T[]> type) {
		assert(data instanceof Object[]);
		return Arrays.copyOf((Object[]) data, ((Object[]) data).length, type);
	}

	public final Microscope microscope;

	private final Timeline<Runnable> timeline = new Timeline<>();

	private LocalTime globalStart;

	public LanguageControl() {
		this.microscope = new Microscope();
	}

	public LanguageControl(Microscope microscope) {
		this.microscope = microscope;
	}

	public void reset() {
		globalStart = LocalTime.now();
		microscope.reset();
		timeline.clear();
	}

	public Timeline<Runnable> getTimeline() {
		return timeline;
	}

	public Parser initParser() {

		final ArrayList<String> definedChannels = new ArrayList<>();
		final ArrayList<String> definedRegions  = new ArrayList<>();

		Parser parser = new Parser();
		parser.addParseStartListener(() -> {
			definedChannels.clear();
			definedRegions.clear();
		});

		parser.defineType("led", "385nm", e -> LED.LED_385);
		parser.defineType("led", "470nm", e -> LED.LED_470);
		parser.defineType("led", "567nm", e -> LED.LED_567);
		parser.defineType("led", "625nm", e -> LED.LED_625);

		parser.defineType("led-power", "{<led-power>:int}%",
				e -> e.evaluate("<led-power>"),
				true);

		parser.defineType("exposure-time", "{<exposure-time>:int}ms",
				e -> e.evaluate("<exposure-time>"),
				true);

		parser.defineType("led-setting", "{led-power:led-power} at {wavelength:led}",
				e -> {
					int power = (Integer) e.evaluate("led-power");
					LED led = (LED) e.evaluate("wavelength");
					return new Microscope.LEDSetting(led, power);
				},
				true);

		parser.defineType("another-led-setting", ", {led-setting:led-setting}",
				e -> e.evaluate("led-setting"),
				true);

		parser.defineType("channel-name", "'{<name>:[A-Za-z0-9]:+}'",
				e -> e.getParsedString("<name>"),
				true);

		parser.defineSentence(
				"Define channel {channel-name:channel-name}:" +
				"{\n  }excite with {led-setting:led-setting}{another-led-setting:another-led-setting:0-3}" +
				"{\n  }use an exposure time of {exposure-time:exposure-time}.",
				e -> {
					String name = (String) e.evaluate("channel-name");
					LEDSetting firstLedSetting = (LEDSetting) e.evaluate("led-setting");
					LEDSetting[] otherLedSettings = convert(e.evaluate("another-led-setting"), LEDSetting[].class);
					int exposureTime = (Integer) e.evaluate("exposure-time");
					Channel channel = new Channel(name, firstLedSetting, otherLedSettings, exposureTime);
					microscope.addChannel(channel);
					return null;
				}
		).onSuccessfulParsed(n -> {
			definedChannels.add(n.getParsedString("channel-name"));
		});

		// Define "Tile Scan 1" as a (w x h x d) region centered at (x, y, z)
		parser.defineType("region-name", "'{<region-name>:[a-zA-Z0-9]:+}'",
				e -> e.getParsedString("<region-name>"),
				true);

		parser.defineType("region-dimensions", "{<width>:float} x {<height>:float} x {<depth>:float} microns",
				e -> {
					Double w = (Double) e.evaluate("<width>");
					Double h = (Double) e.evaluate("<height>");
					Double d = (Double) e.evaluate("<depth>");
					return new Double[] { w, h, d };
				},
				true);

		parser.defineType("region-center", "{<center>:tuple<float,x,y,z>} microns",
				e -> convert(e.evaluate("<center>"), Double[].class),
				true);

		parser.defineSentence(
				"Define a position {region-name:region-name}:" +
				"{\n  }{region-dimensions:region-dimensions}" +
				"{\n  }centered at {region-center:region-center}.",
				e -> {
					String name = (String) e.evaluate("region-name");
					Double[] dimensions = (Double[]) e.evaluate("region-dimensions");
					Double[] center = (Double[]) e.evaluate("region-center");
					microscope.addPosition(new Position(name, center, dimensions));
					return null;
				}
		).onSuccessfulParsed(n -> {
			definedRegions.add(n.getParsedString("region-name"));
		});

		parser.defineSentence(
				"Define the output folder at {folder:path}.",
				e -> null);

		parser.defineType("defined-channels", "'{channel:[A-Za-z0-9]:+}'",
				e -> e.getParsedString("channel"),
				(e, justCheck) -> Autocompletion.literal(e, definedChannels)
		);

		parser.defineType("defined-positions", "'{position:[A-Za-z0-9]:+}'",
				e -> e.getParsedString("position"),
				(e, justCheck) -> Autocompletion.literal(e, definedRegions)
		);

		parser.defineType("time-unit", "second(s)", e -> 1);
		parser.defineType("time-unit", "minute(s)", e -> 60);
		parser.defineType("time-unit", "hour(s)",   e -> 3600);

		parser.defineType("time-interval", "{n:float} {time-unit:time-unit}",
				e -> {
					double nUnits = (Double)e.evaluate("n");
					int unitInSeconds = (Integer)e.evaluate("time-unit");
					return Math.round(nUnits * unitInSeconds);
				},
				true);

		parser.defineType("repetition", "once", e -> new long[] { 1, 0 });
		parser.defineType("repetition", "every {interval:time-interval} for {duration:time-interval}",
				e -> {
					long interval = (long) e.evaluate("interval");
					long duration = (long) e.evaluate("duration");
					return new long[] { interval, duration };
				},
				true);

		parser.defineType("z-distance", "{z-distance:float} microns",
				e -> e.evaluate("z-distance"),
				true);

		parser.defineType("lens",  "5x lens", e -> Lens.FIVE);
		parser.defineType("lens", "20x lens", e -> Lens.TWENTY);

		parser.defineType("mag", "0.5x magnification changer", e -> MagnificationChanger.ZERO_FIVE);
		parser.defineType("mag", "1.0x magnification changer", e -> MagnificationChanger.ONE_ZERO);
		parser.defineType("mag", "2.0x magnification changer", e -> MagnificationChanger.TWO_ZERO);

		parser.defineType("binning", "1 x 1", e -> Binning.ONE);
		parser.defineType("binning", "2 x 2", e -> Binning.TWO);
		parser.defineType("binning", "3 x 3", e -> Binning.THREE);
		parser.defineType("binning", "4 x 4", e -> Binning.FOUR);
		parser.defineType("binning", "5 x 5", e -> Binning.FIVE);

		parser.defineType("start", "At the beginning",            e -> globalStart);
		parser.defineType("start", "At {time:time}",              e -> e.evaluate("time"), true);
		parser.defineType("start", "After {delay:time-interval}",
				e -> {
					long afterSeconds = (long) e.evaluate("delay");
					return globalStart.plusSeconds(afterSeconds);
				},
				true);

		parser.defineType("position-list", "all positions", e -> new String[] { ALL_POSITIONS });
		parser.defineType("position-list", "position(s) {positions:list<defined-positions>}",
				e -> {
					final Object[] positionsAsObjects = (Object[]) e.evaluate("positions");
					return Arrays.stream(positionsAsObjects).map(o -> (String) o).toArray(String[]::new);
				});

		parser.defineType("channel-list", "all channels", e -> new String[] { ALL_CHANNELS });
		parser.defineType("channel-list", "channel(s) {channels:list<defined-channels>}",
				e -> {
					final Object[] channelsAsObjects = (Object[]) e.evaluate("channels");
					return Arrays.stream(channelsAsObjects).map(o -> (String) o).toArray(String[]::new);
				});

		parser.defineSentence(
				"{start:start}{, }acquire..." +
				"{\n  }{repetition:repetition}" +
				"{\n  }{position-list:position-list}" +
				"{\n  }{channel-list:channel-list}" +
				"{\n  }with a plane distance of {dz:z-distance}" +
				"{\n  }using the {lens:lens} with the {magnification:mag} and a binning of {binning:binning}.",
				e -> {
					final LocalTime time = (LocalTime) e.evaluate("start");
					final long[] repetition = (long[]) e.evaluate("repetition");
					final long interval = repetition[0];
					final long duration = repetition[1];

					final String[] positionNames = (String[]) e.evaluate("position-list");
					final String[] channelNames = (String[]) e.evaluate("channel-list");
					final Lens lens = (Lens) e.evaluate("lens");
					final MagnificationChanger mag = (MagnificationChanger) e.evaluate("magnification");
					final Binning binning = (Binning) e.evaluate("binning");
					final double dz = (double) e.evaluate("dz");

					LocalDateTime start = LocalDate.now().atTime(time);
					if(globalStart.isAfter(time))
						start = start.plusDays(1);

					int nCycles = duration < interval ? 1 : (int)(duration / interval + 1);
					for(int c = 0; c < nCycles; c++) {
						LocalDateTime plannedExecutionTime = start.plusSeconds(c * interval);
						timeline.put(plannedExecutionTime, () -> {
							microscope.setLens(lens);
							microscope.setMagnificationChanger(mag);
							microscope.setBinning(binning);
							microscope.acquire(positionNames, channelNames, dz);
						});
					}
					return null;
				});

		parser.defineSentence(
				"{start:start}{, }adjust..." +
				"{\n  }{repetition:repetition}" +
				"{\n  }the power of the {led:led} led of channel {channel:defined-channels} to {power:led-power}.",
				e -> {
					final LocalTime time = (LocalTime) e.evaluate("start");
					final long[] repetition = (long[]) e.evaluate("repetition");
					final long interval = repetition[0];
					final long duration = repetition[1];

					final LED led = (LED) e.evaluate("led");
					final String channel = (String) e.evaluate("channel");
					final int power = (Integer) e.evaluate("power");

					LocalDateTime start = LocalDate.now().atTime(time);
					if(globalStart.isAfter(time))
						start = start.plusDays(1);

					int nCycles = duration < interval ? 1 : (int)(duration / interval + 1);

					Interpolator interpolator = new Interpolator(
							() -> microscope.getChannel(channel).getLEDSetting(led).getIntensity(),
							(c, v)  -> microscope.getChannel(channel).getLEDSetting(led).setIntensity((int) Math.round(v)),
							power, nCycles);

					for(int c = 0; c < nCycles; c++) {
						int cycle = c;
						LocalDateTime plannedExecutionTime = start.plusSeconds(c * interval);
						timeline.put(plannedExecutionTime, () -> interpolator.interpolate(cycle));
					}
					return null;
				});

		parser.defineSentence(
				"{start:start}{, }adjust..." +
				"{\n  }{repetition:repetition}" +
				"{\n  }the exposure time of channel {channel:defined-channels} to {exposure-time:exposure-time}.",
				e -> {
					final LocalTime time = (LocalTime) e.evaluate("start");
					final long[] repetition = (long[]) e.evaluate("repetition");
					final long interval = repetition[0];
					final long duration = repetition[1];

					final String channel = (String) e.evaluate("channel");
					final int exposureTime = (Integer) e.evaluate("exposure-time");

					LocalDateTime start = LocalDate.now().atTime(time);
					if(globalStart.isAfter(time))
						start = start.plusDays(1);

					int nCycles = duration < interval ? 1 : (int)(duration / interval + 1);

					Interpolator interpolator = new Interpolator(
							() -> microscope.getChannel(channel).getExposureTime(),
							(c, v) -> microscope.getChannel(channel).setExposureTime((int) Math.round(v)),
							exposureTime, nCycles);

					for(int c = 0; c < nCycles; c++) {
						int cycle = c;
						LocalDateTime plannedExecutionTime = start.plusSeconds(c * interval);
						timeline.put(plannedExecutionTime, () -> interpolator.interpolate(cycle));
					}
					return null;
				});


		parser.defineType("temperature", "{temperature:float}\u00B0C", e -> e.evaluate("temperature"), true);

		parser.defineType("co2-concentration", "{CO2 concentration:float}%", e -> e.evaluate("CO2 concentration"), true);

		parser.defineSentence(
				"{start:start}{, }adjust..." +
				"{\n  }{repetition:repetition}" +
				"{\n  }the CO2 concentration to {co2-concentration:co2-concentration}.",
				e -> {
					final LocalTime time = (LocalTime) e.evaluate("start");
					final long[] repetition = (long[]) e.evaluate("repetition");
					final long interval = repetition[0];
					final long duration = repetition[1];

					final double co2Concentration = (Double) e.evaluate("co2-concentration");

					LocalDateTime start = LocalDate.now().atTime(time);
					if(globalStart.isAfter(time))
						start = start.plusDays(1);

					int nCycles = duration < interval ? 1 : (int)(duration / interval + 1);

					Interpolator interpolator = new Interpolator(
							microscope::getCO2Concentration,
							(c, v) -> microscope.setCO2Concentration(v),
							co2Concentration, nCycles);

					for(int c = 0; c < nCycles; c++) {
						int cycle = c;
						LocalDateTime plannedExecutionTime = start.plusSeconds(c * interval);
						timeline.put(plannedExecutionTime, () -> interpolator.interpolate(cycle));
					}
					return null;
				});

		parser.defineSentence(
				"{start:start}{, }adjust..." +
				"{\n  }{repetition:repetition}" +
				"{\n  }the temperature to {temperature:temperature}.",
				e -> {
					final LocalTime time = (LocalTime) e.evaluate("start");
					final long[] repetition = (long[]) e.evaluate("repetition");
					final long interval = repetition[0];
					final long duration = repetition[1];

					final double temperature = (Double) e.evaluate("temperature");

					LocalDateTime start = LocalDate.now().atTime(time);
					if(globalStart.isAfter(time))
						start = start.plusDays(1);

					int nCycles = duration < interval ? 1 : (int)(duration / interval + 1);

					Interpolator interpolator = new Interpolator(
							microscope::getTemperature,
							(c, v) -> microscope.setTemperature(v),
							temperature, nCycles);

					for(int c = 0; c < nCycles; c++) {
						int cycle = c;
						LocalDateTime plannedExecutionTime = start.plusSeconds(c * interval);
						timeline.put(plannedExecutionTime, () -> interpolator.interpolate(cycle));
					}
					return null;
				});

		return parser;
	}
}
