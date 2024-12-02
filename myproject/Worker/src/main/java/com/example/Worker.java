package com.example;

import java.io.*;
// import java.net.*;


public class Worker{

    private static final String MANAGER_TO_WORKERS_QUEUE_NAME = "Manager-To-Workers";
    private static final String WORKERS_TO_MANAGER_BUCKET_NAME = "Workers-To-Manager";

    // String linesPerWorker = System.getenv("LINES_PER_WORKER");
    String localAppID = System.getenv("LOCAL_APP_ID");


    static AWS aws = AWS.getInstance();
    private Boolean terminate;

    // Constructor
    public Worker() {
        this.terminate = false;
    }

    // @Override
    public void startWorker() {
        while(!terminate){
            String msg = aws.getMessage(MANAGER_TO_WORKERS_QUEUE_NAME, localAppID);
            if (msg != ""){
                try{
                    if (msg.equals("terminate")) {
                        terminate = true;  // Set terminate flag to true to break the loop
                        System.out.println("Received termination message. Shutting down worker.");
                        break;  // Exit the loop and terminate the worker thread
                    }
                    String[] parts = msg.split(" ");
                    String operation = parts[0]; // Extract operation
                    String url = parts[1];      // Extract URL
                    // downloadFile(url);
                    String newURL = processFile(operation, url);

                    // If newURL is empty, handle the error - write it!!!!!!!!!!1
    
                    aws.uploadFileToS3(url, newURL, operation, WORKERS_TO_MANAGER_BUCKET_NAME, localAppID);
                    // check if terminate??
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
    }
}