package edu.cmu.cs.gabriel;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import edu.cmu.cs.IO.DirectoryPicker;
import edu.cmu.cs.gabriel.network.NetworkProtocol;
import edu.cmu.cs.gabriel.network.VideoControlThread;

/**
 * Created by junjuew on 2/2/16.
 */
public class GabrielConfigurationAsyncTask extends AsyncTask<String, Integer, Boolean>{
    private static final String LOG_TAG = "ConfigurationAsyncTask";
    private Activity callingActivity;

    private ProgressDialog dialog;
    private InetAddress remoteIP;
    private int sendToPort;
    private int recvFromPort;

    // TCP send socket
    private Socket tcpSocket = null;
    private DataOutputStream networkWriter = null;

    // TCP recv socket
    private Socket recvTcpSocket = null;
    private DataInputStream networkReader = null;
    public AsyncResponse delegate =null;
    private String action=null;
    private volatile String uiMsg=null;

    private byte[] extra=null;

    // you may separate this or combined to caller class.
    public interface AsyncResponse {
        void processFinish(String action, boolean output, byte[] extra);
    }

    public GabrielConfigurationAsyncTask(Activity activity,
                                         String IPString,
                                         int sendToPort,
                                         int recvFromPort,
                                         String action) {
        this.callingActivity =activity;
        dialog = new ProgressDialog(callingActivity);
        try {
            remoteIP = InetAddress.getByName(IPString);
        } catch (UnknownHostException e) {
            Log.e(LOG_TAG, "unknown host: " + e.getMessage());
        }
        this.sendToPort = sendToPort;
        this.recvFromPort = recvFromPort;
        this.action=action;
    }

    public GabrielConfigurationAsyncTask(Activity activity,
                                         String IPString,
                                         int sendToPort,
                                         int recvFromPort,
                                         String action,
                                         AsyncResponse delegate) {
        this(activity, IPString, sendToPort, recvFromPort, action);
        this.delegate = delegate;
    }

    private void closeConnection(){
        if (tcpSocket != null) {
            try {
                tcpSocket.close();
            } catch (IOException e) {
            }
        }
        if (networkWriter != null) {
            try {
                networkWriter.close();
            } catch (IOException e) {
            }
        }
        if (recvTcpSocket != null) {
            try {
                recvTcpSocket.close();
            } catch (IOException e) {
            }
        }
        if (networkReader != null) {
            try {
                networkReader.close();
            } catch (IOException e) {
            }
        }
    }

    private void setupConnection(InetAddress ip, int sendToPort, int recvFromPort)
            throws IOException {
        closeConnection();
        tcpSocket = new Socket();
        tcpSocket.setTcpNoDelay(true);
        tcpSocket.connect(new InetSocketAddress(ip, sendToPort), 3 * 1000);
        networkWriter = new DataOutputStream(tcpSocket.getOutputStream());

        recvTcpSocket = new Socket();
        recvTcpSocket.setTcpNoDelay(true);
        recvTcpSocket.setSoTimeout(5 * 1000 * 60);
        recvTcpSocket.connect(new InetSocketAddress(ip, recvFromPort), 3 * 1000);
        networkReader = new DataInputStream(recvTcpSocket.getInputStream());
    }

    private void sendPacket(byte[] header, byte[] data) throws IOException {
        //send add person packet first
        // make it as a single packet
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos=new DataOutputStream(baos);

        dos.writeInt(header.length);
        dos.writeInt(data.length);
        dos.write(header);
        dos.write(data);
        networkWriter.write(baos.toByteArray());
        networkWriter.flush();
        Log.d(LOG_TAG, "header size: " + header.length+ " data size: " +data.length);
    }

    private String receiveMsg(DataInputStream reader) throws IOException {
        int retLength = reader.readInt();
        byte[] recvByte = new byte[retLength];
        int readSize = 0;
        while(readSize < retLength){
            int ret = reader.read(recvByte, readSize, retLength-readSize);
            if(ret <= 0){
                break;
            }
            readSize += ret;
        }
        String receivedString = new String(recvByte);
        return receivedString;
    }

    private String parseResponseData(String response) throws JSONException {
        JSONObject obj;
        obj = new JSONObject(response);
        String result = null;
        result = obj.getString(NetworkProtocol.CUSTOM_DATA_MESSAGE_VALUE);
        Log.d(LOG_TAG, "resp: " + result.substring(0, Math.min(result.length(), 10)));
        return result;
    }

    private String parseResponsePacket(String recvData){
        // convert the message to JSON
        JSONObject obj;
        String msgData = null;
        long frameID = -1;
        String ret=null;
        try{
            obj = new JSONObject(recvData);
            frameID = obj.getLong(NetworkProtocol.HEADER_MESSAGE_FRAME_ID);
            Log.d(LOG_TAG, "received response. frameID: "+frameID);
            msgData = obj.getString(NetworkProtocol.HEADER_MESSAGE_RESULT);
            ret = parseResponseData(msgData);
        } catch(JSONException e){
            e.printStackTrace();
        }
        return ret;
    }

    private void switchConnection() throws IOException {
        InetAddress ip=null;
        if (remoteIP.equals(Const.CLOUD_GABRIEL_IP)){
            try {
                ip = InetAddress.getByName(Const.CLOUDLET_GABRIEL_IP);
            } catch (UnknownHostException e) {
                Log.e(LOG_TAG, "unknown host: " + e.getMessage());
            }
        } else {
            try {
                ip = InetAddress.getByName(Const.CLOUD_GABRIEL_IP);
            } catch (UnknownHostException e) {
                Log.e(LOG_TAG, "unknown host: " + e.getMessage());
            }
        }
        setupConnection(ip, sendToPort,recvFromPort);
        Log.d(LOG_TAG, "switching host: " + remoteIP + " changed to " + ip);
    }

    private byte[] generateLoadStateHeader(String openfaceState) {
        JSONObject headerJson = new JSONObject();
        try{

            headerJson.put("id", 0);
            headerJson.put("load_state", openfaceState);
            Log.d(LOG_TAG, "send load_state request");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return headerJson.toString().getBytes();
    }

    private byte[] generateGetStateHeader(){
        JSONObject headerJson = new JSONObject();
        try{

            headerJson.put("id", 0);
            headerJson.put("get_state", "True");
            Log.d(LOG_TAG, "send get_state request");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return headerJson.toString().getBytes();
    }


    private byte[] generateHeader(String headerContent) {
        JSONObject headerJson = new JSONObject();
        try{

            headerJson.put("id", 0);
            headerJson.put(headerContent, "True");
            Log.d(LOG_TAG, "send request: " + headerContent);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return headerJson.toString().getBytes();
    }

    private byte[] generateHeader(String header, String value) {
        JSONObject headerJson = new JSONObject();
        try{

            headerJson.put("id", 0);
            headerJson.put(header, value);
            Log.d(LOG_TAG, "send request: " + header + " value: "+value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return headerJson.toString().getBytes();
    }

    @Override
    protected void onPreExecute() {
        dialog.setMessage("Communicating to Backend Server ... Please wait");
        dialog.show();
    }

    @Override
    protected void onPostExecute(Boolean bgResult) {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
        if (null != this.delegate){
            delegate.processFinish(this.action, bgResult, extra);
        }
        if (null!=uiMsg){
            Log.i("configurationAsyncTask", "success: " + bgResult + ". " + uiMsg);
            Toast.makeText(callingActivity.getApplicationContext(),
                    "success? "+bgResult + "\nmessage: " + uiMsg,
                Toast.LENGTH_LONG).show();
        } else {
            Log.i("configurationAsyncTask", "success: " + bgResult);
            Toast.makeText(callingActivity.getApplicationContext(),
                    "success? "+bgResult,
                    Toast.LENGTH_LONG).show();
        }

//        Toast.makeText(callingActivity.getApplicationContext(), "async task success? "+bgResult,
//                Toast.LENGTH_LONG).show();
    }

    @Override
    protected Boolean doInBackground(String... urls) {
        Boolean success =false;
        String task = action;
        // task is to sync state
        if (task.equals(Const.GABRIEL_CONFIGURATION_SYNC_STATE)){
            try{
                setupConnection(remoteIP, sendToPort, recvFromPort);
                //get state
                byte[] header= generateGetStateHeader();
                byte[] data= "dummpy".getBytes();
                sendPacket(header, data);
                String resp = receiveMsg(networkReader);
                String openfaceState=parseResponsePacket(resp);
                //switch connection
                switchConnection();
                //load state
                byte[] loadStateHeader= generateLoadStateHeader(openfaceState);
                sendPacket(loadStateHeader, data);
                resp = receiveMsg(networkReader);
                String content =parseResponsePacket(resp).toLowerCase();
                if (content.equals("true")) {
                    success = true;
                }
            } catch (IOException e){
                e.printStackTrace();
                Log.e(LOG_TAG, "IO exception sync state failed");
            }
        } else if (task.equals(Const.GABRIEL_CONFIGURATION_RESET_STATE)){
            try{
                setupConnection(remoteIP, sendToPort, recvFromPort);
                //get state
                byte[] header= generateHeader("reset");
                byte[] data= "dummpy".getBytes();
                sendPacket(header, data);
                String resp = receiveMsg(networkReader);
                String content =parseResponsePacket(resp).toLowerCase();
                if (content.equals("true")) {
                    success = true;
                }
            } catch (IOException e){
                e.printStackTrace();
                Log.e(LOG_TAG, "IO exception reset state failed");
            }
        } else if (task.equals(Const.GABRIEL_CONFIGURATION_REMOVE_PERSON)){
            try{
                setupConnection(remoteIP, sendToPort, recvFromPort);
                //get state
                String name = urls[0];
                byte[] header= generateHeader("remove_person", name);
                byte[] data= "dummpy".getBytes();
                sendPacket(header, data);
                String resp = receiveMsg(networkReader);
                String content =parseResponsePacket(resp).toLowerCase();
                if (content.equals("true")) {
                    success = true;
                }
            } catch (IOException e){
                e.printStackTrace();
                Log.e(LOG_TAG, "IO exception reset state failed");
            }
        } else if (task.equals(Const.GABRIEL_CONFIGURATION_DOWNLOAD_STATE)){
            try{
                setupConnection(remoteIP, sendToPort, recvFromPort);
                //get state
                byte[] header= generateGetStateHeader();
                byte[] data= "dummpy_long_enough_for_correctness".getBytes();
                sendPacket(header, data);
                String resp = receiveMsg(networkReader);
                String openfaceState=parseResponsePacket(resp);
                extra=openfaceState.getBytes();
                try{
                    Log.d(LOG_TAG, "saving openface state string to file...");
                    if (isExternalStorageWritable()){
                        // Get the directory for the user's public pictures directory.
                        File root = new File(Environment.getExternalStorageDirectory(),
                                Const.FILE_ROOT_PATH);
//                        if (!root.mkdirs()) {
//                            throw new FileNotFoundException("Directory cannot be created: "+root);
//                        }
                        root.mkdirs();
                        SimpleDateFormat dateFormat = new SimpleDateFormat("MM_dd_hh_mm_ss");
                        GregorianCalendar cal = new GregorianCalendar();
                        dateFormat.setTimeZone(cal.getTimeZone());
                        File file = new File(root, "openface_"+ dateFormat.format(cal.getTime())
                                +".txt");
                        FileOutputStream f = new FileOutputStream(file);
                        PrintWriter pw = new PrintWriter(f);
                        pw.print(openfaceState);
                        pw.flush();
                        pw.close();
                        f.close();
                        success = true;
                        uiMsg="saved state to: " + file;
                    } else {
                        uiMsg="No permission to write external storage";
                    }
                } catch (FileNotFoundException e){
                    uiMsg=e.getMessage();
                }
            } catch (IOException e){
                e.printStackTrace();
                Log.e(LOG_TAG, "IO exception sync state failed");
            }
        } else if (task.equals(Const.GABRIEL_CONFIGURATION_UPLOAD_STATE)){

        }
        closeConnection();
        return success;
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

}
