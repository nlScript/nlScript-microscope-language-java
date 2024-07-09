package nlScript.mic;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

public class Timeline<E> {

	private final TreeMap<LocalDateTime, ArrayList<E>> timeline = new TreeMap<>();

	public void put(LocalDateTime time, E entry) {
		ArrayList<E> list = timeline.computeIfAbsent(time, k -> new ArrayList<>());
		list.add(entry);
	}

	public void runAndRemoveEntriesBefore(LocalDateTime time, Consumer<E> function) {
		ArrayList<LocalDateTime> timesToRemove = new ArrayList<>();
		for(LocalDateTime entryTime : timeline.keySet()) {
			if(entryTime.isBefore(time)) {
				timesToRemove.add(entryTime);
				for(E entry : timeline.get(entryTime))
					function.accept(entry);
			}
			else
				break;
		}
		timesToRemove.forEach(timeline.keySet()::remove);
	}

	private final AtomicBoolean stop = new AtomicBoolean(false);
	private ExecutorService executor;

	public void process(Consumer<E> function) {
		stop.set(false);
		executor = Executors.newSingleThreadExecutor();
		executor.submit(() -> {
			while (!stop.get() && !timeline.isEmpty()) {
				Timeline.this.runAndRemoveEntriesBefore(LocalDateTime.now(), function);
				if(timeline.isEmpty())
					return;
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException ignored) {
			}
		});
	}

	public void waitForProcessing() {
		executor.shutdown();
		try {
			executor.awaitTermination(1, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void cancel() {
		stop.set(true);
		waitForProcessing();
	}

	public void clear() {
		timeline.clear();
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for(LocalDateTime t : timeline.keySet()) {
			ArrayList<E> entries = timeline.get(t);
			sb.append(t).append(" -> ").append(entries);
		}
		return sb.toString();
	}
}
