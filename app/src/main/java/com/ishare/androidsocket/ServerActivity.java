package com.ishare.androidsocket;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 服务端代码
 * Created by huangyouyang on 2017/6/29.
 */

public class ServerActivity extends AppCompatActivity {

    private TextView tvState = null;
    private TextView tvIp = null;
    private EditText etSend = null;
    private Button btnSend = null;
    private Button btnAcept = null;

    private Socket socket;
    private ServerSocket mServerSocket = null;
    private boolean running = false;
    private AcceptThread mAcceptThread;
    private ReceiveThread mReceiveThread;
    private Handler mHandler = null;

    class ServerHandler extends Handler {

        static final int CONNECT_SUCCESS = 1;
        static final int DISCONNECT = 2;
        static final int RECEIVE_DATA = 3;
        static final int SEND_SATA = 4;

        final WeakReference<Context> mContext;
        ServerHandler(Context context, Looper looper) {
            super(looper);
            this.mContext = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RECEIVE_DATA:
                    String str = (String) msg.obj;
                    tvState.setText(str);
                    break;

                case CONNECT_SUCCESS:
                    tvIp.setText("客户端" + msg.obj + "已连接");
                    displayToast("连接成功");
                    break;

                case DISCONNECT:
                    displayToast("连接已断开");
                    //清空TextView
                    tvState.setText(null);
                    tvIp.setText(null);
                    try {
                        socket.close();
                        mServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    btnAcept.setEnabled(true);
                    btnSend.setEnabled(false);
                    break;

                case SEND_SATA:
                    etSend.setText("");
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        initView();
        initData();
        initListener();
    }

    private void initView() {

        tvIp = (TextView) findViewById(R.id.tv_ip);
        etSend = (EditText) findViewById(R.id.et_send);
        tvState = (TextView) findViewById(R.id.tv_state);
        btnAcept = (Button) findViewById(R.id.btn_accept);
        btnSend = (Button) findViewById(R.id.btn_send);

        btnSend.setEnabled(false);//设置发送按键为不可见
    }


    private void initData() {

        mHandler = new ServerHandler(this, getMainLooper());
        Log.i("HYY ", "local ip=" + getLocalip());
    }

    private void initListener() {

        btnAcept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //开始监听线程，监听客户端连接
                mAcceptThread = new AcceptThread();
                running = true;
                mAcceptThread.start();
                btnSend.setEnabled(true);//设置发送按键为可见
                tvIp.setText("等待连接");
                btnAcept.setEnabled(false);

            }
        });

        //发送数据按钮
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // 发送请求数据
                try {
                    ServerActivity.SendThread st = new ServerActivity.SendThread(socket);
                    st.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void displayToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    //定义监听客户端连接的线程
    private class AcceptThread extends Thread {
        @Override
        public void run() {

            try {
                mServerSocket = new ServerSocket(40012);//建立一个ServerSocket服务器端
                socket = mServerSocket.accept();//阻塞直到有socket客户端连接
                try {
                    sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Message msg = Message.obtain();
                msg.what = ServerHandler.CONNECT_SUCCESS;
                msg.obj = socket.getInetAddress().getHostAddress();//获取客户端IP地址
                mHandler.sendMessage(msg);//返回连接成功的信息

                //开启mReceiveThread线程接收数据
                mReceiveThread = new ReceiveThread(socket);
                mReceiveThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //定义接收数据的线程
    private class ReceiveThread extends Thread {

        private InputStream is = null;
        private String read;
        //建立构造函数来获取socket对象的输入流
        ReceiveThread(Socket sk) {
            try {
                is = sk.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while (running) {
                try {
                    sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                BufferedReader br = null;
                try {
                    br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                try {
                    //读服务器端发来的数据，阻塞直到收到结束符\n或\r
                    read = br.readLine();
                    System.out.println(read);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    running = false;//防止服务器端关闭导致客户端读到空指针而导致程序崩溃
                    //发送信息通知用户客户端已关闭
                    mHandler.sendEmptyMessage(ServerHandler.DISCONNECT);
                    e.printStackTrace();
                    break;
                }

                //用Handler把读取到的信息发到主线程
                Message msg = Message.obtain();
                msg.what = ServerHandler.RECEIVE_DATA;
                msg.obj = read;
                mHandler.sendMessage(msg);
            }
        }
    }

    private class SendThread extends Thread{

        OutputStream os = null;
        //建立构造函数来获取socket对象的输出流
        SendThread(Socket socket) throws IOException {
            os = socket.getOutputStream();
        }

        @Override
        public void run() {
            super.run();
            try {
                os = socket.getOutputStream();//得到socket的输出流
                //输出EditText里面的数据，数据最后加上换行符才可以让服务器端的readline()停止阻塞
                os.write((etSend.getText().toString() + "\n").getBytes("utf-8"));
                mHandler.sendEmptyMessage(ClientActivity.ClientHandler.SEND_SATA);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);//清空消息队列，防止Handler强引用导致内存泄漏
    }

    private String getLocalip() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        assert wifiManager != null;
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        Log.i("HYY ", "int ip " + ipAddress);
        if (ipAddress == 0) return null;
        return ((ipAddress & 0xff) + "." + (ipAddress >> 8 & 0xff) + "."
                + (ipAddress >> 16 & 0xff) + "." + (ipAddress >> 24 & 0xff));
    }
}
