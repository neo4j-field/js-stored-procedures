package jsproc.neo4j;

import javax.script.ScriptEngine;
import java.util.Date;

public class ScriptDetails {

    private ScriptEngine engine;
    private String publicName;
    private String name;

    public ScriptEngine getEngine() {
        return engine;
    }

    public void setEngine(ScriptEngine engine) {
        this.engine = engine;
    }

    public String getPublicName() {
        return publicName;
    }

    public void setPublicName(String publicName) {
        this.publicName = publicName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
