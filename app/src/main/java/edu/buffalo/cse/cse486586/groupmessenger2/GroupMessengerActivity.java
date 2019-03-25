package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String[] REMOTE_PORT = {"11108","11112","11116","11120","11124"};
    static final int SERVER_PORT = 10000;
    static final String key="key";
    static int message_num=0;
    static int global_proposal = 0;
    int syn = -1;
    static String delimit= ";";
    static  String myPort;
    static String failed_port=null;
    static int failed_broadcast=0;
    static final String value="value";
    static final String str = "content://edu.buffalo.cse.cse486586.groupmessenger2.provider" ;
    Uri uri = Uri.parse(str );
    Comparator<ArrayList<String>> SynComparator = new Comparator<ArrayList<String>>() {
        @Override
        public int compare(ArrayList<String> l1, ArrayList<String> l2) {
            int d1 = Integer.parseInt(l1.get(4));
            int d2 = Integer.parseInt(l2.get(4));
            if (d1<d2)
                return -1;
            else if (d1>d2)
                return 1;
            else if (d1==d2) {
                int d3 = Integer.parseInt(l1.get(l1.size()-2));
                int d4 = Integer.parseInt(l2.get(l2.size()-2));
                if (d3 < d4)
                    return -1;
                else if (d3 > d4)
                    return 1;
            }
            return 0;
        }
    };



    PriorityQueue<ArrayList<String>> pq= new PriorityQueue<ArrayList<String>>(11, SynComparator);
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

        final EditText etext = (EditText)findViewById(R.id.editText1);
        findViewById(R.id.button4).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String msg = etext.getText().toString();
                        etext.setText("");
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                    }
                });
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            try {
                //noinspection InfiniteLoopStatement
                while (true){
                    try {

                        Socket client = serverSocket.accept();
                        client.setSoTimeout(500);
                        Log.d(TAG, "Myport  " + myPort);

                        BufferedReader br = new BufferedReader(new InputStreamReader(
                                client.getInputStream()));
                        String r_data;

                        if ((r_data = br.readLine()) != null) {
                            Log.i(TAG, " S : Received msg not null through socket buffer reader is : " + r_data);
                            ArrayList<String> rec_data = new ArrayList<String>();
                            rec_data.addAll(Arrays.asList(r_data.split(delimit)));
                            if (rec_data.get(0).equals("Initial_Message")) {
                                global_proposal = global_proposal +1;
                                String proposal = rec_data.get(2) + delimit + rec_data.get(3) + delimit + String.valueOf(global_proposal);
                                PrintWriter pw = new PrintWriter(client.getOutputStream(),
                                        true);

                                pw.println(proposal);
                                Log.d(TAG, "S : Entered 1st if and sending proposal: " + proposal + "  for message" +r_data);
                                rec_data.add(String.valueOf(global_proposal));
                                rec_data.add(myPort);
                                rec_data.add("0"); //Undeliverable
                                pq.add(rec_data);
                                Log.i(TAG, " S: Printing Priotity Queue at line 178 after adding a msg ");
                                printpriorityqueue();

                            }
                            if (rec_data.get(0).equals("Agreed_Proposal")) {
                                Log.d(TAG, "S : In 2nd if:  " + rec_data.get(3));
                                PrintWriter pw = new PrintWriter(client.getOutputStream(),
                                        true);

                                pw.println("received_final_proposal");
                                Log.i(TAG,"S :  global proposal value before "+ global_proposal );
                                if (global_proposal < Integer.parseInt(rec_data.get(3))) {
                                    global_proposal = Integer.parseInt(rec_data.get(3));
                                    Log.i(TAG,"S :  global proposal value after modifying "+ global_proposal);
                                }
                                Iterator it = pq.iterator();
                                while (it.hasNext()) {
                                    ArrayList<String> ret_data = (ArrayList<String>) it.next();
                                    if (rec_data.get(1).equals(ret_data.get(2)) && rec_data.get(2).equals(ret_data.get(3))) {
                                        Log.i(TAG, " S: Printing Priotity Queue at line 188 before removing for  modifying proposal");
                                        printpriorityqueue();
                                        pq.remove(ret_data);
                                        Log.i(TAG, " S: Printing Priotity Queue at line 191 after removing for  modifying proposal");
                                        printpriorityqueue();
                                        ret_data.set(4, rec_data.get(3));
                                        ret_data.set(5, rec_data.get(4));
                                        ret_data.set(6, "1");
                                        pq.add(ret_data);
                                        Log.i(TAG, " S: Printing Priotity Queue at line 201 after adding  modifying proposal");
                                        printpriorityqueue();

                                    }
                                }
                                Log.i(TAG,"S :  entered line 203 ");
                                while (true) {
                                    Log.i(TAG,"S :  enetred line 204 while loop for delivering messages");
                                    ArrayList<String> head = pq.peek();
                                    if (head != null) {
                                        if (head.get(6).equals("1")) {
                                            Log.i(TAG,"S : enetred line 210 if loop ");

                                            syn++;
                                            Log.i(TAG, "S : Delivering the message with id " + head.get(4));
                                            pq.poll();
                                            Log.i(TAG, " S: Printing Priotity Queue at line 217 after polling");
                                            printpriorityqueue();
                                            publishProgress(head.get(1), Integer.toString(syn), head.get(4));
                                            Log.i(TAG,"S : line 216 Exited from Public Progress");

                                        }
                                        else{
                                            Log.i(TAG,"S : entered line 220 else loop ");
                                            break;
                                        }

                                    }
                                    else{
                                        Log.i(TAG,"S :  enetred line 227 else loop ");
                                        break;
                                    }
                                }
                                Log.i(TAG," S : line 230 Exitted while loop used for delivering :) ");
                            }
                            if(rec_data.get(0).equals("Failed_port")){
                                failed_broadcast=1;
                                Log.d(TAG, "S : In 3rd if: failed port  " + rec_data.get(1));
                                PrintWriter pw = new PrintWriter(client.getOutputStream(),
                                        true);

                                pw.println("received_final_failed_node");
                                Iterator it = pq.iterator();
                                Log.i(TAG, " S: line 241 Printing Priotity queue before removing failed node proposals ");
                                printpriorityqueue();
                                while (it.hasNext()) {
                                    ArrayList<String> ret_data = (ArrayList<String>) it.next();
                                    if (rec_data.get(1).equals(ret_data.get(3))) {
                                        syn++;
                                        publishProgress(ret_data.get(1), Integer.toString(syn), ret_data.get(4));
                                        pq.remove(ret_data);

                                    }
                                    Log.i(TAG, " S: line 246 Printing Priotity queue after removing failed node proposals ");
                                    printpriorityqueue();

                                }

                            }
                        }
                        Log.i(TAG, "S :  Closing Client socket" );
                        client.close();
                    }catch (SocketTimeoutException e){
                        Log.e(TAG, " Socket timeout  exception " + e.getMessage());
                        e.printStackTrace();
                    }
                    catch (IOException e){
                        Log.e(TAG, " line 222 IO Exception " + e.getMessage());
                        e.printStackTrace();
                    }catch (NullPointerException e){
                        Log.e(TAG,"line 225 Nullpointer Exception " + e.getMessage());
                        e.printStackTrace();
                    }catch (Exception e){
                        Log.e(TAG," line 228 Exception found " + e.getMessage());
                        e.printStackTrace();
                    }
                    Log.i(TAG, "S: line 244 In forever while");
                    while (true) {
                        Log.i(TAG,"S :  enetred line 204 while loop for delivering messages");
                        ArrayList<String> head = pq.peek();
                        if (head != null) {
                            if (head.get(6).equals("1")) {
                                Log.i(TAG,"S : enetred line 210 if loop ");

                                syn++;
                                Log.i(TAG, "S : Delivering the message with id " + head.get(4));
                                pq.poll();
                                Log.i(TAG, " S: Printing Priotity Queue at line 217 after polling");
                                printpriorityqueue();
                                publishProgress(head.get(1), Integer.toString(syn), head.get(4));
                                Log.i(TAG,"S : line 216 Exited from Public Progress");
                            }
                            else{
                                Log.i(TAG,"S : entered line 220 else loop ");
                                break;
                            }

                        }
                        else{
                            Log.i(TAG,"S :  enetred line 227 else loop ");
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, " line 233  S : Can't listen on ServerSocket " + e.getMessage());
                e.printStackTrace();
            }

            return null;
        }
        @Override
        protected void onProgressUpdate(String...strings) {
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strings[2]+" " + strReceived + "\n");
            ContentValues keyValueToInsert = new ContentValues();
            keyValueToInsert.put(key, strings[1]);
            keyValueToInsert.put(value, strReceived);
            uri = getContentResolver().insert(uri, keyValueToInsert);

        }
        public void printpriorityqueue() {
            Iterator it1 = pq.iterator();
            while (it1.hasNext()) {
                ArrayList<String> rdata = (ArrayList<String>) it1.next();
                Log.i(TAG, rdata.get(0) + " message " + rdata.get(1) + " msg id "+rdata.get(2) +" msg sent id "+ rdata.get(3) +" msg sequence num " +rdata.get(4) + " msg proposal finalized process id" +rdata.get(5) +" Delivery status " + rdata.get(6));
            }
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                message_num=message_num+1;
                String data = "Initial_Message"+delimit +msgs[0]+delimit+String.valueOf(message_num)+delimit+myPort;

                int proposal_max=0;
                int process_num=0;
                String msg_num_local="";


                for (String remote_port:REMOTE_PORT) {
                    if (!remote_port.equals(failed_port)) {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(remote_port));
                        socket.setSoTimeout(500);
                        Log.i(TAG, "C : Connected to server with port " + remote_port + " from port " + myPort);
                        try {
                            PrintWriter outclient = new PrintWriter(socket.getOutputStream(),
                                    true);
                            outclient.println(data);
                            Log.d(TAG, "C : sent " + data + " to  port " + remote_port);
//                            outclient.close();
                        } catch (Exception e) {
                            Log.e(TAG, "C : Client not sending msg " + e.getMessage());
                            e.printStackTrace();
                        }

                        try {
                            BufferedReader bufread = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            String rec = bufread.readLine();
                            Log.i(TAG, "C : received reply " + rec + " form port " + remote_port + "for " + data);
                            if (rec != null) {
                                Log.d(TAG, "C : proposal returned: " + rec);
                                String[] rec_proposal = rec.split(delimit);

                                Log.i(TAG, "C : line no 319 proposal max at this point is " + proposal_max + " process num " + process_num + " for msg " + rec);
                                if (proposal_max < Integer.parseInt(rec_proposal[2])) {
                                    proposal_max = Integer.parseInt(rec_proposal[2]);
                                    process_num = Integer.parseInt(remote_port);
                                    Log.i(TAG, "C : line no 319 proposal_max after modifying  is " + proposal_max + " process num " + process_num + " for msg " + rec);

                                }

                                else if (proposal_max == Integer.parseInt(rec_proposal[2]) && process_num < Integer.parseInt(remote_port)) {
                                    process_num = Integer.parseInt(remote_port);
                                }

                                msg_num_local = rec_proposal[0];
                            }
                        } catch (SocketTimeoutException e) {
                            Log.e(TAG, "C : line 377 SocketTimeout Exception " + e.getMessage());
                            e.printStackTrace();
                            failed_port = remote_port;
                        } catch (NullPointerException e) {
                            Log.e(TAG, "C : line 337 Null pointer Exception " + e.getMessage());
                            e.printStackTrace();
                            failed_port = remote_port;
                        } catch (IOException e) {
                            Log.e(TAG, "C : IOException: Client not sending final proposal " + e.getMessage());
                            e.printStackTrace();
                            failed_port = remote_port;
                        } catch (Exception e) {
                            Log.e(TAG, "C : Exception: Client not sending final proposal " + e.getMessage());
                            e.printStackTrace();
                            failed_port = remote_port;
                        }
                        socket.close();

                    }
                }
                String final_proposal="Agreed_Proposal"+delimit+msg_num_local+delimit+ myPort+delimit+String.valueOf(proposal_max)+delimit+ String.valueOf(process_num);
                for (String remote_port:REMOTE_PORT) {
                    if (!remote_port.equals(failed_port)) {

                        try {
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(remote_port));
                            socket.setSoTimeout(500);
                            PrintWriter outclient1 = new PrintWriter(socket.getOutputStream(),
                                    true);
                            outclient1.println(final_proposal);
//                            outclient1.close();
                            Log.d(TAG, "C : Sent Final proposal: " + final_proposal + " to port " + remote_port);
                            BufferedReader bufread = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            String rec = bufread.readLine();
                            if (rec.equals("C : received_final_proposal")){
                                socket.close();
                            }
                        }catch (SocketTimeoutException e){
                            Log.e(TAG,"C : line 416 SocketTimeout Exception "+e.getMessage());
                            e.printStackTrace();
                            failed_port=remote_port;

                        }catch (NullPointerException e){
                            Log.e(TAG,"C : line 337 Null pointer Exception "+e.getMessage());
                            e.printStackTrace();
                            failed_port=remote_port;

                        } catch (IOException e) {
                            Log.e(TAG, "C : IOException: Client not sending final proposal "+e.getMessage() );
                            e.printStackTrace();
                            failed_port=remote_port;
                        }catch (Exception e) {
                            Log.e(TAG, "C : Exception: Client not sending final proposal "+e.getMessage() );
                            e.printStackTrace();
                            failed_port=remote_port;
                        }

                    }
                }
                if (failed_port!=null && failed_broadcast==0){
                    Log.i(TAG, "Failed Node found and broadcasting " + failed_port );
                    failed_broadcast=1;
                    for(String remote_port:REMOTE_PORT)
                    {

                        if (!remote_port.equals(failed_port)) {

                            try {
                                String failedport_msg="Failed_port"+delimit+failed_port;
                                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                        Integer.parseInt(remote_port));
                                PrintWriter outclient2 = new PrintWriter(socket.getOutputStream(),
                                        true);
                                outclient2.println(failedport_msg);
//                                outclient2.close();
                                Log.d(TAG, "C : Sent Final proposal: " + final_proposal + " to port " + remote_port);
                                BufferedReader bufread1 = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                String rec = bufread1.readLine();
                                if (rec.equals("C : received_final_failed_node")){
                                    socket.close();
                                }
                            }catch (NullPointerException e){
                                Log.e(TAG,"C : line 446 Null pointer Exception "+e.getMessage());
                                e.printStackTrace();
                            }
                            catch (Exception e){
                                Log.e(TAG,"C : line 450  Exception raised while sending failed node "+e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "C : line 326 : Multicasting Initial Message Failed ClientTask UnknownHostException " + e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(TAG, "C : line 328 : ClientTask socket IOException " + e.getMessage());
                e.printStackTrace();

            } catch (Exception e){
                Log.e(TAG, "C : line 332 :Exception " + e.getMessage());
                e.printStackTrace();
            }
            return null;

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}

/*References
https://docs.oracle.com/javase/8/docs/api/java/util/PriorityQueue.html
https://docs.oracle.com/javase/8/docs/api/java/net/Socket.html#setSoTimeout-int-4
https://developer.android.com/reference/java/net/Socket
https://developer.android.com/reference/java/lang/Exception
https://docs.oracle.com/javase/8/docs/api/java/net/Socket.html
https://docs.oracle.com/javase/8/docs/api/java/util/ArrayList.html
https://docs.oracle.com/javase/8/docs/api/java/lang/String.html
https://docs.oracle.com/javase/8/docs/api/java/io/BufferedReader.html
https://docs.oracle.com/javase/8/docs/api/java/io/PrintWriter.html
 */