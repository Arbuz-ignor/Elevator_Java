package elevator_sim.models;

public enum Direction {
    DOWN(-1),
    IDLE(0),
    UP(1);

    private final int step;

    Direction(int step) {
        this.step = step;
    }

    public int step() {
        return step;
    }

    @Override
    public String toString() {
        return switch (this) {
            case UP -> "вверх";
            case DOWN -> "вниз";
            case IDLE -> "стоит";
        };
    }
}
