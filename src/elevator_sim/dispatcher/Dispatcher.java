package elevator_sim.dispatcher;
import elevator_sim.models.*;
import elevator_sim.utils.Logger;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class Dispatcher extends Thread {

    private final BlockingQueue<Object> q = new LinkedBlockingQueue<>();
    private final List<Elevator> elevators;
    private final Map<Integer, Elevator> byId = new HashMap<>();

    public Dispatcher(List<Elevator> elevators) {
        super("Диспетчер");
        setDaemon(true);

        this.elevators = elevators;
        for (Elevator e : elevators) {
            byId.put(e.id, e);
        }
    }

    public void submitRequest(Object request) {
        q.offer(request);
    }

    //чем меньше score тем лучше типа стоимость назначения
    private ElevatorScore score(Elevator elevator, int reqFloor, Direction reqDirection) {
        ElevatorState st = elevator.getStateSnapshot();

        int curr = st.currentFloor;
        int dist = Math.abs(curr - reqFloor);

        double score = (double) dist;
        // небольшой штраф за уже набранные цели и загрузку чтобы не забивать один лифт
        int targets = st.targets.size();
        int load = elevator.getLoad();
        score += targets * 1.5;
        if (elevator.isFull()) score += 100;
        if (st.status == ElevatorStatus.IDLE) {
            //ничего не добавляем, просто dist
            score *= 1.0;
        } else {
            // если лифт уже едет в ту же сторону и этаж по пути это выгоднее
            if (st.direction == Direction.UP) {
                if (reqDirection == Direction.UP && reqFloor >= curr) score *= 0.5;
                else score += 40;
            } else if (st.direction == Direction.DOWN) {
                if (reqDirection == Direction.DOWN && reqFloor <= curr) score *= 0.5;
                else score += 40;
            }
        }

        // чуть приоритетим 1 этаж важнее, например вход
        if (reqFloor == 1) score *= 0.8;

        return new ElevatorScore(elevator.id, score);
    }

    private Elevator chooseElevator(int floor, Direction direction) {
        List<ElevatorScore> scores = new ArrayList<>();
        for (Elevator e : elevators) scores.add(score(e, floor, direction));

        scores.sort(Comparator.comparingDouble(x -> x.score));

        // минимальная стоимость
        return byId.get(scores.get(0).elevatorId);
    }

    private void handleHall(HallRequest req) {
        Logger.logLine("Поступил запрос", "номер", req.номер, "этаж", req.floor, "направление", req.direction);

        try {
            Elevator elevator = chooseElevator(req.floor, req.direction);

            req.assignedElevatorId = elevator.id;
            elevator.registerHallRequest(req);

            // сигнал пассажиру лифт назначен
            req.assignedEvent.countDown();

            Logger.logLine("Лифт назначен", "номер", req.номер, "лифт", elevator.id, "этаж", req.floor);
        } catch (Exception e) {
            Logger.logLine("Ошибка диспетчера", "err", String.valueOf(e));
            req.assignedEvent.countDown(); // чтобы пассажир не завис навсегда
        }
    }

    private void handleCar(CarRequest req) {
        Logger.logLine("Запрос из лифта", "лифт", req.elevatorId, "этаж", req.targetFloor, "номер", req.номер);

        Elevator elevator = byId.get(req.elevatorId);
        if (elevator != null) {
            elevator.addTarget(req.targetFloor);
        }
    }

    @Override
    public void run() {
        Logger.logLine("Запуск диспетчера");

        while (true) {
            try {
                Object req = q.take();

                if (req instanceof HallRequest) handleHall((HallRequest) req);
                else if (req instanceof CarRequest) handleCar((CarRequest) req);

            } catch (Exception e) {
                Logger.logLine("Критическая ошибка", "err", String.valueOf(e));
            }
        }
    }
}
