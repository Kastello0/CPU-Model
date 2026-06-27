import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CPU extends Thread implements ProcessObserver{
    private Scheduler scheduler;
    private boolean lock;
    private Process runningProcess;
    private Clock clock;
    private int numCompletedProcesses, numContextSwitches;
    private Statistics statistics;
    private SimulationLogger logger;

    public CPU(String processFile, int timeQuantum, SimulationLogger logger){
        this.logger = logger;
        this.clock = Clock.getInstance(timeQuantum);
        List<Process> processes = readProcessesFromCSV(processFile, logger);

        if (processes != null && logger != null) {
            int[] pids = new int[processes.size()];
            int[] bursts = new int[processes.size()];
            for (int i = 0; i < processes.size(); i++) {
                pids[i] = processes.get(i).getProcessID();
                bursts[i] = processes.get(i).getBurstTime();
            }
            logger.initializeProgress(pids, bursts);
        }

        this.scheduler = new Scheduler(processes, getHighestPriority(processes));
        clock.addObserver(scheduler);
        statistics = new Statistics(clock.getQuantum());
    }

    public void run(){
        clock.startClock();
    }

    public boolean isLocked() {
        return lock;
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

    private int getHighestPriority(List<Process> processes) {
        if (processes == null || processes.isEmpty()) return 1;
        return processes.stream()
                .map(Process::returnPriority)
                .max(Integer::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("Empty process list"));
    }

    public static List<Process> readProcessesFromCSV(String filePath, SimulationLogger logger) {
        List<Process> processes = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                int pid = Integer.parseInt(values[0].trim());
                int arrivalTime = Integer.parseInt(values[1].trim());
                int burstTime = Integer.parseInt(values[2].trim());
                int priority = Integer.parseInt(values[3].trim());
                processes.add(new Process(pid, arrivalTime, burstTime, priority, logger));
            }
        } catch (IOException e) {
            if (logger != null) logger.log("Error reading file: " + e.getMessage());
            else System.err.println("Error reading file: " + e.getMessage());
            return null;
        }
        return processes;
    }

    //Logic for thread handling
    public void initiateProcess(){
        runningProcess.setObserver(this);
        if (runningProcess.isStarted()){
            runningProcess.resumeProcess();
        } else {
            runningProcess.start();
        }
    }

    @Override
    public void finishProcess() {
        synchronized (this){
            clock.resetQuantum();
            setLock(false);
            statistics.saveProcessInfo(runningProcess, clock.getElapsedTime(), numContextSwitches);
            numCompletedProcesses++;
            if (numCompletedProcesses >= scheduler.processes.size()) {
                clock.stopClock();
                if (logger != null) {
                    logger.showStatistics(statistics.toString());
                    logger.log("Simulation Finished.");
                } else {
                    System.out.println(statistics.toString());
                }
            }
        }
        runningProcess = scheduler.dequeue();
        if (runningProcess != null) {
            numContextSwitches++;
            initiateProcess();
            setLock(true);
        }
    }

    class Scheduler implements ClockObserver {
        private final Queue queue;
        private final List<Process> processes;

        public Scheduler(List<Process> processList, int numPriorityLevels){
            this.queue = new Queue(numPriorityLevels);
            this.processes = processList;
        }

        @Override
        public void onUpdate(int time) {
            if((numCompletedProcesses < scheduler.processes.size())) {
                if (logger != null) logger.log("\ntime: " + time);
            }
            for(Process process : processes){
                if(process.getArrivalTime() == time){
                    if (logger != null) logger.log("Process arriving: " + process.getProcessID());
                    queue.enqueue(process);
                }
            }
            if(runningProcess != null){
                if (logger != null) logger.log("onUpdate() : running process " + runningProcess.getProcessID()
                        + ", remaining time: " + runningProcess.getBurstTime());
            }
        }

        @Override
        public void notifyQuantum(int quantum) {
            // Check if the CPU is currently locked on a running process
            if (isLocked() && runningProcess != null) {
                if (logger != null) logger.log("Stopping current process: " + runningProcess.getProcessID());
                runningProcess.pauseProcess();
                queue.enqueue(runningProcess); // Re-enqueue the current process
                runningProcess = null;
                setLock(false); // Release the lock
            }

            // Dequeue the next process if available
            runningProcess = dequeue();

            if (runningProcess != null) {
                if (logger != null) logger.log("notifyQuantum() : process switched to " + runningProcess.getProcessID()
                        + ", remaining time: " + runningProcess.getBurstTime());
                numContextSwitches++;
                initiateProcess(); // Process the dequeued process
                setLock(true); // Lock the CPU
            } else {
                if (logger != null) logger.log("No process to switch to. Queue is empty.");
                setLock(false);
                clock.stopClock();
            }
        }

        public Process dequeue(){
            return queue.dequeue();
        }
    }
}