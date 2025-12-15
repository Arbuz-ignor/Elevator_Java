package elevator_sim.models;
import elevator_sim.Config;
import elevator_sim.utils.Logger;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
public final class Elevator extends Thread {

    public final int id;
    public final int capacity;

    private final ReentrantLock lock = new ReentrantLock(true);
    private ElevatorState state;
    //кто сейчас внутри лифта
    private final Set<String> passengers = new HashSet<>();
    // сигнал прибытия на этаж
    // пассажиры ждут через waitForArrival()
    private final Map<Integer, CompletableFuture<Void>> arrivalFutures = new ConcurrentHashMap<>();
    // этаж список внешних заявок люди ждут лифт на этаже
    private final Map<Integer, List<HallRequest>> hallRequestsByFloor = new HashMap<>();
    private final Object wakeMonitor = new Object();

    public Elevator(int elevatorId, int startFloor) {
        super("Лифт-" + elevatorId);
        setDaemon(true);

        id = elevatorId;
        capacity = Config.CAPACITY;

        state = new ElevatorState(startFloor, Direction.IDLE, ElevatorStatus.IDLE, new ArrayDeque<>());
    }

    public ElevatorState getStateSnapshot() {
        lock.lock();
        try {
            return ElevatorState.copyOf(state);
        } finally {
            lock.unlock();
        }
    }

    public void addTarget(int floor) {
        lock.lock();
        try {
            state.targets.addLast(floor);
        } finally {
            lock.unlock();
        }
        wakeUp();
    }

    private CompletableFuture<Void> getArrivalFuture(int floor) {
        return arrivalFutures.computeIfAbsent(floor, f -> new CompletableFuture<>());
    }

    public boolean waitForArrival(int floor, long timeoutMs) {
        try {
            getArrivalFuture(floor).get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
    public void registerHallRequest(HallRequest req) {
        lock.lock();
        try {
            hallRequestsByFloor.computeIfAbsent(req.floor, f -> new ArrayList<>()).add(req);
        } finally {
            lock.unlock();
        }
        wakeUp();
    }

    public void passengerExit(String passengerId) {
        lock.lock();
        try {
            passengers.remove(passengerId);
        } finally {
            lock.unlock();
        }
    }

    private void wakeUp() {
        synchronized (wakeMonitor) {
            wakeMonitor.notifyAll();
        }
    }

    private Set<Integer> allPendingFloors() {
        lock.lock();
        try {
            Set<Integer> floors = new HashSet<>(state.targets);
            floors.addAll(hallRequestsByFloor.keySet());
            return floors;
        } finally {
            lock.unlock();
        }
    }


    // смотрим вперед по направлению, если там нет целей - разворачиваемся
    private Integer nextDestinationLook() {
        int curr;
        Direction dir;

        lock.lock();
        try {
            curr = state.currentFloor;
            dir = state.direction;
        } finally {
            lock.unlock();
        }

        List<Integer> pending = new ArrayList<>(allPendingFloors());
        if (pending.isEmpty()) return null;

        Collections.sort(pending);

        List<Integer> higher = new ArrayList<>();
        List<Integer> lower  = new ArrayList<>();

        for (int f : pending) {
            if (f > curr) higher.add(f);
            else if (f < curr) lower.add(f);
        }

        if (dir == Direction.UP) {
            if (!higher.isEmpty()) return Collections.min(higher);
            if (!lower.isEmpty())  return Collections.max(lower);
            return curr;
        }

        if (dir == Direction.DOWN) {
            if (!lower.isEmpty())  return Collections.max(lower);
            if (!higher.isEmpty()) return Collections.min(higher);
            return curr;
        }

        //просто ближайший по расстоянию
        int best = pending.get(0);
        int bestDist = Math.abs(best - curr);
        for (int f : pending) {
            int d = Math.abs(f - curr);
            if (d < bestDist) {
                bestDist = d;
                best = f;
            }
        }
        return best;
    }

    private void setDirectionTowards(int dest) {
        lock.lock();
        try {
            if (dest > state.currentFloor) {
                state = new ElevatorState(state.currentFloor, Direction.UP, state.status, state.targets);
            } else if (dest < state.currentFloor) {
                state = new ElevatorState(state.currentFloor, Direction.DOWN, state.status, state.targets);
            } else {
                state = new ElevatorState(state.currentFloor, Direction.IDLE, state.status, state.targets);
            }
        } finally {
            lock.unlock();
        }
    }

    private void moveOneFloor() {
        int curr;

        lock.lock();
        try {
            state = new ElevatorState(state.currentFloor, state.direction, ElevatorStatus.MOVING, state.targets);

            int step = state.direction.step();
            if (step == 0) return;

            int newFloor = state.currentFloor + step;
            state = new ElevatorState(newFloor, state.direction, state.status, state.targets);

            curr = newFloor;
        } finally {
            lock.unlock();
        }

        sleepSec(Config.FLOOR_TRAVEL_TIME);
        Logger.logLine("Местонахождение", "лифт", id, "этаж", curr);
    }

    private boolean shouldStopHere(int floor) {
        lock.lock();
        try {
            if (state.targets.contains(floor)) return true;
            return hallRequestsByFloor.containsKey(floor);
        } finally {
            lock.unlock();
        }
    }


    private void openCloseDoorsAndService() {
        int floor;

        lock.lock();
        try {
            state = new ElevatorState(state.currentFloor, state.direction, ElevatorStatus.DOORS_OPEN, state.targets);
            floor = state.currentFloor;
        } finally {
            lock.unlock();
        }

        Logger.logLine("Двери открыты", "лифт", id, "этаж", floor);
        sleepSec(Config.DOOR_OPEN_TIME);

        //подбор ожидающих пассажиров
        List<HallRequest> picked;

        lock.lock();
        try {
            List<HallRequest> waiting = hallRequestsByFloor.remove(floor);
            if (waiting == null) waiting = new ArrayList<>();

            int free = capacity - passengers.size();
            int canTake = Math.max(0, free);

            picked = new ArrayList<>();
            List<HallRequest> notPicked = new ArrayList<>();

            for (int i = 0; i < waiting.size(); i++) {
                if (i < canTake) picked.add(waiting.get(i));
                else notPicked.add(waiting.get(i));
            }

            if (!notPicked.isEmpty()) {
                hallRequestsByFloor.computeIfAbsent(floor, f -> new ArrayList<>()).addAll(notPicked);
            }
        } finally {
            lock.unlock();
        }

        for (HallRequest req : picked) {
            lock.lock();
            try {
                passengers.add(req.passengerId);
            } finally {
                lock.unlock();
            }
            req.pickedUp.countDown();
            Logger.logLine("Посадка", "лифт", id, "этаж", floor, "пас", req.passengerId.substring(0, 4));
        }

        //удаляем текущий этаж из целей если он там был
        lock.lock();
        try {
            ArrayDeque<Integer> newTargets = new ArrayDeque<>();
            for (int t : state.targets) {
                if (t != floor) newTargets.addLast(t);
            }
            state = new ElevatorState(state.currentFloor, state.direction, state.status, newTargets);
        } finally {
            lock.unlock();
        }

        CompletableFuture<Void> f = getArrivalFuture(floor);
        f.complete(null);
        arrivalFutures.put(floor, new CompletableFuture<>());

        Logger.logLine("Двери закрываются", "лифт", id, "этаж", floor);
        sleepSec(Config.DOOR_CLOSE_TIME);

        lock.lock();
        try {
            state = new ElevatorState(state.currentFloor, state.direction, ElevatorStatus.IDLE, state.targets);
        } finally {
            lock.unlock();
        }
    }

    private static void sleepSec(double sec) {
        try {
            Thread.sleep((long) (sec * 1000));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
    @Override
    public void run() {
        Logger.logLine("Лифт запущен", "лифт", id, "этаж", getStateSnapshot().currentFloor);

        while (true) {
            Integer dest = nextDestinationLook();

            if (dest == null) {
                lock.lock();
                try {
                    state = new ElevatorState(state.currentFloor, Direction.IDLE, ElevatorStatus.IDLE, state.targets);
                } finally {
                    lock.unlock();
                }

                synchronized (wakeMonitor) {
                    try {
                        wakeMonitor.wait(500);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                continue;
            }

            setDirectionTowards(dest);

            int curr = getStateSnapshot().currentFloor;
            if (curr == dest) {
                if (shouldStopHere(curr)) {
                    openCloseDoorsAndService();
                } else {
                    lock.lock();
                    try {
                        state = new ElevatorState(state.currentFloor, Direction.IDLE, state.status, state.targets);
                    } finally {
                        lock.unlock();
                    }
                }
                continue;
            }

            while (true) {
                curr = getStateSnapshot().currentFloor;
                if (curr == dest) break;

                moveOneFloor();
                curr = getStateSnapshot().currentFloor;

                if (shouldStopHere(curr)) {
                    openCloseDoorsAndService();

                    Integer newDest = nextDestinationLook();
                    if (newDest == null) break;

                    dest = newDest;
                    setDirectionTowards(dest);
                }
            }

        }
    }
}
