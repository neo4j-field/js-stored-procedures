package jsproc.neo4j;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.kernel.availability.AvailabilityGuard;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.neo4j.logging.Log;
import org.neo4j.logging.internal.LogService;

public class StartupActions extends LifecycleAdapter {

    private final GraphDatabaseAPI graphDatabaseAPI;
    private final LogService logService;
    private final Log log;

    public StartupActions(GraphDatabaseAPI graphDatabaseAPI, LogService logService) {
        this.graphDatabaseAPI = graphDatabaseAPI;
        this.logService = logService;
        this.log = logService.getUserLog(StartupActions.class);
        StoredProcedureEngine.getStoredProcedureEngine(log);
    }

    @Override
    public void start() {
        if (!this.graphDatabaseAPI.databaseName().equals(GraphDatabaseSettings.SYSTEM_DATABASE_NAME)) {
            log.debug("Startup Extension Loaded. Set up Proc Loader");
            ProcLoader procLoader = new ProcLoader(graphDatabaseAPI);
            Thread thread = new Thread(procLoader);
            thread.setDaemon(true);
            thread.start();
        }

    }

    class ProcLoader implements Runnable {

        GraphDatabaseAPI db;

        public ProcLoader(GraphDatabaseAPI dbApi) {
            this.db = dbApi;
        }

        @Override
        public void run() {
            log.info("ENTER - Load Stored Proc Nodes from DB into Engine");
            AvailabilityGuard availabilityGuard = db.getDependencyResolver().resolveDependency(AvailabilityGuard.class);
            while (!availabilityGuard.isAvailable()) {
                log.info("DB Not available yet. Retrying in 5 seconds");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    log.warn("Thread was interrupted", e);
                }
            }
            StoredProcedureEngine.getStoredProcedureEngine(null).loadStoredProcedures(db);
            log.info("LEAVE - Load Stored Proc Nodes from DB into Engine");
        }

    }
}
