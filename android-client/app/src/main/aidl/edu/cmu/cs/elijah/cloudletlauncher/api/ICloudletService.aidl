// ICloudletService.aidl
package edu.cmu.cs.elijah.cloudletlauncher.api;

import edu.cmu.cs.elijah.cloudletlauncher.api.ICloudletServiceCallback;

interface ICloudletService {
    // Public APIs
    boolean isServiceReady();

    void findCloudlet(String appId);

    void disconnectCloudlet(String appId);

    void registerCallback(ICloudletServiceCallback callback);

    void unregisterCallback(ICloudletServiceCallback callback);

    // Debugging and configuration APIs
    boolean isProfileReady();

    void useTestProfile(boolean flag);

    void setUserId(String userId);

    void startOpenVpn();

    void endOpenVpn();
}
