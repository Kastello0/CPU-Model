import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CPUGUI extends JFrame implements SimulationLogger {
    private final JTextArea logArea;
    private final JTextArea statsArea;
    private final JTextField fileField;
    private final JTextField quantumField;
    private final JButton startButton;
    private final JPanel progressContainer;
    private final Map<Integer, JProgressBar> progressBars;

    public CPUGUI() {
        setTitle("CPU Scheduler Simulation");
        setSize(1000, 600); // Increased width to accommodate the new panel
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        progressBars = new HashMap<>();

        // Top Panel: Configuration and Controls
        JPanel topPanel = new JPanel(new FlowLayout());

        topPanel.add(new JLabel("CSV File:"));
        fileField = new JTextField(20);
        topPanel.add(fileField);

        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser(new File("."));
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                fileField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });
        topPanel.add(browseButton);

        topPanel.add(new JLabel("Time Quantum:"));
        quantumField = new JTextField("6", 5);
        topPanel.add(quantumField);

        startButton = new JButton("Start Simulation");
        startButton.addActionListener(e -> startSimulation());
        topPanel.add(startButton);

        add(topPanel, BorderLayout.NORTH);

        // Center Panel: Live Logging
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Simulation Logs"));

        // East Panel: Process Progress
        progressContainer = new JPanel();
        progressContainer.setLayout(new BoxLayout(progressContainer, BoxLayout.Y_AXIS));
        JScrollPane progressScroll = new JScrollPane(progressContainer);
        progressScroll.setBorder(BorderFactory.createTitledBorder("Process Progress"));
        progressScroll.setPreferredSize(new Dimension(250, 0));

        // Group Center and East into a SplitPane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, logScroll, progressScroll);
        splitPane.setResizeWeight(0.8);
        add(splitPane, BorderLayout.CENTER);

        // Bottom Panel: Final Statistics
        statsArea = new JTextArea(6, 50);
        statsArea.setEditable(false);
        statsArea.setFont(new Font("Monospaced", Font.BOLD, 12));
        JScrollPane statsScroll = new JScrollPane(statsArea);
        statsScroll.setBorder(BorderFactory.createTitledBorder("Final Statistics"));
        add(statsScroll, BorderLayout.SOUTH);
    }

    private void startSimulation() {
        String file = fileField.getText();
        int quantum;

        try {
            quantum = Integer.parseInt(quantumField.getText());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid Time Quantum. Please enter an integer.");
            return;
        }

        if (file.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a process CSV file.");
            return;
        }

        // Reset UI for a fresh run
        logArea.setText("");
        statsArea.setText("");
        startButton.setEnabled(false);
        progressContainer.removeAll();
        progressBars.clear();
        progressContainer.revalidate();
        progressContainer.repaint();

        // Initialize and start the CPU thread
        CPU cpu = new CPU(file, quantum, this);
        cpu.start();
    }

    @Override
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void showStatistics(String stats) {
        SwingUtilities.invokeLater(() -> {
            statsArea.setText(stats);
            startButton.setEnabled(true);
        });
    }

    @Override
    public void initializeProgress(int[] pids, int[] totalBursts) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < pids.length; i++) {
                int pid = pids[i];

                JPanel row = new JPanel(new BorderLayout());
                row.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

                JLabel label = new JLabel("PID " + pid + " ");
                JProgressBar bar = new JProgressBar(0, 100);
                bar.setValue(0);
                bar.setStringPainted(true);

                row.add(label, BorderLayout.WEST);
                row.add(bar, BorderLayout.CENTER);

                progressBars.put(pid, bar);
                progressContainer.add(row);
            }
            progressContainer.revalidate();
            progressContainer.repaint();
        });
    }

    @Override
    public void updateProcessProgress(int pid, int remainingBurst, int totalBurst) {
        SwingUtilities.invokeLater(() -> {
            JProgressBar bar = progressBars.get(pid);
            if (bar != null) {
                int completed = totalBurst - remainingBurst;
                int percentage = (int) (((double) completed / totalBurst) * 100);
                bar.setValue(percentage);
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new CPUGUI().setVisible(true);
        });
    }
}