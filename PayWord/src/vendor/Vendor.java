package vendor;

import backend.Commit;
import backend.Payment;
import backend.Payword;
import broker.Broker;
import backend.Account;
import user.User;
import user.UserInfo;
import utils.*;

import java.nio.ByteBuffer;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;


public class Vendor {

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private byte[] identity;
    private Account account;

    private Map<UserInfo, Commit> userCommitments;
    private Map<UserInfo, List<Payment>> userPayments;

    private List<UserInfo> allUsers;

    public Vendor() {
        KeyPair keyPair = Crypto.getRSAKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        initIdentity();
        this.account = new Account();

        this.userCommitments = new HashMap<>();
        this.userPayments = new HashMap<>();

        this.allUsers = new ArrayList<>();
    }

    public Vendor(String identity) {
        KeyPair keyPair = Crypto.getRSAKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        initIdentity();

        //Copy the bytes from the given String
        byte[] stringIdentityBytes = identity.getBytes();
        for (int i = 0; i < stringIdentityBytes.length; ++i) {
            this.identity[i] = stringIdentityBytes[i];
        }
        this.account = new Account();

        this.userCommitments = new HashMap<>();
        this.userPayments = new HashMap<>();

        this.allUsers = new ArrayList<>();
    }

    private void initIdentity() {
        this.identity = new byte[Constants.IDENTITY_NO_OF_BITS / 8];
        for (int i = 0; i < Constants.IDENTITY_NO_OF_BITS / 8; ++i) {
            this.identity[i] = 0;
        }
    }

    public byte[] getIdentity() {
        return this.identity;
    }

    public PublicKey getPublicKey() {
        return this.publicKey;
    }

    private PrivateKey getPrivateKey() {
        return this.privateKey;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public Account getAccount() {
        return this.account;
    }

    private UserInfo getUserWithIdentity(byte[] userIdentity) {
        for (UserInfo userInfo : allUsers)
            if (Arrays.equals(userInfo.getIdentity(), userIdentity))
                return userInfo;
        return null;
    }

    /**
     * Get the array of byte that represents the info of the vendor
     * The format of the array is:
     *  - 4 bytes for the length of the identity
     *  - the identity bytes
     *  - 4 bytes for the length of the public key encoded value
     *  - the public key encoded value
     *  - 8 bytes for the account number
     * @return the array of bytes that represents the info of the vendor
     */
    public byte[] getVendorInfo() {
        int size = Constants.INT_NO_OF_BYTES + identity.length + Constants.INT_NO_OF_BYTES + publicKey.getEncoded().length + 2 * Constants.LONG_NO_OF_BYTES;
        byte[] vendorInfo = new byte[size];

        int index = 0;

        //System.out.println("Vendor.getVendorInfo: lengthOfIdentity=" + identity.length);
        //copy identity length
        byte[] lengthOfIdentity = ByteBuffer.allocate(Constants.INT_NO_OF_BYTES).putInt(identity.length).array();
        for (int i = 0; i < Constants.INT_NO_OF_BYTES; ++i, ++index) {
            vendorInfo[index] = lengthOfIdentity[i];
        }

        //copy identity
        for (int i = 0; i < identity.length; ++i, ++index) {
            vendorInfo[index] = identity[i];
        }

        byte[] publicKeyEncoded = publicKey.getEncoded();


        //System.out.println("Vendor.getVendorInfo: publicKeyLength=" + publicKeyEncoded.length);
        //copy publicKey length
        byte[] lengthOfPublicKey = ByteBuffer.allocate(Constants.INT_NO_OF_BYTES).putInt(publicKeyEncoded.length).array();
        for (int i = 0; i < Constants.INT_NO_OF_BYTES; ++i, ++index) {
            vendorInfo[index] = lengthOfPublicKey[i];
        }

        //System.out.println("Vendor.getVendorInfo: vendorPublicKey=" + ((RSAPublicKey) publicKey).getModulus().toString());
        //copy publicKey
        for (int i = 0; i < publicKeyEncoded.length; ++i, ++index) {
            vendorInfo[index] = publicKeyEncoded[i];
        }

        //System.out.println("Vendor.getVendorInfo: accountNumber=" + getAccount().getAccountNumber());
        //copy account number
        byte[] accountNumberBytes = ByteBuffer.allocate(Constants.LONG_NO_OF_BYTES).putLong(getAccount().getAccountNumber()).array();
        for (int i = 0; i < Constants.LONG_NO_OF_BYTES; ++i, ++index) {
            vendorInfo[index] = accountNumberBytes[i];
        }

        return vendorInfo;
    }

    /**
     * Add a new commit from a user
     * @param userInfo information about the user
     * @param commit the commit
     * @return
     */
    public boolean addNewCommit(UserInfo userInfo, Commit commit) {
        //check U's signature on commit
        //get the unsigned part
        int size = 932; //the no of bytes of the message without the signed hash
        byte[] message = Arrays.copyOfRange(commit.getBytes(), 0, size);

        //get the signed hash
        byte[] signedHash = Arrays.copyOfRange(commit.getBytes(), size, commit.getBytes().length);

        Signature signature = null;
        boolean result = false;
        try {
            signature = Signature.getInstance("SHA1WithRSA");
            signature.initVerify(userInfo.getPublicKey());
            signature.update(message);
            result = signature.verify(signedHash);
            System.out.println("Vendor.addNewCommit: verify User signature on commit result: " + result);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

        if (result) {
            //check B's signature on C(U)
            //extract C(U) from the commit
            byte[] userCertificate = Arrays.copyOfRange(commit.getBytes(), this.identity.length, this.identity.length + 732);

            //get the unsigned part
            byte[] unsignedPart = Arrays.copyOfRange(userCertificate, 0, 604);

            //get the signed hash
            signedHash = Arrays.copyOfRange(userCertificate, 604, userCertificate.length);

            //check the Broker signature on the user certificate
            signature = null;
            try {
                //get the broker signature from the userCertificate
                byte[] brokerSignatureBytes = Arrays.copyOfRange(userCertificate, 2 * (Constants.IDENTITY_NO_OF_BITS / 8), 2 * (Constants.IDENTITY_NO_OF_BITS / 8) + 162);
                PublicKey brokerPublicKey = null;
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(brokerSignatureBytes);
                KeyFactory keyFactory = null;
                try {
                    keyFactory = KeyFactory.getInstance("RSA");
                    brokerPublicKey = keyFactory.generatePublic(keySpec);

                    //System.out.println("Vendor.addNewCommit: brokerPublicKey=" + ((RSAPublicKey) brokerPublicKey).getModulus().toString());
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (InvalidKeySpecException e) {
                    e.printStackTrace();
                }

                signature = Signature.getInstance("SHA1WithRSA");
                signature.initVerify(brokerPublicKey);
                signature.update(unsignedPart);
                result = signature.verify(signedHash);
                System.out.println("Vendor.addNewCommit: verify Broker signature on User certificate result: " + result);
                if (result) {
                    //TODO: Check if the certificate is not expired

                    userCommitments.put(userInfo, commit);
                }

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (SignatureException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            }

        }

        return result;
    }

    public int addNewPayment(UserInfo userInfo, Payment payment) {

        System.out.println("Vendor.addNewPayment: paymentNo=" + payment.getPaywordNo());

        if (userPayments.get(userInfo) != null) {
            List<Payment> listOfPayments = userPayments.get(userInfo);

            //check if payment is authentic: eg: h(ci) = c(i-1)
            Payword ci = payment.getPayword();
            Payword ciHash = new Payword(ci);
            Payword ci_1 = listOfPayments.get(listOfPayments.size() - 1).getPayword();
            if (ciHash.equals(ci_1)) {
                System.out.println("Vendor.addNewPayment: authentic payment! => add to the list of payments");

                listOfPayments.add(payment);
                userPayments.remove(userInfo);
                userPayments.put(userInfo, listOfPayments);

                return 1;
            }
            else {
                System.out.println("Vendor.addNewPayment: not authentic payment! => don't add to the list of payments");

                //TODO: do something to stop the service and force the user to redo all steps: generate commit and new payment
                //TODO: redeem what the user paid so far ??
                return 2;
            }
        }
        else {
            List<Payment> listOfPayments = new ArrayList<>();
            listOfPayments.add(payment);
            userPayments.put(userInfo, listOfPayments);

            return 1;
        }
    }

    /**
     * This is the third step in the scheme
     * The Vendor has to send to the Broker a message containing: commit(U), c(l), l, where l is the last index of a payment
     * @return the redeem messages
     */
    public byte[][] getRedeemMessages() {
        byte[][] redeemMessages = new byte[userCommitments.keySet().size()][];
        byte[] message = null;
        int messageNo = 0;

        //redeem all payments done by all users
        for (UserInfo userInfo : userCommitments.keySet()) {

            List<Payment> paymentList = userPayments.get(userInfo);
            int size = userCommitments.get(userInfo).getBytes().length + paymentList.get(paymentList.size() - 1).getBytes().length;
            message = new byte[size];

            int index = 0;

            //copy the commit
            byte[] commitBytes = userCommitments.get(userInfo).getBytes();
            for (int i = 0; i < commitBytes.length; ++i, ++index)
                message[index] = commitBytes[i];

            //copy the last payword received and its index
            byte[] lastPaywordBytes = paymentList.get(paymentList.size() - 1).getBytes();

            for (int i = 0; i < lastPaywordBytes.length; ++i, ++index)
                message[index] = lastPaywordBytes[i];

            System.out.println("Vendor.getRedeemMessages: lastPaywordBytes=" + Arrays.toString(lastPaywordBytes));

            redeemMessages[messageNo++] = message;
        }

        return redeemMessages;
    }
}
