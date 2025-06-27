package nlScript.mic;

import nlScript.Autocompleter;
import nlScript.ParseException;
import nlScript.ParsedNode;
import nlScript.Parser;
import nlScript.core.Autocompletion;
import nlScript.core.DefaultParsedNode;
import nlScript.core.Generation;
import nlScript.core.GeneratorHints;
import nlScript.ebnf.NamedRule;
import nlScript.ebnf.Rule;
import nlScript.ui.ACEditor;
import nlScript.util.RandomInt;
import nlScript.core.GeneratorHints.Key;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static nlScript.mic.Microscope.*;

public class LanguageControl {

	private static final boolean WITH_COMMENTS = true;

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

	private final Random random = new Random();

	private Rule defineChannelRule,
			definePositionRule,
			acquisitionRule,
			adjustLEDRule,
			adjustExposureTimeRule,
			adjustCO2Rule,
			adjustTemperatureRule;

	final ArrayList<String> definedChannels = new ArrayList<>();
	final ArrayList<String> definedRegions  = new ArrayList<>();

	public Parser initParser() {

		Parser parser = new Parser();
		parser.addParseStartListener(() -> {
			definedChannels.clear();
			definedRegions.clear();
		});

		NamedRule rule;
		parser.defineType("led", "385nm", e -> LED.LED_385);
		parser.defineType("led", "470nm", e -> LED.LED_470);
		parser.defineType("led", "567nm", e -> LED.LED_567);
		parser.defineType("led", "625nm", e -> LED.LED_625);

		rule = parser.defineType("led-power", "{<led-power>:int}%",
				e -> e.evaluate("<led-power>"),
				true);
		parser.setGeneratorHints(rule, "<led-power>", GeneratorHints.from(Key.MIN_VALUE, 0, Key.MAX_VALUE, 100));

		rule = parser.defineType("exposure-time", "{<exposure-time>:int}ms",
				e -> e.evaluate("<exposure-time>"),
				true);
		parser.setGeneratorHints(rule, "<exposure-time>", GeneratorHints.from(Key.MIN_VALUE, 5, Key.MAX_VALUE, 10000));

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

		rule = parser.defineType("channel-name", "'{<name>:[A-Za-z0-9]:+}'",
				e -> e.getParsedString("<name>"),
				true);
		parser.setGeneratorHints(rule, "<name>", GeneratorHints.from(Key.MIN_NUMBER, 3, Key.MAX_NUMBER, 8));

		parser.defineSentence("{//}{comment:[^\r\n]:*}{\n}",
				e -> null,
				new Autocompleter.EntireSequenceCompleter(parser.getTargetGrammar(), new HashMap<>()) {
					@Override
					public Autocompletion[] getAutocompletion(DefaultParsedNode pn, boolean justCheck) {
						if(pn.getParsedString().length() < 2) {
							Autocompletion.EntireSequence es = new Autocompletion.EntireSequence(pn);
							es.addLiteral(pn.getChild(0).getSymbol(), null, "// ");
							es.addParameterized(pn.getChild(1).getSymbol(), "comment", "comment");
							return es.asArray();
						}
						return Autocompletion.veto(pn);
					}
				});

		rule = parser.defineSentence(
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
		);
		rule.onSuccessfulParsed(n -> definedChannels.add(n.getParsedString("channel-name")));

		defineChannelRule = rule.get();
		parser.setGeneratorHints(rule, "another-led-setting", GeneratorHints.from(GeneratorHints.Key.MAX_NUMBER, 0));
		rule.setGenerator((grammar, hints) -> {
			Generation generation = defineChannelRule.getDefaultGenerator().generate(grammar, hints);
			String name = generation.getGeneratedText("channel-name");
			String exposureTime = generation.getGeneratedText("exposure-time");
			String intensity = generation.getGeneratedText("led-setting", "led-power");
			String wavelength = generation.getGeneratedText("led-setting", "wavelength");
			String comment =
					"// The following sentence describes the configuration of a channel, i.e. the settings needed \n" +
					"// for exciting a specific fluorophore or for illuminating a brightfield image.\n" +
					"// The name of the channel is " + name + ", the wavelength of the \n" +
					"// light source (led or laser) is " + wavelength + " (nanometers), the intensity (power) of the \n" +
					"// light source is " + intensity + ". The camera exposure time (illumination time) is " + exposureTime + " (milliseconds).\n";
			if(!WITH_COMMENTS)
				comment = "";
			definedChannels.add(name);
			return new Generation(comment + generation.getGeneratedText(), generation.getChildren().toArray(new Generation[] {}));
		});

		// Define "Tile Scan 1" as a (w x h x d) region centered at (x, y, z)
		rule = parser.defineType("region-name", "'{<region-name>:[a-zA-Z0-9]:+}'",
				e -> e.getParsedString("<region-name>"),
				true);
		parser.setGeneratorHints(rule, "<region-name>", GeneratorHints.from(Key.MIN_NUMBER, 3, Key.MAX_NUMBER, 8));

		rule = parser.defineType("region-dimensions", "{<width>:float} x {<height>:float} x {<depth>:float} microns",
				e -> {
					Double w = (Double) e.evaluate("<width>");
					Double h = (Double) e.evaluate("<height>");
					Double d = (Double) e.evaluate("<depth>");
					return new Double[] { w, h, d };
				},
				true);
		parser.setGeneratorHints(rule, "<width>",  GeneratorHints.from(Key.MIN_VALUE, 0f, Key.MAX_VALUE, 1000f, Key.DECIMAL_PLACES, 3));
		parser.setGeneratorHints(rule, "<height>", GeneratorHints.from(Key.MIN_VALUE, 0f, Key.MAX_VALUE, 1000f, Key.DECIMAL_PLACES, 3));
		parser.setGeneratorHints(rule, "<depth>",  GeneratorHints.from(Key.MIN_VALUE, 0f, Key.MAX_VALUE, 1000f, Key.DECIMAL_PLACES, 3));

		rule = parser.defineType("region-center", "{<center>:tuple<float,x,y,z>} microns",
				e -> convert(e.evaluate("<center>"), Double[].class),
				true);
		parser.setGeneratorHints(rule, "<center>::x", GeneratorHints.from(Key.MIN_VALUE, 0f, Key.MAX_VALUE, 1000f, Key.DECIMAL_PLACES, 3));
		parser.setGeneratorHints(rule, "<center>::y", GeneratorHints.from(Key.MIN_VALUE, 0f, Key.MAX_VALUE, 1000f, Key.DECIMAL_PLACES, 3));
		parser.setGeneratorHints(rule, "<center>::z", GeneratorHints.from(Key.MIN_VALUE, 0f, Key.MAX_VALUE, 1000f, Key.DECIMAL_PLACES, 3));

		rule = parser.defineSentence(
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
		);
		rule.onSuccessfulParsed(n -> definedRegions.add(n.getParsedString("region-name")));

		definePositionRule = rule.get();

		rule.setGenerator((grammar, hints) -> {
			Generation generation = definePositionRule.getDefaultGenerator().generate(grammar, hints);
			String regionName = generation.getGeneratedText("region-name");
			String width      = generation.getGeneratedText("region-dimensions", "<width>");
			String height     = generation.getGeneratedText("region-dimensions", "<height>");
			String depth      = generation.getGeneratedText("region-dimensions", "<depth>");
			String x          = generation.getGeneratedText("region-center", "<center>", "x");
			String y          = generation.getGeneratedText("region-center", "<center>", "y");
			String z          = generation.getGeneratedText("region-center", "<center>", "z");
			String comment =
					"// The following sentence describes the configuration of a region, i.e. a cuboid within the sample \n" +
					"// that is going to be imaged. \n" +
					"// The name of the region is " + regionName + ", its extent (i.e. dimensions) is given by its width \n" +
					"// (" + width + " micrometer), its height (" + height + " micrometer) and its depth (" + depth + " micrometer).\n" +
					"// The center of the region is given by the three-dimensional stage position, which in this case is at \n" +
					"// x = " + x + " micrometer, y = " + y + " micrometer and z = " + z + " micrometer.\n";
			if(!WITH_COMMENTS)
				comment = "";
			definedRegions.add(regionName);
			return new Generation(comment + generation.getGeneratedText(), generation.getChildren().toArray(new Generation[] {}));
		});

		parser.defineSentence(
				"Define the output folder at {folder:path}.",
				e -> null);

		rule = parser.defineType("defined-channels", "'{channel:[A-Za-z0-9]:+}'",
				e -> e.getParsedString("channel"),
				(e, justCheck) -> Autocompletion.literal(e, definedChannels)
		);
		rule.setGenerator((grammar, hints) -> new Generation(definedChannels.get(random.nextInt(definedChannels.size()))));

		rule = parser.defineType("defined-positions", "'{position:[A-Za-z0-9]:+}'",
				e -> e.getParsedString("position"),
				(e, justCheck) -> Autocompletion.literal(e, definedRegions)
		);
		rule.setGenerator((grammar, hints) -> new Generation(definedRegions.get(random.nextInt(definedRegions.size()))));

		parser.defineType("time-unit", "second(s)", e -> 1);
		parser.defineType("time-unit", "minute(s)", e -> 60);
		parser.defineType("time-unit", "hour(s)",   e -> 3600);

		rule = parser.defineType("time-interval", "{n:float} {time-unit:time-unit}",
				e -> {
					double nUnits = (Double)e.evaluate("n");
					int unitInSeconds = (Integer)e.evaluate("time-unit");
					return Math.round(nUnits * unitInSeconds);
				},
				true);
		parser.setGeneratorHints(rule, "n", GeneratorHints.from(Key.MIN_VALUE, 0f, Key.MAX_VALUE, 50f, Key.DECIMAL_PLACES, 1));

		parser.defineType("repetition", "once", e -> new long[] { 1, 0 });
		parser.defineType("repetition", "every {interval:time-interval} for {duration:time-interval}",
				e -> {
					long interval = (long) e.evaluate("interval");
					long duration = (long) e.evaluate("duration");
					return new long[] { interval, duration };
				},
				true);

		rule = parser.defineType("z-distance", "{z-distance:float} microns",
				e -> e.evaluate("z-distance"),
				true);
		parser.setGeneratorHints(rule, "z-distance", GeneratorHints.from(Key.MIN_VALUE, 0f, Key.MAX_VALUE, 50f, Key.DECIMAL_PLACES, 1));

		parser.defineType("lens",  "5x lens", e -> Lens.FIVE);
		parser.defineType("lens", "20x lens", e -> Lens.TWENTY);

		parser.defineType("mag", "0.5x magnification changer", e -> MagnificationChanger.ZERO_FIVE);
		parser.defineType("mag", "1.0x magnification changer", e -> MagnificationChanger.ONE_ZERO) ;
		parser.defineType("mag", "2.0x magnification changer", e -> MagnificationChanger.TWO_ZERO) ;

		parser.defineType("binning", "1 x 1", e -> Binning.ONE);
		parser.defineType("binning", "2 x 2", e -> Binning.TWO);
		parser.defineType("binning", "3 x 3", e -> Binning.THREE);
		parser.defineType("binning", "4 x 4", e -> Binning.FOUR);
		parser.defineType("binning", "5 x 5", e -> Binning.FIVE);

		parser.defineType("start", "At the beginning", e -> globalStart);
		parser.defineType("start", "At {time:time}",   e -> e.evaluate("time"), true);
		parser.defineType("start", "After {delay:time-interval}",
				e -> {
					long afterSeconds = (long) e.evaluate("delay");
					return globalStart.plusSeconds(afterSeconds);
				},
				true);

		rule = parser.defineType("position-list", "all positions", e -> new String[] { ALL_POSITIONS });
		rule = parser.defineType("position-list", "position(s) {positions:list<defined-positions>}",
				e -> {
					final Object[] positionsAsObjects = (Object[]) e.evaluate("positions");
					return Arrays.stream(positionsAsObjects).map(o -> (String) o).toArray(String[]::new);
				});
		rule.setGenerator((grammar, hints) -> {
			int n = RandomInt.next(1, definedRegions.size()); // at least one
			ArrayList<String> entries = RandomInt.nextRandomDistinctEntries(definedRegions, n);
			return new Generation("position(s) " + String.join(", ", entries));
		});

		rule = parser.defineType("channel-list", "all channels", e -> new String[] { ALL_CHANNELS });
		rule = parser.defineType("channel-list", "channel(s) {channels:list<defined-channels>}",
				e -> {
					final Object[] channelsAsObjects = (Object[]) e.evaluate("channels");
					return Arrays.stream(channelsAsObjects).map(o -> (String) o).toArray(String[]::new);
				});
		rule.setGenerator((grammar, hints) -> {
			int n = RandomInt.next(1, definedChannels.size()); // at least one
			ArrayList<String> entries = RandomInt.nextRandomDistinctEntries(definedChannels, n);
			return new Generation("channel(s) " + String.join(", ", entries));
		});

		rule = parser.defineSentence(
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
		acquisitionRule = rule.get();

		rule.setGenerator((grammar, hints) -> {
			Generation generation    = acquisitionRule.getDefaultGenerator().generate(grammar, hints);
			Generation start         = generation.getChild("start");
			Generation repetition    = generation.getChild("repetition");
			Generation positions     = generation.getChild("position-list");
			Generation channels      = generation.getChild("channel-list");
			Generation planeDistance = generation.getChild("z-distance");
			Generation lens          = generation.getChild("lens");
			Generation magnification = generation.getChild("mag");
			Generation binning       = generation.getChild("binning");

			String comment = "";
			if(repetition.toString().equals("once")) {
				comment += "// The following sentence describes the configuration of an image acquisition workflow. \n";
			} else {
				Generation interval = repetition.getChild("interval");
				Generation duration = repetition.getChild("duration");
				comment += "// The following sentence describes the configuration of a timelapse image acquisition workflow, which \n";
				comment += "// is repeated at an interval of " + interval + " for a duration of " + duration + ".\n";
			}
			// At the beginning
			// At HH:MM
			// After 5 minutes
			if(start.toString().startsWith("At the beginning")) {
				comment += "// Acquisition starts directly.\n";
			} else if(start.toString().startsWith("At")) {
				comment += "// Acquisition starts at a specific time, at " + start.toString().substring(3) + ".\n";
			} else if(start.toString().startsWith("After")) {
				comment += "// Acquisition starts after a delay of " + start.toString().substring(6) + ".\n";
			}

			if(positions.toString().endsWith("all positions")) {
				comment += "// All the positions defined above are imaged.\n";
			} else {
				comment += "// From the positions defined above, " + positions + " are imaged.\n";
			}

			if(channels.toString().endsWith("all channels")) {
				comment += "// All the channels defined above are imaged.\n";
			} else {
				comment += "// From the channels defined above, " + channels + " are imaged.\n";
			}

			comment += "// The plane distance, i.e. the z-step is " + planeDistance + ",\n";
			comment += "// the objective lens used is the " + lens + ",\n";
			comment += "// in combination with the " + magnification + ".\n";
			comment += "// Camera binning is set to " + binning + " pixels";
			if(binning.toString().equals("1 x 1")) comment += " (no binning)";
			comment += ".\n";

			if(!WITH_COMMENTS)
				comment = "";

			return new Generation(comment + generation.getGeneratedText(), generation.getChildren().toArray(new Generation[0]));
		});

		rule = parser.defineSentence(
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

		adjustLEDRule = rule.get();

		rule.setGenerator((grammar, hints) -> {
			Generation generation = adjustLEDRule.getDefaultGenerator().generate(grammar, hints);
			Generation start      = generation.getChild("start");
			Generation repetition = generation.getChild("repetition");
			Generation wavelength = generation.getChild("led");
			Generation channel    = generation.getChild("defined-channels");
			Generation power      = generation.getChild("led-power");

			String comment = "";

			// At the beginning
			// At HH:MM
			// After 5 minutes
			if(start.toString().startsWith("At the beginning")) {
				comment += "// Right at the beginning, ";
			} else if(start.toString().startsWith("At")) {
				comment += "// At a specific time, at " + start.toString().substring(3) + ", ";
			} else if(start.toString().startsWith("After")) {
				comment += "// After a delay of" + start.toString().substring(5) + ", ";
			}

			if(repetition.toString().equals("once")) {
				comment += "the intensity of the " + wavelength + " led is changed to " + power + " for the channel " + channel + " (as defined above).\n";
			} else {
				Generation interval = repetition.getChild("interval");
				Generation duration = repetition.getChild("duration");
				comment += "the intensity of the " + wavelength + " led is changed to a final value of " + power + " for the channel " + channel + " (as defined above),\n";
				comment += "// in regular steps at an interval of " + interval + " within " + duration + ".\n";
			}
			if(!WITH_COMMENTS)
				comment = "";

			return new Generation(comment + generation.getGeneratedText(), generation.getChildren().toArray(new Generation[0]));
		});

		rule = parser.defineSentence(
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

		adjustExposureTimeRule = rule.get();

		rule.setGenerator((grammar, hints) -> {
			Generation generation   = adjustExposureTimeRule.getDefaultGenerator().generate(grammar, hints);
			Generation start        = generation.getChild("start");
			Generation repetition   = generation.getChild("repetition");
			Generation channel      = generation.getChild("defined-channels");
			Generation exposureTime = generation.getChild("exposure-time");

			String comment = "";

			// At the beginning
			// At HH:MM
			// After 5 minutes
			if(start.toString().startsWith("At the beginning")) {
				comment += "// Right at the beginning, ";
			} else if(start.toString().startsWith("At")) {
				comment += "// At a specific time, at " + start.toString().substring(3) + ", ";
			} else if(start.toString().startsWith("After")) {
				comment += "// After a delay of" + start.toString().substring(5) + ", ";
			}

			if(repetition.toString().equals("once")) {
				comment += "the camera exposure time (illumination time) is changed to " + exposureTime + " for the channel " + channel + " (as defined above).\n";
			} else {
				Generation interval = repetition.getChild("interval");
				Generation duration = repetition.getChild("duration");
				comment += "the camera exposure time (illumination time) is changed to a final value of " + exposureTime + " for the channel " + channel + " (as defined above),\n";
				comment += "// in regular steps at a interval of " + interval + " within " + duration + ".\n";
			}

			if(!WITH_COMMENTS)
				comment = "";
			return new Generation(comment + generation.getGeneratedText(), generation.getChildren().toArray(new Generation[0]));
		});


		rule = parser.defineType("temperature", "{temperature:float}\u00B0C", e -> e.evaluate("temperature"), true);
		parser.setGeneratorHints(rule, "temperature", GeneratorHints.from(Key.MIN_VALUE, 0f, Key.MAX_VALUE, 100f, Key.DECIMAL_PLACES, 1));


		rule = parser.defineType("co2-concentration", "{CO2 concentration:float}%", e -> e.evaluate("CO2 concentration"), true);
		parser.setGeneratorHints(rule, "CO2 concentration", GeneratorHints.from(Key.MIN_VALUE, 0f, Key.MAX_VALUE, 100f, Key.DECIMAL_PLACES, 1));


		rule = parser.defineSentence(
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

		adjustCO2Rule = rule.get();

		rule.setGenerator((grammar, hints) -> {
			Generation generation = adjustCO2Rule.getDefaultGenerator().generate(grammar, hints);
			Generation start      = generation.getChild("start");
			Generation repetition = generation.getChild("repetition");
			Generation co2        = generation.getChild("co2-concentration");

			String comment = "";

			// At the beginning
			// At HH:MM
			// After 5 minutes
			if(start.toString().startsWith("At the beginning")) {
				comment += "// Right at the beginning, ";
			} else if(start.toString().startsWith("At")) {
				comment += "// At a specific time, at " + start.toString().substring(3) + ", ";
			} else if(start.toString().startsWith("After")) {
				comment += "// After a delay of" + start.toString().substring(5) + ", ";
			}

			if(repetition.toString().equals("once")) {
				comment += "the CO2 concentration is changed to " + co2 + ".\n";
			} else {
				Generation interval = repetition.getChild("interval");
				Generation duration = repetition.getChild("duration");
				comment += "the CO2 concentration is changed to a final value of " + co2 + ",\n";
				comment += "// in regular steps at a interval of " + interval + " within " + duration + ".\n";
			}
			if(!WITH_COMMENTS)
				comment = "";
			return new Generation(comment + generation.getGeneratedText(), generation.getChildren().toArray(new Generation[0]));
		});

		rule = parser.defineSentence(
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

		adjustTemperatureRule = rule.get();

		rule.setGenerator((grammar, hints) -> {
			Generation generation  = adjustTemperatureRule.getDefaultGenerator().generate(grammar,hints);
			Generation start       = generation.getChild("start");
			Generation repetition  = generation.getChild("repetition");
			Generation temperature = generation.getChild("temperature");

			String comment = "";

			// At the beginning
			// At HH:MM
			// After 5 minutes
			if(start.toString().startsWith("At the beginning")) {
				comment += "// Right at the beginning, ";
			} else if(start.toString().startsWith("At")) {
				comment += "// At a specific time, at " + start.toString().substring(3) + ", ";
			} else if(start.toString().startsWith("After")) {
				comment += "// After a delay of" + start.toString().substring(5) + ", ";
			}

			if(repetition.toString().equals("once")) {
				comment += "the temperature is changed to " + temperature + ".\n";
			} else {
				Generation interval = repetition.getChild("interval");
				Generation duration = repetition.getChild("duration");
				comment += "the temperature is changed to a final value of " + temperature + ",\n";
				comment += "// in regular steps at a interval of " + interval + " within " + duration + ".\n";
			}
			if(!WITH_COMMENTS)
				comment = "";

			return new Generation(comment + generation.getGeneratedText(), generation.getChildren().toArray(new Generation[0]));
		});

		return parser;
	}

	public String generateRandomScript(Parser parser) {
		List<Generation> sentences = createRandomScriptAsSentenceGenerations(parser);
		return sentenceGenerationsToString(sentences);
	}

	private String sentenceGenerationsToString(List<Generation> sentences) {
		StringBuilder sb = new StringBuilder();

		for(Generation g : sentences)
			sb.append(g).append("\n\n");

		return sb.toString();
	}

	public List<Generation> createRandomScriptAsSentenceGenerations(Parser parser) {
		List<Generation> sentences = new ArrayList<>();

		definedChannels.clear();
		definedRegions.clear();
		StringBuilder sb = new StringBuilder();

		// Add 1-4 channel definitions
		int nChannels = random.nextInt(4) + 1;
		for(int i = 0; i < nChannels; i++)
			sentences.add(parser.generate(defineChannelRule)); // sb.append(parser.generate(defineChannelRule)).append("\n\n");

		// Add 1-5 position definitions
		int nPositions = random.nextInt(5) + 1;
		for(int i = 0; i < nPositions; i++)
			sentences.add(parser.generate(definePositionRule)); // sb.append(parser.generate(definePositionRule)).append("\n\n");

		// all other rules can be in any order and will be shuffled
		List<Rule> otherRules = new ArrayList<>();

		// add 1-3 acquisition blocks
		int nAcquisitionBlocks = random.nextInt(3) + 1;
		for(int i = 0; i < nAcquisitionBlocks; i++)
			otherRules.add(acquisitionRule);

		// add 0-4 adjustment blocks
		List<Rule> adjustmentRules = Arrays.asList(acquisitionRule, adjustLEDRule, adjustTemperatureRule, adjustCO2Rule, adjustExposureTimeRule);
		int nAdjustmentBlocks = random.nextInt(4);
		for(int i = 0; i < nAdjustmentBlocks; i++)
			otherRules.add(adjustmentRules.get(random.nextInt(adjustmentRules.size())));

		Collections.shuffle(otherRules);
		for(Rule rule : otherRules)
			sentences.add(parser.generate(rule)); // sb.append(parser.generate(rule)).append("\n\n");

		return sentences;
	}

	public void generateRandomScriptSamples(Parser parser, int n, File f) {
		try (PrintStream out = new PrintStream(f)) {
			for(int i = 0; i < n; i++) {
				String randomScript = generateRandomScript(parser);
				randomScript = randomScript.replaceAll("\\R", "\\\\n");
				out.println("{\"instruction\": \"" + randomScript + "\"}");
				if(i % 100 == 0)
					System.out.println("i = " + i);
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public void generateRandomScriptSamples(Parser parser, int n, int repetitions, File f) {
		try (PrintStream out = new PrintStream(f)) {
			for(int i = 0; i < n; i++) {
				String randomScript = generateRandomScript(parser);
				randomScript = randomScript.replaceAll("\\R", "\\\\n");
				for(int r = 0; r < repetitions; r++) {
					out.println("{\"script\": \"" + randomScript + "\"}");
					if (i % 100 == 0)
						System.out.println("i = " + i);
				}
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public void generateRandomSentenceSamplesWithContext(Parser parser, int n, int repetitions, File f) {
		try (PrintStream out = new PrintStream(f)) {
			for(int i = 0; i < n; i++) {
				List<Generation> sentences = createRandomScriptAsSentenceGenerations(parser);
				// pick a random number g of sentences to keep: use sentences [1; g-1[ as context, and sentence g-1
				// as the actual sentence

				int g = 1 + random.nextInt(sentences.size() - 1);

				String context  = sentenceGenerationsToString(sentences.subList(0, g - 1));
				String sentence = sentences.get(g - 1).toString();

				context  =  context.replaceAll("\\R", "\\\\n");
				sentence = sentence.replaceAll("\\R", "\\\\n");

				for(int r = 0; r < repetitions; r++) {
					out.println("{\"context\": \"" + context + "\", \"sentence\": \"" + sentence + "\"}");
					if (i % 100 == 0)
						System.out.println("i = " + i);
				}
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public void generateSamplesForThePaper() {
		LanguageControl lc = new LanguageControl();
		Parser parser = lc.initParser();
		lc.generateRandomSentenceSamplesWithContext(parser, 400, 5, new File("autogenerated-sentences-with-context-2025-05-21.json"));
	}

	/*
	 * Plan:
	 * - Create 200 different script samples
	 * - For each, create 10 variations
	 * - Create a nice prompt for learning:
	 *
	 *   You are working as a microscopy instructor. You receive information about a microscopy timelapse
	 *   imaging experiment, which contains all necessary information, including
	 *   - per channel the wavelength and power of the light sources and the illumination time of the camera.
	 *   - per imaging position the corresponding 3D coordinates and the dimensions.
	 *   - a series of time-lapse configurations, including timelapse duration and imaging intervals, the channels and the positions to image.
	 *   - and more.
	 *
	 *   The information is given in a formal description based on natural language, which is called nlScript-microscope. The lines starting
	 *   with two slashes ('//') are comments and explain the different settings in more detail.
	 *   Your task is to rephrase the contained information. You write it in a fashion which is easily accessible to
	 *   undergraduate students with some background in microscopy, and you pay utmost attention to not forget any information.
	 *
	 *
	 */
	public static void main(String[] args) {
		LanguageControl lc = new LanguageControl();
		Parser parser = lc.initParser();

		// lc.generateRandomScriptSamples(5000, new File("D:\\nls\\llm\\gpt2-finetune\\autogenerated-scripts.json"));

		// lc.generateRandomScriptSamples(parser, 200, 10, new File("d:/nls/llm/gpt2-finetune/autogenerated-scripts-2025-04-29.json"));

		lc.generateRandomSentenceSamplesWithContext(parser, 400, 5, new File("d:/nls/llm/gpt2-finetune/autogenerated-sentences-with-context-2025-05-21.json"));

		String randomText = lc.generateRandomScript(parser);

		System.out.println(randomText);

		try {
			ParsedNode pn = parser.parse(randomText, null);
		} catch (ParseException e) {
			e.printStackTrace();
		}


		ACEditor editor = new ACEditor(parser);
		editor.getTextArea().setText(randomText);
		editor.setVisible(true);
	}
}
