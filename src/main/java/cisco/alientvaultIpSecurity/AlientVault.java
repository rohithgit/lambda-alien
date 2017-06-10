package cisco.alientvaultIpSecurity;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import redis.clients.jedis.Jedis;

public class AlientVault implements RequestHandler<String, String> {
	Jedis jc;
	static HashMap<String, String> dataHand = new HashMap<String, String>();
	private static final String IPADDRESS_PATTERN = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
			+ "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
			+ "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";

	public static boolean validateIp(final String ip) {
		if (ip != null) {
			Pattern pattern = Pattern.compile(IPADDRESS_PATTERN);
			// System.out.println("Test "+ip);
			Matcher matcher = pattern.matcher(ip);
			return matcher.matches();
		} else {
			return false;
			
		}
	}

	public String handleRequest(String input, Context context) {

		int returnCode = 8;
		DataInputStream dis = null;
		String data = "not updated";
         
		//ALient vault IP
		String httpsURL = "http://reputation.alienvault.com/reputation.data";
		URL myurl;
		try {

			myurl = new URL(httpsURL);
			HttpURLConnection con = (HttpURLConnection) myurl.openConnection();
			InputStream ins = con.getInputStream();
			boolean redirect = false;

			// normally, 3xx is redirect
			int status = con.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				if (status == HttpURLConnection.HTTP_MOVED_TEMP
					|| status == HttpURLConnection.HTTP_MOVED_PERM
						|| status == HttpURLConnection.HTTP_SEE_OTHER)
				redirect = true;
			}

			System.out.println("Response Code ... " + status);

			if (redirect) {

				// get redirect url from "location" header field
				String newUrl = con.getHeaderField("Location");
		        newUrl=newUrl.replace("https://", "http://");
				// get the cookie if need, for login
				String cookies = con.getHeaderField("Set-Cookie");
                 status=con.getResponseCode();
				// open the new connnection again
				con = (HttpURLConnection) new URL(newUrl).openConnection();
				con.setRequestProperty("Cookie", cookies);
				con.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
				con.addRequestProperty("User-Agent", "Mozilla");
				con.addRequestProperty("Referer", "google.com");

				System.out.println("Redirect to URL : " + newUrl);

			}
			//
			if(status==HttpURLConnection.HTTP_OK)
			{
			BufferedInputStream bis = new BufferedInputStream(ins);
			dis = new DataInputStream(bis);
			System.out.println("Entered 1");
			while (dis.available() != 0) {
				System.out.println("Entered 2");
				data = dis.readLine();
				int startIndex = data.indexOf("#");

				String keyIp = data.substring(0, startIndex);
				if (validateIp(keyIp)) {
					String keyValue = data.substring(startIndex, data.length());
					dataHand.put(keyIp, keyValue);
				}

				

			}
			}
			System.out.println(dataHand);

		} catch (MalformedURLException e) {

			e.printStackTrace();
		} catch (IOException e) {

			e.printStackTrace();
		}
		
		// JedisElasticCache("testkey","testvalue");*/
		
		///To pull data to AWS Elastic Cache
		//String mumbaiHost = "mycachecluster.dp9s5w.ng.0001.aps1.cache.amazonaws.com";
		//String NorthvirginaHost = "secipdpcache.qtbzro.0001.use1.cache.amazonaws.com";
		String env="elasticache."+System.getenv("domain_stateful");
		jc = new Jedis(env, 6379);
	    JedisElasticCache();
		
		return data;
	}

	
	public void JedisElasticCache() {

		for (Map.Entry<String, String> entry : dataHand.entrySet()) {
		
		
			jc.set(entry.getKey(), entry.getValue());
			System.out.println("---->"+ entry.getValue());
		}

		

	}

}