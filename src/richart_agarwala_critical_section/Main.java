package richart_agarwala_critical_section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Process {
    int pid;
    int clock = 0;

    boolean requesting = false;
    int requestTime = Integer.MAX_VALUE;

    int replyCount = 0;

    int[] replyStatus; // 0 or 1
    List<Integer> deferred = new ArrayList<>();

    static List<Process> processes;
    static final Object lock = new Object();
    static int N;

    public Process(int pid, int N) {
        this.pid = pid;
        this.replyStatus = new int[N];
    }

    public void requestCS() {
        synchronized (lock) {
            clock++;
            requestTime = clock;
            requesting = true;
            replyCount = 0;

            Arrays.fill(replyStatus, 0);

            System.out.println("\nProcess " + pid + " requesting CS at time " + requestTime);
        }

        // Send request to all other processes
        for (Process p : processes) {
            if (p.pid != this.pid) {
                new Thread(() -> p.receiveRequest(pid, requestTime)).start();
            }
        }

        // Wait for all replies
        while (true) {
            synchronized (lock) {
                if (replyCount == N - 1) break;
            }
            try {
                Thread.sleep(50);
            } catch (Exception e) {
            }
        }

        enterCS();
    }

    public void receiveRequest(int senderId, int timestamp) {
        synchronized (lock) {
            clock = Math.max(clock, timestamp) + 1;

            Process sender = processes.get(senderId);

            boolean sendReply = false;

            if (!requesting) {
                sendReply = true;
            } else if (timestamp < requestTime) {
                sendReply = true;
            } else if (timestamp == requestTime && senderId < pid) {
                sendReply = true;
            }

            if (sendReply) {
                sender.receiveReply(pid);
            } else {
                deferred.add(senderId);
            }
        }
    }

    public void receiveReply(int senderId) {
        synchronized (lock) {
            if (replyStatus[senderId] == 0) {
                replyStatus[senderId] = 1;
                replyCount++;
            }
        }
    }

    public void enterCS() {
        synchronized (lock) {
            System.out.println(">>> Process " + pid + " ENTERING CS");

            printStatus();

            try {
                Thread.sleep(500);
            } catch (Exception e) {
            }

            exitCS();
        }
    }

    public void exitCS() {
        synchronized (lock) {
            requesting = false;
            requestTime = Integer.MAX_VALUE;

            System.out.println("<<< Process " + pid + " EXITING CS");

            // Send replies to deferred processes
            for (int pId : deferred) {
                processes.get(pId).receiveReply(pid);
            }
            deferred.clear();
        }
    }

    public void printStatus() {
        System.out.print("Reply Status for Process " + pid + ": ");
        for (int i = 0; i < replyStatus.length; i++) {
            if (i != pid)
                System.out.print("P" + i + "=" + replyStatus[i] + " ");
        }
        System.out.println("\nSUCCESS: All replies received.\n");
    }
}

class Main {
    public static void main(String[] args) {

        int N = 5; // number of processes (4–6)
        int M = 3; // number of requests per process

        Process.N = N;
        Process.processes = new ArrayList<>();

        // Initialize processes
        for (int i = 0; i < N; i++) {
            Process.processes.add(new Process(i, N));
        }

        ExecutorService executor = Executors.newFixedThreadPool(N);

        // Each process makes M requests
        for (Process p : Process.processes) {
            executor.execute(() -> {
                for (int i = 0; i < M; i++) {
                    try {
                        Thread.sleep(new Random().nextInt(1000));
                    } catch (Exception e) {
                    }

                    p.requestCS();
                }
            });
        }

        executor.shutdown();
    }
}