package com.example;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Manager implements Runnable {
    private final String inputFilePath;
    private final int linesPerWorker;
    private final long workerTimeout;
    private final ExecutorService threadPool;

    // Constructor
    public Manager(String inputFilePath, int linesPerWorker, long workerTimeout) {
        this.inputFilePath = inputFilePath;
        this.linesPerWorker = linesPerWorker;
        this.workerTimeout = workerTimeout;
        this.threadPool = Executors.newCachedThreadPool();
    }

    @Override
    public void run() {
        System.out.println("Manager started for file: " + inputFilePath);

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath))) {
            processInputFile(reader);
        } catch (IOException e) {
            System.err.println("Error reading input file: " + e.getMessage());
        } finally {
            terminateManager();
        }
    }

    /**
     * Processes the input file and submits tasks to the thread pool.
     */
    private void processInputFile(BufferedReader reader) throws IOException {
        int startPosition = 0;
        String[] batch;

        while ((batch = readNextBatch(reader)) != null) {
            submitWorkerTask(startPosition, linesPerWorker);
            startPosition += batch.length;
        }
    }

    /**
     * Reads the next batch of lines from the input file.
     * @param reader BufferedReader for the input file
     * @return Array of lines for the current batch, or null if EOF
     */
    private String[] readNextBatch(BufferedReader reader) throws IOException {
        String[] batch = new String[linesPerWorker];
        int batchIndex = 0;
        String line;

        while (batchIndex < linesPerWorker && (line = reader.readLine()) != null) {
            batch[batchIndex++] = line;
        }

        // Return null if no lines were read (EOF)
        return batchIndex == 0 ? null : trimBatch(batch, batchIndex);
    }

    /**
     * Trims the batch array to the exact number of lines read.
     * @param batch Array of lines
     * @param size Number of lines read
     * @return Trimmed array
     */
    private String[] trimBatch(String[] batch, int size) {
        if (size == batch.length) return batch;
        String[] trimmedBatch = new String[size];
        System.arraycopy(batch, 0, trimmedBatch, 0, size);
        return trimmedBatch;
    }

    /**
     * Submits a worker task to the thread pool.
     * @param batch Array of lines to process
     * @param startPosition Starting line position
     */
    private void submitWorkerTask(int startPosition, int linesToProcess) {
        Worker worker = new Worker(inputFilePath, linesToProcess, startPosition, workerTimeout);
        threadPool.submit(worker);
        System.out.println("Task submitted for lines: " + startPosition + " to " + (startPosition + linesToProcess - 1));
    }
    

    /**
     * Gracefully terminates the manager and its threads.
     */
    private void terminateManager() {
        System.out.println("Shutting down Manager...");
        threadPool.shutdown();
    }
}