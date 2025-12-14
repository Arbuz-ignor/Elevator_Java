package elevator_sim.models;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public final class HallRequest extends Request {
    public final int floor;
    public final Direction direction;
    public volatile Integer assignedElevatorId = null;

    public final CountDownLatch assignedEvent = new CountDownLatch(1); // лифт назначен
    public final CountDownLatch pickedUp = new CountDownLatch(1);      // пассажир сел

    public HallRequest(int floor, Direction direction, String passengerId, int number) {
        super(UUID.randomUUID().toString().substring(0, 8), number, Instant.now(), passengerId, RequestType.HALL);
        this.floor = floor;
        this.direction = direction;
    }
}
