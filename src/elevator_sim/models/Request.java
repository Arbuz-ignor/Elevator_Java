package elevator_sim.models;

import java.time.Instant;

public abstract class Request {
    public final String id;
    public final int номер;
    public final Instant timestamp;
    public final String passengerId;
    public final RequestType requestType;

    protected Request(String id, int номер, Instant timestamp, String passengerId, RequestType requestType) {
        this.id = id;
        this.номер = номер;
        this.timestamp = timestamp;
        this.passengerId = passengerId;
        this.requestType = requestType;
    }
}
