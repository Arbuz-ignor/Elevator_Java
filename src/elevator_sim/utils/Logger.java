package elevator_sim.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.locks.ReentrantLock;

public final class Logger {

    //чтобы время старта было одно на все потоки
    private static volatile Logger instance;
    private static final Object CLASS_LOCK = new Object();

    private final Instant start;
    private final ReentrantLock printLock = new ReentrantLock(true); // fair, чтобы строки не мешались

    private Logger() {
        start = Instant.now();
    }

    public static Logger get() {
        Logger local = instance;
        if (local == null) {
            synchronized (CLASS_LOCK) {
                local = instance;
                if (local == null) {
                    local = new Logger();
                    instance = local;
                }
            }
        }
        return local;
    }

    public void log(String event, Object... kv) {
        // время с начала симуляции в секундах
        double dt = Duration.between(start, Instant.now()).toNanos() / 1_000_000_000.0;
        String ts = String.format("%8.3fs", dt);

        String tName = Thread.currentThread().getName();

        // мапа для красивых подписей полей
        Map<String, String> LABELS = new LinkedHashMap<>();
        LABELS.put("номер", "Номер");
        LABELS.put("этаж", "Этаж");
        LABELS.put("лифт", "Лифт");
        LABELS.put("напр", "Направление");
        LABELS.put("цель", "Цель");
        LABELS.put("пас", "Пассажир");
        LABELS.put("с", "С этажа");
        LABELS.put("на", "На этаж");
        LABELS.put("ошибка", "Ошибка");
        LABELS.put("err", "Ошибка");
        LABELS.put("msg", "Сообщение");

        // перевод направления на русский
        Map<String, String> DIR_RU = new LinkedHashMap<>();
        DIR_RU.put("UP", "вверх");
        DIR_RU.put("DOWN", "вниз");
        DIR_RU.put("IDLE", "—");

        String detailsStr = "-";
        if (kv != null && kv.length > 0) {

            //  чтобы красиво: Лифт: 2  Этаж: 5
            StringJoiner sj = new StringJoiner(" | ");

            for (int i = 0; i + 1 < kv.length; i += 2) {
                String k = String.valueOf(kv[i]);
                Object vObj = kv[i + 1];

                String title = LABELS.getOrDefault(k, k);

                // важный момент: направление хотим русское
                if ("напр".equals(k) || "direction".equals(k)) {
                    String vv = String.valueOf(vObj);
                    vv = DIR_RU.getOrDefault(vv, vv);
                    sj.add(title + ": " + vv);
                } else {
                    sj.add(title + ": " + vObj);
                }
            }

            detailsStr = (sj.length() == 0) ? "-" : sj.toString();
        }

        // фиксируем ширины, чтобы логи были ровные
        String eventPadded = String.format("%-22s", event);
        String tNamePadded = String.format("%-14s", tName);

        String line = ts + " | " + tNamePadded + " | " + eventPadded + " | " + detailsStr;

        // без lock строки из разных потоков будут перемешиваться
        printLock.lock();
        try {
            System.out.println(line);
        } finally {
            printLock.unlock();
        }
    }

    public static void logLine(String event, Object... kv) {
        Logger.get().log(event, kv);
    }
}
