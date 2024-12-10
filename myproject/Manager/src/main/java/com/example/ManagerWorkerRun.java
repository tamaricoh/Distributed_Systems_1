package com.example;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    private String[] messagesArray; // Array to store messages

    public ManagerWorkerRun(int active_workers, int numOfTasks, String localAppID, Manager manager) {
        terminate = false;
        this.manager = manager;
        this.active_workers = active_workers;
        this.numOfTasks = numOfTasks;
        this.localAppID = localAppID;
        this.numOfCompletedTasks = 0;
        this.messagesArray = new String[numOfTasks]; // Initialize array with numOfTasks length
    }

    @Override
    public void run() {
        while (!terminate && numOfCompletedTasks < numOfTasks) { // Stop when all tasks are processed
            String[] msg = aws.getMessage(WORKERS_TO_MANAGER_QUEUE_NAME + localAppID);
            if (msg != null && msg.length == 3) { // Ensure message is valid
                String stringMsg = msg[0] + " " + msg[1] + " " + msg[2];            
                messagesArray[numOfCompletedTasks] = stringMsg; // Store message in array
                numOfCompletedTasks++;
                System.out.println("Message added to array: " + stringMsg);
            }
            else if(msg.length == 1 & msg[0].contentEquals("terminate")) {
                terminateWorkers();
                deleteReasources(true);
            }
            if (numOfCompletedTasks == numOfTasks) {
                String fileName = createSummaryFile();
                if (fileName != null) {
                    String s3Key = aws.uploadFileToS3(fileName, CLIENT_BUCKET);
                    if (s3Key != null) {
                        System.out.println("File successfully uploaded to S3. S3 Key: " + s3Key);
                        aws.sendSQSMessage(localAppID + " " + s3Key, MANAGER_TO_LOCALAPP_QUEUE_NAME);
                        terminateWorkers();
                        deleteReasources(false);
                        removeClient();
                    } else {
                        System.err.println("Error uploading file to S3.");
                    }
                }
            }
        }
        String fileName = createSummaryFile();
        if (fileName != null) {
            String s3Key = aws.uploadFileToS3(fileName, CLIENT_BUCKET);
            if (s3Key != null) {
                System.out.println("File successfully uploaded to S3. S3 Key: " + s3Key);
                aws.sendSQSMessage(localAppID + " " + s3Key, MANAGER_TO_LOCALAPP_QUEUE_NAME);
                terminateWorkers();
                deleteReasources(false);
                removeClient();
            } else {
                System.err.println("Error uploading file to S3.");
            }
        }
            System.out.println("All tasks processed. Total messages: " + numOfCompletedTasks);
    }
        
    private void terminateWorkers() {
        //send termination message to workers
        for(int i =0; i<active_workers ; i++){
            aws.sendSQSMessage("terminate", MANAGER_TO_WORKERS_QUEUE_NAME + localAppID);
        }
        int instances = active_workers;
        //wait for all workers to shut down
        while(active_workers>0){
            String[] msg = aws.getMessage(WORKERS_TO_MANAGER_QUEUE_NAME + localAppID);
            if(msg != null & msg[0].contentEquals("terminating")){
                active_workers--;
            }
            else{
                continue;
            }
        }
        //update avilable workers
        this.manager.availableWorkers.addAndGet(instances);
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
    String fileName = "summary_" + localAppID + ".txt";
    Path filePath = Paths.get(fileName);

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
        for (String message : messagesArray) {
            writer.write(message);
            writer.newLine(); // Write each message on a new line
        }
        System.out.println("Summary file created successfully: " + fileName);
    } catch (IOException e) {
        System.err.println("Error writing to summary file: " + e.getMessage());
        return null; // Return null if an error occurs
    }

    return filePath.toString(); // Return the file name on success
}
}
