package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;

import software.amazon.awssdk.services.sqs.model.Message;

public class ManagerLocalRun implements Runnable {

    private static final String LOCALAPP_TO_MANAGER_QUEUE_NAME = NamingConvention.LOCAL_MANAGER_SQS;
    private static final String MANAGER_TO_WORKERS_QUEUE_NAME = NamingConvention.MANAGER_TO_WORKERS_SQS;
    private static final String WORKERS_TO_MANAGER_QUEUE_NAME = NamingConvention.WORKERS_TO_MANAGER_SQS;
    private static String SQS_READY = NamingConvention.MANAGER_LOCAL_SQS;
    private static String CLIENT_BUCKET = NamingConvention.UPLOAD_FILE_BUCKET;
    private static String EC2_BUCKET = NamingConvention.JAR_BUCKET;
    private static String WORKER_JAR;
    
    static AWSManeger aws = AWSManeger.getInstance();
    private Boolean terminate;
    private static Manager manager;

    public ManagerLocalRun(Manager manager1) {
        terminate = false;
        manager = manager1;
        WORKER_JAR = manager1.WORKER_JAR;
    }

    @Override
    public void run() {
        while (!terminate) {
            Message msg= aws.getMessage(LOCALAPP_TO_MANAGER_QUEUE_NAME);
            if (msg == null) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {continue;}
            }
            String message = msg.body();
            if (message.contentEquals("terminate")){
                terminate();
                break;
            }
            String[] parts = message.split(" ");
            String s3Location = parts[0];
            int linesPerWorker = Integer.parseInt(parts[1]);
            String LocalAppID = String.valueOf(manager.addLocal());

            Path path = aws.downloadFileFromS3(s3Location, CLIENT_BUCKET);
            if (path != null) {
                int numOfTasks = readFile(path, linesPerWorker, LocalAppID);
                if (numOfTasks != -1){
                    int active_workers = bootstrap(numOfTasks/linesPerWorker, LocalAppID);
                    System.out.println("[YARDEN] active workers count: " + active_workers);
                    if (active_workers > 0){
                        ManagerWorkerRun workerTask = new ManagerWorkerRun(active_workers, numOfTasks, LocalAppID, manager, s3Location);
                        addClient(LocalAppID);
                        manager.submitTask(workerTask);
                    }
                    else{
                        System.out.println("[YARDEN] failed to hand out workers");
                        aws.sendSQSMessage(s3Location + " " + linesPerWorker, LOCALAPP_TO_MANAGER_QUEUE_NAME);
                    }
                }
            }
            aws.deleteMessage(LOCALAPP_TO_MANAGER_QUEUE_NAME, msg.receiptHandle());
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
                String[] parts = line.split("\t");
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
        aws.createBucketIfNotExists(NamingConvention.BASE_CLIENT_BUCKET + LocalAppID);
        if (manager.getAvailableWorkers() > 0 && numOfWorkers > 0){
            numOfWorkers = Math.min(numOfWorkers, manager.getAvailableWorkers());
            manager.availableWorkers.addAndGet(-numOfWorkers);
            for (int i = 0 ; i < numOfWorkers; i++){
                initializeWorker("worker-"+LocalAppID, 1, LocalAppID);
            }
         }
         else {
            return 0;
         }
         return numOfWorkers;
    }


    private static void initializeWorker(String tag, int num, String LocalAppID){
        aws.checkIfFileExistsInS3(EC2_BUCKET, WORKER_JAR);
        String workerDataScript = aws.generateWorkerDataScript(EC2_BUCKET, WORKER_JAR, LocalAppID);
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
        try {
            Thread.sleep(15000);
        } catch (Exception e) {
        }
        while(manager.getThreadCount() > 1){
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
            }
        }
        aws.shutdown();
    }
}
