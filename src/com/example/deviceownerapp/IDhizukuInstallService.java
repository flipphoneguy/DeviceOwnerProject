package com.example.deviceownerapp;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

/**
 * Manual AIDL interface for DhizukuInstallService.
 */
public interface IDhizukuInstallService extends IInterface {

    int createInstallSession() throws RemoteException;
    void writeToSession(int sessionId, String name, ParcelFileDescriptor pfd) throws RemoteException;
    void commitSession(int sessionId) throws RemoteException;
    void abandonSession(int sessionId) throws RemoteException;
    void destroy() throws RemoteException;

    abstract class Stub extends Binder implements IDhizukuInstallService {
        private static final String DESCRIPTOR = "com.example.deviceownerapp.IDhizukuInstallService";

        static final int TRANSACTION_createInstallSession = IBinder.FIRST_CALL_TRANSACTION;
        static final int TRANSACTION_writeToSession = IBinder.FIRST_CALL_TRANSACTION + 1;
        static final int TRANSACTION_commitSession = IBinder.FIRST_CALL_TRANSACTION + 2;
        static final int TRANSACTION_abandonSession = IBinder.FIRST_CALL_TRANSACTION + 3;
        static final int TRANSACTION_destroy = IBinder.FIRST_CALL_TRANSACTION + 4;

        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        public static IDhizukuInstallService asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof IDhizukuInstallService) {
                return (IDhizukuInstallService) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case TRANSACTION_createInstallSession: {
                    data.enforceInterface(DESCRIPTOR);
                    int result = this.createInstallSession();
                    reply.writeNoException();
                    reply.writeInt(result);
                    return true;
                }
                case TRANSACTION_writeToSession: {
                    data.enforceInterface(DESCRIPTOR);
                    int sessionId = data.readInt();
                    String name = data.readString();
                    ParcelFileDescriptor pfd = null;
                    if (data.readInt() != 0) {
                        pfd = ParcelFileDescriptor.CREATOR.createFromParcel(data);
                    }
                    this.writeToSession(sessionId, name, pfd);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_commitSession: {
                    data.enforceInterface(DESCRIPTOR);
                    int sessionId = data.readInt();
                    this.commitSession(sessionId);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_abandonSession: {
                    data.enforceInterface(DESCRIPTOR);
                    int sessionId = data.readInt();
                    this.abandonSession(sessionId);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_destroy: {
                    data.enforceInterface(DESCRIPTOR);
                    this.destroy();
                    reply.writeNoException();
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements IDhizukuInstallService {
            private IBinder remote;

            Proxy(IBinder remote) {
                this.remote = remote;
            }

            @Override
            public IBinder asBinder() {
                return remote;
            }

            @Override
            public int createInstallSession() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    remote.transact(TRANSACTION_createInstallSession, data, reply, 0);
                    reply.readException();
                    return reply.readInt();
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            }

            @Override
            public void writeToSession(int sessionId, String name, ParcelFileDescriptor pfd) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(sessionId);
                    data.writeString(name);
                    if (pfd != null) {
                        data.writeInt(1);
                        pfd.writeToParcel(data, 0);
                    } else {
                        data.writeInt(0);
                    }
                    remote.transact(TRANSACTION_writeToSession, data, reply, 0);
                    reply.readException();
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            }

            @Override
            public void commitSession(int sessionId) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(sessionId);
                    remote.transact(TRANSACTION_commitSession, data, reply, 0);
                    reply.readException();
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            }

            @Override
            public void abandonSession(int sessionId) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(sessionId);
                    remote.transact(TRANSACTION_abandonSession, data, reply, 0);
                    reply.readException();
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            }

            @Override
            public void destroy() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    remote.transact(TRANSACTION_destroy, data, reply, 0);
                    reply.readException();
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            }
        }
    }
}
