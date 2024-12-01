package com.example;

public class LocalApplication{
    static String BUCKET_NAME = "Text_File_Bucket";
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
    //TODO::while listening to the sqsqueue should check if a termination message message received and handle accordingly.
    public static String listen(String filePath, AWS awsTool, String queueName){
        String summaryFileName = "FileNotFound";
        while (summaryFileName.contentEquals("FileNotFound")) {
            try {
                Thread.sleep(1000); // Wait for 1 second before retrying
            } catch (InterruptedException e) {
                //should also check if got a termination msg
                Thread.currentThread().interrupt(); // Restore interrupted status
                throw new RuntimeException("Thread was interrupted while waiting", e);
            }
            summaryFileName = awsTool.checkSQSQueue(queueName, filePath);
            //handle termination1!!
            /*
            if(args.length > 3 && args[3].equals("terminate")){
                terminateManager();
                summaryFileName = "-";
            }
            */
        }
        return summaryFileName;
    }
    
    //TODO:: implement this function: terminateManager
    public static void terminateManager() {   
    }
    //TODO:: implement this function: initalizeManager;
    //TODO:: implement this function: setupAWS

    // Main method
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("No args: LocalApplication <inputFileName> <outputFileName> <n> [--terminate]");
            return;
        }

        String inputFileName = args[0];
        String outputFileName = args[1];
        int n = Integer.parseInt(args[2]);
        boolean terminate = args.length > 3 && args[3].equals("terminate");

        setupAWS();
        String filePathS3 = aws.uploadFileToS3(inputFileName, BUCKET_NAME);
        if( filePathS3 == null){
            //retry or shut down and update user. 
        }
        //message format - <file_location_s3>:::<lines_per_worker>
        String msg = filePathS3 + dilimeter + n;
        aws.sendSQSMessage(msg, SQS_CLIENT);
        initalizeManager();
        filePathS3 = listen(filePathS3, aws, SQS_READY);

        if(aws.downloadFileFromS3(BUCKET_NAME, filePathS3, outputFileName + ".txt")){
            processFile(outputFileName + ".txt", outputFileName + ".html");
        }


        // If terminate flag is passed, simulate manager termination
        if (terminate) {
            terminateManager();
        }
    }
}
