package it.sapienza.netlab.airmon.listeners;

public class Listeners {

    public interface OnJobDoneListener {
        void OnJobDone();
    }

    public interface OnConnectionLost {
        void OnConnectionLost();
    }
}
