package backend;

import user.UserInfo;
import utils.Constants;

import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;


public class Commit {

    private byte[] bytes;

    public Commit(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return this.bytes;
    }

    public UserInfo getUserInfoFromCommit() {
        UserInfo userInfo = new UserInfo();

        //the userIdentity and userPublicKey can be extracted from the user certificate
        byte[] userCertificate = Arrays.copyOfRange(this.bytes, Constants.IDENTITY_NO_OF_BITS / 8, (Constants.IDENTITY_NO_OF_BITS / 8) + 732);

        //get the unsigned part
        byte[] unsignedPart = Arrays.copyOfRange(userCertificate, 0, 604);

        //get the userIdentity
        byte[] userIdentity = Arrays.copyOfRange(unsignedPart, Constants.IDENTITY_NO_OF_BITS / 8, 2 * (Constants.IDENTITY_NO_OF_BITS / 8));

        //get the userPublicKey
        byte[] userPublicKeyBytes = Arrays.copyOfRange(unsignedPart, 2 * (Constants.IDENTITY_NO_OF_BITS / 8) + 162, 2 * (Constants.IDENTITY_NO_OF_BITS / 8) + 2 * 162);
        PublicKey userPublicKey = null;
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(userPublicKeyBytes);
        KeyFactory keyFactory = null;
        try {
            keyFactory = KeyFactory.getInstance("RSA");
            userPublicKey = keyFactory.generatePublic(keySpec);

            System.out.println("UserInfo.getUserInfoFromCommit: userPublicKeyBytes=" + ((RSAPublicKey) userPublicKey).getModulus().toString());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }

        //get the userAccountNo
        long accountNo = ByteBuffer.wrap(unsignedPart, unsignedPart.length - 16, 8).getLong();

        //get the userCreditLimit
        long creditLimit = ByteBuffer.wrap(unsignedPart, unsignedPart.length - 8, 8).getLong();

        userInfo.setIdentity(userIdentity);
        userInfo.setPublicKey(userPublicKey);
        userInfo.setAccountNumber(accountNo);
        userInfo.setCreditLimit(creditLimit);

        return userInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Commit commit = (Commit) o;

        return Arrays.equals(bytes, commit.bytes);

    }

    @Override
    public int hashCode() {
        return bytes != null ? Arrays.hashCode(bytes) : 0;
    }

}
