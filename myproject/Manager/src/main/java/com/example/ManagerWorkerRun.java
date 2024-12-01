package com.example;

public class ManagerWorkerRun implements Runnable{

    private static final String MANAGER_TO_WORKERS_QUEUE_NAME = "Manager-To-Workers";
    private static final String WORKERS_TO_MANAGER_QUEUE_NAME = "Workers-To-Manager";
    private static final String WORKERS_TO_MANAGER_BUCKET_NAME = "Workers-To-Manager";

    static AWSManeger aws = AWSManeger.getInstance();
    private Boolean terminate;

    public ManagerWorkerRun(){
        terminate = false;
    }

    public void run() {

    }

    private String createSQSMessage(String operation, String url) {
        return "Operation: " + operation + " URL: " + url;
    }
}
