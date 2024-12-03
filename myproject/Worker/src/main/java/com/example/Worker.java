package com.example;

import java.io.*;
// import java.net.*;

import software.amazon.awssdk.services.sqs.model.Message;


public class Worker{

    private static final String MANAGER_TO_WORKERS_QUEUE = "manager_to_workers";
    private static final String CLIENT_BUCKET = "workers_to_manager";
    private static final String WORKERS_TO_MANAGER_QUEUE = "workers_to_manager";

    // String linesPerWorker = System.getenv("LINES_PER_WORKER");
    String localAppID = System.getenv("LOCAL_APP_ID");


    static AWS aws = AWS.getInstance();
    public Boolean terminate;

    // Constructor
    public Worker() {
        this.terminate = false;
    }

    private String getName(String bucketName){
        return bucketName + localAppID;
    }

    // @Override
    public void startWorker() {
        while(!this.terminate){
            Message msg = aws.getMessage(getName(MANAGER_TO_WORKERS_QUEUE));
            if (msg != null){
                try{
                    String task = msg.body();
                    if (task.contentEquals("terminate")) {
                        terminate = true;  // Set terminate flag to true to break the loop
                        System.out.println("Received termination message. Shutting down worker.");
                        break;  // Exit the loop and terminate the worker thread
                    }
                    String[] parts = task.split(" ");
                    String operation = parts[0]; // Extract operation
                    String url = parts[1];      // Extract URL

                    String newURL = processFile(operation, url);
                    if (!newURL.contains("Error:")){
                        newURL = aws.uploadFileToS3(newURL, getName(CLIENT_BUCKET));
                    }
                    aws.sendMessage(getName(WORKERS_TO_MANAGER_QUEUE), operation + " " + url + " " + newURL);
                    aws.deleteMessage(getName(MANAGER_TO_WORKERS_QUEUE), msg.receiptHandle());
                } catch (Exception e) {
                    System.err.println("Error while processing the task: " + e.getMessage());  
                }
            }
        }
    }

    /**
     * Processes a downloaded PDF file based on the specified operation.
     *
     * @param operation Type of processing to perform on the file.
     * @return The result of the file processing operation.
     * @throws IOException If an error occurs during file processing.
     */
    private String processFile(String operation, String url) throws IOException {
        return PDFConverter.convertFromUrl(url, operation);
    }

    public static void main(String[] args){
        Worker worker = new Worker();
        worker.startWorker();
        aws.shutdown();
        //shut down the EC2 instance()
    }
}