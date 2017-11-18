package vendor;

import java.security.PublicKey;
import java.util.Arrays;


public class VendorInfo {

    private PublicKey publicKey;
    private byte[] identity;
    private long accountNumber;

    public VendorInfo() {
        this.publicKey = null;
        this.identity = null;
        this.accountNumber = 0;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VendorInfo that = (VendorInfo) o;

        if (accountNumber != that.accountNumber) return false;
        if (publicKey != null ? !publicKey.equals(that.publicKey) : that.publicKey != null) return false;
        return Arrays.equals(identity, that.identity);

    }

    @Override
    public int hashCode() {
        int result = publicKey != null ? publicKey.hashCode() : 0;
        result = 31 * result + (identity != null ? Arrays.hashCode(identity) : 0);
        result = 31 * result + (int) (accountNumber ^ (accountNumber >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "VendorInfo{" +
                "publicKey=" + publicKey + "\n" +
                ", identity=" + Arrays.toString(identity) + "\n" +
                ", accountNumber=" + accountNumber +
                '}';
    }
}
