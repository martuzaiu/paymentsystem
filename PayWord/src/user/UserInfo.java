package user;

import java.security.PublicKey;
import java.util.Arrays;


public class UserInfo {

    private PublicKey publicKey;
    private byte[] identity;
    private long accountNumber;
    private long creditLimit;

    public UserInfo() {
        this.publicKey = null;
        this.identity = null;
        this.accountNumber = 0;
        this.creditLimit = 0;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public byte[] getIdentity() {
        return identity;
    }

    public void setIdentity(byte[] identity) {
        this.identity = identity;
    }

    public long getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(long accountNumber) {
        this.accountNumber = accountNumber;
    }

    public long getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(long creditLimit) {
        this.creditLimit = creditLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserInfo userInfo = (UserInfo) o;

        if (accountNumber != userInfo.accountNumber) return false;
        //if (publicKey != null ? !publicKey.equals(userInfo.publicKey) : userInfo.publicKey != null) return false;
        return Arrays.equals(identity, userInfo.identity);

    }

    @Override
    public int hashCode() {
        int result = publicKey != null ? publicKey.hashCode() : 0;
        result = 31 * result + (identity != null ? Arrays.hashCode(identity) : 0);
        result = 31 * result + (int) (accountNumber ^ (accountNumber >>> 32));
        result = 31 * result + (int) (creditLimit ^ (creditLimit >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "UserInfo{" +
                "publicKey=" + publicKey + ",\n" +
                "identity=" + Arrays.toString(identity) + ",\n" +
                "accountNumber=" + accountNumber + ",\n" +
                "creditLimit=" + creditLimit +
                '}';
    }
}
