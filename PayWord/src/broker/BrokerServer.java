package broker;

import utils.Constants;
import vendor.VendorInfo;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;


public class BrokerServer implements Runnable {

    public static final int PORT = 1994;

    private Socket connection;
    private int connectionID;

    private Broker broker = Broker.getInstance();

    public BrokerServer(Socket connection, int connectionID) {
        this.connection = connection;
        this.connectionID = connectionID;
    }


    public static void main(String[] args) {
        int count = 0;

        try {
            ServerSocket serverSocket = new ServerSocket(PORT);

            while (true) {
                Socket connection = serverSocket.accept();
                Runnable runnable = new BrokerServer(connection, ++count);
                Thread thread = new Thread(runnable);
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            DataInputStream dataInputStream = new DataInputStream(connection.getInputStream());
            DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());

            int commandID;

            while ((commandID = dataInputStream.readInt()) != Constants.CommunicationProtocol.END_COMMUNICATION) {
                System.out.println("BrokerServer.run: commandID=" + commandID);

                //Do here all things involving the Broker depending on what the client asked
                processCommand(commandID, dataInputStream, dataOutputStream);
            }

            System.out.println("BrokerServer.run: Communication with the client ended!");

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
        System.out.println("BrokerServer.processCommand: commandID=" + commandID);
        switch (commandID) {
            case Constants.CommunicationProtocol.USER_REGISTER_TO_BROKER:
                userRegisterToBroker(dataInputStream, dataOutputStream);
                break;

            case Constants.CommunicationProtocol.VENDOR_REGISTER_TO_BROKER:
                vendorRegisterToBroker(dataInputStream, dataOutputStream);
                break;

            case Constants.CommunicationProtocol.REDEEM:
                redeem(dataInputStream, dataOutputStream);
                break;

            default:
                break;
        }
    }

    private boolean userRegisterToBroker(DataInputStream dataInputStream, DataOutputStream dataOutputStream) {
        System.out.println("BrokerServer.userRegisterToBroker");
        try {
            //wait for user personal info length
            int lengthOfUserPersonalInfo = dataInputStream.readInt();

            //wait for user personal info
            byte[] userPersonalInfo = new byte[lengthOfUserPersonalInfo];
            dataInputStream.read(userPersonalInfo);

            //send data to broker instance
            boolean resultOfRegister = broker.registerNewUser(userPersonalInfo);

            if (resultOfRegister) {
                System.out.println("BrokerServer.userRegisterToBroker: register OK");
                dataOutputStream.writeInt(Constants.CommunicationProtocol.OK);

                byte[] userCertificate = broker.getUserCertificate(broker.getUserIdentityFromPersonalInfo(userPersonalInfo));
                //System.out.println("BrokerServer.userRegisterToBroker: userCertificate=" + Arrays.toString(userCertificate));
                //send the user certificate length
                dataOutputStream.writeInt(userCertificate.length);

                //send the user certificate
                dataOutputStream.write(userCertificate);
            } else {
                System.out.println("BrokerServer.userRegisterToBroker: register NOK");
                dataOutputStream.writeInt(Constants.CommunicationProtocol.NOK);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private boolean vendorRegisterToBroker(DataInputStream dataInputStream, DataOutputStream dataOutputStream) {
        System.out.println("BrokerServer.vendorRegisterToBroker");
        try {
            //wait for user personal info length
            int lengthOfVendorInfo = dataInputStream.readInt();

            //wait for user personal info
            byte[] vendorInfo = new byte[lengthOfVendorInfo];
            dataInputStream.read(vendorInfo);

            //send data to broker instance
            boolean resultOfRegister = broker.registerNewVendor(vendorInfo);

            if (resultOfRegister) {
                System.out.println("BrokerServer.vendorRegisterToBroker: send OK");
                dataOutputStream.writeInt(Constants.CommunicationProtocol.OK);
            } else {
                System.out.println("BrokerServer.vendorRegisterToBroker: send NOK");
                dataOutputStream.writeInt(Constants.CommunicationProtocol.NOK);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private boolean redeem(DataInputStream dataInputStream, DataOutputStream dataOutputStream) {
        System.out.println("BrokerServer.redeem");
        try {
            //wait for redeem message length
            int lengthOfRedeemMessage = dataInputStream.readInt();

            //wait for redeem message bytes
            byte[] redeemMessageBytes = new byte[lengthOfRedeemMessage];
            dataInputStream.read(redeemMessageBytes);

            //Handle the redeem
            //System.out.println("BrokerServer.redeem: redeemMessage=" + Arrays.toString(redeemMessageBytes));

            boolean resultOfRedeem = broker.redeem(redeemMessageBytes);

            if (resultOfRedeem) {
                dataOutputStream.writeInt(Constants.CommunicationProtocol.OK);
            } else {
                dataOutputStream.writeInt(Constants.CommunicationProtocol.NOK);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }


        return true;
    }
}
