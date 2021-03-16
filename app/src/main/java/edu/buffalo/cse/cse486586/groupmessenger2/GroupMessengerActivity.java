package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Iterator;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeoutException;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static HashSet<String> crashed = new HashSet<String>();
    static final String[] list = {REMOTE_PORT0, REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3, REMOTE_PORT4};
    static Set<String> consensus = new HashSet<String>(Arrays.asList(list));
    ConcurrentSkipListMap<Float, String[]> messages = new ConcurrentSkipListMap<Float, String[]>();
    ConcurrentHashMap<Float, String> ID_Msg = new ConcurrentHashMap<Float, String>();
    static final int SERVER_PORT = 10000;
    int send_counter = 0;
    int proposal = 0;
    float final_proposal;
    int accepted = 0;
    float accept;
    int key = 0;
    private ContentResolver mContentResolver;
    String myPort = "0";
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        mContentResolver = getContentResolver();
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        Button button = (Button) findViewById(R.id.button4);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final EditText editText = (EditText) findViewById(R.id.editText1);

                String msg = editText.getText().toString() + "\n";
                editText.setText("");
                TextView tv = (TextView) findViewById(R.id.textView1);
                tv.append("\t" + msg);


                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        private synchronized float proposal(String[] msg, String myPort, String sentPort) {
            proposal = Math.max(proposal, accepted) + 1;
            final_proposal = Float.parseFloat(proposal + "." + myPort);
            Log.e(TAG, "Adding proposal" + proposal);
            messages.put(final_proposal, msg);
            ID_Msg.put(final_proposal, sentPort);
            return final_proposal;
        }


        private synchronized void finalize(float proposed, float agreed) {
            Log.e(TAG, "THis is proposed" + proposed + " This is agreed " + agreed);
            String[] msg = messages.get(proposed);

            Iterator<Float> itr = messages.keySet().iterator();
            while (itr.hasNext()) {
                Log.e(TAG, Float.toString(itr.next()));
            }
            Iterator<String[]> itr1 = messages.values().iterator();
            while (itr1.hasNext()) {
                Log.e(TAG, "Messages: " + Arrays.toString(itr1.next()));
            }


            if (msg != null && !Arrays.asList(msg).isEmpty()) {
                accepted = Math.max(accepted, (int) Math.floor(agreed));
                msg[1] = "True";
                messages.remove(proposed);
                messages.put(agreed, msg);
                String polled[];
                float k;
                Log.e(TAG, "CRASHED PROCESSES: " + crashed);
                Log.e(TAG, "ID_MSG " + ID_Msg);

                if (!messages.isEmpty()) {
                    polled = messages.firstEntry().getValue();
                    k = messages.firstEntry().getKey();
                    while (!(polled == null)) {
                        k = messages.firstEntry().getKey();
                        if (!crashed.isEmpty() && crashed.contains(ID_Msg.get(k))) {
                            messages.remove(k);
                            if (!messages.isEmpty())
                                polled = messages.firstEntry().getValue();
                            else
                                polled = null;
                        } else if (polled[1].equals("True")) {

                            messages.remove(k);
                            ContentValues mContentValues = new ContentValues();
                            mContentValues.put(KEY_FIELD, Integer.toString(key));
                            mContentValues.put(VALUE_FIELD, polled[0]);
                            mContentResolver.insert(mUri, mContentValues);
                            key += 1;
                            if (!messages.isEmpty())
                                polled = messages.firstEntry().getValue();
                            else
                                polled = null;
                        } else {
                            polled = null;
                        }
                    }
                }
            }
        }

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String sentPort = null;
            while (!Thread.interrupted()) {
                try {
                    Socket s = serverSocket.accept();
                    InputStreamReader inputStreamReader = new InputStreamReader(s.getInputStream());
                    String i = new BufferedReader(inputStreamReader).readLine();

                    Log.e(TAG, "REceived : " + i);
                    if (i == null)
                        continue;
                    int index = i.indexOf(":");
                    int index1 = index;
                    String msg_id = i.substring(0, index);

                    String sendPort_Msg = i.substring(index + 1);
                    index = sendPort_Msg.indexOf(":");
                    sentPort = sendPort_Msg.substring(0, index);
                    String crashedMsg = sendPort_Msg.substring(index + 1);
                    index = crashedMsg.indexOf(":");
                    String crashedPort = crashedMsg.substring(0, index);
                    Log.e(TAG, "Going to insert crash at receive" + crashed + "Port " + crashedPort);
                    if (crashed.isEmpty() && !crashedPort.equals("[]")) {
                        crashedPort = crashedPort.substring(1, crashedPort.length() - 1);
                        crashed.add(crashedPort);
                        Log.e(TAG, "Inserted crash at receive " + crashed);
                    }
                    String[] message = {crashedMsg.substring(index + 1), "False"};
                    float proposed = proposal(message, myPort, sentPort);

                    PrintWriter printWriter = new PrintWriter(s.getOutputStream());

                    printWriter.println(proposed);
                    printWriter.flush();


                    String i1 = new BufferedReader(inputStreamReader).readLine();
                    if (i1 == null)
                        continue;
                    Log.e(TAG, "RECEIVED FINAL " + i1);
                    String agreedMsg = i1.substring(index1 + 1);
                    int ind = agreedMsg.indexOf(":");
                    float agreed = Float.parseFloat(agreedMsg.substring(0, ind));
                    String portmsg = agreedMsg.substring(ind + 1);
                    ind = portmsg.indexOf(":");
                    float proposal_port = Float.parseFloat(portmsg.substring(0, ind));
                    String crashedMsg2 = portmsg.substring(ind + 1);
                    ind = crashedMsg2.indexOf(":");
                    String crashedPort2 = crashedMsg2.substring(0, ind);
                    Log.e(TAG, "Going to insert crash" + crashed + "Port " + crashedPort2);
                    if (crashed.isEmpty() && !crashedPort2.equals("[]")) {
                        crashedPort2 = crashedPort2.substring(1, crashedPort2.length() - 1);
                        crashed.add(crashedPort2);
                        Log.e(TAG, "Inserted crash " + crashed);
                    }
                    finalize(proposal_port, agreed);
                        /*if (!TextUtils.isEmpty(portmsg.substring(ind+1)))
                            publishProgress(portmsg.substring(ind+1));*/


                    printWriter.println("Success!");
                    printWriter.flush();
                    s.close();


                }/*catch (NullPointerException ex){
                        consensus.remove(sentPort);
                        crashed.add(remotePort[i]);
                        continue;
                    }*/ catch (SocketTimeoutException e) {
                    Log.e(TAG, "Process DIED!!!! " + sentPort);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */

            String strReceived = strings[0].trim();

            TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append("\t" + strReceived);
            tv.append("\n");


            String filename = "GroupMessengerOutput";
            String string = strReceived + "\n";
            FileOutputStream outputStream;

            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }

            return;
        }
    }

    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            Set<String> localList = new HashSet<String>();
            Socket[] sockets = new Socket[5];

            HashMap<String, Float> proposals = new HashMap<String, Float>();

            send_counter++;
            String[] remotePort = {REMOTE_PORT0, REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3, REMOTE_PORT4};
            System.out.println("Sending messages to all!!");
            for (int i = 0; i < 5; i++) {
                try {

                    sockets[i] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort[i]));
                    sockets[i].setSoTimeout(10000);
                    PrintWriter printWriter = new PrintWriter(sockets[i].getOutputStream());
                    InputStreamReader inputStreamReader = new InputStreamReader(sockets[i].getInputStream());
                    Log.e(TAG, "Writing message " + send_counter + ":" + myPort + ":" + crashed + ":" + msgs[0]);
                    printWriter.println(send_counter + ":" + myPort + ":" + crashed + ":" + msgs[0]);
                    printWriter.flush();
                    String reply = new BufferedReader(inputStreamReader).readLine();
                    proposals.put(remotePort[i], Float.parseFloat(reply));
                    localList.add(remotePort[i]);

                } catch (NullPointerException ex) {
                    Log.e(TAG, "Null pointer msg " + remotePort[i]);
                    consensus.remove(remotePort[i]);
                    crashed.add(remotePort[i]);

                } catch (UnknownHostException ex) {
                    ex.printStackTrace();
                } catch (SocketTimeoutException ex) {
                    Log.e(TAG, "Sockettimeout msg " + remotePort[i]);
                    consensus.remove(remotePort[i]);
                    crashed.add(remotePort[i]);

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            if (localList.equals(consensus)) {
                for (int i = 0; i < 5; i++) {
                    try {
                        sockets[i].setSoTimeout(10000);
                        PrintWriter printWriter = new PrintWriter(sockets[i].getOutputStream());
                        InputStreamReader inputStreamReader = new InputStreamReader(sockets[i].getInputStream());
                        accept = Collections.max(proposals.values());

                        String msgToSend = "0:" + accept + ":" + proposals.get(remotePort[i]) + ":" +
                                crashed + ":" + ":" + msgs[0];


                        Log.e(TAG, "Writing final " + msgToSend);
                        printWriter.println(msgToSend);
                        printWriter.flush();
                        String reply2 = new BufferedReader(inputStreamReader).readLine();

                    } catch (NullPointerException ex) {
                        Log.e(TAG, "Null pointer final " + remotePort[i]);
                        consensus.remove(remotePort[i]);
                        crashed.add(remotePort[i]);

                    } catch (UnknownHostException ex) {
                        ex.printStackTrace();
                    } catch (SocketTimeoutException ex) {
                        Log.e(TAG, "Sockettimeout final " + remotePort[i]);
                        consensus.remove(remotePort[i]);
                        crashed.add(remotePort[i]);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            proposals.clear();

                return null;
            }
        }
    }
