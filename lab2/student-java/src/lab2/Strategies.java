package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.get(0);
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int position;

        public Replay(String trace) { this.trace = trace; }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() { position = 0; }

        public int pick(List<Integer> enabled, int last) {
            if (position >= trace.length()) {
                throw new IllegalStateException("trace " + trace + " ended before the run finished");
            }
            int next = trace.charAt(position) - 'A';
            if (!enabled.contains(next)) {
                throw new IllegalStateException("trace " + trace + ": thread " + trace.charAt(position)
                        + " is not enabled at decision " + (position + 1));
            }
            position++;
            return next;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final Random rnd;

        public RandomPick(long seed) { this.rnd = new Random(seed); }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private final int bound;
        private final List<Branch> path = new ArrayList<>();
        private int position;
        private int preemptions;

        public Dfs(int bound) { this.bound = bound; }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            position = 0;
            preemptions = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            boolean canContinue = enabled.contains(last);
            if (position == path.size()) {
                List<Integer> options = canContinue && preemptions >= bound ? List.of(last) : List.copyOf(enabled);
                path.add(new Branch(options));
            }
            int next = path.get(position++).choice();
            if (canContinue && next != last) preemptions++;
            return next;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            path.subList(position, path.size()).clear();
            while (!path.isEmpty() && path.get(path.size() - 1).exhausted()) {
                path.remove(path.size() - 1);
            }
            if (path.isEmpty()) return false;
            path.get(path.size() - 1).index++;
            return true;
        }

        private static final class Branch {
            final List<Integer> options;
            int index;

            Branch(List<Integer> options) { this.options = options; }

            int choice() { return options.get(index); }

            boolean exhausted() { return index + 1 >= options.size(); }
        }
    }
}
