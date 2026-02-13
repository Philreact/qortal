package org.qortal.api;

import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.rewrite.handler.RedirectPatternRule;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.InetAccessHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.qortal.api.resource.AnnotationPostProcessor;
import org.qortal.api.resource.ApiDefinition;
import org.qortal.api.websocket.*;
import org.qortal.network.Network;
import org.qortal.settings.Settings;
import org.qortal.utils.SslUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletRequest;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.security.KeyStore;
import java.security.SecureRandom;

public class ApiService {

	private static ApiService instance;

	private final ResourceConfig config;
	private Server server;
	private ApiKey apiKey;

	public static final String API_VERSION_HEADER = "X-API-VERSION";

	private ApiService() {
		this.config = new ResourceConfig();
		this.config.packages("org.qortal.api.resource", "org.qortal.api.restricted.resource");
		this.config.register(org.glassfish.jersey.media.multipart.MultiPartFeature.class);
		this.config.register(org.qortal.api.model.ConnectedPeerJacksonWriter.class, 10000);  // High priority for ConnectedPeer endpoints
		this.config.register(OpenApiResource.class);
		this.config.register(ApiDefinition.class);
		this.config.register(AnnotationPostProcessor.class);
	}

	public static ApiService getInstance() {
		if (instance == null)
			instance = new ApiService();

		return instance;
	}

	public Iterable<Class<?>> getResources() {
		return this.config.getClasses();
	}

	public void setApiKey(ApiKey apiKey) {
		this.apiKey = apiKey;
	}

	public ApiKey getApiKey() {
		return this.apiKey;
	}


	public void start() {
		//System.setProperty("javax.net.debug", "ssl,handshake");
		try {
			// 1. Setup Providers
			Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
			Security.addProvider(new org.bouncycastle.jsse.provider.BouncyCastleJsseProvider());

			String keystorePathname = Settings.getInstance().getSslKeystorePathname();
			String keystorePassword = Settings.getInstance().getSslKeystorePassword();

			if (keystorePathname != null && keystorePassword != null) {
				if (!Files.isReadable(Path.of(keystorePathname))) {
					SslUtils.generateSsl();
				}

				// 2. SSL Context Factory
				SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
				sslContextFactory.setKeyStorePath(keystorePathname);
				sslContextFactory.setKeyStorePassword(keystorePassword);
				sslContextFactory.setKeyStoreType("PKCS12");
				sslContextFactory.setKeyStoreProvider("BC");

				// Use SunJSSE for ALPN support
				sslContextFactory.setProvider("SunJSSE");
				sslContextFactory.setProtocol("TLS");

				// Disable Hostname Verification (Fixes "localhost" ALPN issues)
				sslContextFactory.setEndpointIdentificationAlgorithm(null);

				// We explicitly tell the server: "You MUST use a cipher that allows HTTP/2"
				sslContextFactory.setIncludeCipherSuites("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
				// HTTP/2 strict cipher compliance
				sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
				sslContextFactory.setUseCipherSuitesOrder(true);

				this.server = new Server();

				// 3. HTTP Configuration
				HttpConfiguration httpConfig = new HttpConfiguration();
				httpConfig.setSecureScheme("https");
				httpConfig.setSecurePort(Settings.getInstance().getApiPort());

				// HTTPS Config (adds Strict Transport Security, etc.)
				HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
				httpsConfig.addCustomizer(new SecureRequestCustomizer());

				// 4. Connection Factories
				// HTTP/1.1 (The Workhorse)
				HttpConnectionFactory http1 = new HttpConnectionFactory(httpsConfig);

				// HTTP/2 (The Goal)
				HTTP2ServerConnectionFactory h2 = new HTTP2ServerConnectionFactory(httpsConfig);

				// ALPN (The Negotiator: "h2" or "http/1.1"?)
				ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
				alpn.setDefaultProtocol(http1.getProtocol());

				// SSL (The Encryptor)
				// It hands off to ALPN once decryption is done
				SslConnectionFactory ssl = new SslConnectionFactory(sslContextFactory, alpn.getProtocol());

				// Peeks at the first bytes. - If TLS -> sends to 'ssl';  Plain -> sends to 'http1'.
				OptionalSslConnectionFactory optionalSsl = new OptionalSslConnectionFactory(ssl, http1.getProtocol());

				// 5. The Unified Connector
				// Order: Detector -> SSL -> ALPN -> H2 -> HTTP1
				ServerConnector sslConnector = new ServerConnector(this.server,
						optionalSsl, ssl, alpn, h2, http1);

				sslConnector.setPort(Settings.getInstance().getApiPort());
				sslConnector.setHost(Network.getInstance().getBindAddress());

				this.server.addConnector(sslConnector);

			} else {
				// Non-SSL Mode
				InetAddress bindAddr = InetAddress.getByName(Network.getInstance().getBindAddress());
				InetSocketAddress endpoint = new InetSocketAddress(bindAddr, Settings.getInstance().getApiPort());
				this.server = new Server(endpoint);
			}

			// Error handler
			ErrorHandler errorHandler = new ApiErrorHandler();
			this.server.setErrorHandler(errorHandler);

			// Request logging
			if (Settings.getInstance().isApiLoggingEnabled()) {
				RequestLogWriter logWriter = new RequestLogWriter("API-requests.log");
				logWriter.setAppend(true);
				logWriter.setTimeZone("UTC");
				RequestLog requestLog = new CustomRequestLog(logWriter, CustomRequestLog.EXTENDED_NCSA_FORMAT);
				this.server.setRequestLog(requestLog);
			}

			// IP address based access control
			InetAccessHandler accessHandler = new InetAccessHandler();
			for (String pattern : Settings.getInstance().getApiWhitelist()) {
				accessHandler.include(pattern);
			}
			this.server.setHandler(accessHandler);

			// URL rewriting
			RewriteHandler rewriteHandler = new RewriteHandler();
			accessHandler.setHandler(rewriteHandler);

			// Context
			ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
			context.setContextPath("/");
			rewriteHandler.setHandler(context);

			// Cross-origin resource sharing
			FilterHolder corsFilterHolder = new FilterHolder(CrossOriginFilter.class);
			corsFilterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
			corsFilterHolder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET, POST, DELETE");
			corsFilterHolder.setInitParameter(CrossOriginFilter.CHAIN_PREFLIGHT_PARAM, "false");
			context.addFilter(corsFilterHolder, "/*", null);

			// API servlet
			ServletContainer container = new ServletContainer(this.config);
			ServletHolder apiServlet = new ServletHolder(container);
			apiServlet.setInitOrder(1);
			context.addServlet(apiServlet, "/*");

			if (Settings.getInstance().isApiDocumentationEnabled()) {
				// Swagger-UI static content
				ClassLoader loader = this.getClass().getClassLoader();
				ServletHolder swaggerUIServlet = new ServletHolder("static-swagger-ui", DefaultServlet.class);
				swaggerUIServlet.setInitParameter("resourceBase", loader.getResource("resources/swagger-ui/").toString());
				swaggerUIServlet.setInitParameter("dirAllowed", "true");
				swaggerUIServlet.setInitParameter("pathInfoOnly", "true");
				context.addServlet(swaggerUIServlet, "/api-documentation/*");

				rewriteHandler.addRule(new RedirectPatternRule("", "/api-documentation/")); // redirect empty path to API docs
				rewriteHandler.addRule(new RedirectPatternRule("/api-documentation", "/api-documentation/")); // redirect to add trailing slash if missing
			} else {
				// Simple pages that explains that API documentation is disabled
				ClassLoader loader = this.getClass().getClassLoader();
				ServletHolder swaggerUIServlet = new ServletHolder("api-docs-disabled", DefaultServlet.class);
				swaggerUIServlet.setInitParameter("resourceBase", loader.getResource("api-docs-disabled/").toString());
				swaggerUIServlet.setInitParameter("dirAllowed", "true");
				swaggerUIServlet.setInitParameter("pathInfoOnly", "true");
				context.addServlet(swaggerUIServlet, "/api-documentation/*");

				rewriteHandler.addRule(new RedirectPatternRule("", "/api-documentation/")); // redirect empty path to API docs
				rewriteHandler.addRule(new RedirectPatternRule("/api-documentation", "/api-documentation/")); // redirect to add trailing slash if missing
			}

			context.addServlet(AdminStatusWebSocket.class, "/websockets/admin/status");
			context.addServlet(BlocksWebSocket.class, "/websockets/blocks");
			context.addServlet(DataMonitorSocket.class, "/websockets/datamonitor");
			context.addServlet(ActiveChatsWebSocket.class, "/websockets/chat/active/*");
			context.addServlet(ChatMessagesWebSocket.class, "/websockets/chat/messages");
			context.addServlet(UnsignedFeesSocket.class, "/websockets/crosschain/unsignedfees");
			context.addServlet(TradeOffersWebSocket.class, "/websockets/crosschain/tradeoffers");
			context.addServlet(TradeBotWebSocket.class, "/websockets/crosschain/tradebot");
			context.addServlet(TradePresenceWebSocket.class, "/websockets/crosschain/tradepresence");

			// Deprecated
			context.addServlet(PresenceWebSocket.class, "/websockets/presence");

			// Start server
			this.server.start();
		} catch (Exception e) {
			// Failed to start
			System.err.println("Failed to start API");
			e.printStackTrace();
			throw new RuntimeException("Failed to start API", e);

		}
	}

	public void stop() {
		try {
			// Stop server
			this.server.stop();
		} catch (Exception e) {
			// Failed to stop
		}

		this.server = null;
	}

	public static int getApiVersion(HttpServletRequest request) {
		// Get API version
		String apiVersionString = request.getHeader(API_VERSION_HEADER);
		if (apiVersionString == null) {
			// Try query string - this is needed to avoid a CORS preflight. See: https://stackoverflow.com/a/43881141
			apiVersionString = request.getParameter("apiVersion");
		}

		int apiVersion = 1;
		if (apiVersionString != null) {
			apiVersion = Integer.parseInt(apiVersionString);
		}
		return apiVersion;
	}

}
