package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;

public class ManagerLocalRun implements Runnable {

    private static final String LOCALAPP_TO_MANAGER_QUEUE_NAME = "localapp-to-manager";
    private static final String MANAGER_TO_WORKERS_QUEUE_NAME = "manager-to-workers";
    private static final String WORKERS_TO_MANAGER_QUEUE_NAME = "worker-to-manager";
    private static String SQS_READY = "manager-to-localapp";
    // private static final String LOCALAPP_TO_MANAGER_BUCKET_NAME = "LocalApp-To-Manager";
    private static String CLIENT_BUCKET = "text-file-bucket";
    private static String EC2_BUCKET = "jar-bucket";
    private static String WORKER_JAR = "/home/ec2-user/worker.jar";
    
    static AWSManeger aws = AWSManeger.getInstance();
    private Boolean terminate;
    private static Manager manager;

    public ManagerLocalRun(Manager manager1) {
        terminate = false;
        manager = manager1;
    }

    @Override
    public void run() {
        aws.sendSQSMessage("Tamar", "test");
        while (!terminate) {
            String[] msg = aws.getMessage(LOCALAPP_TO_MANAGER_QUEUE_NAME);
            if (msg == null) {
                continue; // Skip if no valid message is received
            }
            if (msg.length == 1 && msg[0] == "terminate"){
                terminate();
                break;
            }

            String s3Location = msg[0];
            String linesPerWorkerStr = msg[1];
            int linesPerWorker = Integer.parseInt(linesPerWorkerStr);
            String LocalAppID = s3Location;

            Path path = aws.downloadFileFromS3(s3Location, CLIENT_BUCKET);
            if (path != null) {
                int numOfTasks = readFile(path, linesPerWorker, LocalAppID);
                if (numOfTasks != -1){
                    int active_workers = bootstrap(numOfTasks/linesPerWorker, LocalAppID);
                    if (active_workers > 0){
                        ManagerWorkerRun workerTask = new ManagerWorkerRun(active_workers, numOfTasks, LocalAppID, manager);
                        addClient(LocalAppID);
                        manager.submitTask(workerTask);
                    }
                    else{
                        aws.sendSQSMessage(s3Location + " " + linesPerWorker, LOCALAPP_TO_MANAGER_QUEUE_NAME);
                    }
                }
            }
        }
    }

    private String parseMessage(String operation, String url) {
        return operation + " " + url;
    }

    /**
     * Reads a text file line by line, creates an SQS message for each line, and sends it to the workers queue.
     * 
     * @param filePath       The path of the file to read.
     * @param linesPerWorker The number of lines to be processed by each worker.
     */
    private int readFile(Path filePath, int linesPerWorker, String LocalAppID) {
        aws.createSqsQueue(MANAGER_TO_WORKERS_QUEUE_NAME + LocalAppID);
        aws.createSqsQueue(WORKERS_TO_MANAGER_QUEUE_NAME + LocalAppID);
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
                String message = parseMessage(operation, url);

                // Send the message to the SQS queue
                aws.sendSQSMessage(message, MANAGER_TO_WORKERS_QUEUE_NAME + LocalAppID);
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
     * @param numOfWorkers Number of worker instances to boot.
     * @param LocalAppID     client identification for creating unique queues and buckets.
     */
    private int bootstrap(int numOfWorkers, String LocalAppID) {
        aws.createBucketIfNotExists("la-"+LocalAppID);
        
        if (manager.getAvailableWorkers() > 0 && numOfWorkers > 0){
            numOfWorkers = Math.min(numOfWorkers, manager.getAvailableWorkers());
            manager.availableWorkers.addAndGet(-numOfWorkers);
            initializeWorker("la-"+LocalAppID, "worker-"+LocalAppID, numOfWorkers, LocalAppID);
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

    //problemitic approach
    private void addClient(String ID){
        Boolean added = false;
        while(!added){
            try {
                manager.addUser(ID);
                added = true;  
            } catch (Exception e) {
                added = false;
            }
        }
    }

    private LinkedList<String> getClients(){
        Boolean fetched = false;
        LinkedList<String> clients = null;
        while(!fetched){
            try {
                clients = manager.getActiveUsersCopy();
                fetched = true;  
            } catch (Exception e) {
                fetched = false;
            }
        }
        return clients;
    }

    private void terminate(){
        terminate = true;
        LinkedList<String> clients = getClients();
        for (String client : clients) {
            aws.sendSQSMessage("terminate", WORKERS_TO_MANAGER_QUEUE_NAME + client);
            aws.sendSQSMessage("terminate", SQS_READY); 
        }
        aws.deleteBucket(CLIENT_BUCKET);
        aws.deleteQueue(LOCALAPP_TO_MANAGER_QUEUE_NAME);
        while(manager.availableWorkers.get() < manager.MAX_WORKERS || manager.getThreadCount() > 0){
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
            }
        }
        aws.shutdown();
    }
}
