package stadion;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

public class StadionGUI extends JFrame {
    private static final String CONFIG_PATH = "resources/config.properties";

    private final Properties config = new Properties();
    private StadionMonitor monitor;
    private final List<Kibic> kibice = new ArrayList<>();
    
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JLabel labelStadion, labelStanowiska;
    private final JButton btnStart, btnPause, btnStop, btnExit;
    
    // Tylko te parametry są edytowalne w GUI przed startem
    private JTextField txtK, txtFans;

    public StadionGUI() {
        setTitle("Symulacja Stadionu - Liga Mistrzów");
        setSize(1000, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        loadConfig();

        // --- Panel Konfiguracji (Góra) ---
        JPanel panelNorth = new JPanel(new GridLayout(2, 1));
        
        JPanel panelSettings = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        javax.swing.border.TitledBorder border = BorderFactory.createTitledBorder("Konfiguracja (edytuj przed startem)");
        border.setTitleJustification(javax.swing.border.TitledBorder.CENTER);
        panelSettings.setBorder(border);
        
        txtK = new JTextField(config.getProperty("K"), 5);
        txtFans = new JTextField(config.getProperty("LiczbaKibicow"), 5);

        panelSettings.add(new JLabel("Pojemność Stadionu (K):")); panelSettings.add(txtK);
        panelSettings.add(new JLabel("Całkowita Liczba Kibiców:")); panelSettings.add(txtFans);

        JPanel panelButtons = new JPanel();
        btnStart = new JButton("START");
        btnPause = new JButton("PAUZA / WZNÓW");
        btnStop = new JButton("STOP / RESET");
        btnExit = new JButton("ZAKOŃCZ");
        
        btnPause.setEnabled(false);
        btnStop.setEnabled(false);

        panelButtons.add(btnStart);
        panelButtons.add(btnPause);
        panelButtons.add(btnStop);
        panelButtons.add(btnExit);

        panelNorth.add(panelSettings);
        panelNorth.add(panelButtons);
        add(panelNorth, BorderLayout.NORTH);

        // --- Tabela (Środek) ---
        String[] columnNames = {"ID", "Drużyna", "Status", "Przepuszczenia"};
        tableModel = new DefaultTableModel(columnNames, 0);
        table = new JTable(tableModel);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // --- Statystyki (Dół) ---
        JPanel panelSouth = new JPanel(new GridLayout(2, 1, 5, 5));
        panelSouth.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        labelStadion = new JLabel("Na stadionie: 0 / 0");
        labelStadion.setFont(new Font("Arial", Font.BOLD, 20));
        labelStanowiska = new JLabel("Stanowiska: [...]");
        panelSouth.add(labelStadion);
        panelSouth.add(labelStanowiska);
        add(panelSouth, BorderLayout.SOUTH);

        // Akcje
        btnStart.addActionListener(e -> startSimulation());
        btnPause.addActionListener(e -> togglePause());
        btnStop.addActionListener(e -> stopSimulation());
        btnExit.addActionListener(e -> System.exit(0));

        // Timer odświeżający GUI (10 razy na sekundę)
        new Timer(100, e -> refreshGUI()).start();
    }

    private void loadConfig() {
        try (FileInputStream in = new FileInputStream(CONFIG_PATH)) {
            config.load(in);
        } catch (IOException e) {
            // Wartości domyślne zgodne z poleceniem
            config.setProperty("K", "15");
            config.setProperty("LiczbaKibicow", "40");
            config.setProperty("LiczbaStanowisk", "3");
            config.setProperty("MaxOsobNaStanowisku", "3");
            config.setProperty("MaxPrzepuszczen", "5");
        }
    }

    private void saveConfig() {
        config.setProperty("K", txtK.getText());
        config.setProperty("LiczbaKibicow", txtFans.getText());
        // Pozostałe parametry pozostają stałe (zgodnie z zadaniem)
        try (FileOutputStream out = new FileOutputStream(CONFIG_PATH)) {
            config.store(out, null);
        } catch (IOException ex) {}
    }

    private void startSimulation() {
        saveConfig(); 
        
        int K = Integer.parseInt(txtK.getText());
        int nFans = Integer.parseInt(txtFans.getText());
        
        // Parametry stałe wg. polecenia
        int nStan = Integer.parseInt(config.getProperty("LiczbaStanowisk", "3"));
        int mOsob = Integer.parseInt(config.getProperty("MaxOsobNaStanowisku", "3"));
        int mPrzep = Integer.parseInt(config.getProperty("MaxPrzepuszczen", "5"));

        monitor = new StadionMonitor(K, nStan, mOsob, mPrzep);
        kibice.clear();
        tableModel.setRowCount(0);

        java.util.Random rnd = new java.util.Random();
        for (int i = 1; i <= nFans; i++) {
            String druzyna = rnd.nextBoolean() ? "Real Madryt" : "Borussia Dortmund";
            Kibic k = new Kibic(i, druzyna, monitor);
            kibice.add(k);
            tableModel.addRow(new Object[]{i, druzyna, "Przychodzi...", 0});
            k.start();
        }

        txtK.setEnabled(false);
        txtFans.setEnabled(false);
        btnStart.setEnabled(false);
        btnPause.setEnabled(true);
        btnStop.setEnabled(true);
    }

    private void togglePause() {
        if (monitor == null) return;
        boolean nowPaused = !monitor.isPaused();
        monitor.setPaused(nowPaused);
    }

    private void stopSimulation() {
        if (monitor != null) {
            monitor.stop();
            for (Kibic k : kibice) {
                k.interrupt(); // To przerwie safeSleep i oczekiwanie w monitorze
            }
        }
        txtK.setEnabled(true);
        txtFans.setEnabled(true);
        btnStart.setEnabled(true);
        btnPause.setEnabled(false);
        btnStop.setEnabled(false);
    }

    private void refreshGUI() {
        if (monitor == null) return;

        // Aktualizacja tabeli statusów
        for (int i = 0; i < kibice.size(); i++) {
            Kibic k = kibice.get(i);
            tableModel.setValueAt(k.getStatus(), i, 2);
            tableModel.setValueAt(k.getLiczbaPrzepuszczen(), i, 3);
        }

        // Aktualizacja napisów pod tabelą
        labelStadion.setText("Stadion: " + monitor.getObecniNaStadionie() + " / " + monitor.getK() + 
                            "  |  Wszyscy Kibice: " + kibice.size());
        
        StringBuilder sb = new StringBuilder("Stanowiska: ");
        List<List<String>> st = monitor.getStanowiska();
        for (int i = 0; i < monitor.getLiczbaStanowisk(); i++) {
            if (i >= st.size()) break;
            List<String> osoby = st.get(i);
            sb.append("St.").append(i + 1).append(": ").append(osoby.size()).append(" os. (")
              .append(osoby.isEmpty() ? "brak" : osoby.get(0)).append(") | ");
        }
        labelStanowiska.setText(sb.toString());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StadionGUI().setVisible(true));
    }
}
