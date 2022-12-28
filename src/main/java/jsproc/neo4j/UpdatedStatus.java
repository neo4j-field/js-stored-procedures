package jsproc.neo4j;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class UpdatedStatus {

    private static Map<String, UpdatedStatus> statusMap = new HashMap<>() ;;

    private Date lastUpdated ;
    private Date lastRead ;
    private long interval = 3600 ;
    private long nodeId = -1 ;

    public static synchronized UpdatedStatus getInstance(GraphDatabaseService db) {
        String name = db.databaseName() ;

        UpdatedStatus instance = statusMap.get(name);

        if( instance == null ) {
            instance = new UpdatedStatus() ;
            statusMap.put(name, instance) ;
        }
        instance.updateStatus(db);
        return instance ;
    }

    private void updateStatus(GraphDatabaseService db) {
        if( lastUpdated == null || lastRead == null || ( new Date().getTime() - lastRead.getTime() ) > interval ) {
            refreshUpdateStatus(db) ;
        }
    }

    private UpdatedStatus() {
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Date getLastRead() {
        return lastRead;
    }

    public void setLastRead(Date lastRead) {
        this.lastRead = lastRead;
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    private void refreshUpdateStatus(GraphDatabaseService db) {
        String dbName = db.databaseName();
        Transaction tx = db.beginTx() ;
        Node node = null ;

        if( nodeId == -1 ) {
            ResourceIterator<Node> nodes = tx.findNodes(StoredProcedureEngine.JS_ProcedureLock) ;
            if( nodes.hasNext() ) {
                node = nodes.next();
            } else {
                node = tx.createNode(StoredProcedureEngine.JS_ProcedureLock) ;
                node.setProperty(StoredProcedureEngine.LastUpdatedime, new Date());
            }
            nodeId = node.getId() ;
        } else {
            node = tx.getNodeById(nodeId) ;
        }
        Object o = node.getProperty(StoredProcedureEngine.LoadInterval, null ) ;
        if( o != null ) {
            interval = ((Integer)o).intValue() ;
        } else {
            interval = 3600; // Refresh every hour
        }
        o = node.getProperty(StoredProcedureEngine.LastUpdatedime, null ) ;
        lastUpdated = (Date)o;
        lastRead = new Date();

        tx.commit();
    }

    public void updateTimestamp(Transaction tx, Date date) {
        Node node = tx.getNodeById(nodeId) ;
        node.setProperty(StoredProcedureEngine.LastUpdatedime, new Date());
    }
}
