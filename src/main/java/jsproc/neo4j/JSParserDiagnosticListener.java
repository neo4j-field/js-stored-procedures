package jsproc.neo4j;

import org.openjdk.nashorn.api.tree.Diagnostic;
import org.openjdk.nashorn.api.tree.DiagnosticListener;

public class JSParserDiagnosticListener implements DiagnosticListener {

    StringBuffer message = new StringBuffer();
    boolean hasError = false;

    @Override
    public void report(Diagnostic diagnostic) {
        if( diagnostic.getKind().name().equals("ERROR")) {
            hasError = true;
        }
        message.append(diagnostic.getMessage());
    }

    public String getMessage() {
        return message.toString();
    }

    public boolean hasError() {
        return hasError;
    }

}
