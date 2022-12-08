package org.neo4j.ps;

public class RegisterResult {

    public final static RegisterResult SUCCESS = new RegisterResult("Success");

    public final String message;

    public RegisterResult(String message) {
        this.message = message;
    }

}
