package com.example;

public class NamingConvention {
    //Files
    public static String MANAGER_JAR_PATH = "/home/yarden/Distributed_Systems_1/myproject/Manager/target/Manager-1.0-SNAPSHOT.jar";
    public static String WORKER_JAR_PATH = "/home/yarden/Distributed_Systems_1/myproject/Worker/target/Worker-1.0-SNAPSHOT.jar";
    
    //Buckets
    public static String JAR_BUCKET = "jar-bucket-103";
    public static String UPLOAD_FILE_BUCKET = "text-file-bucket-103";
    public static String BASE_CLIENT_BUCKET = "localapp-103-";

    //SQS
    public static String MANAGER_TO_WORKERS_SQS = "manager-to-workers-103-";
    public static String WORKERS_TO_MANAGER_SQS = "workers-to-manager-103-";
    public static String LOCAL_MANAGER_SQS = "localapp-to-manager";
    public static String MANAGER_LOCAL_SQS = "manager-to-localapp";
    public static String SQS_TEST= "test";
    public static String dilimeter = " ";


    public static String parse_message(String[] message_parts){
        String output = "";
        for(int i = 0; i <message_parts.length; i++){
            output = output.concat(message_parts[i] + dilimeter);
        }
        return output;
    }

    public static String[] get_message(String message){
        return message.split(dilimeter);
    }
}