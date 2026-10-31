package dev.famjam.radiofm.voice;

import dev.famjam.radiofm.RadioFM;

import java.util.ArrayList;
import java.util.List;

/** RU: задачи до готовности голосового мода ждут, порядок сохраняется | US: tasks wait until the voice mod is up, order is kept */
public abstract class ReadyQueue implements VoiceBackend {

    private final List<Runnable> waiting = new ArrayList<>();
    private boolean ready;

    @Override
    public void runWhenReady(Runnable task) {
        synchronized (waiting) {
            if (!ready) {
                waiting.add(task);
                return;
            }
        }
        run(task);
    }

    protected void markReady() {
        List<Runnable> queued;
        synchronized (waiting) {
            ready = true;
            queued = List.copyOf(waiting);
            waiting.clear();
        }
        queued.forEach(ReadyQueue::run);
    }

    protected void markNotReady() {
        synchronized (waiting) {
            ready = false;
            waiting.clear();
        }
    }

    protected void clearWaiting() {
        synchronized (waiting) {
            waiting.clear();
        }
    }

    @Override
    public void reset() {
        markNotReady();
    }

    private static void run(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            RadioFM.LOGGER.error("Deferred voice chat task failed", e);
        }
    }
}
