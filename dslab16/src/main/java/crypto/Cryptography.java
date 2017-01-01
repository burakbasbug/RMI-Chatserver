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

import com.sun.scenario.effect.impl.sw.sse.SSEBlend_COLOR_BURNPeer;


public class Cryptography {
	
	private static Mac macMD5 = null;
	private static Mac macSHA1 = null;
	private static Mac macSHA256 = null;
	
	private static SecureRandom secRand = null;
	
	private static KeyGenerator aesKeyGen = null;

	public static  enum HMAC_ALGORITHM {
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
	
	
	public static byte[] genSecureRandomNumber(int size){
		
		byte[] rand = new byte[size];
		secRand.nextBytes(rand);
		return rand;
	}
	
	
	public static SecretKey genAESSecretKey(int keySize){
	
		aesKeyGen.init(keySize);
		return aesKeyGen.generateKey();
	}
	
	
	public static byte[] encodeIntoBase64(byte[] toEncode){
		return Base64.encode(toEncode);
	}
	
	
	public static byte[] encodeIntoBase64(Key toEncode){
		return Base64.encode(toEncode.getEncoded());
	}
	
	public static byte[] decodeFromBase64(byte[] toDecode){
		return  Base64.decode(toDecode);
	}
	
	
	public static byte[] genHMAC(Key secretKey, HMAC_ALGORITHM algorithm, String message){
		
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
	
	
	public static boolean validateHMAC(Key secretKey, HMAC_ALGORITHM algorithm, String message, byte[] receivedHash, boolean base64){
		
		byte[] computedHash = genHMAC(secretKey, algorithm, message);
		if(base64)
			computedHash = encodeIntoBase64(computedHash);
		return MessageDigest.isEqual(computedHash, receivedHash);
	}
	
	
	//just for testing
	public static void main(String[] args) {
		
		init();
		
		Key key = genAESSecretKey(256);
		
		byte[] hmac = encodeIntoBase64(genHMAC(key, HMAC_ALGORITHM.HmacSHA256, "Hallo!"));
		System.out.println(validateHMAC(key, HMAC_ALGORITHM.HmacSHA256, "Hallo!", hmac, true));
	}
	
	
	
	
}
