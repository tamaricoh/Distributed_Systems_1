package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

// import com.ibm.j9ddr.vm29.structure.stat;

public class ManagerLocalRun implements Runnable {

    private static final String LOCALAPP_TO_MANAGER_QUEUE_NAME = "LocalApp-To-Manager";
    private static final String MANAGER_TO_WORKERS_QUEUE_NAME = "Manager-To-Workers";
    private static final String WORKERS_TO_MANAGER_QUEUE_NAME = "Worker-To-Manager";
    // private static final String LOCALAPP_TO_MANAGER_BUCKET_NAME = "LocalApp-To-Manager";
    private static String CLIENT_BUCKET = "Text_File_Bucket";
    private static String EC2_BUCKET = "Jar_Bucket";
    private static String WORKER_JAR = "";
    
    static AWSManeger aws = AWSManeger.getInstance();
    private Boolean terminate;
    private static Manager manager;

    public ManagerLocalRun(Manager manager1) {
        terminate = false;
        manager = manager1;
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
            String LocalAppID = UUID.randomUUID().toString();

            Path path = aws.downloadFileFromS3(s3Location, CLIENT_BUCKET);
            if (path != null) {
                int numOfTasks = readFile(path, linesPerWorker, LocalAppID);
                if (numOfTasks != -1){
                    bootstrap(numOfTasks/linesPerWorker, LocalAppID);
                    ManagerWorkerRun workerTask = new ManagerWorkerRun(linesPerWorker, numOfTasks, LocalAppID);
                    manager.submitTask(workerTask);
                }
            }
        }
    }

    private String createSQSMessage(String operation, String url) {
        return operation + " " + url;
    }

    /**
     * Reads a text file line by line, creates an SQS message for each line, and sends it to the workers queue.
     * 
     * @param filePath       The path of the file to read.
     * @param linesPerWorker The number of lines to be processed by each worker.
     */
    private int readFile(Path filePath, int linesPerWorker, String LocalAppID) {
        aws.createSqsQueue(MANAGER_TO_WORKERS_QUEUE_NAME+"_"+LocalAppID);
        aws.createSqsQueue(WORKERS_TO_MANAGER_QUEUE_NAME+"_"+LocalAppID);
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
                String message = createSQSMessage(operation, url);

                // Send the message to the SQS queue
                aws.sendSQSMessage(message, MANAGER_TO_WORKERS_QUEUE_NAME + "_" + LocalAppID);
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
    private int bootstrap(int numOfWorkers, String LocalAppID) {
        aws.createBucketIfNotExists("user_LA_"+LocalAppID);
        
        if (manager.getAvailableWorkers() > 0 && numOfWorkers > 0){
            numOfWorkers = Math.min(numOfWorkers, manager.getAvailableWorkers());
            manager.availableWorkers.addAndGet(-numOfWorkers);
            initializeWorker("user_LA_"+LocalAppID, "Worker_"+LocalAppID, numOfWorkers, LocalAppID);
         }
         else {
            return 0;
         }
         return numOfWorkers;
    }


    private static void initializeWorker(String BUCKET_NAME, String tag, int num, String LocalAppID){
        aws.checkIfFileExistsInS3(EC2_BUCKET, WORKER_JAR);
        String workerDataScript = aws.generateWorkerDataScript(BUCKET_NAME, WORKER_JAR, LocalAppID);
        aws.createEC2(workerDataScript, tag, num);
    }
}
