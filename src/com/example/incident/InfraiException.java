package com.example.incident;

public final class InfraiException extends RuntimeException {
    private final int status;
    public InfraiException(String message, int status) { super(message); this.status = status; }
    public int status() { return status; }
}
