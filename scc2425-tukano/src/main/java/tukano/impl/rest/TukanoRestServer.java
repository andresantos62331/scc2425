package tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import tukano.impl.Token;
import utils.Args;
import utils.IP;



@ApplicationPath("/rest")
public class TukanoRestServer extends Application {
    private static final Logger Log = Logger.getLogger(TukanoRestServer.class.getName());

    static final String INETADDR_ANY = "0.0.0.0";
	static String SERVER_BASE_URI = "http://%s:%s/rest";

	public static final int PORT = 8080;

	public static String serverURI;

    private final Set<Object> singletons = new HashSet<>();
    private final Set<Class<?>> resources = new HashSet<>();

    public TukanoRestServer() {


        serverURI = String.format(SERVER_BASE_URI, IP.hostname(), PORT);
        // Register REST resource classes
        //resources.add(RestBlobsResource.class);
        //resources.add(RestUsersResource.class);
        //resources.add(RestShortsResource.class);

        singletons.add(new RestBlobsResource());
        singletons.add(new RestUsersResource());
        singletons.add(new RestShortsResource());

        // Load properties and configurations
        Token.setSecret(Args.valueOf("-secret", ""));
        Log.info("Tukano Application initialized with resources and singletons");
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