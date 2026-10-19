package stadion;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;
import java.util.List;

/**
 * Monitor stadionu. Caly wspoldzielony stan (licznik osob na stadionie,
 * stanowiska kontroli, kolejka oczekujacych) jest chroniony jednym zamkiem,
 * wiec watki nie moga go zmieniac rownolegle.
 */
public class StadionMonitor {
    private final int K;
    private final int liczbaStanowisk;
    private final int maxNaStanowisku;
    private final int maxPrzepuszczen;

    private int obecniNaStadionie = 0;
    private final List<List<String>> stanowiska = new ArrayList<>();

    private final Lock lock = new ReentrantLock(true);
    private final Condition wejscieNaStadion = lock.newCondition();
    private final Condition stanowiskoDostepne = lock.newCondition();
    private final Condition pauseCondition = lock.newCondition();

    private final List<Kibic> kolejkaOczekujacych = new ArrayList<>();

    private boolean paused = false;
    private boolean stopped = false;

    public StadionMonitor(int K, int liczbaStanowisk, int maxNaStanowisku, int maxPrzepuszczen) {
        this.K = K;
        this.liczbaStanowisk = liczbaStanowisk;
        this.maxNaStanowisku = maxNaStanowisku;
        this.maxPrzepuszczen = maxPrzepuszczen;

        for (int i = 0; i < liczbaStanowisk; i++) {
            stanowiska.add(new ArrayList<>());
        }
    }

    /**
     * Punkt kontrolny wywolywany przez kibica w trakcie pracy. Watki nie trzymaja
     * tu zadnych zasobow, wiec moga bezpiecznie przeczekac pauze albo zakonczyc
     * dzialanie po STOP.
     */
    public void checkPauseAndStop() throws InterruptedException {
        lock.lock();
        try {
            if (stopped) throw new InterruptedException("STOPPED");
            while (paused) {
                pauseCondition.await();
                if (stopped) throw new InterruptedException("STOPPED");
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Zgloszenie kibica do kontroli. Metoda konczy sie dopiero, gdy kibic ma
     * zagwarantowane miejsce na stadionie i na konkretnym stanowisku.
     *
     * @return numer przyznanego stanowiska
     */
    public int wejdzDoKolejki(Kibic k) throws InterruptedException {
        lock.lock();
        try {
            kolejkaOczekujacych.add(k);

            while (true) {
                checkPauseAndStop();

                // 1. Sprawdz pojemnosc stadionu
                if (obecniNaStadionie >= K) {
                    wejscieNaStadion.await();
                    continue;
                }

                // 2. Znajdz wolne stanowisko
                int wybraneStanowisko = -1;
                for (int i = 0; i < liczbaStanowisk; i++) {
                    if (mozeWejscNaStanowisko(i, k.getDruzyna())) {
                        wybraneStanowisko = i;
                        break;
                    }
                }

                if (wybraneStanowisko != -1) {
                    // 3. Sprawdz, czy ktos przed nami nie czeka juz zbyt dlugo
                    if (!ktosMaPierwszenstwo(k)) {
                        for (Kibic przed : kolejkaOczekujacych) {
                            if (przed == k) break;
                            przed.inkrementujPrzepuszczenia();
                        }

                        kolejkaOczekujacych.remove(k);
                        stanowiska.get(wybraneStanowisko).add(k.getDruzyna());
                        obecniNaStadionie++;

                        return wybraneStanowisko;
                    }
                }

                stanowiskoDostepne.await();
            }
        } finally {
            // Kibic trafiony interruptem nie moze zostac w kolejce, bo blokowalby
            // wejscie pozostalym (jego licznik przepuszczen roslby w nieskonczonosc).
            kolejkaOczekujacych.remove(k);
            lock.unlock();
        }
    }

    /** Czy ktos stojacy przed kibicem k przepuscil juz maksymalna liczbe osob. */
    private boolean ktosMaPierwszenstwo(Kibic k) {
        for (Kibic przed : kolejkaOczekujacych) {
            if (przed == k) break;
            if (przed.getLiczbaPrzepuszczen() >= maxPrzepuszczen) {
                return true;
            }
        }
        return false;
    }

    /** Sprawdza limit osob na stanowisku i zgodnosc druzyn. */
    private boolean mozeWejscNaStanowisko(int nr, String druzyna) {
        if (nr < 0 || nr >= stanowiska.size()) return false;
        List<String> osoby = stanowiska.get(nr);
        if (osoby.isEmpty()) return true;
        if (osoby.size() >= maxNaStanowisku) return false;
        return osoby.get(0).equals(druzyna);
    }

    /** Koniec kontroli - kibic zwalnia miejsce przy stanowisku. */
    public void zwolnijStanowisko(int nr, Kibic k) {
        lock.lock();
        try {
            if (nr >= 0 && nr < stanowiska.size()) {
                stanowiska.get(nr).remove(k.getDruzyna());
            }
            stanowiskoDostepne.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Kibic opuszcza stadion - zwalnia jedno z K miejsc. */
    public void opuscStadion() {
        lock.lock();
        try {
            if (obecniNaStadionie > 0) {
                obecniNaStadionie--;
            }
            wejscieNaStadion.signalAll();
            stanowiskoDostepne.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void setPaused(boolean paused) {
        lock.lock();
        try {
            this.paused = paused;
            if (!paused) pauseCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        lock.lock();
        try {
            this.stopped = true;
            pauseCondition.signalAll();
            wejscieNaStadion.signalAll();
            stanowiskoDostepne.signalAll();
        } finally {
            lock.unlock();
        }
    }

    // --- Podglad stanu dla GUI. Kazdy odczyt jest robiony pod zamkiem,
    // a listy sa kopiowane, zeby GUI nie czytalo struktury w trakcie zmiany. ---

    public boolean isPaused() {
        lock.lock();
        try { return paused; } finally { lock.unlock(); }
    }

    public boolean isStopped() {
        lock.lock();
        try { return stopped; } finally { lock.unlock(); }
    }

    public int getObecniNaStadionie() {
        lock.lock();
        try { return obecniNaStadionie; } finally { lock.unlock(); }
    }

    public int getDlugoscKolejki() {
        lock.lock();
        try { return kolejkaOczekujacych.size(); } finally { lock.unlock(); }
    }

    public List<List<String>> getStanowiska() {
        lock.lock();
        try {
            List<List<String>> kopie = new ArrayList<>();
            for (List<String> st : stanowiska) {
                kopie.add(new ArrayList<>(st));
            }
            return kopie;
        } finally {
            lock.unlock();
        }
    }

    public int getK() { return K; }
    public int getLiczbaStanowisk() { return liczbaStanowisk; }
}
