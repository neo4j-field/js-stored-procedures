package org.neo4j.ps;

import org.neo4j.causalclustering.core.CoreGraphDatabase;
import org.neo4j.causalclustering.core.consensus.roles.Role;
import org.neo4j.causalclustering.readreplica.ReadReplicaGraphDatabase;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.availability.AvailabilityGuard;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.neo4j.logging.Log;
import org.neo4j.logging.internal.LogService;


import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StartupActions extends LifecycleAdapter {
    private final GraphDatabaseAPI graphDatabaseAPI;
    private final LogService logService;
    private final Log log;

    public StartupActions(GraphDatabaseAPI graphDatabaseAPI, LogService logService) {
        this.graphDatabaseAPI = graphDatabaseAPI;
        this.logService = logService;
        this.log = logService.getUserLog(StartupActions.class);
        StoredProcedureEngine.getStoredProcedureEngine(log) ;
    }


    @Override
    public void start() {
        // TODO - We may have to check if we're actually loading multiple singletons like this.
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

            AvailabilityGuard availabilityGuard = db.getDependencyResolver()
                    .resolveDependency(AvailabilityGuard.class);

            while (!availabilityGuard.isAvailable()) {
                log.info("DB Not available yet. Retrying in 5 seconds");
                try {
                    Thread.sleep(5000);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

            StoredProcedureEngine engine = StoredProcedureEngine.getStoredProcedureEngine(null) ;
            engine.loadStoredProcedures(db);
            log.info("LEAVE - Load Stored Proc Nodes from DB into Engine");
        }

        // TODO - This is something we may want to revisit later for a causal cluster
        private boolean isWritable() {
            CoreGraphDatabase cdb;
            Role coreRole;
            try {
                cdb = (CoreGraphDatabase) db;
                coreRole = cdb.getRole();
                return coreRole.toString().equals("LEADER");
            } catch (Exception e) {
                return !(db instanceof ReadReplicaGraphDatabase);
            }
        }
    }
}
