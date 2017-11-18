package utils;


public class Constants {

    public static final int IDENTITY_NO_OF_BITS = 1024;
    public static final int KEY_NO_OF_BITS = 1024;
    public static final int LONG_NO_OF_BYTES = 8;
    public static final int INT_NO_OF_BYTES = 4;

    public static final int IDENTITY_LENGTH = 128;
    public static final int HASH_LENGTH = 20;
    public static final int UNSIGNED_COMMIT_LENGTH = 892;
    public static final int SIGNED_COMMIT_LENGTH = 1020;

    public static final String LOCALHOST = "localhost";

    public static class CommunicationProtocol {
        public static final int END_COMMUNICATION = -1;
        public static final int OK = 1;
        public static final int NOK = 0;
        public static final int FRAUD = 2;
        public static final int USER_REGISTER_TO_BROKER = 11;
        public static final int VENDOR_REGISTER_TO_BROKER = 111;
        public static final int GET_IDENTITY = 1111;
        public static final int MAKE_PAYMENT = 11111;
        public static final int COMMIT = 111111;
        public static final int REDEEM = 1111111;
    }

    public static class PaywordValue {
        public static final int ONE = 1;
        public static final int FIVE = 5;
        public static final int TEN = 10;
    }

}
