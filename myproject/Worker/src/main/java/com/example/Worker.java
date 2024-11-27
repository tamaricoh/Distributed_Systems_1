package com.example;

import java.io.BufferedReader;          //need to update dependencies
import java.io.FileReader;
import java.io.IOException;

public class Worker implements Runnable {
    private final String inputFilePath; // Path to the input file
    private int n;                // Number of lines to process
    private final int startPosition;    // Starting line position
    private final long timeout;         // Timeout in milliseconds

    // Constructor
    public Worker(String inputFilePath, int numLines, int startPosition, long timeout) {
        this.inputFilePath = inputFilePath;
        this.n = numLines;
        this.startPosition = startPosition;
        this.timeout = timeout;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath))) {
            System.out.println("Worker started for file: " + inputFilePath);
            // Skip lines until the starting position
            reader.lines().skip(startPosition).findFirst();

            int linesProcessed = 0; //good for debugging
            long startTime = System.currentTimeMillis();
            while (linesProcessed < this.n) {
                System.out.println("proccesing line - " + linesProcessed);
                String line = reader.readLine();
                if (line == null) {
                    System.out.println("end of input file");
                    terminate();
                }


                // Parse line: <operation code> <url>
                String[] parts = line.split("\\s+");
                String operationCode = parts[0];
                String url = parts[1];

                // Process URL using PDFConverter
                String outputUrl = PDFConverter.convertFromUrl(url, operationCode);

                // Call the callback function
                processUrls(operationCode, url, outputUrl);

                linesProcessed++;
            }
            
            System.out.println("Worker completed processing " + linesProcessed + " lines.");
        } catch (IOException e) {
            System.err.println("Error reading input file: " + e.getMessage());
        }
    }

    private void processUrls(String operationCode, String url, String outputUrl) {
        String outputLine = operationCode + " " + url + " " + outputUrl;
        System.out.println(outputLine);
    }

    private void terminate() {
        this.n = 0;
    }
}