import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class RealTimeHeartbeatGrapherSwing extends JPanel {

    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 1024;
    private static final int UPDATE_INTERVAL_MS = 100; // Adjust as needed
    private static final double THRESHOLD = 1000.0; // Adjust this threshold as needed

    private byte[] audioBuffer = new byte[BUFFER_SIZE];
    private double[] audioData;
    private List<Long> peakTimes = new ArrayList<>();
    private double bpm = 0;

    public RealTimeHeartbeatGrapherSwing() {
        setPreferredSize(new Dimension(800, 600));
        setBackground(Color.WHITE);

        // Set up audio capture from microphone
        try {
            AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format, BUFFER_SIZE);
            line.start();

            // Capture audio data from microphone in real-time
            new Thread(() -> {
                while (true) {
                    line.read(audioBuffer, 0, BUFFER_SIZE);
                    processAudioData();
                    detectPeaks();
                    calculateBPM();
                    repaint(); // Refresh the graph
                    try {
                        Thread.sleep(UPDATE_INTERVAL_MS); // Introduce delay
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    private void processAudioData() {
        // Convert bytes to double array
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
                if (peakTimes.isEmpty() || currentTime - peakTimes.get(peakTimes.size() - 1) > 600) {
                    peakTimes.add(currentTime);
                }
            }
        }
    }

    private void calculateBPM() {
        if (peakTimes.size() < 2) return;
        
        long currentTime = System.currentTimeMillis();
        peakTimes.removeIf(time -> currentTime - time > 60000); // Remove peaks older than 1 minute

        if (peakTimes.size() >= 2) {
            long timeSpan = peakTimes.get(peakTimes.size() - 1) - peakTimes.get(0);
            double averageInterval = (double) timeSpan / (peakTimes.size() - 1);
            bpm = 60000 / averageInterval;
        }
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
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Real-Time Heartbeat Graph");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            RealTimeHeartbeatGrapherSwing grapher = new RealTimeHeartbeatGrapherSwing();
            frame.add(grapher);
            frame.pack();
            frame.setVisible(true);
        });
    }
}
