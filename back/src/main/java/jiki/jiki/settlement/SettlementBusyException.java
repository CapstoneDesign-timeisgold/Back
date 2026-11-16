package jiki.jiki.settlement;

public class SettlementBusyException extends RuntimeException {
    public SettlementBusyException(Throwable cause) {
        super("Settlement is busy; retry the same promise ID", cause);
    }
}
