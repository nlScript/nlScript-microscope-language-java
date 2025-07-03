# nlScript microscope language

A natural language for configuring complex microscope timelapse experiments, based on nlScript.

Even on modern microscopes, time-lapse experiments are often limited to data acquisition at fixed intervals. At each time point, images are acquired at a fixed number of planes, at fixed stage positions, possibly repeated for a fixed set of channels. The dynamic nature of biological processes often requires a more flexible imaging pipeline to be observed optimally, e.g., with the imaging interval adapted to the rate of change, with different channels imaged at varying intervals, or with different positions imaged with varying magnification. The hardware of modern microscope allows for such a flexibility, the limiting factor is the software. The reasons for this is the effort needed to design and develop a user interface that is intuitive and yet allows to configure such a flexible imaging experiment.

To address this gap, we have developed a language for describing flexible time-lapse imaging experiments on microscopes. While our language has been developed for the Zeiss Celldiscoverer 7, it is easily adaptable to other microscope modalities. Remarkably, only a few sentence templates are sufficient to configure time-lapse experiments that (i) provide flexible intervals, (ii) select positions and channels at each time point, (iii) adopt magnification, (iv) adopt channel settings such as laser power, (v) provide adjustable environmental settings (temperature and CO2 concentration).

More information about how to use nlScript to develop custom natural language interfaces can be found here:
- https://nlScript.github.io/nlScript-java
- https://github.com/nlScript/nlScript-java

This plugin also contains AI-support (https://github.com/nlScript/nlScript-llm)

You can interactively try the language (without AI-support) at https://nlScript.github.io/nlScript-microscope-language-js.

## Installation
#### With Maven:
```
<groupId>io.github.nlscript</groupId>
<artifactId>nlScript-microscope-language</artifactId>
<version>0.4.0</version>
```

#### With Fiji:

To make testing it easy, `nlScript-microscope-language` is also available as a Fiji (https://fiji.sc) plugin:

* Download the binary version from the GitHub [Releases](https://github.com/nlScript/nlScript-microscope-language-java/releases) page
* Copy it into Fiji's `plugins` folder (`Path-to-Fiji/plugins/`)
* Restart Fiji

## Execution
#### With Maven:
```
mvn compile exec:java -Dexec.mainClass="nlScript.mic.Main"
```
#### With Fiji:

Click on `Plugins>nlScript>Microscope language demo`

## License

This project is licensed under the MIT License - see the [LICENSE.txt](LICENSE.txt) file for details.

