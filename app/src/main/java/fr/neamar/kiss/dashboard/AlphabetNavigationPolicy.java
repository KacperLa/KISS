package fr.neamar.kiss.dashboard;

/** The rail uses only the sorted, nonempty Latin app groups in the current feed. */
public final class AlphabetNavigationPolicy {
    private AlphabetNavigationPolicy() {}

    public static char at(String letters, float y, float height) {
        if (letters.isEmpty()) return 0;
        float fraction = height <= 0 ? 0 : Math.max(0, Math.min(1, y / height));
        return letters.charAt(Math.round(fraction * (letters.length() - 1)));
    }

    public static float position(String letters, char letter, float height) {
        int index = letters.indexOf(letter);
        return index < 0 || letters.length() < 2 ? 0 : height * index / (letters.length() - 1);
    }

    public static char step(String letters, char current, boolean forward) {
        if (letters.isEmpty()) return 0;
        int index = letters.indexOf(current);
        if (index < 0) return letters.charAt(forward ? 0 : letters.length() - 1);
        return letters.charAt(Math.max(0, Math.min(letters.length() - 1, index + (forward ? 1 : -1))));
    }
}
