package edu.cmu.cs.gabriel.network;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.gabriel.token.TokenController;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;

public class VideoStreamingThread extends Thread {

	private static final String LOG_TAG = "krha";
	protected File[] imageFiles = null;
	protected int indexImageFile = 0;

	private boolean is_running = false;
	private InetAddress remoteIP;
	private int remotePort;

	// UDP
	private DatagramSocket udpSocket = null;
	// TCP
	private Socket tcpSocket = null;
	private DataOutputStream networkWriter = null;
	private VideoControlThread networkReceiver = null;

	private FileInputStream cameraInputStream;
	private byte[] frameBuffer = null;
	private long frameGeneratedTime = 0;
	private Object frameLock = new Object();
	private Handler networkHander = null;
	private long frameID = 1;	// must start from 1

	private TokenController tokenController;

	//person's name for training face recognizers
	private String name = null;
    private boolean hasSentInitialization;

    //for swapping faces
    private HashMap<String, String> faceTable;
    //for setting openface server state
    private boolean reset;
    public volatile String state_string;
    public Bitmap curFrame=null;

    private String connection_failure_msg = "Connecting to Gabriel Server Failed. " +
            "Do you have a gabriel server running at: " + Const.GABRIEL_IP + "?";

	public VideoStreamingThread(FileDescriptor fd,
								String IPString,
								int port,
								Handler handler,
								TokenController tokenController,
                                boolean reset) {
		is_running = false;
		this.networkHander = handler;
		this.tokenController = tokenController;
		
		try {
			remoteIP = InetAddress.getByName(IPString);
		} catch (UnknownHostException e) {
			Log.e(LOG_TAG, "unknown host: " + e.getMessage());
		}
		remotePort = port;
//		cameraInputStream = new FileInputStream(fd);
		
		// check input data at image directory
//		imageFiles = this.getImageFiles(Const.TEST_IMAGE_DIR);
        imageFiles=null;
        this.hasSentInitialization =true;
        this.reset = reset;
	}

	public VideoStreamingThread(FileDescriptor fd,
								String IPString,
								int port,
								Handler handler,
								TokenController tokenController,
                                boolean reset,
								String name) {
		this(fd, IPString, port, handler, tokenController, reset);
        if (name != null){
            this.name = name;
            this.hasSentInitialization =false;
        }
	}

    public VideoStreamingThread(FileDescriptor fd,
                                String IPString,
                                int port,
                                Handler handler,
                                TokenController tokenController,
                                boolean reset,
                                HashMap faceTable) {
        this(fd, IPString, port, handler, tokenController, reset);
        if (faceTable != null){
            this.faceTable=faceTable;
            this.hasSentInitialization =false;
        }
    }

	private File[] getImageFiles(File imageDir) {
		if (imageDir == null){
			return null;
		}
	    File[] files = imageDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				if (filename.toLowerCase().endsWith("jpg") == true)
					return true;
				if (filename.toLowerCase().endsWith("jpeg") == true)
					return true;
				return false;
			}
		});
		return files;
	}


    private void switchConnection(){
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
        if (networkReceiver != null) {
            networkReceiver.close();
        }

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
        try {
            tcpSocket = new Socket();
            tcpSocket.setTcpNoDelay(true);
            tcpSocket.connect(new InetSocketAddress(ip, remotePort), 5 * 1000);
            networkWriter = new DataOutputStream(tcpSocket.getOutputStream());
            DataInputStream networkReader = new DataInputStream(tcpSocket.getInputStream());
            networkReceiver = new VideoControlThread(networkReader, this.networkHander, tokenController);
            networkReceiver.start();
        } catch (IOException e) {
            Log.e(LOG_TAG, Log.getStackTraceString(e));
            Log.e(LOG_TAG, "Error in initializing Data socket: " + e);
            this.notifyError(connection_failure_msg);
            this.is_running = false;
            return;
        }
        Log.d(LOG_TAG, "switching host: " + remoteIP + " changed to " + ip);
    }

	public void run() {
		this.is_running = true;
		Log.i(LOG_TAG, "Streaming thread running");

		int packet_count = 0;
		long packet_firstUpdateTime = 0;
		long packet_currentUpdateTime = 0;
		int packet_totalsize = 0;
		long packet_prevUpdateTime = 0;

		try {
			tcpSocket = new Socket();
			tcpSocket.setTcpNoDelay(true);
			tcpSocket.connect(new InetSocketAddress(remoteIP, remotePort), 5 * 1000);
			networkWriter = new DataOutputStream(tcpSocket.getOutputStream());
			DataInputStream networkReader = new DataInputStream(tcpSocket.getInputStream());
			networkReceiver = new VideoControlThread(networkReader, this.networkHander, tokenController);
			networkReceiver.start();
		} catch (IOException e) {
		    Log.e(LOG_TAG, Log.getStackTraceString(e));
			Log.e(LOG_TAG, "Error in initializing Data socket: " + e);
			this.notifyError(connection_failure_msg);
			this.is_running = false;
			return;
		}


		while (this.is_running) {
			try {
				// check token
				if (this.tokenController.getCurrentToken() <= 0) {
//					Log.d(LOG_TAG, "waiting");
					continue;
				}

                long time=System.currentTimeMillis();
                //measurement
//                if (Const.MEASURE_LATENCY){
//                    Message msg = Message.obtain();
//                    msg.what = NetworkProtocol.NETWORK_MEASUREMENT;
//                    networkHander.sendMessage(msg);
//                }

				// get data
				byte[] data = null;
				long dataTime = 0;
				long sendingFrameID = 0;
				synchronized(frameLock){
					while (this.frameBuffer == null){
						try {
							frameLock.wait();
						} catch (InterruptedException e) {}						
					}
					
					data = this.frameBuffer;
					dataTime = this.frameGeneratedTime;
					sendingFrameID = this.frameID;
					this.frameBuffer = null;
				}
                Log.d(LOG_TAG, "wait for framebuffer : " + (System.currentTimeMillis()-time));

				// make it as a single packet
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
		        DataOutputStream dos=new DataOutputStream(baos);
				byte[] header;

                JSONObject headerJson = new JSONObject();
                try{
                    headerJson.put("id", sendingFrameID);
                    //reset flag
                    if (reset){
                        headerJson.put("reset", "True");
                        reset = false;
                    }

                    //initilization packet for training and detecting
                    if (!hasSentInitialization) {
                        if (null != this.name) {
                            headerJson.put("add_person", this.name);
                            headerJson.put("training", this.name);
                            hasSentInitialization = true;
                        } else if (null != this.faceTable) {
                            //detecting case
                            JSONObject faceTableJson = new JSONObject();
                            String faceTableString = null;
                            try {
                                for (Map.Entry<String, String> entry : faceTable.entrySet()) {
                                    faceTableJson.put(entry.getKey(), entry.getValue());
                                }
                                faceTableString = faceTableJson.toString();
                                headerJson.put("face_table", faceTableString);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            hasSentInitialization = true;
                        }
                    } else {
                        //if training then add training flag
                        if (null != this.name){
                            headerJson.put("training", this.name);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
//                Log.d("json", headerJson.toString());
                header = headerJson.toString().getBytes();

				dos.writeInt(header.length);
				dos.writeInt(data.length);
				dos.write(header);
				dos.write(data);

                Log.d(LOG_TAG, "sending frameID: " + sendingFrameID);
                this.tokenController.sendData(sendingFrameID, System.currentTimeMillis(), dos.size());
				networkWriter.write(baos.toByteArray());
				networkWriter.flush();
				this.tokenController.decreaseToken();
//                Log.d(LOG_TAG,"sent frame ID: "+sendingFrameID);
				
				// measurement
		        if (packet_firstUpdateTime == 0) {
		        	packet_firstUpdateTime = System.currentTimeMillis();
		        }
		        packet_currentUpdateTime = System.currentTimeMillis();
                packet_count++;
                packet_totalsize += data.length;
                if (packet_count % 10 == 0) {
                    double currentFPS =
                            1000.0 * packet_count / (packet_currentUpdateTime - packet_firstUpdateTime);
                    Log.d(LOG_TAG, "(NET)\t" + "BW: "
                            + 8.0*packet_totalsize / (packet_currentUpdateTime-packet_firstUpdateTime)/1000
                            + " Mbps\tCurrent BW: "
                            + 8.0*data.length/(packet_currentUpdateTime - packet_prevUpdateTime)/1000
                            + " Mbps\t"
                            + "FPS: "
                            + currentFPS);

				}
		        packet_prevUpdateTime = packet_currentUpdateTime;
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				this.notifyError(e.getMessage());
				this.is_running = false;
				return;
			} 

			try{
				Thread.sleep(1);
			} catch (InterruptedException e) {}
		}
		this.is_running = false;
	}

	public boolean stopStreaming() {
		is_running = false;
		if (udpSocket != null) {
			udpSocket.close();
			udpSocket = null;
		}
		if (cameraInputStream != null) {
			try {
				cameraInputStream.close();
			} catch (IOException e) {
			}
		}
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
		if (networkReceiver != null) {
			networkReceiver.close();
		}

		return true;
	}

	
	private Size cameraImageSize = null;
    private long frame_count = 0, frame_firstUpdateTime = 0;
    private long frame_prevUpdateTime = 0, frame_currentUpdateTime = 0;
    private long frame_totalsize = 0;

    /**
     * receive frame from camera preview
     * @param frame
     * @param parameters
     */
    public void pushAsync(byte[] frame, Parameters parameters) {
        Object frameObj = frame;
        Object param = parameters;
        Object inputAsync[] = new Object[]{frame, param};
        new PushTask().execute(inputAsync);
    }

//    long time = System.currentTimeMillis();
    /**
     * receive frame from opencv camera preview
     * @param frame
     */
    public void pushCVFrame(Mat frame) {
//        time=System.currentTimeMillis();
        Log.d(LOG_TAG, "pushed CV frame for compression");
        new PushCVFrameAsyncTask().execute(frame);
    }

    private class PushCVFrameAsyncTask extends AsyncTask<Mat, Void, byte[]> {
        @Override
        protected void onPostExecute(byte[] buffer) {
            //TODO: implement this if you still want to draw segments based on image but not
//            Log.d(LOG_TAG, "compression finsihed "+ (System.currentTimeMillis()- time));
            // from packet
//            if (!Const.FACE_DEMO_DISPLAY_RECEIVED_FACES) {
//                if (curFrame == null) {
//                    BitmapFactory.Options options = new BitmapFactory.Options();
//                    options.inMutable = true;
//                    curFrame = BitmapFactory.decodeByteArray(buffer,
//                            0,
//                            buffer.length, options);
//                    Log.i(LOG_TAG, "created current frame bitmap");
//                } else {
//                    BitmapFactory.Options options = new BitmapFactory.Options();
//                    options.inBitmap = curFrame;
//                    options.inMutable = true;
//                    curFrame = BitmapFactory.decodeByteArray(buffer,
//                            0,
//                            buffer.length,
//                            options);
//                }
//            }
            return;
        }


        @Override
        protected byte[] doInBackground(Mat... frames) {
            Mat frame=frames[0];
            if (frame_firstUpdateTime == 0) {
                frame_firstUpdateTime = System.currentTimeMillis();
            }
            frame_currentUpdateTime = System.currentTimeMillis();

            long time = System.currentTimeMillis();

            //android compression version
//            int datasize = 0;
//            Bitmap bmp = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.RGB_565);
//            Utils.matToBitmap(frame, bmp);
//            ByteArrayOutputStream bos=new ByteArrayOutputStream();
//            bmp.compress(Bitmap.CompressFormat.JPEG, 70, bos);
//            byte[] jpgByteArray=bos.toByteArray();

            //opencv compression version
            int datasize = 0;
            MatOfByte jpgByteMat = new MatOfByte();
            MatOfInt params = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 80);
            Imgcodecs.imencode(".jpg", frame, jpgByteMat, params);
            Log.d(LOG_TAG, "compression imencode took " + (System.currentTimeMillis()-time));
            byte[] jpgByteArray = jpgByteMat.toArray();

            synchronized (frameLock) {
                frameBuffer = jpgByteArray;
                frameGeneratedTime = System.currentTimeMillis();
                frameID++;
                frameLock.notify();
            }
            datasize = jpgByteArray.length;
            frame_count++;
            frame_totalsize += datasize;
            if (frame_count % 50 == 0) {
                Log.d(LOG_TAG, "(IMG)\t" +
                        "BW: " + 8.0 * frame_totalsize / (frame_currentUpdateTime - frame_firstUpdateTime) / 1000 +
                        " Mbps\tCurrent FPS: " + 8.0 * datasize / (frame_currentUpdateTime - frame_prevUpdateTime) / 1000 + " Mbps\t" +
                        "FPS: " + 1000.0 * frame_count / (frame_currentUpdateTime - frame_firstUpdateTime));
            }
            frame_prevUpdateTime = frame_currentUpdateTime;
            Log.d(LOG_TAG, "compression routine took " + (System.currentTimeMillis()-time));
            return jpgByteArray;
        }
    }

    private class PushTask extends AsyncTask<Object, Void, byte[]> {
        @Override
        protected void onPostExecute(byte[] buffer){
            if (!Const.FACE_DEMO_DISPLAY_RECEIVED_FACES){
                if (curFrame==null){
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inMutable=true;
                    curFrame = BitmapFactory.decodeByteArray(buffer,
                            0,
                            buffer.length,options);
                    Log.i(LOG_TAG, "created current frame bitmap");
                } else {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inBitmap=curFrame;
                    options.inMutable=true;
                    curFrame=BitmapFactory.decodeByteArray(buffer,
                            0,
                            buffer.length,
                            options);
//                Log.d(LOG_TAG, "reused current bitmap");
                }
            }
        }

        @Override
        protected byte[] doInBackground(Object... objs) {
            byte[] frame = (byte[]) objs[0];
            Parameters parameters = (Parameters) objs[1];
            if (frame_firstUpdateTime == 0) {
                frame_firstUpdateTime = System.currentTimeMillis();
            }
            frame_currentUpdateTime = System.currentTimeMillis();

            int datasize = 0;
            cameraImageSize = parameters.getPreviewSize();
            YuvImage image = new YuvImage(frame, parameters.getPreviewFormat(), cameraImageSize.width,
                    cameraImageSize.height, null);
            ByteArrayOutputStream tmpBuffer = new ByteArrayOutputStream();
            image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, tmpBuffer);
            synchronized (frameLock) {
                frameBuffer = tmpBuffer.toByteArray();
                frameGeneratedTime = System.currentTimeMillis();
                frameID++;
                frameLock.notify();
            }
            datasize = tmpBuffer.size();
            frame_count++;
            frame_totalsize += datasize;
            if (frame_count % 50 == 0) {
                Log.d(LOG_TAG, "(IMG)\t" +
                        "BW: " + 8.0 * frame_totalsize / (frame_currentUpdateTime - frame_firstUpdateTime) / 1000 +
                        " Mbps\tCurrent FPS: " + 8.0 * datasize / (frame_currentUpdateTime - frame_prevUpdateTime) / 1000 + " Mbps\t" +
                        "FPS: " + 1000.0 * frame_count / (frame_currentUpdateTime - frame_firstUpdateTime));
            }
            frame_prevUpdateTime = frame_currentUpdateTime;
            return tmpBuffer.toByteArray();
        }
    }


	private void notifyError(String message) {
		// callback
		Message msg = Message.obtain();
		msg.what = NetworkProtocol.NETWORK_RET_FAILED;
		Bundle data = new Bundle();
		data.putString("message", message);
		msg.setData(data);
		this.networkHander.sendMessage(msg);
	}

}
