package stadion;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Pojedynczy kibic - osobny watek, ktory przechodzi cala sciezke na stadion:
 * przyjscie, kolejka, kontrola, mecz, wyjscie.
 */
public class Kibic extends Thread {
    private final int id;
    private final String druzyna;
    private final StadionMonitor monitor;

    private volatile int liczbaPrzepuszczen = 0;
    private volatile String status = "Oczekiwanie na przyjscie";
    private int nrStanowiska = -1;

    public Kibic(int id, String druzyna, StadionMonitor monitor) {
        super("Kibic-" + id);
        this.id = id;
        this.druzyna = druzyna;
        this.monitor = monitor;
    }

    /**
     * Sen podzielony na krotkie odcinki. Dzieki temu pauza i STOP dzialaja
     * od razu, a nie dopiero po zakonczeniu calego opoznienia.
     */
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
            // Kibice nie przychodza wszyscy naraz - najpierw jest 0 osob.
            safeSleep(ThreadLocalRandom.current().nextInt(5000));

            monitor.checkPauseAndStop();

            // 1. Kolejka
            status = "W kolejce";
            nrStanowiska = monitor.wejdzDoKolejki(this);

            // 2. Kontrola
            status = "Kontrola (St." + (nrStanowiska + 1) + ")";
            safeSleep(ThreadLocalRandom.current().nextInt(2000) + 1500);

            // 3. Stadion
            monitor.zwolnijStanowisko(nrStanowiska, this);
            status = "Na stadionie";
            safeSleep(ThreadLocalRandom.current().nextInt(6000) + 4000);

            // 4. Wyjscie
            status = "Wychodzi";
            monitor.opuscStadion();
            status = "Poza stadionem";

        } catch (InterruptedException e) {
            // STOP albo wylaczenie aplikacji - watek konczy prace bez zwalniania zasobow.
            status = "Zatrzymany";
        }
    }

    public void inkrementujPrzepuszczenia() {
        this.liczbaPrzepuszczen++;
    }

    public int getIdKibica() { return id; }
    public int getLiczbaPrzepuszczen() { return liczbaPrzepuszczen; }
    public String getDruzyna() { return druzyna; }
    public String getStatus() { return status; }
}
