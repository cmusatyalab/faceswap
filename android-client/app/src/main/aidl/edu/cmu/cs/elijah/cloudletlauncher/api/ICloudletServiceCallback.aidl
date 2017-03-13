// ICloudletServiceCallback.aidl
package edu.cmu.cs.elijah.cloudletlauncher.api;

/**
 * Callback interface to message each application (client)
 * Note that this is a one-way interface so the server does not block waiting for the client.
 */
oneway interface ICloudletServiceCallback {
    void message(String message);
    void newServerIP(String ipAddr);
    void amReady();
}
