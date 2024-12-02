package com.example;


public class Manager {

    public Manager() {}

    public static void main(String[] args) {
        Thread localAppThread = new Thread(new ManagerLocalRun());
    
        localAppThread.start();
    
        try {
            localAppThread.join();  // Wait for the localAppThread to finish
        } catch (InterruptedException e) {
            System.err.println("Main thread interrupted: " + e.getMessage());
        }
    
        System.out.println("All threads completed, exiting program.");
    }
}