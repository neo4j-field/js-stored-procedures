package jsproc.neo4j;

import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.extension.ExtensionFactory;
import org.neo4j.kernel.extension.ExtensionType;
import org.neo4j.kernel.extension.context.ExtensionContext;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.logging.internal.LogService;

@ServiceProvider
public class StartupActionsKernelExtensionFactory
        extends ExtensionFactory <StartupActionsKernelExtensionFactory.Dependencies> {

    public StartupActionsKernelExtensionFactory() {
        super(ExtensionType.DATABASE, "StartupActions");
    }

    @Override
    public Lifecycle newInstance(ExtensionContext extensionContext, Dependencies dependencies) {
        return new StartupActions((GraphDatabaseAPI) dependencies.getDatabaseService(),
                dependencies.getLogService());
    }

    interface Dependencies {
        GraphDatabaseService getDatabaseService();
        LogService getLogService();
    }
}
