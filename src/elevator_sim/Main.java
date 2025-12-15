package elevator_sim;
import elevator_sim.dispatcher.Dispatcher;
import elevator_sim.models.Elevator;
import elevator_sim.simulation.PassengerGenerator;
import elevator_sim.utils.Logger;
import java.util.ArrayList;
import java.util.List;

public final class Main {

    public static void main(String[] args) {
        Logger.logLine("Начало симуляции", "этажи", Config.FLOORS, "лифты", Config.ELEVATORS);
        List<Elevator> elevators = new ArrayList<>();
        for (int i = 0; i < Config.ELEVATORS; i++) {
            // стартовые этажи чтобы лифты не стояли все в одной точке
            int startFloor = (i == 0) ? 1 : ((i == 1) ? Config.FLOORS : (Config.FLOORS / 2));

            Elevator e = new Elevator(i + 1, startFloor);
            elevators.add(e);
            e.start();
        }
        Dispatcher dispatcher = new Dispatcher(elevators);
        dispatcher.start();
        sleepMs(1000);
        PassengerGenerator gen = new PassengerGenerator(dispatcher, elevators, Config.FLOORS, Config.MAX_PASSENGERS);
        gen.start();

        try {
            while (gen.isAlive()) {
                sleepMs(500);
            }
        } finally {
            Logger.logLine("Конец симуляции");
        }
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
