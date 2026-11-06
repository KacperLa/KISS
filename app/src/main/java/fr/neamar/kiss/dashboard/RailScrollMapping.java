package fr.neamar.kiss.dashboard;
/** Piecewise mapping between actual feed anchors and rail anchors, not raw page percentage. */
public final class RailScrollMapping {
    private RailScrollMapping() {}
    public static float interpolate(float value, float[] source, float[] target) {
        if (source.length == 0 || source.length != target.length) return 0;
        if (value <= source[0]) return target[0];
        for (int i = 1; i < source.length; i++) {
            if (value <= source[i]) {
                float span = source[i] - source[i - 1];
                if (span <= 0) return target[i];
                return target[i - 1] + (value - source[i - 1]) / span * (target[i] - target[i - 1]);
            }
        }
        return target[target.length - 1];
    }
}
