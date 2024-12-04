package com.example;

import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Iterator;


import java.util.concurrent.Semaphore;

public class Manager {

    private static final int THREAD_POOL_SIZE = 5;
    private ExecutorService threadPool;
    public AtomicInteger availableWorkers = new AtomicInteger(14);
    private final Queue<String> userQueue = new LinkedList<>();
    private final Semaphore machineSem = new Semaphore(1);

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

    // Method to add a user to the queue
    public void addUser(String user) throws InterruptedException {
        machineSem.acquire(); // Acquire the lock to add the user
        try {
            userQueue.add(user);  // Add user to the queue
            System.out.println("Added user: " + user);
        } finally {
            machineSem.release(); // Release the lock after the operation
        }
    }

    public String removeUser(String userId) throws InterruptedException {
        machineSem.acquire(); // Acquire the lock to remove a user
        try {
            // Iterate through the queue to find the user with the matching ID
            Iterator<String> iterator = userQueue.iterator();
            while (iterator.hasNext()) {
                String user = iterator.next();
                if (user.equals(userId)) {
                    iterator.remove();  // Remove the user from the queue
                    return user;  // Return the removed user ID
                }
            }
            return null;  // Return null if the user ID is not found in the queue
        } finally {
            machineSem.release(); // Release the lock after the operation
        }
    }

    // Method to get the number of active users in the queue
    public int getActiveUsersCount() throws InterruptedException {
        machineSem.acquire(); // Acquire the lock to check the size
        try {
            return userQueue.size();  // Return the size of the queue (active users)
        } finally {
            machineSem.release(); // Release the lock after the operation
        }
    }
}
