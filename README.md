# Distributed System Programming - Assignment 1

This Maven-based project consists of three independent modules:

1. **LocalApp**  
   The entry point for users. This application submits requests to the system via AWS SQS queues. It also handles the creation of an EC2 instance to run the Manager if it is not already running.

   - **Output Processing**:  
     After receiving the results, the LocalApp fetches a text file from S3 (generated by the Manager) and converts it into an HTML file.

2. **Manager**  
   The core orchestrator of the system. The Manager listens to SQS messages from the LocalApp, processes requests, and manages communication with workers.

   - **Thread Management**:  
     The Manager utilizes a thread pool to handle multiple LocalApp requests concurrently. One thread continuously polls the SQS queue for new messages, while others manage the coordination between the Manager and Workers for each specific request.
   - **Communication**:
     - Between **LocalApp and Manager**: A shared SQS queue is used for message passing. (Currently, two queues are used; the future plan is to consolidate them into one queue.)
     - Between **Manager and Workers**: Each LocalApp request spawns its own communication channels via dedicated SQS queues (currently two queues per request, with plans to reduce this to one).

3. **Worker**  
   The processing units that handle tasks delegated by the Manager. Workers receive a task and a URL pointing to a file on S3. They perform the specified task (e.g., converting a PDF to a text file, HTML, or PNG), upload the resulting file to S3, and send the new URL back to the Manager.

---

## Running the System

### Pre-requisites

1. **AWS Services Setup**:

   - Ensure that the required AWS services (S3, SQS, EC2) are properly configured.
   - Set up IAM roles and policies to allow the applications to interact with these services.

2. **Build the Maven Projects**:
   - Navigate to each module directory (`LocalApp`, `Manager`, `Worker`) and build the JAR files:
     ```bash
     mvn clean install
     ```
   - This will generate executable JAR files in the `target` directory of each module.

### Step-by-Step Execution

1. **Run the LocalApp**:

   - Start the `LocalApp` module:
     ```bash
     java -jar target/LocalApp-1.0-SNAPSHOT.jar
     ```
   - The LocalApp will check if a Manager EC2 instance is running. If not, it will create one using the configured AWS credentials and S3 bucket for storing the Manager's JAR file.

2. **Manager on EC2**:

   - The Manager will be automatically deployed and started on the EC2 instance created by the LocalApp.
   - It listens to SQS messages from the LocalApp and begins processing requests.

3. **Workers**:
   - The Manager spawns or communicates with workers as needed. Workers handle task-specific computation based on the instructions sent by the Manager.

### Example Workflow

- A user submits a request via the LocalApp.
- The LocalApp sends the request to the Manager through the SQS queue.
- The Manager receives the request, divides the workload, and communicates with Workers via separate SQS queues to process the request.
- The Workers complete their tasks and send the results back to the Manager, which aggregates the responses and sends the final result back to the LocalApp.

---

This architecture is designed for scalability, with modular components and efficient message-passing mechanisms to handle distributed workloads.