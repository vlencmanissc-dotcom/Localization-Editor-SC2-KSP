package lv.lenc;

@FunctionalInterface
public interface ProgressListener {
    /** fraction: 0.0 .. 1.0 */
    void onProgress(double fraction, String text);
}
