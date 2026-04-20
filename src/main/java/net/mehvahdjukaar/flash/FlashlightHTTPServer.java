package net.mehvahdjukaar.flash;

import net.mehvahdjukaar.flash.api.SuperclassServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

public class FlashlightHTTPServer {
    private final Server server;

    public FlashlightHTTPServer() throws Exception {
        int port = 4303;
        System.out.println("Starting HTTP on port "+port);

        server = new Server(port);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);
        ResourceConfig config = new ResourceConfig();

        // MANUAL registration
        config.register(SuperclassServlet.class);
        ServletHolder jerseyServlet = new ServletHolder(new ServletContainer(config));
        context.addServlet(jerseyServlet, "/*");

        server.start();
        System.out.println("HTTP Server started on port "+port);
    }

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
            System.out.println("HTTP Server stopped");
        }
    }
}
