package utils;

import java.security.*;
import java.util.Random;


public class Crypto {

    public static KeyPair getRSAKeyPair() {
        KeyPairGenerator keyPairGenerator = null;
        KeyPair keyPair = null;

        try {
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(Constants.KEY_NO_OF_BITS);

            keyPair = keyPairGenerator.genKeyPair();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return keyPair;
    }

    public static byte[] hashMessage(byte[] message) {
        byte[] hash = null;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            hash = messageDigest.digest(message);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return hash;
    }

    public static byte[] getSecret(int noOfBytes) {
        byte[] secret = new byte[noOfBytes];

        Random random = new Random();
        random.nextBytes(secret);

        return secret;
    }
}
