package elevator_sim.simulation;

import elevator_sim.Config;
import elevator_sim.dispatcher.Dispatcher;
import elevator_sim.models.*;
import elevator_sim.utils.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class PassengerGenerator extends Thread {

    private final Dispatcher dispatcher;
    private final Map<Integer, Elevator> elevators;

    private final int floors;
    private final int maxPassengers;

    public PassengerGenerator(Dispatcher dispatcher, List<Elevator> elevators, int floors, int maxPassengers) {
        super("Генератор");
        setDaemon(true);

        this.dispatcher = dispatcher;

        this.elevators = new ConcurrentHashMap<>();
        for (Elevator e : elevators) {
            this.elevators.put(e.id, e);
        }
        this.floors = floors;
        this.maxPassengers = maxPassengers;
    }

    private void journey(String passengerId) {
        String pid = passengerId.substring(0, 4);

        int start = ThreadLocalRandom.current().nextInt(1, floors + 1);
        int target = ThreadLocalRandom.current().nextInt(1, floors + 1);
        while (target == start) target = ThreadLocalRandom.current().nextInt(1, floors + 1);

        Direction direction = (target > start) ? Direction.UP : Direction.DOWN;

        Logger.logLine("Пассажир", "пас", pid, "с", start, "на", target);

        int hallNumber = RequestNumber.next();
        HallRequest hall = new HallRequest(start, direction, passengerId, hallNumber);
        dispatcher.submitRequest(hall);

        //ждём назначения лифта
        try {
            if (!hall.assignedEvent.await(30, TimeUnit.SECONDS)) {
                Logger.logLine("Таймаут назначения", "пас", pid);
                return;
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return;
        }

        Integer assignedId = hall.assignedElevatorId;
        Elevator elev = (assignedId == null) ? null : elevators.get(assignedId);

        if (elev == null) {
            Logger.logLine("Ошибка", "пас", pid, "msg", "Нет назначенного лифта");
            return;
        }

        // ждём посадки лифт должен открыть двери на этаже
        try {
            if (!hall.pickedUp.await(60, TimeUnit.SECONDS)) {
                Logger.logLine("Таймаут посадки", "пас", pid, "лифт", elev.id);
                return;
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return;
        }


        int carNumber = RequestNumber.next();
        CarRequest car = new CarRequest(elev.id, target, passengerId, carNumber);
        dispatcher.submitRequest(car);

        // ждём прибытие
        if (elev.waitForArrival(target, 90_000)) {
            elev.passengerExit(passengerId);
            car.delivered.countDown();

            Logger.logLine("Доставлен", "пас", pid, "лифт", elev.id, "этаж", target);
        } else {
            Logger.logLine("Таймаут доставки", "пас", pid, "лифт", elev.id);
        }
    }

    private static double rand(double a, double b) {
        return a + (b - a) * ThreadLocalRandom.current().nextDouble();
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        Logger.logLine("Запуск генератора");
        sleepMs(1000);

        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < maxPassengers; i++) {
            String pid = UUID.randomUUID().toString();

            Thread t = new Thread(() -> journey(pid), "Пассажир-" + pid.substring(0, 4));
            t.setDaemon(true);
            t.start();

            threads.add(t);

            //ограничиваем количество активных пассажиров
            while (threads.stream().filter(Thread::isAlive).count() >= Config.MAX_ACTIVE_PASSENGERS) {
                sleepMs(200);
            }

            sleepMs((long) (rand(0.2, 0.8) * 1000));
        }

        for (Thread t : threads) {
            try {
                t.join(5000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        Logger.logLine("Симуляция завершена");
    }

    //потокобезопасный счетчик номера заявки 
    private static final class RequestNumber {
        private static final java.util.concurrent.atomic.AtomicInteger COUNTER =
                new java.util.concurrent.atomic.AtomicInteger(0);

        static int next() {
            return COUNTER.incrementAndGet();
        }
    }
}
