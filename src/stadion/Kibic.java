package stadion;

import java.util.Random;

public class Kibic extends Thread {
    private final int id;
    private final String druzyna;
    private final StadionMonitor monitor;
    private int liczbaPrzepuszczen = 0;
    private String status = "Oczekiwanie na przyjście";
    private int nrStanowiska = -1;

    public Kibic(int id, String druzyna, StadionMonitor monitor) {
        super("Kibic-" + id);
        this.id = id;
        this.druzyna = druzyna;
        this.monitor = monitor;
    }

    private void safeSleep(int ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            monitor.checkPauseAndStop();
            long remaining = end - System.currentTimeMillis();
            if (remaining <= 0) break;
            Thread.sleep(Math.min(100, remaining));
        }
    }

    @Override
    public void run() {
        try {
            // Kibice nie przychodzą wszyscy naraz - najpierw jest 0 osób
            safeSleep(new Random().nextInt(5000));
            
            monitor.checkPauseAndStop();
            
            // 1. Kolejka
            status = "W kolejce";
            nrStanowiska = monitor.wejdzDoKolejki(this);
            
            // 2. Kontrola
            status = "Kontrola (St." + (nrStanowiska + 1) + ")";
            safeSleep(new Random().nextInt(2000) + 1500);
            
            // 3. Stadion
            monitor.zwolnijStanowisko(nrStanowiska, this);
            status = "Na stadionie";
            safeSleep(new Random().nextInt(6000) + 4000);
            
            // 4. Wyjście
            status = "Wychodzi";
            monitor.opuscStadion();
            status = "Poza stadionem";

        } catch (InterruptedException e) {
            status = "Zatrzymany";
        }
    }

    public void inkrementujPrzepuszczenia() {
        this.liczbaPrzepuszczen++;
    }

    public int getLiczbaPrzepuszczen() { return liczbaPrzepuszczen; }
    public String getDruzyna() { return druzyna; }
    public String getStatus() { return status; }
}
