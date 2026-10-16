package com.mkt.task.application;

import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.task.command.MutexGroupSaveCommand;
import com.mkt.task.response.MutexGroupResponse;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskMutexGroupAppService {

    private final MutexReads mutexReads;
    private final MutexWrites mutexWrites;

    @Autowired
    public TaskMutexGroupAppService(
            TaskMutexGroupStore store,
            TaskDefinitionStore definitions,
            Clock clock,
            MutexReads mutexReads,
            MutexWrites mutexWrites) {
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.mutexReads = mutexReads != null ? mutexReads : new MutexReads(store);
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.mutexWrites = mutexWrites != null ? mutexWrites : new MutexWrites(store, definitions, clock);
    }

    public TaskMutexGroupAppService(TaskMutexGroupStore store, TaskDefinitionStore definitions, Clock clock) {
        this(store, definitions, clock, null, null);
    }

    public MutexGroupResponse get(long id) {
        return mutexReads.get(id);
    }

    public PageData<MutexGroupResponse> page(PageQuery page) {
        return mutexReads.page(page);
    }

    @Transactional
    public MutexGroupResponse create(MutexGroupSaveCommand command) {
        return mutexWrites.create(command);
    }

    @Transactional
    public MutexGroupResponse update(long id, MutexGroupSaveCommand command) {
        return mutexWrites.update(id, command);
    }

    @Transactional
    public void delete(long id) {
        mutexWrites.delete(id);
    }
}
