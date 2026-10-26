package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    private static final ThreadLocal<Worker> CURRENT = new ThreadLocal<>();

    private final List<Worker> workers = new ArrayList<>();
    final List<SchedLock> locks = new ArrayList<>();
    private final Semaphore schedulerTurn = new Semaphore(0);
    private final StringBuilder trace = new StringBuilder();
    private boolean aborted;

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        workers.add(new Worker((char) ('A' + workers.size()), body));
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        try {
            for (Worker w : workers) {
                w.thread.start();
                schedulerTurn.acquireUninterruptibly();
            }
            mainLoop(strategy);
        } finally {
            stopWorkers();
        }
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        Worker w = current();
        w.touched = true;
        w.log("window");
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        Worker w = current();
        w.beforeOperation(cond);
        w.log("await done");
    }

    static Worker current() {
        return CURRENT.get();
    }

    private void mainLoop(Strategy strategy) {
        int last = -1;
        while (true) {
            List<Integer> enabled = new ArrayList<>();
            boolean anyAlive = false;
            for (int id = 0; id < workers.size(); id++) {
                Worker w = workers.get(id);
                if (w.done) continue;
                anyAlive = true;
                if (w.canProceed()) enabled.add(id);
            }
            if (!anyAlive) return;
            if (enabled.isEmpty()) {
                deadlock = heldLocks();
                return;
            }
            last = strategy.pick(enabled, last);
            Worker next = workers.get(last);
            trace.append(next.letter);
            next.turn.release();
            schedulerTurn.acquireUninterruptibly();
        }
    }

    private String heldLocks() {
        StringJoiner held = new StringJoiner(", ", "held={", "}");
        for (SchedLock lock : locks) {
            if (lock.owner != null) held.add(lock.name + ":" + lock.owner.letter);
        }
        return held.toString();
    }

    private void stopWorkers() {
        aborted = true;
        for (Worker w : workers) {
            if (!w.done) w.turn.release();
        }
        for (Worker w : workers) {
            try {
                w.thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class RunAborted extends RuntimeException {
        RunAborted() { super(null, null, false, false); }
    }

    final class Worker {
        final char letter;
        private final Thread thread;
        private final Semaphore turn = new Semaphore(0);
        private boolean touched = true;
        private boolean done;
        private BooleanSupplier waitingFor;

        private Worker(char letter, Runnable body) {
            this.letter = letter;
            this.thread = new Thread(() -> runBody(body), "worker-" + letter);
        }

        void beforeOperation(BooleanSupplier ready) {
            if (touched || !ready.getAsBoolean()) {
                waitingFor = ready;
                yieldTurn();
                waitingFor = null;
            }
            touched = true;
        }

        void beforeOperation() {
            beforeOperation(() -> true);
        }

        void log(String operation) {
            if (logging) log.add(letter + "  " + operation);
        }

        private boolean canProceed() {
            return waitingFor == null || waitingFor.getAsBoolean();
        }

        private void yieldTurn() {
            schedulerTurn.release();
            turn.acquireUninterruptibly();
            if (aborted) throw new RunAborted();
        }

        private void runBody(Runnable body) {
            CURRENT.set(this);
            try {
                body.run();
            } catch (RunAborted ignored) {
            } finally {
                done = true;
                schedulerTurn.release();
            }
        }
    }
}
