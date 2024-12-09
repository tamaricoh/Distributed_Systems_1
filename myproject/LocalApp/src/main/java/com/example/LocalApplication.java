package com.example;

import java.io.File;

public class LocalApplication{
    private static String MANAGER_JAR = "/home/yarden/Distributed_Systems_1/myproject/Manager/target/Manager-1.0-SNAPSHOT.jar";
    private static String WORKER_JAR = "/home/yarden/Distributed_Systems_1/myproject/Worker/target/Worker-1.0-SNAPSHOT.jar";
    private static String EC2_BUCKET = "jar-bucket-103";
    private static String FILES_BUCKET = "text-file-bucket-103";
    static String SQS_CLIENT = "localapp-to-manager";
    static String SQS_READY = "manager-to-localapp";
    static String SQS_TEST= "test";

    static AWS aws = AWS.getInstance();
    static String dilimeter = " ";

    // Method to process the file
    public static void processFile(String input_file_path, String output_file_path) {
        HTMLConverter htmlConverter = new HTMLConverter(input_file_path, output_file_path);
        htmlConverter.convertToHTML();
        File summery_file = new File(input_file_path);
        summery_file.delete();
        System.out.println("Successfully Converted the input file, you can find the outcome at : " + output_file_path);
    }

    public static String listenToSQS(String filePath, AWS awsTool, String queueName){
        String summaryFileName = "FileNotFound";
        while (summaryFileName.contentEquals("FileNotFound")) {
            summaryFileName = awsTool.checkSQSQueue(queueName, filePath);
            try {
                Thread.sleep(5000); // Wait for 5 second before retrying
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return summaryFileName;
    }

    public static void setupAWS(){
        aws.createBucketIfNotExists(FILES_BUCKET);
        aws.createSqsQueue(SQS_CLIENT);
        aws.createSqsQueue(SQS_READY);
        aws.createSqsQueue(SQS_TEST);
    }
    
    public static void initalizeManager(){
        if(aws.isManagerInstanceRunning()){
            return;
        }
        aws.createBucketIfNotExists(EC2_BUCKET);
        String jar_key_s3 = aws.uploadJar(MANAGER_JAR, EC2_BUCKET);
        String worker_jar_key_s3 = aws.uploadJar(WORKER_JAR, EC2_BUCKET);
        if (jar_key_s3 != "" && worker_jar_key_s3 != ""){
            String userDataScript = aws.generateManagerUserDataScript(EC2_BUCKET, jar_key_s3, worker_jar_key_s3);
            aws.createEC2(userDataScript, "Manager", 1);
        }
    }

    //TODO:: terminateManager: should we wait for an update message from the manager on successfull termination?
    public static void terminateManager(AWS aws) {
        aws.sendSQSMessage(SQS_CLIENT, "terminate");
        if(listenToSQS("terminate", aws, SQS_READY).contentEquals("terminate")){
            System.out.println("Manager shut down successfully");
            System.exit(0);
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Not all args delivered: LocalApplication <input file path> <output file path> <n> [terminate]");
            return;
        }

        String input_file_path = args[0];
        String output_file_path = args[1];
        int n = Integer.parseInt(args[2]);
        boolean terminate = args.length > 3 && args[3].equals("terminate");
        if(!terminate){
            setupAWS();
            String file_key_S3 = aws.uploadFileToS3(input_file_path, FILES_BUCKET);
            int tries = 0;
            while(file_key_S3 == null &&  tries < 5){
                //retry or shut down and update user.
                file_key_S3 = aws.uploadFileToS3(input_file_path, FILES_BUCKET);
                tries++;
            }
            if(tries == 5){
                System.out.println("Couldn't load your requsted file to the server, please check internet conectivity");
                System.exit(0);
            }
            //message format - <file_location_s3>:::<lines_per_worker>
            aws.sendSQSMessage("localapp-to-manager", "Tamar");
            String msg = file_key_S3 + dilimeter + n;
            aws.sendSQSMessage(SQS_CLIENT, msg);
            initalizeManager();
            file_key_S3 = listenToSQS(file_key_S3, aws, SQS_READY);
            if(file_key_S3.contentEquals("terminate")){
                System.out.println("Server is shutting down, please try again later...");
                System.exit(0);
            }
            if(aws.downloadFileFromS3(FILES_BUCKET, file_key_S3, output_file_path + ".txt")){
                processFile(output_file_path + ".txt", output_file_path + ".html");
            }
            else{
                System.out.println("couldn't Download the file from the server....");
            }
        }
        else {
            terminateManager(aws);
        }
    }
}