package vendor;

import backend.Account;
import backend.Commit;
import backend.Payment;
import broker.Bank;
import broker.BrokerServer;
import user.UserInfo;
import utils.Constants;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;


public class VendorServerClient {

    private int port;
    private Vendor vendor;

    private String brokerHostname;
    private int brokerPort;
    private Socket brokerConnection;
    private DataInputStream brokerDataInputStream;
    private DataOutputStream brokerDataOutputStream;

    public VendorServerClient(int port) {
        this.port = port;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    //region Server part
    public void initServer() {
        int connectionsCount = 0;

        try {
            ServerSocket serverSocket = new ServerSocket(port);

            while (true) {
                Socket connection = serverSocket.accept();
                System.out.println("VendorServerClient.initServer: userAddress=" + connection.getInetAddress());
                System.out.println("VendorServerClient.initServer: userPort=" + connection.getPort());
                Runnable runnable = new ConnectionRunnable(connection, ++connectionsCount);
                Thread thread = new Thread(runnable);
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //endregion

    //region Client part
    public boolean connectToBroker(String brokerHostname, int brokerPort) {
        System.out.println("VendorServerClient.connectToBroker");
        try {
            this.brokerHostname = brokerHostname;
            this.brokerPort = brokerPort;

            InetAddress brokerAddress = InetAddress.getByName(this.brokerHostname);
            this.brokerConnection = new Socket(brokerAddress, this.brokerPort);

            this.brokerDataInputStream = new DataInputStream(brokerConnection.getInputStream());
            this.brokerDataOutputStream = new DataOutputStream(brokerConnection.getOutputStream());
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean endCommunicationWithBroker() {
        System.out.println("VendorServerClient.endCommunicationWithBroker");
        try {
            this.brokerDataOutputStream.writeInt(Constants.CommunicationProtocol.END_COMMUNICATION);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean registerToBroker() {
        System.out.println("VendorServerClient.registerToBroker");
        try {
            int response;

            //send register command + all info required and wait for confirmation
            do {
                //send VENDOR_REGISTER_TO_BROKER command
                this.brokerDataOutputStream.writeInt(Constants.CommunicationProtocol.VENDOR_REGISTER_TO_BROKER);

                byte[] vendorInfo = this.vendor.getVendorInfo();
                //send length of vendorInfo message
                int vendorInfoLength = vendorInfo.length;
                this.brokerDataOutputStream.writeInt(vendorInfoLength);

                //send vendorInfo
                this.brokerDataOutputStream.write(vendorInfo);

                //wait for confirmation
                response = this.brokerDataInputStream.readInt();
                System.out.println("VendorServerClient.registerToBroker: response=" + response);
            }while(response == Constants.CommunicationProtocol.NOK);

            System.out.println("VendorServerClient.registerToBroker: register OK");

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private boolean redeem() {
        System.out.println("VendorServerClient.redeem");

        boolean result = false;
        byte[][] redeemMessages = vendor.getRedeemMessages();
        byte[] redeemMessage = null;

        try {
            for (int messageNo = 0; messageNo < redeemMessages.length; ++messageNo) {
                redeemMessage = redeemMessages[messageNo];
                System.out.println("VendorServerClient.redeem: messageNo=" + messageNo + " bytes=" + Arrays.toString(redeemMessage));

                //send REDEEM command
                this.brokerDataOutputStream.writeInt(Constants.CommunicationProtocol.REDEEM);

                //send redeem message length
                this.brokerDataOutputStream.writeInt(redeemMessage.length);

                //send redeem message
                this.brokerDataOutputStream.write(redeemMessage);

                //wait for confirmation
                int response = this.brokerDataInputStream.readInt();

                if (response == Constants.CommunicationProtocol.OK) {
                    System.out.println("VendorServerClient.redeem: redeem OK");
                    result = true;
                } else {
                    System.out.println("VendorServerClient.redeem: redeem NOK");
                    result = false;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return result;
    }
    //endregion


    public static void main(String[] args) {
        Bank bank = Bank.getInstance();

        long startTime = System.currentTimeMillis();

        //Get all this info from args or ask user via Console
        String vendorIdentity = "vendor1@gmail.com";
        long accountNo = 1000;
        long accountBalance = 9999;
        int port = 2001;

        if (args.length != 0) {
            vendorIdentity = args[0];
            accountNo = Long.parseLong(args[1]);
            accountBalance = Long.parseLong(args[2]);
            port = Integer.parseInt(args[3]);
        }

        Account vendorAccount = new Account(accountNo, accountBalance);
        bank.addUserAccount(vendorAccount);
        Vendor vendor = new Vendor(vendorIdentity);
        vendor.setAccount(vendorAccount);


        VendorServerClient vendorServerClient = new VendorServerClient(port);
        vendorServerClient.setVendor(vendor);

        //Proof of Concept: show that the client part of VendorServerClient works (it should register to the Broker and redeem the paywords)
        vendorServerClient.connectToBroker(Constants.LOCALHOST, BrokerServer.PORT);
        vendorServerClient.registerToBroker();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("VendorServerClient.run: startTime=" + startTime + " currentTime=" + System.currentTimeMillis());

                do {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } while (System.currentTimeMillis() - startTime < 35000);

                vendorServerClient.redeem();
                vendorServerClient.endCommunicationWithBroker();
                System.out.println("VendorServerClient.main: vendor accountBalance=" + Bank.getInstance().getAccountBalance(vendor.getAccount().getAccountNumber()));
            }
        });
        thread.start();

        System.out.println("VendorServerClient.main: outside the thread!");

        vendorServerClient.initServer();

    }


    private class ConnectionRunnable implements Runnable {

        private Socket connection;
        private int connectionID;

        private UserInfo userInfo;
        private int lastPaymentValue;

        public ConnectionRunnable(Socket connection, int connectionID) {
            this.connection = connection;
            this.connectionID = connectionID;
        }

        public void setUserInfo(UserInfo userInfo) {
            System.out.println("ConnectionRunnable.setUserInfo");
            this.userInfo = userInfo;
        }

        @Override
        public void run() {
            try {
                DataInputStream dataInputStream = new DataInputStream(connection.getInputStream());
                DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());

                int commandID;

                while ((commandID = dataInputStream.readInt()) != Constants.CommunicationProtocol.END_COMMUNICATION) {
                    System.out.println("VendorServerClient.ConnectionRunnable.run: commandID=" + commandID);

                    //TODO: Do here all things that the client asked
                    processCommand(commandID, dataInputStream, dataOutputStream);
                }

                System.out.println("VendorServerClient.ConnectionRunnable.run: Communication with the User ended!");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                try {
                    connection.close();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
        }

        private void processCommand(int commandID, DataInputStream dataInputStream, DataOutputStream dataOutputStream) {
            System.out.println("VendorServerClient.ConnectionRunnable.processCommand: commandID=" + commandID);
            switch (commandID) {
                case Constants.CommunicationProtocol.GET_IDENTITY:
                    sendVendorIdentity(dataOutputStream);
                    break;

                case Constants.CommunicationProtocol.COMMIT:
                    handleReceiveCommit(dataInputStream, dataOutputStream);
                    break;

                case Constants.CommunicationProtocol.MAKE_PAYMENT:
                    handleMakePayment(dataInputStream, dataOutputStream);
                    break;

                default:
                    break;
            }
        }

        private void sendVendorIdentity(DataOutputStream dataOutputStream) {
            System.out.println("ConnectionRunnable.sendVendorIdentity");

            try {
                //send the identity length
                dataOutputStream.writeInt(vendor.getIdentity().length);

                //send the identity
                dataOutputStream.write(vendor.getIdentity());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleReceiveCommit(DataInputStream dataInputStream, DataOutputStream dataOutputStream) {
            System.out.println("ConnectionRunnable.handleReceiveCommit");

            try {
                //get commit length
                int commitLength = dataInputStream.readInt();

                //get commit bytes
                byte[] bytes = new byte[commitLength];
                dataInputStream.read(bytes);
                System.out.println("ConnectionRunnable.handleReceiveCommit: commitBytes=" + Arrays.toString(bytes));
                Commit commit = new Commit(bytes);

                //Process the commit
                //get userInfo from the commit
                UserInfo userInfo = commit.getUserInfoFromCommit();
                System.out.println("ConnectionRunnable.handleReceiveCommit: userInfo=" + userInfo);
                setUserInfo(userInfo);

                //add the commit to the vendor
                boolean result = vendor.addNewCommit(userInfo, commit);

                //Proof of concept: just send the confirmation
                if (result) {
                    dataOutputStream.writeInt(Constants.CommunicationProtocol.OK);
                    System.out.println("ConnectionRunnable.handleReceiveCommit: response=OK(1)");
                } else {
                    dataOutputStream.writeInt(Constants.CommunicationProtocol.NOK);
                    System.out.println("ConnectionRunnable.handleReceiveCommit: response=NOK(0)");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleMakePayment(DataInputStream dataInputStream, DataOutputStream dataOutputStream) {
            System.out.println("ConnectionRunnable.handleMakePayment");

            try {
                int paymentLength = dataInputStream.readInt();

                byte[] paymentBytes = new byte[paymentLength];
                dataInputStream.read(paymentBytes);
                Payment payment = new Payment(paymentBytes);

                //TODO: Check if the new payment has a different value
                //If it has, then redeem the current sum and start over with the new payword value

                //Process the payment
                //add the payment to the vendor
                int result = vendor.addNewPayment(userInfo, payment);

                //Proof of concept: just send the confirmation
                switch (result) {
                    case 0: //NOK
                        dataOutputStream.writeInt(Constants.CommunicationProtocol.NOK);
                        break;
                    case 1: //OK
                        dataOutputStream.writeInt(Constants.CommunicationProtocol.OK);
                        break;
                    case 2: //FRAUD
                        dataOutputStream.writeInt(Constants.CommunicationProtocol.FRAUD);
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

}
