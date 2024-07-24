package nlScript.mic;

public class Interpolator {

	public interface Setter {
		void set(int c, double v);
	}

	public interface Getter {
		double get();
	}

	private final Getter getter;
	private final Setter setter;

	private double vFrom;
	private final double vTo;

	private final int nCycles;

	public Interpolator(Getter getter, Setter setter, double vTo, int nCycles) {
		this.getter = getter;
		this.setter = setter;
		this.vTo = vTo;
		this.nCycles = nCycles;
	}

	private void initialize() {
		vFrom = getter.get();
	}

	public void interpolate(int cycle) {
		if(cycle == this.nCycles - 1) {
			setter.set(cycle, vTo);
			return;
		}

		if(cycle == 0)
			initialize();
		double interpolated = vFrom + cycle * (vTo - vFrom) / (nCycles - 1);
		setter.set(cycle, interpolated);
	}

	public static void main(String[] args) {
		int nCycles = 10;
		Interpolator interpolator = new Interpolator(() -> 5, (c, v) -> System.out.println(c + ": " + v), 15, nCycles);

		for(int i = 0; i < nCycles; i++)
			interpolator.interpolate(i);
	}
}
