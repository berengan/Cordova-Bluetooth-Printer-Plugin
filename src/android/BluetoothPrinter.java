package org.apache.cordova.sipkita;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Set;
import java.util.UUID;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.Log;

public class BluetoothPrinter extends CordovaPlugin {
	private static final String LOG_TAG = "BluetoothPrinter";
	BluetoothAdapter mBluetoothAdapter;
	BluetoothSocket mmSocket;
	BluetoothDevice mmDevice;
	OutputStream mmOutputStream;
	InputStream mmInputStream;
	Thread workerThread;
	byte[] readBuffer;
	int readBufferPosition;
	int counter;
	volatile boolean stopWorker;

	public BluetoothPrinter() {}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		if (action.equals("list")) {
			listBT(callbackContext);
			return true;
		} else if (action.equals("open")) {
			String name = args.getString(0);
			if (findBT(callbackContext, name)) {
				try {
					openBT(callbackContext);
				} catch (IOException e) {
					Log.e(LOG_TAG, e.getMessage());
					e.printStackTrace();
				}
			} else {
				callbackContext.error("Bluetooth Device Not Found: " + name);
			}
			return true;
		} else if (action.equals("print")) {
			try {
				String msg = args.getString(0);
				sendData(callbackContext, msg);
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
			return true;
		} else if (action.equals("printImage")) {
			try {
				String filepath = args.getString(0);
		File imagefile = new File(filepath);
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(imagefile);
			Bitmap bitmap = BitmapFactory.decodeStream(fis);
		    ByteArrayOutputStream stream=new ByteArrayOutputStream();
    		bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
		    byte[] imageBytes=stream.toByteArray();
			sendDataBytes(callbackContext, imageBytes);
        } catch (FileNotFoundException e) {
			Log.e(LOG_TAG, e.getMessage());
            e.printStackTrace();
        }
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
			return true;
		} else if (action.equals("printBase64")) {
			try {
				String msg = args.getString(0);
				sendDataBase64(callbackContext, msg);
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
			return true;
		} else if (action.equals("close")) {
			try {
				closeBT(callbackContext);
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
			return true;
		}
		return false;
	}

	void listBT(CallbackContext callbackContext) {
		BluetoothAdapter mBluetoothAdapter = null;
		String errMsg = null;
		try {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter == null) {
				errMsg = "No bluetooth adapter available";
				Log.e(LOG_TAG, errMsg);
				callbackContext.error(errMsg);
				return;
			}
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
			}
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				JSONArray json = new JSONArray();
				for (BluetoothDevice device : pairedDevices) {
//					Hashtable map = new Hashtable();
//					map.put("type", device.getType());
//					map.put("address", device.getAddress());
//					map.put("name", device.getName());
//					JSONObject jObj = new JSONObject(map);
					json.put(device.getName());
				}
				callbackContext.success(json);
			} else {
				callbackContext.error("No Bluetooth Device Found");
			}
//			Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
		} catch (Exception e) {
			errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
	}

	// This will find a bluetooth printer device
	boolean findBT(CallbackContext callbackContext, String name) {
		try {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter == null) {
				Log.e(LOG_TAG, "No bluetooth adapter available");
			}
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
			}
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				for (BluetoothDevice device : pairedDevices) {
					// MP300 is the name of the bluetooth printer device
					if (device.getName().equalsIgnoreCase(name)) {
						mmDevice = device;
						return true;
					}
				}
			}
			Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

	// Tries to open a connection to the bluetooth printer device
	boolean openBT(CallbackContext callbackContext) throws IOException {
		try {
			// Standard SerialPortService ID
			UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
			mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
			mmSocket.connect();
			mmOutputStream = mmSocket.getOutputStream();
			mmInputStream = mmSocket.getInputStream();
			beginListenForData();
//			Log.d(LOG_TAG, "Bluetooth Opened: " + mmDevice.getName());
			callbackContext.success("Bluetooth Opened: " + mmDevice.getName());
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

	// After opening a connection to bluetooth printer device, 
	// we have to listen and check if a data were sent to be printed.
	void beginListenForData() {
		try {
			final Handler handler = new Handler();
			// This is the ASCII code for a newline character
			final byte delimiter = 10;
			stopWorker = false;
			readBufferPosition = 0;
			readBuffer = new byte[1024];
			workerThread = new Thread(new Runnable() {
				public void run() {
					while (!Thread.currentThread().isInterrupted() && !stopWorker) {
						try {
							int bytesAvailable = mmInputStream.available();
							if (bytesAvailable > 0) {
								byte[] packetBytes = new byte[bytesAvailable];
								mmInputStream.read(packetBytes);
								for (int i = 0; i < bytesAvailable; i++) {
									byte b = packetBytes[i];
									if (b == delimiter) {
										byte[] encodedBytes = new byte[readBufferPosition];
										System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
//										final String data = new String(encodedBytes, "US-ASCII");
//										readBufferPosition = 0;
//										handler.post(new Runnable() {
//											public void run() {
//												myLabel.setText(data);
//											}
//										});
									} else {
										readBuffer[readBufferPosition++] = b;
									}
								}
							}
						} catch (IOException ex) {
							stopWorker = true;
						}
					}
				}
			});
			workerThread.start();
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * This will send data to be printed by the bluetooth printer
	 */
	boolean sendDataBytes(CallbackContext callbackContext, byte[] msg) throws IOException {
		try {
			// the text typed by the user
			// TODO: encode msg from base64
			mmOutputStream.write(msg);
			// tell the user data were sent
//			Log.d(LOG_TAG, "Data Sent");
			callbackContext.success("Data Sent");
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}



	/*
	 * This will send data to be printed by the bluetooth printer
	 */
	boolean sendDataBase64(CallbackContext callbackContext, String msg) throws IOException {
		try {
			// the text typed by the user
			byte[] out = Base64.decode(msg);
//			msg += "\n";
			System.out.println(out);
			mmOutputStream.write(out);
			// tell the user data were sent
//			Log.d(LOG_TAG, "Data Sent");
			callbackContext.success("Data Sent");
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}


	/*
	 * This will send data to be printed by the bluetooth printer
	 */
	boolean sendData(CallbackContext callbackContext, String msg) throws IOException {
		try {
			// the text typed by the user
			msg += "\n";
			mmOutputStream.write(msg.getBytes());
			// tell the user data were sent
//			Log.d(LOG_TAG, "Data Sent");
			callbackContext.success("Data Sent");
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

	// Close the connection to bluetooth printer.
	boolean closeBT(CallbackContext callbackContext) throws IOException {
		try {
			stopWorker = true;
			mmOutputStream.close();
			mmInputStream.close();
			mmSocket.close();
//			myLabel.setText("Bluetooth Closed");
			callbackContext.success("Bluetooth Closed");
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}
}
/*
 * Base64.java
 *
 * Brazil project web application toolkit,
 * export version: 2.0 
 * Copyright (c) 2000-2002 Sun Microsystems, Inc.
 *
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is included as
 * the file "license.terms", and also available at
 * http://www.sun.com/
 * 
 * The Original Code is from:
 *    Brazil project web application toolkit release 2.0.
 * The Initial Developer of the Original Code is: cstevens.
 * Portions created by cstevens are Copyright (C) Sun Microsystems,
 * Inc. All Rights Reserved.
 * 
 * Contributor(s): cstevens, suhler.
 *
 * Version:  1.9
 * Created by cstevens on 00/04/17
 * Last modified by suhler on 02/07/24 10:49:48
 */

class Base64 {
    static byte[] encodeData;
    static String charSet = 
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    
    static {
    	encodeData = new byte[64];
	for (int i = 0; i<64; i++) {
	    byte c = (byte) charSet.charAt(i);
	    encodeData[i] = c;
	}
    }

    private Base64() {}

    /**
     * base-64 encode a string
     * @param s		The ascii string to encode
     * @returns		The base64 encoded result
     */

    public static String
    encode(String s) {
        return encode(s.getBytes());
    }

    /**
     * base-64 encode a byte array
     * @param src	The byte array to encode
     * @returns		The base64 encoded result
     */

    public static String
    encode(byte[] src) {
	return encode(src, 0, src.length);
    }

    /**
     * base-64 encode a byte array
     * @param src	The byte array to encode
     * @param start	The starting index
     * @param len	The number of bytes
     * @returns		The base64 encoded result
     */

    public static String
    encode(byte[] src, int start, int length) {
        byte[] dst = new byte[(length+2)/3 * 4 + length/72];
        int x = 0;
        int dstIndex = 0;
        int state = 0;	// which char in pattern
        int old = 0;	// previous byte
        int len = 0;	// length decoded so far
	int max = length + start;
        for (int srcIndex = start; srcIndex<max; srcIndex++) {
	    x = src[srcIndex];
	    switch (++state) {
	    case 1:
	        dst[dstIndex++] = encodeData[(x>>2) & 0x3f];
		break;
	    case 2:
	        dst[dstIndex++] = encodeData[((old<<4)&0x30) 
	            | ((x>>4)&0xf)];
		break;
	    case 3:
	        dst[dstIndex++] = encodeData[((old<<2)&0x3C) 
	            | ((x>>6)&0x3)];
		dst[dstIndex++] = encodeData[x&0x3F];
		state = 0;
		break;
	    }
	    old = x;
	    if (++len >= 72) {
	    	dst[dstIndex++] = (byte) '\n';
	    	len = 0;
	    }
	}

	/*
	 * now clean up the end bytes
	 */

	switch (state) {
	case 1: dst[dstIndex++] = encodeData[(old<<4) & 0x30];
	   dst[dstIndex++] = (byte) '=';
	   dst[dstIndex++] = (byte) '=';
	   break;
	case 2: dst[dstIndex++] = encodeData[(old<<2) & 0x3c];
	   dst[dstIndex++] = (byte) '=';
	   break;
	}
	return new String(dst);
    }

    /**
     * A Base64 decoder.  This implementation is slow, and 
     * doesn't handle wrapped lines.
     * The output is undefined if there are errors in the input.
     * @param s		a Base64 encoded string
     * @returns		The byte array eith the decoded result
     */

    public static byte[]
    decode(String s) {
      int end = 0;	// end state
      if (s.endsWith("=")) {
	  end++;
      }
      if (s.endsWith("==")) {
	  end++;
      }
      int len = (s.length() + 3)/4 * 3 - end;
      byte[] result = new byte[len];
      int dst = 0;
      try {
	  for(int src = 0; src< s.length(); src++) {
	      int code =  charSet.indexOf(s.charAt(src));
	      if (code == -1) {
	          break;
	      }
	      switch (src%4) {
	      case 0:
	          result[dst] = (byte) (code<<2);
	          break;
	      case 1: 
	          result[dst++] |= (byte) ((code>>4) & 0x3);
	          result[dst] = (byte) (code<<4);
	          break;
	      case 2:
	          result[dst++] |= (byte) ((code>>2) & 0xf);
	          result[dst] = (byte) (code<<6);
	          break;
	      case 3:
	          result[dst++] |= (byte) (code & 0x3f);
	          break;
	      }
	  }
      } catch (ArrayIndexOutOfBoundsException e) {}
      return result;
    }
}
