package jsproc.neo4j;

public class RegisterResult {

    public final static RegisterResult SUCCESS = new RegisterResult("Success");
    public final static RegisterResult NO_CHANGE = new RegisterResult("No Change");

    public final String message;

    public RegisterResult(String message) {
        this.message = message;
    }

}
