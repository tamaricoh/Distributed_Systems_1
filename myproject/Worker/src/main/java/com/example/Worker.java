package com.example;

import java.io.BufferedReader;          //need to update dependencies
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Worker implements Runnable {
    private final String managerSQS = "";
    private String clientSQS;
    private String inputFilePath; // Path to the input file
    private int n;                      // Number of lines to process
    private int startPosition;    // Starting line position
    private String outputFilePath;
    private Boolean terminate;

    // Constructor
    public Worker(String sqsURL, String inputFilePath, int numLines, int startPosition,String outputFilePath ) {
        this.inputFilePath = inputFilePath;
        this.n = numLines;
        this.startPosition = startPosition;
        this.clientSQS = sqsURL;
        this.outputFilePath = outputFilePath;
        this.terminate = false;

    }

    @Override
    public void run() {
        while(!terminate){
            if(getMessage()){
                try{
                    downloadFile();
                    processFile();
                    uploadFile();
                    update();
                    removeTask();
                    terminate();
                }
                catch{
                    //update the manager according to exeception tell him the task is not completed
                    //continue to a new task
                }
            }
            else terminate();
        }
    }
    
    private Boolean getMessage(){
        return true;
    }

    private void downloadFile(){
        try {
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
    private void processFile(){
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath))) {
            reader.lines().skip(startPosition).findFirst(); //maybe need to change this skipping action
            int linesProcessed = 0; //good for debugging
            while (linesProcessed < this.n) {
                String line = reader.readLine();
                if (line == null) {
                    linesProcessed = n;
                    break;
                }
                // Parse line: <operation code> <url>
                String[] parts = line.split("\\s+");
                String operationCode = parts[0];
                String url = parts[1];
                // Process URL using PDFConverter
                String outputUrl = PDFConverter.convertFromUrl(url, operationCode);

                updateOutput(operationCode, url, outputUrl);
                linesProcessed++;
            }
            
            System.out.println("Worker completed processing " + linesProcessed + " lines.");
        } catch (IOException e) {
            System.err.println("Error reading input file: " + e.getMessage());
        }
    }

    private void updateOutput(String operationCode, String url, String outputUrl) throws IOException{
        String outputLine = operationCode + " " + url + " " + outputUrl;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(this.outputFilePath, true))) {
            writer.write(outputLine);
            writer.newLine();
        }
        catch (IOException e) {
            throw new IOException("Could not update file: " + outputFilePath, e);
        }
    }

    private void uploadFile(){
        //should upload the output file into the s3 bucket and update fields
        try {
        } catch (Exception e) {
            // TODO: handle exception
        }
        
    }
    
    private void update(){}

    private void removeTask(){

    }

    private void terminate() {}
}