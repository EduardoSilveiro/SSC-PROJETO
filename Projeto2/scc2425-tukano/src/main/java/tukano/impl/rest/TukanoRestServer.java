
package tukano.impl.rest;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.ws.rs.core.Application;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import tukano.impl.Token;
import utils.Args;
import utils.IP;
import utils.Props;


public class TukanoRestServer extends Application {
	final private static Logger Log = Logger.getLogger(TukanoRestServer.class.getName());

	static final String INETADDR_ANY = "0.0.0.0";
	static String SERVER_BASE_URI = "http://%s:%s/rest"; //colocar /tukano/rest

	public static final int PORT = 8080;

	public static String serverURI;
			
	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}

	private Set<Object> singletons = new HashSet<>();
	private Set<Class<?>> resources = new HashSet<>();


	public TukanoRestServer() {
		resources.add(RestBlobsResource.class);
		resources.add(RestUsersResource.class);
		resources.add(RestShortsResource.class);
 		Props.load("azurekeys-region.props"); //place the props file in resources folder under java/main
		//serverURI = String.format(SERVER_BASE_URI, IP.hostname(), PORT);

	}



	protected void start() throws Exception {

	 ResourceConfig config = new ResourceConfig();
		config.register(new JacksonFeature());
		JdkHttpServerFactory.createHttpServer( URI.create(serverURI.replace(IP.hostname(), INETADDR_ANY)), config);

		Log.info(String.format("Tukano Server ready @ %s\n",  serverURI));
	}


	public static void main(String[] args) throws Exception {
		//Args.use(args);

		//Token.setSecret( Args.valueOf("-secret", ""));
		//Props.load( Args.valueOf("-props", "").split(","));

		new TukanoRestServer().start();
		return;
	}

	@Override
	public Set<Class<?>> getClasses() {
		return resources;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}
}

/**
package tukano.impl.rest;

 import java.net.URI;
 import java.util.logging.Logger;

 import org.glassfish.jersey.jackson.JacksonFeature;
 import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
 import org.glassfish.jersey.server.ResourceConfig;

 import tukano.impl.Token;
 import utils.Args;
 import utils.IP;


 public class TukanoRestServer {
 final private static Logger Log = Logger.getLogger(TukanoRestServer.class.getName());

 static final String INETADDR_ANY = "0.0.0.0";
 static String SERVER_BASE_URI = "http://%s:%s/rest";

 public static final int PORT = 8080;

 public static String serverURI;

 static {
 System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
 }

 protected TukanoRestServer() {
 serverURI = String.format(SERVER_BASE_URI, IP.hostname(), PORT);
 }


 protected void start() throws Exception {

 ResourceConfig config = new ResourceConfig();

 config.register(RestBlobsResource.class);
 config.register(RestUsersResource.class);
 config.register(RestShortsResource.class);
 config.register(new JacksonFeature());
 JdkHttpServerFactory.createHttpServer( URI.create(serverURI.replace(IP.hostname(), INETADDR_ANY)), config);

 Log.info(String.format("Tukano Server ready @ %s\n",  serverURI));
 }


 public static void main(String[] args) throws Exception {
 Args.use(args);

 Token.setSecret( Args.valueOf("-secret", ""));
 //Props.load( Args.valueOf("-props", "").split(","));

 new TukanoRestServer().start();
 }
 }

 **/