package stadion;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;
import java.util.List;

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
    
    private final List<Kibic> kolejkaOczekujacych = new ArrayList<>();

    private boolean paused = false;
    private boolean stopped = false;
    private final Condition pauseCondition = lock.newCondition();

    public StadionMonitor(int K, int liczbaStanowisk, int maxNaStanowisku, int maxPrzepuszczen) {
        this.K = K;
        this.liczbaStanowisk = liczbaStanowisk;
        this.maxNaStanowisku = maxNaStanowisku;
        this.maxPrzepuszczen = maxPrzepuszczen;
        
        for (int i = 0; i < liczbaStanowisk; i++) {
            stanowiska.add(new ArrayList<>());
        }
    }

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

    public int wejdzDoKolejki(Kibic k) throws InterruptedException {
        lock.lock();
        try {
            kolejkaOczekujacych.add(k);
            
            while (true) {
                checkPauseAndStop();

                // 1. Sprawdź pojemność stadionu
                if (obecniNaStadionie >= K) {
                    wejscieNaStadion.await();
                    continue;
                }

                // 2. Znajdź wolne stanowisko
                int wybraneStanowisko = -1;
                for (int i = 0; i < liczbaStanowisk; i++) {
                    if (mozeWejscNaStanowisko(i, k.getDruzyna())) {
                        wybraneStanowisko = i;
                        break;
                    }
                }

                if (wybraneStanowisko != -1) {
                    // Fairness
                    boolean ktosMaPierwszenstwo = false;
                    for (Kibic przed : kolejkaOczekujacych) {
                        if (przed == k) break;
                        if (przed.getLiczbaPrzepuszczen() >= maxPrzepuszczen) {
                            ktosMaPierwszenstwo = true;
                            break;
                        }
                    }

                    if (!ktosMaPierwszenstwo) {
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
            lock.unlock();
        }
    }

    private boolean mozeWejscNaStanowisko(int nr, String druzyna) {
        if (nr >= stanowiska.size()) return false;
        List<String> osoby = stanowiska.get(nr);
        if (osoby.isEmpty()) return true;
        if (osoby.size() >= maxNaStanowisku) return false;
        return osoby.get(0).equals(druzyna);
    }

    public void zwolnijStanowisko(int nr, Kibic k) {
        lock.lock();
        try {
            if (nr < stanowiska.size()) {
                stanowiska.get(nr).remove(k.getDruzyna());
            }
            stanowiskoDostepne.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void opuscStadion() {
        lock.lock();
        try {
            obecniNaStadionie--;
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
        } finally { lock.unlock(); }
    }

    public void stop() {
        lock.lock();
        try {
            this.stopped = true;
            pauseCondition.signalAll();
            wejscieNaStadion.signalAll();
            stanowiskoDostepne.signalAll();
        } finally { lock.unlock(); }
    }

    public boolean isPaused() { return paused; }
    public boolean isStopped() { return stopped; }
    public int getObecniNaStadionie() { return obecniNaStadionie; }
    public int getK() { return K; }
    public int getLiczbaStanowisk() { return liczbaStanowisk; }
    public List<List<String>> getStanowiska() { return stanowiska; }
    public List<Kibic> getKolejkaOczekujacych() { return kolejkaOczekujacych; }
}
