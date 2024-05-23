import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class RealTimeHeartbeatGrapher extends JPanel {

    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 1024;
    private static final int UPDATE_INTERVAL_MS = 100;
    private static final double THRESHOLD = 1000.0;
    private static final double THRESHOLD_MULTIPLIER = 2.5;
    private static final int MIN_PEAK_DISTANCE = 7500;

    private byte[] audioBuffer = new byte[BUFFER_SIZE];
    private double[] audioData;
    private List<Long> peakTimes = new ArrayList<>();
    private List<Integer> peakIndices = new ArrayList<>();
    private double bpm = 0;
    private double hrv = 0; // Heart Rate Variation will be in milliseconds
    private boolean isRealTimeGraphing = false;
    private boolean isFileProcessing = false;

    private TargetDataLine line;
    private JFrame parentFrame;
    private JPanel graphPanel;
    private JButton chooseFileButton;
    private JButton startRealTimeButton;
    private JButton stopRealTimeButton;
    private JButton clearButton;
    private JButton saveButton;

    public RealTimeHeartbeatGrapher(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        setPreferredSize(new Dimension(800, 600));
        setLayout(new BorderLayout());
        setBackground(Color.DARK_GRAY);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        graphPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (audioData != null) {
                    int width = getWidth();
                    int height = getHeight();
                    g.setColor(Color.WHITE);
                    int lastX = 0, lastY = height / 2;
                    for (int i = 0; i < audioData.length; i++) {
                        int x = i * width / audioData.length;
                        int y = height / 2 + (int) (audioData[i] * height / Short.MAX_VALUE * 0.4);
                        g.drawLine(lastX, lastY, x, y);
                        lastX = x;
                        lastY = y;
                    }

                    if (isFileProcessing) {
                        g.setColor(Color.RED);
                        for (int peakIndex : peakIndices) {
                            int x = peakIndex * width / audioData.length;
                            int y = height / 2 + (int) (audioData[peakIndex] * height / Short.MAX_VALUE * 0.4);
                            g.fillOval(x - 3, y - 3, 6, 6);
                        }
                    }
                }
                g.setColor(Color.RED);
                g.setFont(new Font("Arial", Font.BOLD, 20));
                g.drawString("BPM: " + (int) bpm, 10, 30);
                g.drawString("HRV: " + (int) hrv + " ms", 10, 60);
            }
        };
        graphPanel.setBackground(Color.DARK_GRAY);
        graphPanel.setLayout(new BorderLayout());
        add(graphPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(Color.DARK_GRAY);
        chooseFileButton = new JButton("Choose File");
        startRealTimeButton = new JButton("Start Real-Time");
        stopRealTimeButton = new JButton("Stop Real-Time");
        clearButton = new JButton("Clear Graph");
        saveButton = new JButton("Save Graph");
        buttonPanel.add(chooseFileButton);
        buttonPanel.add(startRealTimeButton);
        buttonPanel.add(stopRealTimeButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(saveButton);
        add(buttonPanel, BorderLayout.SOUTH);

        chooseFileButton.addActionListener(e -> chooseFile());
        startRealTimeButton.addActionListener(e -> startRealTimeGraphing());
        stopRealTimeButton.addActionListener(e -> stopRealTimeGraphing());
        clearButton.addActionListener(e -> clearGraph());
        saveButton.addActionListener(e -> saveGraph());
    }

    private void chooseFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(parentFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            processWavFile(selectedFile);
        }
    }

    private void processWavFile(File file) {
        try {
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = audioInputStream.getFormat();

            int frameSize = format.getFrameSize();
            byte[] buffer = new byte[audioInputStream.available()];
            audioInputStream.read(buffer);

            audioData = new double[buffer.length / frameSize];
            for (int i = 0; i < audioData.length; i++) {
                int start = i * frameSize;
                int end = start + frameSize;
                byte[] frameBytes = new byte[frameSize];
                System.arraycopy(buffer, start, frameBytes, 0, frameSize);
                audioData[i] = ByteBuffer.wrap(frameBytes).getShort();
            }

            audioInputStream.close();

            isFileProcessing = true;
            detectPeaksFromFile();
            calculateBPMFromFile();
            calculateHRV();
            repaint();
        } catch (UnsupportedAudioFileException | IOException e) {
            e.printStackTrace();
        }
    }

    private void detectPeaksFromFile() {
        peakIndices.clear();
        double threshold = calculateThreshold(audioData);
        int lastPeakIndex = -MIN_PEAK_DISTANCE;

        for (int i = 1; i < audioData.length - 1; i++) {
            if (audioData[i] > threshold && audioData[i] > audioData[i - 1] && audioData[i] > audioData[i + 1]) {
                if (i - lastPeakIndex >= MIN_PEAK_DISTANCE) {
                    peakIndices.add(i);
                    lastPeakIndex = i;
                }
            }
        }
    }

    private double calculateThreshold(double[] data) {
        double mean = calculateMean(data, 0, data.length - 1);
        double std = calculateStd(data, mean, 0, data.length - 1);
        return mean + THRESHOLD_MULTIPLIER * std;
    }

    private double calculateMean(double[] data, int start, int end) {
        double sum = 0;
        for (int i = start; i <= end; i++) {
            sum += data[i];
        }
        return sum / (end - start + 1);
    }

    private double calculateStd(double[] data, double mean, int start, int end) {
        double sumSquares = 0;
        for (int i = start; i <= end; i++) {
            sumSquares += Math.pow(data[i] - mean, 2);
        }
        double variance = sumSquares / (end - start + 1);
        return Math.sqrt(variance);
    }

    private void calculateBPMFromFile() {
        if (peakIndices.size() < 2) {
            bpm = 0;
            return;
        }

        double totalTimeInSeconds = audioData.length / (double) SAMPLE_RATE;
        bpm = (peakIndices.size() / totalTimeInSeconds) * 60;
    }

    private void calculateHRV() {
        if (peakTimes.size() < 2) {
            hrv = 0;
            return;
        }

        long sum = 0;
        for (int i = 1; i < peakTimes.size(); i++) {
            sum += peakTimes.get(i) - peakTimes.get(i - 1);
        }
        hrv = (double) sum / (peakTimes.size() - 1);
    }

    private void startRealTimeGraphing() {
        if (!isRealTimeGraphing) {
            try {
                AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format, BUFFER_SIZE);
                line.start();

                isRealTimeGraphing = true;

                new Thread(() -> {
                    while (isRealTimeGraphing) {
                        line.read(audioBuffer, 0, BUFFER_SIZE);
                        processAudioData();
                        detectPeaks();
                        calculateBPM();
                        calculateHRV();
                        repaint();
                        try {
                            Thread.sleep(UPDATE_INTERVAL_MS);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopRealTimeGraphing() {
        if (isRealTimeGraphing) {
            line.stop();
            line.close();
            isRealTimeGraphing = false;
        }
    }

    private void clearGraph() {
        audioData = null;
        peakIndices.clear();
        peakTimes.clear();
        bpm = 0;
        hrv = 0;
        isFileProcessing = false;
        repaint();
    }

    private void saveGraph() {
        if (audioData != null) {
            try {
                JFileChooser fileChooser = new JFileChooser();
                int result = fileChooser.showSaveDialog(parentFrame);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    String fileName = selectedFile.getAbsolutePath();
                    if (!fileName.toLowerCase().endsWith(".png")) {
                        fileName += ".png";
                    }

                    Dimension size = graphPanel.getSize();
                    BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2 = image.createGraphics();
                    graphPanel.paint(g2);
                    g2.dispose();

                    File outputFile = new File(fileName);
                    ImageIO.write(image, "png", outputFile);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void processAudioData() {
        audioData = new double[audioBuffer.length / 2];
        for (int i = 0; i < audioData.length; i++) {
            short sample = ByteBuffer.wrap(audioBuffer, i * 2, 2).getShort();
            audioData[i] = sample;
        }
    }

    private void detectPeaks() {
        long currentTime = System.currentTimeMillis();
        for (double sample : audioData) {
            if (sample > THRESHOLD) {
                if (peakTimes.isEmpty() || currentTime - peakTimes.get(peakTimes.size() - 1) > 400) {
                    peakTimes.add(currentTime);
                }
            }
        }
    }

    private void calculateBPM() {
        long currentTime = System.currentTimeMillis();
    
        // Remove peaks older than 60 seconds
        peakTimes.removeIf(time -> currentTime - time > 60000);
    
        if (peakTimes.size() >= 2) {
            long timeSpan = peakTimes.get(peakTimes.size() - 1) - peakTimes.get(0);
            double averageInterval = (double) timeSpan / (peakTimes.size() - 1);
            bpm = 60000 / averageInterval;
        } else {
            // Reset BPM to 0 if there are no recent peaks
            bpm = 0;
        }
    }


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (audioData != null) {
            int width = getWidth();
            int height = getHeight();
            g.setColor(Color.WHITE);
            int lastX = 0, lastY = height / 2;
            for (int i = 0; i < audioData.length; i++) {
                int x = i * width / audioData.length;
                int y = height / 2 + (int) (audioData[i] * height / Short.MAX_VALUE * 0.4);
                g.drawLine(lastX, lastY, x, y);
                lastX = x;
                lastY = y;
            }

            if (isFileProcessing) {
                g.setColor(Color.RED);
                for (int peakIndex : peakIndices) {
                    int x = peakIndex * width / audioData.length;
                    int y = height / 2 + (int) (audioData[peakIndex] * height / Short.MAX_VALUE * 0.4);
                    g.fillOval(x - 3, y - 3, 6, 6);
                }
            }
        }
        g.setColor(Color.RED);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString("BPM: " + (int) bpm, 10, 30);
        g.drawString("HRV: " + (int) hrv + " ms", 10, 60);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Real-Time Heartbeat Graph");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            RealTimeHeartbeatGrapher grapher = new RealTimeHeartbeatGrapher(frame);
            frame.add(grapher);
            frame.pack();
            frame.setVisible(true);
        });
    }
}


