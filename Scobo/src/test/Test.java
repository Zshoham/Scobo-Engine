package test;

import parser.Parser;

public class Test {

    private static class Timer {

        private long start;

        public Timer() {
            start = System.nanoTime();
        }

        public double stop() { return (System.nanoTime() - start) / 1000000; }
    }

    private static Timer timer;

    public static void stop() {
        System.out.println(timer.stop());
        System.exit(0);
    }

    public static void main(String[] args) {
        timer = new Timer();
        Parser parser = new Parser("C:\\Users\\Hod\\Desktop\\bgu\\Year3\\Sem5\\Information Retrival\\corpus\\corpus");
        parser.start();
    }
}
