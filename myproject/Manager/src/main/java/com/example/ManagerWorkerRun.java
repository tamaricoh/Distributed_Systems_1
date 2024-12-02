package com.example;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ManagerWorkerRun implements Runnable {

    private static final String WORKERS_TO_MANAGER_QUEUE_NAME = "Workers_To_Manager";
    private static final String MANAGER_TO_LOCALAPP_QUEUE_NAME = "Manager_To_LocalApp";
    // private static final String WORKERS_TO_MANAGER_BUCKET_NAME = "Workers-To-Manager";
    private static String CLIENT_BUCKET = "Text_File_Bucket";

    static AWSManeger aws = AWSManeger.getInstance();
    private Boolean terminate;
    // private int linesPerWorker;
    private int numOfTasks;
    private int numOfCompletedTasks;
    private String localAppID;
    private String[] messagesArray; // Array to store messages

    public ManagerWorkerRun(int linesPerWorker, int numOfTasks, String localAppID) {
        terminate = false;
        // this.linesPerWorker = linesPerWorker;////????????????
        this.numOfTasks = numOfTasks;
        this.localAppID = localAppID;
        this.numOfCompletedTasks = 0;
        this.messagesArray = new String[numOfTasks]; // Initialize array with numOfTasks length
    }

    @Override
    public void run() {
        while (!terminate && numOfCompletedTasks < numOfTasks) { // Stop when all tasks are processed
            String[] msg = aws.getMessage(WORKERS_TO_MANAGER_QUEUE_NAME + "_" + localAppID);
            if (msg != null && msg.length == 3) { // Ensure message is valid
                String stringMsg = msg[0] + " " + msg[1] + " " + msg[2];            
                messagesArray[numOfCompletedTasks] = stringMsg; // Store message in array
                numOfCompletedTasks++;
                System.out.println("Message added to array: " + stringMsg);
            }
            if (numOfCompletedTasks == numOfTasks) {
                String fileName = createSummaryFile();
                if (fileName != null) {
                    String s3Key = aws.uploadFileToS3(fileName, CLIENT_BUCKET);
                    if (s3Key != null) {
                        System.out.println("File successfully uploaded to S3. S3 Key: " + s3Key);
                        aws.sendSQSMessage(localAppID + " " + s3Key, MANAGER_TO_LOCALAPP_QUEUE_NAME);
                    } else {
                        System.err.println("Error uploading file to S3.");
                    }
                }
            }
        }
        System.out.println("All tasks processed. Total messages: " + numOfCompletedTasks);
    }

    // private String createSQSMessage(String operation, String url, String newURL) {
    //     return  operation +  url  + newURL;
    // }

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
