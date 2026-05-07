package thread_example;

import java.util.Random;
import java.util.Scanner;

class MyThread extends Thread {
    private int runningTime;

    MyThread(int runningTime) {
        this.runningTime = runningTime;
    }

    public void run() {
        System.out.println("Thread " + threadId() + " started and will run for " + runningTime + " seconds.");
        try {
            sleep(runningTime * 1000L);
            System.out.println("Thread " + threadId() + " ended.");
        } catch (InterruptedException e) {
            System.err.println("Thread " + threadId() + " abruptly closed.");
        }
    }
}

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("How many threads to create? ");
        int nThreads = sc.nextInt();

        // Now for each thread, choose a random running time between minSecs and maxSecs
        Random random = new Random();
        final int minSecs = 0;
        final int maxSecs = 10;

        for (int i = 0; i < nThreads; i++) {
            int randomRunningTime = random.nextInt(maxSecs - minSecs + 1) + minSecs;
            (new MyThread(randomRunningTime)).start();
        }

        sc.close();
    }
}
