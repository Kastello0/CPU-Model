public interface SimulationLogger {
    void log(String message);
    void showStatistics(String stats);
    void initializeProgress(int[] pids, int[] totalBursts);
    void updateProcessProgress(int pid, int remainingBurst, int totalBurst);
}