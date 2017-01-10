package crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.util.encoders.Base64;


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
	
	
	private static byte[] genHMAC(Key key, HMAC_ALGORITHM algorithm, String message){
		
		Mac mac = null;
		try{
			if(algorithm.equals(HMAC_ALGORITHM.HmacMD5)){
				macMD5.init(key);
				mac = macMD5;
			}else if(algorithm.equals(HMAC_ALGORITHM.HmacSHA1)){
				macSHA1.init(key);
				mac = macSHA1;
			}else if(algorithm.equals(HMAC_ALGORITHM.HmacSHA256)){
				macSHA256.init(key);
				mac = macSHA256;
			}
		}catch (InvalidKeyException e) {
			e.printStackTrace();
		}
		
		mac.update(message.getBytes());
		return mac.doFinal();
	}
	
	
	public static String genMessageWithHMac(Key key, HMAC_ALGORITHM algorithm, String message){
			
		byte[] hmac = Cryptography.genHMAC(key, algorithm, message);
		hmac = Cryptography.encodeIntoBase64(hmac);
		StringBuilder stringBuilder = new StringBuilder();
		
		for(byte b : hmac){
			stringBuilder.append(b);
		}
		return stringBuilder.toString() + " " + message;		
	}
	
	
	public static boolean checkHMacInMessage(Key key, HMAC_ALGORITHM algorithm, String privateMsg, boolean base64){
		
		byte[] receivedHash = privateMsg.substring(0, privateMsg.indexOf(" ")).getBytes();
		//remove HMAC
		
		privateMsg = privateMsg.substring(privateMsg.indexOf(" ") + 1, privateMsg.length());
				
		privateMsg = genMessageWithHMac(key, algorithm, privateMsg);
		byte[] computedHash = privateMsg.substring(0, privateMsg.indexOf(" ")).getBytes();
		computedHash = Base64.decode(computedHash);
		
		if(base64){
			receivedHash = decodeFromBase64(receivedHash);
		}
		return MessageDigest.isEqual(computedHash, receivedHash);
	}
	
	public static byte[] cryptoAES(int encryptMode, byte[] ivParameter, byte[] secretKeyInBytes,
			byte[] toCrypt) {

		try {
			IvParameterSpec ivParameterSpec = new IvParameterSpec(ivParameter);
			SecretKey secretKey = new SecretKeySpec(secretKeyInBytes, 0, secretKeyInBytes.length,
					"AES");
			Cipher AEScipher = Cipher.getInstance("AES/CTR/NoPadding");

			AEScipher.init(encryptMode, secretKey, ivParameterSpec);
			return AEScipher.doFinal(toCrypt);
		} catch (InvalidKeyException e) {
			System.err.println(e.getMessage());
		} catch (InvalidAlgorithmParameterException e) {
			System.err.println(e.getMessage());
		} catch (IllegalBlockSizeException e) {
			System.err.println(e.getMessage());
		} catch (BadPaddingException e) {
			System.err.println(e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			System.err.println(e.getMessage());
		} catch (NoSuchPaddingException e) {
			System.err.println(e.getMessage());
		}
		return null;
	}

	public static byte[] cryptoRSA(int encryptMode, Key key, byte[] toCrypt) {

		try {
			Cipher RSAcipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			RSAcipher.init(encryptMode, key);
			return RSAcipher.doFinal(toCrypt);
		} catch (InvalidKeyException e) {
			System.err.println(e.getMessage());
		} catch (IllegalBlockSizeException e) {
			System.err.println(e.getMessage());
		} catch (BadPaddingException e) {
			System.err.println(e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			System.err.println(e.getMessage());
		} catch (NoSuchPaddingException e) {
			System.err.println(e.getMessage());
		}
		return null;
	}
	
	/**
	//just for testing, please do not remove
	public static void main(String[] args) throws IOException {
		
		init();
		Key key = Keys.readSecretKey(new File("keys/hmac.key"));
		String message = "Hello there I'm Bill!";
		
		message = Cryptography.genMessageWithHMac(key, HMAC_ALGORITHM.HmacSHA256, "!msg " + "Bill" + ": " + message);
		
		//message = message.replace("l", "");
		
		String reply = null;

		if(Cryptography.checkHMacInMessage(key, HMAC_ALGORITHM.HmacSHA256, message, true)){
			message = message.substring(message.indexOf(" ") + 1, message.length());
			message = message.substring(message.indexOf(" ") + 1, message.length());
			reply = Cryptography.genMessageWithHMac(key, HMAC_ALGORITHM.HmacSHA256, "Bill.de" + " replied with !ack.");
			
			//reply = reply.replace("a", "");
		
		}else{
			message = message.substring(message.indexOf(" ") + 1, message.length());
			message = message.substring(message.indexOf(" ") + 1, message.length());
			reply = Cryptography.genMessageWithHMac(key, HMAC_ALGORITHM.HmacSHA256, "!tampered " + message);
			
			//reply = reply.replace("i", "");
			
			message += "\n<NOTE: This message has been tampered!>";
		}
				
		

		boolean messageTampered = reply.contains("!tampered");
		boolean replyTampered = !Cryptography.checkHMacInMessage(key, HMAC_ALGORITHM.HmacSHA256, reply, true);
	
		reply = reply.substring(reply.indexOf(" ") + 1, reply.length());
		
		if(replyTampered){	
			reply += "\n<NOTE: This comfirmation message sent from " + "Bill.de" + " has been tampered!>";
		}	
		if(messageTampered){
			reply += "\n<NOTE: Your message sent to " + "Bill.de" + " has been tampered";
			if(replyTampered){
				reply += " too";
			}
			reply += "!>";
		}	
		
		System.out.println("Message: " + message);
		System.out.println("Reply: " + reply);
	}
	
	
	*/
	
}
