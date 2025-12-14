package elevator_sim.models;
import elevator_sim.Config;
import elevator_sim.utils.Logger;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public final class Elevator extends Thread {

    public final int id;
    public final int capacity;

    private final ReentrantLock lock = new ReentrantLock(true);
    private ElevatorState state;

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

    private void wakeUp() {
        synchronized (wakeMonitor) {
            wakeMonitor.notifyAll();
        }
    }

    private Set<Integer> allPendingFloors() {
        lock.lock();
        try {
            return new HashSet<>(state.targets);
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

    private void openCloseDoorsStub() {
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

        Logger.logLine("Двери закрываются", "лифт", id, "этаж", floor);
        sleepSec(Config.DOOR_CLOSE_TIME);

        // удаляем текущий этаж из целей если он был целью
        lock.lock();
        try {
            ArrayDeque<Integer> newTargets = new ArrayDeque<>();
            for (int t : state.targets) if (t != floor) newTargets.addLast(t);

            state = new ElevatorState(state.currentFloor, state.direction, ElevatorStatus.IDLE, newTargets);
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
                openCloseDoorsStub();
                continue;
            }

            while (true) {
                curr = getStateSnapshot().currentFloor;
                if (curr == dest) break;
                moveOneFloor();
            }

            openCloseDoorsStub();
        }
    }
}
