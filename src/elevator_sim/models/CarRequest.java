package elevator_sim.models;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public final class CarRequest extends Request {
    public final int elevatorId;
    public final int targetFloor;
    public final CountDownLatch delivered = new CountDownLatch(1);
    public CarRequest(int elevatorId, int targetFloor, String passengerId, int number) {
        super(UUID.randomUUID().toString().substring(0, 8), number, Instant.now(), passengerId, RequestType.CAR);
        this.elevatorId = elevatorId;
        this.targetFloor = targetFloor;
    }
}
