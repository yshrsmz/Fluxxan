package com.whisppa.droidfluxlib.impl;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.whisppa.droidfluxlib.Callback;
import com.whisppa.droidfluxlib.Dispatcher;
import com.whisppa.droidfluxlib.Payload;
import com.whisppa.droidfluxlib.Store;
import com.whisppa.droidfluxlib.StoreListener;
import com.whisppa.droidfluxlib.annotation.BindAction;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by user on 5/5/2015.
 */
public abstract class AbstractStoreImpl<State> implements Store<State> {
    private static final String TAG = "DroidFlux:AbstractStore";
    private Dispatcher mDispatcher;
    private boolean mIsResolved = false;
    private Callback mWaitCallback = null;
    private final ConcurrentHashMap<String, Method> mActionMap = new ConcurrentHashMap<>();
    private final List<StoreListener> mListeners;
    private final List<String> mWaitingOnList;

    //ensure you call super else!!!
    public AbstractStoreImpl() {
        mListeners = Collections.synchronizedList(new ArrayList<StoreListener>());
        mWaitingOnList = Collections.synchronizedList(new ArrayList<String>());

        Method[] methods = this.getClass().getMethods();//get only public methods
        for (Method m : methods) {
            if (m.isAnnotationPresent(BindAction.class)) {

                String methodName = m.getName();

                Annotation annotation = m.getAnnotation(BindAction.class);
                BindAction actionAnnotation = (BindAction) annotation;
                String actionName = actionAnnotation.value();

                if(TextUtils.isEmpty(actionName))
                    throw new IllegalArgumentException("BindAction value cannot be empty");

                Class<?>[] parameterTypes = m.getParameterTypes();

                if(parameterTypes.length != 1)
                    throw new InvalidParameterException(String.format("Bound method '%s' must accept a single argument", methodName));//let's just use this exception type for want of a better option

                //if(!parameterTypes[0].getName().equals(Payload.class.getName()))
                 //   throw new InvalidParameterException(String.format("Bound method '%s' must accept a single argument of type 'Payload'", methodName));//let's just use this exception type for want of a better option


                bindAction(actionName, m);
            }
        }
    }

    @Override
    public void setDispatcher(Dispatcher dispatcher) {
        mDispatcher = dispatcher;
    }

    @Override
    public boolean handleAction(Payload payload) throws Exception {
        if(mActionMap.containsKey(payload.Type)) {
            Method method;
            method = mActionMap.get(payload.Type);//this.getClass().getMethod(mActionMap.get(payload.Type));

            //TODO: this should be forced to run on the UI thread
            method.invoke(this, payload.Data);

            return true;
        }

        return false;
    }

    @Override
    public void reset() {
        mIsResolved = false;
        mWaitingOnList.clear();
        mWaitCallback = null;
    }

    @Override
    public List<String> getWaitingOnList() {
        return mWaitingOnList;
    }

    @Override
    public Callback getWaitCallback() {
        return mWaitCallback;
    }

    @Override
    public void setWaitCallback(Callback callback) {
        mWaitCallback = callback;
    }

    @Override
    public void notifyListeners(final Callback callback) {
        synchronized (mListeners) {
            Exception exception = null;
            Iterator<StoreListener> it = mListeners.iterator(); // Must be in synchronized block
            while (it.hasNext()) {
                try {
                    it.next().onChanged();
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected exception during notifyAll", e);
                    exception = e;//let's save this for after. It might just fuck things up terribly
                }
            }

            if(callback != null)
                callback.call();

            if(exception != null)
                throw new RuntimeException(exception);
        }
    }

    @Override
    public void notifyListeners() {
        notifyListeners(null);
    }

    @Override
    public boolean isResolved() {
        return mIsResolved;
    }

    @Override
    public boolean setResolved(boolean resolved) {
        return mIsResolved = resolved;
    }

    @Override
    public void addToWaitingOnList(Collection<String> storeNames) {
        mWaitingOnList.addAll(storeNames);
    }

    private void bindAction(String actionType, Method method) {
        mActionMap.put(actionType, method);
    }

    @Override
    public boolean addListener(StoreListener storeListener) {
        removeListener(storeListener);
        return mListeners.add(storeListener);
    }

    @Override
    public void removeListener(StoreListener storeListener) {
        mListeners.remove(storeListener);
    }

    @Override
    public void waitFor(Class[] stores, Callback callback) throws Exception {
        Set<Class> _stores = new HashSet<>(Arrays.asList(stores));

        mDispatcher.waitFor(this.getClass(), _stores, callback);
    }

    @Override
    public void waitFor(Class store, Callback callback) throws Exception {
        waitFor(new Class[]{store}, callback);
    }

    ;

    public abstract State getState();

}
