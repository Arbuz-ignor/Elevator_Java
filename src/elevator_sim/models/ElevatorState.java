package elevator_sim.models;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ElevatorState {
    public final int currentFloor;
    public final Direction direction;
    public final ElevatorStatus status;

    // куда лифт должен приехать внутренние кнопки
    public final Deque<Integer> targets;
    public ElevatorState(int currentFloor, Direction direction, ElevatorStatus status, Deque<Integer> targets) {
        this.currentFloor = currentFloor;
        this.direction = direction;
        this.status = status;
        this.targets = targets;
    }

    //чтобы диспетчер/логика не читали живую структуру, пока лифт ее меняет
    public static ElevatorState copyOf(ElevatorState st) {
        return new ElevatorState(
                st.currentFloor,
                st.direction,
                st.status,
                new ArrayDeque<>(st.targets)
        );
    }
}
