package com.example.nanaclu.utils;

public class NoNetworkException extends Exception {
    public NoNetworkException() {
        super("No network connection");
    }

    public NoNetworkException(String message) {
        super(message);
    }
}
