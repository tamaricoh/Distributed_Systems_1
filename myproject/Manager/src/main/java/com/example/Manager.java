package com.example;

import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Iterator;


import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Manager {

    public final int MAX_WORKERS = 8;
    private static final int THREAD_POOL_SIZE = 5;
    public ExecutorService threadPool;
    public AtomicInteger availableWorkers = new AtomicInteger(MAX_WORKERS);
    private final Queue<String> userQueue = new LinkedList<>();
    private final Semaphore machineSem = new Semaphore(1);
    static AWSManeger aws = AWSManeger.getInstance();
    public int locals = 0;
    public String WORKER_JAR;

    public Manager(String jar) {
        // Initialize the thread pool with a fixed size of 5
        threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        WORKER_JAR = jar;
    }

    public int addLocal(){
        this.locals++;
        return locals-1;
    }

    
    // Shutdown the thread pool gracefully
    public void shutdown() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
        }
    }

    // Submit tasks to the thread pool
    public void submitTask(Runnable task) {
        System.out.println("[YARDEN] submitting a new task to thread pull");
        this.threadPool.submit(task);
    }

    public int getAvailableWorkers() {
        System.out.println("[YARDEN] getting avilable workers");
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

    /**
    * Method to get a copy of the active users queue.
    * This method returns a copy of the linked list containing the active users.
    * 
    * @return A copy of the linked list representing the active users in the queue.
    * @throws InterruptedException If the thread is interrupted while waiting for the lock.
    */
    public LinkedList<String> getActiveUsersCopy() throws InterruptedException {
        machineSem.acquire(); // Acquire the lock to access the queue
        try {
            // Return a new copy of the linked list to avoid exposing the internal structure
            return new LinkedList<>(userQueue);
        } finally {
            machineSem.release(); // Release the lock after the operation
        }
    }

    public int getThreadCount(){
        if (threadPool instanceof java.util.concurrent.ThreadPoolExecutor) {
            return ((java.util.concurrent.ThreadPoolExecutor) threadPool).getActiveCount();
        }
        return 5;
    }

    public static void main(String[] args) {
        Manager manager = new Manager(args[0]);  // Create Manager instance with thread pool
        
        // Create and start the localAppThread (which will run ManagerLocalRun)
        Thread localAppThread = new Thread(new ManagerLocalRun(manager));
        localAppThread.start();

       /*  """try {
            // Create an instance of ManagerLocalRun
            ManagerLocalRun managerTask = new ManagerLocalRun(manager);

            // Submit the ManagerLocalRun instance to the thread pool
            System.out.println("[YARDEN] submitting LocalManager");
            manager.threadPool.submit(managerTask);

            // Ensure all threads complete
            manager.threadPool.shutdown(); // Stop accepting new tasks
            if (!manager.threadPool.awaitTermination(10, TimeUnit.MINUTES)) { // Adjust timeout as needed
                System.err.println("Thread pool did not terminate in the specified time.");
                manager.threadPool.shutdownNow(); // Force shutdown if necessary
            }

            // Continue with further logic
            System.out.println("All threads in the thread pool have finished.");
            manager.shutdown();
        } catch (InterruptedException e) {
            System.err.println("Main thread interrupted while waiting for thread pool termination.");
            manager.threadPool.shutdownNow(); // Force shutdown in case of interruption
            manager.shutdown();
        }"""*/
    }
}