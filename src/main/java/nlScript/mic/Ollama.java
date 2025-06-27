package nlScript.mic;

import java.io.*;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Ollama {

	public static class OllamaException extends Exception {
		public OllamaException(String msg) {
			super(msg);
		}

		public OllamaException(String msg, Throwable cause) {
			super(msg, cause);
		}
	}

	private static final String DEFAULT_HOST  = "localhost";
	private static final int    DEFAULT_PORT  = 11434;
	private static final String DEFAULT_MODEL = "mistral-mic";

	private static final String ADAPTER_RESOURCE_PATH = "/nlScript/mic/Lora_Adapters.zip";

	private String host = DEFAULT_HOST;

	private int port = DEFAULT_PORT;

	private String model = DEFAULT_MODEL;

	public Ollama() {
		loadFromPreferences();
	}

	public Ollama(Ollama other) {
		host = other.host;
		port = other.port;
		model = other.model;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
		saveToPreferences();
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
		saveToPreferences();
	}

	public String getModel() {
		return model;
	}

	private void loadFromPreferences() {
		Preferences prefs = Preferences.userNodeForPackage(Ollama.class);
		this.host  = prefs.get("OLLAMA_HOST",  DEFAULT_HOST);
		this.port  = prefs.getInt("OLLAMA_PORT",  DEFAULT_PORT);
	}

	private void saveToPreferences() {
		Preferences prefs = Preferences.userNodeForPackage(Ollama.class);
		prefs.put("OLLAMA_HOST", host);
		prefs.putInt("OLLAMA_PORT", port);
		try {
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException("Cannot save Ollama preferences", e);
		}
	}

	public static void main(String[] args) throws OllamaException {
//		Consumer<String> stdout = s -> System.out.println("out: " + s);
//		Consumer<String> stderr = s -> System.err.println("err: " + s);
//		Consumer<String> stdin  = s -> System.out.println("in:  " + s);
//
//		System.out.println(Arrays.toString(new Ollama().getAvailableModels(stdout, stderr, stdin)));
//
//		Ollama ollama = new Ollama();
//		ollama.createModel(
//				"xx", "Y:\\unsloth\\mistral\\results\\adapters",
//				stdout,
//				stderr,
//				stdin);
//		String input =
//				"Below is the description of a microscope timelapse experiment. Transfer this description into valid English microscope-nlScript code.\n" +
//				"\n" +
//				"### Instruction:\n" +
//				"\n" +
//				"Image DAPI with 35% power at 405nm and an exposure time of 150 ms.\n" +
//				"Image A488 with 50% power at 488nm and an exposure time of 150 ms.\n" +
//				"\n" +
//				"Image at position (500, 500, 100) with an extent of 400 x 400 x 100 microns\n" +
//				"\n" +
//				"Start a timelapse at 10 am, for 4 hours, and image both channels at the given position every 10 minutes.\n" +
//				"\n" +
//				"### Input:\n" +
//				"\n" +
//				"\n" +
//				"### Response:\n";
//
		String input = "Image DAPI with 35% power at 405nm and an exposure time of 150 ms.";
		new Ollama().query("", input, System.out::print);
	}

	public boolean isOllamaRunning(Consumer<String> stdout, Consumer<String> stderr, Consumer<String> stdin) {
		try {
			URL url = new URL("http://" + host + ":" + port);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			stdin.accept("GET " + url);
			conn.setConnectTimeout(1000);  // 1 second
			conn.setReadTimeout(1000);
			conn.connect();

			int code = conn.getResponseCode();
			readResponse(conn, stdout, stderr);
			conn.disconnect();
			return code == 200 || code == 404;
		} catch (Exception e) {
			return false;
		}
	}

	public boolean isModelAvailable(String modelName, Consumer<String> stdout, Consumer<String> stderr, Consumer<String> stdin) throws OllamaException {
		int responseCode;
		String response;
		try {
			URL url = new URL("http://" + host + ":" + port + "/api/tags");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			stdin.accept("GET " + url);
			conn.setConnectTimeout(1000);
			conn.setReadTimeout(2000);
			conn.connect();

			responseCode = conn.getResponseCode();
			response = readResponse(conn, stdout, stderr);
		} catch(Exception e) {
			throw new OllamaException("Error checking if model " + modelName + " is available", e);
		}

		if(responseCode != 200 && responseCode != 201)
			throw new OllamaException("Error checking if model " + modelName + " is available: HTTP response code: " + responseCode);

		// Naive string match (you can use a JSON parser for robustness)
		return response.contains("\"name\":\"" + modelName);
	}

	public static String[] getAvailableModels(String host, int port, Consumer<String> stdout, Consumer<String> stderr, Consumer<String> stdin) throws OllamaException {
		int responseCode;
		String response;
		try {
			URL url = new URL("http://" + host + ":" + port + "/api/tags");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			stdin.accept("GET " + url);
			conn.setConnectTimeout(1000);
			conn.setReadTimeout(2000);
			conn.connect();

			responseCode = conn.getResponseCode();
			response = readResponse(conn, stdout, stderr);
		} catch (Exception e) {
			throw new OllamaException("Error querying available models", e);
		}

		if(responseCode != 200 && responseCode != 201)
			throw new OllamaException("Error querying available models: HTTP response code: " + response);

		return extractAll(response, "name");
	}

	public String[] getAvailableModels(Consumer<String> stdout, Consumer<String> stderr, Consumer<String> stdin) throws OllamaException {
		return getAvailableModels(this.host, this.port, stdout, stderr, stdin);
	}

	public void createModel(Consumer<String> stdout, Consumer<String> stderr, Consumer<String> stdin) throws OllamaException{
		stdout.accept("Unzipping model\n");
		File modelBaseDir= unzipResourceToTempDir(ADAPTER_RESOURCE_PATH);
		stdout.accept("Model base dir is " + modelBaseDir.getAbsolutePath());
		createModel(model, modelBaseDir.getAbsolutePath(), stdout, stderr, stdin);
	}

	private void createModel(String modelName, String modelBaseDir, Consumer<String> stdout, Consumer<String> stderr, Consumer<String> stdin) throws OllamaException{
		Map<String, String> filesToUpload = new HashMap<>();

		File modelDir = new File(modelBaseDir);
		File[] modelFiles = modelDir.listFiles();
		if(modelFiles == null) {
			throw new OllamaException("Error creating the model: " + modelBaseDir + " is empty");
		}

		int responseCode;
		try {
			for (File file : modelFiles) {
				if (file.isDirectory()) {
					System.err.println("Skipping directory " + file.getAbsolutePath());
				}
				String digest = calculateSHA256(file);
				filesToUpload.put(file.getName(), digest);
				uploadBlob(file, digest, stdout, stderr, stdin);
			}

			String jsonInput = "{"
					+ "\"model\": \"" + modelName + "\","
					+ "\"from\": \"mistral\","
					+ "\"adapters\": {";

			for (String file : filesToUpload.keySet()) {
				String digest = filesToUpload.get(file);
				jsonInput += "\"" + file + "\": \"sha256:" + digest + "\",";
			}
			// delete last ',':
			jsonInput = jsonInput.substring(0, jsonInput.length() - 1);

			jsonInput = jsonInput
					+ "},"
					+ "\"template\": \"" + "{{ .Prompt }}" + "\","
					+ "\"parameters\": {"
					+   "\"stop\": [\"</s>\", \"<s>\", \"<unk>\", \"### Response:\", \"### Instruction:\", \"### Input:\"],"
					+   "\"temperature\": 0.1,"
					+   "\"top_k\": 50,"
					+   "\"top_p\": 1.0,"
					+   "\"repeat_penalty\": 1.0,"
					+   "\"num_predict\": 4096"
					+ "}"
					+ "}";

			URL url = new URL("http://" + host + ":" + port + "/api/create");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			stdin.accept("POST " + url + "\n");
			stdin.accept(jsonInput + "\n");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setDoOutput(true);
			conn.setConnectTimeout(2000);
			conn.setReadTimeout(10000);

			try (OutputStream os = conn.getOutputStream()) {
				os.write(jsonInput.getBytes(StandardCharsets.UTF_8));
			}

			responseCode = conn.getResponseCode();
			readResponse(conn, stdout, stderr);
		} catch (Exception e) {
			throw new OllamaException("Error creating the model", e);
		}

		if(responseCode != 201 && responseCode != 200) {
			throw new OllamaException("Error creating the model: HTTP response code: " + responseCode);
		}
	}

	private boolean blobExists(String digest, Consumer<String> stdout, Consumer<String> stderr, Consumer<String> stdin) throws OllamaException {
		int responseCode;
		try {
			URL url = new URL("http://" + host + ":" + port + "/api/blobs/sha256:" + digest);
			System.out.println("url = " + url);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("HEAD");
			stdin.accept("HEAD " + url);
			conn.setRequestProperty("Content-Type", "application/octet-stream");
			conn.setConnectTimeout(3000);
			conn.setReadTimeout(10000);

			responseCode = conn.getResponseCode();
			readResponse(conn, stdout, stderr);
		} catch(Exception e) {
			throw new OllamaException("Error while checking if blob exists", e);
		}

		if(responseCode == 200)
			return true;
		if(responseCode == 404)
			return false;

		throw new OllamaException("Error while checking if blob exists: HTTP response code: " + responseCode);
	}

	private void uploadBlob(File file, String digest,
							Consumer<String> stdout,
							Consumer<String> stderr,
							Consumer<String> stdin) throws OllamaException {
		if(blobExists(digest, stdout, stderr, stdin))
			return;

		int responseCode;
		try {
			URL url = new URL("http://" + host + ":" + port + "/api/blobs/sha256:" + digest);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			stdin.accept("POST " + file.getName() + " " + url);
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/octet-stream");
			conn.setConnectTimeout(10000);
			conn.setReadTimeout(300000);
			conn.setFixedLengthStreamingMode(file.length());

			try (OutputStream os = conn.getOutputStream();
				 FileInputStream fis = new FileInputStream(file)) {

				byte[] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = fis.read(buffer)) != -1) {
					os.write(buffer, 0, bytesRead);
				}
			}

			responseCode = conn.getResponseCode();
			readResponse(conn, stdout, stderr);
		} catch(Exception e) {
			throw new OllamaException("Error uploading blob (" + file + ")", e);
		}

		if (responseCode != 201)
			throw new OllamaException("Failed to upload blob (" + file + "): HTTP response code: " + responseCode);
	}

	private static String readResponse(HttpURLConnection conn, Consumer<String> stdout, Consumer<String> stderr) throws OllamaException {
		try {
			StringBuilder sb = new StringBuilder();
			String line;
			InputStream stream = null;
			try {
				stream = conn.getInputStream();
			} catch (IOException ignored) {
				// This happens when the HTTP response code indicates an error,
				// in which case we might be able to read from the error stream below
			}
			if(stream != null) {
				try (BufferedReader in = new BufferedReader(
						new InputStreamReader(stream))) {
					while ((line = in.readLine()) != null) {
						if (stdout != null)
							stdout.accept(line + "\n");
						sb.append(line).append("\n");
					}
				}
			}

			stream = conn.getErrorStream();
			if (stderr != null && stream != null) {
				try (BufferedReader err = new BufferedReader(
						new InputStreamReader(stream))) {
					while ((line = err.readLine()) != null) {
						stderr.accept(line + "\n");
					}
				}
			}
			return sb.toString();
		} catch(Exception e) {
			throw new OllamaException("Error reading HTTP response", e);
		}
	}

	private static String calculateSHA256(File file) throws NoSuchAlgorithmException, IOException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try(FileInputStream fis = new FileInputStream(file)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = fis.read(buffer)) != -1) {
				digest.update(buffer, 0, bytesRead);
			}
		}

		byte[] hashBytes = digest.digest();
		return bytesToHex(hashBytes);
	}

	private static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	public void query(String context, String sentence, Consumer<String> tokenConsumer) throws OllamaException {
		String prompt = "\n"
				+ "Below is the description of a microscope timelapse experiment. Transfer this description into valid microscope-nlScript code.\n"
				+ "\n"
				+ "### Instruction:\n"
				+ sentence + "\n"
				+ "\n"
				+ "### Input:\n"
				+ context + "\n"
				+ "\n"
				+ "### Response:\n";
		query(prompt, tokenConsumer);
	}

	public void query(String prompt, Consumer<String> tokenConsumer) throws OllamaException {
		String jsonInputString =
				"{" +
				"	\"model\": \"" + model + "\"," +
				"	\"prompt\": \"" + escapeJson(prompt) + "\"," +
				"	\"stream\": true" +
				"}";

		try {
			// Set up HTTP connection
			URL url = new URL("http://" + host + ":" + port + "/api/generate");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setDoOutput(true);

			// Send the request
			try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
				out.writeBytes(jsonInputString);
				out.flush();
			}

			// Read the streamed response
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.trim().isEmpty()) continue; // skip blank lines
					if (line.contains("\"done\":true")) break; // end of stream

					String token = extractResponse(line);
					if (token != null) {
						tokenConsumer.accept(token);
					}
				}
			}
			conn.disconnect();
		} catch(ConnectException e) {
			throw new OllamaException("Cannot connect to Ollama. Is Ollama installed and running on " + host + " at port " + port, e);
		} catch (IOException e) {
			throw new OllamaException("Cannot communicate with Ollama", e);
		}
	}

	private static String escapeJson(String jsString) {
		jsString = jsString.replace("\\", "\\\\");
		jsString = jsString.replace("\"", "\\\"");
		jsString = jsString.replace("\b", "\\b");
		jsString = jsString.replace("\f", "\\f");
		jsString = jsString.replace("\n", "\\n");
		jsString = jsString.replace("\r", "\\r");
		jsString = jsString.replace("\t", "\\t");
		jsString = jsString.replace("/", "\\/");
		return jsString;
	}

	// Extracts the "response" field from the streamed JSON chunk
	private static String extractResponse(String json) {
		return extract(json, "response");
	}

	private static String extract(String json, String key) {
		String marker = "\"" + key + "\":\"";
		int start = json.indexOf(marker);
		if (start == -1) return null;
		start += marker.length();
		int end = json.indexOf("\"", start);
		if (end == -1) return null;
		return json.substring(start, end).replace("\\n", "\n");
	}

	private static String[] extractAll(String json, String key) {
		String marker = "\"" + key + "\":\"";
		int start = 0;
		ArrayList<String> collected = new ArrayList<>();

		while(start < json.length()) {
			start = json.indexOf(marker, start);
			if (start == -1)
				return collected.toArray(new String[0]);
			start += marker.length();
			int end = json.indexOf("\"", start);
			if (end == -1)
				return collected.toArray(new String[0]);
			String value = json.substring(start, end).replace("\\n", "\n");
			start = end;
			collected.add(value);
		}

		return collected.toArray(new String[0]);
	}

	/**
	 * Loads a ZIP file from the classpath and extracts it to a temporary directory.
	 *
	 * @param resourcePath path in resources (e.g., "/models/model.zip")
	 * @return File object pointing to the temp directory
	 * @throws OllamaException if loading or extracting fails
	 */
	public static File unzipResourceToTempDir(String resourcePath) throws OllamaException {
		try {
			// Load zip as InputStream from classpath
			InputStream zipStream = Ollama.class.getResourceAsStream(resourcePath);
			if (zipStream == null) {
				throw new FileNotFoundException("Resource not found: " + resourcePath);
			}

			// Create a temp directory
			Path tempDir = Files.createTempDirectory("unzipped-");
			tempDir.toFile().deleteOnExit();

			try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(zipStream))) {
				ZipEntry entry;
				while ((entry = zis.getNextEntry()) != null) {
					File newFile = new File(tempDir.toFile(), entry.getName());

					// Make parent directories if needed
					if (entry.isDirectory()) {
						newFile.mkdirs();
					} else {
						File parent = newFile.getParentFile();
						if (!parent.exists()) parent.mkdirs();

						// Write file
						try (FileOutputStream fos = new FileOutputStream(newFile)) {
							byte[] buffer = new byte[4096];
							int len;
							while ((len = zis.read(buffer)) > 0) {
								fos.write(buffer, 0, len);
							}
						}
					}
				}
			}

			return tempDir.toFile();
		} catch(Exception e) {
			throw new OllamaException("Error unzipping model from " + resourcePath, e);
		}
	}
}
