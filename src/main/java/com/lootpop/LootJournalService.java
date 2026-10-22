package com.lootpop;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/** One ordered IO lane: profile switches, observations and writes can never overtake one another. */
final class LootJournalService implements AutoCloseable
{
    private final LootJournalStore store;
    private final Runnable changed;
    private final IntSupplier limit;
    private final IntSupplier days;
    private final IntSupplier trophyLimit;
    private final ScheduledExecutorService scheduler;
    private final Executor io;
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    private volatile State current;
    private volatile boolean closed;

    LootJournalService(LootJournalStore store, Runnable changed, IntSupplier limit, IntSupplier days,
        ScheduledExecutorService scheduler)
    {
        this(store, changed, limit, days, scheduler, () -> 500);
    }

    LootJournalService(LootJournalStore store, Runnable changed, IntSupplier limit, IntSupplier days,
        ScheduledExecutorService scheduler, IntSupplier trophyLimit)
    {
        this.store = store; this.changed = changed; this.limit = limit; this.days = days;
        this.scheduler = scheduler;
        this.trophyLimit = trophyLimit;
        this.io = task ->
        {
            synchronized (LootJournalService.this)
            {
                tail = tail.handle((value, failure) -> null).thenRunAsync(task, scheduler);
            }
        };
    }

    synchronized void switchProfile(String profile)
    {
        if (closed) { return; }
        State previous = current;
        if (previous != null && previous.profile.equals(profile)) { return; }
        State next = profile == null || profile.isEmpty() ? null : new State(profile);
        current = next;
        io.execute(() ->
        {
            if (previous != null) { flush(previous); }
            if (next != null)
            {
                try
                {
                    next.journal = store.load(next.profile);
                    int before = next.journal.snapshot().size();
                    next.journal.prune(limit.getAsInt(), days.getAsInt(), System.currentTimeMillis());
                    int trophyCount = next.journal.trophySnapshot().size();
                    next.journal.trimTrophies(trophyLimit.getAsInt());
                    if (trophyCount != next.journal.trophySnapshot().size()) { dirty(next); }
                    if (next.journal.snapshot().size() != before) { dirty(next); }
                }
                catch (IOException failure)
                {
                    next.readOnly = true;
                    next.error = "Saved file could not be read. It was left untouched.";
                }
                next.loading = false;
            }
            changed.run();
        });
    }

    synchronized void record(LootHistoryEntry entry, LootNotification popup, boolean celebrate, long minimum)
    {
        State state = current;
        if (closed || state == null) { return; }
        io.execute(() ->
        {
            if (state.readOnly) { return; }
            long previous = state.journal.record(entry, limit.getAsInt(), days.getAsInt(), System.currentTimeMillis(), trophyLimit.getAsInt());
            if (celebrate && previous > 0 && entry.getTotalValue() >= minimum)
            {
                popup.markPersonalBest(previous);
            }
            dirty(state);
            changed.run();
        });
    }

    synchronized void confirmCollection(String source, long time)
    {
        confirmCollection(source, time, -1);
    }

    synchronized void confirmCollection(String source, long time, int itemId)
    {
        State state = current;
        if (closed || state == null) { return; }
        io.execute(() ->
        {
            if (state.readOnly) { return; }
            java.util.Map<String, LootHistoryEntry> candidates = new java.util.LinkedHashMap<>();
            for (LootHistoryEntry entry : state.journal.snapshot()) { candidates.put(entry.getId(), entry); }
            for (LootHistoryEntry entry : state.journal.trophySnapshot()) { candidates.put(entry.getId(), entry); }
            for (LootHistoryEntry entry : candidates.values())
            {
                if (entry.getTimestamp() == time && entry.getSourceName().equals(source))
                {
                    entry.setCollectionLogUnlock(true);
                    for (LootHistoryItem item : entry.getItems())
                    { if (item.getItemId() == itemId) { item.setCollectionLogItem(true); } }
                    state.journal.keepTrophy(entry, trophyLimit.getAsInt());
                    dirty(state);
                    changed.run();
                    break;
                }
            }
        });
    }

    synchronized void clear()
    {
        State state = current;
        if (closed || state == null) { return; }
        io.execute(() ->
        {
            if (state.readOnly) { return; }
            state.journal = new LootJournal();
            state.dirty = true;
            flush(state);
            changed.run();
        });
    }

    List<LootHistoryEntry> entries()
    {
        State state = current;
        return state == null ? Collections.emptyList() : state.journal.snapshot();
    }

    String status()
    {
        State state = current;
        if (state == null) { return "Log in to open this character's journal"; }
        if (state.loading) { return "Opening local journal..."; }
        return state.error == null ? "Stored on this device / this character only" : state.error;
    }

    String notice()
    {
        State state = current;
        if (state == null) { return "Log in to open this character's journal"; }
        if (state.loading) { return "Opening local journal..."; }
        return state.error == null ? "" : state.error;
    }

    List<LootHistoryEntry> trophies()
    {
        State state = current;
        return state == null ? Collections.emptyList() : state.journal.trophySnapshot();
    }

    boolean canClear() { State state = current; return state != null && !state.loading && !state.readOnly; }

    private void dirty(State state)
    {
        state.dirty = true;
        if (state.save != null) { state.save.cancel(false); }
        if (closed) { flush(state); }
        else { state.save = scheduler.schedule(() -> io.execute(() -> { flush(state); changed.run(); }), 700, TimeUnit.MILLISECONDS); }
    }

    private void flush(State state)
    {
        if (state.save != null) { state.save.cancel(false); state.save = null; }
        if (!state.dirty || state.readOnly) { return; }
        try { store.save(state.profile, state.journal); state.dirty = false; state.error = null; }
        catch (IOException failure) { state.error = "Journal save failed. Check local folder permissions."; }
    }

    @Override
    public synchronized void close()
    {
        if (closed) { return; }
        closed = true;
        State state = current;
        io.execute(() -> { if (state != null) { flush(state); } });
    }

    private static final class State
    {
        final String profile;
        volatile LootJournal journal = new LootJournal();
        volatile boolean loading = true;
        volatile boolean readOnly;
        volatile String error;
        boolean dirty;
        ScheduledFuture<?> save;
        State(String profile) { this.profile = profile; }
    }
}
