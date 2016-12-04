package com.codingbuffalo.aerialdream.data.protium;

import android.support.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class Interactor {
    private ExecutorService mService;
    
    public Interactor(@NonNull ExecutorService service) {
        mService = service;
    }
    
    @NonNull
    protected final <T> Future<T> execute(@NonNull Task<T> task) {
        return mService.submit(task);
    }
}
