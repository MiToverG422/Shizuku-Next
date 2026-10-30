package rikka.shizuku.server.util;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.os.RemoteException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class InstalledPackagesCompatTest {
    private static final InstalledPackagesCompat.LegacyQuery CHANGED_ABI = (flags, user) -> {
        throw new NoSuchMethodError("Old ParceledListSlice return type");
    };

    @Test public void retainsWorkingLegacyApiWithoutCallingFallback() throws Exception {
        List<PackageInfo> expected = Collections.singletonList(new PackageInfo());
        assertSame(expected, InstalledPackagesCompat.query((flags, user) -> expected,
                () -> { throw new AssertionError("Legacy call must take priority"); }, 4224, 0));
    }

    @Test public void acceptsNewReturnTypeAndPreservesLongFlagsAndUser() throws Exception {
        NewApi api = new NewApi();
        long flags = (1L << 35) | 4224;
        assertSame(api.result.list,
                InstalledPackagesCompat.query(CHANGED_ABI, () -> api, flags, 10));
        assertEquals(flags, api.flags);
        assertEquals(10, api.user);
    }

    @Test public void alsoSupportsIntFlagInterfaceWithLegacyReturnContainer() throws Exception {
        IntApi api = new IntApi();
        assertSame(api.result.list,
                InstalledPackagesCompat.query(CHANGED_ABI, () -> api, 4224, 0));
        assertEquals(4224, api.flags);
    }

    @Test public void remoteFailureDoesNotTriggerFallbackOrBecomeEmptyList() {
        RemoteException expected = new RemoteException("Binder disconnected");
        RemoteException actual = assertThrows(RemoteException.class, () ->
                InstalledPackagesCompat.query((flags, user) -> { throw expected; },
                        () -> { throw new AssertionError("Not an ABI error"); }, 0, 0));
        assertSame(expected, actual);
    }

    @Test public void legacyPermissionFailureDoesNotTriggerFallback() {
        SecurityException expected = new SecurityException("Permission denied");
        assertSame(expected, assertThrows(SecurityException.class, () ->
                InstalledPackagesCompat.query((flags, user) -> { throw expected; },
                        () -> { throw new AssertionError("Not an ABI error"); }, 0, 0)));
    }

    @Test public void reflectivePermissionFailureIsUnwrapped() {
        SecurityException actual = assertThrows(SecurityException.class, () ->
                InstalledPackagesCompat.query(CHANGED_ABI, DeniedApi::new, 0, 0));
        assertEquals("Permission denied", actual.getMessage());
    }

    @Test public void genuinelyEmptyResponseRemainsEmpty() throws Exception {
        assertTrue(InstalledPackagesCompat.query(CHANGED_ABI, NullApi::new, 0, 0).isEmpty());
    }

    @Test public void invalidResponseDoesNotLookLikeEmptyInstalledPackages() {
        assertThrows(IllegalStateException.class, () ->
                InstalledPackagesCompat.query(CHANGED_ABI, InvalidApi::new, 0, 0));
    }

    public static final class PackageInfoList {
        final List<PackageInfo> list = Collections.singletonList(new PackageInfo());
        public List<PackageInfo> getList() { return list; }
    }

    public static final class NewApi {
        final PackageInfoList result = new PackageInfoList();
        long flags;
        int user;
        public PackageInfoList getInstalledPackages(long flags, int user) {
            this.flags = flags;
            this.user = user;
            return result;
        }
    }

    public static final class LegacySlice {
        final List<PackageInfo> list = Collections.singletonList(new PackageInfo());
        public List<PackageInfo> getList() { return list; }
    }

    public static final class IntApi {
        final LegacySlice result = new LegacySlice();
        int flags;
        public LegacySlice getInstalledPackages(int flags, int user) {
            this.flags = flags;
            return result;
        }
    }

    public static final class DeniedApi {
        public Object getInstalledPackages(long flags, int user) { throw new SecurityException("Permission denied"); }
    }

    public static final class NullApi {
        public Object getInstalledPackages(long flags, int user) { return null; }
    }

    public static final class InvalidApi {
        public Object getInstalledPackages(long flags, int user) { return new Object(); }
    }
}
