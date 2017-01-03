package crypto;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.bouncycastle.util.encoders.Base64;

import util.Keys;


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
		if(base64){
			receivedHash = decodeFromBase64(receivedHash);
		}
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
	public static boolean checkHMacInMessage(Key hmacKey, Cryptography.HMAC_ALGORITHM algorithm, String privateMsg, boolean base64){
		
		byte[] receivedHash = privateMsg.substring(0, privateMsg.indexOf(" ")).getBytes();
		//remove HMAC
		
		privateMsg = privateMsg.substring(privateMsg.indexOf(" ") + 1, privateMsg.length());
				
		privateMsg = genMessageWithHMac(hmacKey, privateMsg);
		byte[] computedHash = privateMsg.substring(0, privateMsg.indexOf(" ")).getBytes();
		computedHash = Base64.decode(computedHash);
		
		if(base64){
			receivedHash = decodeFromBase64(receivedHash);
		}
		return MessageDigest.isEqual(computedHash, receivedHash);
	}
	
	
	
	
	//just for testing
	public static void main(String[] args) throws IOException {
		
		init();
		Key key = Keys.readSecretKey(new File("keys/hmac.key"));
		String message = "!msg " + "Jonas" + ": " + "Hello there I'm Jonas!";
		
		message = Cryptography.genMessageWithHMac(key, message);
	
		String reply = null;
		if(Cryptography.checkHMacInMessage(key, HMAC_ALGORITHM.HmacSHA256, message, true)){
			reply = Cryptography.genMessageWithHMac(key, "Jonas" + " replied with !ack.");
		}else{
			//remove HMAC + !msg
			message = message.substring(message.indexOf(" ")+1, message.length());
			message = message.substring(message.indexOf(" ")+1, message.length());
			reply = Cryptography.genMessageWithHMac(key, "!tampered " + message);
		}
		
		if(!Cryptography.checkHMacInMessage(key, HMAC_ALGORITHM.HmacSHA256, reply, true)){
			reply += "\nThe confirmation message received from " + "Jonas" + " has been changed!"; 
		}
		if(reply.contains("!tampered")){
			reply = "The message sent to " + "Jonas" + " has been tampered!";
		}
		
		System.out.println(reply);
		
	}
	
	
	
	
}
