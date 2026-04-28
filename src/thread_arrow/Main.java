package thread_arrow;

import java.util.Scanner;

class Main {
    public static void main(String[] args) {
        final int threadRunningTime = 5;
        final int threadCreateWaitTime = 2;

        Scanner sc = new Scanner(System.in);
        System.out.print("How many threads to create? ");
        int nThreads = sc.nextInt();

        for (int i = 0; i < nThreads; i++) {
            final int threadNo = i;

            new Thread(() -> {
                System.out.println("Thread #" + threadNo + " started");

                try {
                    Thread.sleep(1000 * threadRunningTime);
                } catch (InterruptedException e) {
                    System.err.println("Error when invoking sleep on Thread#" + threadNo + " : " + e.getMessage());
                }

                System.out.println("Thread #" + threadNo + " ended");
            }).start();

            try {
                Thread.sleep(1000 * threadCreateWaitTime);
            } catch (InterruptedException e) {
                System.err.println("Error when sleeping in main thread: " + e.getMessage());
            }
        }

        sc.close();
    }
}
