
package com.kokufu.android.apps.fusedroid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class RootedShell {
    public interface OnOutputStreamLineListener {
        public void onOut(String line);
    }

    private final Process mProcess;
    private final InputStreamThread mInputStreamThread;
    private final InputStreamThread mErrorStreamThread;

    public RootedShell(OnOutputStreamLineListener stdout, OnOutputStreamLineListener stderr)
            throws IOException {
        // ProcessBuilder pb = new ProcessBuilder("/system/bin/sh");
        ProcessBuilder pb = new ProcessBuilder("su", "-c /system/bin/sh");
        mProcess = pb.start();

        if (stdout != null) {
            mInputStreamThread = new InputStreamThread(mProcess.getInputStream(), stdout);
            mInputStreamThread.start();
        } else {
            mInputStreamThread = null;
        }
        if (stderr != null) {
            mErrorStreamThread = new InputStreamThread(mProcess.getErrorStream(), stderr);
            mErrorStreamThread.start();
        } else {
            mErrorStreamThread = null;
        }
    }

    public void stdin(byte[] buffer) throws IOException {
        OutputStream outputStream = mProcess.getOutputStream();
        outputStream.write(buffer);
    }

    public void stdinLine(String command) throws IOException {
        OutputStream outputStream = mProcess.getOutputStream();
        outputStream.write(command.getBytes());
        outputStream.write('\n');
    }

    public void exit() throws IOException {
        stdinLine("exit");
    }

    public static class InputStreamThread extends Thread {
        private final BufferedReader mBufferedReader;
        private final OnOutputStreamLineListener mResultListener;

        public InputStreamThread(InputStream is, OnOutputStreamLineListener outputStreamListener) {
            mBufferedReader = new BufferedReader(new InputStreamReader(is));
            mResultListener = outputStreamListener;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    String line = mBufferedReader.readLine();
                    if (line == null) {
                        break;
                    }
                    if (mResultListener != null) {
                        mResultListener.onOut(line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    mBufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
