package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class ManagerLocalRun implements Runnable {

    private static final String LOCALAPP_TO_MANAGER_QUEUE_NAME = "LocalApp-To-Manager";
    private static final String MANAGER_TO_WORKERS_QUEUE_NAME = "Manager-To-Workers";
    private static final String LOCALAPP_TO_MANAGER_BUCKET_NAME = "LocalApp-To-Manager";
    
    static AWSManeger aws = AWSManeger.getInstance();
    private Boolean terminate;
    private Manager manager;

    public ManagerLocalRun(Manager manager) {
        terminate = false;
        this.manager = manager;
    }

    @Override
    public void run() {
        while (!terminate) {
            String[] msg = aws.getMessage(LOCALAPP_TO_MANAGER_QUEUE_NAME);
            if (msg == null || msg.length < 2) {
                continue; // Skip if no valid message is received
            }

            String s3Location = msg[0];
            String linesPerWorkerStr = msg[1];
            int linesPerWorker = Integer.parseInt(linesPerWorkerStr);

            Path path = aws.downloadFileFromS3(s3Location, LOCALAPP_TO_MANAGER_BUCKET_NAME);
            if (path != null) {
                int numOfTasks = readFile(path, linesPerWorker);
                if (numOfTasks != -1){
                    bootstrap(linesPerWorker, numOfTasks);
                }
            }
        }
    }

    private String createSQSMessage(String operation, String url, int linesPerWorker) {
        return operation + " " + url + " " + linesPerWorker;
    }

    /**
     * Reads a text file line by line, creates an SQS message for each line, and sends it to the workers queue.
     * 
     * @param filePath       The path of the file to read.
     * @param linesPerWorker The number of lines to be processed by each worker.
     */
    private int readFile(Path filePath, int linesPerWorker) {
        int numOfTasks = 0;
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length != 2) {
                    System.err.println("Invalid line format: " + line);
                    continue; // Skip malformed lines
                }

                String operation = parts[0];
                String url = parts[1];
                String message = createSQSMessage(operation, url, linesPerWorker);

                // Send the message to the SQS queue
                aws.sendSQSMessage(message, MANAGER_TO_WORKERS_QUEUE_NAME);
                numOfTasks++;
            }
            return numOfTasks;
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Placeholder for the bootstrap function.
     * 
     * @param linesPerWorker Number of lines each worker should process.
     * @param numOfTasks     Total number of tasks generated.
     */
    private void bootstrap(int linesPerWorker, int numOfTasks) {
        
        // Implementation to be added

        // here we will create a thread that runs ManagerWorkerRun
        ManagerWorkerRun workerTask = new ManagerWorkerRun(linesPerWorker, numOfTasks, UUID.randomUUID().toString());
        manager.submitTask(workerTask);
    }
}
