package br.com.validadorlote.application;

/** Callback de progresso, neutro de toolkit — marshalling de thread é problema do chamador. */
public interface ProgressListener {
    void onProgress(int processed, int total);
}
