package broker;

import user.UserInfo;
import utils.Constants;
import utils.Crypto;
import backend.Payword;
import vendor.Vendor;
import vendor.VendorInfo;

import java.nio.ByteBuffer;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Broker {

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private byte[] identity;
    private List<UserInfo> registeredUsers;
    private List<VendorInfo> registeredVendors;

    private List<Payword> paymentsRedeemed;

    private Bank bank;
    private static Broker instance;

    public static Broker getInstance() {
        if (instance == null) {
            instance = new Broker();
        }

        return instance;
    }

    private Broker() {
        KeyPair keyPair = Crypto.getRSAKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        initIdentity();
        this.registeredUsers = new ArrayList<>();
        this.registeredVendors = new ArrayList<>();
        this.paymentsRedeemed = new ArrayList<>();

        this.bank = Bank.getInstance();
    }

    public Broker(String identity) {
        KeyPair keyPair = Crypto.getRSAKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        initIdentity();

        //Copy the bytes from the given String
        byte[] stringIdentityBytes = identity.getBytes();
        for (int i = 0; i < stringIdentityBytes.length; ++i) {
            this.identity[i] = stringIdentityBytes[i];
        }

        this.registeredUsers = new ArrayList<>();
        this.registeredVendors = new ArrayList<>();
        this.paymentsRedeemed = new ArrayList<>();

        this.bank = Bank.getInstance();
    }

    private void initIdentity() {
        this.identity = new byte[Constants.IDENTITY_NO_OF_BITS / 8];
        for (int i = 0; i < Constants.IDENTITY_NO_OF_BITS / 8; ++i) {
            this.identity[i] = 0;
        }
    }

    public PublicKey getPublicKey() {
        return this.publicKey;
    }

    private PrivateKey getPrivateKey() {
        return this.privateKey;
    }

    public boolean registerNewUser(byte[] userPersonalInfo) {
        int indexStart = 0;
        int indexEnd = 0;

        //get all info from the array

        indexStart = indexEnd;
        indexEnd += 4;

        //get length of userIdentity
        byte[] lengthOfIdentityBytes = new byte[4];
        lengthOfIdentityBytes = Arrays.copyOfRange(userPersonalInfo, indexStart, indexEnd);
        int lengthOfIdentity = ByteBuffer.wrap(lengthOfIdentityBytes).getInt();

        //System.out.println("Broker.registerNewUser: lengthOfIdentity=" + lengthOfIdentity);

        indexStart = indexEnd;
        indexEnd += lengthOfIdentity;

        //get userIdentity
        byte[] userIdentity = new byte[lengthOfIdentity];
        userIdentity = Arrays.copyOfRange(userPersonalInfo, indexStart, indexEnd);

        indexStart = indexEnd;
        indexEnd += 4;

        //get length of public key
        byte[] lengthOfPublicKeyBytes = new byte[4];
        lengthOfPublicKeyBytes = Arrays.copyOfRange(userPersonalInfo, indexStart, indexEnd);
        int lengthOfPublicKey = ByteBuffer.wrap(lengthOfPublicKeyBytes).getInt();

        //System.out.println("Broker.registerNewUser: lengthOfPublicKey=" + lengthOfPublicKey);

        indexStart = indexEnd;
        indexEnd += lengthOfPublicKey;

        //get public key
        byte[] publicKeyBytes = new byte[lengthOfPublicKey];
        publicKeyBytes = Arrays.copyOfRange(userPersonalInfo, indexStart, indexEnd);

        PublicKey userPublicKey = null;
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = null;
        try {
            keyFactory = KeyFactory.getInstance("RSA");
            userPublicKey = keyFactory.generatePublic(keySpec);

            //System.out.println("Broker.registerNewUser: userPublicKey=" + ((RSAPublicKey) userPublicKey).getModulus().toString());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }

        indexStart = indexEnd;
        indexEnd += 8;

        //get account number
        byte[] accountNumberBytes = new byte[8];
        accountNumberBytes = Arrays.copyOfRange(userPersonalInfo, indexStart, indexEnd);
        long userAccountNumber = ByteBuffer.wrap(accountNumberBytes).getLong();

        //System.out.println("Broker.registerNewUser: userAccountNumber=" + userAccountNumber);

        indexStart = indexEnd;
        indexEnd += 8;

        //get credit limit
        byte[] creditLimitBytes = new byte[8];
        creditLimitBytes = Arrays.copyOfRange(userPersonalInfo, indexStart, indexEnd);
        long userCreditLimit = ByteBuffer.wrap(creditLimitBytes).getLong();

        //System.out.println("Broker.registerNewUser: userCreditLimit=" + userCreditLimit);


        //Handle the same user registering two times; should make sure the public key of the user changes
        //Store all info received from the user in some kind of structure
        UserInfo userInfo = new UserInfo();
        userInfo.setIdentity(userIdentity);
        userInfo.setPublicKey(userPublicKey);
        userInfo.setAccountNumber(userAccountNumber);
        userInfo.setCreditLimit(userCreditLimit);

        if (registeredUsers.contains(userInfo)) {
            System.out.println("Broker.registerNewUser: userInfo exists!");

            //update the public key of the existing userInfo
            int indexOfUserInfo = registeredUsers.indexOf(userInfo);
            registeredUsers.get(indexOfUserInfo).setPublicKey(userPublicKey);
            return true;
        }
        else {
            System.out.println("Broker.registerNewUser: new userInfo!");
            registeredUsers.add(userInfo);
            return true;
        }
    }

    public boolean registerNewVendor(byte[] vendorInfoBytes) {
        int indexStart = 0;
        int indexEnd = 0;

        //get all info from the array

        indexStart = indexEnd;
        indexEnd += 4;

        //get length of vendorIdentity
        byte[] lengthOfIdentityBytes = new byte[4];
        lengthOfIdentityBytes = Arrays.copyOfRange(vendorInfoBytes, indexStart, indexEnd);
        int lengthOfIdentity = ByteBuffer.wrap(lengthOfIdentityBytes).getInt();

        //System.out.println("Broker.registerNewVendor: lengthOfIdentity=" + lengthOfIdentity);

        indexStart = indexEnd;
        indexEnd += lengthOfIdentity;

        //get vendorIdentity
        byte[] vendorIdentity = new byte[lengthOfIdentity];
        vendorIdentity = Arrays.copyOfRange(vendorInfoBytes, indexStart, indexEnd);

        indexStart = indexEnd;
        indexEnd += 4;

        //get length of public key
        byte[] lengthOfPublicKeyBytes = new byte[4];
        lengthOfPublicKeyBytes = Arrays.copyOfRange(vendorInfoBytes, indexStart, indexEnd);
        int lengthOfPublicKey = ByteBuffer.wrap(lengthOfPublicKeyBytes).getInt();

        //System.out.println("Broker.registerNewVendor: lengthOfPublicKey=" + lengthOfPublicKey);

        indexStart = indexEnd;
        indexEnd += lengthOfPublicKey;

        //get public key
        byte[] publicKeyBytes = new byte[lengthOfPublicKey];
        publicKeyBytes = Arrays.copyOfRange(vendorInfoBytes, indexStart, indexEnd);

        PublicKey vendorPublicKey = null;
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = null;
        try {
            keyFactory = KeyFactory.getInstance("RSA");
            vendorPublicKey = keyFactory.generatePublic(keySpec);

            //System.out.println("Broker.registerNewVendor: vendorPublicKey=" + ((RSAPublicKey) vendorPublicKey).getModulus().toString());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }

        indexStart = indexEnd;
        indexEnd += 8;

        //get account number
        byte[] accountNumberBytes = new byte[8];
        accountNumberBytes = Arrays.copyOfRange(vendorInfoBytes, indexStart, indexEnd);
        long vendorAccountNumber = ByteBuffer.wrap(accountNumberBytes).getLong();

        //System.out.println("Broker.registerNewVendor: vendorAccountNumber=" + vendorAccountNumber);

        //Handle the same vendor registering two times; should make sure the public key of the user changes
        //Store all info received from the user in some kind of structure
        VendorInfo vendorInfo = new VendorInfo();
        vendorInfo.setIdentity(vendorIdentity);
        vendorInfo.setPublicKey(vendorPublicKey);
        vendorInfo.setAccountNumber(vendorAccountNumber);

        if (registeredVendors.contains(vendorInfo)) {
            System.out.println("Broker.registerNewVendor: vendorInfoBytes exists!");

            //update the public key of the existing vendorInfoBytes
            int indexOfUserInfo = registeredVendors.indexOf(vendorInfo);
            registeredVendors.get(indexOfUserInfo).setPublicKey(vendorPublicKey);
            return true;
        }
        else {
            System.out.println("Broker.registerNewVendor: new vendorInfoBytes!");
            registeredVendors.add(vendorInfo);
            return true;
        }
    }

    /**
     * Get the certificate for the user given by the userIdentity
     * @param userIdentity the identity of the user
     * @return the certificate
     */
    public byte[] getUserCertificate(byte[] userIdentity) {
        System.out.println("Broker.getUserCertificate");
        UserInfo userInfo = getUserWithIdentity(userIdentity);

        int size = this.identity.length + userInfo.getIdentity().length;
        size += this.publicKey.getEncoded().length + userInfo.getPublicKey().getEncoded().length;
        size += 8; //the date is stored as 64 bits long value, therefore 8 bytes
        size += 8; //the accountNumber is stored as 64 bits long value, therefore 8 bytes
        size += 8; //the creditLimit is stored as 64 bits long value, therefore 8 bytes
        byte[] message = new byte[size];

        int index = 0;
        //copy the identity of the Broker
        for (int i = 0; i < this.identity.length; ++i, ++index) {
            message[index] = this.identity[i];
        }

        //copy the identity of the User
        for (int i = 0; i < userIdentity.length; ++i, ++index) {
            message[index] = userIdentity[i];
        }

        //copy the publicKey of the Broker
        byte[] publicKeyEncoded = publicKey.getEncoded();
        for (int i = 0; i < publicKeyEncoded.length; ++i, ++index) {
            message[index] = publicKeyEncoded[i];
        }

        //copy the publicKey of the User
        byte[] userPublicKeyEncoded = userInfo.getPublicKey().getEncoded();
        for (int i = 0; i < userPublicKeyEncoded.length; ++i, ++index) {
            message[index] = userPublicKeyEncoded[i];
        }

        //copy the expire date of the message
        LocalDateTime currentDateLocalDateTime = LocalDateTime.now();
        LocalDateTime expireDateLocalDate = currentDateLocalDateTime.plusMonths(1);
        long expireDateLong = expireDateLocalDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        byte[] expireDateBytes =  ByteBuffer.allocate(8).putLong(expireDateLong).array();
        for (int i = 0; i < expireDateBytes.length; ++i, ++index) {
            message[index] = expireDateBytes[i];
        }

        //copy the accountNumber
        byte[] accountNumberBytes = ByteBuffer.allocate(8).putLong(userInfo.getAccountNumber()).array();
        for (int i = 0; i < accountNumberBytes.length; ++i, ++index) {
            message[index] = accountNumberBytes[i];
        }

        //copy the creditLimit
        byte[] creditLimitBytes = ByteBuffer.allocate(8).putLong(userInfo.getCreditLimit()).array();
        for (int i = 0; i < creditLimitBytes.length; ++i, ++index) {
            message[index] = creditLimitBytes[i];
        }

        //System.out.println("Broker.getUserCertificate: message length=" + message.length);

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
        byte[] certificate = new byte[size];
        index = 0;
        for (int i = 0; i < message.length; ++i, ++index) {
            certificate[index] = message[i];
        }

        for (int i = 0; i < signedHash.length; ++i, ++index) {
            certificate[index] = signedHash[i];
        }

        //System.out.println("Broker.getUserCertificate: certificate length=" + certificate.length);

        return certificate;
    }

    private UserInfo getUserWithIdentity(byte[] userIdentity) {
        for (UserInfo userInfo : registeredUsers)
            if (Arrays.equals(userInfo.getIdentity(), userIdentity))
                return userInfo;
        return null;
    }

    private VendorInfo getVendorWithIdentity(byte[] vendorIdentity) {
        for (VendorInfo vendorInfo : registeredVendors)
            if (Arrays.equals(vendorInfo.getIdentity(), vendorIdentity))
                return vendorInfo;
        return null;
    }

    public byte[] getUserIdentityFromPersonalInfo(byte[] personalInfo) {
        //get identity length
        int identityLength = ByteBuffer.wrap(personalInfo, 0, 4).getInt();

        //get the identity
        byte[] userIdentity = Arrays.copyOfRange(personalInfo, 4, 4 + identityLength);

        return userIdentity;
    }

    /**
     * This is the third step of the scheme
     * @param message the message
     * @return true if the action completed with success, false otherwise
     */
    public boolean redeem(byte[] message) {
        System.out.println("Broker.redeem with vendorInfo");

        //check commit(U)
        //extract commit(U) from the message
        //get the unsigned part
        int size = 932; //the no of bytes of the message without the signed hash
        byte[] unsignedMessage = Arrays.copyOfRange(message, 0, size);

        //get the signed hash
        byte[] signedHash = Arrays.copyOfRange(message, size, 1060);

        //get the vendor identity
        byte[] vendorIdentity = Arrays.copyOfRange(message, 0, 128);

        //get the user identity
        byte[] userCertificate = Arrays.copyOfRange(unsignedMessage, 128, 860);
        byte[] userIdentity = Arrays.copyOfRange(userCertificate, 128, 256);
        UserInfo userInfo = getUserWithIdentity(userIdentity);
        //System.out.println("Broker.redeem: userInfo=" + userInfo);

        //check User signature on commit(U)
        Signature signature = null;
        boolean result = false;
        try {
            signature = Signature.getInstance("SHA1WithRSA");
            signature.initVerify(userInfo.getPublicKey());
            signature.update(unsignedMessage);
            result = signature.verify(signedHash);
            System.out.println("Broker.redeem: verify User signature on commit result: " + result);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

        if (result) {
            //check last payment (apply hash function l times)
            //get c0 - the root of the hash chain from the commit
            byte[] c0 = Arrays.copyOfRange(unsignedMessage, 860, 880); //get 20 bytes
            //System.out.println("Broker.redeem: c0=" + Arrays.toString(c0));

            //apply the hash function l times to see if the resulting c0 is equal with the given c0
            //get cl - the l-th payword
            byte[] cl = Arrays.copyOfRange(message, 1060, 1080);
            //System.out.println("Broker.redeem: cl=" + Arrays.toString(cl));

            //get l - the index of the last payment
            int l = ByteBuffer.wrap(message, 1080, 4).getInt();
            //System.out.println("Broker.redeem: l=" + l);

            //apply the hash function l times
            Payword last = new Payword(); //c(l)
            last.setBytes(cl);
            for (int i = l - 1; i >= 0; --i) {
                //System.out.println("Broker.redeem: l=" + i + " cl=" + Arrays.toString(last.getBytes()));
                Payword current = new Payword(last);

                last = current;
            }
            byte[] c0computed = last.getBytes();
            //System.out.println("Broker.redeem: c0computed=" + Arrays.toString(c0computed));
            Payword rootOfPayment = new Payword();
            rootOfPayment.setBytes(c0computed);

            if (Arrays.equals(c0, c0computed)) {
                System.out.println("Broker.redeem: c0 equals!");
                //check if payment is authentic and not already redeemed
                //check if payment is not redeemed
                if (paymentsRedeemed.contains(rootOfPayment)) {
                    System.out.println("Broker.redeem: payment already done! => don't pay the vendor!");
                    result = false;
                }
                else {
                    System.out.println("Broker.redeem: first payment! => pay the vendor");
                    //make payment to Vendor and take money from User
                    System.out.println("Broker.redeem: lastPaymentIndex=" + l);


                    //TODO: Implement Bank as server
                    //Proof of Concept: take money from the User and add them to the Vendor
                    VendorInfo vendorInfo = getVendorWithIdentity(vendorIdentity);
                    //System.out.println("Broker.redeem: vendorInfo=" + vendorInfo);
                    bank.takeMoneyFromAccount(userInfo.getAccountNumber(), l + 1);
                    bank.addMoneyToAccount(vendorInfo.getAccountNumber(), l + 1);
                    paymentsRedeemed.add(rootOfPayment);
                    result = true;
                }
            } else {
                System.out.println("Broker.redeem: c0 not equals!");
                result = false;
            }
        }

        if (result)
            System.out.println("Broker.redeem: redeem OK");
        else
            System.out.println("Broker.redeem: redeem NOK");

        return result;
    }

}
