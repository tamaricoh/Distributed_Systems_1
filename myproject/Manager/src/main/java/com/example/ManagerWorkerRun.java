package com.example;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

public class ManagerWorkerRun implements Runnable {

    private static final String WORKERS_TO_MANAGER_QUEUE_NAME = "workers-to-manager";
    private static final String MANAGER_TO_LOCALAPP_QUEUE_NAME = "manager-to-localapp";
    // private static final String WORKERS_TO_MANAGER_BUCKET_NAME = "Workers-To-Manager";
    private static String CLIENT_BUCKET = "text-file-bucket";

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
                removeClient();
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
            System.out.println("All tasks processed. Total messages: " + numOfCompletedTasks);
    }
        
    private void terminateWorkers() {
        //empty the queue
        //send termination masseges to workers
        //wait until queue is empty
        //return
        




        terminate = true;
        
    }

    private void deleteReasources(Boolean delete_bucket) {
        //delete both queues
        //optinal delete bucket...
        




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

    return fileName; // Return the file name on success
}
}
