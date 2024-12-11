package com.example;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import software.amazon.awssdk.services.sqs.model.Message;

public class ManagerWorkerRun implements Runnable {

    private static final String WORKERS_TO_MANAGER_QUEUE_NAME = NamingConvention.WORKERS_TO_MANAGER_SQS;
    private static final String MANAGER_TO_LOCALAPP_QUEUE_NAME = NamingConvention.MANAGER_LOCAL_SQS;
    private static final String MANAGER_TO_WORKERS_QUEUE_NAME = NamingConvention.MANAGER_TO_WORKERS_SQS;
    private static String CLIENT_BUCKET = NamingConvention.UPLOAD_FILE_BUCKET;

    static AWSManeger aws = AWSManeger.getInstance();
    private Manager manager;
    private Boolean terminate;
    private int active_workers;
    private int numOfTasks;
    private int numOfCompletedTasks;
    private String localAppID;
    private String s3location;
    private String[] messagesArray; // Array to store messages

    public ManagerWorkerRun(int active_workers, int numOfTasks, String localAppID, Manager manager, String input_file_location) {
        terminate = false;
        this.manager = manager;
        this.s3location = input_file_location;
        this.active_workers = active_workers;
        this.numOfTasks = numOfTasks;
        this.localAppID = localAppID;
        this.numOfCompletedTasks = 0;
        this.messagesArray = new String[numOfTasks]; // Initialize array with numOfTasks length
    }

    @Override
    public void run() {
        while (!terminate && numOfCompletedTasks < numOfTasks) { // Stop when all tasks are processed
            Message msg = aws.getMessage(WORKERS_TO_MANAGER_QUEUE_NAME + localAppID);
            if (msg == null){
                try {
                    Thread.sleep(5000);
                    System.out.println("no messages in Q");
                } catch (InterruptedException e) {
                    continue;
                }
            }
            else if(msg != null && msg.body().contentEquals("terminate")) {
                aws.deleteMessage(WORKERS_TO_MANAGER_QUEUE_NAME + localAppID, msg.receiptHandle());
                terminateWorkers();
                deleteReasources(true);
            }
            else { // message is valid
                messagesArray[numOfCompletedTasks] = msg.body(); // Store message in array
                numOfCompletedTasks++;
                aws.deleteMessage(WORKERS_TO_MANAGER_QUEUE_NAME + localAppID, msg.receiptHandle());
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                continue;
            }
            if (numOfCompletedTasks == numOfTasks) {
                String filePath = createSummaryFile();
                if (filePath != null) {
                    System.out.println("created summery file at: " + filePath);
                    String s3Key = aws.uploadFileToS3(filePath, CLIENT_BUCKET);
                    if (s3Key != null) {
                        System.out.println("File successfully uploaded to S3. S3 Key: " + s3Key);
                        aws.sendSQSMessage(s3location + " " + s3Key, MANAGER_TO_LOCALAPP_QUEUE_NAME);
                        terminateWorkers();
                        deleteReasources(false);
                        removeClient();
                    } else {
                        System.err.println("Error uploading file to S3.");
                    }
                }
            }
        }
        
    }
        
    private void terminateWorkers() {
        //send termination message to workers
        for(int i =0; i<active_workers ; i++){
            aws.sendSQSMessage("terminate", MANAGER_TO_WORKERS_QUEUE_NAME + localAppID);
        }
        try {
            Thread.sleep(7000);
        } catch (Exception e) {}
        this.manager.availableWorkers.addAndGet(active_workers);
        aws.sendSQSMessage("active workers are: " + this.manager.availableWorkers.get(), NamingConvention.SQS_TEST);
        terminate = true;
    }

    private void deleteReasources(Boolean delete_bucket) {
        aws.deleteQueue(MANAGER_TO_WORKERS_QUEUE_NAME + localAppID);
        aws.deleteQueue(WORKERS_TO_MANAGER_QUEUE_NAME + localAppID);
        if(delete_bucket){
            aws.deleteBucket(NamingConvention.BASE_CLIENT_BUCKET + localAppID);
        }
        terminate = true;   
    }

    private void removeClient(){
        Boolean removed = false;
        while(!removed){
            try {
                removed = manager.removeUser(localAppID) == localAppID;  
            } catch (Exception e) {
                removed = false;
            }
        }
    }
                
    private String createSummaryFile() {
        try {
            // Use provided directory or fallback to default
            Path directoryPath = Paths.get("/tmp/app_summaries-" + localAppID);
            Files.createDirectories(directoryPath);

            // Generate a unique filename
            String fileName = "summary-" + localAppID + ".txt";
            Path filePath = directoryPath.resolve(fileName);

            // Write messages to the file with additional error checking
            try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                for (String message : messagesArray) {
                        writer.write(message);
                        writer.newLine();
                }
                return filePath.toString();
            } catch (IOException writeException) {
                System.err.println("Error writing to summary file: " + writeException.getMessage());
                return null;
            }
        } catch (IOException dirException) {
            System.err.println("Error creating summary directory: " + dirException.getMessage());
            return null;
        }
    }
}
