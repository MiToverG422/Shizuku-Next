package rikka.shizuku.server.util;

import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ParceledListSlice;
import android.os.RemoteException;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.util.SystemServiceBinder;

/** Keeps the legacy Binder API while accepting Android 17's PackageInfoList ABI. */
public final class InstalledPackagesCompat {
    private static final String TAG = "InstalledPackagesCompat";
    private static final SystemServiceBinder<IPackageManager> PACKAGE_MANAGER =
            new SystemServiceBinder<>("package", IPackageManager.Stub::asInterface);

    private InstalledPackagesCompat() {}

    public static List<PackageInfo> getInstalledPackages(long flags, int userId) throws RemoteException {
        return query(InstalledPackagesCompat::queryLegacy, PACKAGE_MANAGER::get, flags, userId);
    }

    private static List<PackageInfo> queryLegacy(long flags, int userId) throws RemoteException {
        ParceledListSlice<PackageInfo> slice = PackageManagerApis.getInstalledPackages(flags, userId);
        return slice != null ? slice.getList() : Collections.emptyList();
    }

    public static List<PackageInfo> getInstalledPackagesNoThrow(long flags, int userId) {
        try {
            return getInstalledPackages(flags, userId);
        } catch (Throwable failure) {
            Log.w(TAG, "Package query failed for user " + userId, failure);
            return Collections.emptyList();
        }
    }

    @FunctionalInterface
    interface LegacyQuery {
        List<PackageInfo> get(long flags, int userId) throws RemoteException;
    }

    static List<PackageInfo> query(LegacyQuery legacy, Supplier<?> manager, long flags, int userId)
            throws RemoteException {
        try {
            return legacy.get(flags, userId);
        } catch (LinkageError changedAbi) {
            // The method name and parameters remain the same on Android 17, but
            // its return type changed. Reflection does not bind that return type.
            return queryReflectively(manager.get(), flags, userId);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<PackageInfo> queryReflectively(Object manager, long flags, int userId)
            throws RemoteException {
        if (manager == null) throw new IllegalStateException("Package manager is unavailable");
        try {
            Method query;
            Object flagsArgument;
            try {
                query = manager.getClass().getMethod("getInstalledPackages", long.class, int.class);
                flagsArgument = flags;
            } catch (NoSuchMethodException olderAbi) {
                query = manager.getClass().getMethod("getInstalledPackages", int.class, int.class);
                flagsArgument = (int) flags;
            }
            Object result = query.invoke(manager, flagsArgument, userId);
            if (result == null) return Collections.emptyList();
            Object list = result instanceof List ? result : result.getClass().getMethod("getList").invoke(result);
            if (!(list instanceof List)) throw new IllegalStateException("Invalid installed-package list");
            return (List<PackageInfo>) list;
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RemoteException) throw (RemoteException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Installed-package query failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported installed-package API", failure);
        }
    }
}
