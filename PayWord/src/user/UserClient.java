package user;

import backend.Account;
import backend.Commit;
import backend.Payment;
import broker.Bank;
import broker.BrokerServer;
import utils.Constants;
import vendor.VendorInfo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;


public class UserClient {

    //region Declarations
    private User user;
    private String brokerHostname;
    private int brokerPort;
    private Socket brokerConnection;
    private DataInputStream brokerDataInputStream;
    private DataOutputStream brokerDataOutputStream;

    private byte[] vendorIdentity;
    private Socket vendorConnection;
    private DataInputStream vendorDataInputStream;
    private DataOutputStream vendorDataOutputStream;
    //endregion

    //region Broker
    public boolean connectToBroker(String brokerHostname, int brokerPort) {
        System.out.println("UserClient.connectToBroker");
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
        System.out.println("UserClient.endCommunicationWithBroker");
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

    public boolean registerToBroker(long creditLimit) {
        System.out.println("UserClient.registerToBroker");
        try {
            int response;

            //send register command + all info required and wait for confirmation
            do {
                //send USER_REGISTER_TO_BROKER command
                this.brokerDataOutputStream.writeInt(Constants.CommunicationProtocol.USER_REGISTER_TO_BROKER);

                byte[] personalInfo = this.user.getPersonalInfo(creditLimit);
                //send length of personal info message
                int personalInfoLength = personalInfo.length;
                this.brokerDataOutputStream.writeInt(personalInfoLength);

                //send personal info
                this.brokerDataOutputStream.write(personalInfo);

                //wait for confirmation
                response = this.brokerDataInputStream.readInt();
                System.out.println("UserClient.registerToBroker: response=" + response);
            }while(response == Constants.CommunicationProtocol.NOK);

            //get the user certificate length
            int userCertificateLength = this.brokerDataInputStream.readInt();

            //get the user certificate
            byte[] userCertificate = new byte[userCertificateLength];
            this.brokerDataInputStream.read(userCertificate);
            this.user.setUserCertificate(userCertificate);
            System.out.println("UserClient.registerToBroker: userCertificate=" + Arrays.toString(this.user.getUserCertificate()));
            System.out.println("UserClient.registerToBroker: userCertificate=" + Arrays.toString(userCertificate));
            System.out.println("UserClient.registerToBroker: userCertificate=" + Arrays.toString(this.user.getUserCertificate()));

            System.out.println("UserClient.registerToBroker: register OK");

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
    //endregion

    //region Vendor
    public boolean connectToVendor(String vendorHostname, int vendorPort) {
        System.out.println("UserClient.connectToVendor");
        try {
            InetAddress vendorAddress = InetAddress.getByName(vendorHostname);
            this.vendorConnection = new Socket(vendorAddress, vendorPort);

            this.vendorDataInputStream = new DataInputStream(vendorConnection.getInputStream());
            this.vendorDataOutputStream = new DataOutputStream(vendorConnection.getOutputStream());
            System.out.println("UserClient.connectToVendor: connection started");
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean endCommunicationWithVendor() {
        System.out.println("UserClient.endCommunicationWithVendor");
        try {
            this.vendorDataOutputStream.writeInt(Constants.CommunicationProtocol.END_COMMUNICATION);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean getVendorIdentity() {
        System.out.println("UserClient.getVendorIdentity");
        try {
            //send GET_IDENTITY command
            this.vendorDataOutputStream.writeInt(Constants.CommunicationProtocol.GET_IDENTITY);

            //get the identity length
            int vendorIdentityLength = this.vendorDataInputStream.readInt();
            this.vendorIdentity = new byte[vendorIdentityLength]; //should be 128 bits (1024 bits for the identity)

            //get the identity
            this.vendorDataInputStream.read(this.vendorIdentity);
            System.out.println("UserClient.getVendorIdentity: vendorIdentity=" + Arrays.toString(this.vendorIdentity));

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean makePaymentToVendor() {
        System.out.println("UserClient.makePaymentToVendor");
        try {
            int paymentNo;

            VendorInfo vendorInfo = new VendorInfo();
            vendorInfo.setIdentity(vendorIdentity);

            if (user.isFirstPayment(vendorInfo)) {
                paymentNo = 0;

                //generate the new hash chain for this vendor
                user.generateNewHashChains(vendorInfo);

                //compute the commit(V)
                Commit commit = user.computeCommitment(vendorInfo);
                //System.out.println("UserClient.makePaymentToVendor: commitBytes=" + Arrays.toString(commit.getBytes()));

                //test
                UserInfo userInfo = commit.getUserInfoFromCommit();
                //System.out.println("UserClient.makePaymentToVendor: userInfo=" + userInfo);

                //send the commit to the vendor
                boolean sendCommitResponse;
                do {
                    sendCommitResponse = sendCommit(commit);
                }while(!sendCommitResponse);
                System.out.println("UserClient.makePaymentToVendor: sendCommit finished with success!");
            }
            else {
                paymentNo = user.getVendorNoOfPayments(vendorInfo);
            }

            //send make payment command and wait for confirmation
            int response;
            do {
                //construct and send the payment to the vendor
                //construct the Payment
                Payment payment = user.constructPayment(vendorInfo, paymentNo, Constants.PaywordValue.ONE);
                response = sendPayment(payment);

                //response = sendPayment(payment); //for testing purposes: test if the vendor reacts to fraud attempts

                if (response == Constants.CommunicationProtocol.OK) {
                    user.addPaymentToListOfPayments(vendorInfo, payment);
                }

            }while(response == Constants.CommunicationProtocol.NOK && response != Constants.CommunicationProtocol.FRAUD);

            if (response != Constants.CommunicationProtocol.FRAUD)
                System.out.println("UserClient.makePaymentToVendor: payment DONE");
            else
                System.out.println("UserClient.makePaymentToVendor: FRAUD ATTEMPT");

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean payValue(long value) {
        for (long i = 0; i < value; ++i) {
            if (this.makePaymentToVendor() == false)
                return false;
        }

        return true;
    }

    private boolean sendCommit(Commit commit) throws IOException {
        System.out.println("UserClient.sendCommit");

        //send COMMIT command to Vendor
        this.vendorDataOutputStream.writeInt(Constants.CommunicationProtocol.COMMIT);

        //send commit length
        this.vendorDataOutputStream.writeInt(commit.getBytes().length);
        //System.out.println("UserClient.sendCommit: commitLength=" + commit.getBytes().length);

        //send commit bytes
        this.vendorDataOutputStream.write(commit.getBytes());
        //System.out.println("UserClient.sendCommit: commitBytes=" + Arrays.toString(commit.getBytes()));

        //wait for confirmation
        int response = this.vendorDataInputStream.readInt();
        System.out.println("UserClient.sendCommit: response=" + response);

        if (response == Constants.CommunicationProtocol.OK)
            return true;

        return false;
    }

    private int sendPayment(Payment payment) throws IOException {
        System.out.println("UserClient.sendPayment");

        //send MAKE_PAYMENT command
        this.vendorDataOutputStream.writeInt(Constants.CommunicationProtocol.MAKE_PAYMENT);

        //send payment length
        this.vendorDataOutputStream.writeInt(payment.getBytes().length);

        //send payment bytes
        this.vendorDataOutputStream.write(payment.getBytes());

        //wait for confirmation
        int response = this.vendorDataInputStream.readInt();
        System.out.println("UserClient.sendPayment: response=" + response);

        return response;
    }
    //endregion

    public void setUser(User user) {
        this.user = user;
    }


    public static void main(String[] args) {
        Bank bank = Bank.getInstance();

        //Get all this info from args or ask user via Console
        String userIdentity = "user@gmail.com";
        long accountNo = 1;
        long accountBalance = 1000;
        long creditLimit = 1000;

        if (args.length != 0) {
            userIdentity = args[0];
            accountNo = Long.parseLong(args[1]);
            accountBalance = Long.parseLong(args[2]);
            creditLimit = Long.parseLong(args[3]);
        }

        Account userAccount = new Account(accountNo, accountBalance);
        bank.addUserAccount(userAccount);
        User user = new User(userIdentity);
        user.setAccount(userAccount);

        UserClient userClient = new UserClient();
        userClient.connectToBroker(Constants.LOCALHOST, BrokerServer.PORT);
        userClient.setUser(user);

        //register to the Broker
        userClient.registerToBroker(creditLimit);

        //end communication with the Broker
        userClient.endCommunicationWithBroker();


        //communicate with a Vendor; the Vendor will be given by its port
        //TODO: Get the Vendor by asking the user on the Console
        int vendorPort = 2001;
        userClient.connectToVendor(Constants.LOCALHOST, vendorPort);
        userClient.getVendorIdentity();
        userClient.payValue(5);
        userClient.endCommunicationWithVendor();

        //TODO: If the user wants to make a new payment the same day, the Vendor should be able to handle it
        //for now it doesn't, it looses the UserInfo that it saves within the object that handles one connection
        //userClient.connectToVendor(Constants.LOCALHOST, vendorPort);
        //userClient.getVendorIdentity();
        //userClient.makePaymentToVendor();
        //userClient.makePaymentToVendor();
        //userClient.endCommunicationWithVendor();
    }

}
