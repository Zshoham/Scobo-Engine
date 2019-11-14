package util;

import java.util.ArrayList;

public class Timer {

    private long startTime;

    private ArrayList<Long> samples = new ArrayList<>();

    public Timer() {
        startTime = System.nanoTime();
    }

    public long time() {
        return (System.nanoTime() - startTime) / 1000000;
    }

    public void sample() {
        samples.add(time());
    }

    public double average() {
        double sum = 0;
        for (Long sample : samples) sum += sample;

        return sum / samples.size();
    }
}
