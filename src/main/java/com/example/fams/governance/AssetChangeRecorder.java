package com.example.fams.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores asset change log rows once the transaction that changed the asset has committed,
 * in a separate transaction, so a rolled-back change leaves no log and a logging failure
 * can never undo a real change. Entity listeners can't be injected reliably, so this is
 * reached through a static entry point.
 */
@Component
public class AssetChangeRecorder {

    private static final Logger log = LoggerFactory.getLogger(AssetChangeRecorder.class);
    private static volatile AssetChangeRecorder instance;
    private static final ThreadLocal<List<AssetChangeLog>> PENDING = new ThreadLocal<>();

    private final AssetChangeLogRepository repository;
    private final PlatformTransactionManager transactionManager;

    public AssetChangeRecorder(AssetChangeLogRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionManager = transactionManager;
        instance = this;
    }

    /** Queues entries to be stored after the current transaction commits (or now, if there is none). */
    public static void record(List<AssetChangeLog> entries) {
        AssetChangeRecorder recorder = instance;
        if (recorder == null || entries == null || entries.isEmpty()) {
            return;
        }
        try {
            recorder.accept(entries);
        } catch (Exception e) {
            log.warn("Could not queue asset change log: {}", e.getMessage());
        }
    }

    private void accept(List<AssetChangeLog> entries) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            write(new ArrayList<>(entries));
            return;
        }
        List<AssetChangeLog> pending = PENDING.get();
        if (pending == null) {
            List<AssetChangeLog> created = new ArrayList<>();
            PENDING.set(created);
            pending = created;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write(new ArrayList<>(created));
                }

                @Override
                public void afterCompletion(int status) {
                    PENDING.remove();
                }
            });
        }
        pending.addAll(entries);
    }

    private void write(List<AssetChangeLog> entries) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> repository.saveAll(entries));
        } catch (Exception e) {
            log.warn("Could not store asset change log: {}", e.getMessage());
        }
    }
}
