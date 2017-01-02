package crypto;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.bouncycastle.util.encoders.Base64;


public class Cryptography {
	
	private static Mac macMD5 = null;
	private static Mac macSHA1 = null;
	private static Mac macSHA256 = null;
	
	private static SecureRandom secRand = null;
	
	private static KeyGenerator aesKeyGen = null;

	private static  enum HMAC_ALGORITHM {
									    HmacMD5,
							   			HmacSHA1,
							   			HmacSHA256
	}
	
	private static boolean init = false;
	
	/**
	 * Init() has to be called once before using any functionality of crypto.Cryptgraphy.
	 */
	public static void init(){
		
		if(init)
			return;
	
		secRand = new SecureRandom();
		
		try {
			macMD5 = Mac.getInstance("HmacMD5");
			macSHA1 = Mac.getInstance("HmacSHA1");
			macSHA256 = Mac.getInstance("HmacSHA256");
			
			aesKeyGen = KeyGenerator.getInstance("AES");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		
		init = true;		
	}
	
	
	private static byte[] genSecureRandomNumber(int size){
		
		byte[] rand = new byte[size];
		secRand.nextBytes(rand);
		return rand;
	}
	
	
	private static SecretKey genAESSecretKey(int keySize){
	
		aesKeyGen.init(keySize);
		return aesKeyGen.generateKey();
	}
	
	
	private static byte[] encodeIntoBase64(byte[] toEncode){
		return Base64.encode(toEncode);
	}
	
	
	private static byte[] encodeIntoBase64(Key toEncode){
		return Base64.encode(toEncode.getEncoded());
	}
	
	private static byte[] decodeFromBase64(byte[] toDecode){
		return  Base64.decode(toDecode);
	}
	
	
	private static byte[] genHMAC(Key secretKey, HMAC_ALGORITHM algorithm, String message){
		
		Mac mac = null;
		try{
			if(algorithm.equals(HMAC_ALGORITHM.HmacMD5)){
				macMD5.init(secretKey);
				mac = macMD5;
			}else if(algorithm.equals(HMAC_ALGORITHM.HmacSHA1)){
				macSHA1.init(secretKey);
				mac = macSHA1;
			}else if(algorithm.equals(HMAC_ALGORITHM.HmacSHA256)){
				macSHA256.init(secretKey);
				mac = macSHA256;
			}
		}catch (InvalidKeyException e) {
			e.printStackTrace();
		}
		
		mac.update(message.getBytes());
		return mac.doFinal();
	}
	
	
	private static boolean validateHMAC(Key secretKey, HMAC_ALGORITHM algorithm, String message, byte[] receivedHash, boolean base64){
		
		byte[] computedHash = genHMAC(secretKey, algorithm, message);
		if(base64)
			receivedHash = decodeFromBase64(receivedHash);
		return MessageDigest.isEqual(computedHash, receivedHash);
	}
	
	/**
	 * Returns a String composed of the elements: <HMAC> + " " + <message>.
	 * The HMAC is generated form the data contained in <message> and gets encoded with Base64. 
	 * @param hmacKey The shared HMAC Key
	 * @param message The message to generate a HMAC of.
	 * @return an HMAC of the message followed by a whitespace and the original message.
	 */
	public static String genMessageWithHMac(Key hmacKey, String message){
			
		byte[] hmac = Cryptography.genHMAC(hmacKey, Cryptography.HMAC_ALGORITHM.HmacSHA256, message);
		hmac = Cryptography.encodeIntoBase64(hmac);
		StringBuilder stringBuilder = new StringBuilder();
		
		for(byte b : hmac){
			stringBuilder.append(b);
		}
		return stringBuilder.toString() + " " + message;		
	}
	
	/**
	 * 
	 * @param hmacKey The shared HMAC Key.
	 * @param privateMsg The message to check.
	 * @return true if the HMAC is equal to the HMAC generated form the message, else false.
	 */
	public static boolean checkHMacInMessage(Key hmacKey, String privateMsg){
		
		byte[] hMac = privateMsg.substring(0, privateMsg.indexOf(" ")).getBytes();
		//remove HMAC
		privateMsg = privateMsg.substring(privateMsg.indexOf(" ") + 1, privateMsg.length());
	
		return Cryptography.validateHMAC(hmacKey, Cryptography.HMAC_ALGORITHM.HmacSHA256, privateMsg, hMac, true);
	}
	
	
	
	
	//just for testing
	public static void main(String[] args) {
		
		init();
		
		Key key = genAESSecretKey(256);
		
		byte[] hmac = encodeIntoBase64(genHMAC(key, HMAC_ALGORITHM.HmacSHA256, "Hallo!"));
		System.out.println(validateHMAC(key, HMAC_ALGORITHM.HmacSHA256, "Hallo!", hmac, true));
	}
	
	
	
	
}
