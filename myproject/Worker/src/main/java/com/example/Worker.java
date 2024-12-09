package com.example;

import java.io.*;
// import java.net.*;
import java.nio.file.Paths;
import java.net.URI;

import software.amazon.awssdk.services.sqs.model.Message;


public class Worker{

    private static String MANAGER_TO_WORKERS_QUEUE = "manager-to-workers-";
    private static String CLIENT_BUCKET = "localapp-";
    private static String WORKERS_TO_MANAGER_QUEUE = "workers-to-manager-";
    static String localAppID;
    public Boolean terminate;
    static AWS aws = AWS.getInstance();

    // Constructor
    public Worker(String LocalAppId) {
        localAppID = LocalAppId;
        MANAGER_TO_WORKERS_QUEUE = MANAGER_TO_WORKERS_QUEUE + LocalAppId;
        WORKERS_TO_MANAGER_QUEUE = WORKERS_TO_MANAGER_QUEUE + LocalAppId;
        CLIENT_BUCKET = CLIENT_BUCKET + LocalAppId;
        this.terminate = false;
    }

    // @Override
    public void startWorker() {
        while(!this.terminate){
            Message msg = aws.getMessage(MANAGER_TO_WORKERS_QUEUE);
            // String msg = "ToImage http://www.bethelnewton.org/images/Passover_Guide_BOOKLET.pdf";
            if (msg != null){
                try{
                    String task = msg.body();
                    // String task = msg;
                    if (task.contentEquals("terminate")) {
                        terminate = true;  // Set terminate flag to true to break the loop
                        aws.sendMessage(WORKERS_TO_MANAGER_QUEUE, "terminting");
                        aws.shutdown();
                    }
                    String[] parts = task.split(" ");
                    String operation = parts[0]; // Extract operation
                    String url = parts[1];      // Extract URL

                    String newURL = processFile(operation, url);
                    if (!newURL.contains("Error:")){
                        newURL = Paths.get(new URI(newURL)).toString();
                        newURL = aws.uploadFileToS3(newURL, CLIENT_BUCKET);
                        File file = new File(newURL);
                        if (file.exists()) file.delete();
                    }
                    aws.sendMessage(WORKERS_TO_MANAGER_QUEUE, operation + " " + url + " " + newURL);
                    aws.deleteMessage(MANAGER_TO_WORKERS_QUEUE, msg.receiptHandle());
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
        return PDFConverter.convertFromUrl(url, operation, localAppID);
    }

    public static void main(String[] args){
        Worker worker = new Worker(args[0]);
        worker.startWorker();
        aws.sendMessage(WORKERS_TO_MANAGER_QUEUE, "terminating");
        aws.shutdown();
    }
}