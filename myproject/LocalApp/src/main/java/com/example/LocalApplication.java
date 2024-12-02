package com.example;

public class LocalApplication{
    private static String MANAGER_JAR = "";
    private static String WORKER_JAR = "";
    private static String EC2_BUCKET = "Jar_Bucket";
    private static String CLIENT_BUCKET = "Text_File_Bucket";
    static String SQS_CLIENT = "LocalApp_To_Manager";
    static String SQS_READY = "Manager_To_LocalApp";

    static AWS aws = AWS.getInstance();
    static String dilimeter = ":::";

    // Method to process the file
    public static void processFile(String inputFilePath, String outputFilePath) {
        HTMLConverter htmlConverter = new HTMLConverter(inputFilePath, outputFilePath);
        htmlConverter.convertToHTML();
        System.out.println("Successfully Converted the input file, you can find the outcome at : " + outputFilePath);
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
        aws.createBucketIfNotExists(CLIENT_BUCKET);
        aws.createSqsQueue(SQS_CLIENT);
    }
    //TODO::initalizeManager; formalte the script to run on the ec2 machine
    public static void initalizeManager(){
        if(aws.isManagerInstanceRunning()){
            return;
        }
        aws.createBucketIfNotExists(EC2_BUCKET);
        String manager_path_s3 = aws.uploadJar(MANAGER_JAR, EC2_BUCKET);
        aws.uploadJar(WORKER_JAR, EC2_BUCKET);
        String userDataScript = aws.generateManagerUserDataScript(EC2_BUCKET, manager_path_s3, SQS_CLIENT);
        aws.createEC2(userDataScript, "Manager", 1);

    }

    //TODO:: terminateManager: should we wait for an update message from the manager on successfull termination?
    public static void terminateManager() {
        aws.sendSQSMessage("terminate", SQS_CLIENT);
        //maybe we want to confirm with the manager that he teminated succesfully?
        System.out.println("Manager shut down successfully");   
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("No args: LocalApplication <inputFileName> <outputFileName> <n> [--terminate]");
            return;
        }

        String inputFileName = args[0];
        String outputFileName = args[1];
        int n = Integer.parseInt(args[2]);
        boolean terminate = args.length > 3 && args[3].equals("terminate");
        if(!terminate){
            setupAWS();
            String filePathS3 = aws.uploadFileToS3(inputFileName, CLIENT_BUCKET);
            int tries = 0;
            while(filePathS3 == null &&  tries < 5){
                //retry or shut down and update user.
                filePathS3 = aws.uploadFileToS3(inputFileName, CLIENT_BUCKET);
                tries++;
            }
            if(tries == 5){
                System.out.println("Couldn't load your requsted file to the server, please check internet conectivity");
                System.exit(0);
            }
            //message format - <file_location_s3>:::<lines_per_worker>
            String msg = filePathS3 + dilimeter + n;
            aws.sendSQSMessage(msg, SQS_CLIENT);
            initalizeManager();
            filePathS3 = listenToSQS(filePathS3, aws, SQS_READY);

            if(aws.downloadFileFromS3(CLIENT_BUCKET, filePathS3, outputFileName + ".txt")){
                processFile(outputFileName + ".txt", outputFileName + ".html");
            }
        }
        else {
            terminateManager();
        }
    }
}