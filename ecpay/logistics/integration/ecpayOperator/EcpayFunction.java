package ecpay.logistics.integration.ecpayOperator;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import ecpay.logistics.integration.config.EcpayConfig;
import ecpay.logistics.integration.errorMsg.ErrorMessage;
import ecpay.logistics.integration.exception.EcpayException;

/**
 * �@�Ψ禡���O
 * @author mark.chiu
 *
 */
public class EcpayFunction {
	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
	
	/**
	 * �����ˬd�X
	 * @param key
	 * @param iv
	 * @param obj
	 * @return
	 */
	public final static String genCheckMacValue(String key, String iv, Object obj) {
		Class<?> cls = obj.getClass();
		List<String> fieldNames = getSortedFieldNames(cls);
		String data = "";
		try{
			for(String name: fieldNames){
				if(name != "CheckMacValue"){
					Method method = cls.getMethod("get"+name.substring(0, 1).toUpperCase()+name.substring(1), null);
					data = data + '&' + name + '=' + method.invoke(obj).toString();
				}
			}
			String urlEncode = urlEncode("HashKey=" + key + data + "&HashIV=" + iv).toLowerCase();
			urlEncode = netUrlEncode(urlEncode);
			return hash(urlEncode.getBytes(), "MD5");
		} catch(Exception e){
			throw new EcpayException(ErrorMessage.GEN_CHECK_MAC_VALUE_FAIL);
		}
	}
	
	/**
	 * �����ˬd�X
	 * @param key
	 * @param iv
	 * @param Hashtable<String, String> params
	 * @return
	 */
	public final static String genCheckMacValue(String key, String iv, Hashtable<String, String> params){
		Set<String> keySet = params.keySet();
		TreeSet<String> treeSet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		treeSet.addAll(keySet);
		String name[] = treeSet.toArray(new String[treeSet.size()]);
		String paramStr = "";
		for(int i = 0; i < name.length; i++){
			if(!name[i].equals("CheckMacValue")){
				paramStr += "&" + name[i] + "=" + params.get(name[i]);
			}
		}
		String urlEncode = urlEncode("Hashkey=" + key + paramStr + "&HashIV=" + iv).toLowerCase();
		urlEncode = netUrlEncode(urlEncode);
		return hash(urlEncode.getBytes(), "MD5");
	}
	
	/**
	 * �N�����ݩʻP�ˬd�X�զX��http���ѼƸ�Ʈ榡
	 * @param obj
	 * @param CheckMacValue
	 * @return string
	 */
	public final static String genHttpValue(Object obj, String CheckMacValue){
		Class<?> cls = obj.getClass();
		List<String> fieldNames = getSortedFieldNames(cls);
		Method method;
		String result = "";
		for(int i = 0; i < fieldNames.size(); i++){
			try {
				method = cls.getMethod("get"+fieldNames.get(i).substring(0, 1).toUpperCase()+fieldNames.get(i).substring(1), null);
				fieldNames.set(i, fieldNames.get(i) + '=' + invokeMethod(method, obj));
			} catch (Exception e) {
				throw new EcpayException(ErrorMessage.OBJ_MISSING_FIELD);
			}
			result = result + fieldNames.get(i) + '&';
		}
		return result + "CheckMacValue=" + CheckMacValue;
	}
	
	/**
	 * �N�����ରHashtable
	 * @param obj
	 * @return Hashtable
	 */
	public final static Hashtable<String, String>objToHashtable(Object obj) {
		Class<?> cls = obj.getClass();
		Hashtable<String, String> resultDict = new Hashtable<String, String>();
		List<String> fieldNames = getSortedFieldNames(cls);
		for(int i = 0; i < fieldNames.size(); i++){
			Method method;
			try {
				method = cls.getMethod("get"+fieldNames.get(i).substring(0, 1).toUpperCase()+fieldNames.get(i).substring(1), null);
				resultDict.put(fieldNames.get(i), invokeMethod(method, obj));
			} catch (Exception e) {
				throw new EcpayException(ErrorMessage.OBJ_MISSING_FIELD);
			}
		}
		return resultDict;
	}
	
	private final static String invokeMethod(Method method, Object obj){
		try {
			return method.invoke(obj, null).toString();
		} catch (Exception e) {
			throw new EcpayException(ErrorMessage.OBJ_MISSING_FIELD);
		}
	}
	
	/**
	 * client http post���\��
	 * @param url
	 * @param urlParameters
	 * @return response string
	 */
	public final static String httpPost(String url, String urlParameters, String encoding){
		try{
			URL obj = new URL(url);
			HttpURLConnection connection = null;
			if (obj.getProtocol().toLowerCase().equals("https")) {
				trustAllHosts();
				connection = (HttpsURLConnection) obj.openConnection();
			}
			else {
				connection = (HttpURLConnection) obj.openConnection();
			}
			connection.setRequestMethod("POST");
			connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.2171.71 Safari/537.36 EcPay JAVA API Version " + EcpayConfig.version);
			connection.setRequestProperty("Accept-Language", encoding);
			connection.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
			wr.write(urlParameters.getBytes(encoding));
			wr.flush();
			wr.close();
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), encoding));
			String inputLine;
			StringBuffer response = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();
			return response.toString();
		} catch(Exception e){
			throw new EcpayException(e.getMessage());
		}
	}
	
	/**
	 * ���� Unix TimeStamp
	 * @return TimeStamp
	 */
	public final static String genUnixTimeStamp(){
		Date date = new Date();
		Integer timeStamp = (int)(date.getTime() / 1000);
		return timeStamp.toString();
	}
	
	public final static Document xmlParser(String uri) {
		try{
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setValidating(false);
			factory.setIgnoringElementContentWhitespace(true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			return builder.parse(uri);
		} catch(Exception e){
			throw new EcpayException(ErrorMessage.CONF_FILE_ERROR);
		}
	}
	
	/**
	 * https �B�z
	 */
	private static void trustAllHosts() {
	    X509TrustManager easyTrustManager = new X509TrustManager() {
	        public void checkClientTrusted(
	                X509Certificate[] chain,
	                String authType) throws CertificateException {
	            // Oh, I am easy!
	        }
	        public void checkServerTrusted(
	                X509Certificate[] chain,
	                String authType) throws CertificateException {
	            // Oh, I am easy!
	        }
	        public X509Certificate[] getAcceptedIssuers() {
	            return null;
	        }
	    };
	    // Create a trust manager that does not validate certificate chains
	    TrustManager[] trustAllCerts = new TrustManager[] {easyTrustManager};
	    // Install the all-trusting trust manager
	    try {
	        SSLContext sc = SSLContext.getInstance("TLS");
	        sc.init(null, trustAllCerts, new java.security.SecureRandom());
	        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
	    } 
	    catch (Exception e) {
	    	e.printStackTrace();
	    }
	}
	
	/**
	 * �w�磌���ݩʪ��ѼƧ@�Ƨ�
	 * @param Class
	 * @return List
	 */
	private static List<String> getSortedFieldNames(Class<?> cls){
		Field[] fields = cls.getDeclaredFields();
		List<String> fieldNames = new ArrayList<String>();
		for(Field field: fields){
			fieldNames.add(field.getName());
		}
		Collections.sort(fieldNames, String.CASE_INSENSITIVE_ORDER);
		return fieldNames;
	}
	
	/**
	 * �N��ư� urlEncode�s�X
	 * @param data
	 * @return url encoded string
	 */
	public static String urlEncode(String data){
		String result = "";
		try {
			result = URLEncoder.encode(data, "UTF-8");
		} catch (UnsupportedEncodingException e) {
		}
		return result;
	}
	
	/**
	 * �N������urlEncode�r�갵�ഫ�ŦX .NET�y�����ഫ�W�h
	 * @param url
	 * @return .Net url encoded string
	 */
	private static String netUrlEncode(String url){
		String netUrlEncode = url.replaceAll("%21", "\\!").replaceAll("%28", "\\(").replaceAll("%29", "\\)");
		return netUrlEncode;
	}
	
	/**
	 * �N byte array ��ư� hash md5�� sha256 �B��A�æ^�� hex�Ȫ��r����
	 * @param data
	 * @param isMD5
	 * @return string
	 */
	private final static String hash(byte data[], String mode){
		MessageDigest md = null;
		try{
			if(mode == "MD5"){
				md = MessageDigest.getInstance("MD5");
			}
			else if(mode == "SHA-256"){
				md = MessageDigest.getInstance("SHA-256");
			}
		} catch(NoSuchAlgorithmException e){
		}
		return bytesToHex(md.digest(data));
	}
	
	/**
	 * �N byte array ����ഫ�� hex�r���
	 * @param bytes
	 * @return string
	 */
	private final static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
}