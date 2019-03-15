package com.ishare.androidsocket;

import android.content.Context;
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
import java.lang.ref.WeakReference;
import java.net.Socket;

/**
 * 客户端代码
 * Created by huangyouyang on 2017/6/29.
 */

public class ClientActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView tv;
    private EditText etSend;
    private EditText etIP;
    private Button btnSend;
    private Button btnStart;
    private Button btnStop;

    private Handler mHandler;
    private Socket socket;
    private String str = "";
    boolean running = false;
    
    private StartThread st;
    private ReceiveThread rt;

    
    class ClientHandler extends Handler {

        static final int CONNECT_SUCCESS = 1;
        static final int DISCONNECT = 2;
        static final int RECEIVE_DATA = 3;
        static final int SEND_SATA = 4;
        
        final WeakReference<Context> mContext;
        ClientHandler(Context context, Looper looper) {
            super(looper);
            this.mContext = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CONNECT_SUCCESS:
                    displayToast("连接成功");
                    break;

                case DISCONNECT:
                    displayToast("连接已断开");
                    tv.setText(null);
                    setButtonOnStartState(true);//设置按键状态为可开始
                    break;

                case RECEIVE_DATA:
                    String str = (String) msg.obj;
                    System.out.println(msg.obj);
                    tv.setText(str);
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
        setContentView(R.layout.activity_client);

        initView();
        initData();
        initListener();
    }

    private void initView() {

        tv = (TextView) findViewById(R.id.tv);
        etSend = (EditText) findViewById(R.id.et_send);
        etIP = (EditText) findViewById(R.id.et_ip);

        btnSend = (Button) findViewById(R.id.btn_send);
        btnStart = (Button) findViewById(R.id.btn_start);
        btnStop = (Button) findViewById(R.id.btn_stop);
    }

    private void initData() {

        setButtonOnStartState(true);  //设置按键状态为可开始连接
        mHandler = new ClientHandler(this, getMainLooper());  //实例化Handler，用于进程间的通信
    }

    private void initListener() {

        btnSend.setOnClickListener(this);
        btnStart.setOnClickListener(this);
        btnStop.setOnClickListener(this);
    }

    private void displayToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void setButtonOnStartState(boolean flag) {//设置按钮的状态

        btnSend.setEnabled(!flag);
        btnStop.setEnabled(!flag);
        btnStart.setEnabled(flag);
        etIP.setEnabled(flag);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start:
                //按下开始连接按键即开始StartThread线程
                st = new StartThread();
                st.start();
                setButtonOnStartState(false);  //设置按键状态为不可开始连接
                break;

            case R.id.btn_send:
                // 发送请求数据
                try {
                    SendThread st = new SendThread(socket);
                    st.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

            case R.id.btn_stop:
                running = false;
                setButtonOnStartState(true);//设置按键状态为不可开始连接
                try {
                    socket.close();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    displayToast("未连接成功");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    /**
     * socket连接子线程
     */
    private class StartThread extends Thread {
        @Override
        public void run() {

            try {
                socket = new Socket(etIP.getText().toString(), 40012);  //连接服务端的IP
                //启动接收数据的线程
                rt = new ReceiveThread(socket);
                rt.start();
                running = true;
                if (socket.isConnected()) { //成功连接获取socket对象则发送成功消息
                    mHandler.sendEmptyMessage(ClientHandler.CONNECT_SUCCESS);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * socket接收数据子线程
     */
    private class ReceiveThread extends Thread {

        private InputStream is;
        //建立构造函数来获取socket对象的输入流
        ReceiveThread(Socket socket) throws IOException {
            is = socket.getInputStream();
        }

        @Override
        public void run() {

            while (running) {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                try {
                    //读服务器端发来的数据，阻塞直到收到结束符\n或\r
                    str = br.readLine();
                    Log.i("HYY ", "receive str == "+str);
                } catch (NullPointerException e) {
                    running = false;//防止服务器端关闭导致客户端读到空指针而导致程序崩溃
                    //发送信息通知用户客户端已关闭
                    mHandler.sendEmptyMessage(ClientHandler.DISCONNECT);
                    e.printStackTrace();
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //用Handler把读取到的信息发到主线程
                Message msg = Message.obtain();
                msg.what = ClientHandler.RECEIVE_DATA;
                msg.obj = str;
                mHandler.sendMessage(msg);
               
                try {
                    sleep(400);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            
            //发送信息通知用户客户端已关闭
            mHandler.sendEmptyMessage(ClientHandler.DISCONNECT);
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
                mHandler.sendEmptyMessage(ClientHandler.SEND_SATA);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
