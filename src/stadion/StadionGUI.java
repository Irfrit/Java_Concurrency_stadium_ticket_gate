package stadion;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Okno symulacji: panel konfiguracji, tabela kibicow i podglad stanu stadionu.
 * Parametry wchodzace (pojemnosc i liczba watkow) mozna zmienic przed startem,
 * pozostale pochodza z pliku resources/config.properties.
 */
public class StadionGUI extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final String CONFIG_PATH = "resources/config.properties";
    private static final int MAX_KIBICOW = 300;

    private static final String DRUZYNA_A = "Real Madryt";
    private static final String DRUZYNA_B = "Borussia Dortmund";

    private final Properties config = new Properties();
    private StadionMonitor monitor;
    private final List<Kibic> kibice = new ArrayList<>();

    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JLabel labelStadion, labelStanowiska;
    private final JButton btnStart, btnPause, btnStop, btnExit;

    private final JTextField txtK = new JTextField(4);
    private final JTextField txtFans = new JTextField(4);
    private final JTextField txtStanowiska = new JTextField(4);
    private final JTextField txtNaStanowisku = new JTextField(4);
    private final JTextField txtPrzepuszczenia = new JTextField(4);

    public StadionGUI() {
        setTitle("Symulacja wejscia na stadion - Liga Mistrzow");
        setSize(1000, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        loadConfig();

        // --- Panel konfiguracji (gora) ---
        JPanel panelNorth = new JPanel(new GridLayout(2, 1));

        JPanel panelSettings = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        TitledBorder border = BorderFactory.createTitledBorder("Konfiguracja (edytuj przed startem)");
        border.setTitleJustification(TitledBorder.CENTER);
        panelSettings.setBorder(border);

        txtK.setText(config.getProperty("K", "15"));
        txtFans.setText(config.getProperty("LiczbaKibicow", "40"));

        txtStanowiska.setText(config.getProperty("LiczbaStanowisk", "3"));
        txtNaStanowisku.setText(config.getProperty("MaxOsobNaStanowisku", "3"));
        txtPrzepuszczenia.setText(config.getProperty("MaxPrzepuszczen", "5"));

        panelSettings.add(new JLabel("Pojemnosc stadionu (K):"));
        panelSettings.add(txtK);
        panelSettings.add(new JLabel("Liczba kibicow:"));
        panelSettings.add(txtFans);

        // Parametry stale wg tresci zadania - tylko podglad, zeby bylo widac,
        // ze program czyta je z pliku.
        JTextField[] stale = {txtStanowiska, txtNaStanowisku, txtPrzepuszczenia};
        String[] etykiety = {"Stanowiska:", "Osob na stanowisku:", "Max przepuszczen:"};
        for (int i = 0; i < stale.length; i++) {
            stale[i].setEnabled(false);
            stale[i].setDisabledTextColor(Color.DARK_GRAY);
            stale[i].setToolTipText("Parametr staly z tresci zadania (plik " + CONFIG_PATH + ")");
            panelSettings.add(new JLabel(etykiety[i]));
            panelSettings.add(stale[i]);
        }

        JPanel panelButtons = new JPanel();
        btnStart = new JButton("START");
        btnPause = new JButton("PAUZA / WZNOW");
        btnStop = new JButton("STOP / RESET");
        btnExit = new JButton("ZAKONCZ");

        btnPause.setEnabled(false);
        btnStop.setEnabled(false);

        panelButtons.add(btnStart);
        panelButtons.add(btnPause);
        panelButtons.add(btnStop);
        panelButtons.add(btnExit);

        panelNorth.add(panelSettings);
        panelNorth.add(panelButtons);
        add(panelNorth, BorderLayout.NORTH);

        // --- Tabela kibicow (srodek) ---
        String[] columnNames = {"ID", "Druzyna", "Status", "Przepuszczenia"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(22);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // --- Statystyki (dol) ---
        JPanel panelSouth = new JPanel(new GridLayout(2, 1, 5, 5));
        panelSouth.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        labelStadion = new JLabel("Na stadionie: 0 / 0");
        labelStadion.setFont(new Font("Arial", Font.BOLD, 20));
        labelStanowiska = new JLabel("Stanowiska: [...]");
        panelSouth.add(labelStadion);
        panelSouth.add(labelStanowiska);
        add(panelSouth, BorderLayout.SOUTH);

        btnStart.addActionListener(e -> startSimulation());
        btnPause.addActionListener(e -> togglePause());
        btnStop.addActionListener(e -> stopSimulation());
        btnExit.addActionListener(e -> {
            stopSimulation();
            dispose();
            System.exit(0);
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopSimulation();
            }
        });

        // Timer odswiezajacy GUI 10 razy na sekunde.
        new Timer(100, e -> refreshGUI()).start();
    }

    private void loadConfig() {
        try (FileInputStream in = new FileInputStream(CONFIG_PATH)) {
            config.load(in);
        } catch (IOException e) {
            // Brak pliku - wartosci domyslne zgodne z trescia zadania.
            config.setProperty("K", "15");
            config.setProperty("LiczbaKibicow", "40");
            config.setProperty("LiczbaStanowisk", "3");
            config.setProperty("MaxOsobNaStanowisku", "3");
            config.setProperty("MaxPrzepuszczen", "5");
        }
    }

    /** Zapisuje parametry z okna do pliku, zeby nastepny start byl taki sam. */
    private void saveConfig() {
        config.setProperty("K", txtK.getText().trim());
        config.setProperty("LiczbaKibicow", txtFans.getText().trim());
        config.setProperty("LiczbaStanowisk", txtStanowiska.getText().trim());
        config.setProperty("MaxOsobNaStanowisku", txtNaStanowisku.getText().trim());
        config.setProperty("MaxPrzepuszczen", txtPrzepuszczenia.getText().trim());
        try (FileOutputStream out = new FileOutputStream(CONFIG_PATH)) {
            config.store(out, "Parametry symulacji stadionu");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Nie udalo sie zapisac pliku " + CONFIG_PATH + ":\n" + ex.getMessage(),
                    "Blad zapisu", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void startSimulation() {
        int K = pobierzLiczbe(txtK, "Pojemnosc stadionu (K)", 1, MAX_KIBICOW);
        int nFans = pobierzLiczbe(txtFans, "Liczba kibicow", 1, MAX_KIBICOW);
        int nStan = pobierzLiczbe(txtStanowiska, "Liczba stanowisk", 1, 50);
        int mOsob = pobierzLiczbe(txtNaStanowisku, "Osob na stanowisku", 1, 50);
        int mPrzep = pobierzLiczbe(txtPrzepuszczenia, "Max przepuszczen", 0, 1000);
        if (K < 0 || nFans < 0 || nStan < 0 || mOsob < 0 || mPrzep < 0) {
            return; // komunikat bledu zostal juz pokazany
        }

        saveConfig();

        monitor = new StadionMonitor(K, nStan, mOsob, mPrzep);
        kibice.clear();
        tableModel.setRowCount(0);

        for (int i = 1; i <= nFans; i++) {
            String druzyna = ThreadLocalRandom.current().nextBoolean() ? DRUZYNA_A : DRUZYNA_B;
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

    /**
     * Wczytuje liczbe z pola tekstowego. Przy bledzie pokazuje komunikat
     * i zwraca -1, zeby nie startowac symulacji z blednymi danymi.
     */
    private int pobierzLiczbe(JTextField pole, String nazwa, int min, int max) {
        String tekst = pole.getText().trim();
        try {
            int wartosc = Integer.parseInt(tekst);
            if (wartosc < min || wartosc > max) {
                JOptionPane.showMessageDialog(this,
                        nazwa + ": dopuszczalny zakres to " + min + " - " + max + ".",
                        "Bledna wartosc", JOptionPane.ERROR_MESSAGE);
                return -1;
            }
            return wartosc;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    nazwa + ": \"" + tekst + "\" nie jest liczba calkowita.",
                    "Bledna wartosc", JOptionPane.ERROR_MESSAGE);
            return -1;
        }
    }

    private void togglePause() {
        if (monitor == null) return;
        boolean nowPaused = !monitor.isPaused();
        monitor.setPaused(nowPaused);
        btnPause.setText(nowPaused ? "WZNOW" : "PAUZA / WZNOW");
    }

    private void stopSimulation() {
        if (monitor != null && !monitor.isStopped()) {
            monitor.stop();
            for (Kibic k : kibice) {
                k.interrupt(); // przerywa safeSleep i oczekiwanie w monitorze
            }
        }
        txtK.setEnabled(true);
        txtFans.setEnabled(true);
        btnStart.setEnabled(true);
        btnPause.setEnabled(false);
        btnPause.setText("PAUZA / WZNOW");
        btnStop.setEnabled(false);
    }

    private void refreshGUI() {
        if (monitor == null) return;

        for (int i = 0; i < kibice.size(); i++) {
            tableModel.setValueAt(kibice.get(i).getStatus(), i, 2);
            tableModel.setValueAt(kibice.get(i).getLiczbaPrzepuszczen(), i, 3);
        }

        labelStadion.setText("Stadion: " + monitor.getObecniNaStadionie() + " / " + monitor.getK()
                + "   |   W kolejce: " + monitor.getDlugoscKolejki()
                + "   |   Kibice: " + kibice.size());

        StringBuilder sb = new StringBuilder("Stanowiska: ");
        List<List<String>> st = monitor.getStanowiska();
        for (int i = 0; i < st.size(); i++) {
            List<String> osoby = st.get(i);
            sb.append("St.").append(i + 1).append(": ").append(osoby.size()).append(" os. (")
              .append(osoby.isEmpty() ? "brak" : osoby.get(0)).append(")  ");
        }
        labelStanowiska.setText(sb.toString());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StadionGUI().setVisible(true));
    }
}
