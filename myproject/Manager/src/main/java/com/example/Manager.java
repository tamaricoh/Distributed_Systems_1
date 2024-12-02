package com.example;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Manager {

    private static final int THREAD_POOL_SIZE = 5;
    private ExecutorService threadPool;
    public AtomicInteger availableWorkers = new AtomicInteger(14);; 

    public Manager() {
        // Initialize the thread pool with a fixed size of 5
        threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    public static void main(String[] args) {
        Manager manager = new Manager();  // Create Manager instance with thread pool

        // Create and start the localAppThread (which will run ManagerLocalRun)
        Thread localAppThread = new Thread(new ManagerLocalRun(manager));
        localAppThread.start();

        try {
            localAppThread.join();  // Wait for the localAppThread to finish
        } catch (InterruptedException e) {
            System.err.println("Main thread interrupted: " + e.getMessage());
        }

        System.out.println("All threads completed, exiting program.");

        // Shut down the thread pool after use
        manager.shutdown();
    }

    // Shutdown the thread pool gracefully
    public void shutdown() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
        }
    }

    // Submit tasks to the thread pool
    public void submitTask(Runnable task) {
        threadPool.submit(task);
    }

    public int getAvailableWorkers() {
        return availableWorkers.get();
    }
}
