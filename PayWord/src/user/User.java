package user;

import backend.Account;
import backend.Commit;
import backend.Payment;
import backend.Payword;
import broker.Broker;
import utils.*;
import vendor.VendorInfo;

import java.nio.ByteBuffer;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;


public class User {

    private Broker broker;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private byte[] identity;
    private Account account;

    private byte[] userCertificate;

    private int hashChainLength;
    private Map<VendorInfo, List<Payment>> paymentsDone;
    private Map<VendorInfo, List<List<Payword>>> hashChains1;
    private Map<VendorInfo, List<List<Payword>>> hashChains5;
    private Map<VendorInfo, List<List<Payword>>> hashChains10;

    public User() {
        broker = Broker.getInstance();
        KeyPair keyPair = Crypto.getRSAKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        initIdentity();
        this.account = new Account();

        this.hashChainLength = 10000;
        this.paymentsDone = new HashMap<>();
        this.hashChains1 = new HashMap<>();
        this.hashChains5 = new HashMap<>();
        this.hashChains10 = new HashMap<>();
    }

    public User(String identity) {
        broker = Broker.getInstance();
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

        this.hashChainLength = 10000;
        this.paymentsDone = new HashMap<>();
        this.hashChains1 = new HashMap<>();
        this.hashChains5 = new HashMap<>();
        this.hashChains10 = new HashMap<>();
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

    public byte[] getUserCertificate() {
        return userCertificate;
    }

    public void setUserCertificate(byte[] userCertificate) {
        this.userCertificate = userCertificate;
    }

    /**
     * Get the array of byte that represents the personal info of the user
     * The format of the array is:
     *  - 4 bytes for the length of the identity
     *  - the identity bytes
     *  - 4 bytes for the length of the public key encoded value
     *  - the public key encoded value
     *  - 8 bytes for the account number
     *  - 8 bytes for the credit limit
     * @param creditLimit the credit limit
     * @return the array of bytes that represents the personal info of the user
     */
    public byte[] getPersonalInfo(long creditLimit) {
        int size = Constants.INT_NO_OF_BYTES + identity.length + Constants.INT_NO_OF_BYTES + publicKey.getEncoded().length + 2 * Constants.LONG_NO_OF_BYTES;
        byte[] personalInfo = new byte[size];

        int index = 0;

        //System.out.println("User.getVendorInfo: lengthOfIdentity=" + identity.length);
        //copy identity length
        byte[] lengthOfIdentity = ByteBuffer.allocate(Constants.INT_NO_OF_BYTES).putInt(identity.length).array();
        for (int i = 0; i < Constants.INT_NO_OF_BYTES; ++i, ++index) {
            personalInfo[index] = lengthOfIdentity[i];
        }

        //copy identity
        for (int i = 0; i < identity.length; ++i, ++index) {
            personalInfo[index] = identity[i];
        }

        byte[] publicKeyEncoded = publicKey.getEncoded();


        //System.out.println("User.getVendorInfo: publicKeyLength=" + publicKeyEncoded.length);
        //copy publicKey length
        byte[] lengthOfPublicKey = ByteBuffer.allocate(Constants.INT_NO_OF_BYTES).putInt(publicKeyEncoded.length).array();
        for (int i = 0; i < Constants.INT_NO_OF_BYTES; ++i, ++index) {
            personalInfo[index] = lengthOfPublicKey[i];
        }

        //System.out.println("User.getVendorInfo: userPublicKey=" + ((RSAPublicKey) publicKey).getModulus().toString());
        //copy publicKey
        for (int i = 0; i < publicKeyEncoded.length; ++i, ++index) {
            personalInfo[index] = publicKeyEncoded[i];
        }

        //System.out.println("User.getVendorInfo: accountNumber=" + getAccount().getAccountNumber());
        //copy account number
        byte[] accountNumberBytes = ByteBuffer.allocate(Constants.LONG_NO_OF_BYTES).putLong(getAccount().getAccountNumber()).array();
        for (int i = 0; i < Constants.LONG_NO_OF_BYTES; ++i, ++index) {
            personalInfo[index] = accountNumberBytes[i];
        }

        //System.out.println("User.getVendorInfo: creditLimit=" + creditLimit);
        //copy credit limit
        byte[] creditLimitBytes = ByteBuffer.allocate(Constants.LONG_NO_OF_BYTES).putLong(creditLimit).array();
        for (int i = 0; i < Constants.LONG_NO_OF_BYTES; ++i, ++index) {
            personalInfo[index] = creditLimitBytes[i];
        }

        return personalInfo;
    }

    /**
     * Check if this Vendor was already payed this day
     * @param vendorInfo the VendorInfo
     * @return true if this is the first payment to the Vendor, false otherwise
     */
    public boolean isFirstPayment(VendorInfo vendorInfo) {
        return !paymentsDone.containsKey(vendorInfo);
    }

    /**
     * Generate a new hash chain for the Vendor, in order to make it possible to pay him
     * @param vendorInfo the Vendor
     */
    public void generateNewHashChains(VendorInfo vendorInfo) {
        System.out.println("Started generating hash chains");
        List<Payword> currentHashChain1 = generateHashChain();
        List<Payword> currentHashChain5 = generateHashChain();
        List<Payword> currentHashChain10 = generateHashChain();

        System.out.println("Finished generating hash chains");

        //hash chain for the value 1
        if (hashChains1.get(vendorInfo) != null) {
            List<List<Payword>> vendorPreviousHashChains = hashChains1.get(vendorInfo);
            vendorPreviousHashChains.add(currentHashChain1);
            hashChains1.remove(vendorInfo);
            hashChains1.put(vendorInfo, vendorPreviousHashChains);
        }
        else {
            List<List<Payword>> vendorPreviousHashChains = new ArrayList<>();
            vendorPreviousHashChains.add(currentHashChain1);
            hashChains1.put(vendorInfo, vendorPreviousHashChains);
        }

        //hash chain for the value 5
        if (hashChains5.get(vendorInfo) != null) {
            List<List<Payword>> vendorPreviousHashChains = hashChains5.get(vendorInfo);
            vendorPreviousHashChains.add(currentHashChain5);
            hashChains5.remove(vendorInfo);
            hashChains5.put(vendorInfo, vendorPreviousHashChains);
        }
        else {
            List<List<Payword>> vendorPreviousHashChains = new ArrayList<>();
            vendorPreviousHashChains.add(currentHashChain5);
            hashChains5.put(vendorInfo, vendorPreviousHashChains);
        }

        //hash chain for the value 10
        if (hashChains10.get(vendorInfo) != null) {
            List<List<Payword>> vendorPreviousHashChains = hashChains10.get(vendorInfo);
            vendorPreviousHashChains.add(currentHashChain10);
            hashChains10.remove(vendorInfo);
            hashChains10.put(vendorInfo, vendorPreviousHashChains);
        }
        else {
            List<List<Payword>> vendorPreviousHashChains = new ArrayList<>();
            vendorPreviousHashChains.add(currentHashChain10);
            hashChains10.put(vendorInfo, vendorPreviousHashChains);
        }

    }

    private List<Payword> generateHashChain() {
        List<Payword> currentHashChain = new ArrayList<>();

        byte[] cn = Crypto.getSecret(1024);

        Payword last = new Payword(cn); //c(n-1)
        currentHashChain.add(last);
        int i = 1;
        for (i = this.hashChainLength - 2; i >= 0; --i) {
            Payword current = new Payword(last);
            currentHashChain.add(current);

            last = current;
        }
        return currentHashChain;
    }

    /**
     * Generate a commit for the Vendor
     * commit(V) = sigU(V, C(U), c0, D, I), where
     *  V is the identity of the Vendor,
     *  C(U) is the certificate of the User, generated by the Broker,
     *  c0 is the root of the hash chain,
     *  D is the current date,
     *  I are additional info: length of the chain, etc.
     * @param vendorInfo the Vendor
     * @return the commit
     */
    public Commit computeCommitment(VendorInfo vendorInfo) {
        int size = vendorInfo.getIdentity().length +
                userCertificate.length +
                3 * 20 + Constants.LONG_NO_OF_BYTES + Constants.INT_NO_OF_BYTES;
        byte[] message = new byte[size];

        //System.out.println("User.computeCommitment: size = " + size);

        int index = 0;

        //copy vendor's identity
        byte[] vendorIdentity = vendorInfo.getIdentity();
        for (int i = 0; i < vendorIdentity.length; ++i, ++index) {
            message[index] = vendorIdentity[i];
        }

        //copy user certificate
        for (int i = 0; i < this.userCertificate.length; ++i, ++index) {
            message[index] = this.userCertificate[i];
        }

        //copy the root of the signedHash chain, c01
        List<List<Payword>> allHashChains1 = hashChains1.get(vendorInfo);
        List<Payword> lastHashChainComputed1 = allHashChains1.get(allHashChains1.size() - 1);
        byte[] c01 = lastHashChainComputed1.get(this.hashChainLength - 1).getBytes();
        for (int i = 0; i < c01.length; ++i, ++index) {
            message[index] = c01[i];
        }

        //copy the root of the signedHash chain, c05
        List<List<Payword>> allHashChains5 = hashChains5.get(vendorInfo);
        List<Payword> lastHashChainComputed5 = allHashChains1.get(allHashChains5.size() - 1);
        byte[] c05 = lastHashChainComputed5.get(this.hashChainLength - 1).getBytes();
        for (int i = 0; i < c05.length; ++i, ++index) {
            message[index] = c05[i];
        }

        //copy the root of the signedHash chain, c010
        List<List<Payword>> allHashChains10 = hashChains10.get(vendorInfo);
        List<Payword> lastHashChainComputed10 = allHashChains10.get(allHashChains10.size() - 1);
        byte[] c010 = lastHashChainComputed10.get(this.hashChainLength - 1).getBytes();
        for (int i = 0; i < c010.length; ++i, ++index) {
            message[index] = c010[i];
        }

        //copy the current date
        LocalDateTime currentDateLocalDateTime = LocalDateTime.now();
        long currentDateLong = currentDateLocalDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        byte[] currentDateBytes = ByteBuffer.allocate(8).putLong(currentDateLong).array();
        for (int i = 0; i < currentDateBytes.length; ++i, ++index) {
            message[index] = currentDateBytes[i];
        }

        //copy the length of the chain
        byte[] lengthOfChainBytes = ByteBuffer.allocate(4).putInt(this.hashChainLength).array();
        for (int i = 0; i < lengthOfChainBytes.length; ++i, ++index) {
            message[index] = lengthOfChainBytes[i];
        }

        //hash and sign
        byte[] signedHash = null;

        //sign the message
        Signature sig = null;
        try {
            sig = Signature.getInstance("SHA1WithRSA");
            sig.initSign(getPrivateKey());
            sig.update(message);
            signedHash = sig.sign();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        }

        size += signedHash.length; //the length of the signed hash
        byte[] commitBytes = new byte[size];
        index = 0;
        for (int i = 0; i < message.length; ++i, ++index) {
            commitBytes[index] = message[i];
        }

        for (int i = 0; i < signedHash.length; ++i, ++index) {
            commitBytes[index] = signedHash[i];
        }

        Commit commit = new Commit(commitBytes);

        return commit;
    }

    public void addPaymentToListOfPayments(VendorInfo vendorInfo, Payment payment) {
        List<Payment> paymentList;
        if (paymentsDone.get(vendorInfo) != null)
            paymentList = paymentsDone.get(vendorInfo);
        else
            paymentList = new ArrayList<>();

        paymentList.add(payment);
        paymentsDone.remove(vendorInfo);
        paymentsDone.put(vendorInfo, paymentList);
    }

    public Payment constructPayment(VendorInfo vendorInfo, int paymentNo, int paywordValue) {
        byte[] bytes = new byte[28];

        System.out.println("User.constructPayment: paymentNo=" + paymentNo);

        int index = 0;

        //copy the paymentNo-th payword from the corresponding hash chain
        switch (paywordValue) {
            case Constants.PaywordValue.FIVE:
                System.out.println("User.constructPayment: Payword Value = FIVE");
                List<List<Payword>> allHashChains5 = hashChains5.get(vendorInfo);
                List<Payword> lastHashChainComputed5 = allHashChains5.get(allHashChains5.size() - 1);
                byte[] ci = lastHashChainComputed5.get(this.hashChainLength - paymentNo - 1).getBytes();
                for (int i = 0; i < ci.length; ++i, ++index) {
                    bytes[index] = ci[i];
                }

                //System.out.println("User.constructPayment: " + Arrays.toString(ci));

                //copy the bytes of paymentNo
                byte[] paymentNoBytes = ByteBuffer.allocate(4).putInt(paymentNo).array();
                for (int i = 0; i < paymentNoBytes.length; ++i, ++index) {
                    bytes[index] = paymentNoBytes[i];
                }

                break;

            case Constants.PaywordValue.TEN:
                System.out.println("User.constructPayment: Payword Value = TEN");
                List<List<Payword>> allHashChains10 = hashChains10.get(vendorInfo);
                List<Payword> lastHashChainComputed10 = allHashChains10.get(allHashChains10.size() - 1);
                ci = lastHashChainComputed10.get(this.hashChainLength - paymentNo - 1).getBytes();
                for (int i = 0; i < ci.length; ++i, ++index) {
                    bytes[index] = ci[i];
                }

                //System.out.println("User.constructPayment: " + Arrays.toString(ci));

                //copy the bytes of paymentNo
                paymentNoBytes = ByteBuffer.allocate(4).putInt(paymentNo).array();
                for (int i = 0; i < paymentNoBytes.length; ++i, ++index) {
                    bytes[index] = paymentNoBytes[i];
                }

                break;

            case Constants.PaywordValue.ONE:
            default:
                System.out.println("User.constructPayment: Payword Value = ONE");
                List<List<Payword>> allHashChains1 = hashChains1.get(vendorInfo);
                List<Payword> lastHashChainComputed1 = allHashChains1.get(allHashChains1.size() - 1);
                ci = lastHashChainComputed1.get(this.hashChainLength - paymentNo - 1).getBytes();
                for (int i = 0; i < ci.length; ++i, ++index) {
                    bytes[index] = ci[i];
                }

                //System.out.println("User.constructPayment: " + Arrays.toString(ci));

                //copy the bytes of paymentNo
                paymentNoBytes = ByteBuffer.allocate(4).putInt(paymentNo).array();
                for (int i = 0; i < paymentNoBytes.length; ++i, ++index) {
                    bytes[index] = paymentNoBytes[i];
                }

                break;

        }

        //copy the bytes of paywordValue
        byte[] paywordValueBytes = ByteBuffer.allocate(4).putInt(paywordValue).array();
        for (int i = 0; i < paywordValueBytes.length; ++i, ++index) {
            bytes[index] = paywordValueBytes[i];
        }

        //Send payment to Vendor
        Payment payment = new Payment(bytes);

        return payment;
    }

    public int getVendorNoOfPayments(VendorInfo vendorInfo) {
        return paymentsDone.get(vendorInfo).size();
    }
}
