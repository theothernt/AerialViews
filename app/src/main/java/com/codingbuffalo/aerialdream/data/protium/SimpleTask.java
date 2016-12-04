package com.codingbuffalo.aerialdream.data.protium;

public abstract class SimpleTask extends Task<Void> {
    @Override
    public final Void call() {
        try {
            onExecute();

            if (!Thread.currentThread().isInterrupted()) {
                onComplete();
            }
        } catch (Exception e) {
            onError(e);
        }
        
        return null;
    }

    public abstract void onExecute() throws Exception;
    
    public void onComplete() {
    }
}
