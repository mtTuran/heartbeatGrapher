import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class HeartbeatGrapherSwing extends JPanel {

    private static final int ORIGINAL_SAMPLE_RATE = 44100;
    private static final int TARGET_SAMPLE_RATE = 8000;
    private static final double THRESHOLD = 1000.0;

    private double[] audioData;
    private List<Integer> peakIndices = new ArrayList<>();
    private double bpm = 0;

    public HeartbeatGrapherSwing() {
        setPreferredSize(new Dimension(800, 600));
        setBackground(Color.WHITE);

        // Read WAV file and extract data
        try {
            File file = new File("Christians_Heart.wav");
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = audioInputStream.getFormat();

            // Calculate total frames and allocate buffer
            int frameSize = format.getFrameSize();
            byte[] buffer = new byte[audioInputStream.available()];
            audioInputStream.read(buffer);

            // Convert bytes to double array and downsample
            audioData = new double[buffer.length / frameSize];
            for (int i = 0; i < audioData.length; i++) {
                int start = i * frameSize;
                int end = start + frameSize;
                byte[] frameBytes = new byte[frameSize];
                System.arraycopy(buffer, start, frameBytes, 0, frameSize);
                audioData[i] = ByteBuffer.wrap(frameBytes).getShort();
            }

            audioInputStream.close();

            // Downsample the audio data
            audioData = downsample(audioData, ORIGINAL_SAMPLE_RATE, TARGET_SAMPLE_RATE);

            // Detect peaks and calculate BPM
            detectPeaks();
            calculateBPM();

        } catch (UnsupportedAudioFileException | IOException e) {
            e.printStackTrace();
        }
    }

    private double[] downsample(double[] data, int originalRate, int targetRate) {
        int ratio = originalRate / targetRate;
        double[] downsampledData = new double[data.length / ratio];
        for (int i = 0; i < downsampledData.length; i++) {
            downsampledData[i] = data[i * ratio];
        }
        return downsampledData;
    }

    private void detectPeaks() {
        for (int i = 1; i < audioData.length - 1; i++) {
            if (audioData[i] > THRESHOLD && audioData[i] > audioData[i - 1] && audioData[i] > audioData[i + 1]) {
                if (peakIndices.isEmpty() || i - peakIndices.get(peakIndices.size() - 1) > TARGET_SAMPLE_RATE / 2.5) { // 0.4s minimum interval
                    peakIndices.add(i);
                }
            }
        }
    }

    private void calculateBPM() {
        if (peakIndices.size() < 2) return;

        double totalInterval = 0;
        for (int i = 1; i < peakIndices.size(); i++) {
            totalInterval += (peakIndices.get(i) - peakIndices.get(i - 1));
        }
        double averageInterval = totalInterval / (peakIndices.size() - 1);
        bpm = TARGET_SAMPLE_RATE * 60 / averageInterval;
    }

    private double getTimeInterval() {
        return 1.0 / TARGET_SAMPLE_RATE;
    }
/*
    private double calculateHRV(){
        double totalInterval = 0;
        for (int i = 0; i < peakIndices.size() - 1; i = i + 1){
            totalInterval += ((peakIndices.get(i + 1) - peakIndices.get(i)) * (getTimeInterval()));
        }

    }
*/
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
                int y = height / 2 + (int) (audioData[i] * height / Short.MAX_VALUE * 0.4);
                g.drawLine(lastX, lastY, x, y);
                lastX = x;
                lastY = y;
            }
        }
        // Draw the BPM
        g.setColor(Color.RED);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString("BPM: " + (int) bpm, 10, 30);
        // Draw the Time Interval
        g.setFont(new Font("Arial", Font.PLAIN, 16));
        g.drawString(String.format("Time interval: %.8f seconds", getTimeInterval()), 10, 60);
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
