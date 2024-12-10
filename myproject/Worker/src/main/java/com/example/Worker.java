package com.example;

import java.io.*;

import software.amazon.awssdk.services.sqs.model.Message;


public class Worker{

    private static String MANAGER_TO_WORKERS_QUEUE;
    private static String CLIENT_BUCKET;
    private static String WORKERS_TO_MANAGER_QUEUE;
    private static String DEFAULT_DOWNLOAD_DIR = "/home/user-ec2/downloads";
    static String localAppID;
    public Boolean terminate;
    static AWS aws = AWS.getInstance();

    // Constructor
    public Worker(String LocalAppId) {
        ensureDownloadDirectoryExists();
        localAppID = LocalAppId;
        MANAGER_TO_WORKERS_QUEUE = NamingConvention.MANAGER_TO_WORKERS_SQS + LocalAppId;
        WORKERS_TO_MANAGER_QUEUE = NamingConvention.WORKERS_TO_MANAGER_SQS + LocalAppId;
        CLIENT_BUCKET = NamingConvention.BASE_CLIENT_BUCKET + LocalAppId;
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
                    if (task.contentEquals("terminate")) {
                        terminate = true;  // Set terminate flag to true to break the loop
                        aws.sendMessage(WORKERS_TO_MANAGER_QUEUE, "terminting");
                        aws.shutdown();
                    }
                    String[] parts = task.split(" ");
                    String operation = parts[0]; // Extract operation
                    String url = parts[1];      // Extract URL

                    String processed_file_path = processFile(operation, url);
                    String output_URL;
                    if (processed_file_path.equals(null)){
                        continue;
                    }
                    else if(!processed_file_path.contains("Error: ")){
                        output_URL = aws.uploadFileToS3(processed_file_path, CLIENT_BUCKET);
                        File file = new File(processed_file_path);
                        if (file.exists()) file.delete();
                    }
                    else{
                        output_URL = processed_file_path;
                    }
                    aws.sendMessage(WORKERS_TO_MANAGER_QUEUE, operation + " " + url + " " + output_URL);
                    aws.sendMessage(NamingConvention.SQS_TEST,"worker sent: " + operation + " " + url + " " + output_URL + "to worker manager");
                    aws.deleteMessage(MANAGER_TO_WORKERS_QUEUE, msg.receiptHandle());
                } catch (Exception e) {
                    System.err.println("Error while processing the task: " + e.getMessage());  
                }
            }
        }
    }

    /**
     * Ensures that the download directory exists and returns its full path.
     *
     * @return Full path to the download directory.
     * @throws IllegalStateException If the directory cannot be created.
     */
    public static void ensureDownloadDirectoryExists() {
        File downloadDir = new File(DEFAULT_DOWNLOAD_DIR);

        if (!downloadDir.exists()) {
            if (!downloadDir.mkdirs()) {
                throw new IllegalStateException("Failed to create download directory: " + DEFAULT_DOWNLOAD_DIR);
            }
            System.out.println("Download directory created: " + DEFAULT_DOWNLOAD_DIR);
        } else {
            System.out.println("Download directory already exists: " + DEFAULT_DOWNLOAD_DIR);
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
        return PDFConverter.convertFromUrl(url, operation, localAppID, DEFAULT_DOWNLOAD_DIR);
    }

    public static void main(String[] args){
        Worker worker = new Worker(args[0]);
        aws.sendMessage(NamingConvention.SQS_TEST, "worker running on ec2...");
        worker.startWorker();
        aws.sendMessage(WORKERS_TO_MANAGER_QUEUE, "terminating");
        aws.shutdown();
    }
}