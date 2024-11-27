package com.example;

public class WorkerMain {
    public static void main(String[] args) {
        // Path to the input file
        String inputFilePath = "/home/yarden/Distributed_Systems_1/myproject/Worker/src/main/java/com/example/input-sample-1.txt";

        // Worker configuration
        int numLines = 5;             // Number of lines to process (adjust as needed)
        int startPosition = 0;        // Starting position in the file
        long timeout = 120000;          // Timeout in milliseconds (5 seconds)

        // Create and run a Worker instance
        Worker worker = new Worker(inputFilePath, numLines, startPosition, timeout);

        // Start the Worker in a new thread
        Thread workerThread = new Thread(worker);
        workerThread.start();

        try {
            // Wait for the worker to complete
            workerThread.join();
        } catch (InterruptedException e) {
            System.err.println("Worker thread interrupted: " + e.getMessage());
            Thread.currentThread().interrupt(); // Preserve interrupt status
        }
    }
}
