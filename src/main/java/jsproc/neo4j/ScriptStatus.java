package jsproc.neo4j;

import java.time.ZonedDateTime;

public class ScriptStatus {
    public String publicName ;
    public String functionName ;
    public ZonedDateTime lastReadTime ;
    public boolean loaded = false ;
    public boolean disabled = false ;
}
