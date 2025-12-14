package elevator_sim;

public final class Config {
    //параметры дома
    public static final int FLOORS = 16;
    public static final int ELEVATORS = 4;
    //временные параметры секунды
    public static final double FLOOR_TRAVEL_TIME = 1.0; // изменено для лучшей демонстрации работы
    public static final double DOOR_OPEN_TIME = 2.0;
    public static final double DOOR_CLOSE_TIME = 2.0;
    // симуляция
    public static final int MAX_PASSENGERS = 25;
    public static final int MAX_ACTIVE_PASSENGERS = 8; // (можно оставить 8)
    // вместимость
    public static final int CAPACITY = 6;

    private Config() {}
}
