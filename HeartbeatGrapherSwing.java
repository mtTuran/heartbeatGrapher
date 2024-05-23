import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class HeartbeatGrapherSwing extends JPanel {

    private static final int SAMPLE_RATE = 44100;
    private static final double THRESHOLD_MULTIPLIER = 2.5;
    private static final int MIN_PEAK_DISTANCE = 5000;  // Minimum distance between peaks in samples

    private double[] audioData;
    private List<Integer> peakIndices = new ArrayList<>();
    private double bpm = 0;

    public HeartbeatGrapherSwing() {
        setPreferredSize(new Dimension(800, 600));
        setBackground(Color.WHITE);

        // Read WAV file and extract data
        try {
            File file = new File("dong3_Heart.wav");
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = audioInputStream.getFormat();

            // Calculate total frames and allocate buffer
            int frameSize = format.getFrameSize();
            byte[] buffer = new byte[audioInputStream.available()];
            audioInputStream.read(buffer);

            // Convert bytes to double array
            audioData = new double[buffer.length / frameSize];
            for (int i = 0; i < audioData.length; i++) {
                int start = i * frameSize;
                int end = start + frameSize;
                byte[] frameBytes = new byte[frameSize];
                System.arraycopy(buffer, start, frameBytes, 0, frameSize);
                audioData[i] = ByteBuffer.wrap(frameBytes).getShort();
            }

            audioInputStream.close();

            // Detect peaks and calculate BPM
            detectPeaks();
            calculateBPM();

        } catch (UnsupportedAudioFileException | IOException e) {
            e.printStackTrace();
        }
    }

    private void detectPeaks() {
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

    private void calculateBPM() {
        if (peakIndices.size() < 2) {
            bpm = 0;
            return;
        }

        double totalTimeInSeconds = audioData.length / (double) SAMPLE_RATE;
        bpm = (peakIndices.size() / totalTimeInSeconds) * 60;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (audioData != null) {
            // Draw the graph
            int width = getWidth();
            int height = getHeight();
            g.setColor(Color.BLACK);
            int lastX = 0, lastY = height / 2;
            for (int i = 0; i < audioData.length; i++) {
                int x = i * width / audioData.length;
                int y = height / 2 - (int) (audioData[i] * height / Short.MAX_VALUE * 0.4);
                g.drawLine(lastX, lastY, x, y);
                lastX = x;
                lastY = y;
            }

            // Draw red dots on peaks
            g.setColor(Color.RED);
            for (int peakIndex : peakIndices) {
                int x = peakIndex * width / audioData.length;
                int y = height / 2 - (int) (audioData[peakIndex] * height / Short.MAX_VALUE * 0.4);
                g.fillOval(x - 3, y - 3, 6, 6);
            }
        }

        // Draw the BPM
        g.setColor(Color.RED);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString("BPM: " + (int) bpm, 10, 30);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Heartbeat Graph");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            HeartbeatGrapherSwing grapher = new HeartbeatGrapherSwing();
            frame.add(grapher);
            frame.pack();
            frame.setVisible(true);
        });
    }
}
