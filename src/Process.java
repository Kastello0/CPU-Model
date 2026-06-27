public class Process extends Thread implements ClockObserver {
    private int burstTime, priority, priorityEscalation;
    private final int originalBurstTime;
    private long currentElapsed, finishTime, accumulatedTime, startTime, idleTime;
    private final int arrivalTime, processID;
    private ProcessObserver observer;
    private Clock clock;
    private boolean started, paused = false;
    private SimulationLogger logger;

    //implement lifetime by getting currenttime - arrivaltime, update priority accordingly
    public Process(int pid, int arrivalTime, int burstTime, int priority, SimulationLogger logger){
        this.processID = pid;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.originalBurstTime = burstTime;
        this.priority = priority;
        this.priorityEscalation = 0;
        this.logger = logger;
        clock = Clock.getInstance();
    }

    public synchronized void setAccumulatedTime(long accumulatedTime) {
        this.accumulatedTime = accumulatedTime;
    }

    public void process(){
        started = true;
        startTime = clock.getElapsedTime();
        accumulatedTime = 0;
        long stopTime;
        while (started) {
            synchronized (this) {
                while (paused) {
                    try {
                        stopTime = clock.getElapsedTime();
                        wait();
                        idleTime += clock.getElapsedTime() - stopTime;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            synchronized (this) {
                currentElapsed = getElapsed();
                if (currentElapsed > 1000 + accumulatedTime) {
                    setAccumulatedTime(accumulatedTime + 1000);
                    this.burstTime--;

                    if (logger != null) {
                        logger.log("Processing... Remaining burst time: " + burstTime);
                        logger.updateProcessProgress(processID, burstTime, originalBurstTime);
                    }
                }
                if (burstTime <= 0) {
                    finishTime = clock.getElapsedTime();
                    finishProcess();
                    started = false;
                    if (logger != null) logger.log("Process " + processID + " finished");
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (logger != null) logger.log("Process " + getProcessID() + " interrupted.");
                }
            }
        }
    }

    public void run(){
        process();
    }

    private synchronized void finishProcess() {
        observer.finishProcess();
    }

    public void setObserver(ProcessObserver observer) {
        this.observer = observer;
    }

    public synchronized long getElapsed(){
        return clock.getElapsedTime() - idleTime - startTime;
    }

    public int getBurstTime() {
        return this.burstTime;
    }

    //getPriority() is taken by thread
    public int returnPriority() {
        return priority;
    }

    public void increasePriority() {
        if(priority > 1) {
            this.priority--;
        }
    }

    public boolean isStarted() {
        return started;
    }

    public synchronized void pauseProcess(){
        paused = true;
    }

    public synchronized void resumeProcess(){
        paused = false;
        notify();
    }

    public long getResponseTime(){return startTime - arrivalTime * 1000L;}

    public double getWaitTime(){return idleTime;}

    public double getTurnaroundTime() {
        return finishTime - startTime;
    }

    public int getProcessID() {
        return processID;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    @Override
    public void onUpdate(int time) {
        priorityEscalation++;
        if(priorityEscalation > 15){
            increasePriority();
            priorityEscalation = 0;
        }
    }

    @Override
    public void notifyQuantum(int quantum) {

    }

    @Override
    public String toString() {
        return "Process{" +
                "processID=" + processID +
                ", arrivalTime=" + arrivalTime +
                ", burstTime=" + burstTime +
                ", priority=" + priority +
                '}';
    }
}