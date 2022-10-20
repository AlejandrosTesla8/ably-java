package io.ably.lib.platform;

import android.content.Context;
import io.ably.lib.transport.NetworkConnectivity;
import io.ably.lib.transport.NetworkConnectivity.DelegatedNetworkConnectivity;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.Log;

/**
 * An internal class with platform specific logic.
 * Even though it's internal, its visibility modifier is public because it's referenced by two other packages in this module (one for REST, one for Realtime).
 */
// TODO - change visibility to private or package when possible
public class AndroidPlatform implements Platform {
    public static final String name = "android";

    public AndroidPlatform() {}

    public Context getApplicationContext() {
        return applicationContext;
    }
    /**
     * Set the Android Context for this instance
     */
    public void setAndroidContext(Context context) throws AblyException {
        Log.v(TAG, "setAndroidContext: context=" + context);
        context = context.getApplicationContext();
        if(applicationContext != null) {
            Log.v(TAG, "setAndroidContext(): applicationContext has already been set");
            if(context == applicationContext) {
                Log.v(TAG, "setAndroidContext(): existing applicationContext is compatible with that being set");
                return;
            }
            throw AblyException.fromErrorInfo(new ErrorInfo("Incompatible application context set", 40000, 400));
        } else {
            Log.v(TAG, "setAndroidContext(): there was no existing applicationContext");
        }
        applicationContext = context;
        AndroidNetworkConnectivity.getNetworkConnectivity(context).addListener(this.networkConnectivity);
    }

    public boolean hasApplicationContext() {
        return applicationContext != null;
    }

    /**
     * Get the NetworkConnectivity tracker instance for this context
     * @return A {@link NetworkConnectivity} object
     */
    public NetworkConnectivity getNetworkConnectivity() {
        return networkConnectivity;
    }

    private Context applicationContext;
    private final DelegatedNetworkConnectivity networkConnectivity = new DelegatedNetworkConnectivity();

    private static final String TAG = AndroidPlatform.class.getName();
}
